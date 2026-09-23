package com.example.sightbuddy.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryNamesTest {

    @Test
    fun cleanStripsModelDecoration() {
        assertEquals("Water Outage Notice", MemoryNames.clean("\"Water Outage Notice.\"\nExtra line"))
        assertEquals("Ibuprofen dosage", MemoryNames.clean("Title: **Ibuprofen dosage**"))
    }

    @Test
    fun identicalAndNearIdenticalNamesClash() {
        val taken = listOf("Water outage notice")
        assertTrue(MemoryNames.clashes("water outage notice", taken))
        assertTrue(MemoryNames.clashes("Water outage notices", taken))
        assertFalse(MemoryNames.clashes("Ibuprofen dosage", taken))
    }

    @Test
    fun eightyPercentIsTheLine() {
        // 10 characters, 2 different: exactly 0.8, which counts as the same.
        assertTrue(MemoryNames.similarity("abcdefghij", "abcdefghxy") >= MemoryNames.SIMILAR)
        // 3 different: 0.7, a different name.
        assertFalse(MemoryNames.similarity("abcdefghij", "abcdefgxyz") >= MemoryNames.SIMILAR)
    }

    @Test
    fun numberedFindsAFreeNumber() {
        assertEquals("Kitchen 3", MemoryNames.numbered("Kitchen", listOf("Kitchen", "Kitchen 2")))
    }
}
