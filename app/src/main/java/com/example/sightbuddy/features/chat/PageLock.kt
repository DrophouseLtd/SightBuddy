package com.example.sightbuddy.features.chat

import com.example.sightbuddy.features.chat.PageCorners.Candidates
import com.example.sightbuddy.features.chat.PageCorners.Corner
import com.example.sightbuddy.features.chat.PageCorners.Corners

/**
 * Keeps the guidance on one page. On an open book the model
 * finds corners of both pages, and its best corners used to swap pages from
 * frame to frame, so the guide said left, right, left.
 *
 * With no page locked, it picks one ([PageCorners.choose]) and locks it.
 * Locked, each corner takes the peak nearest where it was, within a jump set
 * by the page's own size ([jumpFor]): less than the distance to the facing
 * page's same corner. A corner with none that near is not seen, even when the
 * facing page's corner is, and so is one that would make the corners no page. Corners not found move with the
 * ones that were, and are looked for again there. A corner never placed takes
 * the peak nearest the page that keeps it the right way round. If the locked
 * page has lost a corner and a whole page is in view that has at least
 * [PageCornerGuide.LOCK_RESYNC_CORNERS] corners where the locked ones are
 * (within the same jump), it takes that: the same page, found again whole.
 * Never the facing page, nor both pages as one, which share only two. A
 * locked page that has seen [PageCornerGuide.LOCK_STALE_MAX_SEEN] corners or
 * fewer for [PageCornerGuide.LOCK_STALE_MS] takes a whole page in view
 * whatever it shares: followed onto stray peaks, it had shrunk to a sliver
 * that no whole page matched, for 9 s with the page plainly in view (phone
 * test, 2026-10-31).
 * The page is let go once none of its corners has been seen for
 * [PageCornerGuide.LOCK_RELEASE_MS], and on [reset]. Pure: tested without a
 * camera.
 */
class PageLock(private val releaseMs: Long = PageCornerGuide.LOCK_RELEASE_MS) {

    /** Where each locked corner is, as fractions of the frame, or null when it has never been placed. */
    private var locked: List<Pair<Float, Float>?>? = null
    private var lastSeenAtMs = 0L
    /** Since when the locked page has seen too few corners, or null. */
    private var fewSinceMs: Long? = null

    /** How the last frame was matched, for the log: "pick", "track", "whole" or "none". */
    var mode = "none"
        private set

    val isLocked: Boolean get() = locked != null

    fun reset() {
        locked = null
        lastSeenAtMs = 0L
        fewSinceMs = null
        mode = "none"
    }

    /**
     * The locked page's corners in this frame. A corner not seen has
     * confidence 0, at the place it is expected (or the heatmap's highest point).
     */
    fun update(c: Candidates, nowMs: Long): Corners {
        if (locked != null && nowMs - lastSeenAtMs > releaseMs) reset()
        val current = locked
        val picked: List<Corner?>
        if (current == null) {
            val chosen = PageCorners.choose(c)
            picked = chosen?.corners?.map { it.takeIf { p -> p.confidence >= PageCornerGuide.CORNER_MIN_CONFIDENCE } }
                ?: List(4) { null }
            mode = if (chosen == null) "none" else "pick"
            if (picked.any { it != null }) locked = picked.map { p -> p?.let { it.x to it.y } }
        } else {
            val followed = follow(c, current)
            val whole = if (followed.count { it != null } < 4) PageCorners.choose(c) else null
            val jump = jumpFor(current, c.widthPx, c.heightPx)
            val shared = whole?.let { page ->
                (0 until 4).count { k ->
                    current[k]?.let { (x, y) ->
                        PageCorners.distance(page[k], Corner(x, y, 0f), c.widthPx, c.heightPx) <= jump
                    } == true
                }
            } ?: 0
            val few = followed.count { it != null } <= PageCornerGuide.LOCK_STALE_MAX_SEEN
            fewSinceMs = if (few) fewSinceMs ?: nowMs else null
            val stale = fewSinceMs?.let { nowMs - it >= PageCornerGuide.LOCK_STALE_MS } == true
            if (whole != null && whole.allSeen() && (shared >= PageCornerGuide.LOCK_RESYNC_CORNERS || stale)) {
                picked = whole.corners
                mode = "whole"
                locked = picked.map { it.x to it.y }
                fewSinceMs = null
            } else {
                picked = followed
                mode = "track"
                if (picked.any { it != null }) locked = moved(current, picked)
            }
        }
        if (picked.any { it != null }) lastSeenAtMs = nowMs
        val places = locked
        return Corners(
            (0 until 4).map { k ->
                picked[k] ?: places?.get(k)?.let { (x, y) -> Corner(x, y, 0f) } ?: c.peaks[k].first().copy(confidence = 0f)
            },
            c.widthPx,
            c.heightPx,
        )
    }

