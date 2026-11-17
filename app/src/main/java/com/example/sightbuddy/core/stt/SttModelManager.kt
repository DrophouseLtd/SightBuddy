package com.example.sightbuddy.core.stt

import android.content.Context
import android.util.Log
import com.example.sightbuddy.core.ModelFileGate
import com.example.sightbuddy.core.ModelFileGate.ForLoad
import com.example.sightbuddy.core.ModelFileGate.Install
import com.example.sightbuddy.core.VerifiedModels
import com.example.sightbuddy.core.stt.SttModelSet.LEGACY_EN
import com.example.sightbuddy.core.stt.SttModelSet.MULTILINGUAL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Manages the on-device Whisper model files (downloaded once, kept in
 * app-private storage). The app remains fully functional without them — the
 * voice service falls back to the system recognizer until one is ready.
 *
 * Two model sets live side by side, in their own folders and with their own
 * completion markers, so neither can be mistaken for the other and nothing
 * removes the other one's files ([SttModelSet]):
 *
 * - the multilingual model, in `filesDir/stt-multilingual/` — what the app
 *   downloads now, and what serves Polish, Finnish and English offline;
 * - the legacy base.en model, in `filesDir/stt/` — kept for phones that already
 *   have it; English only, never downloaded any more, never deleted on its own.
 *
 * Each set's four files (encoder, decoder, tokens, silero VAD) are pinned to a
 * SHA-256 in [SttModelCatalog]; the legacy pins live in [FILES]. Every file is
 * checked before it is used — a download before it is moved into place, a file
 * already on the phone once, before its first load. Files are only handed out
 * checked ([verifiedFiles]), so nothing can load one that was not.
 */
