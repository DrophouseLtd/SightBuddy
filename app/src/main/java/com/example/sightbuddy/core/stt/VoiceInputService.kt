package com.example.sightbuddy.core.stt

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.example.sightbuddy.core.VoiceCommandService
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import android.speech.SpeechRecognizer
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
 *  - runs until the user stops it, or [MAX_RECORDING_MS] is reached
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

    /**
     * Set by the app when the user has turned on OpenAI transcription, and null
     * otherwise. When present it outranks everything: it is the only option that
     * records for as long as the user speaks in any language.
     */
    @Volatile
    var cloudTranscriber: CloudTranscriber? = null

    private fun usingCloud(): Boolean = cloudTranscriber?.isConfigured() == true

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var focusRequest: AudioFocusRequest? = null

    /**
     * Ask the system to silence everything else for the duration of a recording.
     *
     * Music or a podcast in another app otherwise plays straight into the mic and
     * is transcribed alongside the user. Exclusive focus also stops the platform
     * recogniser's own earcons being ducked, which is why they were so quiet.
     */
    private fun takeAudioFocus() {
        val manager = audioManager ?: return
        if (focusRequest != null) return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setOnAudioFocusChangeListener { }
            .build()
        focusRequest = request
        runCatching { manager.requestAudioFocus(request) }
            .onFailure { Log.w(TAG, "Could not take audio focus", it) }
    }

    private fun releaseAudioFocus() {
        val manager = audioManager ?: return
        val request = focusRequest ?: return
        focusRequest = null
        runCatching { manager.abandonAudioFocusRequest(request) }
    }

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
                if (err == 0) return@collect
                _lastError.value = err
                // Only a genuine "nothing heard" should ask the user to repeat. A busy
                // or client error means the recogniser was restarted before it had
                // finished, and prompting there replayed the prompt the instant the
                // next recording began.
                if (err == SpeechRecognizer.ERROR_NO_MATCH ||
                    err == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                ) {
                    _events.tryEmit(SttEvent.Empty)
                }
            }
        }
        scope.launch {
            legacy.isListening.collect { listening ->
                if (!usingWhisper()) _isListening.value = listening
                if (!listening) releaseAudioFocus()
            }
        }
        loadEngineIfReady()
    }

    private fun usingWhisper(): Boolean = engine != null

    /**
     * True when the platform recogniser is driving the mic, which means the system
     * plays its own start, stop and error earcons. Callers use this to avoid
     * stacking the app's cues on top of them.
     */
    fun usesSystemEarcons(): Boolean = !usingWhisper() && !usingCloud()

    /** Load the Whisper engine when model files are available. Single-flight. */
    fun loadEngineIfReady() {
        // The bundled model is Whisper base.en — English only. In any other app
        // language stay on the system recogniser, which does support it.
        if (Locale.getDefault().language != "en") return
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
        takeAudioFocus()
        // Opens synchronously, on purpose. This used to be deferred so the app's
        // "listening" earcon could finish, but the app no longer plays one, and a
        // deferred open means a stop arriving first cancels it rather than ending a
        // live session. That is what stopped follow-up questions recording at all.
        val cloud = cloudTranscriber?.takeIf { it.isConfigured() }
        val eng = engine
        if (cloud == null && eng == null) {
            legacy.startListening()
            return
        }
        if (recordJob?.isActive == true) return
        stopRequested = false
        _isListening.value = true
        recordJob = scope.launch {
            if (cloud != null) recordAndSendToCloud(cloud) else recordAndTranscribe(eng!!)
        }
    }

    /** Idempotent; safe to call when not recording (e.g. release after auto-stop). */
    fun stopListening() {
        if (usingWhisper() || usingCloud()) {
            releaseAudioFocus()
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

                // Silence no longer ends a recording. People pause mid-question to
                // think, and closing the mic on them was cutting questions in half.
                // The user stops it, or the hard cap does.
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

        if (maxedOut) {
            _events.tryEmit(SttEvent.AutoStopped(noSpeech = false))
        }
        if (!speechDetected) {
            _lastError.value = ERROR_NO_MATCH
            _events.tryEmit(SttEvent.Empty)
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

    /**
     * Record until the user stops, or the cap, then send the audio for
     * transcription.
     *
     * No voice activity detection and no auto-stop: that is the entire point of
     * this path. The silero detector also lives in the Whisper download, which a
     * user on this path may never have fetched.
     */
    private suspend fun recordAndSendToCloud(cloud: CloudTranscriber) {
        val sampleRate = WhisperEngine.SAMPLE_RATE
        val chunk = 1600 // 100 ms
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
                maxOf(minBuf, chunk * 8),
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

        val audio = ArrayList<ShortArray>(maxSamples / chunk + 1)
        var total = 0
        try {
            recorder.startRecording()
            val buffer = ShortArray(chunk)
            while (!stopRequested && total < maxSamples) {
                val n = recorder.read(buffer, 0, chunk)
                if (n <= 0) break
                audio.add(buffer.copyOf(n))
                total += n
            }
        } catch (e: Exception) {
            Log.e(TAG, "Recording failed", e)
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
            _isListening.value = false
            releaseAudioFocus()
        }

        // Less than a quarter second is a stray tap, not a question.
        if (total < sampleRate / 4) {
            _lastError.value = ERROR_NO_MATCH
            _events.tryEmit(SttEvent.Empty)
            return
        }

        val pcm = ShortArray(total)
        var offset = 0
        for (part in audio) {
            part.copyInto(pcm, offset)
            offset += part.size
        }

        _isTranscribing.value = true
        val text = try {
            withContext(Dispatchers.IO) {
                cloud.transcribe(pcm, sampleRate, Locale.getDefault().language)
            }
        } finally {
            _isTranscribing.value = false
        }

        if (text.isNullOrBlank()) {
            _lastError.value = ERROR_NO_MATCH
            _events.tryEmit(SttEvent.Empty)
        } else {
            Log.i(TAG, "Cloud transcription: $text")
            _recognizedText.value = text
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
        /**
         * Hard cap on one recording. Nothing else ends it: the user stops it, or it
         * runs to here. Silence no longer closes the mic, because people pause to
         * think mid-question and were being cut off.
         */
        const val MAX_RECORDING_MS = 60_000L

        /** Mirrors SpeechRecognizer.ERROR_NO_MATCH for caller compatibility. */
        const val ERROR_NO_MATCH = 7
    }
}
