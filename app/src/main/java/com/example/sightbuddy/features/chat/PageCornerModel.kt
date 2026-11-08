package com.example.sightbuddy.features.chat

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtSession
import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.os.SystemClock
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import java.nio.FloatBuffer

/**
 * The page-corner model, run with ONNX Runtime; the
 * arithmetic around it is [PageCorners]. One for the app: it is loaded on
 * first use, off the main thread, and kept. If it cannot be loaded, that is
 * logged, [failed] turns true, and Find page takes the picture at once.
 */
class PageCornerModel private constructor(private val assets: AssetManager) {

    /**
     * What the model found in a frame: up to two places for each corner;
     * [modelMs] for the model's run alone, [frameMs] for all of it (the
     * pixels, the input and the run).
     */
    data class Found(val candidates: PageCorners.Candidates, val modelMs: Long, val frameMs: Long) {
        /** Each corner's highest point, page or not. */
        val corners: PageCorners.Corners get() = candidates.best
    }

    @Volatile
    var failed = false
        private set

    private val lock = Any()
    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    private val input = FloatArray(PageCorners.INPUT_LENGTH)
    private val heatmaps = FloatArray(PageCorners.OUTPUT_LENGTH)
    private var pixels = IntArray(0)
    /** Frames in a row the model failed on; [RUN_FAILURES_TO_GIVE_UP] turns it off. */
    private var runFailures = 0

    /** Loads the model now, so the first frame does not wait for it. Blocking. */
    fun warmUp() {
        synchronized(lock) { sessionOrNull() }
    }

    /**
     * The corners of the page in [bitmap], a copy of the frame no bigger than
     * [PageCorners.SOURCE_LONG_SIDE]; null when the model is not there or this
     * frame failed. Blocking: off the main thread.
     */
    fun detect(bitmap: Bitmap): Found? = synchronized(lock) {
        val s = sessionOrNull() ?: return null
        val environment = env ?: return null
        val frameStartedAt = SystemClock.elapsedRealtime()
        try {
            val w = bitmap.width
            val h = bitmap.height
            if (pixels.size < w * h) pixels = IntArray(w * h)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
            val box = PageCorners.letterbox(w, h)
            PageCorners.input(pixels, box, input)
            val startedAt = SystemClock.elapsedRealtime()
            OnnxTensor.createTensor(environment, FloatBuffer.wrap(input), INPUT_SHAPE).use { tensor ->
                s.run(mapOf(INPUT_NAME to tensor)).use { result ->
                    (result.get(0) as OnnxTensor).floatBuffer.get(heatmaps)
                }
            }
            val now = SystemClock.elapsedRealtime()
            runFailures = 0
            Found(PageCorners.peaks(heatmaps, box), now - startedAt, now - frameStartedAt)
        } catch (e: Exception) {
            runFailed(e)
        } catch (e: OutOfMemoryError) {
            runFailed(e)
        }
    }

    /**
     * One frame failed: skipped. Only failing [RUN_FAILURES_TO_GIVE_UP] frames
     * in a row turns the model off, and Find page takes the picture at once.
     */
    private fun runFailed(e: Throwable): Found? {
        runFailures += 1
        if (runFailures >= RUN_FAILURES_TO_GIVE_UP) {
            Log.e(TAG, "Model run failed $runFailures times in a row; Find page takes the picture at once", e)
            failed = true
        } else {
            Log.w(TAG, "Model run failed; frame skipped", e)
        }
        return null
    }

    /**
     * The page in [frame], found again in a small copy of it and flattened from
     * the full frame: a crop of just the page, upright. [steady] is the aim
     * frame's page that took the picture, if the guide took it: the picture's
     * corners are those that follow it, and it stands in for them when they
     * are not all seen ([PageCorners.cropPlan]). No page when the model is not
     * there, neither will do, or memory runs short. Blocking: off the main thread.
     */
    fun cropToPage(frame: Bitmap, steady: PageCorners.Corners? = null): Crop = try {
        crop(frame, steady)
    } catch (e: OutOfMemoryError) {
        Log.w(TAG, "No memory to crop the page; reading the whole frame", e)
        Crop(null, null, null)
    }

