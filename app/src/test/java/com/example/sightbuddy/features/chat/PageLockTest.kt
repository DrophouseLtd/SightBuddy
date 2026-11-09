package com.example.sightbuddy.features.chat

import com.example.sightbuddy.features.chat.PageCorners.Candidates
import com.example.sightbuddy.features.chat.PageCorners.Corner
import com.example.sightbuddy.features.chat.PageCorners.CropSource
import com.example.sightbuddy.features.chat.PageCorners.Corners
import com.example.sightbuddy.features.chat.PageCorners.INPUT_SIZE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The open book: [PageCorners.peaks] finds both pages' corners,
 * [PageCorners.choose] picks one page, [PageLock] keeps to it, and
 * [PageCorners.cropPlan] picks the capture's corners.
 */
class PageLockTest {

    private val w = 720
    private val h = 960

    /** A page's corners, top-left, top-right, bottom-right, bottom-left, at [conf]. */
    private fun quad(l: Float, t: Float, r: Float, b: Float, conf: Float = 0.8f) =
        listOf(Corner(l, t, conf), Corner(r, t, conf), Corner(r, b, conf), Corner(l, b, conf))

    // An open book: the left page from 0.05 to 0.48, the right from 0.52 to 0.95.
    private val leftPage = quad(0.05f, 0.2f, 0.48f, 0.85f)
    private val rightPage = quad(0.52f, 0.2f, 0.95f, 0.85f)

    /** An open book's pages: the corners at the gutter a little in from the outer ones, as pages curve into it. */
    private fun spread(l: Float, gutter: Float, r: Float): Pair<List<Corner>, List<Corner>> {
        val c = 0.8f
        val left = listOf(Corner(l, 0.2f, c), Corner(gutter - 0.02f, 0.24f, c), Corner(gutter - 0.02f, 0.82f, c), Corner(l, 0.85f, c))
        val right = listOf(Corner(gutter + 0.02f, 0.24f, c), Corner(r, 0.2f, c), Corner(r, 0.85f, c), Corner(gutter + 0.02f, 0.82f, c))
        return left to right
    }

    /** Candidates with [first] as each corner's best peak and [second], if any, as its other. */
    private fun candidates(first: List<Corner>, second: List<Corner?> = listOf(null, null, null, null)) =
        Candidates((0 until 4).map { listOfNotNull(first[it], second[it]) }, w, h)

    private fun near(expected: Corner, actual: Corner, tolerance: Float = 1e-4f) {
        assertEquals(expected.x, actual.x, tolerance)
        assertEquals(expected.y, actual.y, tolerance)
    }

    private val unseen = Corner(1.1f, 0f, 0.07f)

    @Test
    fun `each corner's heatmap gives its best point and a second page's peak`() {
        val box = PageCorners.letterbox(w, h)
        val heatmaps = FloatArray(PageCorners.OUTPUT_LENGTH) { 0.01f }
        val plane = INPUT_SIZE * INPUT_SIZE
        fun set(k: Int, x: Int, y: Int, v: Float) { heatmaps[k * plane + y * INPUT_SIZE + x] = v }
        // Top-left: the left page's at 0.25 across, the right page's at 0.75.
        set(PageCorners.TOP_LEFT, 28 + 42, 56, 0.8f)
        set(PageCorners.TOP_LEFT, 28 + 126, 56, 0.5f)
        // Its shoulder, next to the best point: not a peak of its own.
        set(PageCorners.TOP_LEFT, 28 + 43, 56, 0.7f)
        // Top-right: a second peak too weak to count.
        set(PageCorners.TOP_RIGHT, 28 + 126, 56, 0.8f)
        set(PageCorners.TOP_RIGHT, 28 + 42, 56, 0.2f)
        // Bottom-right: a second peak too near the first.
        set(PageCorners.BOTTOM_RIGHT, 28 + 126, 168, 0.8f)
        set(PageCorners.BOTTOM_RIGHT, 28 + 120, 168, 0.6f)
        val c = PageCorners.peaks(heatmaps, box)
        assertEquals(2, c.peaks[PageCorners.TOP_LEFT].size)
        near(Corner(0.25f, 0.25f, 0f), c.peaks[PageCorners.TOP_LEFT][0])
        near(Corner(0.75f, 0.25f, 0f), c.peaks[PageCorners.TOP_LEFT][1])
        assertEquals(0.5f, c.peaks[PageCorners.TOP_LEFT][1].confidence, 1e-6f)
        assertEquals(1, c.peaks[PageCorners.TOP_RIGHT].size)
        assertEquals(1, c.peaks[PageCorners.BOTTOM_RIGHT].size)
        // The best points are what decode gives.
        assertEquals(PageCorners.decode(heatmaps, box), c.best)
    }

