package com.example.sightbuddy.core.stt

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.example.sightbuddy.core.VoiceCommandService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Unified speech-to-text front door.
 *
 * Uses the local Whisper engine when the model files are present; otherwise
 * falls back to the Android system recognizer ([VoiceCommandService]) so the
 * app works out of the box while the model downloads.
 *
 * Whisper path recording rules (both mic modes):
 *  - auto-stop if no speech is detected within [NO_SPEECH_TIMEOUT_MS]
 *  - hard cap of [MAX_RECORDING_MS] per utterance
 *
 * Exposes the same flow surface as the old service ([recognizedText],
 * [isListening], [lastError]) plus [events] for result/empty notifications.
 */
class VoiceInputService(private val context: Context, val models: SttModelManager) {

    sealed class SttEvent {
        /** Recording ended by the service, not the user. */
        data class AutoStopped(val noSpeech: Boolean) : SttEvent()

        /** Recording finished but produced no usable text. */
        data object Empty : SttEvent()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val legacy = VoiceCommandService(context)

    private val _recognizedText = MutableStateFlow("")
    val recognizedText = _recognizedText.asStateFlow()

    private val _lastError = MutableStateFlow(0)
    val lastError = _lastError.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening = _isListening.asStateFlow()

    /** True while Whisper is decoding a finished recording. */
    private val _isTranscribing = MutableStateFlow(false)
    val isTranscribing = _isTranscribing.asStateFlow()

    private val _whisperReady = MutableStateFlow(false)
    val whisperReady = _whisperReady.asStateFlow()

    private val _events = MutableSharedFlow<SttEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    private var engine: WhisperEngine? = null

    private val engineLoadStarted = java.util.concurrent.atomic.AtomicBoolean(false)

    @Volatile
    private var recordJob: Job? = null

    @Volatile
    private var stopRequested = false

    init {
        // Mirror the fallback recognizer's flows so callers observe one surface.
        scope.launch {
            legacy.recognizedText.collect { if (it.isNotEmpty()) _recognizedText.value = it }
        }
        scope.launch {
            legacy.lastError.collect { err ->
                if (err != 0) {
                    _lastError.value = err
                    _events.tryEmit(SttEvent.Empty)
                }
            }
        }
        scope.launch {
            legacy.isListening.collect { if (!usingWhisper()) _isListening.value = it }
        }
        loadEngineIfReady()
    }

    private fun usingWhisper(): Boolean = engine != null

    /** Load the Whisper engine when model files are available. Single-flight. */
    fun loadEngineIfReady() {
        if (!models.isReady() || !engineLoadStarted.compareAndSet(false, true)) return
        scope.launch {
            val loaded = WhisperEngine.load(models)
            if (loaded != null) {
                engine = loaded
                _whisperReady.value = true
            } else {
                engineLoadStarted.set(false) // allow a later retry
            }
        }
    }

    fun startListening() {
        _recognizedText.value = ""
        _lastError.value = 0
        val eng = engine
        if (eng == null) {
            legacy.startListening()
            return
        }
        if (recordJob?.isActive == true) return
        stopRequested = false
        _isListening.value = true
        recordJob = scope.launch { recordAndTranscribe(eng) }
    }

    /** Idempotent; safe to call when not recording (e.g. release after auto-stop). */
    fun stopListening() {
        if (usingWhisper()) {
            stopRequested = true
        } else {
            legacy.stopListening()
        }
    }

    @SuppressLint("MissingPermission") // RECORD_AUDIO is granted during onboarding.
    private suspend fun recordAndTranscribe(eng: WhisperEngine) {
        val sampleRate = WhisperEngine.SAMPLE_RATE
        val window = WhisperEngine.VAD_WINDOW
        val maxSamples = sampleRate * (MAX_RECORDING_MS / 1000L).toInt()
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, window * 8),
            )
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecord init failed", e)
            finishWithError()
            return
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            finishWithError()
            return
        }

        val audio = ArrayList<FloatArray>(maxSamples / window + 1)
        var totalSamples = 0
        var speechDetected = false
        var autoStopNoSpeech = false
        var maxedOut = false
        eng.resetVad()

        try {
            recorder.startRecording()
            val shorts = ShortArray(window)
            val floats = FloatArray(window)
            while (!stopRequested) {
                val n = recorder.read(shorts, 0, window)
                if (n <= 0) break
                for (i in 0 until n) floats[i] = shorts[i] / 32768.0f
                if (n < window) for (i in n until window) floats[i] = 0f
                eng.acceptVadChunk(floats.copyOf())
                audio.add(floats.copyOf(n))
                totalSamples += n

                if (!speechDetected && eng.isSpeechDetected()) speechDetected = true

                val elapsedMs = totalSamples * 1000L / sampleRate
                if (!speechDetected && elapsedMs >= NO_SPEECH_TIMEOUT_MS) {
                    autoStopNoSpeech = true
                    break
                }
                if (totalSamples >= maxSamples) {
                    maxedOut = true
                    break
                }
            }
        } finally {
            try {
                recorder.stop()
            } catch (_: Exception) {
            }
            recorder.release()
            _isListening.value = false
        }

        if (autoStopNoSpeech || maxedOut) {
            _events.tryEmit(SttEvent.AutoStopped(noSpeech = autoStopNoSpeech))
        }
        if (autoStopNoSpeech || !speechDetected) {
            // AutoStopped(noSpeech) already tells the UI; avoid a duplicate Empty.
            _lastError.value = ERROR_NO_MATCH
            if (!autoStopNoSpeech) _events.tryEmit(SttEvent.Empty)
            return
        }

        _isTranscribing.value = true
        try {
            val samples = FloatArray(totalSamples)
            var pos = 0
            for (chunk in audio) {
                chunk.copyInto(samples, pos)
                pos += chunk.size
            }
            val text = withContext(Dispatchers.Default) { eng.transcribe(samples) }
            if (text.isBlank()) {
                finishWithError()
            } else {
                Log.i(TAG, "Whisper transcription: $text")
                _recognizedText.value = text
            }
        } catch (e: Exception) {
            Log.e(TAG, "Transcription failed", e)
            finishWithError()
        } finally {
            _isTranscribing.value = false
        }
    }

    private fun finishWithError() {
        _isListening.value = false
        _lastError.value = ERROR_NO_MATCH
        _events.tryEmit(SttEvent.Empty)
    }

    fun shutdown() {
        stopRequested = true
        scope.cancel()
        legacy.shutdown()
        engine?.release()
        engine = null
    }

    companion object {
        private const val TAG = "VoiceInputService"
        const val NO_SPEECH_TIMEOUT_MS = 10_000L
        const val MAX_RECORDING_MS = 60_000L

        /** Mirrors SpeechRecognizer.ERROR_NO_MATCH for caller compatibility. */
        const val ERROR_NO_MATCH = 7
    }
}
