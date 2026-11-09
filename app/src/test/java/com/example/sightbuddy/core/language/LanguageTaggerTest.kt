package com.example.sightbuddy.core.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #26: a text's language is found paragraph by paragraph, so a mixed
 * answer (the AI's English sentence, then the Arabic text it read) is tagged
 * in parts. The identifier is a fake that answers by script, by hand.
 */
class LanguageTaggerTest {

    /** Paragraphs asked about, and the answers not given yet. */
    private val asked = mutableListOf<String>()
    private val waiting = mutableListOf<Pair<String, (List<LanguageGuess>) -> Unit>>()

    private fun guessFor(text: String): List<LanguageGuess> = when {
        text.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.ARABIC } ->
            listOf(LanguageGuess("ar", 0.97f))
        text.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN } ->
            listOf(LanguageGuess("zh", 0.95f))
        else -> listOf(LanguageGuess("en", 0.99f))
    }

    private val answersAtOnce = LanguageIdentifier { text, onResult ->
        asked += text
        onResult(guessFor(text))
    }

    private val answersLater = LanguageIdentifier { text, onResult ->
        asked += text
        waiting += text to onResult
    }

    private fun spansOf(tagger: LanguageTagger, text: String): List<LanguageSpan>? {
        var result: List<LanguageSpan>? = null
        tagger.spans(text) { result = it }
        return result
    }

    private val mixed = "Here is the text on the sign:\nمرحبا بكم في المدينة\n这是一个测试"

    @Test
    fun `a mixed answer is tagged paragraph by paragraph`() {
        val tagger = LanguageTagger(answersAtOnce) { "en" }
        val spans = spansOf(tagger, mixed)!!
        assertEquals(
            listOf("مرحبا بكم في المدينة" to "ar", "这是一个测试" to "zh"),
            spans.map { mixed.substring(it.start, it.end) to it.tag },
        )
        // The short English sentence was not worth asking about.
        assertFalse(asked.contains("Here is the text on the sign:"))
    }

    @Test
    fun `an English answer has no spans`() {
        val tagger = LanguageTagger(answersAtOnce) { "en" }
        val text = "The leaflet says to take two tablets a day with water, after a meal.\nDo not drive."
        assertEquals(emptyList<LanguageSpan>(), spansOf(tagger, text))
    }

    @Test
    fun `answers that come back out of order still make spans in text order`() {
        val tagger = LanguageTagger(answersLater) { "en" }
        var result: List<LanguageSpan>? = null
        tagger.spans(mixed) { result = it }
        assertNull(result)
        assertEquals(2, waiting.size)
        waiting[1].let { (text, answer) -> answer(guessFor(text)) }
        assertNull("not until every paragraph is known", result)
        waiting[0].let { (text, answer) -> answer(guessFor(text)) }
        assertEquals(listOf("ar", "zh"), result!!.map { it.tag })
    }

    @Test
    fun `a paragraph already known is not asked about again`() {
        val tagger = LanguageTagger(answersAtOnce) { "en" }
        spansOf(tagger, "مرحبا بكم في المدينة")
        spansOf(tagger, "مرحبا بكم في المدينة\n这是一个测试")
        assertEquals(listOf("مرحبا بكم في المدينة", "这是一个测试"), asked)
    }

    @Test
    fun `text with nothing worth asking about is answered at once, without the identifier`() {
        val tagger = LanguageTagger(answersLater) { "en" }
        assertFalse(tagger.needsAsking("Left"))
        assertEquals(emptyList<LanguageSpan>(), spansOf(tagger, "Left"))
        assertTrue(asked.isEmpty())
        assertTrue(tagger.needsAsking(mixed))
    }

    @Test
    fun `a failed identification keeps the app's language`() {
        val tagger = LanguageTagger({ _, onResult -> onResult(emptyList()) }) { "en" }
        assertEquals(emptyList<LanguageSpan>(), spansOf(tagger, "مرحبا بكم"))
    }
}
