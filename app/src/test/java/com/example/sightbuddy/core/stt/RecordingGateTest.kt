package com.example.sightbuddy.core.stt

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #25: a recording ended by anything but the user's own stop must have
 * its transcript discarded — nothing shown in the chat, nothing asked.
 */
class RecordingGateTest {

    @Test
    fun `a cancelled recording discards its transcript`() {
        val gate = RecordingGate()
        gate.cancel(open = true)
        assertTrue(gate.discardsTranscript())
    }

    @Test
    fun `a question already stopped keeps its transcript when audio is cut`() {
        // Asked, stopped, being transcribed; then Capture, Share or a menu cuts the audio.
        val gate = RecordingGate()
        gate.cancel(open = false)
        assertFalse(gate.discardsTranscript())
    }

    @Test
    fun `leaving drops a transcript still on its way`() {
        val gate = RecordingGate()
        gate.discardPending()
        assertTrue(gate.discardsTranscript())
    }

    @Test
    fun `a user-stopped recording keeps its transcript`() {
        val gate = RecordingGate()
        assertFalse(gate.discardsTranscript())
    }

    @Test
    fun `a new recording starts clean`() {
        val gate = RecordingGate()
        gate.cancel(open = true)
        gate.reset()
        assertFalse(gate.discardsTranscript())
    }
}
