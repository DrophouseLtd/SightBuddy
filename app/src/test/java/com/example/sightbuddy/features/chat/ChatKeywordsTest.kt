package com.example.sightbuddy.features.chat

import com.example.sightbuddy.features.chat.ChatKeywords.Keyword
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Issue #13: the two Text chat keywords the app handles itself. Only the
 * keyword as the whole message counts; anything more is the AI's.
 */
class ChatKeywordsTest {

    @Test
    fun `read original, in English and Finnish`() {
        listOf("read original", "read the original", "lue alkuperäinen").forEach {
            assertEquals(it, Keyword.READ_ORIGINAL, ChatKeywords.match(it))
        }
    }

    @Test
    fun `read from image is not a keyword, withdrawn from Text chat, and Image chat's AI chooses it`() {
        // Decided 2026-10-24, after the phone round.
        listOf("read from image", "read from the image", "lue kuvasta", "Read from image.").forEach {
            assertNull(it, ChatKeywords.match(it))
        }
    }

    @Test
    fun `case, spacing and the punctuation speech recognition adds do not matter`() {
        // Whisper writes "Read original." with a capital and a full stop.
        listOf("Read original.", "  READ   ORIGINAL  ", "read original!", "\"Read original\"", "Lue alkuperäinen.")
            .forEach { assertEquals(it, Keyword.READ_ORIGINAL, ChatKeywords.match(it)) }
    }

    @Test
    fun `a keyword inside a longer message is the AI's`() {
        listOf(
            "can you read the original",
            "read original text please",
            "read the original menu",
            "read the original in Finnish",
            "please read from image",
            "read from image and translate it",
            "lue alkuperäinen teksti",
            "voitko lukea kuvasta",
        ).forEach { assertNull(it, ChatKeywords.match(it)) }
    }

    @Test
    fun `other messages, and parts of a keyword, are the AI's`() {
        listOf("", "   ", ".", "read", "original", "read it", "read in full", "lue", "kuvasta", "read image", "read originals")
            .forEach { assertNull(it, ChatKeywords.match(it)) }
    }
}
