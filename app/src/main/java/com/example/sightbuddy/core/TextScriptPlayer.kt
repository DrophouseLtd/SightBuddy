package com.example.sightbuddy.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pure index math for [TextScriptPlayer] (unit-testable, no Android deps).
 */
internal object TextScriptPlayback {
    fun seekBackIndex(charIndex: Int, step: Int): Int =
        (charIndex - step).coerceAtLeast(0)

    sealed class SeekForwardResult {
        data object NoOp : SeekForwardResult()
        data object AtEnd : SeekForwardResult()
        data class PlayFrom(val index: Int) : SeekForwardResult()
    }

    fun seekForward(charIndex: Int, scriptLength: Int, step: Int): SeekForwardResult {
        if (scriptLength == 0 || charIndex >= scriptLength) return SeekForwardResult.NoOp
        val next = charIndex + step
        return if (next >= scriptLength) SeekForwardResult.AtEnd
        else SeekForwardResult.PlayFrom(next)
    }

    fun indexForResume(charIndex: Int, scriptLength: Int, rewind: Int): Int {
        if (scriptLength == 0) return 0
        val clamped = charIndex.coerceIn(0, scriptLength)
        return if (clamped >= scriptLength) {
            (scriptLength - rewind).coerceAtLeast(0)
        } else {
            (clamped - rewind).coerceAtLeast(0)
        }
    }

    /** Maps a range offset from the current TTS segment to an index in the full script. */
    fun absolutePosition(segmentBaseIndex: Int, offsetInSegment: Int, scriptLength: Int): Int =
        (segmentBaseIndex + offsetInSegment).coerceIn(0, scriptLength)
}

/**
 * Character-indexed TTS reader for a single fixed script (Text chat playback).
 *
 * Seek steps and resume rewind are character-based for stable behavior across
 * languages and punctuation. All navigation stops in-flight speech before restarting.
 */
class TextScriptPlayer(
    private val tts: TTSService,
    private val seekStepChars: Int = SEEK_STEP_CHARS,
    private val resumeRewindChars: Int = RESUME_REWIND_CHARS,
    private val debounceMs: Long = DEBOUNCE_MS,
) {
    enum class State { EMPTY, PLAYING, PAUSED, FINISHED }

    private var script: String = ""
    private var charIndex: Int = 0
    /** Script index where the current TTS utterance segment begins. */
    private var utteranceBaseIndex: Int = 0
    private var state: State = State.EMPTY
    private val _playbackState = MutableStateFlow(State.EMPTY)
    val playbackState = _playbackState.asStateFlow()
    private var lastInputMs: Long = 0L

    val hasScript: Boolean get() = script.isNotBlank()
    val currentState: State get() = state

    private fun setState(next: State) {
        state = next
        _playbackState.value = next
    }

    init {
        tts.setOnUtteranceRange { utteranceId, start, _ ->
            if (utteranceId == UTTERANCE_ID && state == State.PLAYING) {
                syncCursorToTtsRange(start)
            }
        }
        tts.setOnUtteranceDone { utteranceId ->
            if (utteranceId == UTTERANCE_ID) {
                onUtteranceFinished()
            }
        }
    }

    private fun syncCursorToTtsRange(offsetInSegment: Int) {
        charIndex = TextScriptPlayback.absolutePosition(
            utteranceBaseIndex,
            offsetInSegment,
            script.length,
        )
    }

    /** Replace script and reset cursor; does not speak. */
    fun loadScript(text: String) {
        script = text.trim()
        charIndex = 0
        setState(if (script.isBlank()) State.EMPTY else State.PAUSED)
        tts.stop(UTTERANCE_ID)
    }

    fun clear() {
        script = ""
        charIndex = 0
        setState(State.EMPTY)
        tts.stop(UTTERANCE_ID)
    }

    /** Stop speech but keep script and cursor (e.g. mic press, mode change). */
    fun interrupt() {
        if (script.isBlank()) return
        tts.stop(UTTERANCE_ID)
        if (state == State.PLAYING) {
            setState(State.PAUSED)
        }
    }

    fun playFromStart(): Boolean {
        if (script.isBlank()) return false
        charIndex = 0
        return playFrom(charIndex, applyResumeRewind = false)
    }

    fun togglePause(): Boolean {
        if (script.isBlank()) return false
        return when (state) {
            State.PLAYING -> {
                interrupt()
                true
            }
            State.PAUSED, State.FINISHED -> {
                playFrom(charIndex, applyResumeRewind = true)
                true
            }
            State.EMPTY -> false
        }
    }

    fun seekBack(): Boolean {
        if (script.isBlank() || !debounce()) return false
        charIndex = TextScriptPlayback.seekBackIndex(charIndex, seekStepChars)
        return playFrom(charIndex, applyResumeRewind = false)
    }

    fun seekForward(): Boolean {
        if (script.isBlank() || !debounce()) return false
        return when (val result = TextScriptPlayback.seekForward(charIndex, script.length, seekStepChars)) {
            is TextScriptPlayback.SeekForwardResult.NoOp -> false
            is TextScriptPlayback.SeekForwardResult.AtEnd -> {
                charIndex = script.length
                tts.stop(UTTERANCE_ID)
                setState(State.FINISHED)
                false
            }
            is TextScriptPlayback.SeekForwardResult.PlayFrom -> {
                charIndex = result.index
                playFrom(charIndex, applyResumeRewind = false)
            }
        }
    }

    /** Hold backward 2s — always restart from the beginning. */
    fun restartFromBeginning(): Boolean {
        if (script.isBlank()) return false
        charIndex = 0
        return playFrom(0, applyResumeRewind = false)
    }

    private fun playFrom(index: Int, applyResumeRewind: Boolean): Boolean {
        if (script.isBlank()) return false
        val start = if (applyResumeRewind) {
            TextScriptPlayback.indexForResume(index, script.length, resumeRewindChars)
        } else {
            index.coerceIn(0, script.length)
        }
        charIndex = start
        utteranceBaseIndex = start
        if (charIndex >= script.length) {
            setState(State.FINISHED)
            tts.stop(UTTERANCE_ID)
            return false
        }
        tts.stop(UTTERANCE_ID)
        val segment = script.substring(charIndex)
        val started = tts.speak(segment, flush = true, utteranceId = UTTERANCE_ID)
        if (started) {
            syncCursorToTtsRange(0)
        }
        setState(if (started) State.PLAYING else State.PAUSED)
        return started
    }

    private fun onUtteranceFinished() {
        if (script.isBlank()) {
            setState(State.EMPTY)
            return
        }
        charIndex = script.length
        setState(State.FINISHED)
    }

    private fun debounce(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastInputMs < debounceMs) return false
        lastInputMs = now
        return true
    }

    companion object {
        const val UTTERANCE_ID = "TEXT_SCRIPT_PLAYER"
        const val SEEK_STEP_CHARS = 20
        const val RESUME_REWIND_CHARS = 5
        const val DEBOUNCE_MS = 300L
        const val HOLD_BACK_RESTART_MS = 1_000L
        const val HOLD_FORWARD_TOGGLE_MS = 1_000L
    }
}
