package com.example.sightbuddy.core.stt

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The silence padding that keeps a transcription under the greedy decoder's
 * output-token cap (see WhisperEngine.DECODER_CAP_PADDING_S), plus the ~30 s
 * input ceiling the recognizer enforces silently.
 */
class WhisperPaddingTest {

    private val rate = WhisperEngine.SAMPLE_RATE

    @Test
    fun shortUtteranceGetsFullPadding() {
        val padded = WhisperEngine.padForDecoderCap(FloatArray(rate * 3))
        assertEquals(rate * 3 + rate * 2, padded.size)
        assertEquals(0f, padded.last(), 0f)
        assertEquals(0f, padded[rate * 3], 0f)
    }

    @Test
    fun emptyInputStaysEmpty() {
        assertEquals(0, WhisperEngine.padForDecoderCap(FloatArray(0)).size)
    }

    @Test
    fun longRecordingIsTrimmedToTheFeatureWindow() {
        // A minute of audio: the recognizer would keep only ~30 s anyway, so
        // the padding must not push the buffer past the real limit.
        val padded = WhisperEngine.padForDecoderCap(FloatArray(rate * 60))
        assertEquals((29.5f * rate).toInt(), padded.size)
    }

    @Test
    fun nearlyFullBufferGetsWhateverPaddingFits() {
        val padded = WhisperEngine.padForDecoderCap(FloatArray((rate * 28.75f).toInt()))
        // 0.75 s of the 2 s padding fits under the 29.5 s ceiling.
        assertEquals((rate * 29.5f).toInt(), padded.size)
    }
}
