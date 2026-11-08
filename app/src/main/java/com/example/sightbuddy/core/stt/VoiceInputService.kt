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
import com.example.sightbuddy.core.llm.LocalGemma
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
import com.example.sightbuddy.core.PrivateLog
import android.media.AudioAttributes
import java.util.concurrent.atomic.AtomicBoolean

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
 * Exposes the same flow surface as the old service ([transcripts],
 * [isListening], [lastError]) plus [events] for result/empty notifications.
 */
class VoiceInputService(
    private val context: Context,
    val models: SttModelManager,
    /**
     * On-device Gemma. When its model is installed it replaces Whisper entirely
     * in English: Whisper is not loaded, and a recording made while Gemma is still
     * loading waits for it.
     */
    private val localGemma: LocalGemma? = null,
    /** The user's speech recognition choice in Settings. */
    private val sttChoice: () -> SttChoice = { SttChoice.WHISPER },
) {

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

    /** Which engine runs right now, from the choice and what is installed and loaded. */
    private fun effectiveChoice(): SttChoice = SttChoice.effective(
        chosen = sttChoice(),
        whisperReady = engine != null,
        gemmaInstalled = localGemma?.installed == true,
        english = Locale.getDefault().language == "en",
    )

    private fun usingGemma(): Boolean = effectiveChoice() == SttChoice.GEMMA

    /** True when the app, not the platform recogniser, holds the recording. */
    private fun appOwnsRecording(): Boolean = usingWhisper() || usingCloud() || usingGemma()

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
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
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

    private val _transcripts = Transcripts()

    /** Each finished transcript, once; see [Transcripts]. */
    val transcripts = _transcripts.flow

    /**
     * Whether the recording in progress may deliver its transcript (issue #25).
     * A recording ended by the user's own stop is transcribed; one ended by
     * anything else (a feature switch, Settings, the background, a pop-up) is
     * discarded.
     */
    private val recordingGate = RecordingGate()

    /** Silero VAD for the Gemma path, loaded lazily from the Whisper download. */
    @Volatile
    private var gemmaVad: SileroVad? = null

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

    private val engineLoadStarted = AtomicBoolean(false)

    @Volatile
    private var recordJob: Job? = null

    @Volatile
    private var stopRequested = false

    init {
        // Mirror the fallback recognizer's flows so callers observe one surface.
        scope.launch {
            legacy.transcripts.collect {
                // A recording cancelled by anything but the user's stop is dropped.
                if (!recordingGate.discardsTranscript()) _transcripts.deliver(it)
            }
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
                if (!appOwnsRecording()) _isListening.value = listening
                if (!listening) releaseAudioFocus()
            }
        }
        loadEngineIfReady()
    }

    private fun usingWhisper(): Boolean = effectiveChoice() == SttChoice.WHISPER

    /**
     * True when the platform recogniser is driving the mic, which means the system
     * plays its own start, stop and error earcons. Callers use this to avoid
     * stacking the app's cues on top of them.
     */
    fun usesSystemEarcons(): Boolean = !appOwnsRecording()

    /** Load the Whisper engine when model files are available. Single-flight. */
    fun loadEngineIfReady() {
        // The bundled model is Whisper base.en — English only. In any other app
        // language stay on the system recogniser, which does support it.
        if (Locale.getDefault().language != "en") return
        if (!models.isReady() || !engineLoadStarted.compareAndSet(false, true)) return
        scope.launch {
            // Only files that have matched their SHA-256 are handed out: files from
            // an earlier version are hashed here once. One that fails is deleted and
            // the download is offered again; until then the system recogniser.
            val files = withContext(Dispatchers.IO) { models.verifiedFiles() }
            val loaded = files?.let { WhisperEngine.load(it) }
            if (loaded != null) {
                engine = loaded
                _whisperReady.value = true
            } else {
                engineLoadStarted.set(false) // allow a later retry
            }
        }
    }

    /**
     * Drops the loaded Whisper engine so its model files can be deleted; the
     * system recogniser takes over. Refused while a recording is using it.
     */
    fun unloadEngine(): Boolean {
        if (recordJob?.isActive == true) return false
        engine?.release()
        engine = null
        _whisperReady.value = false
        engineLoadStarted.set(false)
        return true
    }

    fun startListening() {
        _lastError.value = 0
        recordingGate.reset()
        takeAudioFocus()
        // Opens synchronously, on purpose. This used to be deferred so the app's
        // "listening" earcon could finish; the start cue now plays as the mic opens
        // (it is short, and a tap when Vibration is on), without waiting, and a
        // deferred open means a stop arriving first cancels it rather than ending a
        // live session. That is what stopped follow-up questions recording at all.
        val cloud = cloudTranscriber?.takeIf { it.isConfigured() }
        val gemma = localGemma?.takeIf { usingGemma() }
        val eng = engine?.takeIf { usingWhisper() }
        if (cloud == null && gemma == null && eng == null) {
            legacy.startListening()
            return
        }
        if (recordJob?.isActive == true) return
        stopRequested = false
        _isListening.value = true
        recordJob = scope.launch {
            when {
                cloud != null -> recordAndSend("Cloud", gemmaPath = false) { pcm, rate ->
                    withContext(Dispatchers.IO) {
                        cloud.transcribe(pcm, rate, Locale.getDefault().language)
                    }
                }
                gemma != null -> recordAndSend("Gemma", gemmaPath = true) { pcm, rate ->
                    gemma.transcribe(pcm, rate)
                }
                else -> recordAndTranscribe(eng!!)
            }
        }
    }

    /** Idempotent; safe to call when not recording (e.g. release after auto-stop). */
    fun stopListening() {
        if (appOwnsRecording()) {
            releaseAudioFocus()
            stopRequested = true
        } else {
            legacy.stopListening()
        }
    }

    /**
     * Stop a recording that was not ended by the user's own stop (a feature
     * switch, Settings, the background, a pop-up). If it was still open, its
     * transcript is discarded: nothing is shown in the chat, nothing is asked
     * (issue #25). Leaving also calls [discardPendingTranscript].
     */
    fun cancelListening() {
        recordingGate.cancel(open = _isListening.value)
        if (appOwnsRecording()) {
            releaseAudioFocus()
            stopRequested = true
        } else {
            legacy.stopListening()
        }
    }

    /**
     * The user left the feature or the app: a transcript still on its way,
     * from a recording open or already stopped, is dropped (issue #25).
     */
    fun discardPendingTranscript() {
        recordingGate.discardPending()
    }

    /** The Silero VAD for the Gemma path, loaded once from the Whisper download. */
    private fun gemmaVadIfAvailable(): SileroVad? {
        gemmaVad?.let { return it }
        // Checked against its SHA-256 first (once; it is small).
        val file = models.verifiedVad() ?: return null
        val loaded = SileroVad.load(file)
        gemmaVad = loaded
        return loaded
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

        // A recording cancelled by anything but the user's stop is dropped.
        if (recordingGate.discardsTranscript()) return

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
                PrivateLog.i(TAG) { "Whisper transcription: $text" }
                _transcripts.deliver(text)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Transcription failed", e)
            finishWithError()
        } finally {
            _isTranscribing.value = false
        }
    }

    /**
     * Record until the user stops, or the cap, then hand the audio to
     * [transcribe] (OpenAI or on-device Gemma).
     *
     * No auto-stop: that is the entire point of this path. On the Gemma path
     * ([gemmaPath]) the Silero VAD from the Whisper download is run over the
     * recording first when it is available, so silence is "nothing heard"
     * rather than a made-up transcript (issue #25).
     */
    @SuppressLint("MissingPermission") // As above; a refusal throws into the catch.
    private suspend fun recordAndSend(
        label: String,
        gemmaPath: Boolean,
        transcribe: suspend (pcm: ShortArray, sampleRate: Int) -> String?,
    ) {
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

        // A recording cancelled by anything but the user's stop is dropped.
        if (recordingGate.discardsTranscript()) return

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

        // Gemma has no speech check of its own: run the Silero VAD (from the
        // Whisper download) first, when it is available, so silence is "nothing
        // heard" rather than a made-up transcript (issue #25).
        if (gemmaPath) {
            val vad = gemmaVadIfAvailable()
            if (vad != null && !vad.hasSpeech(pcm)) {
                _lastError.value = ERROR_NO_MATCH
                _events.tryEmit(SttEvent.Empty)
                return
            }
        }

        _isTranscribing.value = true
        val text = try {
            transcribe(pcm, sampleRate)
        } finally {
            _isTranscribing.value = false
        }

        if (text.isNullOrBlank()) {
            _lastError.value = ERROR_NO_MATCH
            _events.tryEmit(SttEvent.Empty)
        } else if (gemmaPath && GemmaTranscriptGate.isNotSpeech(text)) {
            // Gemma echoed its instruction or refused: no speech was heard.
            _lastError.value = ERROR_NO_MATCH
            _events.tryEmit(SttEvent.Empty)
        } else {
            PrivateLog.i(TAG) { "$label transcription: $text" }
            _transcripts.deliver(text)
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