    companion object {
        /**
         * How far, as a share of the frame's diagonal, a corner of the page at
         * [reference] may move between frames: [PageCornerGuide.LOCK_JUMP_SHARE]
         * of the page's shorter side, so never as far as the facing page's same
         * corner, within [PageCornerGuide.LOCK_MIN_JUMP] and
         * [PageCornerGuide.LOCK_MAX_JUMP]. The largest when no side is known.
         */
        fun jumpFor(reference: List<Pair<Float, Float>?>, widthPx: Int, heightPx: Int): Float {
            fun side(i: Int, j: Int): Float? {
                val a = reference[i] ?: return null
                val b = reference[j] ?: return null
                return PageCorners.distance(Corner(a.first, a.second, 0f), Corner(b.first, b.second, 0f), widthPx, heightPx)
            }
            val width = listOfNotNull(side(PageCorners.TOP_LEFT, PageCorners.TOP_RIGHT), side(PageCorners.BOTTOM_LEFT, PageCorners.BOTTOM_RIGHT)).maxOrNull()
            val height = listOfNotNull(side(PageCorners.TOP_LEFT, PageCorners.BOTTOM_LEFT), side(PageCorners.TOP_RIGHT, PageCorners.BOTTOM_RIGHT)).maxOrNull()
            val shorter = listOfNotNull(width, height).minOrNull() ?: return PageCornerGuide.LOCK_MAX_JUMP
            return (shorter * PageCornerGuide.LOCK_JUMP_SHARE).coerceIn(PageCornerGuide.LOCK_MIN_JUMP, PageCornerGuide.LOCK_MAX_JUMP)
        }

        /**
         * Each corner of [c] that follows [reference]: the peak nearest its
         * place, within [maxJump] of the diagonal (by default [jumpFor] the
         * page); then the rest, at their places moved as the found ones moved;
         * then corners never placed, the peak nearest the page that keeps the
         * corners in order. If what is found is no page (out of order; or four
         * that are not convex, not page-shaped, or split by other peaks,
         * [PageCorners.splitByOtherPeaks]), the corner that moved furthest is dropped
         * until it is. Null where none.
         */
        fun follow(
            c: Candidates,
            reference: List<Pair<Float, Float>?>,
            maxJump: Float = jumpFor(reference, c.widthPx, c.heightPx),
            minConfidence: Float = PageCornerGuide.CORNER_MIN_CONFIDENCE,
        ): List<Corner?> {
            val candidates = c.peaks.map { ps -> ps.filter { it.confidence >= minConfidence } }
            fun nearest(k: Int, x: Float, y: Float): Corner? {
                val place = Corner(x, y, 0f)
                val best = candidates[k].minByOrNull { PageCorners.distance(it, place, c.widthPx, c.heightPx) } ?: return null
                return best.takeIf { PageCorners.distance(it, place, c.widthPx, c.heightPx) <= maxJump }
            }
            val picked = MutableList(4) { k -> reference[k]?.let { (x, y) -> nearest(k, x, y) } }
            val found = (0 until 4).filter { picked[it] != null }
            if (found.isEmpty()) return picked
            val dx = found.sumOf { (picked[it]!!.x - reference[it]!!.first).toDouble() }.toFloat() / found.size
            val dy = found.sumOf { (picked[it]!!.y - reference[it]!!.second).toDouble() }.toFloat() / found.size
            for (k in 0 until 4) {
                val place = reference[k]
                if (picked[k] == null && place != null) picked[k] = nearest(k, place.first + dx, place.second + dy)
            }
            val placed = picked.filterNotNull()
            val cx = placed.sumOf { it.x.toDouble() }.toFloat() / placed.size
            val cy = placed.sumOf { it.y.toDouble() }.toFloat() / placed.size
            val middle = Corner(cx, cy, 0f)
            for (k in 0 until 4) {
                if (picked[k] != null || reference[k] != null) continue
                picked[k] = candidates[k]
                    .sortedBy { PageCorners.distance(it, middle, c.widthPx, c.heightPx) }
                    .firstOrNull { p ->
                        val trial = picked.toMutableList().also { it[k] = p }
                        PageCorners.inOrder(trial) &&
                            (trial.any { it == null } || PageCorners.isConvex(Corners(trial.filterNotNull(), c.widthPx, c.heightPx)))
                    }
            }
            // How far each corner went from where it was expected; one never placed, furthest of all.
            fun drift(k: Int): Float {
                val p = picked[k] ?: return -1f
                val place = reference[k] ?: return Float.MAX_VALUE
                return PageCorners.distance(p, Corner(place.first + dx, place.second + dy, 0f), c.widthPx, c.heightPx)
            }
            while (!isPage(picked, c, minConfidence)) {
                val worst = (0 until 4).maxByOrNull { drift(it) } ?: break
                if (picked[worst] == null) break
                picked[worst] = null
            }
            return picked
        }

        /** Corners in order, and four of them a page with no other sure peak inside. */
        private fun isPage(q: List<Corner?>, c: Candidates, minConfidence: Float): Boolean {
            if (!PageCorners.inOrder(q)) return false
            if (q.any { it == null }) return true
            val page = Corners(q.filterNotNull(), c.widthPx, c.heightPx)
            if (!PageCorners.isConvex(page) || !PageCorners.pageShaped(page)) return false
            return !PageCorners.splitByOtherPeaks(page, c, minConfidence)
        }

        /** The locked places after a frame: where corners were found, the others moved with them. */
        private fun moved(before: List<Pair<Float, Float>?>, picked: List<Corner?>): List<Pair<Float, Float>?> {
            val matched = (0 until 4).filter { picked[it] != null && before[it] != null }
            val dx = if (matched.isEmpty()) 0f else matched.sumOf { (picked[it]!!.x - before[it]!!.first).toDouble() }.toFloat() / matched.size
            val dy = if (matched.isEmpty()) 0f else matched.sumOf { (picked[it]!!.y - before[it]!!.second).toDouble() }.toFloat() / matched.size
            return (0 until 4).map { k ->
                picked[k]?.let { it.x to it.y } ?: before[k]?.let { (x, y) -> (x + dx) to (y + dy) }
            }
        }
    }
}
