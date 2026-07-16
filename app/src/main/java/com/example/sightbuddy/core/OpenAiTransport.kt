package com.example.sightbuddy.core

import android.util.Log
import com.example.sightbuddy.di.IntegrityTokenProvider
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Sends Chat Completions payloads to the Supabase Edge [chat] proxy only.
 * The OpenAI API key lives on the server (Edge secret), not in the app.
 */
class OpenAiTransport(
    private val chatProxyUrl: String,
    private val supabaseAnonKey: String,
    private val installId: String,
    private val integrityTokenProvider: IntegrityTokenProvider,
) {

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    fun isConfigured(): Boolean =
        chatProxyUrl.isNotBlank() &&
            supabaseAnonKey.isNotBlank() &&
            installId.isNotBlank()

    fun buildChatCompletionsRequest(openAiRequestBody: JSONObject): Request {
        val wrapped = JSONObject()
        wrapped.put("install_id", installId)
        wrapped.put("integrity_token", integrityTokenProvider.getToken())
        for (key in openAiRequestBody.keys()) {
            wrapped.put(key, openAiRequestBody.get(key))
        }
        return Request.Builder()
            .url(chatProxyUrl)
            .addHeader("Authorization", "Bearer ${supabaseAnonKey.trim()}")
            .addHeader("apikey", supabaseAnonKey.trim())
            .addHeader("Content-Type", "application/json")
            .post(wrapped.toString().toRequestBody(jsonMedia))
            .build()
    }

    companion object {
        const val ERROR_INSTALL_RESTRICTED = "install_restricted"

        fun isInstallRestrictedError(responseBody: String): Boolean {
            if (responseBody.isBlank()) return false
            return try {
                JSONObject(responseBody).optString("error") == ERROR_INSTALL_RESTRICTED
            } catch (_: Exception) {
                false
            }
        }

        /**
         * Appended to system prompts so answers stay within TTS-friendly length.
         * Pair with [LLM_MAX_OUTPUT_TOKENS] in the request body.
         */
        const val LLM_MAX_WORDS_INSTRUCTION: String =
            "Hard limit: your entire reply must be 80 words or fewer."

        const val LLM_MAX_OUTPUT_TOKENS: Int = 150

        /**
         * Runs a chat/completions request via the Supabase Edge proxy.
         * [registerCall] receives the in-flight [Call] or null when finished.
         */
        fun executeChatCompletion(
            httpClient: OkHttpClient,
            transport: OpenAiTransport,
            openAiRequestBody: JSONObject,
            logTag: String,
            onQuotaExhausted: () -> Unit,
            onInstallRestricted: () -> Unit = {},
            registerCall: (Call?) -> Unit,
        ): String {
            if (!transport.isConfigured()) {
                return "AI service is unavailable right now."
            }
            val request = transport.buildChatCompletionsRequest(openAiRequestBody)
            val call = httpClient.newCall(request)
            registerCall(call)
            val response = try {
                call.execute()
            } finally {
                registerCall(null)
            }
            val responseBody = response.body?.string().orEmpty()
            if (response.code == 429) {
                onQuotaExhausted()
                return "Daily AI limit reached. Chat features will reset tomorrow."
            }
            if (response.code == 403 && isInstallRestrictedError(responseBody)) {
                onInstallRestricted()
                return "Cloud AI is unavailable for two days after deleting your data."
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
