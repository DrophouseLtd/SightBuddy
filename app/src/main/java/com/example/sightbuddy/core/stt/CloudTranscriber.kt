package com.example.sightbuddy.core.stt

import android.util.Log
import com.example.sightbuddy.core.OpenAiTransport
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * Transcribes a recording with OpenAI, using the user's own API key.
 *
 * This exists because neither on-device option serves every user. The platform
 * recogniser decides for itself when someone has stopped talking, which cuts
 * questions in half and cannot be configured. The bundled Whisper model is
 * English-only. Sending the audio instead means the app owns the recording from
 * beginning to end in any language, at a fraction of a penny per minute on the
 * user's own account.
 *
 * The cost of that is plain and must stay plain: the audio leaves the device.
 * Nothing here runs unless the user has turned it on and been told so.
 */
class CloudTranscriber(private val transport: OpenAiTransport) {

    private val httpClient = OkHttpClient.Builder()
        // Generous: a minute of audio has to upload before anything comes back.
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun isConfigured(): Boolean = transport.isConfigured()

    /**
     * Blocking. [pcm] is signed 16-bit mono at [sampleRate]; [language] is an
     * ISO-639-1 code, which is a hint that markedly improves short utterances.
     *
     * Returns null on any failure, so the caller can fall back rather than
     * announcing an error the user cannot act on.
     */
    fun transcribe(pcm: ShortArray, sampleRate: Int, language: String): String? {
        if (!transport.isConfigured()) {
            Log.w(TAG, "No API key; cannot transcribe")
            return null
        }
        if (pcm.isEmpty()) return null
        return try {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    "speech.wav",
                    wav(pcm, sampleRate).toRequestBody("audio/wav".toMediaType()),
                )
                .addFormDataPart("model", MODEL)
                .addFormDataPart("language", language)
                // Plain text back: no timestamps or segments to parse.
                .addFormDataPart("response_format", "json")
                .build()
            val request = Request.Builder()
                .url(URL)
                .addHeader("Authorization", "Bearer ${transport.apiKey()}")
                .post(body)
                .build()
            httpClient.newCall(request).execute().use { response ->
                val payload = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.w(TAG, "Transcription failed: ${response.code} $payload")
                    return null
                }
                JSONObject(payload).optString("text", "").trim().ifBlank { null }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Transcription request failed", e)
            null
        }
    }

    /**
     * Wraps raw samples in a WAV header. The endpoint needs a container, and a
     * WAV header is 44 bytes of arithmetic against pulling in an encoder.
     */
    private fun wav(pcm: ShortArray, sampleRate: Int): ByteArray {
        val dataBytes = pcm.size * 2
        val out = ByteArrayOutputStream(44 + dataBytes)

        fun ascii(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
        fun int32(v: Int) {
            out.write(v and 0xFF); out.write((v shr 8) and 0xFF)
            out.write((v shr 16) and 0xFF); out.write((v shr 24) and 0xFF)
        }
        fun int16(v: Int) {
            out.write(v and 0xFF); out.write((v shr 8) and 0xFF)
        }

        ascii("RIFF"); int32(36 + dataBytes); ascii("WAVE")
        ascii("fmt "); int32(16)
        int16(1)                      // PCM, uncompressed
        int16(1)                      // mono
        int32(sampleRate)
        int32(sampleRate * 2)         // byte rate: mono, two bytes per sample
        int16(2)                      // block align
        int16(16)                     // bits per sample
        ascii("data"); int32(dataBytes)
        for (sample in pcm) int16(sample.toInt())
        return out.toByteArray()
    }

    companion object {
        private const val TAG = "CloudTranscriber"
        private const val URL = "https://api.openai.com/v1/audio/transcriptions"

        /** The cheapest transcription model, and ample for single spoken questions. */
        private const val MODEL = "whisper-1"
    }
}
