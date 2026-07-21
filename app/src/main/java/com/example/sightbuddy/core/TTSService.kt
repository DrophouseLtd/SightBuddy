package com.example.sightbuddy.core

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class TTSService(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized = _isInitialized.asStateFlow()

    /** When true, [speak] and cooldown helpers no-op (used during STT so mic audio is not masked). */
    private val _suppressAppSpeech = MutableStateFlow(false)
    val suppressAppSpeech = _suppressAppSpeech.asStateFlow()

    private var utteranceDoneListener: ((String?) -> Unit)? = null
    private var utteranceRangeListener: ((String?, Int, Int) -> Unit)? = null

    /** One-shot per-utterance completion callbacks (e.g. resume reading after a rate announcement). */
    private val oneShotDone = java.util.concurrent.ConcurrentHashMap<String, () -> Unit>()

    fun setOnUtteranceDone(listener: ((String?) -> Unit)?) {
        utteranceDoneListener = listener
    }

    /** [start]/[end] are character offsets within the spoken utterance text. */
    fun setOnUtteranceRange(listener: ((String?, Int, Int) -> Unit)?) {
        utteranceRangeListener = listener
    }

    fun setSuppressAppSpeech(suppress: Boolean) {
        _suppressAppSpeech.value = suppress
    }

    private val announcementCooldowns = mutableMapOf<String, Long>()
    private val COOLDOWN_MS = 3000L

    /** Applied on init and whenever the user changes the Settings speech-rate. */
    @Volatile
    private var speechRate: Float = 1.0f

    init {
        tts = TextToSpeech(context, this)
    }

    /**
     * 1.0 = normal. Safe at any time: stored and re-applied after async init.
     * Playback cursor logic is character-indexed (TTS range callbacks), so
     * changing the rate never desynchronises Text chat playback.
     */
    fun setSpeechRate(rate: Float) {
        speechRate = rate
        if (_isInitialized.value) {
            tts?.setSpeechRate(rate)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTSService", "The Language specified is not supported!")
            } else {
                _isInitialized.value = true
                tts?.setSpeechRate(speechRate)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}

                    override fun onRangeStart(
                        utteranceId: String?,
                        start: Int,
                        end: Int,
                        frame: Int,
                    ) {
                        utteranceRangeListener?.invoke(utteranceId, start, end)
                    }

                    override fun onDone(utteranceId: String?) {
                        utteranceId?.let { oneShotDone.remove(it)?.invoke() }
                        utteranceDoneListener?.invoke(utteranceId)
                    }

                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        // Flushed/stopped utterances never complete; drop their callback.
                        utteranceId?.let { oneShotDone.remove(it) }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        utteranceId?.let { oneShotDone.remove(it)?.invoke() }
                        utteranceDoneListener?.invoke(utteranceId)
                    }
                })
            }
        } else {
            Log.e("TTSService", "Initialization Failed!")
        }
    }

    /**
     * @return true if speech was queued (initialized and not suppressed).
     */
    /**
     * @param force speak even while app speech is suppressed. Use only for
     *   directly user-initiated feedback (e.g. Settings confirmations), never
     *   for background/feature output — that is what suppression exists to stop.
     */
    fun speak(
        text: String,
        flush: Boolean = false,
        utteranceId: String = "TTS_ID_${System.currentTimeMillis()}",
        force: Boolean = false,
        onDone: (() -> Unit)? = null,
    ): Boolean {
        if ((_suppressAppSpeech.value && !force) || text.isBlank()) return false
        if (!_isInitialized.value) return false
        val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val params = Bundle()
        if (onDone != null) oneShotDone[utteranceId] = onDone
        val result = tts?.speak(text, queueMode, params, utteranceId)
        if (result == TextToSpeech.ERROR) oneShotDone.remove(utteranceId)
        return result != TextToSpeech.ERROR
    }

    fun speakWithCooldown(objectName: String) {
        if (_suppressAppSpeech.value) return
        val currentTime = System.currentTimeMillis()
        val lastAnnouncedTime = announcementCooldowns[objectName] ?: 0L

        if (currentTime - lastAnnouncedTime > COOLDOWN_MS) {
            speak(objectName, flush = false)
            announcementCooldowns[objectName] = currentTime
        }
    }

    fun speakLatestWithCooldown(key: String, message: String) {
        if (_suppressAppSpeech.value) return
        val currentTime = System.currentTimeMillis()
        val lastAnnouncedTime = announcementCooldowns[key] ?: 0L

        if (currentTime - lastAnnouncedTime > COOLDOWN_MS) {
            speak(message, flush = true)
            announcementCooldowns[key] = currentTime
        }
    }

    /** Stops all in-flight and queued TTS. */
    fun stop(@Suppress("UNUSED_PARAMETER") utteranceId: String? = null) {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
