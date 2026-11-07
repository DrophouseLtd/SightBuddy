package com.example.sightbuddy.features.chat

import java.util.Locale
import kotlin.math.acos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The page-corner model's arithmetic, without Android: what goes in, what
 * comes out, and the page that comes of it. The model ([PageCornerModel])
 * is the MIT-licensed MobileNetV3 heatmap model from the Hugging Face Space
 * Document Corner Detector (credited in the licence notices).
 *
 * In: the frame resized so its longer side is [INPUT_SIZE], keeping its
 * shape, centred on black, RGB scaled to 0..1 and ImageNet-normalised,
 * channels first. The resize is OpenCV's `INTER_LINEAR` on 8-bit images, to
 * the bit, since that is how the model was checked on recorded frames.
 *
 * Out: four heatmaps, top-left, top-right, bottom-right, bottom-left. Each
 * corner is its heatmap's highest point, and how high that is says how sure
 * the model is. A corner out of view still gets a point, with a low value.
 */
object PageCorners {

    /** The model's input is this square. */
    const val INPUT_SIZE = 224

    /**
     * The longer side of the frame copy the model is given. The model takes
     * 224, so more would only cost time; the frames it was checked on were 960.
     */
    const val SOURCE_LONG_SIDE = 960

    const val TOP_LEFT = 0
    const val TOP_RIGHT = 1
    const val BOTTOM_RIGHT = 2
    const val BOTTOM_LEFT = 3

    /** Floats in the model's input, and in its output. */
    const val INPUT_LENGTH = 3 * INPUT_SIZE * INPUT_SIZE
    const val OUTPUT_LENGTH = 4 * INPUT_SIZE * INPUT_SIZE

