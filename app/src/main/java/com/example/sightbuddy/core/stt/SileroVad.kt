package com.example.sightbuddy.core.stt

import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import com.example.sightbuddy.core.ModelFileGate

/**
 * Silero voice-activity detection, loaded from the Whisper download's VAD file
 * (`silero_vad.onnx`).
 *
 * Whisper loads its own VAD inside [WhisperEngine]; on the Gemma path Whisper is
 * not loaded, so this loads the same model on its own to tell real speech from
 * silence before Gemma is asked to transcribe (issue #25). Heavy to construct —
 * load once and reuse.
 */
class SileroVad private constructor(private val vad: Vad) {

    /** Feed one [WINDOW]-sample chunk of 16 kHz mono float audio. */
    fun acceptChunk(chunk: FloatArray) = vad.acceptWaveform(chunk)

    /** True once the VAD has heard speech since the last [reset]. */
    fun isSpeechDetected(): Boolean = vad.isSpeechDetected()

    fun reset() = vad.clear()

    fun release() = vad.release()

    /**
     * Runs the VAD over a whole recording and reports whether it heard speech.
     * The last, short chunk is padded with silence, as the Whisper path does.
     */
    fun hasSpeech(pcm: ShortArray): Boolean {
        reset()
        val window = FloatArray(WINDOW)
        var i = 0
        while (i < pcm.size) {
            val n = minOf(WINDOW, pcm.size - i)
            for (j in 0 until n) window[j] = pcm[i + j] / 32768.0f
            for (j in n until WINDOW) window[j] = 0f
            acceptChunk(window.copyOf())
            i += n
        }
        return isSpeechDetected()
    }

    companion object {
        const val SAMPLE_RATE = 16000

        /** Samples per VAD call — fixed by the silero model. */
        const val WINDOW = 512

        /** Blocking load; call from a background dispatcher. Null on failure. */
        fun load(vad: ModelFileGate.ForLoad.Verified): SileroVad? = try {
            val vad = Vad(
                config = VadModelConfig(
                    sileroVadModelConfig = SileroVadModelConfig(
                        model = vad.file.absolutePath,
                        threshold = 0.5f,
                        minSilenceDuration = 0.25f,
                        minSpeechDuration = 0.25f,
                        windowSize = WINDOW,
                    ),
                    sampleRate = SAMPLE_RATE,
                    numThreads = 1,
                ),
            )
            SileroVad(vad)
        } catch (e: Throwable) {
            null
        }
    }
}
