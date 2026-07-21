package com.example.sightbuddy.features.vision

import android.graphics.Color
import androidx.camera.core.ImageProxy

/**
 * Analyzes the centre region of a camera frame to determine the dominant colour.
 *
 * Samples a small centre patch directly from the YUV planes (no JPEG round-trip)
 * and takes the per-channel **median**, which is robust against mixed edges and
 * specular highlights that made the old mean-based version unreliable. The
 * sampled region matches the on-screen focus frame in Scan Colour mode.
 */
class ColorIdAnalyzer {

    data class ColorResult(val name: String, val hex: String)

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

    private fun mapToColorName(r: Int, g: Int, b: Int): String {
        val hsv = FloatArray(3)
        Color.RGBToHSV(r, g, b, hsv)
        val hue = hsv[0]        // 0-360
        val sat = hsv[1]        // 0-1
        val value = hsv[2]      // 0-1

        // Achromatic first
        if (value < 0.13f) return "Black"
        if (value > 0.80f && sat < 0.18f) return "White"
        if (sat < 0.15f) return "Gray"

        // Low-value / low-saturation warm hues read as brown or beige,
        // not orange — the old table had no brown at all.
        if (hue in 10f..50f) {
            if (value < 0.55f && sat > 0.2f) return "Brown"
            if (sat < 0.35f && value > 0.6f) return "Beige"
        }

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
}
