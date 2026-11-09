package com.example.sightbuddy.features.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetectionRulesTest {

    /** The lowest score the detector hands back at all. */
    private val lowest = DetectionRules.DETECTOR_FLOOR + 0.01f

    // ── Find objects: unchanged ─────────────────────────────────────────────

    @Test
    fun `Find objects keeps the detector's floor of 0_4`() {
        assertEquals(0.4f, DetectionRules.DETECTOR_FLOOR)
    }

    @Test
    fun `Find objects takes its target on the first frame at the lowest score`() {
        val frame = listOf("cup" to 0.9f, "cat" to lowest)
        assertEquals("cat" to lowest, DetectionRules.findTarget(frame, "cat") { it.first })
    }

    @Test
    fun `Find objects matches its target whatever the case`() {
        val frame = listOf("cup" to lowest)
        assertEquals("cup" to lowest, DetectionRules.findTarget(frame, "Cup") { it.first })
    }

    @Test
    fun `Find objects ignores every other label`() {
        val frame = listOf("dog" to 0.95f, "bowl" to 0.9f)
        assertNull(DetectionRules.findTarget(frame, "cup") { it.first })
    }
}
