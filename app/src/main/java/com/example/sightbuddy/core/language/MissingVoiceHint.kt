package com.example.sightbuddy.core.language

/**
 * The one-time hint about a missing voice (issue #26, decided 2026-10-24):
 * the first time text in a language the phone has no voice for is read, a
 * pop-up says once that a voice for it can be added in the phone's
 * settings. Then never again: that it was shown is stored.
 *
 * The pop-up must not cut the reading it was found in (it is a pop-up like
 * the others, which silence the app): the language is remembered
 * ([voiceMissing]) and shown once the speech has ended ([toShow]), checked
 * [QUIET_CHECKS] times running so a gap between two sentences does not
 * count, with a feature in front and nothing else open. It is marked shown
 * when it is shown.
 *
 * @param shown whether the hint has been shown, ever (stored).
 * @param markShown stores that it has.
 */
class MissingVoiceHint(
    private val shown: () -> Boolean,
    private val markShown: () -> Unit,
) {
    private var pending: String? = null
    private var quietChecks = 0

    /** A language is waiting to be told about. */
    val waiting: Boolean get() = pending != null

    /** [missing] had no voice as a line was said: the first is remembered, unless the hint was shown. */
    fun voiceMissing(missing: Set<String>) {
        if (pending != null || missing.isEmpty() || shown()) return
        pending = missing.first()
        quietChecks = 0
    }

    /**
     * The language to show the hint for now, or null. Called every so often
     * while [waiting]. [speechOver]: nothing is being said, no answer is
     * being written, the page reader is not reading. [featureInFront]: a
     * feature, with no pop-up or Settings over it, and the app in front.
     */
    fun toShow(speechOver: Boolean, featureInFront: Boolean): String? {
        val language = pending ?: return null
        if (shown()) {
            pending = null
            return null
        }
        if (!speechOver || !featureInFront) {
            quietChecks = 0
            return null
        }
        if (++quietChecks < QUIET_CHECKS) return null
        pending = null
        markShown()
        return language
    }

    companion object {
        /** Quiet checks running before the hint shows. */
        private const val QUIET_CHECKS = 2

        /** Whether nothing is being read, recorded or waited for: the hint's quiet. */
        fun readingOver(
            speaking: Boolean = false,
            answerWriting: Boolean = false,
            pageReading: Boolean = false,
            listening: Boolean = false,
            answerOnItsWay: Boolean = false,
        ): Boolean = !speaking && !answerWriting && !pageReading && !listening && !answerOnItsWay
    }
}
