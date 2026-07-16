package com.example.sightbuddy.core

import android.util.Log
import com.example.sightbuddy.di.IntegrityTokenProvider
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Posts anonymous feedback to the Supabase Edge [feedback] function.
 * Uses the same [installId] as LLM quota (per-install UUID, not linked to accounts).
 */
class FeedbackTransport(
    private val feedbackUrl: String,
    private val supabaseAnonKey: String,
    private val installId: String,
    private val integrityTokenProvider: IntegrityTokenProvider,
    private val httpClient: OkHttpClient = defaultClient(),
) {

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    fun isConfigured(): Boolean =
        feedbackUrl.isNotBlank() &&
            supabaseAnonKey.isNotBlank() &&
            installId.isNotBlank()

    /**
     * @return true if the server accepted the request (including silent block after cap).
     */
    fun submit(feedback: String): Boolean {
        if (!isConfigured()) {
            Log.w(TAG, "Feedback transport not configured")
            return false
        }
        val trimmed = feedback.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_FEEDBACK_CHARS) return false

        val body = JSONObject()
            .put("install_id", installId)
            .put("integrity_token", integrityTokenProvider.getToken())
            .put("feedback", trimmed)

        val request = Request.Builder()
            .url(feedbackUrl)
            .addHeader("Authorization", "Bearer ${supabaseAnonKey.trim()}")
            .addHeader("apikey", supabaseAnonKey.trim())
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(jsonMedia))
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    true
                } else {
                    if (response.code == 403 && OpenAiTransport.isInstallRestrictedError(body)) {
                        Log.w(TAG, "Feedback blocked: install restricted")
                    } else {
                        Log.w(TAG, "Feedback rejected: HTTP ${response.code}")
                    }
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Feedback submit failed", e)
            false
        }
    }

    companion object {
        private const val TAG = "FeedbackTransport"
        const val MAX_FEEDBACK_CHARS = 4000

        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
    }
}
