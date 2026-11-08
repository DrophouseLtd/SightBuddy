package com.example.sightbuddy.features.chat

import android.util.Log
import com.example.sightbuddy.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import android.graphics.Bitmap

/**
 * Dev builds only: what the text recogniser saw in each frame, and what the
 * page-corner model made of it, for the on-screen overlay and for looking at
 * a session offline.
 *
 * Every event is one JSON line, both to logcat (`adb logcat -s OcrDebug`) and,
 * since logcat's buffer only holds a minute or so, to a file per Text chat
 * visit in the app's external files, `ocr/ocr-<time>.jsonl`:
 *
 *   adb pull /sdcard/Android/data/com.drophouse.sightbuddy.debug/files/ocr
 *
 * A frame line has "blocks"; a corners line has "corners". Block text is cut
 * to 40 characters.
 */
object OcrDebug {

    val enabled: Boolean = BuildConfig.OCR_DEBUG_OVERLAY

    data class Frame(
        val atMs: Long,
        /** "aim" (guidance), "read" (instant reading) or "capture". */
        val source: String,
        val widthPx: Int,
        val heightPx: Int,
        val ocrMs: Long,
        val blocks: List<LocalTextExtractor.Block>,
    )

    private val _frame = MutableStateFlow<Frame?>(null)
    val frame: StateFlow<Frame?> = _frame.asStateFlow()

    private val writer = Executors.newSingleThreadExecutor()
    private var dir: File? = null
    private var file: File? = null
    private var lastFrameSavedAtMs = 0L

    /** Where session files go; without it only logcat is written. */
    fun init(logDir: File?) {
        if (!enabled) return
        dir = logDir
    }

    fun publish(source: String, widthPx: Int, heightPx: Int, ocrMs: Long, blocks: List<LocalTextExtractor.Block>) {
        if (!enabled) return
        val frame = Frame(System.currentTimeMillis(), source, widthPx, heightPx, ocrMs, blocks)
        _frame.value = frame
        emit(frameJson(frame))
    }

    /**
     * The page-corner model's view of a frame: its four corners,
     * top-left first, with how sure it is of each, the page's share of the
     * frame, and the step taken ("aim") or the crop made ("capture"). The
     * overlay does not draw it.
     */
    fun corners(source: String, step: String, corners: PageCorners.Corners, frameMs: Long, area: Float) {
        if (!enabled) return
        val points = JSONArray()
        corners.corners.forEach { c ->
            points.put(JSONArray().put(round(c.x)).put(round(c.y)).put(round(c.confidence)))
        }
        emit(
            JSONObject()
                .put("t", System.currentTimeMillis())
                .put("src", source)
                .put("corners", points)
                .put("area", round(area))
                .put("ms", frameMs)
                .put("step", step)
                .toString()
        )
    }

    /** Starts a new session: the overlay empties and a new file is begun. */
    fun newSession() {
        if (!enabled) return
        _frame.value = null
        val logDir = dir ?: return
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        file = File(logDir, "ocr-$stamp.jsonl")
    }

    /**
     * About once a second, a small JPEG of the frame the guide just read, named
     * by its time next to the session's log, so the page's paper edges can be
     * tried offline against the same moments. Kept to 640 px and quality 70.
     */
    fun maybeSaveFrame(bitmap: Bitmap) {
        if (!enabled) return
        val session = file ?: return
        val now = System.currentTimeMillis()
        if (now - lastFrameSavedAtMs < FRAME_EVERY_MS) return
        lastFrameSavedAtMs = now
        val scale = FRAME_LONG_SIDE.toFloat() / maxOf(bitmap.width, bitmap.height)
        val small = Bitmap.createScaledBitmap(
            bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true,
        )
        val target = File(session.parentFile, session.nameWithoutExtension + "-" + now + ".jpg")
        writer.execute {
            runCatching {
                target.outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 70, it) }
            }.onFailure { Log.w(TAG, "Could not write ${target.path}", it) }
            small.recycle()
        }
    }

    private fun emit(line: String) {
        Log.d(TAG, line)
        val target = file ?: return
        writer.execute {
            runCatching {
                target.parentFile?.mkdirs()
                target.appendText(line + "\n")
            }.onFailure { Log.w(TAG, "Could not write ${target.path}", it) }
        }
    }

    private fun frameJson(frame: Frame): String {
        val blocks = JSONArray()
        frame.blocks.forEach { b ->
            blocks.put(
                JSONObject()
                    .put("l", round(b.left)).put("t", round(b.top))
                    .put("r", round(b.right)).put("b", round(b.bottom))
                    .put("chars", b.chars).put("lineH", b.lineHeightPx)
                    .put("text", b.text.replace('\n', ' ').take(40))
            )
        }
        return JSONObject()
            .put("t", frame.atMs)
            .put("src", frame.source)
            .put("w", frame.widthPx).put("h", frame.heightPx)
            .put("ms", frame.ocrMs)
            .put("blocks", blocks)
            .toString()
    }

    private fun round(v: Float): Double = Math.round(v * 1000.0) / 1000.0

    private const val TAG = "OcrDebug"
    private const val FRAME_EVERY_MS = 1_000L
    private const val FRAME_LONG_SIDE = 640
}
