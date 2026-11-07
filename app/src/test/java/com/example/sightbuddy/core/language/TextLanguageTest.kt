package com.example.sightbuddy.core.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #26: Read in full and Read from image read Arabic or Chinese text in
 * its own language, but both TalkBack and the app's voice said it with
 * English pronunciation, as the text went to them with no language. The
 * language is now found per paragraph, and these are the rules for when a
 * paragraph counts as another language than the app's: a non-Latin script
 * needs a fair guess; Latin-script text needs a long paragraph and a sure
 * guess, so a short English sentence never flips to another Latin language.
 */
class TextLanguageTest {

    private fun guess(tag: String, confidence: Float) = LanguageGuess(tag, confidence)

    private val longFrench = "Prendre deux comprimés par jour avec un grand verre d'eau, après le repas."
    private val longEnglish = "Take two tablets a day with a large glass of water, after a meal."

    @Test
    fun `an Arabic paragraph is Arabic`() {
        assertEquals("ar", TextLanguage.choose("مرحبا بكم في المدينة", listOf(guess("ar", 0.98f)), "en"))
    }

    @Test
    fun `a Chinese paragraph is Chinese, short as it is`() {
        assertEquals("zh", TextLanguage.choose("出口", listOf(guess("zh", 0.9f)), "en"))
    }

    @Test
    fun `English in the English app is the app's language`() {
        assertNull(TextLanguage.choose(longEnglish, listOf(guess("en", 0.99f)), "en"))
    }

    @Test
    fun `a short Latin sentence keeps the app's language, however sure the guess`() {
        assertNull(TextLanguage.choose("Ciao, come stai?", listOf(guess("it", 0.97f)), "en"))
        assertNull(TextLanguage.choose("The label says so.", listOf(guess("de", 0.95f)), "en"))
    }

    @Test
    fun `a long Latin paragraph in another language is that language when the guess is sure`() {
        assertEquals("fr", TextLanguage.choose(longFrench, listOf(guess("fr", 0.97f)), "en"))
    }

    @Test
    fun `an unsure guess keeps the app's language`() {
        assertNull(TextLanguage.choose(longFrench, listOf(guess("fr", 0.6f), guess("en", 0.3f)), "en"))
        assertNull(TextLanguage.choose("مرحبا بكم", listOf(guess("ar", 0.3f), guess("fa", 0.25f)), "en"))
    }

    @Test
    fun `the surest guess counts, whatever the order`() {
        assertEquals(
            "ar",
            TextLanguage.choose("مرحبا بكم في المدينة", listOf(guess("fa", 0.1f), guess("ar", 0.85f)), "en"),
        )
    }

    @Test
    fun `undetermined and romanised guesses keep the app's language`() {
        assertNull(TextLanguage.choose("مرحبا بكم", listOf(guess("und", 1f)), "en"))
        assertNull(TextLanguage.choose(longFrench, listOf(guess("ja-Latn", 0.95f)), "en"))
        assertNull(TextLanguage.choose("مرحبا بكم", emptyList(), "en"))
    }

    @Test
    fun `the Finnish app reads English text with English, but Finnish text as before`() {
        assertEquals("en", TextLanguage.choose(longEnglish, listOf(guess("en", 0.99f)), "fi"))
        val finnish = "Ota kaksi tablettia päivässä ison vesilasillisen kanssa aterian jälkeen."
        assertNull(TextLanguage.choose(finnish, listOf(guess("fi", 0.99f)), "fi"))
    }

    @Test
    fun `text with no letters keeps the app's language`() {
        assertNull(TextLanguage.choose("12:30 – 14:00", listOf(guess("zh", 0.9f)), "en"))
    }

    @Test
    fun `a short English sentence with a few Arabic words stays English`() {
        assertNull(TextLanguage.choose("The sign says: مرحبا", listOf(guess("ar", 0.6f)), "en"))
    }

    @Test
    fun `only text that may be another language is asked about`() {
        assertFalse(TextLanguage.worthAsking("Left"))
        assertFalse(TextLanguage.worthAsking("Hold steady."))
        assertFalse(TextLanguage.worthAsking("12:30"))
        assertTrue(TextLanguage.worthAsking("出口"))
        assertTrue(TextLanguage.worthAsking("مرحبا"))
        assertTrue(TextLanguage.worthAsking(longFrench))
        assertTrue(TextLanguage.worthAsking(longEnglish))
    }

    @Test
    fun `paragraphs are the text's non-blank lines, without their edges' spaces`() {
        val text = "Here is the text:\n\n  مرحبا بكم \n这是一个测试"
        val found = TextLanguage.paragraphs(text).map { text.substring(it) }
        assertEquals(listOf("Here is the text:", "مرحبا بكم", "这是一个测试"), found)
    }

    @Test
    fun `old and new codes for a language are the same language`() {
        assertEquals(TextLanguage.language("he"), TextLanguage.language("iw"))
        assertEquals(TextLanguage.language("id"), TextLanguage.language("in"))
        assertEquals("zh", TextLanguage.language("zh-CN"))
        assertEquals("zh", TextLanguage.language("cmn_CN"))
        assertEquals("ar", TextLanguage.language("AR"))
    }
}
