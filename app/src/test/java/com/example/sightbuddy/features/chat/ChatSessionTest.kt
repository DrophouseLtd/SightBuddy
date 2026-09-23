package com.example.sightbuddy.features.chat

import com.example.sightbuddy.ui.screens.TranscriptEntry.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rule the whole chat rests on: the app's own words show, and are not saved. */
class ChatSessionTest {

    @Test
    fun everyVoiceIsShownInOrder() {
        val chat = ChatSession()
        chat.system("Looking for text.")
        chat.scanned("Dear Sir or Madam, ...")
        chat.user("What is the date on it?")
        chat.model("The 22nd of September.")

        assertEquals(
            listOf(Kind.SYSTEM, Kind.SCANNED_TEXT, Kind.QUESTION, Kind.ANSWER),
            chat.messages.map { it.kind },
        )
    }

    @Test
    fun savingKeepsTheUserTheModelAndThePage() {
        val chat = ChatSession()
        chat.system("Let me take a picture.")
        chat.user("What is this?")
        chat.model("A tin of tomatoes.")
        chat.system("Sorry, could you please repeat.")
        chat.scanned("PEELED TOMATOES")

        assertEquals(
            listOf(Kind.QUESTION, Kind.ANSWER, Kind.SCANNED_TEXT),
            chat.forSaving().map { it.kind },
        )
    }

    /** The aim guidance says the same line while the camera hunts for a page. */
    @Test
    fun theSameNoticeTwiceRunningIsWrittenOnce() {
        val chat = ChatSession()
        chat.system("No text visible.")
        chat.system("No text visible.")
        assertEquals(1, chat.messages.size)

        // Said again after something else: worth showing again.
        chat.user("Anything there?")
        chat.system("No text visible.")
        assertEquals(3, chat.messages.size)
    }

    @Test
    fun blankLinesAreNotMessages() {
        val chat = ChatSession()
        chat.user("")
        chat.model("   ")
        chat.system("\n")
        assertTrue(chat.isEmpty())
    }

    @Test
    fun clearingStartsTheNextSessionEmpty() {
        val chat = ChatSession()
        chat.user("What is this?")
        chat.model("A tin.")
        chat.clear()

        assertTrue(chat.isEmpty())
        assertTrue(chat.forSaving().isEmpty())
        assertFalse(chat.messages.isNotEmpty())
    }
}
