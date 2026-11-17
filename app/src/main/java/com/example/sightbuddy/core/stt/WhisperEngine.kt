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
 * Local Whisper speech-to-text plus silero voice-activity detection, both via
 * sherpa-onnx. Heavy to construct (~seconds) — create on a background thread
 * via [load] and reuse.
 *
 * Two models, each with its own language policy ([files.set]):
 * - the multilingual base model, told the app's language ([files.language]) —
 *   Polish, Finnish, English … — with an empty language meaning "detect it",
 *   which is what a language Whisper knows but this app does not list needs;
 * - the legacy base.en model, English only, kept for phones that still have it.
 *
 * Not thread-safe: callers serialise access (VoiceInputService records and
 * transcribes on a single dispatcher at a time).
 */
class WhisperEngine private constructor(
    private val plan: WhisperPlan,
    private val recognizer: OfflineRecognizer,
    private val vad: Vad,
) {

    /** The model and language this engine was built for. */
    fun builtFor(): WhisperPlan = plan

    /** Feed one [VAD_WINDOW]-sample chunk of 16 kHz mono float audio. */
    fun acceptVadChunk(chunk: FloatArray) = vad.acceptWaveform(chunk)

    /** True once the VAD has heard speech since the last [resetVad]. */
    fun isSpeechDetected(): Boolean = vad.isSpeechDetected()

    fun resetVad() = vad.clear()

    /** Blocking transcription of the full utterance. */
    fun transcribe(samples: FloatArray): String {
        val stream = recognizer.createStream()
        return try {
            stream.acceptWaveform(padForDecoderCap(samples), SAMPLE_RATE)
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

        /**
         * The greedy decoder in sherpa-onnx (v1.13.4,
         * offline-whisper-greedy-search-decoder.cc:196-198) caps output at
         * audio-frames / 100 * 6 tokens, counted on the real audio before
         * tail padding — a hard cap that truncates mid-word with no error and
         * no override in this version. Recorded speech stops short of it once
         * roughly 2 seconds of zeros are appended (measured 20/20 complete
         * transcriptions .local/qa/multilingual/decoding-diagnosis), so this
         * pads every utterance by that much, up to the 30 s the feature
         * window accepts. Not a quality fix: it removes the cut, it does not
         * choose better words.
         */
        private const val DECODER_CAP_PADDING_S = 2.0
        private const val MAX_INPUT_S = 29.5

        /**
         * [samples] with [DECODER_CAP_PADDING_S] of silence appended, kept
         * under the feature window's real limit so nothing past ~30 s of
         * audio is silently dropped by the recognizer.
         */
        internal fun padForDecoderCap(samples: FloatArray): FloatArray {
            // Nothing recorded: no silence to add, and feeding the recognizer
            // two seconds of pure zeros invites it to hallucinate.
            if (samples.isEmpty()) return samples
            val maxSamples = (MAX_INPUT_S * SAMPLE_RATE).toInt()
            val speech = if (samples.size > maxSamples) samples.copyOf(maxSamples) else samples
            val room = (maxSamples - speech.size).coerceAtLeast(0)
            val pad = minOf(
                (DECODER_CAP_PADDING_S * SAMPLE_RATE).toInt(),
                room,
            )
            if (pad <= 0) return speech
            return speech + FloatArray(pad)
        }

        /** Blocking load; call from a background dispatcher. Null on failure. */
        fun load(files: SttModelManager.WhisperFiles): WhisperEngine? = try {
            val recognizer = OfflineRecognizer(
                config = OfflineRecognizerConfig(
                    featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                    modelConfig = OfflineModelConfig(
                        whisper = OfflineWhisperModelConfig(
                            encoder = files.encoder.file.absolutePath,
                            decoder = files.decoder.file.absolutePath,
                            // The multilingual model is told the app's language;
                            // "" lets Whisper detect it, which is the honest setting
                            // for a language this app does not list. The legacy
                            // base.en model hears English, whatever it is asked.
                            language = if (files.set == SttModelSet.MULTILINGUAL) files.language else "en",
                            task = "transcribe",
                        ),
                        tokens = files.tokens.file.absolutePath,
                        modelType = "whisper",
                        numThreads = 4,
                        debug = false,
                    ),
                ),
            )
            val vad = Vad(
                config = VadModelConfig(
                    sileroVadModelConfig = SileroVadModelConfig(
                        model = files.vad.file.absolutePath,
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
            WhisperEngine(
                WhisperPlan(files.set, if (files.set == SttModelSet.MULTILINGUAL) files.language else "en"),
                recognizer,
                vad,
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load Whisper engine", e)
            null
        }
    }
}