    @Test
    fun `a single page in view is chosen as it is`() {
        val chosen = PageCorners.choose(candidates(leftPage))!!
        assertTrue(chosen.allSeen())
        (0 until 4).forEach { near(leftPage[it], chosen[it]) }
    }

    @Test
    fun `of two pages the one nearer the middle is chosen, never both as one`() {
        // The right page is a little nearer the middle; the two pages' outer
        // corners together sit nearer still, but hold the gutter's within.
        val (left, right) = spread(0.02f, 0.46f, 0.9f)
        val chosen = PageCorners.choose(candidates(left, right))!!
        (0 until 4).forEach { near(right[it], chosen[it]) }
        // The other way round, the left page.
        val (left2, right2) = spread(0.1f, 0.54f, 0.98f)
        val chosen2 = PageCorners.choose(candidates(right2, left2))!!
        (0 until 4).forEach { near(left2[it], chosen2[it]) }
    }

    @Test
    fun `corners mixed from two pages do not make a page`() {
        // Top-left from the right page, the rest from the left one: a slanted
        // shape, not a page.
        val q = listOf(rightPage[0], leftPage[1], leftPage[2], leftPage[3])
        assertFalse(PageCorners.inOrder(q) && PageCorners.pageShaped(Corners(q, w, h)))
    }

    @Test
    fun `with no corner seen nothing is chosen`() {
        assertNull(PageCorners.choose(candidates(listOf(unseen, unseen, unseen, unseen))))
    }

    @Test
    fun `the lock keeps to its page when the facing page's corners are stronger`() {
        val lock = PageLock()
        val first = lock.update(candidates(leftPage), 0L)
        (0 until 4).forEach { near(leftPage[it], first[it]) }
        // Next frame: the right page's corners are each corner's best.
        val page = lock.update(candidates(rightPage, leftPage), 200L)
        assertTrue(page.allSeen())
        (0 until 4).forEach { near(leftPage[it], page[it]) }
    }

    @Test
    fun `a corner only the facing page has is not seen`() {
        val (left, right) = spread(0.05f, 0.5f, 0.95f)
        val lock = PageLock()
        lock.update(candidates(left), 0L)
        // The left page's top-right (at the gutter) is gone; the right page's
        // top-right is far off.
        val second = listOf(left[0], null, left[2], left[3])
        val page = lock.update(candidates(right, second), 200L)
        assertEquals(3, page.seenCount())
        assertFalse(page.seen(PageCorners.TOP_RIGHT))
        assertEquals(PageCornerGuide.State.PARTIAL, PageCornerGuide.stateFor(page))
    }

    @Test
    fun `the lock follows its page as the phone moves`() {
        val lock = PageLock()
        lock.update(candidates(leftPage), 0L)
        var moved = leftPage
        for (step in 1..5) {
            moved = moved.map { it.copy(x = it.x + 0.08f) }
            val page = lock.update(candidates(moved), step * 200L)
            assertEquals(4, page.seenCount())
            near(moved[0], page[0])
        }
    }

    @Test
    fun `a corner lost while the page moves is found again where it went`() {
        val lock = PageLock()
        lock.update(candidates(leftPage), 0L)
        // Bottom-left lost while the page moves 0.2 across, in two steps.
        var moved = leftPage
        for (step in 1..2) {
            moved = moved.map { it.copy(x = it.x + 0.1f) }
            lock.update(candidates(moved.mapIndexed { k, c -> if (k == PageCorners.BOTTOM_LEFT) unseen else c }), step * 200L)
        }
        // Back, at its new place: 0.2 from where it was locked, found.
        moved = moved.map { it.copy(x = it.x + 0.1f) }
        val page = lock.update(candidates(moved), 600L)
        assertTrue(page.allSeen())
        near(moved[PageCorners.BOTTOM_LEFT], page[PageCorners.BOTTOM_LEFT])
    }

