package com.example.sightbuddy.core

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.media.AudioAttributes
import android.os.VibrationAttributes

class HapticManager(private val context: Context) {

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    fun vibrateWarning() {
        if (!vibrator.hasVibrator()) return
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(100)
        }
    }

    fun vibrateAlert() {
        if (!vibrator.hasVibrator()) return
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 150, 100, 150)
            val amplitudes = intArrayOf(0, 255, 0, 255)
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 150, 100, 150), -1)
        }
    }

    /**
     * Temporary stability hotwire:
     * force-disable proximity vibration so Find Object always uses audio feedback.
     */
    fun vibrateProximity(proximity: Float): Boolean {
        return false
    }

    /** One short pulse: the haptic stand-in for the recording start and stop cues. */
    fun tap() {
        if (!vibrator.hasVibrator()) return
        vibrateAsFeedback(VibrationEffect.createOneShot(TAP_MS, TAP_AMPLITUDE))
    }

    /**
     * Vibrates as accessibility feedback. Without a stated purpose Android files
     * an app's vibration as touch feedback, and phones with "touch feedback"
     * switched off drop every one of them silently ("ignored_for_settings").
     */
    private fun vibrateAsFeedback(effect: VibrationEffect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(
                effect,
                VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ACCESSIBILITY),
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(
                effect,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .build(),
            )
        }
    }

    // The rung the running pulse was built for, so it is only rebuilt on a change.
    private var pulseRung = -1

    /**
     * A repeating pulse that quickens as [proximity] rises, 0 far off and 1 dead
     * centre: the haptic stand-in for Find objects' rising pitch note. Rebuilt only
     * when it moves to another of [PULSE_RUNGS] speeds, so it never stutters.
     */
    fun proximityPulse(proximity: Float) {
        if (!vibrator.hasVibrator()) return
        val rung = (proximity.coerceIn(0f, 1f) * (PULSE_RUNGS - 1)).toInt()
        if (rung == pulseRung) return
        pulseRung = rung
        val t = rung / (PULSE_RUNGS - 1).toFloat()
        val gap = (PULSE_SLOWEST_GAP_MS - (PULSE_SLOWEST_GAP_MS - PULSE_FASTEST_GAP_MS) * t).toLong()
        val buzz = (PULSE_BUZZ_MIN_MS + (PULSE_BUZZ_MAX_MS - PULSE_BUZZ_MIN_MS) * t).toLong()
        // Repeats from index 1: buzz, gap, buzz, gap…
        vibrateAsFeedback(
            VibrationEffect.createWaveform(longArrayOf(0, buzz, gap), intArrayOf(0, 255, 0), 1)
        )
    }

    fun stopPulse() {
        if (pulseRung == -1) return
        pulseRung = -1
        vibrator.cancel()
    }

    private companion object {
        const val TAP_MS = 45L
        const val TAP_AMPLITUDE = 220
        const val PULSE_RUNGS = 8
        const val PULSE_SLOWEST_GAP_MS = 700f
        const val PULSE_FASTEST_GAP_MS = 70f
        const val PULSE_BUZZ_MIN_MS = 30f
        const val PULSE_BUZZ_MAX_MS = 60f
    }
}
