package com.example.sightbuddy.core.stt

import com.example.sightbuddy.core.LanguageStore
import org.junit.Assert.*
import org.junit.Test

class MultilingualPolicyTest {
    @Test fun supportedLanguagesAreExplicitAndUnknownIsNotEnglish() {
        for (tag in listOf("pl", "fi", "en")) assertTrue(LanguageStore.supportsWhisper(tag))
        for (tag in listOf("", "de", "xx", "EN")) assertFalse(LanguageStore.supportsWhisper(tag))
    }

    @Test fun chosenAndroidRemainsAndroidAndUnloadedFallsBack() {
        assertEquals(SttChoice.ANDROID, SttChoice.effective(SttChoice.ANDROID, true, false, false))
        assertEquals(SttChoice.ANDROID, SttChoice.effective(SttChoice.WHISPER, false, false, false))
        assertEquals(SttChoice.WHISPER, SttChoice.effective(SttChoice.WHISPER, true, false, false))
    }
}
