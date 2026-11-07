package com.example.sightbuddy.core.language

/**
 * Says which languages a text could be in (ML Kit Language Identification
 * in the app). [onResult] is called once; an empty list when it could not
 * tell, or failed.
 */
fun interface LanguageIdentifier {
    fun identify(text: String, onResult: (List<LanguageGuess>) -> Unit)

    /**
     * Loads the model before it is needed, so the first long sentence is not
     * late, and a long page does not run out of [SpeechLanguageOrder.WAIT_MS]
     * on a cold model. Failures are ignored.
     */
    fun warmUp() {}
}

/**
 * Finds the stretches of a text in another language than the app's (issue
 * #26), paragraph by paragraph ([TextLanguage.paragraphs]), so a mixed
 * answer (the AI's English sentence, then the Arabic text it read) is tagged
 * in parts. Only paragraphs worth asking about go to the [identifier]
 * ([TextLanguage.worthAsking]), and each paragraph's answer is kept, so a
 * sentence spoken as it streamed, and then shown in the finished answer, is
 * asked about once.
 *
 * @param appLanguage the language the app speaks in, now.
 */
class LanguageTagger(
    private val identifier: LanguageIdentifier,
    private val appLanguage: () -> String,
) {
    /** Paragraphs' guesses, newest last; the oldest go past [KEPT]. */
    private val known = object : LinkedHashMap<String, List<LanguageGuess>>(KEPT, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<LanguageGuess>>?) =
            size > KEPT
    }

    /** Whether any paragraph of [text] needs the identifier: false for a short English line. */
    fun needsAsking(text: String): Boolean =
        TextLanguage.paragraphs(text).any { TextLanguage.worthAsking(text.substring(it)) }

    /**
     * The stretches of [text] in another language, in text order, to
     * [onResult] once every paragraph is known: at once when nothing needs
     * asking or every answer is known already, else from the identifier's
     * last answer.
     */
    fun spans(text: String, onResult: (List<LanguageSpan>) -> Unit) {
        val asked = TextLanguage.paragraphs(text).filter { TextLanguage.worthAsking(text.substring(it)) }
        if (asked.isEmpty()) {
            onResult(emptyList())
            return
        }
        val answers = arrayOfNulls<List<LanguageGuess>>(asked.size)
        var left = asked.size
        var reported = false
        fun answered(index: Int, guesses: List<LanguageGuess>) {
            val all = synchronized(answers) {
                if (answers[index] != null) return
                answers[index] = guesses
                left--
                if (left > 0 || reported) return
                reported = true
                answers.map { it!! }
            }
            val language = appLanguage()
            onResult(
                asked.indices.mapNotNull { i ->
                    val range = asked[i]
                    TextLanguage.choose(text.substring(range), all[i], language)
                        ?.let { LanguageSpan(range.first, range.last + 1, it) }
                },
            )
        }
        asked.forEachIndexed { index, range ->
            val paragraph = text.substring(range)
            val cached = synchronized(known) { known[paragraph] }
            if (cached != null) {
                answered(index, cached)
            } else {
                identifier.identify(paragraph) { guesses ->
                    // Not a failure: the next time may do better.
                    if (guesses.isNotEmpty()) synchronized(known) { known[paragraph] = guesses }
                    answered(index, guesses)
                }
            }
        }
    }

    private companion object {
        const val KEPT = 200
    }
}
