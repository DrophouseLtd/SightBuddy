package com.example.sightbuddy.core.llm

import org.junit.Assert.*
import org.junit.Test

class ProcessModelOwnerTest {
    @Test fun replacementClosesOldBeforeNewConstructionAndStaleReleaseCannotCloseNew() {
        val owner = ProcessModelOwner()
        val old = Any()
        val fresh = Any()
        val events = mutableListOf<String>()
        owner.replace(old) { events.add("close-old") }
        owner.replace(fresh) { events.add("close-new") }
        events.add("construct-new")
        assertEquals(listOf("close-old", "construct-new"), events)
        assertFalse(owner.release(old))
        assertEquals(2, events.size)
        assertTrue(owner.release(fresh))
        assertEquals("close-new", events.last())
    }
}
