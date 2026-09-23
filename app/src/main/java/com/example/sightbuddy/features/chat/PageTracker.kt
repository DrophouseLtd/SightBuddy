package com.example.sightbuddy.features.chat

import com.example.sightbuddy.features.chat.LocalTextExtractor.TextFrame
import kotlin.math.max
import kotlin.math.min

/**
 * The text fallback for aiming: finds the pages in the text the recogniser
 * sees, for when no sheet of paper is found (see [PaperFinder], which comes
 * first).
 *
 * The recogniser separates titles and sections well, but a section on its own
 * is too small to aim at, so the sections are put together into pages:
 *
 *  1. Blocks that are not text to read are dropped: a character or two, mostly
 *     digits and symbols, text printed sideways, or lines too tall to be print.
 *  2. The rest are grouped loosely, blocks within a few line heights of each
 *     other; on an open magazine that is often both pages as one region.
 *  3. A printed page is taller than it is wide, so a region wider than tall is
 *     split at the empty strip between blocks that leaves the two most
 *     page-shaped halves: the gutter, not a gap between columns. Each half is
 *     split again if it is still too wide.
 *  4. The largest page in the frame is the target. Its edges are smoothed
 *     while it stays in about the same place, and it is held for [holdMs]
 *     through frames that find it missing or with much less text than just
 *     before, which is the recogniser missing it, not the page leaving.
 *
 * An earlier version locked on to a page and recognised it again by its
 * words; it is gone, the paper does that job more simply.
 *
 * Pure geometry and timing, so it is tested by replaying recorded sessions.
 */
