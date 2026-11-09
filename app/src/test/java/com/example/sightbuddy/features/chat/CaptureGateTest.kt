package com.example.sightbuddy.features.chat

import com.example.sightbuddy.core.ModeNames
import com.example.sightbuddy.features.chat.CaptureGate.Press
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Capture pressed several times fast in Image chat said "Could not reach
 * OpenAI": each press started again and cancelled the request on its way.
 */
class CaptureGateTest {

    @Test
    fun `image chat ignores Capture while a picture or answer is on its way`() {
        assertEquals(Press.IGNORED, CaptureGate.press(ModeNames.IMAGE_CHAT, imageChatBusy = true, textGuiding = false))
    }

    @Test
    fun `image chat takes a new picture when nothing is on its way`() {
        assertEquals(Press.NEW_PICTURE, CaptureGate.press(ModeNames.IMAGE_CHAT, imageChatBusy = false, textGuiding = false))
    }

    @Test
    fun `five presses fast in image chat take one picture`() {
        var pictureAsked = false
        var pictures = 0
        repeat(5) {
            val busy = CaptureGate.imageChatBusy(
                pictureAsked = pictureAsked,
                pictureInFlight = false,
                answering = false,
                questionWaiting = false,
            )
            if (CaptureGate.press(ModeNames.IMAGE_CHAT, imageChatBusy = busy, textGuiding = false) == Press.NEW_PICTURE) {
                pictures++
                pictureAsked = true
            }
        }
        assertEquals(1, pictures)
    }

    @Test
    fun `capture while find page guides takes the picture at once`() {
        assertEquals(Press.TAKE_NOW, CaptureGate.press(ModeNames.TEXT_CHAT, imageChatBusy = false, textGuiding = true))
        // Image chat's state has no say in Text chat.
        assertEquals(Press.TAKE_NOW, CaptureGate.press(ModeNames.TEXT_CHAT, imageChatBusy = true, textGuiding = true))
    }

    @Test
    fun `capture in text chat takes a new picture at once`() {
        assertEquals(Press.NEW_PICTURE, CaptureGate.press(ModeNames.TEXT_CHAT, imageChatBusy = false, textGuiding = false))
    }

    @Test
    fun `a second capture press within two seconds is the same press`() {
        assertFalse(CaptureGate.debounced(nowMs = 10_000L, lastPressMs = null))
        assertTrue(CaptureGate.debounced(nowMs = 10_000L + CaptureGate.CAPTURE_DEBOUNCE_MS - 1, lastPressMs = 10_000L))
        assertFalse(CaptureGate.debounced(nowMs = 10_000L + CaptureGate.CAPTURE_DEBOUNCE_MS, lastPressMs = 10_000L))
        assertEquals(2_000L, CaptureGate.CAPTURE_DEBOUNCE_MS)
    }

    @Test
    fun `capture pressed again while text chat's picture is on its way is ignored`() {
        // The phone, 2026-10-31: the photo's shutter comes about two seconds after
        // the press; pressed again meanwhile, a second picture followed the first.
        assertEquals(
            Press.IGNORED,
            CaptureGate.press(ModeNames.TEXT_CHAT, imageChatBusy = false, textGuiding = false, textPictureComing = true),
        )
        // Not in Image chat, whose own busy state decides.
        assertEquals(
            Press.NEW_PICTURE,
            CaptureGate.press(ModeNames.IMAGE_CHAT, imageChatBusy = false, textGuiding = false, textPictureComing = true),
        )
    }

    @Test
    fun `find page starts guiding, and is ignored while it guides`() {
        assertEquals(Press.NEW_PICTURE, CaptureGate.findPage(guiding = false))
        assertEquals(Press.IGNORED, CaptureGate.findPage(guiding = true))
    }

    @Test
    fun `any picture or answer on its way makes image chat busy`() {
        fun busy(asked: Boolean = false, inFlight: Boolean = false, answering: Boolean = false, waiting: Boolean = false) =
            CaptureGate.imageChatBusy(asked, inFlight, answering, waiting)
        assertFalse(busy())
        assertTrue(busy(asked = true))
        assertTrue(busy(inFlight = true))
        assertTrue(busy(answering = true))
        assertTrue(busy(waiting = true))
    }

    @Test
    fun `a Text chat question while the page is still being read is about that page`() {
        // Captured, the summary still being written: no new picture.
        assertFalse(CaptureGate.textNeedsPicture(scanned = false, readingPage = true))
        assertFalse(CaptureGate.textNeedsPicture(scanned = true, readingPage = false))
        // Nothing captured and nothing on its way: a picture first.
        assertTrue(CaptureGate.textNeedsPicture(scanned = false, readingPage = false))
    }
}
