package com.example.sightbuddy.core.language

/** One language the identifier thinks a text may be in, and how sure it is (0 to 1). */
data class LanguageGuess(val tag: String, val confidence: Float)

/** The stretch of a text from [start] to [end] (not included) is in [tag]'s language. */
data class LanguageSpan(val start: Int, val end: Int, val tag: String)

/**
 * When a paragraph counts as another language than the app's (issue #26).
 * Read in full and Read from image keep a text's own language, and TalkBack
 * and the app's voice said Arabic or Chinese with English pronunciation, as
 * the text reached them with no language. Each paragraph's language is now
 * found on the phone (ML Kit Language Identification), and these rules
 * decide what the guess is worth:
 *
 * - A paragraph mostly in a non-Latin script (Arabic, Chinese, Cyrillic...)
 *   takes the identifier's best guess when it is at least
 *   [OTHER_SCRIPT_CONFIDENCE] sure: the script already says most of it, so
 *   a short sign ("出口") counts.
 * - A Latin-script paragraph needs [MIN_LATIN_LETTERS] letters and a guess
 *   [LATIN_CONFIDENCE] sure. Short sentences are where the identifier
 *   confuses English with other Latin languages, and the app's voice must
 *   not flip between them sentence by sentence.
 * - Anything else, the app's own language, an undetermined guess or a
 *   romanised one ("ja-Latn") keeps the app's language: null.
 */
object TextLanguage {
    const val MIN_LATIN_LETTERS = 40
    const val LATIN_CONFIDENCE = 0.9f
    const val OTHER_SCRIPT_CONFIDENCE = 0.5f

    /** Each non-blank line of [text], without the spaces at its edges. */
    fun paragraphs(text: String): List<IntRange> {
        val found = ArrayList<IntRange>()
        var lineStart = 0
        while (lineStart <= text.length) {
            val newline = text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }
            var start = lineStart
            var end = newline
            while (start < end && text[start].isWhitespace()) start++
            while (end > start && text[end - 1].isWhitespace()) end--
            if (end > start) found += start until end
            lineStart = newline + 1
        }
        return found
    }

    /** Whether [paragraph] could be anything but the app's language: worth asking the identifier. */
    fun worthAsking(paragraph: String): Boolean {
        val letters = Letters.of(paragraph)
        return when {
            letters.total == 0 -> false
            letters.mostlyLatin -> letters.latin >= MIN_LATIN_LETTERS
            else -> true
        }
    }

    /**
     * The language [paragraph] is in, as a language code, when it is not
     * [appLanguage]'s and the [guesses] are sure enough; otherwise null.
     */
    fun choose(paragraph: String, guesses: List<LanguageGuess>, appLanguage: String): String? {
        if (!worthAsking(paragraph)) return null
        val best = guesses.maxByOrNull { it.confidence } ?: return null
        val needed = if (Letters.of(paragraph).mostlyLatin) LATIN_CONFIDENCE else OTHER_SCRIPT_CONFIDENCE
        if (best.confidence < needed) return null
        if (best.tag.equals("und", ignoreCase = true)) return null
        // A romanised guess ("ja-Latn"): no voice reads Latin letters as Japanese.
        if (best.tag.contains("-Latn", ignoreCase = true)) return null
        val language = language(best.tag)
        return if (language == language(appLanguage)) null else language
    }

    /**
     * The language part of a tag or a locale ("zh-CN", "cmn_CN"), lower case,
     * with the old and new codes for one language made one ("iw" and "he").
     * Mandarin and Cantonese voices read Chinese text, so they are Chinese.
     */
    fun language(tag: String): String {
        val code = tag.substringBefore('-').substringBefore('_').lowercase()
        return SAME_LANGUAGE[code] ?: code
    }

    private val SAME_LANGUAGE = mapOf(
        "iw" to "he",
        "in" to "id",
        "ji" to "yi",
        "cmn" to "zh",
        "yue" to "zh",
        "no" to "nb",
        "tl" to "fil",
    )

    /** A paragraph's letters, counted by script. */
    private class Letters(val total: Int, val latin: Int) {
        val mostlyLatin get() = latin * 2 >= total

        companion object {
            fun of(text: String): Letters {
                var total = 0
                var latin = 0
                var i = 0
                while (i < text.length) {
                    val cp = text.codePointAt(i)
                    if (Character.isLetter(cp)) {
                        total++
                        if (Character.UnicodeScript.of(cp) == Character.UnicodeScript.LATIN) latin++
                    }
                    i += Character.charCount(cp)
                }
                return Letters(total, latin)
            }
        }
    }
}
