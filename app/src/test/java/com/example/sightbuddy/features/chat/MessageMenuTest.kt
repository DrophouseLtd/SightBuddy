package com.example.sightbuddy.features.chat

import com.example.sightbuddy.features.chat.MessageMenu.FocusBack
import com.example.sightbuddy.features.chat.MessageMenu.Item
import com.example.sightbuddy.features.chat.MessageMenu.Notice
import com.example.sightbuddy.ui.screens.TranscriptEntry
import com.example.sightbuddy.ui.screens.TranscriptEntry.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Issue #21: press and hold on a chat message, with a screen reader on, opens Copy, Share, Save chat. */
class MessageMenuTest {

    /** The user's questions, the AI's answers and the scanned page: Copy, Share, Save chat, in that order. */
    @Test
    fun theConversationsMessagesHaveTheMenuInOrder() {
        for (kind in listOf(Kind.QUESTION, Kind.ANSWER, Kind.SCANNED_TEXT)) {
            assertEquals(
                "$kind",
                listOf(Item.COPY, Item.SHARE, Item.SAVE_CHAT),
                MessageMenu.items(kind, screenReaderOn = true),
            )
        }
    }

    /** The app's own notes are not the conversation: no menu. */
    @Test
    fun theAppsOwnNotesHaveNoMenu() {
        assertTrue(MessageMenu.items(Kind.SYSTEM, screenReaderOn = true).isEmpty())
    }

    /** Without a screen reader nothing changes: a long press still selects text. */
    @Test
    fun withoutAScreenReaderNoMessageHasAMenu() {
        for (kind in Kind.entries) {
            assertTrue("$kind", MessageMenu.items(kind, screenReaderOn = false).isEmpty())
        }
    }

    /** Copy and Share take that one message's text, as shown: not its label, not the chat. */
    @Test
    fun copyAndShareTakeThatMessagesTextAlone() {
        val chat = ChatSession()
        chat.system("Let me take a picture.")
        chat.user("What is this?")
        chat.model("A tin of tomatoes.\nBest before May.")
        chat.scanned("PEELED TOMATOES\n400 g")

        val answer = chat.messages[2]
        assertEquals("A tin of tomatoes.\nBest before May.", MessageMenu.textOf(answer))
        assertEquals("What is this?", MessageMenu.textOf(chat.messages[1]))
        assertEquals("PEELED TOMATOES\n400 g", MessageMenu.textOf(chat.messages[3]))
        assertNotEquals(chat.forSharing { it.name }, MessageMenu.textOf(answer))
    }

    /** The question shown before its answer arrives is the user's words like any other. */
    @Test
    fun theQuestionWaitingForItsAnswerCopiesAsTyped() {
        val pending = TranscriptEntry(Kind.QUESTION, "Is it vegan?")
        assertEquals(listOf(Item.COPY, Item.SHARE, Item.SAVE_CHAT), MessageMenu.items(pending.kind, true))
        assertEquals("Is it vegan?", MessageMenu.textOf(pending))
    }

    /** Android 13 and later show their own notice on a copy; only earlier ones need "Copied". */
    @Test
    fun copiedIsSaidOnlyWhereAndroidDoesNotSayIt() {
        assertTrue(MessageMenu.saysCopied(30))
        assertTrue(MessageMenu.saysCopied(31))
        assertTrue(MessageMenu.saysCopied(32))
        assertFalse(MessageMenu.saysCopied(33))
        assertFalse(MessageMenu.saysCopied(36))
    }

    /** After Copy: "Copied" where Android says nothing, and a failure always, whatever the version. */
    @Test
    fun copySaysCopiedOnlyBelowAndroid13AndAFailureAlways() {
        assertEquals(Notice.COPIED, MessageMenu.copyNotice(copied = true, sdkInt = 31))
        assertEquals(Notice.COPIED, MessageMenu.copyNotice(copied = true, sdkInt = 32))
        assertNull(MessageMenu.copyNotice(copied = true, sdkInt = 33))
        assertNull(MessageMenu.copyNotice(copied = true, sdkInt = 36))
        assertEquals(Notice.COPY_FAILED, MessageMenu.copyNotice(copied = false, sdkInt = 32))
        assertEquals(Notice.COPY_FAILED, MessageMenu.copyNotice(copied = false, sdkInt = 36))
    }

    /** Back, or a touch outside the menu: TalkBack goes back to the message. */
    @Test
    fun closingWithoutAChoiceGoesBackToTheMessage() {
        assertEquals(FocusBack.NOW, MessageMenu.focusBack(choice = null))
    }

    /** Copy: back to the message, but not over the app saying "Copied" or that it failed. */
    @Test
    fun copyGoesBackToTheMessageOnceNothingIsLeftToHear() {
        fun afterCopy(sdkInt: Int) =
            MessageMenu.focusBack(Item.COPY, spoken = MessageMenu.copyNotice(copied = true, sdkInt = sdkInt) != null)
        assertEquals(FocusBack.NOW, afterCopy(33))
        assertEquals(FocusBack.NOW, afterCopy(36))
        assertEquals(FocusBack.AFTER_SPEECH, afterCopy(32))
        assertEquals(FocusBack.AFTER_SPEECH, afterCopy(30))
        assertEquals(FocusBack.AFTER_SPEECH, MessageMenu.focusBack(Item.COPY, spoken = true))
    }

    /**
     * Save chat's confirmation is to be heard (and its note scrolls the chat), and
     * Share's sheet is on top: focus is left where it is.
     */
    @Test
    fun shareAndSaveChatLeaveTheFocusAlone() {
        assertEquals(FocusBack.NEVER, MessageMenu.focusBack(Item.SHARE))
        assertEquals(FocusBack.NEVER, MessageMenu.focusBack(Item.SAVE_CHAT))
        assertEquals(FocusBack.NEVER, MessageMenu.focusBack(Item.SAVE_CHAT, spoken = true))
    }
}
