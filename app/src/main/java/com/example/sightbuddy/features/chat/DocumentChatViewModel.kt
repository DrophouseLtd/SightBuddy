package com.example.sightbuddy.features.chat

import android.graphics.Bitmap
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
import java.util.concurrent.TimeUnit

/**
 * Manages the Document Chat feature using OpenAI's Chat Completions API:
 * 1. Captures a camera frame
 * 2. Extracts text locally via ML Kit (to save tokens)
 * 3. Sends extracted text + user prompt to OpenAI (or Supabase Edge proxy)
 * 4. Returns the AI response for TTS playback
 */
class DocumentChatViewModel(
    private val transport: OpenAiTransport,
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

    private val model = "gpt-4o-mini"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private var lastExtractedText: String = ""
    /** First assistant reply only — used by Text chat playback when text preview is off. */
    private var firstAssistantResponse: String = ""
    private var activeCall: Call? = null

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
     * Set extracted text directly (used by text-preview mode where local OCR
     * runs first and the user can then ask follow-up questions via mic).
     */
    fun setExtractedText(text: String) {
        lastExtractedText = text
    }

    fun extractedTextForPlayback(): String = lastExtractedText

    fun firstAssistantResponseForPlayback(): String = firstAssistantResponse

    private fun recordFirstAssistantResponse(response: String) {
        if (firstAssistantResponse.isBlank() && response.isNotBlank()) {
            firstAssistantResponse = response
        }
    }

    suspend fun processDocument(bitmap: Bitmap, userPrompt: String = ""): String {
        _isProcessing.value = true
        return try {
            withContext(Dispatchers.IO) {
                val extractedText = textExtractor.extractText(bitmap)
                lastExtractedText = extractedText

                if (extractedText.isBlank()) {
                    _isProcessing.value = false
                    return@withContext "I couldn't detect any text in the image. Try pointing the camera at text and holding it steady."
                }

                val systemMsg = "You are a helpful assistant for a visually impaired user. " +
                    "Be clear, concise, and conversational. " +
                    "When describing documents, start with what kind of document it is. " +
                    OpenAiTransport.LLM_MAX_WORDS_INSTRUCTION

                val userMsg = if (userPrompt.isBlank()) {
                    "I just scanned a document with my camera. " +
                        "Please read and summarize the following text clearly and concisely. " +
                        "For example, say 'Here's a bill from the gas company, would you like to know the payment details?':\n\n$extractedText"
                } else {
                    "Here is text extracted from a document I scanned:\n\n" +
                        "\"$extractedText\"\n\n" +
                        "My question: $userPrompt\n\n" +
                        "Please answer clearly and concisely."
                }

                val response = callOpenAI(systemMsg, userMsg)

                val newHistory = _chatHistory.value.toMutableList()
                newHistory.add(ChatMessage("user", userPrompt.ifBlank { "Describe this document" }))
                newHistory.add(ChatMessage("assistant", response))
                _chatHistory.value = newHistory

                _lastResponse.value = response
                recordFirstAssistantResponse(response)
                _isProcessing.value = false
                response
            }
        } catch (e: Exception) {
            Log.e("DocumentChatViewModel", "Processing error", e)
            _isProcessing.value = false
            "An error occurred while processing. Please try again."
        }
    }

    /**
     * Ask a follow-up question about the previously scanned document.
     */
    suspend fun askFollowUp(userPrompt: String): String {
        if (lastExtractedText.isBlank()) {
            return "Please scan a document first by pointing your camera at text."
        }

        _isProcessing.value = true
        return try {
            withContext(Dispatchers.IO) {
                val historyContext = _chatHistory.value.joinToString("\n") { msg ->
                    "${msg.role}: ${msg.text}"
                }

                val systemMsg = "You are a helpful assistant for a visually impaired user. " +
                    "Be clear, concise, and conversational. " +
                    OpenAiTransport.LLM_MAX_WORDS_INSTRUCTION

                val userMsg = "Here is text from a document I scanned:\n\n" +
                    "\"$lastExtractedText\"\n\n" +
                    "Previous conversation:\n$historyContext\n\n" +
                    "My new question: $userPrompt\n\n" +
                    "Please answer clearly and concisely."

                val response = callOpenAI(systemMsg, userMsg)

                val newHistory = _chatHistory.value.toMutableList()
                newHistory.add(ChatMessage("user", userPrompt))
                newHistory.add(ChatMessage("assistant", response))
                _chatHistory.value = newHistory

                _lastResponse.value = response
                _isProcessing.value = false
                response
            }
        } catch (e: Exception) {
            Log.e("DocumentChatViewModel", "Follow-up error", e)
            _isProcessing.value = false
            "An error occurred. Please try again."
        }
    }

    private fun callOpenAI(systemMessage: String, userMessage: String): String {
        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemMessage))
            put(JSONObject().put("role", "user").put("content", userMessage))
        }

        val body = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("max_tokens", OpenAiTransport.LLM_MAX_OUTPUT_TOKENS)
            put("temperature", 0.7)
        }

        return OpenAiTransport.executeChatCompletion(
            httpClient = httpClient,
            transport = transport,
            openAiRequestBody = body,
            logTag = "DocumentChatViewModel",
            onQuotaExhausted = onQuotaExhausted,
            onInstallRestricted = onInstallRestricted,
            registerCall = { activeCall = it },
        )
    }

    fun clearHistory() {
        _chatHistory.value = emptyList()
        lastExtractedText = ""
        firstAssistantResponse = ""
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
