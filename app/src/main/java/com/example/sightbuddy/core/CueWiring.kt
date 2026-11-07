package com.example.sightbuddy.core

import com.example.sightbuddy.core.Cues.Cue
import com.example.sightbuddy.core.SoundFXService.SFX

/**
 * The one place the app's [Cues] meet the sound pool, the pitch tone and the
 * vibrator. Nothing else plays a sample or starts a vibration (CueRoutingTest
 * checks the sources for it); stopping them is allowed anywhere.
 *
 * [systemEarcons]: the Android recogniser beeps itself, so the recording cues
 * are the short unpitched perks that sit beside its beep.
 */
fun deviceCues(
    settings: SettingsManager,
    soundFX: SoundFXService,
    haptics: HapticManager,
    pitchTone: PitchToneService,
    systemEarcons: () -> Boolean,
): Cues = Cues(
    soundOn = { settings.soundEffects.value },
    vibrationOn = { settings.vibration.value },
    sound = object : Cues.Sound {
        override fun play(cue: Cue, alongside: Boolean) {
            val sfx = when (cue) {
                Cue.RECORDING_START -> if (systemEarcons()) SFX.RECORDING_PERK else SFX.LISTENING
                Cue.RECORDING_STOP -> if (systemEarcons()) SFX.RECORDING_PERK_STOP else SFX.STOP_LISTENING
                Cue.SHUTTER -> SFX.CAMERA_CLICK
                Cue.TICK -> SFX.WORKING
            }
            if (alongside) soundFX.playAlongside(sfx) else soundFX.play(sfx)
        }

        override fun tone(proximity: Float) = pitchTone.start(pitchTone.stepFor(proximity))
        override fun stopTone() = pitchTone.stop()
    },
    vibration = object : Cues.Vibration {
        override val present get() = haptics.hasVibrator
        override fun tap() = haptics.tap()
        override fun pulse(proximity: Float) = haptics.proximityPulse(proximity)
        override fun stopPulse() = haptics.stopPulse()
    },
)
