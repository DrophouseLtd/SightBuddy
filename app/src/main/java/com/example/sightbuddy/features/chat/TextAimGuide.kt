package com.example.sightbuddy.features.chat

import com.example.sightbuddy.features.chat.LocalTextExtractor.TextFrame
import kotlin.math.abs
import kotlin.math.max

/**
 * Talks the camera onto a page of text before Text chat takes its picture.
 *
 * Fed the page to aim at in each frame (the sheet of paper from [PaperFinder],
 * or the text page from [PageTracker] when there is no sheet), it answers with
 * one step: say nothing, say there is no text, say a direction (left / right /
 * up / down, closer / further), say hold steady once the page is ready (all in
 * view, big enough and readable), or take the picture once it has stayed ready
 * for [holdMs]. A change of direction is said only once a second frame agrees,
 * so one odd frame does not flip the cue. Pure timing and geometry, so it is
 * tested without a camera.
 */
class TextAimGuide(
    private val holdMs: Long = 2_000L,
    private val cueRepeatMs: Long = 1_500L,
    private val noTextRepeatMs: Long = 4_000L,
    /**
     * How long text must be gone before "no text" is said. The recogniser drops
     * the odd frame of text it can see, which used to put "no text visible" in
     * between the direction cues; it is one or the other.
     */
    private val noTextAfterMs: Long = 1_500L,
    /** Lines shorter than this, in frame pixels, are too small to read well. */
    private val minLinePx: Int = MIN_LINE_PX,
) {
    enum class Direction { LEFT, RIGHT, UP, DOWN, CLOSER, FURTHER, CENTRE }

    sealed interface Step {
        data object Silent : Step
        data object NoText : Step
        data class Cue(val direction: Direction) : Step
        data object Capture : Step
    }

    private var lastDirection: Direction? = null
    /** A new direction seen once, waiting for the next frame to agree. */
    private var pendingDirection: Direction? = null
    private var lastCueAtMs = 0L
    private var centredSinceMs = 0L
    private var lastNoTextAtMs = Long.MIN_VALUE / 2
    private var noTextSinceMs: Long? = null
    private var started = false

    fun reset() {
        lastDirection = null
        pendingDirection = null
        lastCueAtMs = 0L
        centredSinceMs = 0L
        lastNoTextAtMs = Long.MIN_VALUE / 2
        noTextSinceMs = null
        started = false
    }

    fun next(frame: TextFrame?, nowMs: Long): Step {
        if (frame == null) {
            // Counted from the start, so a page never in view is said at once.
            val since = noTextSinceMs ?: (if (started) nowMs else nowMs - noTextAfterMs)
            noTextSinceMs = since
            started = true
            if (nowMs - since < noTextAfterMs) return Step.Silent
            lastDirection = null
            pendingDirection = null
            centredSinceMs = 0L
            // A pulse, not a stream: often enough to know it is still looking.
            if (nowMs - lastNoTextAtMs < noTextRepeatMs) return Step.Silent
            lastNoTextAtMs = nowMs
            return Step.NoText
        }
        started = true
        noTextSinceMs = null
        val direction = directionFor(frame, wasCentred = lastDirection == Direction.CENTRE, minLinePx = minLinePx)
        if (lastDirection != null && direction != lastDirection && direction != pendingDirection) {
            pendingDirection = direction
            return Step.Silent
        }
        pendingDirection = null
        if (direction == Direction.CENTRE) {
            if (lastDirection != Direction.CENTRE) {
                lastDirection = Direction.CENTRE
                centredSinceMs = nowMs
                lastCueAtMs = nowMs
                return Step.Cue(Direction.CENTRE)
            }
            if (nowMs - centredSinceMs >= holdMs) {
                reset()
                return Step.Capture
            }
            return Step.Silent
        }
        centredSinceMs = 0L
        if (direction != lastDirection || nowMs - lastCueAtMs >= cueRepeatMs) {
            lastDirection = direction
            lastCueAtMs = nowMs
            return Step.Cue(direction)
        }
        return Step.Silent
    }

    companion object {
        /** Text this close to an edge is taken to carry on past it. */
        const val EDGE_MARGIN = 0.04f

        /** How far the page's middle may sit from the frame's to become centred… */
        const val CENTRE_ENTER = 0.15f

        /** …and how far it may then wander before it no longer is. */
        const val CENTRE_LEAVE = 0.25f

        /** Least share of the view's width or height the page's text should fill. */
        const val MIN_FILL = 0.65f

        /** Once ready, the page may shrink this much before it is not. */
        const val FILL_SLACK = 0.05f

        /** ML Kit reads characters from about 16 px; a line box is a little taller. */
        const val MIN_LINE_PX = 18

        /**
         * Which way to move the phone. Text past opposite edges needs the phone
         * further away; text running off one edge comes next, since there is
         * more of it that way; then the page's middle against the frame's; then
         * lines too small to read, which need the phone closer. [wasCentred]
         * widens the centre so a hand's wobble does not leave it.
         */
        fun directionFor(
            frame: TextFrame,
            wasCentred: Boolean = false,
            minLinePx: Int = MIN_LINE_PX,
            minFill: Float = MIN_FILL,
        ): Direction {
            val cutLeft = frame.left < EDGE_MARGIN
            val cutRight = frame.right > 1f - EDGE_MARGIN
            val cutTop = frame.top < EDGE_MARGIN
            val cutBottom = frame.bottom > 1f - EDGE_MARGIN
            val offX = (frame.left + frame.right) / 2f - 0.5f
            val offY = (frame.top + frame.bottom) / 2f - 0.5f
            val tolerance = if (wasCentred) CENTRE_LEAVE else CENTRE_ENTER
            val fill = max(frame.right - frame.left, frame.bottom - frame.top)
            val readable = frame.lineHeightPx !in 1 until minLinePx
            return when {
                (cutLeft && cutRight) || (cutTop && cutBottom) -> Direction.FURTHER
                cutLeft -> Direction.LEFT
                cutRight -> Direction.RIGHT
                cutTop -> Direction.UP
                cutBottom -> Direction.DOWN
                // All of the page in view, big and readable: ready, wherever it
                // sits. Centring only brings a small or far-off page into place.
                readable && fill >= (if (wasCentred) minFill - FILL_SLACK else minFill) -> Direction.CENTRE
                abs(offX) > tolerance && abs(offX) >= abs(offY) ->
                    if (offX < 0f) Direction.LEFT else Direction.RIGHT
                abs(offY) > tolerance ->
                    if (offY < 0f) Direction.UP else Direction.DOWN
                frame.lineHeightPx in 1 until minLinePx -> Direction.CLOSER
                // The page should fill most of the view, so the capture reads it
                // at its best without any of it cut off.
                max(frame.right - frame.left, frame.bottom - frame.top) < minFill -> Direction.CLOSER
                else -> Direction.CENTRE
            }
        }
    }
}
