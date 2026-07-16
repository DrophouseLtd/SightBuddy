package com.example.sightbuddy.features.vision

import androidx.camera.core.ImageProxy

/**
 * Analyzes camera frame luminance by reading the Y-plane directly.
 * Returns a 0-100 percentage representing ambient light level.
 */
class LightLevelAnalyzer {

    data class LightResult(val percentage: Int, val description: String)

    /**
     * Analyze the Y (luminance) plane of a YUV_420_888 ImageProxy.
     * Returns a LightResult with 0-100 scale and a text description.
     */
    fun analyze(imageProxy: ImageProxy): LightResult {
        val yPlane = imageProxy.planes[0]
        val buffer = yPlane.buffer
        val ySize = buffer.remaining()

        // Sample every 16th byte for speed (no need to read every pixel)
        var totalLuminance = 0L
        var count = 0
        val step = 16

        for (i in 0 until ySize step step) {
            totalLuminance += (buffer.get(i).toInt() and 0xFF)
            count++
        }

        // Reset buffer position
        buffer.rewind()

        if (count == 0) return LightResult(0, "Unknown")

        val avgLuminance = totalLuminance / count // 0-255
        val percentage = ((avgLuminance / 255.0) * 100).toInt().coerceIn(0, 100)

        val description = when {
            percentage < 10 -> "Very dark"
            percentage < 25 -> "Dark"
            percentage < 45 -> "Dim"
            percentage < 60 -> "Moderate light"
            percentage < 75 -> "Bright"
            percentage < 90 -> "Very bright"
            else -> "Extremely bright"
        }

        return LightResult(percentage, description)
    }
}
