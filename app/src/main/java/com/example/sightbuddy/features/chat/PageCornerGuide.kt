package com.example.sightbuddy.features.chat

/**
 * Find page's guidance by the page's four corners, with no directions: the
 * model's corner confidences jump too much from frame to frame for left,
 * right, up, down or further to be right (phone test, 2026-10-30).
 *
 * Fed the corners of the page [PageLock] keeps to in each frame, it answers
 * with one step: all four corners in view but the page small, closer; all four
 * in view and big enough, hold steady; then the picture, once the corners have
 * kept still for [steadyFrames] frames. No corner for a while: no text visible.
 * Otherwise nothing is said: [found], how much of the page is found, is played
 * as a rising tone instead. A change of what is said needs a second agreeing
 * frame. It guides until the page is taken, or until the user presses Capture.
 *
 * It starts optimistic: every threshold is below, to tighten from the phone's
 * log (tag `PAGE-CORNERS`) if pictures come out badly.
 */
class PageCornerGuide(
    private val cueRepeatMs: Long = 1_500L,
    private val noTextRepeatMs: Long = 4_000L,
    /** How long no corner must be seen before "no text" is said. */
    private val noTextAfterMs: Long = 1_500L,
    private val steadyFrames: Int = STEADY_FRAMES,
) {
    /** What to do after a frame: nothing, say something, or take the picture. */
    sealed interface Step {
        data object Silent : Step
        data object NoText : Step
        data class Cue(val direction: Direction) : Step
        data object Capture : Step
    }

    /** What a cue says: closer, or hold steady (the page centred and big enough). */
    enum class Direction { CLOSER, CENTRE }

    /** What the corners say in a frame. */
    enum class State {
        /** Some corners in view, not all: the tone guides. */
        PARTIAL,
        /** All four, the page small. */
        CLOSER,
        /** All four, the page big enough. */
        READY,
    }

    /** How much of the page is found, 0 to 1, smoothed: the tone's pitch. */
    var found = 0f
        private set

    private var lastState: State? = null
    private var pendingState: State? = null
    private var lastCueAtMs = 0L
    private var lastNoTextAtMs = Long.MIN_VALUE / 2
    private var noTextSinceMs: Long? = null
    /** The corners at the last frame since "hold steady", and how many frames they have kept still. */
    private var steadyCorners: PageCorners.Corners? = null
    private var steadyCount = 0

    fun reset() {
        found = 0f
        lastState = null
        pendingState = null
        lastCueAtMs = 0L
        lastNoTextAtMs = Long.MIN_VALUE / 2
        noTextSinceMs = null
        steadyCorners = null
        steadyCount = 0
    }

    fun next(corners: PageCorners.Corners?, nowMs: Long): Step {
        found += (score(corners) - found) * TONE_SMOOTHING
        val state = corners?.let { stateFor(it) }
        if (state == null || corners == null) {
            // Not at once, even at the start: the first frames often have no page
            // locked yet while the model sees one (phone test, 2026-10-31).
            val since = noTextSinceMs ?: nowMs
            noTextSinceMs = since
            if (nowMs - since < noTextAfterMs) return Step.Silent
            lastState = null
            pendingState = null
            steadyCorners = null
            steadyCount = 0
            if (nowMs - lastNoTextAtMs < noTextRepeatMs) return Step.Silent
            lastNoTextAtMs = nowMs
            return Step.NoText
        }
        noTextSinceMs = null
        if (lastState != null && state != lastState && state != pendingState) {
            // One odd frame neither changes what is said nor ends a steady hold.
            pendingState = state
            return Step.Silent
        }
        pendingState = null
        return when (state) {
            State.READY -> {
                if (lastState != State.READY) {
                    lastState = State.READY
                    lastCueAtMs = nowMs
                    steadyCorners = corners
                    steadyCount = 0
                    return Step.Cue(Direction.CENTRE)
                }
                val before = steadyCorners
                steadyCount = if (before != null && PageCorners.movement(before, corners) <= STEADY_MOVE) steadyCount + 1 else 0
                steadyCorners = corners
                if (steadyCount >= steadyFrames) {
                    reset()
                    return Step.Capture
                }
                return Step.Silent
            }
            State.CLOSER -> {
                steadyCorners = null
                steadyCount = 0
                if (lastState != State.CLOSER || nowMs - lastCueAtMs >= cueRepeatMs) {
                    lastState = State.CLOSER
                    lastCueAtMs = nowMs
                    return Step.Cue(Direction.CLOSER)
                }
                return Step.Silent
            }
            State.PARTIAL -> {
                lastState = State.PARTIAL
                steadyCorners = null
                steadyCount = 0
                return Step.Silent
            }
        }
    }

    companion object {
        /** A corner is in view when the model is at least this sure of it (0.6-0.9 seen for real corners, 0.1-0.2 for ones out of view). */
        const val CORNER_MIN_CONFIDENCE = 0.3f

        /**
         * All four corners in view, the page should cover at least this share of
         * the frame. Tried on the phone: at 0.2 a book page's letters were too
         * small to read well; 0.275 and 0.325 let the phone stay back far
         * enough to see both pages of a book whole, and the corners mixed
         * across them. 0.35 worked best (decided 2026-10-31).
         */
        const val MIN_PAGE_AREA = 0.35f

        /** Corners that moved less than this share of the frame's diagonal kept still. */
        const val STEADY_MOVE = 0.04f

        /** Frames in a row the corners must keep still after "hold steady" before the picture. */
        const val STEADY_FRAMES = 2

        /**
         * A heatmap's second peak counts only this far from its first, as a
         * share of the frame's diagonal: on an open book, the facing page's
         * corner, not the same corner's edge.
         */
        const val PEAK_MIN_SEPARATION = 0.15f

        /**
         * A locked corner follows only a peak within this share of the page's
         * shorter side of where it was: the facing page's same corner is a
         * page's width away...
         */
        const val LOCK_JUMP_SHARE = 0.35f

        /** ...but always this much of the frame's diagonal... */
        const val LOCK_MIN_JUMP = 0.04f

        /** ...and never more than this. */
        const val LOCK_MAX_JUMP = 0.25f

        /**
         * A locked page missing a corner is replaced by a whole page in view
         * only if this many of its corners are where the locked ones are.
         */
        const val LOCK_RESYNC_CORNERS = 3

        /** The page is let go once none of its corners has been seen for this long. */
        const val LOCK_RELEASE_MS = 1_500L

        /** A locked page seeing this many corners or fewer for [LOCK_STALE_MS] gives way to a whole page in view. */
        const val LOCK_STALE_MAX_SEEN = 2
        const val LOCK_STALE_MS = 1_000L

        /**
         * A gutter splitting a would-be page runs its height: a top peak no
         * further inside its top edge than this share of its height, and a
         * bottom peak likewise at its bottom...
         */
        const val GUTTER_END_SHARE = 0.15f

        /**
         * ...or this share of the diagonal past the edge (a book's gutter
         * corners can sit at the frame's edge)...
         */
        const val GUTTER_EDGE_MARGIN = 0.03f

        /** ...one above the other, within this share of the page's width across. */
        const val GUTTER_ALIGN = 0.15f

        /** A page picked anew has every corner's angle between these, in degrees. */
        const val PAGE_MIN_ANGLE = 55f
        const val PAGE_MAX_ANGLE = 125f

        /** No edge of a page longer than this times the one opposite ([PageCorners.pageShaped]). */
        const val PAGE_MAX_EDGE_RATIO = 1.6f

        /**
         * Capture, when the picture's own corners are not all seen: the steady
         * corners are used if each corner it does see lies this near them (a
         * share of the diagonal)...
         */
        const val FALLBACK_MATCH = 0.05f

        /** ...and it sees at least this many. */
        const val FALLBACK_MIN_SEEN = 2

        /** ...taken this much further out than [WARP_GROW], for the hand's movement. */
        const val FALLBACK_GROW = 0.05f

        /** Capture: the steady corners count only this soon after the guide took the picture. */
        const val STEADY_PAGE_FRESH_MS = 3_000L

        /**
         * The tone follows how much of the page is found, smoothed: each frame
         * moves it this share of the way to the frame's own score, so one
         * corner flickering does not make it flutter.
         */
        const val TONE_SMOOTHING = 0.4f

        /** Capture: how much further out each corner is taken, so the crop does not cut into the page. */
        const val WARP_GROW = 0.02f

        /** Capture: a flattened page narrower than this, in pixels, is not cropped to. */
        const val WARP_MIN_SIDE_PX = 64

        /**
         * What [c] says, or null for no page: all four corners in view, closer
         * when the page covers under [MIN_PAGE_AREA] of the frame, else ready;
         * some in view, partial.
         */
        fun stateFor(c: PageCorners.Corners, minConfidence: Float = CORNER_MIN_CONFIDENCE): State? {
            val seen = c.seenCount(minConfidence)
            return when {
                seen == 0 -> null
                seen < 4 -> State.PARTIAL
                PageCorners.areaShare(c) < MIN_PAGE_AREA -> State.CLOSER
                else -> State.READY
            }
        }

        /** How much of the page [c] shows, 0 to 1: its corners' mean confidence, those not seen counting 0. */
        fun score(c: PageCorners.Corners?, minConfidence: Float = CORNER_MIN_CONFIDENCE): Float =
            c?.corners?.sumOf { if (it.confidence >= minConfidence) it.confidence.toDouble().coerceAtMost(1.0) else 0.0 }
                ?.let { (it / 4).toFloat() } ?: 0f
    }
}
