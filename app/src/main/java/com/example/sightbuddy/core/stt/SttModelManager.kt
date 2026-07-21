package com.example.sightbuddy.core.stt

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Manages the on-device Whisper STT model files (downloaded once, kept in
 * app-private storage). The app remains fully functional without them — the
 * voice service falls back to the system recognizer until [isReady].
 *
 * Files (Whisper base.en int8 + silero VAD, ~154 MB total):
 *   filesDir/stt/base.en-encoder.int8.onnx
 *   filesDir/stt/base.en-decoder.int8.onnx
 *   filesDir/stt/base.en-tokens.txt
 *   filesDir/stt/silero_vad.onnx
 *
 * Download source is BuildConfig.STT_MODEL_BASE_URL; when empty, automatic
 * download is disabled (dev builds get files via adb push).
 */
class SttModelManager(context: Context, private val baseUrl: String) {

    sealed class DownloadState {
        data object Idle : DownloadState()
        data class Downloading(val percent: Int) : DownloadState()
        data object Ready : DownloadState()
        data class Failed(val message: String) : DownloadState()
    }

    val modelDir: File = File(context.filesDir, "stt")

    private val _downloadState = MutableStateFlow<DownloadState>(
        if (isReady()) DownloadState.Ready else DownloadState.Idle,
    )
    val downloadState = _downloadState.asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    fun encoderFile() = File(modelDir, ENCODER)
    fun decoderFile() = File(modelDir, DECODER)
    fun tokensFile() = File(modelDir, TOKENS)
    fun vadFile() = File(modelDir, VAD)

    /** True when every model file is present and the completion marker exists. */
    fun isReady(): Boolean =
        File(modelDir, MARKER).exists() &&
            FILES.all { (name, minBytes) -> File(modelDir, name).length() >= minBytes }

    val downloadEnabled: Boolean get() = baseUrl.isNotBlank()

    /**
     * Download any missing model files. Safe to call repeatedly; skips files
     * already fully present. No-op (returns false) when downloads are disabled.
     */
    @Volatile
    private var downloadInProgress = false

    suspend fun downloadIfNeeded(): Boolean = withContext(Dispatchers.IO) {
        if (isReady()) {
            _downloadState.value = DownloadState.Ready
            return@withContext true
        }
        if (!downloadEnabled) return@withContext false
        // Reject a second concurrent trigger.
        if (downloadInProgress) return@withContext false
        downloadInProgress = true
        try {
            modelDir.mkdirs()
            val totalBytes = FILES.values.sum().toFloat()
            var doneBytes = 0L
            _downloadState.value = DownloadState.Downloading(0)
            for ((name, minBytes) in FILES) {
                val target = File(modelDir, name)
                if (target.length() >= minBytes) {
                    doneBytes += minBytes
                    continue
                }
                val tmp = File(modelDir, "$name.part")
                val url = baseUrl.trimEnd('/') + "/" + name
                Log.i(TAG, "Downloading $url")
                client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code} for $name")
                    val body = resp.body ?: error("Empty body for $name")
                    body.byteStream().use { input ->
                        tmp.outputStream().use { out ->
                            val buf = ByteArray(256 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                doneBytes += n
                                val pct = ((doneBytes / totalBytes) * 100).toInt().coerceIn(0, 99)
                                _downloadState.value = DownloadState.Downloading(pct)
                            }
                        }
                    }
                }
                if (tmp.length() < minBytes) error("$name incomplete (${tmp.length()} bytes)")
                if (!tmp.renameTo(target)) error("Could not finalise $name")
            }
            File(modelDir, MARKER).writeText("v1")
            _downloadState.value = DownloadState.Ready
            Log.i(TAG, "STT model download complete")
            true
        } catch (e: Exception) {
            Log.e(TAG, "STT model download failed", e)
            _downloadState.value = DownloadState.Failed(e.message ?: "download failed")
            false
        } finally {
            downloadInProgress = false
        }
    }

    companion object {
        private const val TAG = "SttModelManager"
        const val ENCODER = "base.en-encoder.int8.onnx"
        const val DECODER = "base.en-decoder.int8.onnx"
        const val TOKENS = "base.en-tokens.txt"
        const val VAD = "silero_vad.onnx"
        private const val MARKER = ".download-complete"

        // Minimum plausible sizes (bytes) to reject truncated files.
        private val FILES = linkedMapOf(
            ENCODER to 25_000_000L,
            DECODER to 110_000_000L,
            TOKENS to 100_000L,
            VAD to 400_000L,
        )
    }
}
