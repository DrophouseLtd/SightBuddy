package com.example.sightbuddy.core.stt

/**
 * Whether a recording's transcript may be delivered (issue #25).
 *
 * A recording ended by the user's own stop (Ask pressed again, volume-down
 * Ask) is transcribed and delivered as today. A recording ended by anything
 * else — a feature switch, Settings, the app going to the background, a
 * pop-up opening — is discarded: nothing is shown in the chat, nothing is
 * asked.
 */
class RecordingGate {
    private var cancelled = false

    /**
     * Audio was cut by something other than the user's own stop. Only a
     * recording still [open] is discarded: one the user already stopped, still
     * being transcribed, keeps its question when a Capture press, Share or a
     * menu cuts the audio in those seconds.
     */
    fun cancel(open: Boolean) {
        if (open) cancelled = true
    }

    /**
     * The user left (another feature, Settings, the background): a transcript
     * still on its way belongs to what was left, open or not.
     */
    fun discardPending() {
        cancelled = true
    }

    /** A new recording starts clean. */
    fun reset() {
        cancelled = false
    }

    /** True when the transcript of this recording must be dropped. */
    fun discardsTranscript(): Boolean = cancelled
}
