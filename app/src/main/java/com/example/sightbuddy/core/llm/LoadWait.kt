package com.example.sightbuddy.core.llm

/**
 * How long a request waits for Gemma to load, not counting the time the model
 * file is being hashed: the first load after an update hashes 2.6 GB before
 * the load itself (about 70 s the first time) even starts, and the first
 * question must not time out because of it. At most [maxLeftOutMs] is left
 * out, so a hash stuck on stalled storage cannot keep a request waiting for
 * ever. Times in ms, from one clock.
 */
class LoadWait(private val limitMs: Long, private val startedAt: Long, private val maxLeftOutMs: Long = 300_000L) {

    private var leftOut = 0L
    private var last = startedAt

    /** True when the load has been waited for longer than the limit; call it as the wait goes on. */
    fun expired(now: Long, verifying: Boolean): Boolean {
        if (verifying) leftOut = minOf(maxLeftOutMs, leftOut + (now - last))
        last = now
        return now - startedAt - leftOut > limitMs
    }
}
