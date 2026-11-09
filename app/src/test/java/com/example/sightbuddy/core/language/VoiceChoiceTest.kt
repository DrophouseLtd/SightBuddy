package com.example.sightbuddy.core.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Issue #26: text in another language is spoken (and tagged for TalkBack)
 * with that language's voice only where the phone has one; otherwise it is
 * said in the app's language, as before, and the language is reported as
 * missing (for the one-time hint). For the app's own voice the text is cut
 * into parts, one per change of voice.
 */
class VoiceChoiceTest {

    private val arabic = VoiceInfo("ar", installed = true, needsNetwork = false)
    private val hebrewOldCode = VoiceInfo("iw-IL", installed = true, needsNetwork = false)
    private val chineseNotDownloaded = VoiceInfo("zh-CN", installed = false, needsNetwork = false)
    private val japaneseOnline = VoiceInfo("ja-JP", installed = true, needsNetwork = true)
    private val mandarin = VoiceInfo("cmn-CN", installed = true, needsNetwork = false)
    private val voices = listOf(arabic, hebrewOldCode, chineseNotDownloaded, japaneseOnline)

    @Test
    fun `a language with an installed voice is spoken with it`() {
        assertEquals("ar", VoiceChoice.localeFor("ar", voices) { true })
    }

    @Test
    fun `a voice under the old code for the language counts`() {
        assertEquals("he", VoiceChoice.localeFor("he", voices) { true })
    }

    @Test
    fun `a voice not downloaded, or only online, does not count`() {
        assertNull(VoiceChoice.localeFor("zh", voices) { true })
        assertNull(VoiceChoice.localeFor("ja", voices) { true })
        assertNull(VoiceChoice.localeFor("ko", voices) { true })
    }

    @Test
    fun `when the bare language is not taken, the voice's own locale is used`() {
        assertEquals("cmn-CN", VoiceChoice.localeFor("zh", listOf(mandarin)) { it != "zh" })
    }

    @Test
    fun `an engine that lists no voices is asked about the language itself`() {
        assertEquals("ar", VoiceChoice.localeFor("ar", null) { it == "ar" })
        assertNull(VoiceChoice.localeFor("zh", null) { it == "ar" })
    }

    @Test
    fun `spans keep the languages with a voice, and the others are missing`() {
        val spans = listOf(LanguageSpan(0, 5, "ar"), LanguageSpan(6, 10, "zh"))
        val picked = VoiceChoice.pick(spans) { tag -> if (tag == "ar") "ar" else null }
        assertEquals(listOf(LanguageSpan(0, 5, "ar")), picked.spans)
        assertEquals(setOf("zh"), picked.missing)
    }

    @Test
    fun `spans take the voice's locale`() {
        val picked = VoiceChoice.pick(listOf(LanguageSpan(0, 5, "zh"))) { "cmn-CN" }
        assertEquals(listOf(LanguageSpan(0, 5, "cmn-CN")), picked.spans)
    }

    @Test
    fun `a text with no spans is one part in the app's voice`() {
        assertEquals(listOf(VoiceChoice.Part(0, 11, null)), VoiceChoice.parts("Hello there", emptyList()))
    }

    @Test
    fun `a mixed answer is cut where the voice changes`() {
        val text = "Here it is:\nمرحبا بكم\nThat is all."
        val start = text.indexOf('م')
        val end = start + "مرحبا بكم".length
        val parts = VoiceChoice.parts(text, listOf(LanguageSpan(start, end, "ar")))
        assertEquals(
            listOf("Here it is:\n" to null, "مرحبا بكم" to "ar", "\nThat is all." to null),
            parts.map { text.substring(it.start, it.end) to it.tag },
        )
    }

    @Test
    fun `paragraphs in one language with only spaces between are one part`() {
        val text = "مرحبا\n\nبكم\n"
        val spans = listOf(LanguageSpan(0, 5, "ar"), LanguageSpan(7, 10, "ar"))
        assertEquals(listOf(VoiceChoice.Part(0, text.length, "ar")), VoiceChoice.parts(text, spans))
    }

    @Test
    fun `different languages one after another are parts of their own`() {
        val text = "مرحبا\n出口"
        val spans = listOf(LanguageSpan(0, 5, "ar"), LanguageSpan(6, 8, "zh"))
        assertEquals(
            listOf(VoiceChoice.Part(0, 6, "ar"), VoiceChoice.Part(6, 8, "zh")),
            VoiceChoice.parts(text, spans),
        )
    }
}
