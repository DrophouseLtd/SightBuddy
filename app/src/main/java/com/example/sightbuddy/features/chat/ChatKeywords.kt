package com.example.sightbuddy.features.chat

import java.util.Locale

/**
 * Text chat's keyword (issue #13), which the app handles itself rather than
 * leaving the choice to the AI: "read original", the captured scan read out
 * exactly as captured, with no AI. ("Read from image" was one too, in Text
 * chat, until the phone round of 2026-10-24: withdrawn.)
 *
 * Only the keyword as the whole message counts, in English or Finnish
 * whatever the app language; case, spacing and the punctuation speech
 * recognition adds ("Read original.") do not matter. Anything said with it
 * ("read the original menu") is a question for the AI, which reads the
 * text out itself when asked (see [ChatPrompts]).
 */
object ChatKeywords {
    enum class Keyword { READ_ORIGINAL }

    private val KEYWORDS = mapOf(
        "read original" to Keyword.READ_ORIGINAL,
        "read the original" to Keyword.READ_ORIGINAL,
        "lue alkuperäinen" to Keyword.READ_ORIGINAL,
    )

    private val SPACES = Regex("\\s+")

    fun match(message: String): Keyword? {
        val words = message
            .lowercase(Locale.ROOT)
            .trim { !it.isLetterOrDigit() }
            .replace(SPACES, " ")
        return KEYWORDS[words]
    }
}
