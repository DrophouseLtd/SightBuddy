package com.example.sightbuddy.features.chat

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.sightbuddy.R
import com.example.sightbuddy.core.OpenAiTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import android.content.Context

/**
 * Manages the Image Chat feature using OpenAI's vision-capable Chat Completions API.
 * Sends a full camera frame as a base64 JPEG so the model can describe scenes,
 * answer visual questions ("is it going to rain?"), identify objects, etc.
 *
 * The captured image is kept in memory so follow-up questions still have
 * visual context without re-capturing.
 */
class ImageChatViewModel(
    private val context: Context,
    private val transport: OpenAiTransport,
    /** Model id, read per request so a Settings change applies immediately. */
    private val modelProvider: () -> String = { "gpt-4o" },
    private val onQuotaExhausted: () -> Unit = {},
    private val onInstallRestricted: () -> Unit = {},
) {

    data class ChatMessage(val role: String, val text: String)

    private val _chatHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatHistory = _chatHistory.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    private val _lastResponse = MutableStateFlow("")
    val lastResponse = _lastResponse.asStateFlow()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private var lastImageBase64: String = ""
    private var activeCall: Call? = null

    fun cancelActiveRequest() {
        activeCall?.cancel()
        activeCall = null
        transport.cancelLocal()
        _isProcessing.value = false
    }

    fun resetSession() {
        cancelActiveRequest()
        clearHistory()
    }

    /**
     * Encode bitmap to base64 JPEG, downscaled to keep token cost reasonable.
     *
     * Its long side is 1,024 px, sent with OpenAI's high detail (decided
     * 2026-10-24). High detail looks at a photo with its short side at 768 px:
     * a 4:3 photo at 1,024 by 768 is seen whole for the same four tiles (about
     * 765 tokens) a 768 by 576 one costs. It was 768 with low detail, one
     * 512 px look (85 tokens), too coarse for text in the photo.
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val maxDim = 1024
        val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
            val scale = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true
            )
        } else {
            bitmap
        }

        val stream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        if (scaled !== bitmap) scaled.recycle()
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * [onDelta] (issue #24): the answer as it is written, from the on-device
     * model's thread; see [OpenAiTransport.executeChatCompletion]. The return
     * value is the whole answer either way, and the turn is kept the same way.
     */
    suspend fun processImage(bitmap: Bitmap, userPrompt: String = "", onDelta: ((String) -> Unit)? = null): String {
        _isProcessing.value = true
        return try {
            withContext(Dispatchers.IO) {
                // New photo always starts a fresh conversation session.
                _chatHistory.value = emptyList()
                lastImageBase64 = bitmapToBase64(bitmap)

                val response = callOpenAIVision(ChatPrompts.imageFirst(userPrompt, transport.answersOnDevice()), onDelta = onDelta)

                record(userPrompt.ifBlank { "Describe this image" }, response)

                _lastResponse.value = response
                _isProcessing.value = false
                response
            }
        } catch (e: Exception) {
            Log.e("ImageChatViewModel", "Processing error", e)
            _isProcessing.value = false
            context.getString(R.string.spoken_image_processing_error)
        }
    }

    /** As [processImage]'s [onDelta]. */
    suspend fun askFollowUp(userPrompt: String, onDelta: ((String) -> Unit)? = null): String {
        if (lastImageBase64.isBlank()) {
            return context.getString(R.string.spoken_capture_first)
        }
        if (userPrompt.isBlank()) {
            return context.getString(R.string.spoken_ask_about_image)
        }
        // At the length limit the chat stays as it is, so it can be saved.
        if (totalHistoryWordCount() > OpenAiTransport.CHAT_WORD_LIMIT) {
            return transport.errors.chatFull
        }

        _isProcessing.value = true
        return try {
            withContext(Dispatchers.IO) {
                // Re-attach the cached image: follow-ups are usually about visual
                // details the first description didn't mention, and without the
                // image the model can only answer from its own earlier text
                // (and says "I can't access images" for anything else).
                val response = callOpenAIVision(ChatPrompts.imageFollowUp(userPrompt, transport.answersOnDevice()), onDelta = onDelta)

                record(userPrompt, response)

                _lastResponse.value = response
                _isProcessing.value = false
                response
            }
        } catch (e: Exception) {
            Log.e("ImageChatViewModel", "Follow-up error", e)
            _isProcessing.value = false
            context.getString(R.string.spoken_generic_error)
        }
    }

    /**
     * Keeps a turn for the next request. A cancelled one is left out: it has no
     * answer, and an empty one would be sent as the model's reply.
     */
    private fun record(question: String, response: String) {
        if (response == OpenAiTransport.CANCELLED) return
        _chatHistory.value = _chatHistory.value + listOf(
            ChatMessage("user", question),
            ChatMessage("assistant", response),
        )
    }

    private fun callOpenAIVision(
        prompt: ChatPrompts.Prompt,
        includeImage: Boolean = true,
        onDelta: ((String) -> Unit)? = null,
    ): String {
        val userText = prompt.user
        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", prompt.system))

            _chatHistory.value.forEach { message ->
                put(
                    JSONObject()
                        .put("role", message.role)
                        .put("content", message.text)
                )
            }

            if (includeImage) {
                val imageContent = JSONObject().apply {
                    put("type", "image_url")
                    put("image_url", JSONObject().apply {
                        put("url", "data:image/jpeg;base64,$lastImageBase64")
                        // High: low detail is a 512 px look, too coarse for text in
                        // the photo (read out when asked) or small details.
                        put("detail", "high")
                    })
                }
                val textContent = JSONObject().apply {
                    put("type", "text")
                    put("text", userText)
                }
                val userContentArray = JSONArray().apply {
                    put(textContent)
                    put(imageContent)
                }
                put(JSONObject().put("role", "user").put("content", userContentArray))
            } else {
                put(JSONObject().put("role", "user").put("content", userText))
            }
        }

        val body = JSONObject().apply {
            put("model", modelProvider())
            put("messages", messages)
            put("max_tokens", prompt.maxTokens)
            put("temperature", prompt.temperature)
        }

        return OpenAiTransport.executeChatCompletion(
            httpClient = httpClient,
            transport = transport,
            openAiRequestBody = body,
            logTag = "ImageChatViewModel",
            onQuotaExhausted = onQuotaExhausted,
            onInstallRestricted = onInstallRestricted,
            registerCall = { activeCall = it },
            onDelta = onDelta,
        )
    }

    fun clearHistory() {
        _chatHistory.value = emptyList()
        lastImageBase64 = ""
        _lastResponse.value = ""
    }

    fun hasCachedImage(): Boolean = lastImageBase64.isNotBlank()

    fun totalHistoryWordCount(): Int {
        return _chatHistory.value.sumOf { message ->
            message.text.trim()
                .split(Regex("\\s+"))
                .count { it.isNotBlank() }
        }
    }
}