    @Test
    fun `a locked page found again whole takes its corner from there`() {
        val lock = PageLock()
        lock.update(candidates(leftPage), 0L)
        // The same page, its top-right now seen further off than a corner may jump
        // (still page-shaped: no edge over 1.6 times the one opposite).
        val found = listOf(leftPage[0], Corner(0.66f, 0.2f, 0.8f), leftPage[2], leftPage[3])
        val page = lock.update(candidates(found), 200L)
        assertEquals("whole", lock.mode)
        near(found[PageCorners.TOP_RIGHT], page[PageCorners.TOP_RIGHT])
        assertTrue(page.allSeen())
    }

    @Test
    fun `a whole facing page does not take the lock from a page missing a corner`() {
        val (left, right) = spread(0.05f, 0.5f, 0.95f)
        val lock = PageLock()
        lock.update(candidates(left), 0L)
        val second = listOf(left[0], null, left[2], left[3])
        lock.update(candidates(right, second), 200L)
        assertEquals("track", lock.mode)
    }

    @Test
    fun `a page left with two corners for a while gives way to a whole page in view`() {
        val lock = PageLock()
        lock.update(candidates(leftPage), 0L)
        // Its top corners lost; a whole page elsewhere shares none of its corners.
        val other = quad(0.55f, 0.1f, 0.95f, 0.6f)
        val frame = Candidates(
            listOf(
                listOf(other[0]),
                listOf(other[1]),
                listOf(other[2], leftPage[2]),
                listOf(other[3], leftPage[3]),
            ),
            w,
            h,
        )
        assertEquals(2, lock.update(frame, 200L).seenCount())
        assertEquals("track", lock.mode)
        lock.update(frame, 200L + PageCornerGuide.LOCK_STALE_MS - 100L)
        assertEquals("track", lock.mode)
        val page = lock.update(frame, 200L + PageCornerGuide.LOCK_STALE_MS)
        assertEquals("whole", lock.mode)
        assertTrue(page.allSeen())
        near(other[PageCorners.TOP_LEFT], page[PageCorners.TOP_LEFT])
    }

    @Test
    fun `the page is let go after a while with none of it seen`() {
        val lock = PageLock()
        lock.update(candidates(leftPage), 0L)
        val none = candidates(listOf(unseen, unseen, unseen, unseen))
        assertEquals(0, lock.update(none, 1_000L).seenCount())
        assertTrue(lock.isLocked)
        lock.update(none, PageCornerGuide.LOCK_RELEASE_MS + 1L)
        assertFalse(lock.isLocked)
        // Then any page is taken anew.
        val page = lock.update(candidates(rightPage, leftPage), PageCornerGuide.LOCK_RELEASE_MS + 200L)
        assertEquals("pick", lock.mode)
        assertTrue(page.allSeen())
    }

    @Test
    fun `reset lets the page go`() {
        val lock = PageLock()
        lock.update(candidates(leftPage), 0L)
        lock.reset()
        assertFalse(lock.isLocked)
    }

    @Test
    fun `a capture's corners follow the steady page`() {
        // Steady on the right page; in the picture the left page's are best.
        val followed = PageLock.follow(candidates(leftPage, rightPage), rightPage.map { it.x to it.y })
        (0 until 4).forEach { near(rightPage[it], followed[it]!!) }
    }

    private val steady = Corners(rightPage, w, h)

    @Test
    fun `a capture seeing all four corners uses its own`() {
        val own = Corners(rightPage.map { it.copy(x = it.x - 0.1f) }, w, h)
        val plan = PageCorners.cropPlan(own, steady)
        assertEquals(CropSource.OWN, plan.source)
        assertEquals(PageCornerGuide.WARP_GROW, plan.grow, 0f)
        assertEquals(own, plan.corners)
    }

    @Test
    fun `a capture seeing two corners near the steady ones uses the steady ones, grown more`() {
        // Moved 0.02 across: 14 px of a 1200 px diagonal.
        val own = Corners(
            rightPage.mapIndexed { k, c -> if (k < 2) c.copy(x = c.x + 0.02f) else c.copy(confidence = 0.1f) }, w, h,
        )
        val plan = PageCorners.cropPlan(own, steady)
        assertEquals(CropSource.STEADY, plan.source)
        assertEquals(PageCornerGuide.FALLBACK_GROW, plan.grow, 0f)
        assertEquals(steady, plan.corners)
    }

