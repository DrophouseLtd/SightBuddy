package com.example.sightbuddy.core

/**
 * What the app's sounds and vibration may do, from Settings' two switches,
 * Sound effects and Vibration (both on by default). Each switch is on its own:
 * with both on, a cue plays its sound and vibrates too. Speech is not part of
 * this; the voice is never switched off here. [Cues] applies the rule.
 */
object FeedbackOutputs {

    /** Whether a cue, or a guidance note, is played as a sound, felt as a vibration, or both. */
    data class Outputs(val sound: Boolean, val vibration: Boolean)

    /** The recording start and stop and the shutter: each switch gives its own output. */
    fun cue(soundEffects: Boolean, vibration: Boolean): Outputs = Outputs(soundEffects, vibration)

    /** The waiting tick: heard with Sound effects, never felt. */
    fun tick(soundEffects: Boolean): Outputs = Outputs(soundEffects, vibration = false)

    /**
     * Find objects' pitch guidance, asked for only while Pitch feedback is on
     * (off, the directions are spoken). It is never silent: the rising note,
     * the quickening pulse, or both.
     * - Both switches on: the note and the pulse.
     * - Sound effects only: the note.
     * - Vibration only: the pulse, or the note on a phone that cannot vibrate.
     * - Both off: the note, since Pitch feedback asks for guidance.
     */
    fun pitchGuidance(soundEffects: Boolean, vibration: Boolean, hasVibrator: Boolean): Outputs {
        val pulse = vibration && hasVibrator
        return Outputs(sound = soundEffects || !pulse, vibration = pulse)
    }
}
