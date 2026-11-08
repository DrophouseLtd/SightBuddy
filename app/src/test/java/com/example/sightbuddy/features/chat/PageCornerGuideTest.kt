package com.example.sightbuddy.features.chat

import com.example.sightbuddy.features.chat.PageCornerGuide.State
import com.example.sightbuddy.features.chat.PageCorners.Corner
import com.example.sightbuddy.features.chat.PageCorners.Corners
import com.example.sightbuddy.features.chat.PageCornerGuide.Direction
import com.example.sightbuddy.features.chat.PageCornerGuide.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageCornerGuideTest {

    private val seen = 0.8f
    private val unseen = 0.1f

    /** A page with a margin of [m] all round, moved [dx] right, with each corner's confidence. */
    private fun page(
        m: Float = 0.1f,
        dx: Float = 0f,
        tl: Float = seen,
        tr: Float = seen,
        br: Float = seen,
        bl: Float = seen,
    ) = Corners(
        listOf(
            Corner(m + dx, m, tl),
            Corner(1f - m + dx, m, tr),
            Corner(1f - m + dx, 1f - m, br),
            Corner(m + dx, 1f - m, bl),
        ),
        720,
        960,
    )

    private val none get() = page(tl = unseen, tr = unseen, br = unseen, bl = unseen)

    @Test
    fun `all four corners in view and big enough is ready`() {
        assertEquals(State.READY, PageCornerGuide.stateFor(page()))
    }

    @Test
    fun `all four corners in view but the page small needs the phone closer`() {
        // 0.4 by 0.4: 16% of the frame.
        assertEquals(State.CLOSER, PageCornerGuide.stateFor(page(0.3f)))
        // 0.58 by 0.58: 34%, still too few pixels a letter to read well.
        assertEquals(State.CLOSER, PageCornerGuide.stateFor(page(0.21f)))
        // 0.6 by 0.6: 36%.
        assertEquals(State.READY, PageCornerGuide.stateFor(page(0.2f)))
    }

    @Test
    fun `some corners in view is partial, whichever they are`() {
        assertEquals(State.PARTIAL, PageCornerGuide.stateFor(page(tl = unseen)))
        assertEquals(State.PARTIAL, PageCornerGuide.stateFor(page(tl = unseen, bl = unseen)))
        assertEquals(State.PARTIAL, PageCornerGuide.stateFor(page(tl = unseen, br = unseen)))
        assertEquals(State.PARTIAL, PageCornerGuide.stateFor(page(tl = unseen, tr = unseen, bl = unseen)))
    }

    @Test
    fun `no corner in view is no page`() {
        assertNull(PageCornerGuide.stateFor(none))
    }

    @Test
    fun `corners partly in view say nothing, the tone guides`() {
        val guide = PageCornerGuide()
        var t = 0L
        listOf(page(tl = unseen), page(tl = unseen, bl = unseen), page(tr = unseen, br = unseen), page(br = unseen)).forEach {
            repeat(5) { _ ->
                assertEquals(Step.Silent, guide.next(it, t))
                t += 200L
            }
        }
    }

    @Test
    fun `nothing seen is said after a moment, from the start too, then as a pulse`() {
        val guide = PageCornerGuide()
        // The first frames often have no page yet while one is being picked.
        assertEquals(Step.Silent, guide.next(none, 0L))
        assertEquals(Step.Silent, guide.next(none, 1_000L))
        assertEquals(Step.NoText, guide.next(none, 1_500L))
        assertEquals(Step.Silent, guide.next(none, 1_700L))
        assertEquals(Step.NoText, guide.next(none, 5_500L))
        assertEquals(Step.NoText, guide.next(null, 9_500L))
    }

    @Test
    fun `closer is said, then no faster than the cue pacing`() {
        val guide = PageCornerGuide()
        val small = page(0.3f)
        assertEquals(Step.Cue(Direction.CLOSER), guide.next(small, 0L))
        assertEquals(Step.Silent, guide.next(small, 1_000L))
        assertEquals(Step.Cue(Direction.CLOSER), guide.next(small, 1_500L))
    }

    @Test
    fun `a change of what is said needs a second agreeing frame`() {
        val guide = PageCornerGuide()
        assertEquals(Step.Cue(Direction.CLOSER), guide.next(page(0.3f), 0L))
        assertEquals(Step.Silent, guide.next(page(), 200L))
        assertEquals(Step.Cue(Direction.CENTRE), guide.next(page(), 400L))
    }

    @Test
    fun `hold steady, then the picture once the corners keep still for two frames`() {
        val guide = PageCornerGuide()
        assertEquals(Step.Cue(Direction.CENTRE), guide.next(page(), 0L))
        assertEquals(Step.Silent, guide.next(page(dx = 0.01f), 200L))
        assertEquals(Step.Capture, guide.next(page(dx = 0.02f), 400L))
    }

    @Test
    fun `a moving page is not taken until it keeps still`() {
        val guide = PageCornerGuide()
        assertEquals(Step.Cue(Direction.CENTRE), guide.next(page(), 0L))
        // 0.1 of the width is 72 px of a 1200 px diagonal: 0.06, more than 0.04.
        assertEquals(Step.Silent, guide.next(page(dx = 0.1f), 200L))
        assertEquals(Step.Silent, guide.next(page(dx = 0.11f), 400L))
        assertEquals(Step.Capture, guide.next(page(dx = 0.11f), 600L))
    }

    @Test
    fun `one odd frame does not end a steady hold`() {
        val guide = PageCornerGuide()
        assertEquals(Step.Cue(Direction.CENTRE), guide.next(page(), 0L))
        assertEquals(Step.Silent, guide.next(page(), 200L))
        // A corner flickers out of view for a frame.
        assertEquals(Step.Silent, guide.next(page(br = unseen), 400L))
        assertEquals(Step.Capture, guide.next(page(), 600L))
    }

    @Test
    fun `hold steady is said again after the page was lost`() {
        val guide = PageCornerGuide()
        assertEquals(Step.Cue(Direction.CENTRE), guide.next(page(), 0L))
        assertEquals(Step.Silent, guide.next(page(br = unseen), 200L))
        assertEquals(Step.Silent, guide.next(page(br = unseen), 400L))
        // Back, and a second frame agrees: said again.
        assertEquals(Step.Silent, guide.next(page(), 600L))
        assertEquals(Step.Cue(Direction.CENTRE), guide.next(page(), 800L))
    }

    @Test
    fun `guidance never takes the picture by itself without a steady page`() {
        val guide = PageCornerGuide()
        val partial = page(tl = unseen)
        var t = 0L
        while (t < 60_000L) {
            assertTrue(guide.next(if (t % 2_000L < 1_000L) partial else none, t) != Step.Capture)
            t += 200L
        }
    }

    @Test
    fun `the score is the corners' mean confidence, those not seen counting nothing`() {
        assertEquals(0.8f, PageCornerGuide.score(page()), 1e-6f)
        assertEquals(0.6f, PageCornerGuide.score(page(tl = unseen)), 1e-6f)
        assertEquals(0f, PageCornerGuide.score(none), 0f)
        assertEquals(0f, PageCornerGuide.score(null), 0f)
    }

    @Test
    fun `the tone rises smoothly as the page is found, and falls back when it is lost`() {
        val guide = PageCornerGuide()
        // Three corners in view: a score of 0.6, nothing said, so no picture taken.
        val three = page(br = unseen)
        guide.next(three, 0L)
        val first = guide.found
        // One frame moves it TONE_SMOOTHING of the way.
        assertEquals(0.6f * PageCornerGuide.TONE_SMOOTHING, first, 1e-5f)
        guide.next(three, 200L)
        assertTrue(guide.found > first && guide.found < 0.6f)
        repeat(10) { guide.next(three, 400L + it * 200L) }
        assertEquals(0.6f, guide.found, 0.01f)
        // A second corner flickering away for one frame dips it a little, not to 0.4.
        val before = guide.found
        guide.next(page(br = unseen, bl = unseen), 2_400L)
        assertTrue(guide.found < before && guide.found > 0.5f)
        repeat(20) { guide.next(none, 2_600L + it * 200L) }
        assertTrue(guide.found < 0.01f)
        guide.reset()
        assertEquals(0f, guide.found, 0f)
    }
}