    @Test
    fun `a capture whose corners moved from the steady ones reads the whole frame`() {
        // Moved 0.1 across: 72 px, 0.06 of the diagonal.
        val own = Corners(
            rightPage.mapIndexed { k, c -> if (k < 2) c.copy(x = c.x + 0.1f) else c.copy(confidence = 0.1f) }, w, h,
        )
        assertEquals(CropSource.NONE, PageCorners.cropPlan(own, steady).source)
    }

    @Test
    fun `a capture seeing one corner, or with no steady page, reads the whole frame`() {
        val one = Corners(rightPage.mapIndexed { k, c -> if (k == 0) c else c.copy(confidence = 0.1f) }, w, h)
        assertEquals(CropSource.NONE, PageCorners.cropPlan(one, steady).source)
        val two = Corners(rightPage.mapIndexed { k, c -> if (k < 2) c else c.copy(confidence = 0.1f) }, w, h)
        assertEquals(CropSource.NONE, PageCorners.cropPlan(two, null).source)
        assertNull(PageCorners.cropPlan(two, null).corners)
    }

    /** [page] scaled by [f] about the frame's middle: the book further away. */
    private fun scaled(page: List<Corner>, f: Float) =
        page.map { it.copy(x = 0.5f + (it.x - 0.5f) * f, y = 0.5f + (it.y - 0.5f) * f) }

    /**
     * Locked on the left page, which then loses its top-right (at the gutter)
     * while the right page's corners are all there: the top-right must not
     * jump to the right page's.
     */
    private fun assertKeepsToLeftPage(left: List<Corner>, right: List<Corner>) {
        val lock = PageLock()
        lock.update(candidates(left), 0L)
        val second = listOf(left[0], null, left[2], left[3])
        val page = lock.update(candidates(right, second), 200L)
        assertEquals("track", lock.mode)
        assertFalse(page.seen(PageCorners.TOP_RIGHT))
        assertEquals(3, page.seenCount())
        listOf(PageCorners.TOP_LEFT, PageCorners.BOTTOM_RIGHT, PageCorners.BOTTOM_LEFT).forEach { near(left[it], page[it]) }
    }

    @Test
    fun `a book further away keeps its page when a gutter corner is lost`() {
        assertKeepsToLeftPage(scaled(leftPage, 0.85f), scaled(rightPage, 0.85f))
    }

    @Test
    fun `small pages keep their page when a gutter corner is lost`() {
        assertKeepsToLeftPage(quad(0.20f, 0.2f, 0.43f, 0.85f), quad(0.57f, 0.2f, 0.80f, 0.85f))
    }

    @Test
    fun `a capture's corners do not jump to the facing page`() {
        val left = quad(0.20f, 0.2f, 0.43f, 0.85f)
        val right = quad(0.57f, 0.2f, 0.80f, 0.85f)
        val second = listOf(left[0], null, left[2], left[3])
        val followed = PageLock.follow(candidates(right, second), left.map { it.x to it.y })
        assertNull(followed[PageCorners.TOP_RIGHT])
        listOf(PageCorners.TOP_LEFT, PageCorners.BOTTOM_RIGHT, PageCorners.BOTTOM_LEFT).forEach { near(left[it], followed[it]!!) }
    }

    @Test
    fun `followed corners that make no page do not count`() {
        // Even allowed a long jump, a top-right taken from the facing page
        // would hold the gutter's corners inside: it is not seen.
        val (left, right) = spread(0.05f, 0.5f, 0.95f)
        val second = listOf(left[0], null, left[2], left[3])
        val followed = PageLock.follow(candidates(right, second), left.map { it.x to it.y }, maxJump = 0.6f)
        assertNull(followed[PageCorners.TOP_RIGHT])
        assertEquals(3, followed.count { it != null })
    }

