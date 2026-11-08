package com.example.sightbuddy.core

import com.example.sightbuddy.core.SpeechRoute.Output
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Issue #15: which of the app's lines go to the screen reader, and when. */
class SpeechRouteTest {

    private fun decide(
        text: String = "A cup on a table.",
        recording: Boolean = false,
        suppressed: Boolean = false,
        force: Boolean = false,
        muted: Boolean = false,
        chosen: Boolean = true,
        running: Boolean = true,
        inFront: Boolean = true,
        cue: Boolean = false,
        cuesToScreenReader: Boolean = true,
    ) = SpeechRoute.decide(
        text = text,
        recording = recording,
        suppressed = suppressed,
        force = force,
        muted = muted,
        screenReaderChosen = chosen,
        screenReaderRunning = running,
        appInFront = inFront,
        cue = cue,
        cuesToScreenReader = cuesToScreenReader,
    )

    @Test
    fun `a line while the app is not in front goes to the app voice, not to a node nobody reads`() {
        // A download finishing in the background, or a line forced under a
        // pop-up window: TalkBack is not told about the app's window then.
        assertEquals(Output.APP_VOICE, decide(inFront = false, force = true).output)
        assertEquals(Output.APP_VOICE, decide(inFront = false, cue = true).output)
        assertEquals(Output.SCREEN_READER, decide(inFront = true).output)
    }

    @Test
    fun `a recording cuts the screen reader off only while the app speaks through it`() {
        assertTrue(SpeechRoute.cutsScreenReaderForRecording(screenReaderChosen = true, screenReaderRunning = true))
        // Setting off: what TalkBack says is not the app's to cut.
        assertFalse(SpeechRoute.cutsScreenReaderForRecording(screenReaderChosen = false, screenReaderRunning = true))
        assertFalse(SpeechRoute.cutsScreenReaderForRecording(screenReaderChosen = true, screenReaderRunning = false))
    }

    @Test
    fun `the setting off keeps the app voice, screen reader or not`() {
        assertEquals(Output.APP_VOICE, decide(chosen = false, running = true).output)
        assertEquals(Output.APP_VOICE, decide(chosen = false, running = false).output)
    }

    @Test
    fun `the setting on goes to the screen reader only while one is running`() {
        assertEquals(Output.SCREEN_READER, decide(chosen = true, running = true).output)
        assertEquals(Output.APP_VOICE, decide(chosen = true, running = false).output)
    }

    @Test
    fun `forced lines follow the setting too`() {
        assertEquals(Output.SCREEN_READER, decide(force = true).output)
        assertEquals(Output.SCREEN_READER, decide(force = true, suppressed = true).output)
    }

    @Test
    fun `direction cues go to the screen reader, or stay on the app voice by one switch`() {
        assertEquals(Output.SCREEN_READER, decide(cue = true, cuesToScreenReader = true).output)
        assertEquals(Output.APP_VOICE, decide(cue = true, cuesToScreenReader = false).output)
        // The switch is about cues only.
        assertEquals(Output.SCREEN_READER, decide(cue = false, cuesToScreenReader = false).output)
    }

    @Test
    fun `nothing at all is said during a recording, to either output, forced or not`() {
        for (chosen in listOf(true, false)) for (force in listOf(true, false)) for (cue in listOf(true, false)) {
            val d = decide(recording = true, chosen = chosen, force = force, cue = cue)
            assertEquals("chosen=$chosen force=$force cue=$cue", Output.NONE, d.output)
            assertFalse(d.shown)
        }
    }

    @Test
    fun `mute silences both outputs but the line is still shown`() {
        val d = decide(muted = true)
        assertEquals(Output.NONE, d.output)
        assertTrue(d.shown)
        assertEquals(Output.SCREEN_READER, decide(muted = true, force = true).output)
    }

    @Test
    fun `suppressed speech is neither said nor shown, unless forced`() {
        val d = decide(suppressed = true)
        assertEquals(Output.NONE, d.output)
        assertFalse(d.shown)
    }

    @Test
    fun `a blank line is nothing`() {
        assertEquals(Output.NONE, decide(text = "  ").output)
    }

    @Test
    fun `the estimate grows with the text and keeps the shutter note short`() {
        val note = SpeechRoute.estimateMs("Let me take a picture")
        assertTrue("note $note ms", note in 1_000L..3_000L)
        // Under the longest the shutter waits for its note.
        assertTrue(note < 5_000L)
        assertTrue(SpeechRoute.estimateMs("Left") < note)
        assertTrue(SpeechRoute.estimateMs("x".repeat(1_000)) > 30_000L)
    }

    // Issue #23, decided 2026-10-24: Android's voice refuses text at its limit
    // (getMaxSpeechInputLength, 4,000) and says nothing. The app's voice says
    // so instead; the text stays in the chat, for TalkBack.

    private val tooLong = "The text is too long to read aloud."

    @Test
    fun `text under Android's limit is said as it is`() {
        assertEquals("A cup on a table.", SpeechRoute.forVoice("A cup on a table.", maxChars = 4_000, tooLong = tooLong))
        val longest = "a".repeat(3_999)
        assertEquals(longest, SpeechRoute.forVoice(longest, maxChars = 4_000, tooLong = tooLong))
    }

    @Test
    fun `text at or over Android's limit is said as too long, not silently dropped`() {
        assertEquals(tooLong, SpeechRoute.forVoice("a".repeat(4_000), maxChars = 4_000, tooLong = tooLong))
        assertEquals(tooLong, SpeechRoute.forVoice("a".repeat(12_000), maxChars = 4_000, tooLong = tooLong))
    }
}
