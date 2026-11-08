package com.example.sightbuddy.core.llm

import com.example.sightbuddy.core.llm.StreamWatch.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #24, the audit of 2026-10-27: once a streamed answer is told to stop
 * (a cancel, or the time limit), its wait must end even if LiteRT-LM never
 * calls back, so the model is let go for the next question.
 */
class StreamWatchTest {

    private val watch = StreamWatch(limitMs = 180_000, capMs = 10_000)

    @Test
    fun `an answer running normally is waited for`() {
        assertEquals(Step.WAIT, watch.step(elapsedMs = 1_000, cancelled = false))
        assertEquals(Step.WAIT, watch.step(elapsedMs = 179_000, cancelled = false))
    }

    @Test
    fun `a cancel stops it once, and the wait gives up 10 s later`() {
        assertEquals(Step.STOP, watch.step(elapsedMs = 5_000, cancelled = true))
        assertEquals(Step.WAIT, watch.step(elapsedMs = 6_000, cancelled = true))
        assertEquals(Step.WAIT, watch.step(elapsedMs = 15_000, cancelled = true))
        assertEquals(Step.GIVE_UP, watch.step(elapsedMs = 15_001, cancelled = true))
        assertFalse(watch.stoppedByLimit)
    }

    @Test
    fun `the time limit stops it, and says so`() {
        assertEquals(Step.STOP, watch.step(elapsedMs = 180_001, cancelled = false))
        assertTrue(watch.stoppedByLimit)
        assertEquals(Step.GIVE_UP, watch.step(elapsedMs = 190_002, cancelled = false))
    }
}
