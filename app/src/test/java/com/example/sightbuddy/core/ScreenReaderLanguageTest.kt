package com.example.sightbuddy.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app's words tagged with its language for the screen reader (issue #28):
 * with the phone in English and the app in Finnish, TalkBack read every Finnish
 * button with an English voice.
 */
class ScreenReaderLanguageTest {

    @Test
    fun `tagged only when the app's language differs from the phone's`() {
        assertTrue(ScreenReaderLanguage.wanted(appLanguage = "fi", phoneLanguage = "en"))
        assertFalse(ScreenReaderLanguage.wanted(appLanguage = "fi", phoneLanguage = "fi"))
        assertFalse(ScreenReaderLanguage.wanted(appLanguage = "en", phoneLanguage = "en"))
        // An English app on a Finnish phone: the English words get an English voice.
        assertTrue(ScreenReaderLanguage.wanted(appLanguage = "en", phoneLanguage = "fi"))
    }

    @Test
    fun `text with no language of its own is tagged whole`() {
        assertEquals(listOf(0 until 12), ScreenReaderLanguage.untagged(12, emptyList()))
    }

    @Test
    fun `stretches already in another language keep it, the rest is the app's`() {
        // "Lue: مرحبا tänään": the Arabic at 5..10 is tagged already (issue #26).
        assertEquals(listOf(0 until 5, 10 until 17), ScreenReaderLanguage.untagged(17, listOf(5 until 10)))
        // Tagged at the very start and end; overlapping and unordered tags.
        assertEquals(listOf(3 until 6), ScreenReaderLanguage.untagged(10, listOf(6 until 10, 0 until 2, 1 until 3)))
    }

    @Test
    fun `text tagged throughout, or empty, gets nothing more`() {
        assertEquals(emptyList<IntRange>(), ScreenReaderLanguage.untagged(8, listOf(0 until 8)))
        assertEquals(emptyList<IntRange>(), ScreenReaderLanguage.untagged(0, emptyList()))
    }
}
