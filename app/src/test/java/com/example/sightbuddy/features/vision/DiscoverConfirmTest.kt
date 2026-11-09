package com.example.sightbuddy.features.vision

import com.example.sightbuddy.features.vision.DetectionRules.DETECTOR_FLOOR
import com.example.sightbuddy.features.vision.DetectionRules.DISCOVER_FRAMES_IN_A_ROW
import com.example.sightbuddy.features.vision.DetectionRules.DISCOVER_MIN_SCORE
import com.example.sightbuddy.features.vision.DetectionRules.DISCOVER_OFTEN_WRONG_MIN_SCORE
import com.example.sightbuddy.features.vision.DetectionRules.OFTEN_WRONG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Discover objects named cats, dogs and a butterfly that were not there
 * (issue #14): it said the top detection of every frame, at the detector's
 * floor of 0.4. Written against the numbers in [DetectionRules], so tuning
 * them by ear does not break these.
 */
class DiscoverConfirmTest {

    private val n = DISCOVER_FRAMES_IN_A_ROW
    private val ordinary = DISCOVER_MIN_SCORE + 0.05f
    private val sureAnimal = DISCOVER_OFTEN_WRONG_MIN_SCORE + 0.05f

    /** Feeds [frames] in turn and returns what each one said. */
    private fun DiscoverConfirm.run(frames: List<List<Pair<String, Float>>>): List<String?> =
        frames.map { next(it) }

    private fun repeatFrame(times: Int, frame: List<Pair<String, Float>>) = List(times) { frame }

    @Test
    fun `a cat in one frame at the detector's floor is not named`() {
        assertNull(DiscoverConfirm().next(listOf("cat" to DETECTOR_FLOOR + 0.01f)))
    }

    @Test
    fun `an object is named only on its last frame of the run`() {
        val said = DiscoverConfirm().run(repeatFrame(n, listOf("cup" to ordinary)))
        assertEquals(List(n - 1) { null } + "cup", said)
    }

    @Test
    fun `a missed frame starts the count again`() {
        val frames = repeatFrame(n - 1, listOf("cup" to ordinary)) +
            listOf(emptyList()) +
            repeatFrame(n - 1, listOf("cup" to ordinary))
        assertTrue(DiscoverConfirm().run(frames).all { it == null })
    }

    @Test
    fun `a score Find objects would take is too low for Discover`() {
        val frames = repeatFrame(n + 2, listOf("cup" to DISCOVER_MIN_SCORE - 0.05f))
        assertTrue(DiscoverConfirm().run(frames).all { it == null })
    }

    @Test
    fun `a frame below the threshold breaks the run`() {
        val frames = repeatFrame(n - 1, listOf("cup" to ordinary)) +
            listOf(listOf("cup" to DISCOVER_MIN_SCORE - 0.05f)) +
            repeatFrame(n - 1, listOf("cup" to ordinary))
        assertTrue(DiscoverConfirm().run(frames).all { it == null })
    }

    @Test
    fun `an animal needs a higher score than an object`() {
        val frames = repeatFrame(n + 2, listOf("dog" to ordinary, "cup" to ordinary - 0.01f))
        val said = DiscoverConfirm().run(frames)
        assertEquals("cup", said.last())
        assertTrue("dog" !in said)
    }

    @Test
    fun `a sure animal is named`() {
        assertEquals("cat", DiscoverConfirm().run(repeatFrame(n, listOf("cat" to sureAnimal))).last())
    }

    @Test
    fun `the most confident confirmed object is named, not a newcomer`() {
        val c = DiscoverConfirm()
        c.run(repeatFrame(n, listOf("cup" to ordinary, "chair" to ordinary + 0.1f)))
        assertEquals("chair", c.next(listOf("cup" to ordinary, "chair" to ordinary + 0.1f, "cat" to 0.95f)))
    }

    @Test
    fun `an object stays named while it stays in view`() {
        val said = DiscoverConfirm().run(repeatFrame(n + 3, listOf("cup" to ordinary)))
        assertEquals(List(4) { "cup" }, said.takeLast(4))
    }

    @Test
    fun `two cups in one frame count as one frame`() {
        val frames = repeatFrame(n - 1, listOf("cup" to ordinary, "cup" to ordinary))
        assertTrue(DiscoverConfirm().run(frames).all { it == null })
    }

    @Test
    fun `reset forgets the run`() {
        val c = DiscoverConfirm()
        c.run(repeatFrame(n - 1, listOf("cup" to ordinary)))
        c.reset()
        assertNull(c.next(listOf("cup" to ordinary)))
    }

    @Test
    fun `the rules hold together`() {
        assertTrue(n >= 2)
        assertTrue(DETECTOR_FLOOR < DISCOVER_MIN_SCORE)
        assertTrue(DISCOVER_MIN_SCORE < DISCOVER_OFTEN_WRONG_MIN_SCORE)
        assertTrue(DISCOVER_OFTEN_WRONG_MIN_SCORE < 1f)
        // A misspelt label would never match the detector's.
        assertTrue(OFTEN_WRONG.all { it in COCO_OBJECTS })
        assertTrue(listOf("cat", "dog", "bird").all { it in OFTEN_WRONG })
    }
}
