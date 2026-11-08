package com.example.sightbuddy.core.llm

import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * One model call at a time (LocalGemma: answers, transcription, warm-up,
 * release).
 *
 * A semaphore, not a lock: a lock belongs to the thread that took it, and a
 * call that waits on a coroutine resumes on whichever thread of its pool is
 * free. The first streaming attempt (issue #24) took a `ReentrantLock` on one
 * thread and let it go on another: the unlock threw, its error became the
 * answer, and the lock stayed taken, so the next question waited in silence
 * (69 s on the phone, 2026-10-24) until it was cancelled.
 */
class ModelGate {
    private val permit = Semaphore(1)

    /**
     * Waits for the model, asking [gaveUp] every [POLL_MS]; true once this
     * call has it, false when it gave up. A true must be followed by [leave].
     */
    fun enter(gaveUp: () -> Boolean): Boolean {
        while (!permit.tryAcquire(POLL_MS, TimeUnit.MILLISECONDS)) {
            if (gaveUp()) return false
        }
        return true
    }

    /** Lets the next call in; from any thread. */
    fun leave() = permit.release()

    /** Runs [block] with the model, waiting as long as it takes. */
    inline fun <T> withModel(block: () -> T): T {
        enter { false }
        try {
            return block()
        } finally {
            leave()
        }
    }

    private companion object {
        const val POLL_MS = 100L
    }
}
