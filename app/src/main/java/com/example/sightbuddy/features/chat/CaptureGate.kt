package com.example.sightbuddy.features.chat

import com.example.sightbuddy.core.ModeNames

/**
 * What a press of Capture, or of Find page, does in Image chat and Text chat.
 *
 * Image chat ignores Capture while a picture or an answer is on its way. Each
 * press used to start a new session, which cancelled the request in flight, and
 * the cancelled request was said as "Could not reach OpenAI". In Text chat,
 * Capture takes the picture at once, and while Find page's guidance runs, it
 * takes it then and there. Find page pressed while it guides does nothing.
 */
object CaptureGate {

    /**
     * Whether a question in Text chat needs a new picture first. Not while the
     * page just captured is still being read or summarised: a question asked
     * then is about that page (the phone, 2026-10-24: a follow-up asked while
     * the summary was still being written started a new scan instead).
     */
    fun textNeedsPicture(scanned: Boolean, readingPage: Boolean): Boolean = !scanned && !readingPage

    enum class Press {
        /** A picture or an answer is on its way: nothing happens. */
        IGNORED,
        /** Find page's guidance is running: Capture takes the picture now, whole. */
        TAKE_NOW,
        /** A new picture, and with it a new session. */
        NEW_PICTURE,
    }

    /** Find page: a new guided picture, unless its guidance is running already. */
    fun findPage(guiding: Boolean): Press = if (guiding) Press.IGNORED else Press.NEW_PICTURE

    /**
     * Image chat has something on its way: a picture asked for and not taken
     * yet, a picture being described, an answer being worked out, or a
     * question waiting for its picture or its answer. An answer already being
     * spoken (issue #24) is no longer on its way, though the model is still
     * writing it: Capture starts a new chat then, as after any answer.
     */
    fun imageChatBusy(
        pictureAsked: Boolean,
        pictureInFlight: Boolean,
        answering: Boolean,
        questionWaiting: Boolean,
        answerSpeaking: Boolean = false,
    ): Boolean = pictureAsked || pictureInFlight || (!answerSpeaking && (answering || questionWaiting))

    /**
     * A Capture press within [CAPTURE_DEBOUNCE_MS] of the last is the same
     * press, in either chat and from the volume key: with nothing heard yet,
     * it was pressed again (the phone, 2026-10-31). Null: no press yet.
     */
    fun debounced(nowMs: Long, lastPressMs: Long?): Boolean =
        lastPressMs != null && nowMs - lastPressMs < CAPTURE_DEBOUNCE_MS

    const val CAPTURE_DEBOUNCE_MS = 2_000L

    /**
     * What Capture does. In Text chat, [textPictureComing] is a picture already
     * on its way (the photo stream being bound, the photo being taken): its
     * shutter comes about two seconds after the press, and a press meanwhile
     * took a second picture after the first (the phone, 2026-10-31).
     */
    fun press(
        mode: String?,
        imageChatBusy: Boolean,
        textGuiding: Boolean,
        textPictureComing: Boolean = false,
    ): Press = when (mode) {
        ModeNames.IMAGE_CHAT -> if (imageChatBusy) Press.IGNORED else Press.NEW_PICTURE
        ModeNames.TEXT_CHAT -> when {
            textGuiding -> Press.TAKE_NOW
            textPictureComing -> Press.IGNORED
            else -> Press.NEW_PICTURE
        }
        else -> Press.NEW_PICTURE
    }
}