class SttModelManager internal constructor(
    filesDir: File,
    private val baseUrl: String,
    /** The legacy model's pins; the multilingual model's are in [SttModelCatalog]. */
    private val files: Map<String, Pinned> = FILES,
    private val catalog: (SttModelSet) -> SttModelSpec = { SttModelCatalog.of(it, files) },
) {
    constructor(context: Context, baseUrl: String) : this(context.filesDir, baseUrl)

    sealed class DownloadState {
        data object Idle : DownloadState()
        data class Downloading(val percent: Int) : DownloadState()
        data object Ready : DownloadState()
        data class Failed(val message: String) : DownloadState()
    }

    /** The Whisper files of one set, each checked against its SHA-256. */
    class WhisperFiles(
        val set: SttModelSet,
        /** The language the multilingual model is to be told; "en" for the legacy one. */
        val language: String = "en",
        val encoder: ForLoad.Verified,
        val decoder: ForLoad.Verified,
        val tokens: ForLoad.Verified,
        val vad: ForLoad.Verified,
    )

    private val root = filesDir

    private val verified = VerifiedModels(File(filesDir, VerifiedModels.DIR))

    private val specs = SttModelSet.entries.associateWith { catalog(it) }

    private val states = SttModelSet.entries.associateWith {
        MutableStateFlow<DownloadState>(if (isReady(it)) DownloadState.Ready else DownloadState.Idle)
    }

    /**
     * The multilingual model's state: the set the app downloads, and what the
     * download UI reports progress from.
     */
    val downloadState: StateFlow<DownloadState> = states.getValue(MULTILINGUAL).asStateFlow()

    /** One set's download state. */
    fun stateOf(set: SttModelSet): StateFlow<DownloadState> = states.getValue(set).asStateFlow()

    /** The small model's state, shown in Settings next to the base one. */
    val smallDownloadState: StateFlow<DownloadState> = states.getValue(SttModelSet.SMALL).asStateFlow()

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

    /** The model files' folder for [set]. */
    fun dirFor(set: SttModelSet): File = File(root, specs.getValue(set).dirName)

    /** [dirFor] the multilingual model, the one this version downloads. */
    val modelDir: File get() = dirFor(MULTILINGUAL)

    fun specOf(set: SttModelSet): SttModelSpec = specs.getValue(set)

    /**
     * True when every model file of [set] is present and its completion marker
     * exists. Present, not necessarily checked: [verifiedFiles] hands them out
     * checked.
     */
    fun isReady(set: SttModelSet = MULTILINGUAL): Boolean {
        val spec = specs.getValue(set)
        val dir = File(root, spec.dirName)
        return File(dir, spec.marker).exists() &&
            spec.allFiles.all { File(dir, it.name).length() >= it.bytes }
    }

    /** Which sets are complete on this phone. */
    fun installedSets(): List<SttModelSet> = SttModelSet.entries.filter { isReady(it) }

    val downloadEnabled: Boolean get() = baseUrl.isNotBlank()

    /** Space one set takes up on the device, in bytes. */
    fun sizeOnDisk(set: SttModelSet): Long {
        val dir = dirFor(set)
        if (!dir.exists()) return 0L
        val spec = specs.getValue(set)
        // The model files themselves: the completion marker is a few bytes of
        // bookkeeping, not something to tell the user they are storing.
        return spec.allFiles.sumOf { File(dir, it.name).length() }
    }

    /** Space every Whisper model takes up on the device, in bytes. */
    fun sizeOnDisk(): Long = SttModelSet.entries.sumOf { sizeOnDisk(it) }

    /**
     * Removes [set]'s model files, and only those. Refused while a download is
     * running. The caller unloads the engine first; the recogniser then falls
     * back to the other model or the system one.
     */
    fun deleteModels(set: SttModelSet = MULTILINGUAL): Boolean {
        if (downloadInProgress) return false
        val spec = specs.getValue(set)
        val dir = File(root, spec.dirName)
        val ok = !dir.exists() || dir.deleteRecursively()
        spec.allFiles.forEach { verified.forget(File(dir, it.name)) }
        states.getValue(set).value = DownloadState.Idle
        return ok
    }

    /**
     * Removes every Whisper model on the phone, both sets. This is the user
     * asking to delete the voice models, not an upgrade tidying up.
     */
    fun deleteAllModels(): Boolean = SttModelSet.entries.map { deleteModels(it) }.all { it }

    /**
     * The model files of [set] to load, or null when one is missing or does not
     * match its pinned SHA-256. Files hashed before are not hashed again (the
     * set takes a second or two, once). A file that does not match is deleted
     * with the set's completion marker: the set counts as not downloaded, the
     * download is offered again, and [removedAsDamaged] is set. Blocking: call
     * from a background thread.
     */
    @Synchronized
    fun verifiedFiles(set: SttModelSet = MULTILINGUAL, language: String = planLanguage()): WhisperFiles? {
        if (!isReady(set)) return null
        val spec = specs.getValue(set)
        val checked = spec.allFiles.associate { it.name to (verifiedFile(set, it.name) ?: return null) }
        return WhisperFiles(
            set = set,
            language = if (set == MULTILINGUAL) language else "en",
            encoder = checked.getValue(spec.encoder.name),
            decoder = checked.getValue(spec.decoder.name),
            tokens = checked.getValue(spec.tokens.name),
            vad = checked.getValue(spec.vad.name),
        )
    }

    /** The app's language as the model should be told it; "en" when it cannot be named. */
    private fun planLanguage(): String = plan(Locale.getDefault().toLanguageTag())?.language ?: "en"

    /**
     * The Silero VAD alone, which the Gemma speech path uses: the multilingual
     * model's when it is here, the legacy one's otherwise.
     */
    @Synchronized
    fun verifiedVad(): ForLoad.Verified? =
        verifiedFile(MULTILINGUAL, specs.getValue(MULTILINGUAL).vad.name)
            ?: verifiedFile(LEGACY_EN, specs.getValue(LEGACY_EN).vad.name)

    private fun verifiedFile(set: SttModelSet, name: String): ForLoad.Verified? {
        val spec = specs.getValue(set)
        val pinned = spec.allFiles.first { it.name == name }
        return when (val result = ModelFileGate.forLoad(File(dirFor(set), name), pinned.sha256, verified)) {
            is ForLoad.Verified -> result
            ForLoad.Missing -> null
            is ForLoad.Unreadable -> {
                Log.e(TAG, "Could not read $name to check it", result.error)
                null
            }
            ForLoad.Damaged -> {
                Log.e(TAG, "$name does not match its SHA-256: deleted, to be downloaded again")
                File(dirFor(set), spec.marker).delete()
                states.getValue(set).value = DownloadState.Idle
                _removedAsDamaged.value = true
                null
            }
        }
    }

    /**
     * Download any missing model files of [set]. Safe to call repeatedly; skips
     * files already fully present and matching. No-op (returns false) when
     * downloads are disabled.
     */
    @Volatile
    private var downloadInProgress = false

    suspend fun downloadIfNeeded(set: SttModelSet = MULTILINGUAL): Boolean = withContext(Dispatchers.IO) {
        val spec = specs.getValue(set)
        val state = states.getValue(set)
        if (isReady(set)) {
            state.value = DownloadState.Ready
            return@withContext true
        }
        if (!downloadEnabled) return@withContext false
        // Reject a second concurrent trigger.
        if (downloadInProgress) return@withContext false
        downloadInProgress = true
        try {
            val dir = dirFor(set).apply { mkdirs() }
            val totalBytes = spec.allFiles.sumOf { it.bytes }.toFloat()
            var doneBytes = 0L
            state.value = DownloadState.Downloading(0)
            for (pinned in spec.allFiles) {
                val target = File(dir, pinned.name)
                // Kept from an earlier, unfinished download only if it matches;
                // one that does not is deleted here and fetched again.
                if (target.length() >= pinned.bytes &&
                    ModelFileGate.forLoad(target, pinned.sha256, verified) is ForLoad.Verified
                ) {
                    doneBytes += pinned.bytes
                    continue
                }
                val tmp = File(dir, "${pinned.name}.part")
                // A part left behind by an interrupted attempt is not trusted.
                tmp.delete()
                val url = pinned.url(baseUrl)
                Log.i(TAG, "Downloading $url")
                client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code} for ${pinned.name}")
                    val body = resp.body ?: error("Empty body for ${pinned.name}")
                    body.byteStream().use { input ->
                        tmp.outputStream().use { out ->
                            val buf = ByteArray(256 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                doneBytes += n
                                val pct = ((doneBytes / totalBytes) * 100).toInt().coerceIn(0, 99)
                                state.value = DownloadState.Downloading(pct)
                            }
                        }
                    }
                }
                if (tmp.length() < pinned.bytes) error("${pinned.name} incomplete (${tmp.length()} bytes)")
                when (ModelFileGate.install(tmp, target, pinned.sha256, verified)) {
                    Install.INSTALLED -> Unit
                    Install.WRONG_FILE -> error("${pinned.name} does not match its SHA-256")
                    Install.NOT_MOVED -> error("Could not finalise ${pinned.name}")
                }
            }
            File(dir, spec.marker).writeText(spec.set.name.lowercase())
            // Leftover partial files from an earlier, interrupted attempt are of
            // no use now that every file is in place and checked.
            dir.listFiles { f -> f.name.endsWith(".part") }?.forEach { it.delete() }
            state.value = DownloadState.Ready
            Log.i(TAG, "STT model download complete: ${spec.set}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "STT model download failed (${spec.set})", e)
            state.value = DownloadState.Failed(e.message ?: "download failed")
            false
        } finally {
            downloadInProgress = false
        }
    }

    /**
     * What runs for [languageTag]: the multilingual model in its language, the
     * legacy one in English, or null for the platform recogniser.
     */
    fun plan(languageTag: String): WhisperPlan? = WhisperPlan.choose({ isReady(it) }, languageTag)

    /** A model file: the least a whole one weighs (for progress), and its SHA-256. */
    internal class Pinned(val minBytes: Long, val sha256: String)

    companion object {
        private const val TAG = "SttModelManager"

        // The legacy base.en files, by name: what phones already have.
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
