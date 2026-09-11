package com.example.sightbuddy.features.vision

import android.graphics.Color
import androidx.annotation.StringRes
import androidx.camera.core.ImageProxy
import com.example.sightbuddy.R

/**
 * Analyzes the centre region of a camera frame to determine the dominant colour.
 *
 * Samples a small centre patch directly from the YUV planes (no JPEG round-trip)
 * and takes the per-channel **median**, which is robust against mixed edges and
 * specular highlights that made the old mean-based version unreliable. The
 * sampled region matches the on-screen focus frame in Scan Colour mode.
 */
class ColorIdAnalyzer {

    /**
     * The name is a resource id, not text: the analyzer runs off the main thread
     * and has no Context, so the caller resolves it in the app language.
     */
    data class ColorResult(@StringRes val nameRes: Int, val hex: String)

    fun analyze(imageProxy: ImageProxy): ColorResult {
        val width = imageProxy.width
        val height = imageProxy.height
        // ~10% of the frame's shorter side, capped so median stays cheap.
        val patch = (minOf(width, height) / 10).coerceIn(8, 48)
        val startX = (width - patch) / 2
        val startY = (height - patch) / 2

        val yPlane = imageProxy.planes[0]
        val uPlane = imageProxy.planes[1]
        val vPlane = imageProxy.planes[2]
        val yBuf = yPlane.buffer
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer

        val n = patch * patch
        val rs = IntArray(n)
        val gs = IntArray(n)
        val bs = IntArray(n)
        var i = 0

        for (dy in 0 until patch) {
            val py = startY + dy
            for (dx in 0 until patch) {
                val px = startX + dx
                val yIdx = py * yPlane.rowStride + px * yPlane.pixelStride
                val uvIdx = (py / 2) * uPlane.rowStride + (px / 2) * uPlane.pixelStride
                val y = (yBuf.get(yIdx).toInt() and 0xFF)
                val u = (uBuf.get(uvIdx).toInt() and 0xFF) - 128
                val v = (vBuf.get(uvIdx).toInt() and 0xFF) - 128

                // BT.601 YUV -> RGB
                rs[i] = (y + 1.402f * v).toInt().coerceIn(0, 255)
                gs[i] = (y - 0.344136f * u - 0.714136f * v).toInt().coerceIn(0, 255)
                bs[i] = (y + 1.772f * u).toInt().coerceIn(0, 255)
                i++
            }
        }

        val r = median(rs)
        val g = median(gs)
        val b = median(bs)

        return ColorResult(mapToColorName(r, g, b), String.format("#%02X%02X%02X", r, g, b))
    }

    private fun median(values: IntArray): Int {
        values.sort()
        return values[values.size / 2]
    }

    @StringRes
    private fun mapToColorName(r: Int, g: Int, b: Int): Int {
        val hsv = FloatArray(3)
        Color.RGBToHSV(r, g, b, hsv)
        val hue = hsv[0]        // 0-360
        val sat = hsv[1]        // 0-1
        val value = hsv[2]      // 0-1

        // Achromatic first
        if (value < 0.13f) return R.string.colour_black
        if (value > 0.80f && sat < 0.18f) return R.string.colour_white
        if (sat < 0.15f) return R.string.colour_gray

        // Low-value / low-saturation warm hues read as brown or beige,
        // not orange — the old table had no brown at all.
        if (hue in 10f..50f) {
            if (value < 0.55f && sat > 0.2f) return R.string.colour_brown
            if (sat < 0.35f && value > 0.6f) return R.string.colour_beige
        }

        return when {
            hue < 15f -> R.string.colour_red
            hue < 40f -> R.string.colour_orange
            hue < 70f -> R.string.colour_yellow
            hue < 160f -> R.string.colour_green
            hue < 200f -> R.string.colour_cyan
            hue < 260f -> R.string.colour_blue
            hue < 290f -> R.string.colour_purple
            hue < 340f -> R.string.colour_pink
            else -> R.string.colour_red
        }
    }
}
