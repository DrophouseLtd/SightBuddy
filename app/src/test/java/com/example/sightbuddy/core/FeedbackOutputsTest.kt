package com.example.sightbuddy.core

import com.example.sightbuddy.core.FeedbackOutputs.Outputs
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Settings has two switches, Sound effects and Vibration, both on by default.
 * Each is on its own: with both on, a cue plays its sound and vibrates. They
 * replaced one either-or switch that gave vibration instead of sound.
 *
 * Find objects' pitch guidance is its own case: while Pitch feedback is on it
 * never goes quiet, whatever the two switches say.
 */
class FeedbackOutputsTest {

    @Test
    fun withBothOnTheRecordingCueSoundsAndVibrates() {
        assertEquals(Outputs(sound = true, vibration = true), FeedbackOutputs.cue(soundEffects = true, vibration = true))
    }

    /** The waiting tick is never felt: it follows Sound effects only. */
    @Test
    fun theWaitingTickIsHeardButNeverFelt() {
        assertEquals(Outputs(sound = true, vibration = false), FeedbackOutputs.tick(soundEffects = true))
        assertEquals(Outputs(sound = false, vibration = false), FeedbackOutputs.tick(soundEffects = false))
    }

    @Test
    fun eachSwitchTakesAwayOnlyItsOwnOutputFromTheCue() {
        assertEquals(Outputs(sound = false, vibration = true), FeedbackOutputs.cue(soundEffects = false, vibration = true))
        assertEquals(Outputs(sound = true, vibration = false), FeedbackOutputs.cue(soundEffects = true, vibration = false))
    }

    @Test
    fun withBothOffTheCueIsNeitherHeardNorFelt() {
        assertEquals(Outputs(sound = false, vibration = false), FeedbackOutputs.cue(soundEffects = false, vibration = false))
    }

    @Test
    fun withBothOnPitchGuidancePlaysTheNoteAndPulses() {
        assertEquals(
            Outputs(sound = true, vibration = true),
            FeedbackOutputs.pitchGuidance(soundEffects = true, vibration = true, hasVibrator = true),
        )
    }

    @Test
    fun withSoundOnlyPitchGuidancePlaysTheNote() {
        assertEquals(
            Outputs(sound = true, vibration = false),
            FeedbackOutputs.pitchGuidance(soundEffects = true, vibration = false, hasVibrator = true),
        )
    }

    @Test
    fun withVibrationOnlyPitchGuidancePulsesWithoutTheNote() {
        assertEquals(
            Outputs(sound = false, vibration = true),
            FeedbackOutputs.pitchGuidance(soundEffects = false, vibration = true, hasVibrator = true),
        )
    }

    /** A pulse the phone cannot give would leave the guidance silent. */
    @Test
    fun withVibrationOnlyOnAPhoneThatCannotVibrateTheNotePlaysInstead() {
        assertEquals(
            Outputs(sound = true, vibration = false),
            FeedbackOutputs.pitchGuidance(soundEffects = false, vibration = true, hasVibrator = false),
        )
    }

    /** While Pitch feedback is on, its guidance is never off: the note stays. */
    @Test
    fun withBothOffPitchGuidanceStillPlaysTheNote() {
        assertEquals(
            Outputs(sound = true, vibration = false),
            FeedbackOutputs.pitchGuidance(soundEffects = false, vibration = false, hasVibrator = true),
        )
    }
}
