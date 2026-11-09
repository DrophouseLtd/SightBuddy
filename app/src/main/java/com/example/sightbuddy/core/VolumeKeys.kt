package com.example.sightbuddy.core

import android.view.KeyEvent

/**
 * The volume keys as Capture and Ask, a setting off by default (the keys also
 * set TalkBack's volume). Volume up is Capture and volume down is Ask, each
 * only in a feature with that button, with the app in front and nothing over
 * it. A key acts when it is let go, one key at a time.
 *
 * Both keys held is Android's accessibility shortcut, which can switch TalkBack
 * off and on, so a press that ever had both keys down does nothing here, and
 * the second key is left to Android from the start. A key with nothing to do
 * when it goes down is Android's for that whole press: the volume changes as
 * it always has. MainActivity feeds every volume key event through [onKey].
 */
class VolumeKeys {
    enum class Key { UP, DOWN }
    enum class Action { CAPTURE, ASK }

    /** What to do with one key event. */
    sealed interface Outcome {
        /** Not ours: Android handles it as always. */
        data object Pass : Outcome

        /** Ours, with nothing to do (yet): kept from Android, so the volume stays. */
        data object Swallow : Outcome

        /** Ours: press that button. */
        data class Act(val action: Action) : Outcome
    }

    /** The keys down now. */
    private val held = mutableSetOf<Key>()

    /** The keys whose press is ours. */
    private val taken = mutableSetOf<Key>()

    /** Both keys have been down together since the last time none was. */
    private var both = false

    /**
     * One key event. [action] is what the key would do now (see [actionFor]),
     * null when nothing; it is asked again on release, so a pop-up opened
     * during the press stops it.
     */
    fun onKey(key: Key, pressed: Boolean, repeat: Boolean, canceled: Boolean, action: Action?): Outcome {
        if (pressed) {
            // A held key repeats: its press stays whoever's it was.
            if (repeat) return if (key in taken) Outcome.Swallow else Outcome.Pass
            // A new press: anything left of this key's last one is over.
            held -= key
            taken -= key
            if (held.isEmpty()) both = false
            held += key
            if (held.size > 1) both = true
            if (both || action == null) return Outcome.Pass
            taken += key
            return Outcome.Swallow
        }
        held -= key
        val ours = taken.remove(key)
        val together = both
        if (held.isEmpty()) both = false
        return when {
            !ours -> Outcome.Pass
            together || canceled || action == null -> Outcome.Swallow
            else -> Outcome.Act(action)
        }
    }

    /** Forgets every press, for when the window loses the keys mid-press. */
    fun reset() {
        held.clear()
        taken.clear()
        both = false
    }

    /** Which of the two buttons the keys stand for are on screen. */
    data class Buttons(val capture: Boolean, val ask: Boolean)

    companion object {
        /**
         * Capture and Ask as the feature shows them now, as HomeScreen lays
         * them out: Capture in the two chats, Ask in the chats with the AI and
         * in Find objects. The keyboard hides both, and once something is typed
         * the Ask button is Send, so the key is no Ask then.
         */
        fun buttonsOnScreen(feature: String?, aiChat: Boolean, keyboardUp: Boolean, draftTyped: Boolean): Buttons {
            if (keyboardUp) return Buttons(capture = false, ask = false)
            val chat = feature == ModeNames.IMAGE_CHAT || feature == ModeNames.TEXT_CHAT
            val askFeature = (chat && aiChat) || feature == ModeNames.FIND_OBJECTS
            return Buttons(capture = chat, ask = askFeature && !draftTyped)
        }

        fun keyOf(keyCode: Int): Key? = when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> Key.UP
            KeyEvent.KEYCODE_VOLUME_DOWN -> Key.DOWN
            else -> null
        }

        /**
         * What [key] does now: up is Capture where there is a Capture button,
         * down is Ask where there is an Ask button; null with the setting off or
         * anything over the feature (onboarding, Settings, Help, a pop-up), and
         * with the app in the background.
         */
        fun actionFor(key: Key, enabled: Boolean, inFront: Boolean, hasCapture: Boolean, hasAsk: Boolean): Action? =
            when {
                !enabled || !inFront -> null
                key == Key.UP -> Action.CAPTURE.takeIf { hasCapture }
                else -> Action.ASK.takeIf { hasAsk }
            }
    }
}
