package com.example.sightbuddy.core.stt

/**
 * Decides whether a Gemma transcript is real speech or the model's own words
 * on silence (issue #25).
 *
 * Gemma hallucinates on audio with no speech: it echoes its own instruction
 * ("Transcribe this audio."), refuses ("I'm sorry, I cannot transcribe the
 * audio."), or invents words. Whisper gates on Silero VAD; the Gemma path has
 * no speech check, so a silent recording became a made-up "you said" message.
 * A transcript that is the model's own words is treated as "nothing heard".
 */
object GemmaTranscriptGate {

    /**
     * The prompt Gemma is given to transcribe (see
     * [com.example.sightbuddy.core.llm.LocalGemma]). An echo of it is the model
     * repeating its instruction, not speech.
     */
    private const val TRANSCRIBE_PROMPT = "transcribe this audio"

    /** Refusal phrases Gemma produces on silence. */
    private val REFUSALS = listOf(
        "cannot transcribe",
        "can't transcribe",
        "could not transcribe",
        "couldn't transcribe",
        "i'm sorry",
        "i am sorry",
    )

    /**
     * True when [text] is the model's own words rather than speech: nothing
     * was heard, so nothing should be shown or asked.
     */
    fun isNotSpeech(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return true
        val lower = trimmed.lowercase()
        if (lower.contains(TRANSCRIBE_PROMPT)) return true
        return REFUSALS.any { lower.contains(it) }
    }
}
