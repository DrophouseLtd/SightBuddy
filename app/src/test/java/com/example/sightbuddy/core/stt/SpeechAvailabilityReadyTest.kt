package com.example.sightbuddy.core.stt

import org.junit.Assert.*
import org.junit.Test

class SpeechAvailabilityReadyTest {
    @Test fun cloudChoiceSurvivesReadyTransitionForUnsupportedLanguage() {
        for (language in listOf("pl", "fi")) {
            var wasReady = false
            var cloud = true
            fun transition(ready: Boolean) {
                val speech = SpeechAvailability(modelServesLanguage = false, modelDownloaded = ready, cloudEnabled = cloud)
                if (speech.shouldDisableCloudOnModelReady(wasReady)) cloud = false
                wasReady = ready
            }
            transition(false)
            transition(true)
            assertTrue(language, cloud)
            assertEquals(SpeechEngine.CLOUD, SpeechAvailability(false, true, cloud).engine)
            transition(true)
            assertTrue(language, cloud)
        }
    }

    @Test fun englishNewReadyTransitionDisablesCloudOnlyOnce() {
        var wasReady = false
        var cloud = true
        fun transition(ready: Boolean) {
            val speech = SpeechAvailability(true, ready, cloud)
            if (speech.shouldDisableCloudOnModelReady(wasReady)) cloud = false
            wasReady = ready
        }
        transition(false)
        assertTrue(cloud)
        transition(true)
        assertFalse(cloud)
        cloud = true // User deliberately re-enables cloud after the download.
        transition(true)
        assertTrue(cloud)
    }
}
