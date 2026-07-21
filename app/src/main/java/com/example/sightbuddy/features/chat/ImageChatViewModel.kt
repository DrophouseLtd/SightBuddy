package com.example.sightbuddy.features.chat

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
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

/**
 * Manages the Image Chat feature using OpenAI's vision-capable Chat Completions API.
 * Sends a full camera frame as a base64 JPEG so the model can describe scenes,
 * answer visual questions ("is it going to rain?"), identify objects, etc.
 *
 * The captured image is kept in memory so follow-up questions still have
 * visual context without re-capturing.
 */
class ImageChatViewModel(
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
    private val systemMsg = "You are a helpful visual assistant for a visually impaired user. " +
        "Be clear, concise, and conversational. " +
        "Describe what matters most first. Mention colours, weather, distances, and potential hazards when relevant. " +
        // The user speaks their questions; speech-to-text may garble them. Guard
        // against the model's canned "I can't access images" refusal — the photo
        // IS attached to every request.
        "The user's photo is attached to every message, so you can always see it. " +
        "Never say you cannot access, view, or see images. " +
        "Questions are transcribed from speech and may contain recognition errors. " +
        "Interpret them charitably as being about the photo. If a question mentions " +
        "something that is not in the image, do not just say it is absent — describe " +
        "what you DO see and its relevant details (such as colours), since the wording " +
        "was probably misheard. " +
        OpenAiTransport.LLM_MAX_WORDS_INSTRUCTION

    fun cancelActiveRequest() {
        activeCall?.cancel()
        activeCall = null
        _isProcessing.value = false
    }

    fun resetSession() {
        cancelActiveRequest()
        clearHistory()
    }

    /**
     * Encode bitmap to base64 JPEG, downscaled to keep token cost reasonable.
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val maxDim = 768
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

    suspend fun processImage(bitmap: Bitmap, userPrompt: String = ""): String {
        _isProcessing.value = true
        return try {
            withContext(Dispatchers.IO) {
                // New photo always starts a fresh conversation session.
                _chatHistory.value = emptyList()
                lastImageBase64 = bitmapToBase64(bitmap)

                val prompt = if (userPrompt.isBlank()) {
                    "Describe what you see in this image clearly and concisely for a visually impaired user. " +
                        "Start with the overall scene, then mention notable details. " +
                        "Keep it conversational — for example: 'You're looking at a park with a few people walking. " +
                        "The sky looks overcast, it might rain soon.'"
                } else {
                    userPrompt
                }

                val response = callOpenAIVision(userText = prompt, includeImage = true)

                val newHistory = _chatHistory.value.toMutableList()
                newHistory.add(ChatMessage("user", userPrompt.ifBlank { "Describe this image" }))
                newHistory.add(ChatMessage("assistant", response))
                _chatHistory.value = newHistory

                _lastResponse.value = response
                _isProcessing.value = false
                response
            }
        } catch (e: Exception) {
            Log.e("ImageChatViewModel", "Processing error", e)
            _isProcessing.value = false
            "An error occurred while processing the image. Please try again."
        }
    }

    suspend fun askFollowUp(userPrompt: String): String {
        if (lastImageBase64.isBlank()) {
            return "Please capture first."
        }
        if (userPrompt.isBlank()) {
            return "Please ask a question about the image."
        }

        _isProcessing.value = true
        return try {
            withContext(Dispatchers.IO) {
                // Re-attach the cached image: follow-ups are usually about visual
                // details the first description didn't mention, and without the
                // image the model can only answer from its own earlier text
                // (and says "I can't access images" for anything else).
                val response = callOpenAIVision(userText = userPrompt, includeImage = true)

                val newHistory = _chatHistory.value.toMutableList()
                newHistory.add(ChatMessage("user", userPrompt))
                newHistory.add(ChatMessage("assistant", response))
                _chatHistory.value = newHistory

                _lastResponse.value = response
                _isProcessing.value = false
                response
            }
        } catch (e: Exception) {
            Log.e("ImageChatViewModel", "Follow-up error", e)
            _isProcessing.value = false
            "An error occurred. Please try again."
        }
    }

    private fun callOpenAIVision(userText: String, includeImage: Boolean): String {
        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemMsg))

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
                        put("detail", "low")
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
            put("max_tokens", OpenAiTransport.LLM_MAX_OUTPUT_TOKENS)
            put("temperature", 0.7)
        }

        return OpenAiTransport.executeChatCompletion(
            httpClient = httpClient,
            transport = transport,
            openAiRequestBody = body,
            logTag = "ImageChatViewModel",
            onQuotaExhausted = onQuotaExhausted,
            onInstallRestricted = onInstallRestricted,
            registerCall = { activeCall = it },
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
