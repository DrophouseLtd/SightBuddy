package com.example.sightbuddy.core.stt

import com.example.sightbuddy.core.stt.SttChoice.ANDROID
import com.example.sightbuddy.core.stt.SttChoice.GEMMA
import com.example.sightbuddy.core.stt.SttChoice.WHISPER
import org.junit.Assert.assertEquals
import org.junit.Test

class SttChoiceTest {

    private fun eff(chosen: SttChoice, whisper: Boolean, gemma: Boolean, english: Boolean = true) =
        SttChoice.effective(chosen, whisper, gemma, english)

    @Test
    fun theChoiceRunsWhenItsModelIsHere() {
        assertEquals(WHISPER, eff(WHISPER, whisper = true, gemma = true))
        assertEquals(GEMMA, eff(GEMMA, whisper = true, gemma = true))
        assertEquals(ANDROID, eff(ANDROID, whisper = true, gemma = true))
    }

    @Test
    fun aMissingModelFallsBackToTheOtherThenAndroid() {
        assertEquals(GEMMA, eff(WHISPER, whisper = false, gemma = true))
        assertEquals(WHISPER, eff(GEMMA, whisper = true, gemma = false))
        assertEquals(ANDROID, eff(WHISPER, whisper = false, gemma = false))
    }

    @Test
    fun otherLanguagesAlwaysUseAndroid() {
        assertEquals(ANDROID, eff(WHISPER, whisper = true, gemma = true, english = false))
    }

    @Test
    fun settingsCyclesThroughInstalledModelsThenAndroid() {
        assertEquals(listOf(WHISPER, GEMMA, ANDROID), SttChoice.available(whisperReady = true, gemmaInstalled = true))
        assertEquals(listOf(GEMMA, ANDROID), SttChoice.available(whisperReady = false, gemmaInstalled = true))
    }
}
