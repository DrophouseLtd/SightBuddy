package com.example.sightbuddy.core.llm

/**
 * The wait for one streamed answer in LocalGemma (issue #24), checked every
 * tenth of a second: go on waiting, tell the model to stop (the answer was
 * cancelled, or it has run [limitMs]), or give up waiting [capMs] after
 * that if LiteRT-LM never says it is done, so the model is let go for the
 * next question rather than held for ever.
 */
class StreamWatch(private val limitMs: Long, private val capMs: Long) {
    enum class Step { WAIT, STOP, GIVE_UP }

    private var stoppedAt: Long? = null

    /** The answer was stopped by the time limit, not by a cancel. */
    var stoppedByLimit: Boolean = false
        private set

    fun step(elapsedMs: Long, cancelled: Boolean): Step {
        val stopped = stoppedAt
        if (stopped == null) {
            if (!cancelled && elapsedMs <= limitMs) return Step.WAIT
            stoppedAt = elapsedMs
            stoppedByLimit = !cancelled
            return Step.STOP
        }
        return if (elapsedMs - stopped > capMs) Step.GIVE_UP else Step.WAIT
    }
}
