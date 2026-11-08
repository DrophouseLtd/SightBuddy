package com.example.sightbuddy.core.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #24: what LiteRT-LM's streaming callback hands over is turned into
 * new text only. Its Java side cannot say whether each message holds the text
 * written since the last one or all of it so far (that is decided in native
 * code), so both are handled: speaking the whole text again with each part
 * would repeat the answer from the start.
 */
class StreamDeltasTest {

    @Test
    fun `parts that are each new text are passed on as they are`() {
        val deltas = StreamDeltas()
        assertEquals("The shop", deltas.next("The shop"))
        assertEquals(" opens", deltas.next(" opens"))
        assertEquals(" at nine.", deltas.next(" at nine."))
        assertEquals("The shop opens at nine.", deltas.text)
        assertFalse(deltas.cumulative)
    }

    @Test
    fun `parts that each hold all the text so far give only what is new`() {
        val deltas = StreamDeltas()
        assertEquals("The shop", deltas.next("The shop"))
        assertEquals(" opens", deltas.next("The shop opens"))
        assertEquals(" at nine.", deltas.next("The shop opens at nine."))
        assertEquals("", deltas.next("The shop opens at nine."))
        assertEquals("The shop opens at nine.", deltas.text)
        assertTrue(deltas.cumulative)
    }

    @Test
    fun `a short new part that repeats a short start is still new text`() {
        // "No" then "No": too short to tell, so taken as written twice.
        val deltas = StreamDeltas()
        assertEquals("No", deltas.next("No"))
        assertEquals("No", deltas.next("No"))
        assertEquals("NoNo", deltas.text)
    }

    @Test
    fun `empty parts are nothing`() {
        val deltas = StreamDeltas()
        assertEquals("", deltas.next(""))
        assertEquals("Hi", deltas.next("Hi"))
        assertEquals("", deltas.next(""))
        assertEquals("Hi", deltas.text)
    }

    @Test
    fun `the log line for a part says when, how long and how it starts`() {
        assertEquals(
            "delta 3 at 1250 ms: 43 chars, new 5, \"The shop opens at nine. It is \"",
            StreamDeltas.logLine(index = 3, atMs = 1250, chunk = "The shop opens at nine. It is a small shop.", new = "small"),
        )
        // Line breaks are shown, not written: one log line per part.
        assertEquals(
            "delta 1 at 10 ms: 10 chars, new 10, \"Title\\nText\"",
            StreamDeltas.logLine(index = 1, atMs = 10, chunk = "Title\nText", new = "Title\nText"),
        )
    }
}
