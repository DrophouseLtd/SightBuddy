package com.example.sightbuddy.core.llm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadWaitTest {

    @Test
    fun aLoadIsWaitedForUpToTheLimit() {
        val wait = LoadWait(limitMs = 90_000, startedAt = 1_000)
        assertFalse(wait.expired(now = 91_000, verifying = false))
        assertTrue(wait.expired(now = 91_001, verifying = false))
    }

    @Test
    fun timeSpentHashingTheModelDoesNotCount() {
        // The first load after an update hashes 2.6 GB before loading.
        val wait = LoadWait(limitMs = 90_000, startedAt = 0)
        assertFalse(wait.expired(now = 50_000, verifying = true))
        assertFalse(wait.expired(now = 120_000, verifying = true))
        // The load itself then gets the whole limit.
        assertFalse(wait.expired(now = 200_000, verifying = false))
        assertTrue(wait.expired(now = 210_001, verifying = false))
    }

    @Test
    fun aHashThatNeverEndsStopsBeingLeftOutAfterTheCap() {
        // Stalled storage: the hash goes on, and the wait must still end.
        val wait = LoadWait(limitMs = 90_000, startedAt = 0, maxLeftOutMs = 300_000)
        assertFalse(wait.expired(now = 300_000, verifying = true))
        assertFalse(wait.expired(now = 390_000, verifying = true))
        assertTrue(wait.expired(now = 390_001, verifying = true))
    }

    @Test
    fun theDefaultCapIsFiveMinutes() {
        val wait = LoadWait(limitMs = 90_000, startedAt = 0)
        assertFalse(wait.expired(now = 390_000, verifying = true))
        assertTrue(wait.expired(now = 390_001, verifying = true))
    }
}
