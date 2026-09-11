package com.example.sightbuddy.features.vision

import androidx.annotation.StringRes
import androidx.camera.core.ImageProxy
import com.example.sightbuddy.R

/**
 * Analyzes camera frame luminance by reading the Y-plane directly.
 * Returns a 0-100 percentage representing ambient light level.
 */
class LightLevelAnalyzer {

    /**
     * The description is a resource id, not text: the analyzer runs off the main
     * thread and has no Context, so the caller resolves it in the app language.
     */
    data class LightResult(val percentage: Int, @StringRes val descriptionRes: Int)

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

        if (count == 0) return LightResult(0, R.string.light_unknown)

        val avgLuminance = totalLuminance / count // 0-255
        val percentage = ((avgLuminance / 255.0) * 100).toInt().coerceIn(0, 100)

        val description = when {
            percentage < 10 -> R.string.light_very_dark
            percentage < 25 -> R.string.light_dark
            percentage < 45 -> R.string.light_dim
            percentage < 60 -> R.string.light_moderate
            percentage < 75 -> R.string.light_bright
            percentage < 90 -> R.string.light_very_bright
            else -> R.string.light_extremely_bright
        }

        return LightResult(percentage, description)
    }
}
