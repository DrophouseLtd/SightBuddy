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
        assertEquals(ANDROID, eff(ANDROID, whisper = true, gemma = true))
    }

    @Test
    fun gemmaSpeechIsHiddenAndAStoredChoiceOfItRunsWhisper() {
        // Hidden 2026-10-24: slower than Whisper and it made up text from silence.
        assertEquals(WHISPER, SttChoice.fromKey("gemma"))
        assertEquals(WHISPER, eff(GEMMA, whisper = true, gemma = true))
        // Never the fallback either: no Whisper means Android.
        assertEquals(ANDROID, eff(WHISPER, whisper = false, gemma = true))
        assertEquals(ANDROID, eff(GEMMA, whisper = false, gemma = true))
    }

    @Test
    fun otherLanguagesAlwaysUseAndroid() {
        assertEquals(ANDROID, eff(WHISPER, whisper = true, gemma = true, english = false))
    }

    @Test
    fun settingsOffersWhisperThenAndroidNeverGemma() {
        assertEquals(listOf(WHISPER, ANDROID), SttChoice.available(whisperReady = true, gemmaInstalled = true))
        assertEquals(listOf(ANDROID), SttChoice.available(whisperReady = false, gemmaInstalled = true))
    }
}
