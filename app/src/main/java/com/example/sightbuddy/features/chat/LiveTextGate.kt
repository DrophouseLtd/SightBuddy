package com.example.sightbuddy.features.chat

/**
 * Whether Text chat's live text is read out, and whether it is drawn.
 *
 * Both need only the Live text setting, until the chat starts. The boxes are
 * drawn with Preview off too, where they sit, on the plain background: a
 * screen reader can then find a box and select it (decided 2026-10-31).
 */
data class LiveTextGate(
    /** Text chat is idle: no guidance, question, recording or reading under way. */
    val idle: Boolean,
    /** The Live text setting. */
    val settingOn: Boolean,
    /** A page, a question, an answer or a note: the chat's messages are on screen. */
    val chatStarted: Boolean,
) {
    val reads: Boolean get() = idle && settingOn && !chatStarted
    val draws: Boolean get() = reads
}
