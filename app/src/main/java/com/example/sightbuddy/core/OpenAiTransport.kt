package com.example.sightbuddy.core

import android.util.Log
import com.example.sightbuddy.core.llm.LocalAnswerer
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import com.example.sightbuddy.core.llm.PromptTooLongException
import java.util.Locale

/**
 * Sends Chat Completions requests **directly to OpenAI** using the user's own
 * API key (bring-your-own-key mode). No proxy, no server of ours — usage and
 * billing go straight to the user's OpenAI account.
 *
 * Also the one place that decides where an answer comes from: OpenAI when the
 * user allows it and the device is online, otherwise the on-device model.
 *
 * **Privacy gate.** [apiAllowed] is the user's "Use API" switch. While it is
 * off, [isConfigured] is false, [apiKey] is empty and no request can be built,
 * whatever key is stored. Every OpenAI call in the app (chat and transcription)
 * goes through this class, so this is the only gate there has to be.
 */
class OpenAiTransport(
    private val apiKeyProvider: () -> String,
    val errors: Errors = Errors(),
    private val apiAllowed: () -> Boolean = { true },
    private val online: () -> Boolean = { true },
    /** The on-device model, or null when it is not installed. */
    @Volatile var local: LocalAnswerer? = null,
    /** The user's "Use local chat AI" switch. */
    private val localAllowed: () -> Boolean = { true },
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
        val apiOff: String = "AI answers are off. Turn on Use API in settings, or install the on-device model.",
        val localFailed: String = "The on-device model could not answer. Please try again.",
        val tooLong: String = "Sorry, your message was too long. Can you summarize it?",
        val chatFull: String = "This chat has reached its maximum length. You can save it with Save chat under More. Capture again to start a new chat.",
    )

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    /** True only when the user allows the API **and** a key is stored. */
    fun isConfigured(): Boolean = apiAllowed() && apiKeyProvider().isNotBlank()

    /** Stops an on-device answer in progress (the OkHttp call is cancelled by its owner). */
    fun cancelLocal() {
        local?.cancel()
    }

    /** Whether any answer is possible: OpenAI, or the on-device model. */
    fun canAnswer(): Boolean = isConfigured() || usableLocal() != null

    private fun usableLocal(): LocalAnswerer? = local?.takeIf { localAllowed() && it.isReady() }

    /** The user's key, for requests this class does not build itself. Empty while the API is off. */
    fun apiKey(): String = if (apiAllowed()) apiKeyProvider().trim() else ""

    fun buildChatCompletionsRequest(openAiRequestBody: JSONObject): Request {
        // Last line of defence: nothing reaches OpenAI while the user has said no.
        check(isConfigured()) { "OpenAI request built while the API is not allowed" }
        return Request.Builder()
            .url(CHAT_COMPLETIONS_URL)
            .addHeader("Authorization", "Bearer ${apiKeyProvider().trim()}")
            .addHeader("Content-Type", "application/json")
            .post(openAiRequestBody.toString().toRequestBody(jsonMedia))
            .build()
    }

    companion object {
        /** Logcat tag saying where each answer came from. */
        const val ROUTE_TAG = "AI-ROUTE"

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
         * Words of questions and answers in one chat before it is full. It used to
         * end the chat silently; now the chat stays, to be saved, and new questions
         * are told it is full.
         */
        const val CHAT_WORD_LIMIT: Int = 500

        /**
         * Appended to prompts so the reply follows the app language, never the
         * language of the question or of the prompt itself. Someone may speak
         * English into a Finnish app and must still be answered in Finnish.
         *
         * The Finnish form is written in Finnish: an instruction in the target
         * language steers the output language far more reliably than one about it.
         */
        fun replyLanguageInstruction(): String =
            when (Locale.getDefault().language) {
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
            val cloudUsable = transport.isConfigured() && transport.online()
            val local = transport.usableLocal()
            if (!cloudUsable && local != null) {
                Log.i(ROUTE_TAG, "$logTag: on device")
                return try {
                    local.complete(openAiRequestBody) ?: transport.errors.localFailed
                } catch (e: PromptTooLongException) {
                    if (e.chatTooLong) transport.errors.chatFull else transport.errors.tooLong
                }
            }
            if (!transport.isConfigured()) {
                Log.i(ROUTE_TAG, "$logTag: nothing available")
                return if (transport.apiAllowed()) transport.errors.noKey else transport.errors.apiOff
            }
            Log.i(ROUTE_TAG, "$logTag: OpenAI")
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
                Log.e(logTag, "Chat API error ${response.code}")
                PrivateLog.i(logTag) { "Chat API error body: $responseBody" }
                if (responseBody.contains("context_length_exceeded")) return transport.errors.tooLong
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