    @Test
    fun `a weak stray peak inside a page does not stop it being chosen`() {
        val page = quad(0.1f, 0.1f, 0.9f, 0.9f)
        listOf(PageCorners.TOP_LEFT, PageCorners.BOTTOM_RIGHT).forEach { k ->
            val second = MutableList<Corner?>(4) { null }.also { it[k] = Corner(0.5f, 0.45f, 0.4f) }
            val chosen = PageCorners.choose(candidates(page, second))
            assertTrue(chosen != null && chosen.allSeen())
            (0 until 4).forEach { near(page[it], chosen!![it]) }
        }
    }

    @Test
    fun `a page with a peak inside as sure as its weakest corner is not one page`() {
        // Taking that top-right peak instead makes no page either.
        val page = listOf(Corner(0.1f, 0.1f, 0.5f), Corner(0.9f, 0.1f, 0.9f), Corner(0.9f, 0.9f, 0.8f), Corner(0.1f, 0.9f, 0.8f))
        val second = listOf(null, Corner(0.5f, 0.5f, 0.6f), null, null)
        assertNull(PageCorners.choose(candidates(page, second)))
        // Less sure than the weakest corner, it is a stray: the page is chosen.
        val weak = listOf(null, Corner(0.5f, 0.5f, 0.45f), null, null)
        val chosen = PageCorners.choose(candidates(page, weak))
        assertTrue(chosen != null && chosen.allSeen())
        (0 until 4).forEach { near(page[it], chosen!![it]) }
    }

    /**
     * An open book as recorded: outer corners sure (0.7-0.9), the gutter's
     * corners weaker (0.3-0.6), a little in from the outer ones.
     */
    private val bookOuter = listOf(Corner(0.05f, 0.2f, 0.85f), Corner(0.95f, 0.2f, 0.9f), Corner(0.95f, 0.85f, 0.75f), Corner(0.05f, 0.85f, 0.7f))
    private val bookGutter = listOf(Corner(0.52f, 0.24f, 0.5f), Corner(0.48f, 0.24f, 0.45f), Corner(0.48f, 0.82f, 0.35f), Corner(0.52f, 0.82f, 0.4f))

    /** True when every seen corner of [page] lies on one side of the gutter. */
    private fun onePage(page: Corners): Boolean {
        val xs = page.corners.filter { it.confidence >= PageCornerGuide.CORNER_MIN_CONFIDENCE }.map { it.x }
        return xs.all { it <= 0.5f } || xs.all { it >= 0.5f }
    }

    @Test
    fun `an open book with a weaker gutter is one page, not the spread`() {
        val chosen = PageCorners.choose(candidates(bookOuter, bookGutter))!!
        assertTrue(chosen.allSeen())
        assertTrue(onePage(chosen))
    }

    @Test
    fun `an open book's spread is not followed as one page either`() {
        // Locked on the spread's outer corners somehow: following them, the
        // gutter's corners inside make it no page.
        val followed = PageLock.follow(candidates(bookOuter, bookGutter), bookOuter.map { it.x to it.y }, maxJump = 0.02f)
        assertTrue(followed.count { it != null } < 4)
    }

    @Test
    fun `a page with a stray peak at its top edge only is still chosen`() {
        val page = quad(0.1f, 0.1f, 0.9f, 0.9f)
        // A peak just inside the top edge, with no partner at the bottom.
        val second = listOf(Corner(0.5f, 0.12f, 0.6f), null, null, null)
        val chosen = PageCorners.choose(candidates(page, second))
        assertTrue(chosen != null && chosen.allSeen())
        (0 until 4).forEach { near(page[it], chosen!![it]) }
    }

    /** A single page, corners sure, from 0.05 to 0.95 across and 0.2 to 0.85 down. */
    private val wide = quad(0.05f, 0.2f, 0.95f, 0.85f)

    @Test
    fun `peaks from a text column inside a page do not split it`() {
        val inner = listOf(Corner(0.20f, 0.35f, 0.4f), null, null, Corner(0.20f, 0.70f, 0.4f))
        val chosen = PageCorners.choose(candidates(wide, inner))!!
        (0 until 4).forEach { near(wide[it], chosen[it]) }
    }

    @Test
    fun `peaks from a heading and a figure inside a page do not split it`() {
        val inner = listOf(null, Corner(0.60f, 0.30f, 0.4f), Corner(0.65f, 0.70f, 0.4f), null)
        val chosen = PageCorners.choose(candidates(wide, inner))!!
        (0 until 4).forEach { near(wide[it], chosen[it]) }
    }

