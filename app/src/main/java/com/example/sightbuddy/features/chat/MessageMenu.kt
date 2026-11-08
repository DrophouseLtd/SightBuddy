package com.example.sightbuddy.features.chat

import com.example.sightbuddy.ui.screens.TranscriptEntry

/**
 * The pop-up menu on a chat message (issue #21). While a screen reader runs, a
 * press and hold on a message (TalkBack: double-tap and hold) opens it, the
 * same kind of menu as a memory's ⋮. Without one, a long press still selects
 * the text, as before. No custom accessibility actions: TalkBack's own actions
 * and reading controls on a message stay as they are.
 */
object MessageMenu {

    enum class Item {
        /** That message's text to the clipboard. */
        COPY,

        /** That message's text to Android's share sheet, not the whole chat. */
        SHARE,

        /** The chat to Memories: the More row's Save chat. */
        SAVE_CHAT,
    }

    private val ALL = listOf(Item.COPY, Item.SHARE, Item.SAVE_CHAT)

    /**
     * What the menu holds for a message of [kind], in order; empty where a
     * message has none. The user's questions, the AI's answers and a scanned
     * page have it; the app's own notes do not, as they are not saved either.
     */
    fun items(kind: TranscriptEntry.Kind, screenReaderOn: Boolean): List<Item> =
        if (screenReaderOn && kind != TranscriptEntry.Kind.SYSTEM) ALL else emptyList()

    /** What Copy and Share send: the message's text as shown, without its label or the rest of the chat. */
    fun textOf(entry: TranscriptEntry): String = entry.text

    /**
     * Whether a copy is confirmed by saying "Copied". Android 13 (API 33) and
     * later show their own notice when an app copies; earlier ones say nothing.
     */
    fun saysCopied(sdkInt: Int): Boolean = sdkInt < ANDROID_13

    /** What the app says after Copy. */
    enum class Notice { COPIED, COPY_FAILED }

    /** What the app says after Copy: "Copied" only where Android does not, a failure always; null for nothing. */
    fun copyNotice(copied: Boolean, sdkInt: Int): Notice? = when {
        !copied -> Notice.COPY_FAILED
        saysCopied(sdkInt) -> Notice.COPIED
        else -> null
    }

    /** When TalkBack is sent back to the message after its menu closes. */
    enum class FocusBack {
        /** Once the pop-up has gone. */
        NOW,

        /** Once what the app says ([spoken]) has been said, so TalkBack does not talk over it. */
        AFTER_SPEECH,

        /** Not at all. */
        NEVER,
    }

    /**
     * How a menu's ending hands TalkBack back. [choice] is null for Back or a
     * touch outside; [spoken] is whether the app says something after it.
     * Save chat says where it saved and writes a note that scrolls the chat,
     * and Share's sheet is on top: focus is left alone after both.
     */
    fun focusBack(choice: Item?, spoken: Boolean = false): FocusBack = when (choice) {
        null -> FocusBack.NOW
        Item.COPY -> if (spoken) FocusBack.AFTER_SPEECH else FocusBack.NOW
        Item.SHARE, Item.SAVE_CHAT -> FocusBack.NEVER
    }

    private const val ANDROID_13 = 33
}
