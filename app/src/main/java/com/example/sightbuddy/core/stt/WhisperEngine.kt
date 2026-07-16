package com.example.sightbuddy.core.stt

import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig

/**
 * Local Whisper (base.en, int8) speech-to-text plus silero voice-activity
 * detection, both via sherpa-onnx. Heavy to construct (~seconds) — create on a
 * background thread via [load] and reuse.
 *
 * Not thread-safe: callers serialise access (VoiceInputService records and
 * transcribes on a single dispatcher at a time).
 */
class WhisperEngine private constructor(
    private val recognizer: OfflineRecognizer,
    private val vad: Vad,
) {

    /** Feed one [VAD_WINDOW]-sample chunk of 16 kHz mono float audio. */
    fun acceptVadChunk(chunk: FloatArray) = vad.acceptWaveform(chunk)

    /** True once the VAD has heard speech since the last [resetVad]. */
    fun isSpeechDetected(): Boolean = vad.isSpeechDetected()

    fun resetVad() = vad.clear()

    /** Blocking transcription of the full utterance. */
    fun transcribe(samples: FloatArray): String {
        val stream = recognizer.createStream()
        return try {
            stream.acceptWaveform(samples, SAMPLE_RATE)
            recognizer.decode(stream)
            recognizer.getResult(stream).text.trim()
        } finally {
            stream.release()
        }
    }

    fun release() {
        recognizer.release()
        vad.release()
    }

    companion object {
        private const val TAG = "WhisperEngine"
        const val SAMPLE_RATE = 16000

        /** Samples per VAD call — fixed by the silero model. */
        const val VAD_WINDOW = 512

        /** Blocking load; call from a background dispatcher. Null on failure. */
        fun load(models: SttModelManager): WhisperEngine? = try {
            val recognizer = OfflineRecognizer(
                config = OfflineRecognizerConfig(
                    featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                    modelConfig = OfflineModelConfig(
                        whisper = OfflineWhisperModelConfig(
                            encoder = models.encoderFile().absolutePath,
                            decoder = models.decoderFile().absolutePath,
                            language = "en",
                            task = "transcribe",
                        ),
                        tokens = models.tokensFile().absolutePath,
                        modelType = "whisper",
                        numThreads = 4,
                        debug = false,
                    ),
                ),
            )
            val vad = Vad(
                config = VadModelConfig(
                    sileroVadModelConfig = SileroVadModelConfig(
                        model = models.vadFile().absolutePath,
                        threshold = 0.5f,
                        minSilenceDuration = 0.25f,
                        minSpeechDuration = 0.25f,
                        windowSize = VAD_WINDOW,
                    ),
                    sampleRate = SAMPLE_RATE,
                    numThreads = 1,
                ),
            )
            Log.i(TAG, "Whisper engine loaded")
            WhisperEngine(recognizer, vad)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load Whisper engine", e)
            null
        }
    }
}
