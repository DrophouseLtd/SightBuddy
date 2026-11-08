package com.example.sightbuddy.features.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #24: an answer is spoken sentence by sentence as it is written. From
 * the phone on 2026-10-24: the log said "first sentence at -395840755 ms" for
 * an answer of which nothing was said; the waiting tick ran on over the
 * speech; and an error said in place of an answer was never spoken.
 */
class AnswerSpeakerTest {

    private class Harness(var here: Boolean = true, var accepts: Boolean = true) {
        var clock = 1_000L
        val said = mutableListOf<Pair<String, Boolean>>()
        var firstSentences = 0
        val speaker = AnswerSpeaker(
            stillHere = { here },
            say = { sentence, first -> if (accepts) said += sentence to first; accepts },
            now = { clock },
            onFirstSentence = { firstSentences++ },
        )
    }

    @Test
    fun `sentences are said as they are complete, the first cutting what was said before`() {
        val h = Harness()
        h.speaker.add("The shop opens")
        assertEquals(emptyList<Pair<String, Boolean>>(), h.said)
        h.speaker.add(" at nine. It closes")
        h.speaker.add(" at five. And")
        h.speaker.finish("The shop opens at nine. It closes at five. And")
        assertEquals(
            listOf("The shop opens at nine." to true, "It closes at five." to false, "And" to false),
            h.said,
        )
    }

    @Test
    fun `an answer that was not written part by part is said whole when it comes`() {
        // An error said in place of an answer, or OpenAI's answer, comes all at once.
        val h = Harness()
        h.speaker.finish("The on-device model could not answer. Please try again.")
        assertEquals(listOf("The on-device model could not answer. Please try again." to true), h.said)
    }

    @Test
    fun `a cancelled answer says nothing more, not even its last part`() {
        val h = Harness()
        h.speaker.add("First. Second, half")
        h.speaker.finish(CANCELLED)
        assertEquals(listOf("First." to true), h.said)
    }

    @Test
    fun `an answer that fails part way says the failure after what was said, not its broken end`() {
        // The audit, 2026-10-27: the part written was kept as if it were the answer.
        val h = Harness()
        h.speaker.add("The label says. Take two")
        h.speaker.finish(FAILED, failedPartWay = true)
        assertEquals(listOf("The label says." to true, FAILED to false), h.said)
        // The bubble kept what was said.
        assertEquals("The label says.", h.speaker.shown)
    }

    @Test
    fun `an answer that fails before anything was said says the failure first`() {
        val h = Harness()
        h.speaker.add("Take two")
        h.speaker.finish(FAILED, failedPartWay = true)
        assertEquals(listOf(FAILED to true), h.said)
    }

    @Test
    fun `after leaving the chat nothing more of the answer is said, even back in it`() {
        val h = Harness()
        h.speaker.add("One. ")
        h.here = false
        h.speaker.add("Two. ")
        h.here = true
        h.speaker.add("Three. ")
        h.speaker.finish("One. Two. Three.")
        assertEquals(listOf("One." to true), h.said)
    }

    @Test
    fun `a sentence the voice refuses silences the rest, so no answer resumes half way`() {
        // Mute, a recording taking the floor, a pop-up: the voice says no.
        val h = Harness()
        h.speaker.add("One. ")
        h.accepts = false
        h.speaker.add("Two. ")
        h.accepts = true
        h.speaker.add("Three. ")
        h.speaker.finish("One. Two. Three.")
        assertEquals(listOf("One." to true), h.said)
    }

    @Test
    fun `the first sentence is reported once, so the waiting tick can stop`() {
        val h = Harness()
        h.speaker.add("Still writing")
        assertEquals(0, h.firstSentences)
        h.speaker.add(". More. ")
        h.speaker.add("And more. ")
        assertEquals(1, h.firstSentences)
    }

    @Test
    fun `the bubble grows by whole sentences, keeping the answer's own lines`() {
        val h = Harness()
        h.speaker.add("Instructions\nTake two tab")
        assertEquals("Instructions", h.speaker.shown)
        h.speaker.add("lets. Do not")
        assertEquals("Instructions\nTake two tablets.", h.speaker.shown)
    }

    @Test
    fun `an answer of which nothing was said is logged so, not as a negative time`() {
        val h = Harness(here = false)
        h.speaker.add("Read it all. ")
        h.clock += 68_958
        h.speaker.finish("Read it all.")
        val line = h.speaker.logLine()
        assertEquals("nothing spoken; whole answer at 68958 ms; deltas 1; sentences spoken 0", line)
        assertFalse(line.contains("-"))
    }

    @Test
    fun `the log line says when the first sentence was said and when the answer ended`() {
        val h = Harness()
        h.clock += 300
        h.speaker.add("The shop ")
        h.clock += 900
        h.speaker.add("opens at nine. ")
        h.clock += 3_800
        h.speaker.add("Bye.")
        h.speaker.finish("The shop opens at nine. Bye.")
        assertEquals("first sentence spoken at 1200 ms; whole answer at 5000 ms; deltas 3; sentences spoken 2", h.speaker.logLine())
        assertTrue(h.said.isNotEmpty())
    }

    private companion object {
        const val CANCELLED = ""
        const val FAILED = "The on-device model could not answer. Please try again."
    }
}
