package com.example.sightbuddy.features.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.vision.detector.ObjectDetector

/**
 * COCO object detection using EfficientDet-Lite0 via TensorFlow Lite Task Vision.
 *
 * The model asset (`efficientdet-lite0.tflite`) ships with baked-in metadata that
 * contains the COCO label map and preprocessing parameters. [ObjectDetector] reads
 * this metadata automatically — no manual label file, input normalization, or
 * post-processing (NMS / bbox decoding) is needed.
 *
 * Expected input: 320x320 RGB. The model's fused NMS outputs four tensors
 * (boxes, classes, scores, num_detections), all consumed internally by the Task API.
 */
class TFLiteObjectAnalyzer(private val context: Context) {

    data class Detection(
        val label: String,
        val confidence: Float,
        val boundingBox: RectF  // Normalized 0..1
    )

    private var objectDetector: ObjectDetector? = null
    private val confidenceThreshold = 0.4f

    companion object {
        private const val TAG = "TFLiteObjectAnalyzer"
        private const val MODEL_ASSET = "efficientdet-lite0.tflite"
        private const val INPUT_SIZE = 320
    }

    init {
        try {
            val baseOptions = BaseOptions.builder()
                .setNumThreads(4)
                .build()

            val options = ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(baseOptions)
                .setScoreThreshold(confidenceThreshold)
                .setMaxResults(25)
                .build()

            objectDetector = ObjectDetector.createFromFileAndOptions(
                context, MODEL_ASSET, options
            )
            Log.i(TAG, "EfficientDet-Lite0 loaded via Task Vision (metadata labels)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model", e)
        }
    }

    /**
     * Run detection on a Bitmap. The bitmap is scaled to 320x320 before inference.
     * Returns detections above [confidenceThreshold] with normalized bounding boxes.
     */
    fun analyze(bitmap: Bitmap): List<Detection> {
        val detector = objectDetector ?: return emptyList()

        val resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val tensorImage = TensorImage.fromBitmap(resized)

        val results = detector.detect(tensorImage)

        // Recycle only after detect() has consumed the pixels (TensorImage holds a lazy ref)
        if (resized !== bitmap) resized.recycle()

        val w = INPUT_SIZE.toFloat()
        val h = INPUT_SIZE.toFloat()

        val detections = ArrayList<Detection>(results.size)
        for (result in results) {
            val category = result.categories.firstOrNull() ?: continue
            val label = category.label
            if (label.isNullOrBlank()) continue

            val box = result.boundingBox
            detections.add(
                Detection(
                    label = label,
                    confidence = category.score,
                    boundingBox = RectF(
                        box.left / w,
                        box.top / h,
                        box.right / w,
                        box.bottom / h
                    )
                )
            )
        }

        Log.d(TAG, "Returning ${detections.size} detections (threshold $confidenceThreshold)")
        return detections
    }

    fun close() {
        objectDetector?.close()
        objectDetector = null
    }
}
