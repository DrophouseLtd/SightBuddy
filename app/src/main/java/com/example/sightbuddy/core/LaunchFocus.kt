package com.example.sightbuddy.core

/**
 * Each time the feature screen comes in front (launch, back from the
 * background, Settings or a pop-up closed), TalkBack starts on the feature
 * bar's selected tab, which reads the feature showing ("Image chat, 1 of 5"),
 * rather than on Help or wherever it last was. A swipe to another feature moves
 * nothing: TalkBack reads the new tab itself, wherever its cursor is.
 *
 * The app moves TalkBack's own cursor (accessibility focus) to the tab.
 * TalkBack places its first focus on a window by rules of its own (the first
 * element, an element on screen, or where it last was), and only when nothing
 * holds focus yet; its log on the phone showed all three. Whoever acts last
 * wins, so the move is repeated over the first second, and the tab also comes
 * first in TalkBack's order (`featureTabFirst`) so its first-element rule lands
 * there too. Input focus does nothing here: TalkBack follows it only to
 * editable nodes.
 */
class LaunchFocus {
    private var inFront = false
    private var pending = false

    /**
     * True when the feature screen has just come in front: at launch, back from
     * the background, or with Settings, Help, Memories or another pop-up closed.
     * False while it stays in front, so a swipe to another feature (TalkBack
     * reads the new tab itself) moves nothing.
     */
    fun take(featuresInFront: Boolean): Boolean {
        val arrived = featuresInFront && !inFront
        inFront = featuresInFront
        if (arrived) pending = true
        return arrived
    }

    /**
     * Whether the tab should take the focus now, as it comes on screen: once
     * for each arrival. The feature bar leaves the screen while the keyboard is
     * up and comes back with the hold still on; moving again then took
     * TalkBack to the tab in the middle of the chat.
     */
    fun request(): Boolean {
        if (!pending) return false
        pending = false
        return true
    }

    companion object {
        /**
         * The feature screen as TalkBack meets it: the app in front, past
         * onboarding, no Settings, a feature showing, and no pop-up over it. A
         * message's own menu and the Find objects picker do not count as pop-ups
         * here: closing the menu returns TalkBack to the message, and closing the
         * picker is followed by "Looking for ...", which the tab would talk over.
         */
        fun featureScreenInFront(
            appInForeground: Boolean,
            onboardingBlocking: Boolean,
            settingsOpen: Boolean,
            featureShowing: Boolean,
            screenPopupOpen: Boolean,
        ): Boolean = appInForeground && !onboardingBlocking && !settingsOpen && featureShowing &&
            !screenPopupOpen

        /**
         * When the tab takes TalkBack's focus, in ms after it is on screen. Taken
         * before TalkBack's own first focus, it is kept; TalkBack decides about
         * half a second after the window opens, and its "where it last was" may
         * still move away, so the move is repeated over the first 0.7 s. On a tab
         * that already holds the focus it does nothing. A user who touches or
         * swipes elsewhere within that time is taken back to the tab once; the
         * window is kept short for that reason.
         */
        val SCREEN_READER_FOCUS_ATTEMPTS_MS = listOf(0L, 150L, 400L, 700L)
    }
}
