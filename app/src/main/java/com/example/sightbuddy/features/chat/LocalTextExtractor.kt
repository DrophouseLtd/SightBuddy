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
     * [TextSections]), or an empty string if no text was found.
     * [debugSource] names the caller for the dev overlay.
     */
    suspend fun extractText(bitmap: Bitmap, debugSource: String = "capture"): String {
        val (visionText, ms) = recognise(bitmap)
        val all = visionText?.textBlocks.orEmpty()
        val extracted = TextSections.join(all.map { block -> block.lines.map { it.text } }).trim()
        Log.i("LocalTextExtractor", "Extracted ${extracted.length} chars")
        if (OcrDebug.enabled) {
            OcrDebug.publish(debugSource, bitmap.width, bitmap.height, ms, blocksOf(visionText, bitmap))
        }
        return extracted
    }

    /**
     * Every recognised block, for live text: where it sits as fractions of the
     * frame, and how tall its lines are. Nothing is kept.
     */
    suspend fun locateText(bitmap: Bitmap, debugSource: String = "read"): Located {
        val (visionText, ms) = recognise(bitmap)
        val blocks = blocksOf(visionText, bitmap)
        if (OcrDebug.enabled) OcrDebug.publish(debugSource, bitmap.width, bitmap.height, ms, blocks)
        return Located(blocks, bitmap.width, bitmap.height)
    }

    data class Located(val blocks: List<Block>, val widthPx: Int, val heightPx: Int)

    /** A recognised block of text, its box as fractions of the frame. */
    data class Block(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val chars: Int,
        /** Median line height, in frame pixels. */
        val lineHeightPx: Int,
        val text: String,
    )

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

    private fun blocksOf(visionText: Text?, bitmap: Bitmap): List<Block> {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        return visionText?.textBlocks.orEmpty().mapNotNull { block ->
            val box = block.boundingBox ?: return@mapNotNull null
            val heights = block.lines.mapNotNull { it.boundingBox?.height() }.sorted()
            Block(
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

    fun close() {
        recognizer.close()
    }
}
