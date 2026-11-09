package com.example.sightbuddy.core

import android.view.KeyEvent
import com.example.sightbuddy.core.VolumeKeys.Action.ASK
import com.example.sightbuddy.core.VolumeKeys.Action.CAPTURE
import com.example.sightbuddy.core.VolumeKeys.Key.DOWN
import com.example.sightbuddy.core.VolumeKeys.Key.UP
import com.example.sightbuddy.core.VolumeKeys.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Item 10 (issue #9): with the setting on, volume up is Capture and volume down
 * is Ask, on release, one key at a time. Both keys held is Android's
 * accessibility shortcut and never does anything here. With nothing to do (the
 * setting off, Settings or a pop-up open, a feature without the button) every
 * key event is passed on, so the keys behave exactly as before.
 */
class VolumeKeysTest {

    private val keys = VolumeKeys()

    private fun down(key: VolumeKeys.Key, action: VolumeKeys.Action?, repeat: Boolean = false) =
        keys.onKey(key, pressed = true, repeat = repeat, canceled = false, action = action)

    private fun up(key: VolumeKeys.Key, action: VolumeKeys.Action?, canceled: Boolean = false) =
        keys.onKey(key, pressed = false, repeat = false, canceled = canceled, action = action)

    @Test
    fun volumeUpIsCaptureOnRelease() {
        assertEquals(Outcome.Swallow, down(UP, CAPTURE))
        assertEquals(Outcome.Act(CAPTURE), up(UP, CAPTURE))
    }

    @Test
    fun volumeDownIsAskOnRelease() {
        assertEquals(Outcome.Swallow, down(DOWN, ASK))
        assertEquals(Outcome.Act(ASK), up(DOWN, ASK))
    }

    @Test
    fun withNothingToDoTheKeysStayAndroids() {
        assertEquals(Outcome.Pass, down(UP, null))
        assertEquals(Outcome.Pass, up(UP, null))
        assertEquals(Outcome.Pass, down(DOWN, null))
        assertEquals(Outcome.Pass, up(DOWN, null))
    }

    @Test
    fun bothKeysHeldDoNothing() {
        val outcomes = mutableListOf<Outcome>()
        outcomes += down(UP, CAPTURE)
        // The second key is Android's from the start.
        assertEquals(Outcome.Pass, down(DOWN, ASK))
        // Held for the accessibility shortcut.
        repeat(10) {
            outcomes += down(UP, CAPTURE, repeat = true)
            outcomes += down(DOWN, ASK, repeat = true)
        }
        outcomes += up(UP, CAPTURE)
        outcomes += up(DOWN, ASK)
        assertTrue(outcomes.toString(), outcomes.none { it is Outcome.Act })
    }

    @Test
    fun bothKeysDoNothingWhicheverIsLetGoFirst() {
        val outcomes = listOf(
            down(DOWN, ASK),
            down(UP, CAPTURE),
            up(DOWN, ASK),
            up(UP, CAPTURE),
        )
        assertTrue(outcomes.toString(), outcomes.none { it is Outcome.Act })
    }

    @Test
    fun oneKeyWorksAgainOnceBothAreUp() {
        down(UP, CAPTURE)
        down(DOWN, ASK)
        up(UP, CAPTURE)
        up(DOWN, ASK)
        assertEquals(Outcome.Swallow, down(DOWN, ASK))
        assertEquals(Outcome.Act(ASK), up(DOWN, ASK))
    }

    @Test
    fun aHeldKeyActsOnceOnRelease() {
        assertEquals(Outcome.Swallow, down(UP, CAPTURE))
        repeat(10) { assertEquals(Outcome.Swallow, down(UP, CAPTURE, repeat = true)) }
        assertEquals(Outcome.Act(CAPTURE), up(UP, CAPTURE))
    }

    @Test
    fun aCanceledPressDoesNothing() {
        assertEquals(Outcome.Swallow, down(UP, CAPTURE))
        assertEquals(Outcome.Swallow, up(UP, CAPTURE, canceled = true))
    }

    @Test
    fun aPopUpOpenedDuringThePressStopsIt() {
        assertEquals(Outcome.Swallow, down(UP, CAPTURE))
        assertEquals(Outcome.Swallow, up(UP, null))
    }

    @Test
    fun aPressThatBeganWithNothingToDoStaysAndroids() {
        assertEquals(Outcome.Pass, down(UP, null))
        assertEquals(Outcome.Pass, down(UP, CAPTURE, repeat = true))
        assertEquals(Outcome.Pass, up(UP, CAPTURE))
    }

    @Test
    fun resetForgetsAPressWhoseReleaseNeverCame() {
        down(UP, CAPTURE)
        keys.reset()
        assertEquals(Outcome.Swallow, down(DOWN, ASK))
        assertEquals(Outcome.Act(ASK), up(DOWN, ASK))
    }

    @Test
    fun upIsCaptureAndDownIsAskOnlyWhereThatButtonIs() {
        fun action(key: VolumeKeys.Key, enabled: Boolean = true, inFront: Boolean = true,
                   hasCapture: Boolean = true, hasAsk: Boolean = true) =
            VolumeKeys.actionFor(key, enabled, inFront, hasCapture, hasAsk)

        // Image chat, or Text chat with the AI.
        assertEquals(CAPTURE, action(UP))
        assertEquals(ASK, action(DOWN))
        // Find objects has Ask but no Capture.
        assertNull(action(UP, hasCapture = false))
        assertEquals(ASK, action(DOWN, hasCapture = false))
        // Text chat without the AI has Capture but no Ask.
        assertEquals(CAPTURE, action(UP, hasAsk = false))
        assertNull(action(DOWN, hasAsk = false))
        // The setting off.
        assertNull(action(UP, enabled = false))
        assertNull(action(DOWN, enabled = false))
        // A pop-up, Settings, Help, onboarding or the background.
        assertNull(action(UP, inFront = false))
        assertNull(action(DOWN, inFront = false))
    }

    private fun shown(
        feature: String?,
        aiChat: Boolean = true,
        keyboardUp: Boolean = false,
        draftTyped: Boolean = false,
    ) = VolumeKeys.buttonsOnScreen(feature, aiChat, keyboardUp, draftTyped)

    @Test
    fun theKeysStandForTheFeaturesOwnButtons() {
        assertEquals(VolumeKeys.Buttons(capture = true, ask = true), shown(ModeNames.IMAGE_CHAT))
        assertEquals(VolumeKeys.Buttons(capture = true, ask = true), shown(ModeNames.TEXT_CHAT))
        // Text chat without the AI has no Ask.
        assertEquals(VolumeKeys.Buttons(capture = true, ask = false), shown(ModeNames.TEXT_CHAT, aiChat = false))
        assertEquals(VolumeKeys.Buttons(capture = false, ask = true), shown(ModeNames.FIND_OBJECTS))
        listOf(ModeNames.DISCOVER_OBJECTS, ModeNames.SCAN_LIGHT, ModeNames.SCAN_COLOUR, null).forEach {
            assertEquals("$it", VolumeKeys.Buttons(capture = false, ask = false), shown(it))
        }
    }

    @Test
    fun nothingWhileTheKeyboardIsUp() {
        // The keyboard hides the buttons, so the keys stay the volume.
        listOf(ModeNames.IMAGE_CHAT, ModeNames.TEXT_CHAT, ModeNames.FIND_OBJECTS).forEach {
            assertEquals(it, VolumeKeys.Buttons(capture = false, ask = false), shown(it, keyboardUp = true))
            assertEquals(it, VolumeKeys.Buttons(capture = false, ask = false), shown(it, keyboardUp = true, draftTyped = true))
        }
    }

    @Test
    fun noAskOnceSomethingIsTyped() {
        // The keyboard closed with a draft left: that button is Send now. Capture stays.
        assertEquals(VolumeKeys.Buttons(capture = true, ask = false), shown(ModeNames.IMAGE_CHAT, draftTyped = true))
        assertEquals(VolumeKeys.Buttons(capture = false, ask = false), shown(ModeNames.FIND_OBJECTS, draftTyped = true))
    }

    @Test
    fun onlyTheTwoVolumeKeys() {
        assertEquals(UP, VolumeKeys.keyOf(KeyEvent.KEYCODE_VOLUME_UP))
        assertEquals(DOWN, VolumeKeys.keyOf(KeyEvent.KEYCODE_VOLUME_DOWN))
        assertNull(VolumeKeys.keyOf(KeyEvent.KEYCODE_VOLUME_MUTE))
        assertNull(VolumeKeys.keyOf(KeyEvent.KEYCODE_BACK))
    }
}
