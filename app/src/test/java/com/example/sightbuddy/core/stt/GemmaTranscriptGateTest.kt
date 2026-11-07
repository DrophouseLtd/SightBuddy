package com.example.sightbuddy.core.stt

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #25: Gemma hallucinates on audio with no speech. A transcript that is
 * the model's own words — an echo of its instruction, or a refusal — is not
 * speech and must not be shown or asked.
 */
class GemmaTranscriptGateTest {

    @Test
    fun `an instruction echo is not speech`() {
        assertTrue(GemmaTranscriptGate.isNotSpeech("Yo, is this Rivera game? Because Transcribe this audio."))
        assertTrue(GemmaTranscriptGate.isNotSpeech("Captain, that's Transcribe this audio."))
    }

    @Test
    fun `a refusal is not speech`() {
        assertTrue(GemmaTranscriptGate.isNotSpeech("I'm sorry, I cannot transcribe the audio."))
    }

    @Test
    fun `invented words are not caught by the gate, only by the VAD`() {
        // "Take a big shot and a yeah do a backflip." is invented from silence;
        // only the Silero VAD can catch this kind, not the text.
        assertFalse(GemmaTranscriptGate.isNotSpeech("Take a big shot and a yeah do a backflip."))
    }

    @Test
    fun `real speech is not refused`() {
        assertFalse(GemmaTranscriptGate.isNotSpeech("What colour is the cup?"))
        assertFalse(GemmaTranscriptGate.isNotSpeech("Read the menu"))
    }

    @Test
    fun `blank text is not speech`() {
        assertTrue(GemmaTranscriptGate.isNotSpeech("   "))
    }
}
