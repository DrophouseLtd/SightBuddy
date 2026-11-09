package com.example.sightbuddy.features.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector

/**
 * COCO object detection using EfficientDet-Lite0 via MediaPipe Tasks.
 *
 * The model asset (`efficientdet-lite0.tflite`) ships with baked-in metadata that
 * contains the COCO label map and preprocessing parameters. [ObjectDetector] reads
 * this metadata automatically — no manual label file, input normalization, or
 * post-processing (NMS / bbox decoding) is needed.
 *
 * MediaPipe replaced the TensorFlow Lite Task Library, whose last release has
 * native code aligned for 4 KB memory pages only: Google Play requires 16 KB
 * (2.4.2's upload was refused for it). The model is the same.
 *
 * Expected input: 320x320 RGB. The model's fused NMS outputs four tensors
 * (boxes, classes, scores, num_detections), all consumed internally by the task.
 */
class TFLiteObjectAnalyzer(private val context: Context) {

    data class Detection(
        val label: String,
        val confidence: Float,
        val boundingBox: RectF  // Normalized 0..1
    )

    private var objectDetector: ObjectDetector? = null
    private val confidenceThreshold = DetectionRules.DETECTOR_FLOOR

    companion object {
        private const val TAG = "TFLiteObjectAnalyzer"
        private const val MODEL_ASSET = "efficientdet-lite0.tflite"
        private const val INPUT_SIZE = 320
    }

    init {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_ASSET)
                .build()

            val options = ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                .setScoreThreshold(confidenceThreshold)
                .setMaxResults(25)
                .build()

            objectDetector = ObjectDetector.createFromOptions(context, options)
            Log.i(TAG, "EfficientDet-Lite0 loaded via MediaPipe Tasks (metadata labels)")
        } catch (e: Throwable) {
            // Anything, an Error from the native library or its start-up included:
            // the app runs without object detection rather than crash at launch.
            Log.e(TAG, "Failed to load model", e)
        }
    }

    /**
     * Run detection on a Bitmap. The bitmap is scaled to 320x320 before inference.
     * Returns detections above [confidenceThreshold] with normalized bounding boxes.
     */
    @Synchronized // with close(): the detector is never closed during a detection
    fun analyze(bitmap: Bitmap): List<Detection> {
        val detector = objectDetector ?: return emptyList()

        val resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val results = try {
            detector.detect(BitmapImageBuilder(resized).build()).detections()
        } finally {
            // Recycle only after detect() has consumed the pixels.
            if (resized !== bitmap) resized.recycle()
        }

        val w = INPUT_SIZE.toFloat()
        val h = INPUT_SIZE.toFloat()

        val detections = ArrayList<Detection>(results.size)
        for (result in results) {
            val category = result.categories().firstOrNull() ?: continue
            val label = category.categoryName()
            if (label.isNullOrBlank()) continue

            val box = result.boundingBox()
            detections.add(
                Detection(
                    label = label,
                    confidence = category.score(),
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

    @Synchronized
    fun close() {
        objectDetector?.close()
        objectDetector = null
    }
}
