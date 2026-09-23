package com.example.sightbuddy.core

/**
 * Speaks a guidance cue on a steady beat instead of whenever it changes.
 *
 * The detector's view of an object flickers: from frame to frame the object
 * is left, then centred, then missed, then left again. Saying each change as
 * it came made Find objects chatter and bounce. Here every frame only records
 * where the object is, and once per [beatMs] the cue seen most often since the
 * last beat is said (the latest breaks a tie), so a one-frame flicker is
 * outvoted and the voice keeps a regular rhythm. The first sighting is said at
 * once. Nothing is said once the object has not been seen for [staleMs].
 */
class CueBeat<T : Any>(
    private val beatMs: Long = 1_200L,
    private val staleMs: Long = 800L,
) {
    private val seen = ArrayDeque<Pair<Long, T>>()
    private var lastBeatAtMs: Long? = null

    fun reset() {
        seen.clear()
        lastBeatAtMs = null
    }

    /**
     * Records [cue], where the object is now (null when it was not found), and
     * returns the cue to say if this frame falls on the beat.
     */
    fun next(cue: T?, nowMs: Long): T? {
        if (cue != null) seen.addLast(nowMs to cue)
        while (seen.isNotEmpty() && nowMs - seen.first().first > beatMs) seen.removeFirst()
        val lastSeen = seen.lastOrNull() ?: return null
        if (nowMs - lastSeen.first > staleMs) return null
        val lastBeat = lastBeatAtMs
        if (lastBeat != null && nowMs - lastBeat < beatMs) return null
        lastBeatAtMs = nowMs
        val counts = seen.groupingBy { it.second }.eachCount()
        val most = counts.values.max()
        // The most frequent; among equals, the one seen last.
        return seen.last { counts[it.second] == most }.second
    }
}
