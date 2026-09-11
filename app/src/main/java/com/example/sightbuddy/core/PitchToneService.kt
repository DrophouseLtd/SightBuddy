package com.example.sightbuddy.core

import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlin.math.PI
import kotlin.math.sin

/**
 * A steady tone whose pitch says how near the centre of the frame an object is.
 *
 * Used by Find objects as an alternative to the one-shot direction cues. Rather
 * than naming a direction every time it changes, this holds a note and raises it
 * as the object approaches the middle, so the user can sweep the phone and hear
 * the target rise under them.
 *
 * The rungs climb from G to the C an octave and a fourth above, so reaching the
 * centre lands on the tonic and sounds finished rather than merely high. Notes
 * are skipped on the way: an arpeggio is easier to place by ear than a full
 * scale, and a smooth glide would give the ear nothing to measure at all.
 *
 * It owns its own [AudioTrack] and touches nothing else, so it cannot interfere
 * with speech or with the cue pool.
 */
class PitchToneService {

    private var track: AudioTrack? = null
    private var playing = false
    private var currentStep = -1

    /** Start the tone at [step], or move an already-sounding tone to it. */
    fun start(step: Int) {
        val clamped = step.coerceIn(0, SCALE.lastIndex)
        val existing = track ?: build() ?: return
        if (!playing) {
            currentStep = -1
            existing.play()
            playing = true
        }
        setStep(clamped)
    }

    /** Move the sounding tone to a new rung; a no-op if it is already there. */
    fun setStep(step: Int) {
        val clamped = step.coerceIn(0, SCALE.lastIndex)
        if (clamped == currentStep) return
        currentStep = clamped
        val t = track ?: return
        // The ceiling is the device's, not ours, so ask rather than assume.
        val ceiling = runCatching {
            AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC) * 2
        }.getOrDefault(SAMPLE_RATE * 2)
        val rate = (SAMPLE_RATE * SCALE[clamped]).toInt().coerceAtMost(ceiling)
        runCatching { t.playbackRate = rate }
            .onFailure { Log.w(TAG, "Could not set playback rate", it) }
    }

    fun stop() {
        val t = track ?: return
        if (playing) {
            runCatching { t.pause() }
            runCatching { t.reloadStaticData() }
            playing = false
        }
        currentStep = -1
    }

    fun release() {
        stop()
        runCatching { track?.release() }
        track = null
    }

    /**
     * Maps how near the centre an object is, 0 furthest and 1 dead centre, to a
     * rung of the scale.
     */
    fun stepFor(proximity: Float): Int =
        (proximity.coerceIn(0f, 1f) * SCALE.lastIndex).toInt().coerceIn(0, SCALE.lastIndex)

    private fun build(): AudioTrack? = runCatching {
        val pcm = sineLoop()
        val attrs = AudioAttributes.Builder()
            // Same category as the other cues: never ducked as media would be.
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val t = AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(format)
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        t.write(pcm, 0, pcm.size)
        t.setLoopPoints(0, pcm.size, -1)
        track = t
        t
    }.onFailure { Log.e(TAG, "Could not build the tone track", it) }.getOrNull()

    /**
     * One buffer holding a whole number of cycles, so looping it is seamless. The
     * base frequency divides the sample rate exactly, which is why it is 196.875 Hz
     * rather than a true G3 at 196: the difference is inaudible and it avoids a
     * click on every loop.
     */
    private fun sineLoop(): ShortArray {
        val framesPerCycle = SAMPLE_RATE / BASE_HZ
        val frames = framesPerCycle * CYCLES
        return ShortArray(frames) { i ->
            val angle = 2.0 * PI * (i % framesPerCycle) / framesPerCycle
            // Well below full scale: this sound is held for long stretches.
            (sin(angle) * 0.35 * Short.MAX_VALUE).toInt().toShort()
        }
    }

    companion object {
        private const val TAG = "PitchToneService"

        /**
         * Deliberately low. Pitch is shifted by playback rate, and the rate may not
         * exceed twice the device output rate, so a track built at 44.1 kHz can only
         * rise by an octave. Building it at 22.05 kHz leaves room for the octave and
         * a fourth this scale needs. The tone is a sine well under 1 kHz, so the
         * lower rate costs nothing audible.
         */
        private const val SAMPLE_RATE = 22050

        /** 22050 / 112 = 196.875 Hz, a hair above G3, and a whole number of samples
         *  per cycle so the loop joins without a click. */
        private const val BASE_HZ = 112
        private const val CYCLES = 100

        /**
         * G C E G A C, as multipliers against the base note. Starting on the
         * dominant and ending on the tonic is what makes the arrival at the centre
         * feel like a resolution rather than just the top of a slide.
         */
        private val SCALE = floatArrayOf(
            1.0f,        // G
            4f / 3f,     // C
            5f / 3f,     // E
            2.0f,        // G
            9f / 4f,     // A
            8f / 3f,     // C
        )
    }
}
