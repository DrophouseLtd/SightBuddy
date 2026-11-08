package com.example.sightbuddy.core

/**
 * Which of the app's words to tag with its language for the screen reader
 * (issue #28). TalkBack picks its voice from a text's locale and otherwise
 * speaks in the phone's language, so with the phone in English and the app in
 * Finnish it read every Finnish button with an English voice. See
 * `ui/ScreenReaderLocale.kt`, which applies this to every node.
 */
object ScreenReaderLanguage {

    /** Tagged only when the app's language differs from the phone's. */
    fun wanted(appLanguage: String, phoneLanguage: String): Boolean = appLanguage != phoneLanguage

    /**
     * The stretches of a text [length] long not [tagged] with a language of
     * their own already (a chat message's Arabic, say, issue #26): those get
     * the app's language. In order, none empty.
     */
    fun untagged(length: Int, tagged: List<IntRange>): List<IntRange> {
        val out = mutableListOf<IntRange>()
        var from = 0
        for (range in tagged.sortedBy { it.first }) {
            if (range.first > from) out += from until range.first
            from = maxOf(from, range.last + 1)
        }
        if (from < length) out += from until length
        return out
    }
}