    private fun crop(frame: Bitmap, steady: PageCorners.Corners?): Crop {
        val scale = PageCorners.SOURCE_LONG_SIDE.toFloat() / maxOf(frame.width, frame.height)
        val small = if (scale < 1f) {
            frame.scale((frame.width * scale).toInt().coerceAtLeast(1), (frame.height * scale).toInt().coerceAtLeast(1))
        } else {
            frame
        }
        val found = try { detect(small) } finally { if (small != frame) small.recycle() }
            ?: return Crop(null, null, null)
        val c = found.candidates
        val own = if (steady != null) {
            val followed = PageLock.follow(c, steady.corners.map { it.x to it.y })
            PageCorners.Corners(List(4) { k -> followed[k] ?: c.peaks[k].first().copy(confidence = 0f) }, c.widthPx, c.heightPx)
        } else {
            PageCorners.choose(c) ?: c.best
        }
        val plan = PageCorners.cropPlan(own, steady)
        val corners = plan.corners ?: return Crop(null, found, null, plan.source, own)
        val grown = PageCorners.grown(corners, plan.grow)
        val size = PageCorners.warpSize(grown, frame.width, frame.height)
            ?: return Crop(null, found, null, PageCorners.CropSource.NONE, own)
        val fw = frame.width.toFloat()
        val fh = frame.height.toFloat()
        val src = FloatArray(8) { i -> if (i % 2 == 0) grown[i / 2].x * fw else grown[i / 2].y * fh }
        val dw = size.width.toFloat()
        val dh = size.height.toFloat()
        val dst = floatArrayOf(0f, 0f, dw, 0f, dw, dh, 0f, dh)
        val matrix = Matrix()
        if (!matrix.setPolyToPoly(src, 0, dst, 0, 4)) return Crop(null, found, null, PageCorners.CropSource.NONE, own)
        val page = try {
            createBitmap(size.width, size.height)
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "No memory for a ${size.width}x${size.height} page; reading the whole frame", e)
            return Crop(null, found, null, PageCorners.CropSource.NONE, own)
        }
        Canvas(page).drawBitmap(frame, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        return Crop(page, found, size, plan.source, own)
    }

    /**
     * [page] is the flattened page, or null to read the whole frame; [found]
     * what the model saw, [own] the picture's own corners of the page, and
     * [source] whose corners the crop used.
     */
    data class Crop(
        val page: Bitmap?,
        val found: Found?,
        val size: PageCorners.Size?,
        val source: PageCorners.CropSource = PageCorners.CropSource.NONE,
        val own: PageCorners.Corners? = null,
    )

    private fun sessionOrNull(): OrtSession? {
        session?.let { return it }
        if (failed) return null
        val startedAt = SystemClock.elapsedRealtime()
        // Creating the session is the last step that can fail to load: nothing to close then.
        val (environment, made) = try {
            val bytes = assets.open(ASSET).use { it.readBytes() }
            val runtime = OrtEnvironment.getEnvironment()
            runtime to createSession(runtime, bytes)
        } catch (e: Throwable) {
            // Anything, the native library included: Find page then takes the picture at once.
            Log.e(TAG, "Model load failed; Find page takes the picture at once", e)
            failed = true
            return null
        }
        val (created, provider) = made
        env = environment
        session = created
        Log.i(TAG, "Model loaded in ${SystemClock.elapsedRealtime() - startedAt} ms, $provider, $THREADS threads")
        // One run on a black frame: the first is slow, and its time is worth knowing.
        // A failed one counts as a failed frame; the session is kept.
        val warmStartedAt = SystemClock.elapsedRealtime()
        try {
            OnnxTensor.createTensor(environment, FloatBuffer.wrap(FloatArray(PageCorners.INPUT_LENGTH)), INPUT_SHAPE).use { tensor ->
                created.run(mapOf(INPUT_NAME to tensor)).close()
            }
            Log.i(TAG, "Warm-up run ${SystemClock.elapsedRealtime() - warmStartedAt} ms")
        } catch (e: Exception) {
            runFailed(e)
        } catch (e: OutOfMemoryError) {
            runFailed(e)
        }
        return created
    }

    /**
     * A session on XNNPACK where the runtime has it, else on the plain CPU
     * provider, and which it is, for the log.
     */
    private fun createSession(environment: OrtEnvironment, bytes: ByteArray): Pair<OrtSession, String> {
        try {
            OrtSession.SessionOptions().use { options ->
                options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                // XNNPACK runs its own threads; the session's would compete with them.
                options.setIntraOpNumThreads(1)
                options.addXnnpack(mapOf("intra_op_num_threads" to THREADS.toString()))
                return environment.createSession(bytes, options) to "XNNPACK"
            }
        } catch (e: OrtException) {
            Log.i(TAG, "No XNNPACK in this runtime (${e.message}); using the CPU provider")
        }
        OrtSession.SessionOptions().use { options ->
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            options.setIntraOpNumThreads(THREADS)
            return environment.createSession(bytes, options) to "CPU"
        }
    }

    companion object {
        const val TAG = "PAGE-CORNERS"
        private const val ASSET = "page_corners.onnx"
        private const val INPUT_NAME = "input"
        private val INPUT_SHAPE = longArrayOf(1, 3, PageCorners.INPUT_SIZE.toLong(), PageCorners.INPUT_SIZE.toLong())

        /** Threads for the model: half of a phone's eight cores, the camera and the voice keep the rest. */
        private const val THREADS = 4

        /** Frames failed in a row before the model is given up on. */
        private const val RUN_FAILURES_TO_GIVE_UP = 3

        @Volatile
        private var instance: PageCornerModel? = null

        fun get(context: Context): PageCornerModel =
            instance ?: synchronized(this) {
                instance ?: PageCornerModel(context.applicationContext.assets).also { instance = it }
            }
    }
}
