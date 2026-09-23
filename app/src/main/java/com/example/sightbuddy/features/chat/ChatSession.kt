package com.example.sightbuddy.features.chat

import androidx.compose.runtime.mutableStateListOf
import com.example.sightbuddy.ui.screens.TranscriptEntry

/**
 * The conversation on screen, for whichever feature is open. Three voices, and
 * every line goes through this one place:
 *
 * - [user]: what the user asked, spoken or typed.
 * - [model]: what the AI answered, on the phone or through OpenAI.
 * - [system]: Sight Buddy's own words — "Looking for text", "No text was
 *   detected", "Sorry, could you please repeat". Shown like any other message
 *   and kept until the feature is left, but never saved: [forSaving] leaves
 *   them out.
 * - [scanned]: a page the app read, which is saved.
 *
 * It is not built from the model's own history any more. That history is what
 * the AI is told; this is what the user sees, and the two are not the same: the
 * app says things the AI never hears, and asks the AI things the user never
 * said.
 */
class ChatSession {

    private val entries = mutableStateListOf<TranscriptEntry>()

    /** Oldest first, as they are shown. */
    val messages: List<TranscriptEntry> get() = entries

    fun user(text: String) = add(TranscriptEntry.Kind.QUESTION, text)

    fun model(text: String) = add(TranscriptEntry.Kind.ANSWER, text)

    /** A page the app read out, kept as part of the conversation. */
    fun scanned(text: String) = add(TranscriptEntry.Kind.SCANNED_TEXT, text)

    /**
     * Sight Buddy's own words. Said as often as it needs saying, written once:
     * the aim guidance repeats itself while the camera hunts for a page.
     */
    fun system(text: String) {
        val last = entries.lastOrNull()
        if (last?.kind == TranscriptEntry.Kind.SYSTEM && last.text == text.trim()) return
        add(TranscriptEntry.Kind.SYSTEM, text)
    }

    /** What a saved chat keeps: the user, the AI, and any page read. */
    fun forSaving(): List<TranscriptEntry> =
        entries.filterNot { it.kind == TranscriptEntry.Kind.SYSTEM }

    fun isEmpty(): Boolean = entries.isEmpty()

    /** A new picture, another feature, or a session left too long: start over. */
    fun clear() = entries.clear()

    private fun add(kind: TranscriptEntry.Kind, text: String) {
        val trimmed = text.trim()
        if (trimmed.isNotEmpty()) entries.add(TranscriptEntry(kind, trimmed))
    }
}
