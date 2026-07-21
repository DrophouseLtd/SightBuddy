package com.example.sightbuddy.core

import android.util.Log
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Sends Chat Completions requests **directly to OpenAI** using the user's own
 * API key (bring-your-own-key mode). No proxy, no server of ours — usage and
 * billing go straight to the user's OpenAI account.
 */
class OpenAiTransport(private val apiKeyProvider: () -> String) {

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    fun isConfigured(): Boolean = apiKeyProvider().isNotBlank()

    fun buildChatCompletionsRequest(openAiRequestBody: JSONObject): Request =
        Request.Builder()
            .url(CHAT_COMPLETIONS_URL)
            .addHeader("Authorization", "Bearer ${apiKeyProvider().trim()}")
            .addHeader("Content-Type", "application/json")
            .post(openAiRequestBody.toString().toRequestBody(jsonMedia))
            .build()

    companion object {
        private const val CHAT_COMPLETIONS_URL = "https://api.openai.com/v1/chat/completions"

        /**
         * Appended to system prompts so answers stay within TTS-friendly length.
         * Pair with [LLM_MAX_OUTPUT_TOKENS] in the request body.
         */
        const val LLM_MAX_WORDS_INSTRUCTION: String =
            "Hard limit: your entire reply must be 80 words or fewer."

        const val LLM_MAX_OUTPUT_TOKENS: Int = 150

        /**
         * Runs a chat/completions request against OpenAI with the user's key.
         * [registerCall] receives the in-flight [Call] or null when finished.
         * [onQuotaExhausted] / [onInstallRestricted] are legacy hooks kept for
         * caller compatibility; only [onQuotaExhausted] fires (on HTTP 429).
         */
        fun executeChatCompletion(
            httpClient: OkHttpClient,
            transport: OpenAiTransport,
            openAiRequestBody: JSONObject,
            logTag: String,
            onQuotaExhausted: () -> Unit = {},
            @Suppress("UNUSED_PARAMETER") onInstallRestricted: () -> Unit = {},
            registerCall: (Call?) -> Unit,
        ): String {
            if (!transport.isConfigured()) {
                return "No API key set. Add your OpenAI API key in settings to use AI features."
            }
            val request = transport.buildChatCompletionsRequest(openAiRequestBody)
            val call = httpClient.newCall(request)
            registerCall(call)
            val response = try {
                call.execute()
            } catch (e: Exception) {
                Log.e(logTag, "Chat request failed", e)
                return "Could not reach OpenAI. Check your internet connection."
            } finally {
                registerCall(null)
            }
            val responseBody = response.body?.string().orEmpty()
            if (response.code == 401 || response.code == 403) {
                return "Your OpenAI API key was rejected. Please check it in settings."
            }
            if (response.code == 429) {
                onQuotaExhausted()
                return "OpenAI rate limit or spending cap reached. Please try again later."
            }
            if (!response.isSuccessful) {
                Log.e(logTag, "Chat API error ${response.code}: $responseBody")
                return "AI service error (${response.code}). Please try again."
            }
            return try {
                val json = JSONObject(responseBody)
                json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()
            } catch (e: Exception) {
                Log.e(logTag, "Failed to parse chat response", e)
                "I received a response but couldn't read it. Please try again."
            }
        }
    }
}
