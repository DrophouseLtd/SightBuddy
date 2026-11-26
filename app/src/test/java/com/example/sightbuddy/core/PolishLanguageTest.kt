package com.example.sightbuddy.core

import com.example.sightbuddy.R
import com.example.sightbuddy.features.chat.ChatPrompts
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PolishLanguageTest {
    @Test
    fun cycleContainsAllThreeLanguages() {
        assertEquals("fi", LanguageStore.nextLanguage("en"))
        assertEquals("pl", LanguageStore.nextLanguage("fi"))
        assertEquals("en", LanguageStore.nextLanguage("pl"))
        assertEquals(R.string.language_name_polish, LanguageStore.languageNameRes("pl"))
    }

    @Test
    fun polishPromptPreservesLanguageOfReadText() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("pl"))
            assertTrue(OpenAiTransport.replyLanguageInstruction().contains("po polsku"))
            for (onDevice in listOf(false, true)) {
                val rule = ChatPrompts.languageRule(onDevice, reading = true)
                assertTrue(rule.contains("po polsku"))
                assertTrue(rule.contains("języku oryginału"))
            }
        } finally {
            Locale.setDefault(previous)
        }
    }
}
