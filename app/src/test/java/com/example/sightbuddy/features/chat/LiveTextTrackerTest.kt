package com.example.sightbuddy.features.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LiveTextTrackerTest {

    private fun block(text: String, left: Float = 0.1f) =
        LiveText.Block(text, left, 0.3f, left + 0.5f, 0.4f, lineHeightPx = 30)

    private val sign = listOf(block("Koolla on väliä pienet yksiöt"))
    private val other = listOf(block("Sinulla on jo työ asuntosijoittaminen helpoksi"))

    private fun LiveTextTracker.feed(blocks: List<LiveText.Block>, at: Long) = update(blocks, 1440, 1920, at)

    @Test
    fun `text in view is shown at once`() {
        assertEquals("Koolla on väliä pienet yksiöt", LiveTextTracker().feed(sign, 0L)!!.blocks.single().text)
    }

    @Test
    fun `the words are held for three seconds, the boxes still move`() {
        val tracker = LiveTextTracker(holdMs = 3_000L)
        val first = tracker.feed(sign, 0L)!!
        val moved = tracker.feed(sign.map { it.copy(left = 0.3f) } + other, 1_000L)!!
        assertEquals(first.generation, moved.generation)
        assertEquals(1, moved.blocks.size)
        assertEquals(0.3f, moved.blocks.single().left)
        val later = tracker.feed(other, 3_000L)!!
        assertEquals(first.generation + 1, later.generation)
        assertEquals("Sinulla on jo työ asuntosijoittaminen helpoksi", later.blocks.single().text)
    }

    @Test
    fun `the same words seen again are not a new generation`() {
        val tracker = LiveTextTracker(holdMs = 3_000L)
        val first = tracker.feed(sign, 0L)!!
        assertEquals(first.generation, tracker.feed(sign, 4_000L)!!.generation)
    }

    @Test
    fun `resuming takes the text in view as new, to be shown and read at once`() {
        val tracker = LiveTextTracker(holdMs = 3_000L)
        val first = tracker.feed(sign, 0L)!!
        tracker.resume()
        // The same words as before, a moment later: still new.
        assertEquals(first.generation + 1, tracker.feed(sign, 500L)!!.generation)
    }

    @Test
    fun `text gone for a moment stays, gone for longer is cleared`() {
        val tracker = LiveTextTracker(keepMs = 1_500L)
        tracker.feed(sign, 0L)
        assertNotNull(tracker.feed(emptyList(), 1_000L))
        assertNull(tracker.feed(emptyList(), 2_000L))
    }
}
