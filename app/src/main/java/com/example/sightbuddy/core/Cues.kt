package com.example.sightbuddy.core

/**
 * The one way the app makes a sound or a vibration. Every cue (the recording
 * start and stop, the shutter, the waiting tick) and Find objects' pitch
 * guidance go through here, so Settings' Sound effects and Vibration are asked
 * every time; [FeedbackOutputs] holds the rule. The devices behind [Sound] and
 * [Vibration] are wired up in CueWiring.kt, and check their switch again
 * themselves for the cues. Speech never comes through here.
 *
 * The switches are read at each cue, so a change in Settings applies at once.
 */
class Cues(
    private val soundOn: () -> Boolean,
    private val vibrationOn: () -> Boolean,
    private val sound: Sound,
    private val vibration: Vibration,
) {
    /**
     * [felt]: whether Vibration gives the cue a tap as well. [alongside]: it
     * always sounds over whatever already plays: the tick starts while the
     * camera click or the recording-stop cue may still sound, and cut them.
     */
    enum class Cue(val felt: Boolean, val alongside: Boolean = false) {
        RECORDING_START(felt = true),
        RECORDING_STOP(felt = true),

        /** The camera click, as a picture is taken. */
        SHUTTER(felt = true),

        /** One beat of the waiting tick, while an answer is on its way. Heard only. */
        TICK(felt = false, alongside = true),
    }

    /** What is heard: the cue samples and the pitch tone. */
    interface Sound {
        /** [alongside]: over whatever already sounds, instead of cutting it. */
        fun play(cue: Cue, alongside: Boolean)
        fun tone(proximity: Float)
        fun stopTone()
    }

    /** What is felt. [present] is false on a phone that cannot vibrate. */
    interface Vibration {
        val present: Boolean

        /** A short, light tap. */
        fun tap()
        fun pulse(proximity: Float)
        fun stopPulse()
    }

    /** Gives [cue]: heard if Sound effects is on, felt if Vibration is on and the cue is [Cue.felt]. */
    fun give(cue: Cue, alongside: Boolean = false) {
        val out = if (cue.felt) {
            FeedbackOutputs.cue(soundOn(), vibrationOn())
        } else {
            FeedbackOutputs.tick(soundOn())
        }
        if (out.vibration) vibration.tap()
        if (out.sound) sound.play(cue, alongside || cue.alongside)
    }

    /**
     * Find objects' pitch guidance at [proximity], 0 far off and 1 dead centre:
     * the rising tone, the quickening pulse, or both. Never silent while it
     * runs; see [FeedbackOutputs.pitchGuidance].
     */
    fun guide(proximity: Float) {
        val out = FeedbackOutputs.pitchGuidance(soundOn(), vibrationOn(), vibration.present)
        // A switch changed while the guidance runs silences its own half.
        if (out.sound) sound.tone(proximity) else sound.stopTone()
        if (out.vibration) vibration.pulse(proximity) else vibration.stopPulse()
    }

    fun stopGuidance() {
        sound.stopTone()
        vibration.stopPulse()
    }
}
