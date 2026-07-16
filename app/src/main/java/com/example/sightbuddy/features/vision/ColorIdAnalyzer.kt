package com.example.sightbuddy.features.vision

import android.graphics.Bitmap
import android.graphics.Color
import androidx.camera.core.ImageProxy

/**
 * Analyzes the center region of a camera frame to determine the dominant color.
 * Maps the detected color to a human-readable name for TTS feedback.
 */
class ColorIdAnalyzer {

    data class ColorResult(val name: String, val hex: String)

    /**
     * Analyze a camera frame and return the dominant color name.
     * Samples the center 10% of the image for accuracy.
     */
    fun analyze(imageProxy: ImageProxy): ColorResult {
        val bitmap = imageProxyToBitmap(imageProxy)
        val width = bitmap.width
        val height = bitmap.height

        // Sample the center 10% of the image
        val sampleSize = (minOf(width, height) * 0.1f).toInt().coerceAtLeast(1)
        val centerX = width / 2
        val centerY = height / 2
        val startX = (centerX - sampleSize / 2).coerceAtLeast(0)
        val startY = (centerY - sampleSize / 2).coerceAtLeast(0)
        val endX = (centerX + sampleSize / 2).coerceAtMost(width - 1)
        val endY = (centerY + sampleSize / 2).coerceAtMost(height - 1)

        var totalR = 0L
        var totalG = 0L
        var totalB = 0L
        var count = 0

        for (x in startX..endX) {
            for (y in startY..endY) {
                val pixel = bitmap.getPixel(x, y)
                totalR += Color.red(pixel)
                totalG += Color.green(pixel)
                totalB += Color.blue(pixel)
                count++
            }
        }

        bitmap.recycle()

        if (count == 0) return ColorResult("Unknown", "#000000")

        val avgR = (totalR / count).toInt()
        val avgG = (totalG / count).toInt()
        val avgB = (totalB / count).toInt()

        val colorName = mapToColorName(avgR, avgG, avgB)
        val hex = String.format("#%02X%02X%02X", avgR, avgG, avgB)
        return ColorResult(colorName, hex)
    }

    private fun mapToColorName(r: Int, g: Int, b: Int): String {
        val hsv = FloatArray(3)
        Color.RGBToHSV(r, g, b, hsv)
        val hue = hsv[0]        // 0-360
        val saturation = hsv[1] // 0-1
        val value = hsv[2]      // 0-1

        // Handle achromatic colors first
        if (value < 0.15f) return "Black"
        if (value > 0.85f && saturation < 0.15f) return "White"
        if (saturation < 0.15f) return "Gray"

        // Chromatic colors based on hue wheel
        return when {
            hue < 15f -> "Red"
            hue < 40f -> "Orange"
            hue < 70f -> "Yellow"
            hue < 160f -> "Green"
            hue < 200f -> "Cyan"
            hue < 260f -> "Blue"
            hue < 290f -> "Purple"
            hue < 340f -> "Pink"
            else -> "Red"
        }
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        val yBuffer = imageProxy.planes[0].buffer
        val uBuffer = imageProxy.planes[1].buffer
        val vBuffer = imageProxy.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = android.graphics.YuvImage(
            nv21,
            android.graphics.ImageFormat.NV21,
            imageProxy.width,
            imageProxy.height,
            null
        )
        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(
            android.graphics.Rect(0, 0, imageProxy.width, imageProxy.height),
            80,
            out
        )
        val bytes = out.toByteArray()
        return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}
