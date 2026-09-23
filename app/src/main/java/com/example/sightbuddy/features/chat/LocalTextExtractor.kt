package com.example.sightbuddy.features.chat

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Uses Google ML Kit Text Recognition to extract text from a camera frame locally.
 * This saves API tokens by sending only extracted text to Gemini instead of the full image.
 */
class LocalTextExtractor {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Extract text from a Bitmap, one paragraph per recognised block (see
     * [TextSections]), or an empty string if no text was found. With [page],
     * blocks whose middle lies to the left or right of that page are left out,
     * so a guided capture reads the page it was aimed at and not the one beside
     * it. Only sideways: the two pages of a spread sit side by side, and the
     * page box can be one section of the page, so cutting above and below it
     * lost the rest of the page. If no block is left, everything is kept.
     * [debugSource] names the caller for the dev overlay.
     */
    suspend fun extractText(bitmap: Bitmap, debugSource: String = "capture", page: TextFrame? = null): String {
        val (visionText, ms) = recognise(bitmap)
        val all = visionText?.textBlocks.orEmpty()
        val onPage = page?.let { p ->
            val w = bitmap.width.toFloat()
            all.filter { block ->
                val box = block.boundingBox ?: return@filter false
                box.exactCenterX() / w in (p.left - PAGE_MARGIN)..(p.right + PAGE_MARGIN)
            }
        }?.takeIf { it.isNotEmpty() } ?: all
        val extracted = TextSections.join(onPage.map { block -> block.lines.map { it.text } }).trim()
        Log.i("LocalTextExtractor", "Extracted ${extracted.length} chars")
        if (OcrDebug.enabled) {
            OcrDebug.publish(debugSource, bitmap.width, bitmap.height, ms, blocksOf(visionText, bitmap))
        }
        return extracted
    }

    /**
     * Every recognised block, for aiming the camera: where it sits as fractions
     * of the frame, and how tall its lines are. Nothing is kept.
     *
     * Aiming reads a smaller copy of the frame, which is several times faster;
     * [scaleToFull] is how much larger the full frame is, and sizes come back in
     * its pixels, so they say how the text will read in the full-size capture.
     */
    suspend fun locateText(bitmap: Bitmap, scaleToFull: Float = 1f, debugSource: String = "aim"): Located {
        val (visionText, ms) = recognise(bitmap)
        val blocks = blocksOf(visionText, bitmap).map {
            it.copy(lineHeightPx = (it.lineHeightPx * scaleToFull).toInt())
        }
        val width = (bitmap.width * scaleToFull).toInt()
        val height = (bitmap.height * scaleToFull).toInt()
        if (OcrDebug.enabled) OcrDebug.publish(debugSource, width, height, ms, blocks)
        return Located(blocks, width, height)
    }

    data class Located(val blocks: List<PageTracker.Block>, val widthPx: Int, val heightPx: Int)

    /** The recognised text, or null on failure, and how long it took in ms. */
    private suspend fun recognise(bitmap: Bitmap): Pair<Text?, Long> {
        val startedAt = System.currentTimeMillis()
        val text = suspendCancellableCoroutine<Text?> { continuation ->
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { e ->
                    Log.w("LocalTextExtractor", "Text recognition failed", e)
                    continuation.resume(null)
                }
        }
        return text to System.currentTimeMillis() - startedAt
    }

    private fun blocksOf(visionText: Text?, bitmap: Bitmap): List<PageTracker.Block> {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        return visionText?.textBlocks.orEmpty().mapNotNull { block ->
            val box = block.boundingBox ?: return@mapNotNull null
            val heights = block.lines.mapNotNull { it.boundingBox?.height() }.sorted()
            PageTracker.Block(
                left = box.left / w,
                top = box.top / h,
                right = box.right / w,
                bottom = box.bottom / h,
                chars = block.text.length,
                lineHeightPx = heights.getOrElse(heights.size / 2) { box.height() },
                text = block.text,
            )
        }
    }

    /** The text's bounding box as fractions of the frame. */
    data class TextFrame(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val chars: Int,
        /** Median line height in frame pixels; 0 when not known. */
        val lineHeightPx: Int = 0,
    )

    fun close() {
        recognizer.close()
    }

    private companion object {
        /** How far outside the aimed-at page, as a share of the frame, a block may sit. */
        const val PAGE_MARGIN = 0.06f
    }
}
