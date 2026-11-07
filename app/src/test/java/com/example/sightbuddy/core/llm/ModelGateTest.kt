package com.example.sightbuddy.core.llm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Issue #24, the phone on 2026-10-24: a streamed answer took the model's lock
 * on one thread and let it go on another (a coroutine resumes on any thread
 * of its pool). The lock stayed taken, the next question waited 69 s in
 * silence until it was cancelled, and the unlock's error became the answer.
 */
class ModelGateTest {

    /** Gives up after [ms]: no wait in a test is unbounded. */
    private fun within(ms: Long): () -> Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(ms)
        return { System.nanoTime() > deadline }
    }

    @Test
    fun `a call that ends on another thread than it started on lets the next one in`() {
        val gate = ModelGate()
        val pool = Executors.newFixedThreadPool(3)
        try {
            assertTrue(pool.submit<Boolean> { gate.enter(within(2_000)) }.get(5, TimeUnit.SECONDS))
            // The same call, finishing on another thread.
            pool.submit { runCatching { gate.leave() } }.get(5, TimeUnit.SECONDS)
            val next = pool.submit<Boolean> { gate.enter(within(2_000)) }
            assertTrue("the next call never got the model", next.get(5, TimeUnit.SECONDS))
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `a call waiting for the model gives up when it is cancelled`() {
        val gate = ModelGate()
        val pool = Executors.newFixedThreadPool(2)
        try {
            assertTrue(pool.submit<Boolean> { gate.enter(within(2_000)) }.get(5, TimeUnit.SECONDS))
            val waiting = pool.submit<Boolean> { gate.enter(within(300)) }
            assertFalse(waiting.get(5, TimeUnit.SECONDS))
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `one call at a time`() {
        val gate = ModelGate()
        assertTrue(gate.enter(within(1_000)))
        val pool = Executors.newSingleThreadExecutor()
        try {
            assertFalse(pool.submit<Boolean> { gate.enter(within(300)) }.get(5, TimeUnit.SECONDS))
            gate.leave()
            assertTrue(pool.submit<Boolean> { gate.enter(within(1_000)) }.get(5, TimeUnit.SECONDS))
        } finally {
            pool.shutdownNow()
        }
    }
}
