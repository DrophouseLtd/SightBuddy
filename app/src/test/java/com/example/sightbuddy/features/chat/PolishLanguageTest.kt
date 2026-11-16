package com.example.sightbuddy.features.chat

import com.example.sightbuddy.core.LanguageStore
import com.example.sightbuddy.core.OpenAiTransport
import com.example.sightbuddy.core.stt.SttChoice
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class PolishLanguageTest {
    @Test fun languageCycleIncludesAllThree() {
        assertEquals("fi", LanguageStore.nextLanguage("en"))
        assertEquals("pl", LanguageStore.nextLanguage("fi"))
        assertEquals("en", LanguageStore.nextLanguage("pl"))
    }

    @Test fun polishReadOriginalRequiresTheWholeCommand() {
        assertEquals(ChatKeywords.Keyword.READ_ORIGINAL, ChatKeywords.match("Przeczytaj oryginał."))
        assertEquals(ChatKeywords.Keyword.READ_ORIGINAL, ChatKeywords.match("Czytaj oryginał"))
        assertNull(ChatKeywords.match("przeczytaj oryginał menu"))
        assertEquals(ChatKeywords.Keyword.READ_ORIGINAL, ChatKeywords.match("read original"))
        assertEquals(ChatKeywords.Keyword.READ_ORIGINAL, ChatKeywords.match("lue alkuperäinen"))
    }

    @Test fun polishAiRulesPreserveOriginalText() {
        val old = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("pl"))
            assertTrue(OpenAiTransport.replyLanguageInstruction().contains("po polsku"))
            assertTrue(OpenAiTransport.readingLanguageInstruction().contains("oryginalnym języku"))
            assertTrue(ChatPrompts.languageRule(onDevice = true).contains("po polsku"))
        } finally { Locale.setDefault(old) }
    }

    @Test fun englishOnlyWhisperDoesNotServePolish() {
        assertEquals(SttChoice.ANDROID, SttChoice.effective(
            chosen = SttChoice.WHISPER, whisperReady = true, gemmaInstalled = false, english = false,
        ))
        assertEquals(SttChoice.WHISPER, SttChoice.effective(
            chosen = SttChoice.WHISPER, whisperReady = true, gemmaInstalled = false, english = true,
        ))
    }
}
