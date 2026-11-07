package com.example.sightbuddy.features.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #24, the phone on 2026-10-24: the waiting tick played on over a
 * streamed answer being spoken, as if it were still being waited for.
 */
class ChatWorkingTest {

    private fun working(
        inChat: Boolean = true,
        pictureInFlight: Boolean = false,
        questionWaiting: Boolean = false,
        processing: Boolean = false,
        answerSpeaking: Boolean = false,
        aiming: Boolean = false,
        takingPicture: Boolean = false,
    ) = ChatWorking.working(inChat, pictureInFlight, questionWaiting, processing, answerSpeaking, aiming, takingPicture)

    @Test
    fun `a picture, a question or an answer on its way is working`() {
        assertFalse(working())
        assertTrue(working(pictureInFlight = true))
        assertTrue(working(questionWaiting = true))
        assertTrue(working(processing = true))
        assertFalse(working(inChat = false, processing = true))
    }

    @Test
    fun `once the answer is being spoken it is not waited for any more`() {
        // The question stays pending and the model keeps writing until the end.
        assertFalse(working(questionWaiting = true, processing = true, answerSpeaking = true))
    }

    @Test
    fun `a question waiting while the user aims is not worked on yet`() {
        // The phone, 2026-10-27: a question asked before the scan waited through
        // the guided capture, and the tick played while the user was aiming.
        assertFalse(working(questionWaiting = true, aiming = true))
        // Once the picture is taken, the wait is the AI's.
        assertTrue(working(questionWaiting = true, processing = true))
    }

    @Test
    fun `nothing is worked on while the picture is still being taken`() {
        // The phone, 2026-10-31: the photo takes over a second, and the tick
        // started before the shutter.
        assertFalse(working(questionWaiting = true, takingPicture = true))
        assertTrue(working(questionWaiting = true))
    }

    @Test
    fun `no tick while anything is being said, whatever is still on its way`() {
        // The phone, 2026-10-27: the tick still played during speech. Speech
        // that is not the answer's own (the line before a photo, an answer
        // still being said, by the voice or by TalkBack, when the next question
        // is already on its way) was not covered by the answer's first sentence.
        assertTrue(ChatWorking.tickBeat(working = true, speaking = false))
        assertFalse(ChatWorking.tickBeat(working = true, speaking = true))
        assertFalse(ChatWorking.tickBeat(working = false, speaking = false))
    }

    @Test
    fun `Capture works again once the answer is being spoken`() {
        // Busy while it is worked out; once spoken, Capture starts a new chat
        // (cancelling the rest), as it does after any answer.
        assertTrue(CaptureGate.imageChatBusy(false, false, answering = true, questionWaiting = true))
        assertFalse(
            CaptureGate.imageChatBusy(false, false, answering = true, questionWaiting = true, answerSpeaking = true),
        )
        // A picture asked for or on its way is still busy.
        assertTrue(CaptureGate.imageChatBusy(true, false, answering = false, questionWaiting = false, answerSpeaking = true))
    }
}
