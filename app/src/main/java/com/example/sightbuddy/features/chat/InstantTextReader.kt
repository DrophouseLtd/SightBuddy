package com.example.sightbuddy.features.chat

/**
 * Decides when Text chat's live text changes.
 *
 * The recogniser returns much the same text frame after frame, never quite
 * identical. Redrawing it each time would drop a selection and move TalkBack
 * off its place, so the live text is replaced only when the text is new: when
 * its words overlap what is shown by less than [sameAbove].
 */
class InstantTextReader(
    private val minChars: Int = 3,
    private val sameAbove: Float = 0.6f,
) {
    private var lastWords: Set<String> = emptySet()

    /** True when [text] is new enough to show; it then counts as shown. */
    fun shouldRead(text: String): Boolean {
        if (text.trim().length < minChars) return false
        val words = wordsOf(text)
        if (words.isEmpty() || overlap(words, lastWords) >= sameAbove) return false
        lastWords = words
        return true
    }

    fun reset() {
        lastWords = emptySet()
    }

    private fun wordsOf(text: String): Set<String> =
        text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }.toSet()

    private fun overlap(a: Set<String>, b: Set<String>): Float {
        if (a.isEmpty() || b.isEmpty()) return 0f
        return (a intersect b).size.toFloat() / (a union b).size
    }
}
