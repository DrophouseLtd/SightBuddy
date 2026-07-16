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
 * Requests server-side deletion of quota/feedback for this [installId] and starts the
 * post-deletion AI restriction window.
 */
class DeleteDataTransport(
    private val deleteDataUrl: String,
    private val supabaseAnonKey: String,
    private val installId: String,
    private val integrityTokenProvider: IntegrityTokenProvider,
    private val httpClient: OkHttpClient = defaultClient(),
) {

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    fun isConfigured(): Boolean =
        deleteDataUrl.isNotBlank() &&
            supabaseAnonKey.isNotBlank() &&
            installId.isNotBlank()

    fun deleteMyData(): Boolean {
        if (!isConfigured()) {
            Log.w(TAG, "Delete-data transport not configured")
            return false
        }

        val body = JSONObject()
            .put("install_id", installId)
            .put("integrity_token", integrityTokenProvider.getToken())

        val request = Request.Builder()
            .url(deleteDataUrl)
            .addHeader("Authorization", "Bearer ${supabaseAnonKey.trim()}")
            .addHeader("apikey", supabaseAnonKey.trim())
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(jsonMedia))
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    true
                } else {
                    Log.w(TAG, "Delete data rejected: HTTP ${response.code}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Delete data request failed", e)
            false
        }
    }

    companion object {
        private const val TAG = "DeleteDataTransport"

        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
    }
}
