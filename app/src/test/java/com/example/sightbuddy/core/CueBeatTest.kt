package com.example.sightbuddy.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CueBeatTest {

    @Test
    fun `the first sighting is said at once`() {
        assertEquals("left", CueBeat<String>().next("left", 0L))
    }

    @Test
    fun `a change between beats is not said until the beat`() {
        val beat = CueBeat<String>(beatMs = 1_200L)
        beat.next("left", 0L)
        assertNull(beat.next("right", 200L))
        assertNull(beat.next("right", 400L))
        assertEquals("right", beat.next("right", 1_200L))
    }

    @Test
    fun `a one-frame flicker is outvoted on the beat`() {
        val beat = CueBeat<String>(beatMs = 1_000L)
        beat.next("left", 0L)
        beat.next("left", 200L)
        beat.next("centre", 400L)
        beat.next("left", 600L)
        beat.next("left", 800L)
        assertEquals("left", beat.next("centre", 1_000L))
    }

    @Test
    fun `the beat keeps its rhythm while the object holds`() {
        val beat = CueBeat<String>(beatMs = 1_000L)
        val said = (0L..3_000L step 200L).mapNotNull { t -> beat.next("left", t)?.let { t } }
        assertEquals(listOf(0L, 1_000L, 2_000L, 3_000L), said)
    }

    @Test
    fun `nothing is said once the object is gone`() {
        val beat = CueBeat<String>(beatMs = 1_000L, staleMs = 800L)
        beat.next("left", 0L)
        beat.next(null, 400L)
        beat.next(null, 800L)
        assertNull(beat.next(null, 1_000L))
    }

    @Test
    fun `missed frames between sightings do not stop the beat`() {
        val beat = CueBeat<String>(beatMs = 1_000L, staleMs = 800L)
        beat.next("left", 0L)
        beat.next(null, 400L)
        beat.next("left", 600L)
        beat.next(null, 800L)
        assertEquals("left", beat.next(null, 1_000L))
    }
}
