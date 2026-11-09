package com.example.sightbuddy.core.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #26: the app's voice says a mixed text in parts, one per voice, each
 * an utterance of its own. Whoever spoke the text (the page reader follows
 * its place by the voice's progress) must still see one utterance: progress
 * at its place in the whole text, under the text's own id, and the end only
 * when the last part ends.
 */
class SpokenPartsTest {

    private val parts = SpokenParts()

    @Test
    fun `the last part has the text's own id, the others ids of their own`() {
        assertEquals("READER", parts.idOf("READER", index = 2, last = true))
        val inner = parts.idOf("READER", index = 0, last = false)
        assertTrue(inner != "READER")
        assertTrue(parts.isInnerPart(inner))
        assertFalse(parts.isInnerPart("READER"))
    }

    @Test
    fun `progress in a part is reported at its place in the whole text`() {
        val first = parts.idOf("READER", index = 0, last = false)
        parts.started(first, offset = 0)
        parts.started("READER", offset = 12)
        assertEquals("READER" to 3, parts.inWhole(first, 3))
        assertEquals("READER" to 15, parts.inWhole("READER", 3))
    }

    @Test
    fun `a line said whole is reported as it is`() {
        assertEquals("TTS_1" to 7, parts.inWhole("TTS_1", 7))
    }

    @Test
    fun `a line said whole under an id used before for parts is not shifted`() {
        parts.started("READER", offset = 12)
        parts.forget("READER")
        assertEquals("READER" to 3, parts.inWhole("READER", 3))
    }
}
