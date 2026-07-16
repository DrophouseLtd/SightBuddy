package com.example.sightbuddy.features.chat

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Uses Google ML Kit Text Recognition to extract text from a camera frame locally.
 * This saves API tokens by sending only extracted text to Gemini instead of the full image.
 */
class LocalTextExtractor {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Extract text from a Bitmap. Returns the extracted string, or empty string if no text found.
     */
    suspend fun extractText(bitmap: Bitmap): String {
        return suspendCancellableCoroutine { continuation ->
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val extracted = visionText.text.trim()
                    Log.i("LocalTextExtractor", "Extracted ${extracted.length} chars")
                    continuation.resume(extracted)
                }
                .addOnFailureListener { e ->
                    Log.e("LocalTextExtractor", "Text extraction failed", e)
                    continuation.resume("")
                }
        }
    }

    fun close() {
        recognizer.close()
    }
}