    private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
    private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)

    /** Where a frame of [sourceWidth] x [sourceHeight] sits inside the input square. */
    data class Letterbox(
        val sourceWidth: Int,
        val sourceHeight: Int,
        val width: Int,
        val height: Int,
        val left: Int,
        val top: Int,
    )

    fun letterbox(sourceWidth: Int, sourceHeight: Int): Letterbox {
        val r = INPUT_SIZE.toDouble() / max(sourceWidth, sourceHeight)
        val w = (sourceWidth * r).toInt().coerceIn(1, INPUT_SIZE)
        val h = (sourceHeight * r).toInt().coerceIn(1, INPUT_SIZE)
        return Letterbox(sourceWidth, sourceHeight, w, h, (INPUT_SIZE - w) / 2, (INPUT_SIZE - h) / 2)
    }

    /**
     * The model's input from [pixels] (ARGB, row by row, the letterbox's source
     * size), written into [out].
     */
    fun input(pixels: IntArray, box: Letterbox, out: FloatArray = FloatArray(INPUT_LENGTH)): FloatArray {
        val plane = INPUT_SIZE * INPUT_SIZE
        // Black, normalised.
        for (c in 0 until 3) out.fill((0f - MEAN[c]) / STD[c], c * plane, (c + 1) * plane)
        val sw = box.sourceWidth
        val sh = box.sourceHeight
        val xs = taps(sw, box.width)
        val ys = taps(sh, box.height)
        val upper = IntArray(box.width * 3)
        val lower = IntArray(box.width * 3)
        for (y in 0 until box.height) {
            across(pixels, sw, ys.index[y], xs, upper)
            across(pixels, sw, minOf(ys.index[y] + 1, sh - 1), xs, lower)
            val b0 = ys.near[y]
            val b1 = ys.far[y]
            val at = (box.top + y) * INPUT_SIZE + box.left
            for (x in 0 until box.width) {
                for (c in 0 until 3) {
                    val i = 3 * x + c
                    // OpenCV's fixed point, to the bit.
                    val v = (((upper[i] shr 4) * b0) shr 16) + (((lower[i] shr 4) * b1) shr 16)
                    val level = ((v + 2) shr 2).coerceIn(0, 255)
                    out[c * plane + at + x] = (level / 255f - MEAN[c]) / STD[c]
                }
            }
        }
        return out
    }

    /** Row [y] of [pixels] resized across: R, G and B of each pixel in turn, weighed [WEIGHT_ONE] to one. */
    private fun across(pixels: IntArray, width: Int, y: Int, xs: Taps, out: IntArray) {
        val row = y * width
        for (x in xs.index.indices) {
            val left = pixels[row + xs.index[x]]
            val right = pixels[row + minOf(xs.index[x] + 1, width - 1)]
            for (c in 0 until 3) {
                val shift = 16 - 8 * c
                out[3 * x + c] = ((left shr shift) and 0xFF) * xs.near[x] + ((right shr shift) and 0xFF) * xs.far[x]
            }
        }
    }

    /** For each output pixel, the source pixel before it, and its weight and the next one's. */
    private class Taps(val index: IntArray, val near: IntArray, val far: IntArray)

    /**
     * Pixel centres aligned and the edges held, as OpenCV's `INTER_LINEAR`
     * does, its float rounding included.
     */
    private fun taps(src: Int, dst: Int): Taps {
        val scale = src.toDouble() / dst
        val index = IntArray(dst)
        val near = IntArray(dst)
        val far = IntArray(dst)
        for (d in 0 until dst) {
            var f = ((d + 0.5) * scale - 0.5).toFloat()
            var i = floor(f).toInt()
            f -= i
            if (i < 0) { i = 0; f = 0f }
            if (i >= src - 1) { i = src - 1; f = 0f }
            index[d] = i
            near[d] = Math.rint(((1f - f) * WEIGHT_ONE).toDouble()).toInt()
            far[d] = Math.rint((f * WEIGHT_ONE).toDouble()).toInt()
        }
        return Taps(index, near, far)
    }

    /** OpenCV's 11-bit resize weights. */
    private const val WEIGHT_ONE = 2048f

    /** One corner, as fractions of the frame's width and height, and how sure the model is of it. */
    data class Corner(val x: Float, val y: Float, val confidence: Float)

    /** The four corners of a frame [widthPx] by [heightPx], in the model's order. */
    data class Corners(val corners: List<Corner>, val widthPx: Int, val heightPx: Int) {
        operator fun get(i: Int): Corner = corners[i]
        fun seen(i: Int, minConfidence: Float = PageCornerGuide.CORNER_MIN_CONFIDENCE): Boolean =
            corners[i].confidence >= minConfidence
        fun seenCount(minConfidence: Float = PageCornerGuide.CORNER_MIN_CONFIDENCE): Int =
            corners.count { it.confidence >= minConfidence }
        fun allSeen(minConfidence: Float = PageCornerGuide.CORNER_MIN_CONFIDENCE): Boolean =
            seenCount(minConfidence) == 4
    }

    /** The corners in [heatmaps] (the model's output) for the frame [box] was made from. */
    fun decode(heatmaps: FloatArray, box: Letterbox): Corners {
        val plane = INPUT_SIZE * INPUT_SIZE
        val corners = (0 until 4).map { k ->
            var best = 0
            var bestValue = heatmaps[k * plane]
            for (i in 1 until plane) {
                val v = heatmaps[k * plane + i]
                if (v > bestValue) { bestValue = v; best = i }
            }
            val px = best % INPUT_SIZE
            val py = best / INPUT_SIZE
            Corner(
                x = (px - box.left).toFloat() / box.width,
                y = (py - box.top).toFloat() / box.height,
                confidence = bestValue,
            )
        }
        return Corners(corners, box.sourceWidth, box.sourceHeight)
    }

    /**
     * Each corner's places in a frame, best first: its heatmap's highest point
     * (in view or not), then, if there is one, the next highest peak the model
     * is sure enough of, far enough from the first. On an open book that is the
     * same corner of the facing page.
     */
    data class Candidates(val peaks: List<List<Corner>>, val widthPx: Int, val heightPx: Int) {
        /** Each corner's highest point, what [decode] gives. */
        val best: Corners get() = Corners(peaks.map { it.first() }, widthPx, heightPx)
    }

    /**
     * Up to two peaks per corner in [heatmaps]: the highest point, and the
     * highest local peak (no neighbour higher) at least [minConfidence] and
     * [minSeparation] of the diagonal from it.
     */
    fun peaks(
        heatmaps: FloatArray,
        box: Letterbox,
        minConfidence: Float = PageCornerGuide.CORNER_MIN_CONFIDENCE,
        minSeparation: Float = PageCornerGuide.PEAK_MIN_SEPARATION,
    ): Candidates {
        val plane = INPUT_SIZE * INPUT_SIZE
        val w = box.sourceWidth.toFloat()
        val h = box.sourceHeight.toFloat()
        val diagonal = hypot(w, h)
        fun at(i: Int, v: Float) = Corner(
            x = (i % INPUT_SIZE - box.left).toFloat() / box.width,
            y = (i / INPUT_SIZE - box.top).toFloat() / box.height,
            confidence = v,
        )
        val peaks = (0 until 4).map { k ->
            val base = k * plane
            var best = 0
            var bestValue = heatmaps[base]
            for (i in 1 until plane) {
                val v = heatmaps[base + i]
                if (v > bestValue) { bestValue = v; best = i }
            }
            val first = at(best, bestValue)
            var second: Corner? = null
            for (i in 0 until plane) {
                val v = heatmaps[base + i]
                if (v < minConfidence || (second != null && v <= second.confidence)) continue
                if (!isLocalPeak(heatmaps, base, i, v)) continue
                val c = at(i, v)
                if (hypot((c.x - first.x) * w, (c.y - first.y) * h) / diagonal < minSeparation) continue
                second = c
            }
            listOfNotNull(first, second)
        }
        return Candidates(peaks, box.sourceWidth, box.sourceHeight)
    }

    /** True when no neighbour of pixel [i] in the heatmap starting at [base] is higher than [v]. */
    private fun isLocalPeak(heatmaps: FloatArray, base: Int, i: Int, v: Float): Boolean {
        val x = i % INPUT_SIZE
        val y = i / INPUT_SIZE
        for (dy in -1..1) for (dx in -1..1) {
            if (dx == 0 && dy == 0) continue
            val nx = x + dx
            val ny = y + dy
            if (nx !in 0 until INPUT_SIZE || ny !in 0 until INPUT_SIZE) continue
            if (heatmaps[base + ny * INPUT_SIZE + nx] > v) return false
        }
        return true
    }

    /**
     * The page to lock onto when none is: of every way of taking one peak per
     * corner the model is sure of (a corner with none left out), the one with
     * the most corners, then the surest (corner confidences summed), then the
     * one whose middle is nearest the frame's: a page's sure corners beat weak
     * peaks inside it, from its text or pictures. Its
     * corners must lie the right way round, and four must make a page: convex,
     * no angle outside [PageCornerGuide.PAGE_MIN_ANGLE] to
     * [PageCornerGuide.PAGE_MAX_ANGLE], and nothing inside that splits it
     * ([splitByOtherPeaks]): an open book's two pages, outer corners only, are
     * not one page, since the gutter lies within. The highest points are one
     * of the ways tried, under the same checks. A corner left out keeps its
     * highest point, below the confidence that counts as seen. Null when none
     * is seen, or no way makes a page.
     */
    fun choose(c: Candidates, minConfidence: Float = PageCornerGuide.CORNER_MIN_CONFIDENCE): Corners? {
        val options = c.peaks.map { ps -> ps.filter { it.confidence >= minConfidence }.ifEmpty { listOf(null) } }
        val w = c.widthPx.toFloat()
        val h = c.heightPx.toFloat()
        var best: List<Corner?>? = null
        var bestSeen = 0
        var bestSure = 0f
        var bestDistance = 0f
        for (tl in options[0]) for (tr in options[1]) for (br in options[2]) for (bl in options[3]) {
            val q = listOf(tl, tr, br, bl)
            val seen = q.filterNotNull()
            if (seen.isEmpty() || !inOrder(q)) continue
            if (seen.size == 4) {
                val page = Corners(seen, c.widthPx, c.heightPx)
                if (!isConvex(page) || !pageShaped(page)) continue
                if (splitByOtherPeaks(page, c, minConfidence)) continue
            }
            val cx = seen.sumOf { it.x.toDouble() }.toFloat() / seen.size
            val cy = seen.sumOf { it.y.toDouble() }.toFloat() / seen.size
            val distance = hypot((cx - 0.5f) * w, (cy - 0.5f) * h)
            val sure = seen.sumOf { it.confidence.toDouble() }.toFloat()
            val better = seen.size > bestSeen ||
                (seen.size == bestSeen && (sure > bestSure || (sure == bestSure && distance < bestDistance)))
            if (better) {
                best = q
                bestSeen = seen.size
                bestSure = sure
                bestDistance = distance
            }
        }
        val chosen = best ?: return null
        return Corners(chosen.mapIndexed { k, p -> p ?: c.peaks[k].first() }, c.widthPx, c.heightPx)
    }

    /**
     * True when peaks of [c] other than [page]'s own corners show it is not
     * one page: a single peak inside it as sure as its weakest corner; or a
     * gutter, whatever its strength, which runs the page's height: a top
     * corner's peak near its top edge and a bottom corner's near its bottom
     * edge (within [PageCornerGuide.GUTTER_END_SHARE] of its height inside,
     * or [PageCornerGuide.GUTTER_EDGE_MARGIN] of the diagonal past), both
     * seen, between its sides, and one above the other, within
     * [PageCornerGuide.GUTTER_ALIGN] of its width across. A weak stray peak,
     * one at an edge with no partner, or peaks in the page's middle (its text
     * and pictures) do not count.
     */
    fun splitByOtherPeaks(page: Corners, c: Candidates, minConfidence: Float = PageCornerGuide.CORNER_MIN_CONFIDENCE): Boolean {
        val others = c.peaks.mapIndexed { k, ps ->
            ps.filter { it.confidence >= minConfidence && it !in page.corners }.map { k to it }
        }.flatten()
        val weakest = page.corners.minOf { it.confidence }
        if (others.any { (_, p) -> p.confidence >= weakest && contains(page, p) }) return true
        val tops = others.filter { (k, p) -> (k == TOP_LEFT || k == TOP_RIGHT) && nearEnd(page, p, top = true) }.map { it.second }
        val bottoms = others.filter { (k, p) -> (k == BOTTOM_LEFT || k == BOTTOM_RIGHT) && nearEnd(page, p, top = false) }.map { it.second }
        val width = max(page[TOP_RIGHT].x - page[TOP_LEFT].x, page[BOTTOM_RIGHT].x - page[BOTTOM_LEFT].x)
        return tops.any { t -> bottoms.any { b -> kotlin.math.abs(t.x - b.x) <= PageCornerGuide.GUTTER_ALIGN * width } }
    }

    /**
     * True when [p] lies between the page's left and right edges, near its
     * [top] edge (or bottom): no more than [PageCornerGuide.GUTTER_END_SHARE]
     * of the page's height inside it, nor [PageCornerGuide.GUTTER_EDGE_MARGIN]
     * of the diagonal past it.
     */
    private fun nearEnd(page: Corners, p: Corner, top: Boolean): Boolean {
        val w = page.widthPx.toFloat()
        val h = page.heightPx.toFloat()
        val diagonal = hypot(w, h)
        val height = max(
            hypot((page[TOP_LEFT].x - page[BOTTOM_LEFT].x) * w, (page[TOP_LEFT].y - page[BOTTOM_LEFT].y) * h),
            hypot((page[TOP_RIGHT].x - page[BOTTOM_RIGHT].x) * w, (page[TOP_RIGHT].y - page[BOTTOM_RIGHT].y) * h),
        )
        // Signed distance inside each edge, in pixels: top, right, bottom, left.
        val inside = (0 until 4).map { i ->
            val a = page[i]
            val b = page[(i + 1) % 4]
            val ex = (b.x - a.x) * w
            val ey = (b.y - a.y) * h
            (ex * (p.y - a.y) * h - ey * (p.x - a.x) * w) / hypot(ex, ey)
        }
        val fromEdge = if (top) inside[0] else inside[2]
        return inside[1] > 0f && inside[3] > 0f &&
            fromEdge >= -PageCornerGuide.GUTTER_EDGE_MARGIN * diagonal &&
            fromEdge <= PageCornerGuide.GUTTER_END_SHARE * height
    }

    /** True when [p] lies inside the convex page [c]. */
    fun contains(c: Corners, p: Corner): Boolean = (0 until 4).all { i ->
        val a = c[i]
        val b = c[(i + 1) % 4]
        (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x) > 0f
    }

    /** Corners the right way round: left ones left of right ones, top ones above bottom ones. */
    fun inOrder(q: List<Corner?>): Boolean {
        fun ok(a: Corner?, b: Corner?, f: (Corner, Corner) -> Boolean) = a == null || b == null || f(a, b)
        return ok(q[TOP_LEFT], q[TOP_RIGHT]) { a, b -> a.x < b.x } &&
            ok(q[BOTTOM_LEFT], q[BOTTOM_RIGHT]) { a, b -> a.x < b.x } &&
            ok(q[TOP_LEFT], q[BOTTOM_LEFT]) { a, b -> a.y < b.y } &&
            ok(q[TOP_RIGHT], q[BOTTOM_RIGHT]) { a, b -> a.y < b.y }
    }

    /**
     * Every corner's angle, on the screen, between [PageCornerGuide.PAGE_MIN_ANGLE]
     * and [PageCornerGuide.PAGE_MAX_ANGLE], and each edge no more than
     * [PageCornerGuide.PAGE_MAX_EDGE_RATIO] times the one opposite. A page at a
     * slant keeps well under that; corners mixed from both pages of a book
     * (one page's top, the other's outer bottom corner) passed the angles with
     * a bottom edge twice the top (phone test, 2026-10-31).
     */
    fun pageShaped(c: Corners): Boolean = edgesMatch(c) && (0 until 4).all { i ->
        val a = c[(i + 3) % 4]
        val b = c[i]
        val d = c[(i + 1) % 4]
        val ux = (a.x - b.x) * c.widthPx
        val uy = (a.y - b.y) * c.heightPx
        val vx = (d.x - b.x) * c.widthPx
        val vy = (d.y - b.y) * c.heightPx
        val lengths = hypot(ux, uy) * hypot(vx, vy)
        if (lengths == 0f) return@all false
        val degrees = Math.toDegrees(acos(((ux * vx + uy * vy) / lengths).coerceIn(-1f, 1f).toDouble())).toFloat()
        degrees in PageCornerGuide.PAGE_MIN_ANGLE..PageCornerGuide.PAGE_MAX_ANGLE
    }

    /** Each edge no more than [PageCornerGuide.PAGE_MAX_EDGE_RATIO] times the one opposite, in pixels. */
    private fun edgesMatch(c: Corners): Boolean {
        fun length(i: Int, j: Int) = hypot((c[i].x - c[j].x) * c.widthPx, (c[i].y - c[j].y) * c.heightPx)
        fun match(a: Float, b: Float) = minOf(a, b) > 0f && maxOf(a, b) / minOf(a, b) <= PageCornerGuide.PAGE_MAX_EDGE_RATIO
        return match(length(TOP_LEFT, TOP_RIGHT), length(BOTTOM_LEFT, BOTTOM_RIGHT)) &&
            match(length(TOP_LEFT, BOTTOM_LEFT), length(TOP_RIGHT, BOTTOM_RIGHT))
    }

    /** Distance between two corners of a frame [widthPx] by [heightPx], as a share of its diagonal. */
    fun distance(a: Corner, b: Corner, widthPx: Int, heightPx: Int): Float =
        hypot((a.x - b.x) * widthPx, (a.y - b.y) * heightPx) / hypot(widthPx.toFloat(), heightPx.toFloat())

    /** Where a capture's crop came from: the picture's own corners, the steady aim frame's, or none. */
    enum class CropSource { OWN, STEADY, NONE }

    /**
     * One page of an open book seen whole. The model can outline both pages of
     * a spread as one page, sure of all four corners (phone test, 2026-10-31),
     * so an outline whose four corners are seen and which is clearly wider
     * than tall ([SPREAD_ASPECT], in pixels) is taken for a spread, and the
     * half whose middle is nearest the frame's is kept, cut at the middles of
     * its top and bottom edges. A page held sideways is cut too: the price of
     * not looking for the gutter in the picture. Anything else as it is.
     */
    fun oneOfSpread(c: Corners): Corners {
        if (!c.allSeen()) return c
        val (tl, tr, br, bl) = c.corners
        fun length(a: Corner, b: Corner) = hypot((a.x - b.x) * c.widthPx, (a.y - b.y) * c.heightPx)
        val width = (length(tl, tr) + length(bl, br)) / 2f
        val height = (length(tl, bl) + length(tr, br)) / 2f
        if (height <= 0f || width / height < SPREAD_ASPECT) return c
        fun middle(a: Corner, b: Corner) = Corner((a.x + b.x) / 2f, (a.y + b.y) / 2f, minOf(a.confidence, b.confidence))
        val top = middle(tl, tr)
        val bottom = middle(bl, br)
        val left = listOf(tl, top, bottom, bl)
        val right = listOf(top, tr, br, bottom)
        fun offCentre(q: List<Corner>) = hypot(q.map { it.x }.average() - 0.5, q.map { it.y }.average() - 0.5)
        return Corners(if (offCentre(left) <= offCentre(right)) left else right, c.widthPx, c.heightPx)
    }

    /**
     * An outline this many times wider than tall is an open book. A page
     * upright read 0.5 to 0.7 on the phone; a spread of two, seen in
     * perspective, about 1.1 (1.15 missed it, 2026-10-31); a page held
     * sideways is about 1.4.
     */
    const val SPREAD_ASPECT = 0.95f

    /** The corners to crop to, and how far to grow them. */
    data class CropPlan(val corners: Corners?, val grow: Float, val source: CropSource)

    /**
     * Which corners a capture is cropped to, one page of an open book
     * ([oneOfSpread]) either way. Its own ([whole]) when all four are seen
     * and make a page. Otherwise the steady corners ([steadyWhole]) of the aim frame that
     * took the picture, if the picture sees at least
     * [PageCornerGuide.FALLBACK_MIN_SEEN] corners and each lies within
     * [PageCornerGuide.FALLBACK_MATCH] of its steady place: then grown
     * further, for the hand's movement. Otherwise none: the whole frame.
     */
    fun cropPlan(whole: Corners, steadyWhole: Corners?): CropPlan {
        // One page of an open book, whichever corners are used.
        val own = oneOfSpread(whole)
        val steady = steadyWhole?.let { oneOfSpread(it) }
        if (own.allSeen() && isConvex(own)) return CropPlan(own, PageCornerGuide.WARP_GROW, CropSource.OWN)
        if (steady != null && steady.allSeen() && isConvex(steady)) {
            val seen = (0 until 4).filter { own.seen(it) }
            val near = seen.all { distance(own[it], steady[it], own.widthPx, own.heightPx) <= PageCornerGuide.FALLBACK_MATCH }
            if (seen.size >= PageCornerGuide.FALLBACK_MIN_SEEN && near) {
                return CropPlan(steady, PageCornerGuide.FALLBACK_GROW, CropSource.STEADY)
            }
        }
        return CropPlan(null, 0f, CropSource.NONE)
    }

    /** Share of the frame inside the four corners, taken in order. */
    fun areaShare(c: Corners): Float {
        var twice = 0f
        for (i in 0 until 4) {
            val a = c[i]
            val b = c[(i + 1) % 4]
            twice += a.x * b.y - b.x * a.y
        }
        return kotlin.math.abs(twice) / 2f
    }

    /**
     * How far the corners moved between [a] and [b]: the furthest any one of
     * them went, as a share of the frame's diagonal.
     */
    fun movement(a: Corners, b: Corners): Float {
        val w = b.widthPx.toFloat()
        val h = b.heightPx.toFloat()
        val diagonal = hypot(w, h)
        return (0 until 4).maxOf { i -> hypot((a[i].x - b[i].x) * w, (a[i].y - b[i].y) * h) } / diagonal
    }

    /**
     * True when the corners, in order, go round clockwise on the screen without
     * crossing: a page, not a bow tie or a line.
     */
    fun isConvex(c: Corners): Boolean = (0 until 4).all { i ->
        val a = c[i]
        val b = c[(i + 1) % 4]
        val d = c[(i + 2) % 4]
        (b.x - a.x) * (d.y - b.y) - (b.y - a.y) * (d.x - b.x) > 0f
    }

    /**
     * The corners moved [grow] of the way further from their middle, and held
     * inside the frame: the model finds a corner to a few pixels, and cutting
     * into the page would cut its text.
     */
    fun grown(c: Corners, grow: Float = PageCornerGuide.WARP_GROW): Corners {
        val cx = c.corners.sumOf { it.x.toDouble() }.toFloat() / 4f
        val cy = c.corners.sumOf { it.y.toDouble() }.toFloat() / 4f
        return c.copy(corners = c.corners.map {
            it.copy(
                x = (cx + (it.x - cx) * (1f + grow)).coerceIn(0f, 1f),
                y = (cy + (it.y - cy) * (1f + grow)).coerceIn(0f, 1f),
            )
        })
    }

    /** The corners' confidences, top-left first, and the page's share of the frame, for the log. */
    fun summary(c: Corners): String =
        "conf=" + c.corners.joinToString(",") { "%.2f".format(Locale.US, it.confidence) } +
            " area=" + "%.2f".format(Locale.US, areaShare(c))

    /** Where each seen corner of [c] is, as fractions across and down, for the log: "TL 0.12,0.30 TR - ...". */
    fun positions(c: Corners, minConfidence: Float = PageCornerGuide.CORNER_MIN_CONFIDENCE): String =
        listOf("TL", "TR", "BR", "BL").mapIndexed { k, name ->
            val p = c[k]
            if (p.confidence >= minConfidence) "$name %.2f,%.2f".format(Locale.US, p.x, p.y) else "$name -"
        }.joinToString(" ")

    /** The size of the flattened page. */
    data class Size(val width: Int, val height: Int)

    /**
     * How big the page inside [c] comes out flattened from a frame [frameWidth]
     * by [frameHeight]: as wide as its longer top or bottom edge and as tall as
     * its longer side, in that frame's pixels, its longer side no more than the
     * frame's. Null for a page too small to be worth reading on its own.
     */
    fun warpSize(c: Corners, frameWidth: Int, frameHeight: Int): Size? {
        fun edge(i: Int, j: Int) = hypot((c[i].x - c[j].x) * frameWidth, (c[i].y - c[j].y) * frameHeight)
        var w = max(edge(TOP_LEFT, TOP_RIGHT), edge(BOTTOM_LEFT, BOTTOM_RIGHT))
        var h = max(edge(TOP_LEFT, BOTTOM_LEFT), edge(TOP_RIGHT, BOTTOM_RIGHT))
        val longest = max(frameWidth, frameHeight).toFloat()
        if (max(w, h) > longest) {
            val s = longest / max(w, h)
            w *= s
            h *= s
        }
        val size = Size(w.roundToInt(), h.roundToInt())
        return size.takeIf { minOf(it.width, it.height) >= PageCornerGuide.WARP_MIN_SIDE_PX }
    }
}
