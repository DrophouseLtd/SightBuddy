package com.example.sightbuddy.features.chat

import android.content.Context
import android.graphics.Bitmap
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
import java.util.concurrent.TimeUnit

/**
 * Manages the Document Chat feature using OpenAI's Chat Completions API:
 * 1. Captures a camera frame
 * 2. Extracts text locally via ML Kit (to save tokens)
 * 3. Sends extracted text + user prompt directly to OpenAI (user's own key)
 * 4. Returns the AI response for TTS playback
 */
class DocumentChatViewModel(
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

    private val textExtractor = LocalTextExtractor()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private var lastExtractedText: String = ""
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
     * Set extracted text directly (used by text-preview mode where local OCR
     * runs first and the user can then ask follow-up questions via mic).
     */
    fun setExtractedText(text: String) {
        lastExtractedText = text
    }

    fun extractedTextForPlayback(): String = lastExtractedText

    /**
     * [onDelta] (issue #24): the answer as it is written, from the on-device
     * model's thread; see [OpenAiTransport.executeChatCompletion]. The return
     * value is the whole answer either way, and the turn is kept the same way.
     */
    suspend fun processDocument(bitmap: Bitmap, userPrompt: String = "", onDelta: ((String) -> Unit)? = null): String {
        _isProcessing.value = true
        return try {
            withContext(Dispatchers.IO) {
                val extractedText = textExtractor.extractText(bitmap)
                lastExtractedText = extractedText

                if (extractedText.isBlank()) {
                    _isProcessing.value = false
                    return@withContext context.getString(R.string.spoken_no_text_in_image)
                }

                val onDevice = transport.answersOnDevice()
                val prompt = if (userPrompt.isBlank()) {
                    ChatPrompts.documentSummary(extractedText, onDevice)
                } else {
                    ChatPrompts.documentQuestion(extractedText, userPrompt, onDevice)
                }

                val response = callOpenAI(prompt, onDelta)

                // A cancelled answer belongs to a session already reset: keep no turn.
                if (response != OpenAiTransport.CANCELLED) {
                    val newHistory = _chatHistory.value.toMutableList()
                    newHistory.add(ChatMessage("user", userPrompt.ifBlank { "Describe this document" }))
                    newHistory.add(ChatMessage("assistant", response))
                    _chatHistory.value = newHistory
                }

                _lastResponse.value = response
                _isProcessing.value = false
                response
            }
        } catch (e: Exception) {
            Log.e("DocumentChatViewModel", "Processing error", e)
            _isProcessing.value = false
            context.getString(R.string.spoken_processing_error)
        }
    }

    /**
     * Ask a follow-up question about the previously scanned document.
     * [onDelta] as in [processDocument].
     */
    suspend fun askFollowUp(userPrompt: String, onDelta: ((String) -> Unit)? = null): String {
        if (lastExtractedText.isBlank()) {
            return context.getString(R.string.spoken_scan_document_first)
        }
        // At the length limit the chat stays as it is, so it can be saved.
        if (totalHistoryWordCount() > OpenAiTransport.CHAT_WORD_LIMIT) {
            return transport.errors.chatFull
        }

        _isProcessing.value = true
        return try {
            withContext(Dispatchers.IO) {
                val historyContext = _chatHistory.value.joinToString("\n") { msg ->
                    "${msg.role}: ${msg.text}"
                }

                val response = callOpenAI(
                    ChatPrompts.documentFollowUp(
                        lastExtractedText,
                        historyContext,
                        userPrompt,
                        transport.answersOnDevice(),
                    ),
                    onDelta,
                )

                if (response != OpenAiTransport.CANCELLED) {
                    val newHistory = _chatHistory.value.toMutableList()
                    newHistory.add(ChatMessage("user", userPrompt))
                    newHistory.add(ChatMessage("assistant", response))
                    _chatHistory.value = newHistory
                }

                _lastResponse.value = response
                _isProcessing.value = false
                response
            }
        } catch (e: Exception) {
            Log.e("DocumentChatViewModel", "Follow-up error", e)
            _isProcessing.value = false
            context.getString(R.string.spoken_generic_error)
        }
    }

    private fun callOpenAI(prompt: ChatPrompts.Prompt, onDelta: ((String) -> Unit)? = null): String {
        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", prompt.system))
            put(JSONObject().put("role", "user").put("content", prompt.user))
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
            logTag = "DocumentChatViewModel",
            onQuotaExhausted = onQuotaExhausted,
            onInstallRestricted = onInstallRestricted,
            registerCall = { activeCall = it },
            onDelta = onDelta,
        )
    }

    fun clearHistory() {
        _chatHistory.value = emptyList()
        lastExtractedText = ""
        _lastResponse.value = ""
    }

    fun hasCachedDocument(): Boolean = lastExtractedText.isNotBlank()

    fun totalHistoryWordCount(): Int {
        return _chatHistory.value.sumOf { message ->
            message.text.trim()
                .split(Regex("\\s+"))
                .count { it.isNotBlank() }
        }
    }

    fun close() {
        textExtractor.close()
    }
}
