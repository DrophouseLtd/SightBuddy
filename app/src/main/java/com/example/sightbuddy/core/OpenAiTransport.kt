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
class OpenAiTransport(
    private val apiKeyProvider: () -> String,
    val errors: Errors = Errors(),
) {

    /**
     * Failure messages, which are spoken to the user and so must be in the app
     * language. Passed in already localised; the English defaults keep the class
     * usable without a Context.
     */
    class Errors(
        val noKey: String = "No API key set. Add your OpenAI API key in settings to use AI features.",
        val network: String = "Could not reach OpenAI. Check your internet connection.",
        val keyRejected: String = "Your OpenAI API key was rejected. Please check it in settings.",
        val rateLimited: String = "OpenAI rate limit or spending cap reached. Please try again later.",
        val service: (Int) -> String = { code -> "AI service error ($code). Please try again." },
    )

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    fun isConfigured(): Boolean = apiKeyProvider().isNotBlank()

    /** The user's key, for requests this class does not build itself. */
    fun apiKey(): String = apiKeyProvider().trim()

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
         * Appended to every LLM system prompt. The model must weigh user safety in all replies.
         */
        const val LLM_USER_SAFETY_INSTRUCTION: String =
            "Always prioritize user safety in your responses and avoid guidance that could put the user at risk."

        /**
         * Appended to system prompts to keep answers short enough to listen to.
         *
         * A target rather than a hard limit. As a limit it truncated reasoning
         * mid-thought on questions that genuinely needed more, which reads as the
         * assistant refusing to engage.
         */
        const val LLM_MAX_WORDS_INSTRUCTION: String =
            "Keep replies brief, aiming for 80 words or fewer. Shorter is better. " +
                "Never pad an answer, and stop as soon as the question is answered."

        const val LLM_MAX_OUTPUT_TOKENS: Int = 150

        /**
         * Appended to prompts so the reply follows the app language, never the
         * language of the question or of the prompt itself. Someone may speak
         * English into a Finnish app and must still be answered in Finnish.
         *
         * The Finnish form is written in Finnish: an instruction in the target
         * language steers the output language far more reliably than one about it.
         */
        fun replyLanguageInstruction(): String =
            when (java.util.Locale.getDefault().language) {
                "fi" -> " Vastaa aina suomeksi, vaikka kysymys olisi muulla kielellä. Always reply in Finnish."
                else -> " Always reply in English, even if the question is in another language."
            }

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
                return transport.errors.noKey
            }
            val request = transport.buildChatCompletionsRequest(openAiRequestBody)
            val call = httpClient.newCall(request)
            registerCall(call)
            val response = try {
                call.execute()
            } catch (e: Exception) {
                Log.e(logTag, "Chat request failed", e)
                return transport.errors.network
            } finally {
                registerCall(null)
            }
            val responseBody = response.body?.string().orEmpty()
            if (response.code == 401 || response.code == 403) {
                return transport.errors.keyRejected
            }
            if (response.code == 429) {
                onQuotaExhausted()
                return transport.errors.rateLimited
            }
            if (!response.isSuccessful) {
                Log.e(logTag, "Chat API error ${response.code}: $responseBody")
                return transport.errors.service(response.code)
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
