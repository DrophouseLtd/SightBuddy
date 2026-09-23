package com.example.sightbuddy.features.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveReadQueueTest {

    private val title = "Koolla on väliä"
    private val body = "Niin sanotusti varma nakki ja nauraen matkalla pankkiin"
    private val ad = "Sinulla on jo työ, älä tee asuntosijoittamisesta toista"

    @Test
    fun `boxes are read in order, each once`() {
        val q = LiveReadQueue()
        q.update(listOf(title, body), 0L)
        assertEquals(title, q.next())
        assertEquals(body, q.next())
        assertNull(q.next())
    }

    @Test
    fun `the same boxes in new frames are not queued again`() {
        val q = LiveReadQueue()
        q.update(listOf(title, body), 0L)
        q.update(listOf(title, body), 200L)
        q.update(listOf(title, body), 400L)
        assertEquals(title, q.next())
        assertEquals(body, q.next())
        assertNull(q.next())
    }

    @Test
    fun `text already read is not read again, even misread or in part`() {
        val q = LiveReadQueue()
        q.update(listOf(body), 0L)
        q.next()
        // One word misread.
        q.update(listOf("Niin sanotusti varma nakki ja nauraen matkalla pankkiln"), 5_000L)
        assertNull(q.next())
        // Only part of it in view.
        q.update(listOf("varma nakki ja nauraen"), 6_000L)
        assertNull(q.next())
    }

    @Test
    fun `new text joins the end of the queue`() {
        val q = LiveReadQueue()
        q.update(listOf(title, body), 0L)
        assertEquals(title, q.next())
        q.update(listOf(title, body, ad), 1_000L)
        assertEquals(body, q.next())
        assertEquals(ad, q.next())
    }

    @Test
    fun `boxes that left the screen leave the queue`() {
        val q = LiveReadQueue()
        q.update(listOf(title, body, ad), 0L)
        q.update(listOf(title, ad), 500L)
        assertEquals(title, q.next())
        assertEquals(ad, q.next())
        assertNull(q.next())
    }

    @Test
    fun `no text on screen empties the queue`() {
        val q = LiveReadQueue()
        assertFalse(q.update(listOf(title, body), 0L))
        assertTrue(q.update(emptyList(), 2_000L))
        assertNull(q.next())
    }

    @Test
    fun `read text may be read again once the memory has passed`() {
        val q = LiveReadQueue(memoryMs = 60_000L)
        q.update(listOf(title), 0L)
        q.next()
        q.update(listOf(title), 61_000L)
        assertEquals(title, q.next())
    }

    @Test
    fun `the queue stops at its word limit, and a long box is cut to it`() {
        val q = LiveReadQueue(maxQueuedWords = 10)
        val long = (1..25).joinToString(" ") { "sana$it" }
        q.update(listOf(long, ad), 0L)
        assertEquals(10, q.next()!!.split(" ").size)
        assertNull(q.next())
    }

    @Test
    fun `after a reset the text in view is new again`() {
        val q = LiveReadQueue()
        q.update(listOf(title), 0L)
        q.next()
        q.reset()
        q.update(listOf(title), 1_000L)
        assertEquals(title, q.next())
    }
}
