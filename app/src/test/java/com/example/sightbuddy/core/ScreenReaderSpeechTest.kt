package com.example.sightbuddy.core

import com.example.sightbuddy.core.language.LanguageSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #15: the app's lines as TalkBack is given them, on two live-region
 * nodes, and when each line counts as said. Time is a fake clock moved by hand.
 *
 * Issue #24, the phone on 2026-10-27: a streamed answer read by TalkBack
 * stopped for 5-10 s at a time. Each line was handed over only once the one
 * before it counted as said, by an estimate of speech at about 14 characters
 * a second; at the user's TalkBack speed that is far slower than the speech.
 * The user's rule: never guess TalkBack's speed. A line now goes to TalkBack
 * as soon as it is ready, a small fixed gap after the one before.
 */
class ScreenReaderSpeechTest {

    private var nowMs = 0L
    private val timers = mutableListOf<Pair<Long, () -> Unit>>()
    private val speech = ScreenReaderSpeech(
        now = { nowMs },
        schedule = { delayMs, action -> timers += (nowMs + delayMs) to action },
    )
    private val gap = ScreenReaderSpeech.HANDOVER_GAP_MS

    /** Moves the clock to [toMs], running every timer due by then, in order. */
    private fun advanceTo(toMs: Long) {
        while (true) {
            val next = timers.filter { it.first <= toMs }.minByOrNull { it.first } ?: break
            timers.remove(next)
            nowMs = next.first
            next.second()
        }
        nowMs = toMs
    }

    private fun shownTexts() = speech.lines.value.map { it?.text }

    @Test
    fun `a line is put on a node for the screen reader`() {
        speech.say("A cup on a table.", flush = true, urgent = false)
        assertEquals(listOf("A cup on a table.", null), shownTexts())
    }

    @Test
    fun `the same line twice running is a new line each time`() {
        speech.say("Left", flush = true, urgent = true)
        val first = speech.lines.value.filterNotNull().single()
        advanceTo(gap)
        speech.say("Left", flush = true, urgent = true)
        val second = speech.lines.value.filterNotNull().single { it.text == "Left" }
        // Each line is its own node (the screen keys a node by the line's
        // serial), so the second "Left" appears as a node of its own.
        assertNotEquals(first.serial, second.serial)
    }

    @Test
    fun `urgent lines are assertive, the rest polite`() {
        speech.say("Hold steady", flush = true, urgent = true)
        assertTrue(speech.lines.value[0]!!.urgent)
        advanceTo(gap)
        speech.say("Looking for text", flush = true, urgent = false)
        assertFalse(speech.lines.value[1]!!.urgent)
    }

    @Test
    fun `a queued line goes to the screen reader a short gap after the one before, not when it counts as said`() {
        speech.say("The first sentence of a long answer, which takes a while to say.", flush = true, urgent = false)
        speech.say("The second.", flush = false, urgent = false)
        assertEquals(1, shownTexts().count { it != null })
        advanceTo(gap)
        // Handed over now: TalkBack queues it behind the first itself.
        assertTrue("The second." in shownTexts())
        // The first is still on its node: TalkBack may not have read it yet.
        assertTrue(shownTexts().any { it?.startsWith("The first") == true })
    }

    @Test
    fun `a burst of sentences goes over one gap apart, in order`() {
        val handed = mutableListOf<String>()
        val texts = listOf("One.", "Two.", "Three.", "Four.")
        texts.forEachIndexed { i, t -> speech.say(t, flush = i == 0, urgent = false) }
        var t = 0L
        repeat(texts.size) {
            advanceTo(t)
            shownTexts().filterNotNull().forEach { if (it !in handed) handed += it }
            t += gap
        }
        assertEquals(texts, handed)
    }

    @Test
    fun `a line handed over stays on its node while the next arrives`() {
        speech.say("One.", flush = true, urgent = false)
        advanceTo(gap)
        speech.say("Two.", flush = false, urgent = false)
        assertEquals(setOf("One.", "Two."), shownTexts().filterNotNull().toSet())
    }

