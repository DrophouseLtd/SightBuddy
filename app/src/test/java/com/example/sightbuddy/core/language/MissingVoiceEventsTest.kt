package com.example.sightbuddy.core.language

import org.junit.Assert.*
import org.junit.Test

class MissingVoiceEventsTest {
    @Test fun initializationBeforeListenerIsNotLost() {
        val events = MissingVoiceEvents()
        events.report(setOf("pl"))
        val received = mutableListOf<Set<String>>()
        events.listener = { received.add(it) }
        assertEquals(listOf(setOf("pl")), received)
        events.listener = { received.add(it) }
        assertEquals(1, received.size)
    }

    @Test fun attachedListenerGetsLiveReport() {
        val events = MissingVoiceEvents()
        val received = mutableListOf<Set<String>>()
        events.listener = { received.add(it) }
        events.report(setOf("fi"))
        assertEquals(listOf(setOf("fi")), received)
    }

    @Test fun detachedListenerAccumulatesLanguagesWithoutDuplicates() {
        val events = MissingVoiceEvents()
        events.report(setOf("pl"))
        events.report(setOf("pl", "fi"))
        val received = mutableListOf<Set<String>>()
        events.listener = { received.add(it) }
        assertEquals(listOf(setOf("pl", "fi")), received)
    }
}
