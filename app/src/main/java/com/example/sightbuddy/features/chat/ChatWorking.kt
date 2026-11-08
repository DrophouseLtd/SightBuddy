package com.example.sightbuddy.features.chat

/**
 * Whether Image chat or Text chat is working on an answer: what the waiting
 * tick and the "Working" line follow.
 *
 * Not once the answer is being spoken (issue #24): a streamed answer is still
 * being written, and its question still pending, while its first sentences
 * are said, and the tick played on over them (the phone, 2026-10-24).
 *
 * Nor while the user is still aiming for the picture: a question asked before
 * the scan waits through the guidance, but nothing is worked on until the
 * picture is taken (the phone, 2026-10-27). Nor while the picture is still
 * being taken: the photo takes over a second, and the tick came before the
 * shutter (the phone, 2026-10-31).
 */
object ChatWorking {
    fun working(
        inChat: Boolean,
        pictureInFlight: Boolean,
        questionWaiting: Boolean,
        processing: Boolean,
        answerSpeaking: Boolean,
        aiming: Boolean = false,
        takingPicture: Boolean = false,
    ): Boolean = inChat && !aiming && !takingPicture && !answerSpeaking &&
        (pictureInFlight || questionWaiting || processing)

    /**
     * Whether this beat of the waiting tick sounds: only while working and
     * nothing is being said, by the app's voice or through the screen reader
     * (issue #24, the phone on 2026-10-27). The tick fills silence; the
     * answer's first sentence is not the only speech while something is on
     * its way: the line before a photo, direction cues, an answer still being
     * said when the next question is already asked.
     */
    fun tickBeat(working: Boolean, speaking: Boolean): Boolean = working && !speaking
}
