package com.example.sightbuddy.core.llm

/**
 * How many tokens Gemma's reply may have, its window holding the prompt and
 * the reply together. Chat answers ask for room for a page (issue #13), which
 * an ordinary answer never uses; a long scan is not refused for that room.
 */
object ContextBudget {
    /**
     * [requested], or what the [window] has left after [promptTokens] if that is
     * less, but never below [floor] (an ordinary answer's cap, or [requested]
     * when that is smaller): null when not even that fits.
     */
    fun replyTokens(promptTokens: Int, requested: Int, window: Int, floor: Int): Int? {
        val left = window - promptTokens
        return if (left >= minOf(floor, requested)) minOf(requested, left) else null
    }
}
