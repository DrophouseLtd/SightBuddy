package com.example.sightbuddy.core.language

import org.junit.Assert.assertEquals
import org.junit.Test

class MissingVoiceEventsTest {
    @Test fun initializationEventIsQueuedUntilListenerIsInstalled() {
        val events = MissingVoiceEvents()
        events.report(setOf("pl"))
        val received = mutableListOf<Set<String>>()
        events.listener = { received.add(it) }
        assertEquals(listOf(setOf("pl")), received)
        events.listener = { received.add(it) }
        assertEquals(1, received.size)
    }

    @Test fun attachedListenerGetsLiveReportAndDetachedEventsAccumulate() {
        val events = MissingVoiceEvents()
        val received = mutableListOf<Set<String>>()
        events.listener = { received.add(it) }
        events.report(setOf("fi"))
        events.listener = null
        events.report(setOf("pl"))
        events.report(setOf("pl", "fi"))
        events.listener = { received.add(it) }
        assertEquals(listOf(setOf("fi"), setOf("pl", "fi")), received)
    }
}