    @Test
    fun `a page's sure corners beat weak inner peaks nearer the middle`() {
        // The page sits left of the middle; weak inner peaks make a smaller
        // page right in it. The sure corners are the page.
        val page = quad(0.0f, 0.1f, 0.9f, 0.9f)
        val inner = quad(0.3f, 0.3f, 0.7f, 0.7f, conf = 0.4f)
        val chosen = PageCorners.choose(candidates(page, inner))!!
        (0 until 4).forEach { near(page[it], chosen[it]) }
    }

    /** [wide] with a gutter: a top-right peak at [top] and a bottom-left one at [bottom]. */
    private fun split(top: Corner, bottom: Corner) =
        PageCorners.splitByOtherPeaks(Corners(wide, w, h), candidates(wide, listOf(null, top, null, bottom)))

    @Test
    fun `a gutter splits a page only with its ends one above the other`() {
        // The page is 0.9 wide: GUTTER_ALIGN allows 0.135 across.
        assertTrue(split(Corner(0.50f, 0.22f, 0.4f), Corner(0.62f, 0.83f, 0.4f)))
        assertFalse(split(Corner(0.50f, 0.22f, 0.4f), Corner(0.65f, 0.83f, 0.4f)))
    }

    @Test
    fun `a gutter's ends may lie just past the page's top and bottom edges`() {
        // GUTTER_EDGE_MARGIN is 36 px of the 1200 px diagonal: 30 px past
        // the top edge counts, 42 px does not.
        assertTrue(split(Corner(0.50f, 0.2f - 30f / h, 0.4f), Corner(0.50f, 0.83f, 0.4f)))
        assertFalse(split(Corner(0.50f, 0.2f - 42f / h, 0.4f), Corner(0.50f, 0.83f, 0.4f)))
        assertTrue(split(Corner(0.50f, 0.22f, 0.4f), Corner(0.50f, 0.85f + 30f / h, 0.4f)))
        assertFalse(split(Corner(0.50f, 0.22f, 0.4f), Corner(0.50f, 0.85f + 42f / h, 0.4f)))
    }

    @Test
    fun `a gutter's ends lie near the page's top and bottom, not in its middle`() {
        // The page is 624 px tall: GUTTER_END_SHARE is 94 px from each edge.
        assertTrue(split(Corner(0.50f, 0.2f + 85f / h, 0.4f), Corner(0.50f, 0.85f - 85f / h, 0.4f)))
        assertFalse(split(Corner(0.50f, 0.2f + 105f / h, 0.4f), Corner(0.50f, 0.83f, 0.4f)))
        assertFalse(split(Corner(0.50f, 0.22f, 0.4f), Corner(0.50f, 0.85f - 105f / h, 0.4f)))
    }

    @Test
    fun `a small page's corners may still move a little between frames`() {
        // 0.35 of a 0.05-wide page is 13 px, under LOCK_MIN_JUMP's 48 px.
        val tiny = quad(0.50f, 0.50f, 0.55f, 0.60f).map { it.x to it.y }
        assertEquals(PageCornerGuide.LOCK_MIN_JUMP, PageLock.jumpFor(tiny, w, h), 1e-6f)
        // A mid-sized page: 0.35 of its shorter side, 0.3 of the width (216 px).
        val mid = quad(0.3f, 0.2f, 0.6f, 0.8f).map { it.x to it.y }
        assertEquals(0.35f * 216f / 1200f, PageLock.jumpFor(mid, w, h), 1e-5f)
        // A page running well past the view: held to LOCK_MAX_JUMP.
        val big = quad(-0.2f, -0.2f, 1.2f, 1.2f).map { it.x to it.y }
        assertEquals(PageCornerGuide.LOCK_MAX_JUMP, PageLock.jumpFor(big, w, h), 1e-6f)
        // One corner only, no side to measure: LOCK_MAX_JUMP too.
        val one = listOf(0.5f to 0.5f, null, null, null)
        assertEquals(PageCornerGuide.LOCK_MAX_JUMP, PageLock.jumpFor(one, w, h), 1e-6f)
    }
}
