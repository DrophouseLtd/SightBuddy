package com.example.sightbuddy.core

/**
 * Whether the direction cues ("Left", "Closer", "Hold steady") go to the
 * screen reader with everything else when the user speaks with it (issue #15).
 * They go as assertive lines, which cut what the screen reader is saying, as
 * the app's voice cuts itself for a cue. False keeps them on the app's voice
 * while everything else goes to the screen reader: the fallback, should
 * TalkBack drop, delay or pile them up on the phone.
 */
const val CUES_THROUGH_SCREEN_READER = true

/**
 * Where one line of the app's speech goes: the app's own voice, the user's
 * screen reader (issue #15), or nowhere. [TTSService.speak] asks this for
 * every line, so every caller follows it.
 */
object SpeechRoute {
    enum class Output { NONE, APP_VOICE, SCREEN_READER }

    /** [shown]: the line is the one the scanning features show on screen, said or not. */
    data class Decision(val output: Output, val shown: Boolean)

    /**
     * @param recording a recording holds the microphone floor: nothing is said
     *   to either output, forced or not, and nothing is kept to say later, so
     *   no voice goes into the recording. What TalkBack was already saying is
     *   cut as the floor is taken ([cutsScreenReaderForRecording]).
     * @param suppressed the feature's output is held back (Settings, a pop-up,
     *   a recording); [force] lines still come through.
     * @param muted the user's Mute: silent on both outputs, still shown.
     * @param screenReaderChosen the user's Speak with screen reader setting.
     * @param screenReaderRunning a screen reader is on now. Without one the
     *   app's voice speaks, whatever the setting.
     * @param appInFront the app's window is in front and has the focus. When
     *   not (in the background, or under a pop-up window), Android does not
     *   pass the app's live-region changes to the screen reader, so the line
     *   would be lost: the app's voice says it instead.
     * @param cue a direction cue; see [CUES_THROUGH_SCREEN_READER].
     */
    fun decide(
        text: String,
        recording: Boolean,
        suppressed: Boolean,
        force: Boolean,
        muted: Boolean,
        screenReaderChosen: Boolean,
        screenReaderRunning: Boolean,
        appInFront: Boolean,
        cue: Boolean,
        cuesToScreenReader: Boolean = CUES_THROUGH_SCREEN_READER,
    ): Decision {
        if (recording || text.isBlank()) return Decision(Output.NONE, shown = false)
        // Suppressed: this feature does not own the screen either, so the line is
        // not shown as if it had been said.
        if (suppressed && !force) return Decision(Output.NONE, shown = false)
        if (muted && !force) return Decision(Output.NONE, shown = true)
        val toScreenReader = screenReaderChosen && screenReaderRunning && appInFront &&
            (!cue || cuesToScreenReader)
        return Decision(if (toScreenReader) Output.SCREEN_READER else Output.APP_VOICE, shown = true)
    }

    /**
     * About how long the screen reader takes to say [text]: TalkBack never
     * says when it has finished, and its voice and speed are the user's. Set
     * for a normal speed (about 14 characters a second, plus a moment for
     * TalkBack to pick the line up), so on a faster TalkBack a line waits a
     * little longer than needed rather than being talked over.
     */
    fun estimateMs(text: String): Long = START_MS + text.length * MS_PER_CHAR

    /**
     * Whether a recording starting cuts the screen reader off. Only while the
     * app speaks through it: a long answer handed to TalkBack would otherwise
     * go on into the open microphone when the recording starts without a
     * touch (the volume keys), and what TalkBack would say about the screen
     * then is held back while the microphone records anyway.
     */
    fun cutsScreenReaderForRecording(screenReaderChosen: Boolean, screenReaderRunning: Boolean): Boolean =
        screenReaderChosen && screenReaderRunning

    /**
     * What the app's own voice says for [text] (issue #23, decided
     * 2026-10-24): Android's text-to-speech refuses a text of [maxChars]
     * (`getMaxSpeechInputLength()`, 4,000) or more, silently. Such a text is
     * not split: the voice says [tooLong] instead, and the text stays in the
     * chat, to read with TalkBack.
     */
    fun forVoice(text: String, maxChars: Int, tooLong: String): String =
        if (text.length >= maxChars) tooLong else text

    private const val START_MS = 300L
    private const val MS_PER_CHAR = 70L
}
