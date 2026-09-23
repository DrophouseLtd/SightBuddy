package com.example.sightbuddy.features.chat

import com.example.sightbuddy.features.chat.LocalTextExtractor.TextFrame
import com.example.sightbuddy.features.chat.TextAimGuide.Direction
import com.example.sightbuddy.features.chat.TextAimGuide.Step
import org.junit.Assert.assertEquals
import org.junit.Test

class TextAimGuideTest {

    private fun frame(l: Float, t: Float, r: Float, b: Float) = TextFrame(l, t, r, b, chars = 40)
    private val centred = frame(0.15f, 0.3f, 0.85f, 0.7f)

    @Test
    fun `text running off one edge points that way`() {
        assertEquals(Direction.LEFT, TextAimGuide.directionFor(frame(0.0f, 0.3f, 0.6f, 0.7f)))
        assertEquals(Direction.RIGHT, TextAimGuide.directionFor(frame(0.4f, 0.3f, 1.0f, 0.7f)))
        assertEquals(Direction.UP, TextAimGuide.directionFor(frame(0.2f, 0.0f, 0.8f, 0.6f)))
        assertEquals(Direction.DOWN, TextAimGuide.directionFor(frame(0.2f, 0.4f, 0.8f, 1.0f)))
    }

    @Test
    fun `text inside the frame is steered by its middle`() {
        assertEquals(Direction.LEFT, TextAimGuide.directionFor(frame(0.1f, 0.4f, 0.4f, 0.6f)))
        assertEquals(Direction.DOWN, TextAimGuide.directionFor(frame(0.4f, 0.7f, 0.6f, 0.9f)))
        assertEquals(Direction.CENTRE, TextAimGuide.directionFor(centred))
    }

    @Test
    fun `text past opposite edges needs the phone further away`() {
        assertEquals(Direction.FURTHER, TextAimGuide.directionFor(frame(0.0f, 0.3f, 1.0f, 0.7f)))
        assertEquals(Direction.FURTHER, TextAimGuide.directionFor(frame(0.2f, 0.0f, 0.8f, 1.0f)))
    }

    @Test
    fun `centred text too small to read needs the phone closer`() {
        val small = centred.copy(lineHeightPx = 10)
        assertEquals(Direction.CLOSER, TextAimGuide.directionFor(small, minLinePx = 18))
        assertEquals(Direction.CENTRE, TextAimGuide.directionFor(small.copy(lineHeightPx = 24), minLinePx = 18))
    }

    @Test
    fun `a centred page filling little of the view needs the phone closer`() {
        // A page far away: centred, readable, but a third of the view.
        assertEquals(Direction.CLOSER, TextAimGuide.directionFor(frame(0.35f, 0.35f, 0.65f, 0.65f)))
        assertEquals(Direction.CENTRE, TextAimGuide.directionFor(frame(0.1f, 0.2f, 0.9f, 0.8f)))
    }

    @Test
    fun `a whole page in view and big enough is ready even off centre`() {
        // Middle 0.23 right and 0.1 down, all edges inside, 65% wide.
        assertEquals(Direction.CENTRE, TextAimGuide.directionFor(frame(0.30f, 0.15f, 0.95f, 0.75f)))
    }

    @Test
    fun `a small page is still brought towards the middle`() {
        // Middle 0.16 right: steered while it is not yet centred, then left alone
        // and asked closer once it is.
        val drifted = frame(0.46f, 0.3f, 0.86f, 0.7f)
        assertEquals(Direction.RIGHT, TextAimGuide.directionFor(drifted, wasCentred = false))
        assertEquals(Direction.CLOSER, TextAimGuide.directionFor(drifted, wasCentred = true))
    }

    @Test
    fun `a new direction is said only when the next frame agrees`() {
        val guide = TextAimGuide()
        val left = frame(0.0f, 0.3f, 0.6f, 0.7f)
        val right = frame(0.4f, 0.3f, 1.0f, 0.7f)
        assertEquals(Step.Cue(Direction.LEFT), guide.next(left, 0L))
        assertEquals(Step.Silent, guide.next(right, 500L))
        assertEquals(Step.Silent, guide.next(left, 1_000L))
        assertEquals(Step.Silent, guide.next(right, 1_200L))
        assertEquals(Step.Cue(Direction.RIGHT), guide.next(right, 1_400L))
    }

    @Test
    fun `no text is said once, then again only after a pause`() {
        val guide = TextAimGuide(noTextRepeatMs = 4_000L)
        assertEquals(Step.NoText, guide.next(null, 0L))
        assertEquals(Step.Silent, guide.next(null, 1_000L))
        assertEquals(Step.NoText, guide.next(null, 4_000L))
    }

    @Test
    fun `a dropped frame between cues says nothing`() {
        val guide = TextAimGuide(noTextAfterMs = 1_500L)
        val left = frame(0.0f, 0.3f, 0.6f, 0.7f)
        assertEquals(Step.Cue(Direction.LEFT), guide.next(left, 0L))
        assertEquals(Step.Silent, guide.next(null, 500L))
        assertEquals(Step.Silent, guide.next(left, 1_000L))
        // Gone for good this time: said once the text has been missing long enough.
        assertEquals(Step.Silent, guide.next(null, 1_500L))
        assertEquals(Step.NoText, guide.next(null, 3_000L))
    }

    @Test
    fun `a direction is played on change and repeated while it holds`() {
        val guide = TextAimGuide(cueRepeatMs = 1_500L)
        val left = frame(0.0f, 0.3f, 0.6f, 0.7f)
        assertEquals(Step.Cue(Direction.LEFT), guide.next(left, 0L))
        assertEquals(Step.Silent, guide.next(left, 500L))
        assertEquals(Step.Cue(Direction.LEFT), guide.next(left, 1_500L))
    }

    @Test
    fun `two seconds held in the middle takes the picture`() {
        val guide = TextAimGuide(holdMs = 2_000L)
        assertEquals(Step.Cue(Direction.CENTRE), guide.next(centred, 0L))
        assertEquals(Step.Silent, guide.next(centred, 1_000L))
        assertEquals(Step.Capture, guide.next(centred, 2_000L))
    }

    @Test
    fun `drifting off the middle restarts the hold`() {
        val guide = TextAimGuide(holdMs = 2_000L)
        guide.next(centred, 0L)
        guide.next(frame(0.0f, 0.3f, 0.6f, 0.7f), 1_400L)
        guide.next(frame(0.0f, 0.3f, 0.6f, 0.7f), 1_500L)
        assertEquals(Step.Silent, guide.next(centred, 1_700L))
        assertEquals(Step.Cue(Direction.CENTRE), guide.next(centred, 1_800L))
        assertEquals(Step.Silent, guide.next(centred, 3_000L))
        assertEquals(Step.Capture, guide.next(centred, 3_800L))
    }

    @Test
    fun `one stray frame does not restart the hold`() {
        val guide = TextAimGuide(holdMs = 2_000L)
        guide.next(centred, 0L)
        assertEquals(Step.Silent, guide.next(frame(0.0f, 0.3f, 0.6f, 0.7f), 1_000L))
        assertEquals(Step.Capture, guide.next(centred, 2_000L))
    }
}
