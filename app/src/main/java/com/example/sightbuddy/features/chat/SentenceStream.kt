package com.example.sightbuddy.features.chat

/**
 * Turns an answer arriving part by part into the complete sentences it holds,
 * so the answer can be spoken as it is written (issue #24): the first full
 * sentence plays while the rest is still being generated.
 *
 * A stream: [append] holds whatever it was given until a sentence boundary,
 * then hands back the sentences now complete; [finish] hands back whatever is
 * left when the answer ends. The pieces handed back are the text they were
 * given, in order and trimmed, so the final chat bubble is the full answer.
 *
 * A sentence ends at ".", "!", "?" or an ellipsis ("…") followed by a space,
 * and at the non-Latin ends "。！？" (followed by anything), "؟", "۔" and "।".
 * The end mark and any closing quotes or brackets after it belong to the
 * sentence. An end mark is only known to be one once what follows it has
 * arrived: a model writes "3." and "50" as two parts, and "e." and "g.".
 * A "." after a known abbreviation ("Mr.", "e.g.", "etc."), matched as a
 * whole word in any case, does not end a sentence; nor does the "." of a
 * list item's number at the start of a line ("1. Take two tablets").
 *
 * A line break ends one too: a heading, a label's line or a list item has no
 * full stop, and a page of them was not spoken until its last sentence (the
 * phone, 2026-10-24). The model is told to rejoin lines broken mid-sentence.
 */
class SentenceStream {

    /** The text not yet split off as complete sentences. */
    private val buffer = StringBuilder()

    /** How many characters given so far are not yet in a sentence handed back. */
    val held: Int get() = buffer.length

    /**
     * Hands back the complete sentences now in [text], holding back the last
     * one that has no ending yet (the model may still be writing it).
     */
    fun append(text: String): List<String> {
        buffer.append(text)
        val complete = mutableListOf<String>()
        while (true) {
            val end = sentenceEnd(buffer) ?: break
            val sentence = buffer.substring(0, end).trim()
            buffer.delete(0, end)
            if (sentence.isNotEmpty()) complete.add(sentence)
        }
        // Blank lines between paragraphs belong to no sentence.
        if (buffer.isBlank()) buffer.setLength(0)
        return complete
    }

    /**
     * The answer is over: hands back the last sentence, finished or not.
     * Call once, after the last [append].
     */
    fun finish(): String? {
        val rest = buffer.toString().trim()
        buffer.setLength(0)
        return rest.ifEmpty { null }
    }

    /**
     * The index just past the first sentence end in [text], or null when the
     * text does not yet hold a complete sentence.
     */
    private fun sentenceEnd(text: CharSequence): Int? {
        for (i in text.indices) {
            when (text[i]) {
                '\n' -> if (text.substring(0, i).isNotBlank()) return i + 1
                '。', '！', '？' -> {
                    val after = afterClosers(text, i + 1)
                    if (after < text.length) return after
                    return null
                }
                '.', '!', '?', '…', '؟', '۔', '।' -> {
                    val after = afterClosers(text, i + 1)
                    // Not known yet: the next part may make it "3.50" or "e.g.".
                    if (after >= text.length) return null
                    if (!text[after].isWhitespace()) continue
                    if (text[i] == '.' && endsWithAbbreviation(text, i + 1)) continue
                    if (text[i] == '.' && isListNumber(text, i)) continue
                    return after
                }
            }
        }
        return null
    }

    /** Skips the closing quotes and brackets that follow a sentence end. */
    private fun afterClosers(text: CharSequence, start: Int): Int {
        var i = start
        while (i < text.length && text[i] in CLOSERS) i++
        return i
    }

    /**
     * Whether the "." at [dot] ends a list item's number ("1. Take two
     * tablets"): one to three digits that begin their line. A number ending
     * a sentence inside a line ("There are 5.") still ends it.
     */
    private fun isListNumber(text: CharSequence, dot: Int): Boolean {
        var start = dot
        while (start > 0 && text[start - 1].isDigit()) start--
        if (dot - start !in 1..3) return false
        var i = start - 1
        while (i >= 0 && text[i] != '\n') {
            if (!text[i].isWhitespace()) return false
            i--
        }
        return true
    }

    /** Whether a known abbreviation, as a whole word, ends just before [afterDot]. */
    private fun endsWithAbbreviation(text: CharSequence, afterDot: Int): Boolean =
        ABBREVIATIONS.any { abbr ->
            val start = afterDot - abbr.length
            start >= 0 &&
                text.substring(start, afterDot).equals(abbr, ignoreCase = true) &&
                (start == 0 || !text[start - 1].isLetterOrDigit())
        }

    companion object {
        /** Closing marks that belong to the sentence they follow. */
        private val CLOSERS = charArrayOf(')', ']', '}', '"', '\'', '»', '”', '’', '„', '«', '」', '』')

        private val ABBREVIATIONS = listOf(
            "Mr.", "Mrs.", "Ms.", "Dr.", "Prof.", "St.", "Sr.", "Jr.", "vs.",
            "etc.", "e.g.", "i.e.", "cf.", "approx.", "Fig.", "Inc.", "Ltd.",
        )
    }
}