    @Test
    fun `done comes at the estimate, not before`() {
        var done = false
        speech.say("Let me take a picture", flush = true, urgent = false, onDone = { done = true })
        val estimate = SpeechRoute.estimateMs("Let me take a picture")
        advanceTo(estimate - 1)
        assertFalse(done)
        assertTrue(speech.isSpeaking())
        advanceTo(estimate)
        assertTrue(done)
        assertFalse(speech.isSpeaking())
    }

    @Test
    fun `queued lines still count as said one after another, for whoever waits on them`() {
        val said = mutableListOf<String>()
        speech.say("First box of text.", flush = false, urgent = false, onDone = { said += "first" })
        speech.say("Second box.", flush = false, urgent = false, onDone = { said += "second" })
        val firstEnd = SpeechRoute.estimateMs("First box of text.")
        advanceTo(firstEnd)
        assertEquals(listOf("first"), said)
        assertTrue(speech.isSpeaking())
        advanceTo(firstEnd + SpeechRoute.estimateMs("Second box.") - 1)
        assertEquals(listOf("first"), said)
        advanceTo(firstEnd + SpeechRoute.estimateMs("Second box."))
        assertEquals(listOf("first", "second"), said)
        assertFalse(speech.isSpeaking())
    }

    @Test
    fun `a long line leaves its node after a moment, while it still counts as being said`() {
        val page = "A page of text. ".repeat(100)
        speech.say(page, flush = true, urgent = false)
        advanceTo(ScreenReaderSpeech.NODE_HOLD_MS)
        // TalkBack took the text when it appeared; a swipe now meets nothing.
        assertEquals(listOf<String?>(null, null), shownTexts())
        assertTrue(speech.isSpeaking())
        advanceTo(SpeechRoute.estimateMs(page))
        assertFalse(speech.isSpeaking())
    }

    @Test
    fun `a flush drops the earlier line's done, as the app voice does`() {
        var firstDone = false
        var secondDone = false
        speech.say("The first answer.", flush = true, urgent = false, onDone = { firstDone = true })
        advanceTo(100)
        speech.say("Second", flush = true, urgent = false, onDone = { secondDone = true })
        advanceTo(60_000)
        assertFalse(firstDone)
        assertTrue(secondDone)
    }

    @Test
    fun `a flush drops a queued line before it is shown`() {
        speech.say("First box of text.", flush = false, urgent = false)
        speech.say("Second box.", flush = false, urgent = false)
        speech.say("Left", flush = true, urgent = true)
        advanceTo(60_000)
        // "Second box." never reached a node.
        assertNull(speech.lines.value.firstOrNull { it?.text == "Second box." })
    }

    @Test
    fun `a flush right after a line still waits the gap, so the two are separate changes`() {
        speech.say("One.", flush = true, urgent = false)
        speech.say("Left", flush = true, urgent = true)
        assertFalse("Left" in shownTexts())
        advanceTo(gap)
        assertTrue("Left" in shownTexts())
    }

    @Test
    fun `stop empties both nodes and drops every done`() {
        var done = false
        speech.say("An answer.", flush = true, urgent = false, onDone = { done = true })
        speech.say("Queued.", flush = false, urgent = false, onDone = { done = true })
        speech.stop()
        assertEquals(listOf<String?>(null, null), shownTexts())
        assertFalse(speech.isSpeaking())
        advanceTo(60_000)
        assertFalse(done)
        assertEquals(listOf<String?>(null, null), shownTexts())
    }

    @Test
    fun `a line after a stop is shown`() {
        speech.say("Left", flush = true, urgent = true)
        speech.stop()
        advanceTo(gap)
        speech.say("Left", flush = true, urgent = true)
        assertEquals(1, speech.lines.value.count { it?.text == "Left" })
    }

    @Test
    fun `a line keeps the languages of its parts, for TalkBack (issue #26)`() {
        val arabic = listOf(LanguageSpan(12, 21, "ar"))
        speech.say("Here it is: مرحبا بكم", flush = true, urgent = false, languages = arabic)
        assertEquals(arabic, speech.lines.value.filterNotNull().single().languages)
    }
}
