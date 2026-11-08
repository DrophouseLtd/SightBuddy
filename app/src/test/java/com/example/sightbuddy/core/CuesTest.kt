package com.example.sightbuddy.core

import com.example.sightbuddy.core.Cues.Cue
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Every cue the app gives, heard or felt, goes through [Cues], which asks
 * Settings' Sound effects and Vibration each time. These fakes record what
 * reached the sound pool, the tone and the vibrator.
 */
class CuesTest {

    private class FakeSound : Cues.Sound {
        val played = mutableListOf<String>()
        override fun play(cue: Cue, alongside: Boolean) {
            played += if (alongside) "$cue alongside" else "$cue"
        }
        override fun tone(proximity: Float) { played += "tone $proximity" }
        override fun stopTone() { played += "stop tone" }
    }

    private class FakeVibration(override val present: Boolean = true) : Cues.Vibration {
        val felt = mutableListOf<String>()
        override fun tap() { felt += "tap" }
        override fun pulse(proximity: Float) { felt += "pulse $proximity" }
        override fun stopPulse() { felt += "stop pulse" }
    }

    private var soundOn = true
    private var vibrationOn = true
    private val sound = FakeSound()
    private var vibration = FakeVibration()
    private fun cues() = Cues({ soundOn }, { vibrationOn }, sound, vibration)

    @Test
    fun withBothOnTheRecordingCuesAndTheShutterAreHeardAndFelt() {
        val cues = cues()
        cues.give(Cue.RECORDING_START)
        cues.give(Cue.RECORDING_STOP)
        cues.give(Cue.SHUTTER, alongside = true)

        assertEquals(listOf("RECORDING_START", "RECORDING_STOP", "SHUTTER alongside"), sound.played)
        assertEquals(listOf("tap", "tap", "tap"), vibration.felt)
    }

    @Test
    fun soundEffectsOffLeavesOnlyTheVibration() {
        soundOn = false
        val cues = cues()
        cues.give(Cue.RECORDING_START)
        cues.give(Cue.SHUTTER)

        assertEquals(emptyList<String>(), sound.played)
        assertEquals(listOf("tap", "tap"), vibration.felt)
    }

    @Test
    fun vibrationOffLeavesOnlyTheSound() {
        vibrationOn = false
        val cues = cues()
        cues.give(Cue.RECORDING_STOP)
        cues.give(Cue.SHUTTER)

        assertEquals(listOf("RECORDING_STOP", "SHUTTER"), sound.played)
        assertEquals(emptyList<String>(), vibration.felt)
    }

    @Test
    fun withBothOffNoCueIsGiven() {
        soundOn = false
        vibrationOn = false
        val cues = cues()
        Cue.entries.forEach { cues.give(it) }

        assertEquals(emptyList<String>(), sound.played)
        assertEquals(emptyList<String>(), vibration.felt)
    }

    /** The waiting tick follows Sound effects only: it is never felt. */
    @Test
    fun theWaitingTickIsHeardWithSoundEffectsAndNeverFelt() {
        val cues = cues()
        cues.give(Cue.TICK)
        soundOn = false
        cues.give(Cue.TICK)

        // Over the camera click or the recording-stop cue still sounding,
        // never cutting it (audit, 2026-10-28).
        assertEquals(listOf("TICK alongside"), sound.played)
        assertEquals(emptyList<String>(), vibration.felt)
    }

    /** The switches are read at each cue, so a change in Settings applies at once. */
    @Test
    fun aSwitchChangedBetweenCuesAppliesToTheNextOne() {
        val cues = cues()
        cues.give(Cue.RECORDING_START)
        soundOn = false
        cues.give(Cue.RECORDING_STOP)

        assertEquals(listOf("RECORDING_START"), sound.played)
        assertEquals(listOf("tap", "tap"), vibration.felt)
    }

    @Test
    fun pitchGuidanceWithBothOnPlaysTheToneAndPulses() {
        cues().guide(0.75f)

        assertEquals(listOf("tone 0.75"), sound.played)
        assertEquals(listOf("pulse 0.75"), vibration.felt)
    }

    @Test
    fun pitchGuidanceWithVibrationOnlyPulsesAndSilencesTheTone() {
        soundOn = false
        cues().guide(0.5f)

        assertEquals(listOf("stop tone"), sound.played)
        assertEquals(listOf("pulse 0.5"), vibration.felt)
    }

    @Test
    fun pitchGuidanceOnAPhoneThatCannotVibratePlaysTheTone() {
        soundOn = false
        vibration = FakeVibration(present = false)
        cues().guide(0.5f)

        assertEquals(listOf("tone 0.5"), sound.played)
        assertEquals(listOf("stop pulse"), vibration.felt)
    }

    @Test
    fun pitchGuidanceWithBothOffStillPlaysTheTone() {
        soundOn = false
        vibrationOn = false
        cues().guide(0.25f)

        assertEquals(listOf("tone 0.25"), sound.played)
        assertEquals(listOf("stop pulse"), vibration.felt)
    }

    @Test
    fun pitchGuidanceWithSoundOnlyPlaysTheToneAndStopsThePulse() {
        vibrationOn = false
        cues().guide(1f)

        assertEquals(listOf("tone 1.0"), sound.played)
        assertEquals(listOf("stop pulse"), vibration.felt)
    }

    @Test
    fun stoppingTheGuidanceStopsBoth() {
        cues().stopGuidance()

        assertEquals(listOf("stop tone"), sound.played)
        assertEquals(listOf("stop pulse"), vibration.felt)
    }
}
