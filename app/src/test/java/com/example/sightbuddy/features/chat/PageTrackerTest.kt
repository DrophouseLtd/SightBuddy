package com.example.sightbuddy.features.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageTrackerTest {

    private val w = 1440
    private val h = 1920

    private val leftWords = "Koolla on väliä pienet yksiöt kaupungissa"
    private val rightWords = "Sinulla on jo työ, asuntosijoittaminen tehdään helpoksi meidän palvelulla"

    private fun block(l: Float, t: Float, r: Float, b: Float, text: String = leftWords, lineH: Int = 30) =
        PageTracker.Block(l, t, r, b, text.length, lineH, text)

    /** A page of body text: paragraphs a line apart. */
    private fun page(l: Float, r: Float, top: Float = 0.2f, paragraphs: Int = 6, text: String = leftWords) =
        List(paragraphs) { i ->
            val t = top + i * 0.1f
            block(l, t, r, t + 0.09f, text)
        }

    private fun PageTracker.feed(blocks: List<PageTracker.Block>, at: Long) = update(blocks, w, h, at)

    @Test
    fun `codes, symbols, sideways text and giant lines are not taken as text`() {
        val blocks = listOf(
            block(0.3f, 0.3f, 0.7f, 0.4f),
            block(0.1f, 0.8f, 0.9f, 0.95f, text = "E38133E835802293829R"),
            block(0.1f, 0.1f, 0.2f, 0.12f, text = "7"),
            block(0.0f, 0.5f, 1.0f, 0.9f, lineH = 400),
            // Lines 200 px tall in a block 60 px wide: printed sideways.
            block(0.90f, 0.3f, 0.94f, 0.6f, text = "TVS the Vedar", lineH = 200),
        )
        assertEquals(setOf(0), PageTracker().feed(blocks, 0L).kept)
    }

    @Test
    fun `two pages side by side are found as two pages`() {
        // Close enough to group as one region, which is wider than a page.
        val left = page(0.02f, 0.47f) + block(0.05f, 0.1f, 0.40f, 0.16f, text = "Koolla on väliä", lineH = 90)
        val right = page(0.50f, 0.98f, text = rightWords)
        val result = PageTracker().feed(left + right, 0L)
        assertEquals(2, result.pages.size)
        assertTrue(result.pages.all { it.right <= 0.48f || it.left >= 0.49f })
    }

    @Test
    fun `a gap between columns of one page is not taken for a gutter`() {
        // Two columns of one page, together taller than wide.
        val columns = page(0.25f, 0.48f, top = 0.1f, paragraphs = 8) + page(0.51f, 0.75f, top = 0.1f, paragraphs = 8)
        assertEquals(1, PageTracker().feed(columns, 0L).pages.size)
    }

    @Test
    fun `a frame that misses most of the page keeps the page`() {
        val tracker = PageTracker()
        val full = page(0.2f, 0.8f)
        val first = tracker.feed(full, 0L).target
        val missed = tracker.feed(full.take(1), 500L)
        assertTrue(missed.held)
        assertEquals(first, missed.target)
    }

    @Test
    fun `the largest page in view is the target`() {
        val small = page(0.02f, 0.40f, paragraphs = 3)
        val big = page(0.52f, 0.98f, text = rightWords)
        val result = PageTracker().feed(small + big, 0L)
        assertTrue(result.target!!.left > 0.5f)
    }

    @Test
    fun `a page gone for longer than the hold is let go`() {
        val tracker = PageTracker(holdMs = 1_500L)
        tracker.feed(page(0.2f, 0.8f), 0L)
        assertNotNull(tracker.feed(emptyList(), 1_000L).target)
        assertNull(tracker.feed(emptyList(), 2_000L).target)
    }
}
