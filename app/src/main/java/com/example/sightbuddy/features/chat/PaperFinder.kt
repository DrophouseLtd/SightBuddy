package com.example.sightbuddy.features.chat

import com.example.sightbuddy.features.chat.LocalTextExtractor.TextFrame

/**
 * Finds the sheet of paper in view, which aiming goes by before the text.
 *
 * Paper is the bright part of the picture. On a tiny grey copy of the frame
 * the brightness that best splits the picture in two is found (Otsu's method),
 * and the largest connected bright area is taken as the paper; the dark print
 * on it does not break it up, since the margins and the gaps between lines
 * connect it all.
 *
 * On the recorded magazine sessions the usual document-scanner approach, a
 * four-cornered outline from edge detection, found a page in 2 frames of 44:
 * hands, the spread and pages running out of view break the corners. The
 * bright area followed the magazine in most of them.
 *
 * It comes before the text when it looks like a sheet, see [choose]; close
 * up, the paper fills the view and its shape is unknown, and the text decides.
 */
object PaperFinder {

    data class Paper(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        /** Share of the frame it covers. */
        val share: Float,
        /** Share of its box it fills: a sheet fills most of it, a floor does not. */
        val solidity: Float,
    )

    /** [luma] is [width] x [height] brightness values, 0 to 255, row by row. */
    fun find(luma: IntArray, width: Int, height: Int): Paper? {
        if (luma.isEmpty()) return null
        val threshold = otsu(luma)
        val seen = BooleanArray(luma.size)
        val queue = IntArray(luma.size)
        var best: Paper? = null
        var bestCount = 0
        for (start in luma.indices) {
            if (seen[start] || luma[start] <= threshold) continue
            var head = 0
            var tail = 0
            queue[tail++] = start
            seen[start] = true
            var x0 = width
            var y0 = height
            var x1 = 0
            var y1 = 0
            while (head < tail) {
                val i = queue[head++]
                val x = i % width
                val y = i / width
                if (x < x0) x0 = x
                if (x > x1) x1 = x
                if (y < y0) y0 = y
                if (y > y1) y1 = y
                if (x > 0) tail = visit(i - 1, luma, threshold, seen, queue, tail)
                if (x < width - 1) tail = visit(i + 1, luma, threshold, seen, queue, tail)
                if (y > 0) tail = visit(i - width, luma, threshold, seen, queue, tail)
                if (y < height - 1) tail = visit(i + width, luma, threshold, seen, queue, tail)
            }
            if (tail > bestCount) {
                bestCount = tail
                val boxArea = (x1 - x0 + 1) * (y1 - y0 + 1)
                best = Paper(
                    left = x0.toFloat() / width,
                    top = y0.toFloat() / height,
                    right = (x1 + 1).toFloat() / width,
                    bottom = (y1 + 1).toFloat() / height,
                    share = tail.toFloat() / luma.size,
                    solidity = tail.toFloat() / boxArea,
                )
            }
        }
        return best
    }

    private fun visit(i: Int, luma: IntArray, threshold: Int, seen: BooleanArray, queue: IntArray, tail: Int): Int {
        if (seen[i] || luma[i] <= threshold) return tail
        seen[i] = true
        queue[tail] = i
        return tail + 1
    }

    /** The brightness that best splits [luma] into two groups. */
    fun otsu(luma: IntArray): Int {
        val hist = IntArray(256)
        for (v in luma) hist[v.coerceIn(0, 255)]++
        val total = luma.size.toDouble()
        var sumAll = 0.0
        for (t in 0..255) sumAll += t * hist[t].toDouble()
        var weightBack = 0.0
        var sumBack = 0.0
        var bestVariance = -1.0
        var threshold = 127
        for (t in 0..255) {
            weightBack += hist[t]
            if (weightBack == 0.0) continue
            val weightFore = total - weightBack
            if (weightFore == 0.0) break
            sumBack += t * hist[t].toDouble()
            val meanBack = sumBack / weightBack
            val meanFore = (sumAll - sumBack) / weightFore
            val variance = weightBack * weightFore * (meanBack - meanFore) * (meanBack - meanFore)
            if (variance > bestVariance) {
                bestVariance = variance
                threshold = t
            }
        }
        return threshold
    }

    /**
     * What the guide steers by. The paper comes first: a sheet shaped about
     * like A4 upright (height 1.15 to 1.75 times the width, A4 being 1.41),
     * solid and at least 8% of the frame, is the target, pictures and margins
     * included, even with part of it out of view, so the guide can say which
     * way it runs off. How readable it is comes from the text on it; with none
     * readable yet the guide asks for closer. A sheet touching opposite edges
     * is bigger than the view and its shape is not known, so it is not used;
     * nor is a spread, which is wider than tall. Without a sheet, the text
     * pages decide ([text]). [frameAspect] is the frame's width over height.
     */
    fun choose(text: TextFrame?, paper: Paper?, frameAspect: Float, blocks: List<PageTracker.Block>): Choice {
        val sheet = paper?.takeIf { isSheet(it, frameAspect) } ?: return Choice(text, paperUsed = false)
        val onSheet = blocks.filter { b ->
            b.text.count { it.isLetter() } >= MIN_LETTERS &&
                (b.left + b.right) / 2f in sheet.left..sheet.right &&
                (b.top + b.bottom) / 2f in sheet.top..sheet.bottom
        }
        val heights = onSheet.map { it.lineHeightPx }.sorted()
        return Choice(
            TextFrame(
                sheet.left, sheet.top, sheet.right, sheet.bottom,
                chars = onSheet.sumOf { it.chars },
                // Nothing readable on it yet: too small, so the guide asks for closer.
                lineHeightPx = heights.getOrElse(heights.size / 2) { 1 },
            ),
            paperUsed = true,
        )
    }

    fun isSheet(p: Paper, frameAspect: Float): Boolean {
        val acrossWidth = p.left < EDGE && p.right > 1f - EDGE
        val acrossHeight = p.top < EDGE && p.bottom > 1f - EDGE
        if (acrossWidth || acrossHeight) return false
        if (p.share < MIN_SHARE || p.solidity < MIN_SOLIDITY) return false
        val heightOverWidth = (p.bottom - p.top) / ((p.right - p.left) * frameAspect).coerceAtLeast(1e-3f)
        return heightOverWidth in MIN_SHEET_RATIO..MAX_SHEET_RATIO
    }

    data class Choice(val target: TextFrame?, val paperUsed: Boolean)

    /** Size of the grey copy the paper is looked for in. */
    const val WIDTH = 120
    const val HEIGHT = 160

    /** Paper this close to the frame's edge may carry on past it. */
    private const val EDGE = 0.02f
    private const val MIN_SHARE = 0.08f
    private const val MIN_SOLIDITY = 0.6f
    /** Height over width of a sheet about A4 upright (1.41), allowing for tilt and perspective. */
    const val MIN_SHEET_RATIO = 1.15f
    const val MAX_SHEET_RATIO = 1.75f
    /** A block with fewer letters than this says nothing about readability. */
    private const val MIN_LETTERS = 3
}
