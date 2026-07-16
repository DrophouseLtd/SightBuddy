package com.example.sightbuddy.core

import android.media.AudioManager
import android.media.ToneGenerator

/**
 * Lightweight beep tones for UI feedback (mic activate/deactivate).
 * Uses [ToneGenerator] so no audio files are needed.
 */
class BeepService {

    private val toneGenerator = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, 80)
    } catch (_: Exception) {
        null
    }

    fun beepActivate() {
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
    }

    fun beepDeactivate() {
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 100)
    }

    fun shutdown() {
        toneGenerator?.release()
    }
}
