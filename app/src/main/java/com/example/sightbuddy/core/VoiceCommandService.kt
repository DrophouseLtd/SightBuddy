package com.example.sightbuddy.core

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * The platform speech recogniser.
 *
 * A recogniser is built for each recording and destroyed with it. Reusing one
 * across sessions is what the API appears to invite, but in practice a session
 * that ends badly leaves the instance unable to start again, and it never
 * recovers: starts then succeed perhaps half the time. Building a fresh one
 * costs a few milliseconds and removes that failure entirely.
 *
 * Every callback carries the id of the session it belongs to. A destroyed
 * recogniser can still deliver a late callback, and without the id that
 * callback would report the *new* session as finished.
 */
class VoiceCommandService(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null

    /** Incremented per recording; callbacks from older sessions are dropped. */
    private var sessionId = 0
    
    private val _recognizedText = MutableStateFlow("")
    val recognizedText = _recognizedText.asStateFlow()

    private val _lastError = MutableStateFlow(0)
    val lastError = _lastError.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening = _isListening.asStateFlow()

    /** Replace the recogniser with a clean one. Main thread only, as the API requires. */
    private fun rebuild(): Boolean {
        speechRecognizer?.let { old -> runCatching { old.destroy() } }
        speechRecognizer = null
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e("VoiceCommandService", "Speech recognition is not available on this device.")
            return false
        }
        sessionId += 1
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        setupListener(sessionId)
        return true
    }

    private fun setupListener(session: Int) {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            private fun stale(): Boolean = session != sessionId

            override fun onReadyForSpeech(params: Bundle?) {
                if (stale()) return
                _isListening.value = true
                Log.i("VoiceCommandService", "Ready for speech")
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                if (stale()) return
                _isListening.value = false
                _lastError.value = error
                Log.e("VoiceCommandService", "Speech recognition error: $error")
            }

            override fun onResults(results: Bundle?) {
                if (stale()) return
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val command = matches[0]
                    Log.i("VoiceCommandService", "Recognized command: $command")
                    _recognizedText.value = command
                }
                _isListening.value = false
            }

            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    fun startListening() {
        if (!rebuild()) return
        _recognizedText.value = ""
        _lastError.value = 0
        _isListening.value = true
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            // Recognise in the app's chosen language, not always English.
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // NOTE: do not set the EXTRA_SPEECH_INPUT_*_SILENCE_LENGTH_MILLIS hints
            // here. They were tried to stop the recogniser ending a recording while
            // the user was still talking. Passed as Long they were ignored, and once
            // corrected to Int the recogniser rejected every session with
            // ERROR_CLIENT and returned no transcript at all. Endpointing on this
            // platform is not ours to configure; lengthening a recording needs
            // partial results plus a restart, not an extra.
        }
        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        _isListening.value = false
    }

    fun shutdown() {
        speechRecognizer?.destroy()
    }
}
