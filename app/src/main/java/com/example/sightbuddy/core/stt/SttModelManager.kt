package com.example.sightbuddy.core.stt

import android.content.Context
import android.util.Log
import com.example.sightbuddy.core.ModelFileGate
import com.example.sightbuddy.core.ModelFileGate.ForLoad
import com.example.sightbuddy.core.ModelFileGate.Install
import com.example.sightbuddy.core.VerifiedModels
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
 *
 * Every file is checked against its pinned SHA-256 ([FILES]) before it is
 * used: a download before it is moved into place, files already on the phone
 * once, before their first load. The files are only handed out checked
 * ([verifiedFiles], [verifiedVad]), so nothing can load one that was not.
 */
class SttModelManager internal constructor(
    filesDir: File,
    private val baseUrl: String,
    private val files: Map<String, Pinned> = FILES,
) {
    constructor(context: Context, baseUrl: String) : this(context.filesDir, baseUrl)

    sealed class DownloadState {
        data object Idle : DownloadState()
        data class Downloading(val percent: Int) : DownloadState()
        data object Ready : DownloadState()
        data class Failed(val message: String) : DownloadState()
    }

    /** The Whisper files, each checked against its SHA-256: only the gate makes a [ForLoad.Verified]. */
    class WhisperFiles(
        val encoder: ForLoad.Verified,
        val decoder: ForLoad.Verified,
        val tokens: ForLoad.Verified,
        val vad: ForLoad.Verified,
    )

    val modelDir: File = File(filesDir, "stt")

    private val verified = VerifiedModels(File(filesDir, VerifiedModels.DIR))

    private val _downloadState = MutableStateFlow<DownloadState>(
        if (isReady()) DownloadState.Ready else DownloadState.Idle,
    )
    val downloadState = _downloadState.asStateFlow()

    private val _removedAsDamaged = MutableStateFlow(false)

    /** True when files on the phone failed their check and were removed, until [damagedNoticeSaid]. */
    val removedAsDamaged = _removedAsDamaged.asStateFlow()

    fun damagedNoticeSaid() {
        _removedAsDamaged.value = false
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    /**
     * True when every model file is present and the completion marker exists.
     * Present, not necessarily checked: [verifiedFiles] hands them out checked.
     */
    fun isReady(): Boolean =
        File(modelDir, MARKER).exists() &&
            files.all { (name, pinned) -> File(modelDir, name).length() >= pinned.minBytes }

    val downloadEnabled: Boolean get() = baseUrl.isNotBlank()

    /** Space the model files take up on the device, in bytes. */
    fun sizeOnDisk(): Long =
        modelDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /**
     * Removes every model file. Refused while a download is running. The caller
     * unloads the engine first; the recogniser then falls back to the system one.
     */
    fun deleteModels(): Boolean {
        if (downloadInProgress) return false
        val ok = modelDir.deleteRecursively()
        files.keys.forEach { verified.forget(File(modelDir, it)) }
        _downloadState.value = DownloadState.Idle
        return ok
    }

    /**
     * The model files to load, or null when one is missing or does not match
     * its pinned SHA-256. Files hashed before are not hashed again (the set
     * takes a second or two, once). A file that does not match is deleted with
     * the completion marker: the models count as not downloaded, the download
     * is offered again, and [removedAsDamaged] is set. Blocking: call from a
     * background thread.
     */
    @Synchronized
    fun verifiedFiles(): WhisperFiles? {
        if (!isReady()) return null
        val checked = files.keys.associateWith { verifiedFile(it) ?: return null }
        return WhisperFiles(
            encoder = checked.getValue(ENCODER),
            decoder = checked.getValue(DECODER),
            tokens = checked.getValue(TOKENS),
            vad = checked.getValue(VAD),
        )
    }

    /** [verifiedFiles] for the Silero VAD alone, which the Gemma speech path uses. */
    @Synchronized
    fun verifiedVad(): ForLoad.Verified? = verifiedFile(VAD)

    private fun verifiedFile(name: String): ForLoad.Verified? =
        when (val result = ModelFileGate.forLoad(File(modelDir, name), files.getValue(name).sha256, verified)) {
            is ForLoad.Verified -> result
            ForLoad.Missing -> null
            is ForLoad.Unreadable -> {
                Log.e(TAG, "Could not read $name to check it", result.error)
                null
            }
            ForLoad.Damaged -> {
                Log.e(TAG, "$name does not match its SHA-256: deleted, to be downloaded again")
                File(modelDir, MARKER).delete()
                if (!downloadInProgress) _downloadState.value = DownloadState.Idle
                _removedAsDamaged.value = true
                null
            }
        }

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
            val totalBytes = files.values.sumOf { it.minBytes }.toFloat()
            var doneBytes = 0L
            _downloadState.value = DownloadState.Downloading(0)
            for ((name, pinned) in files) {
                val target = File(modelDir, name)
                // Kept from an earlier, unfinished download only if it matches;
                // one that does not is deleted here and fetched again.
                if (target.length() >= pinned.minBytes &&
                    ModelFileGate.forLoad(target, pinned.sha256, verified) is ForLoad.Verified
                ) {
                    doneBytes += pinned.minBytes
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
                if (tmp.length() < pinned.minBytes) error("$name incomplete (${tmp.length()} bytes)")
                when (ModelFileGate.install(tmp, target, pinned.sha256, verified)) {
                    Install.INSTALLED -> Unit
                    Install.WRONG_FILE -> error("$name does not match its SHA-256")
                    Install.NOT_MOVED -> error("Could not finalise $name")
                }
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

    /** A model file: the least a whole one weighs (for progress), and its SHA-256. */
    internal class Pinned(val minBytes: Long, val sha256: String)

    companion object {
        private const val TAG = "SttModelManager"
        const val ENCODER = "base.en-encoder.int8.onnx"
        const val DECODER = "base.en-decoder.int8.onnx"
        const val TOKENS = "base.en-tokens.txt"
        const val VAD = "silero_vad.onnx"
        internal const val MARKER = ".download-complete"

        /**
         * Minimum plausible sizes (bytes) reject truncated files; the SHA-256 is
         * what each file must be. The hashes are the release assets of
         * DrophouseLtd/sightbuddy-stt-models, tag whisper-base-en-v1 (the
         * default STT_MODEL_BASE_URL): GitHub's `digest` for each asset,
         * checked against a download of each on 2026-10-28. A build with its
         * own STT_MODEL_BASE_URL must serve these same files: anything else
         * fails the check and is never loaded. A new model release needs new
         * hashes here.
         */
        internal val FILES = linkedMapOf(
            ENCODER to Pinned(25_000_000L, "ef6b936f4c9b1d90a3b68634b60c4ed8576b26172b33c2535ec0e933c9edb823"),
            DECODER to Pinned(110_000_000L, "f7162ad6db2dbef16cfaeaa7f945b9d7dd9c1b8d472f6aca82f2273d185e4d41"),
            TOKENS to Pinned(100_000L, "306cd27f03c1a714eca7108e03d66b7dc042abe8c258b44c199a7ed9838dd930"),
            VAD to Pinned(400_000L, "9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6"),
        )
    }
}
