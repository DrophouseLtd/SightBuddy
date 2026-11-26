package com.example.sightbuddy.core.stt

import org.junit.Assert.*
import org.junit.Test

class WhisperAudioTest {
    @Test fun allSamplesSurviveWithSilenceAtEachBoundary() {
        for (seconds in listOf(0, 1, 29, 30, 60)) {
            val original = FloatArray(seconds * 16000) { (it + 1).toFloat() }
            val windows = WhisperEngine.audioWindows(original)
            val actual = windows.flatMap { it.dropLast(32000) }
            assertEquals(original.size, actual.size)
            assertTrue(original.indices.all { original[it] == actual[it] })
            assertTrue(windows.all { it.size <= 29 * 16000 + 8000 && it.takeLast(32000).all { x -> x == 0f } })
        }
    }
}
