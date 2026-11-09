package com.example.sightbuddy.core.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #26 (decided 2026-10-24): once only, the first time text in a
 * language the phone has no voice for is read, a pop-up says that a voice
 * can be added in the phone's text-to-speech settings. That it was shown is
 * stored, so it is never shown again.
 *
 * Audit follow-up (2026-10-28): the pop-up must not cut the reading it was
 * found in. The language is remembered when its voice is found missing, and
 * the pop-up waits until the speech has ended (checked twice running, so a
 * gap between two sentences does not count), with a feature in front and
 * nothing else open. It is marked shown when it is actually shown.
 */
class MissingVoiceHintTest {

    private var stored = false
    private fun hint() = MissingVoiceHint(shown = { stored }, markShown = { stored = true })

    /** Checks [times] times running; the last answer. */
    private fun MissingVoiceHint.check(times: Int, speechOver: Boolean = true, featureInFront: Boolean = true): String? {
        var last: String? = null
        repeat(times) { last = toShow(speechOver, featureInFront) }
        return last
    }

    @Test
    fun `nothing is shown while the reading goes on`() {
        val hint = hint()
        hint.voiceMissing(setOf("zh"))
        assertNull(hint.check(5, speechOver = false))
        assertFalse("not marked shown before it is shown", stored)
    }

    @Test
    fun `it is shown once the speech has ended, and marked shown then`() {
        val hint = hint()
        hint.voiceMissing(setOf("zh"))
        assertNull(hint.toShow(speechOver = false, featureInFront = true))
        assertNull("one quiet check may be a gap between sentences", hint.toShow(speechOver = true, featureInFront = true))
        assertFalse(stored)
        assertEquals("zh", hint.toShow(speechOver = true, featureInFront = true))
        assertTrue(stored)
    }

    @Test
    fun `a sentence starting again after a gap waits again`() {
        val hint = hint()
        hint.voiceMissing(setOf("ar"))
        assertNull(hint.toShow(speechOver = true, featureInFront = true))
        assertNull(hint.toShow(speechOver = false, featureInFront = true))
        assertNull(hint.toShow(speechOver = true, featureInFront = true))
        assertEquals("ar", hint.toShow(speechOver = true, featureInFront = true))
    }

    @Test
    fun `it is shown once ever`() {
        val hint = hint()
        hint.voiceMissing(setOf("zh"))
        assertEquals("zh", hint.check(2))
        hint.voiceMissing(setOf("ar"))
        assertNull(hint.check(5))
        // After a restart too.
        val again = hint()
        again.voiceMissing(setOf("ar"))
        assertNull(again.check(5))
    }

    @Test
    fun `not while no feature is in front, and the language is kept for later`() {
        val hint = hint()
        hint.voiceMissing(setOf("zh"))
        assertNull(hint.check(5, featureInFront = false))
        assertFalse(stored)
        assertEquals("zh", hint.check(2))
    }

    @Test
    fun `nothing missing shows nothing`() {
        val hint = hint()
        hint.voiceMissing(emptySet())
        assertFalse(hint.waiting)
        assertNull(hint.check(5))
    }

    @Test
    fun `the first missing language is the one told about`() {
        val hint = hint()
        hint.voiceMissing(setOf("zh"))
        hint.voiceMissing(setOf("ar"))
        assertTrue(hint.waiting)
        assertEquals("zh", hint.check(2))
        assertFalse(hint.waiting)
    }

    @Test
    fun `not quiet while the user records, or while an answer is on its way`() {
        // Audit, 2026-10-28: a recording silences the voice, and a question
        // or photo on its way is not yet written; the hint opened over both.
        assertTrue(MissingVoiceHint.readingOver())
        assertFalse(MissingVoiceHint.readingOver(speaking = true))
        assertFalse(MissingVoiceHint.readingOver(answerWriting = true))
        assertFalse(MissingVoiceHint.readingOver(pageReading = true))
        assertFalse(MissingVoiceHint.readingOver(listening = true))
        assertFalse(MissingVoiceHint.readingOver(answerOnItsWay = true))
    }
}
