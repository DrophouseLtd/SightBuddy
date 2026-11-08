package com.example.sightbuddy.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Item 9 (issue #8): TalkBack started on Help, the first thing on the screen.
 * At launch the selected feature tab takes the focus instead, once, so TalkBack
 * reads the feature showing; not on a return from the background or a swipe.
 */
class LaunchFocusTest {

    @Test
    fun theSelectedTabTakesTheFocusWhenTheFeaturesFirstShow() {
        val focus = LaunchFocus()
        // Permissions, terms, a pop-up: the features are not in front yet.
        assertFalse(focus.take(featuresInFront = false))
        assertFalse(focus.take(featuresInFront = false))
        assertTrue(focus.take(featuresInFront = true))
    }

    @Test
    fun eachArrivalOnTheFeatureScreenButNotASwipe() {
        val focus = LaunchFocus()
        assertTrue(focus.take(featuresInFront = true))
        // A swipe to another feature: TalkBack reads the new tab itself.
        assertFalse(focus.take(featuresInFront = true))
        // To the background, or Settings opened.
        assertFalse(focus.take(featuresInFront = false))
        // Back: the tab takes the focus again.
        assertTrue(focus.take(featuresInFront = true))
        assertFalse(focus.take(featuresInFront = true))
    }

    @Test
    fun theTabAsksForTheFocusOnlyOnce() {
        val focus = LaunchFocus()
        // Nothing to ask for before the launch focus is given.
        assertFalse(focus.request())
        assertTrue(focus.take(featuresInFront = true))
        // The feature bar as it first shows.
        assertTrue(focus.request())
        // The keyboard hid the bar while a question was typed; it is back.
        assertFalse(focus.request())
        assertFalse(focus.request())
        // Settings opened and closed: one move for the new arrival.
        focus.take(featuresInFront = false)
        assertTrue(focus.take(featuresInFront = true))
        assertTrue(focus.request())
        assertFalse(focus.request())
    }

    @Test
    fun theFocusMoveIsTriedWithinTheFirstSecondOnly() {
        val attempts = LaunchFocus.SCREEN_READER_FOCUS_ATTEMPTS_MS
        // At once, before TalkBack places its own first focus.
        assertTrue(attempts.first() == 0L)
        assertTrue(attempts.zipWithNext().all { (a, b) -> b > a })
        // Never later: by then the user may be exploring the screen.
        assertTrue(attempts.last() <= 700L)
    }

    @Test
    fun theFeatureScreenIsInFrontOnlyWithNothingOverIt() {
        fun inFront(
            foreground: Boolean = true,
            onboarding: Boolean = false,
            settings: Boolean = false,
            feature: Boolean = true,
            popup: Boolean = false,
        ) = LaunchFocus.featureScreenInFront(foreground, onboarding, settings, feature, popup)
        assertTrue(inFront())
        assertFalse(inFront(foreground = false))
        assertFalse(inFront(onboarding = true))
        assertFalse(inFront(settings = true))
        assertFalse(inFront(feature = false))
        // Help, Memories and other pop-ups: closing one is an arrival. (The
        // message menu and the object picker are not passed as pop-ups.)
        assertFalse(inFront(popup = true))
    }

    @Test
    fun aNewLaunchTakesItAgain() {
        assertTrue(LaunchFocus().take(featuresInFront = true))
        assertTrue(LaunchFocus().take(featuresInFront = true))
    }
}