class PageTracker(
    /** How long a page is held while it cannot be seen. */
    private val holdMs: Long = 1_500L,
    /** A frame with less than this share of the recent most text is a miss. */
    private val dropoutShare: Float = 0.35f,
    /** Weight of the new frame when smoothing the page's edges. */
    private val smoothing: Float = 0.5f,
) {
    /** One recognised block, as fractions of the frame. */
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

    data class Result(
        /** The page to steer by, or null when there is none. */
        val target: TextFrame?,
        /** Indices of the blocks taken as text. */
        val kept: Set<Int>,
        /** Indices of the blocks in the target page this frame. */
        val chosen: Set<Int>,
        /** Every page found this frame. */
        val pages: List<TextFrame> = emptyList(),
        /** True when [target] is the page held while it is not seen. */
        val held: Boolean,
    )

    private var target: TextFrame? = null
    private var targetAtMs = 0L
    private val recentChars = ArrayDeque<Pair<Long, Int>>()

    fun reset() {
        target = null
        targetAtMs = 0L
        recentChars.clear()
    }

    fun update(blocks: List<Block>, frameWidthPx: Int, frameHeightPx: Int, nowMs: Long): Result {
        val kept = blocks.indices.filter { isText(blocks[it], frameWidthPx, frameHeightPx) }
        val pages = group(kept, blocks, frameWidthPx, frameHeightPx)
            .flatMap { splitIntoPages(it, blocks, frameWidthPx, frameHeightPx, depth = 0) }
            .map { it to boxOf(it, blocks) }
        val boxes = pages.map { it.second }
        val previous = target?.takeIf { nowMs - targetAtMs <= holdMs }
        val best = pages.filter { it.second.chars >= MIN_PAGE_CHARS }.maxByOrNull { area(it.second) }

        while (recentChars.isNotEmpty() && nowMs - recentChars.first().first > holdMs) recentChars.removeFirst()
        val recentMost = recentChars.maxOfOrNull { it.second } ?: 0
        val chars = best?.second?.chars ?: 0
        recentChars.addLast(nowMs to chars)

        if (previous != null && (best == null || chars < recentMost * dropoutShare)) {
            return Result(previous, kept.toSet(), emptySet(), boxes, held = true)
        }
        if (best == null) {
            target = null
            return Result(null, kept.toSet(), emptySet(), boxes, held = false)
        }
        val box = if (previous != null && overlap(best.second, previous) > 0f) blend(previous, best.second) else best.second
        target = box
        targetAtMs = nowMs
        return Result(box, kept.toSet(), best.first.toSet(), boxes, held = false)
    }

    private fun isText(block: Block, frameWidthPx: Int, frameHeightPx: Int): Boolean {
        val visible = block.text.filterNot { it.isWhitespace() }
        if (visible.length < MIN_CHARS) return false
        // Codes, prices and chart numbers read as text are mostly digits and symbols.
        if (visible.count { it.isLetter() }.toFloat() / visible.length < MIN_LETTER_SHARE) return false
        // Text printed sideways, along a spine or an edge: its "lines" are taller
        // than the block is wide, which a line of two or more letters never is.
        if (block.lineHeightPx > (block.right - block.left) * frameWidthPx) return false
        // A line this tall is not print held at reading distance.
        return block.lineHeightPx <= frameHeightPx * MAX_LINE_SHARE
    }

    /** Blocks within [REGION_GAP_LINES] line heights of each other form one region. */
    private fun group(indices: List<Int>, blocks: List<Block>, widthPx: Int, heightPx: Int): List<List<Int>> {
        val parent = IntArray(indices.size) { it }
        fun find(i: Int): Int {
            var r = i
            while (parent[r] != r) r = parent[r]
            return r
        }
        for (i in indices.indices) for (j in i + 1 until indices.size) {
            val a = blocks[indices[i]]
            val b = blocks[indices[j]]
            val reachPx = REGION_GAP_LINES * max(a.lineHeightPx, b.lineHeightPx)
            val gapX = (max(a.left, b.left) - min(a.right, b.right)) * widthPx
            val gapY = (max(a.top, b.top) - min(a.bottom, b.bottom)) * heightPx
            if (gapX <= reachPx && gapY <= reachPx) parent[find(i)] = find(j)
        }
        return indices.indices.groupBy { find(it) }.values.map { members -> members.map { indices[it] } }
    }

    /**
     * Splits a region wider than a page at the gap that leaves the most
     * page-shaped halves, and those again while they are too wide.
     */
    private fun splitIntoPages(
        region: List<Int>,
        blocks: List<Block>,
        widthPx: Int,
        heightPx: Int,
        depth: Int,
    ): List<List<Int>> {
        if (depth >= MAX_SPLITS || aspect(region, blocks, widthPx, heightPx) <= MAX_PAGE_ASPECT) return listOf(region)
        val chars = region.sumOf { blocks[it].chars }
        var best: Triple<Float, List<Int>, List<Int>>? = null
        for (cut in gapsIn(region, blocks)) {
            val (left, right) = region.partition { (blocks[it].left + blocks[it].right) / 2f < cut }
            val smaller = min(left.sumOf { blocks[it].chars }, right.sumOf { blocks[it].chars })
            if (smaller < chars * MIN_PAGE_SHARE) continue
            val score = max(aspect(left, blocks, widthPx, heightPx), aspect(right, blocks, widthPx, heightPx))
            if (best == null || score < best.first) best = Triple(score, left, right)
        }
        val split = best ?: return listOf(region)
        return splitIntoPages(split.second, blocks, widthPx, heightPx, depth + 1) +
            splitIntoPages(split.third, blocks, widthPx, heightPx, depth + 1)
    }

    /** The middles of the empty vertical strips between a region's blocks. */
    private fun gapsIn(region: List<Int>, blocks: List<Block>): List<Float> {
        val spans = region.map { blocks[it].left to blocks[it].right }.sortedBy { it.first }
        val cuts = mutableListOf<Float>()
        var reach = spans.first().second
        for ((left, right) in spans.drop(1)) {
            if (left - reach >= MIN_GUTTER) cuts += (left + reach) / 2f
            reach = max(reach, right)
        }
        return cuts
    }

    /** Width over height, in pixels. */
    private fun aspect(region: List<Int>, blocks: List<Block>, widthPx: Int, heightPx: Int): Float {
        val box = boxOf(region, blocks)
        return (box.right - box.left) * widthPx / ((box.bottom - box.top) * heightPx).coerceAtLeast(1f)
    }

    private fun boxOf(region: List<Int>, blocks: List<Block>): TextFrame {
        val members = region.map { blocks[it] }
        val heights = members.map { it.lineHeightPx }.sorted()
        return TextFrame(
            left = members.minOf { it.left },
            top = members.minOf { it.top },
            right = members.maxOf { it.right },
            bottom = members.maxOf { it.bottom },
            chars = members.sumOf { it.chars },
            lineHeightPx = heights[heights.size / 2],
        )
    }

    private fun area(f: TextFrame): Float = (f.right - f.left).coerceAtLeast(0f) * (f.bottom - f.top).coerceAtLeast(0f)

    private fun overlap(a: TextFrame, b: TextFrame): Float =
        (min(a.right, b.right) - max(a.left, b.left)).coerceAtLeast(0f) *
            (min(a.bottom, b.bottom) - max(a.top, b.top)).coerceAtLeast(0f)

    private fun blend(old: TextFrame, new: TextFrame): TextFrame {
        fun mix(o: Float, n: Float) = o + (n - o) * smoothing
        return new.copy(
            left = mix(old.left, new.left),
            top = mix(old.top, new.top),
            right = mix(old.right, new.right),
            bottom = mix(old.bottom, new.bottom),
        )
    }

    companion object {
        const val MIN_CHARS = 2
        const val MIN_LETTER_SHARE = 0.5f
        /** Tallest line, as a share of the frame height, still taken as print. */
        const val MAX_LINE_SHARE = 0.15f
        /** Blocks further apart than this many line heights are separate regions. */
        const val REGION_GAP_LINES = 3f
        /** A region wider than tall by more than this is taken to be more than one page. */
        const val MAX_PAGE_ASPECT = 1f
        /** The narrowest empty strip, as a share of the frame width, that can be a gutter. */
        const val MIN_GUTTER = 0.012f
        /** Each side of a split must hold at least this share of the region's text. */
        const val MIN_PAGE_SHARE = 0.15f
        const val MAX_SPLITS = 3
        /** The least text worth aiming at. */
        const val MIN_PAGE_CHARS = 40
    }
}
