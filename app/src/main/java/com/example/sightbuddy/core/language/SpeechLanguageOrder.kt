package com.example.sightbuddy.core.language

/**
 * Keeps the app's lines in order while some wait for their language (issue
 * #26). Finding a text's language takes the identifier a moment, on its own
 * thread; a line that needs it is said once it is known, and every line
 * after it waits its turn, so a streamed answer is still said sentence by
 * sentence in order. A line with nothing to find, and nothing waiting before
 * it, is said at once, exactly as before: short app lines and direction cues
 * never wait.
 *
 * - A language not found within [waitMs] is given up on: the line is said
 *   in the app's language, as before, and a late answer changes nothing.
 *   [onGaveUp] is told, for the log.
 * - [clear] drops every line still waiting (a flush, a stop, a recording
 *   taking the floor), as the voice drops what it has queued.
 * - Lines are said in the order they came, on the thread that completes the
 *   head of the queue (the identifier's answer comes on the main thread).
 */
class SpeechLanguageOrder(
    private val schedule: (delayMs: Long, action: () -> Unit) -> Unit,
    private val waitMs: Long = WAIT_MS,
    private val onGaveUp: () -> Unit = {},
) {
    private class Entry(val deliver: (List<LanguageSpan>) -> Boolean) {
        var spans: List<LanguageSpan>? = null
    }

    private val queue = ArrayDeque<Entry>()

    /**
     * Says a line through [deliver], with the stretches of it in another
     * language, once [find] has found them; null [find] when there is
     * nothing to find.
     *
     * @return what [deliver] answered when the line was said at once;
     *   otherwise true, as the line is on its way.
     */
    fun add(
        find: ((onFound: (List<LanguageSpan>) -> Unit) -> Unit)?,
        deliver: (List<LanguageSpan>) -> Boolean,
    ): Boolean {
        val entry = Entry(deliver)
        synchronized(this) {
            if (find == null && queue.isEmpty()) return deliver(emptyList())
            if (find == null) entry.spans = emptyList()
            queue.addLast(entry)
        }
        if (find != null) {
            schedule(waitMs) { if (found(entry, emptyList())) onGaveUp() }
            find { spans -> found(entry, spans) }
        }
        return true
    }

    /** Drops every line still waiting. */
    fun clear() {
        synchronized(this) { queue.clear() }
    }

    /** True while a line waits to be said. */
    fun waiting(): Boolean = synchronized(this) { queue.isNotEmpty() }

    /** True when this settled [entry]'s language; false when it was cleared or settled already. */
    private fun found(entry: Entry, spans: List<LanguageSpan>): Boolean {
        synchronized(this) {
            // Cleared, or found already (the answer, or the wait running out).
            if (entry.spans != null || entry !in queue) return false
            entry.spans = spans
            // Every line now ready at the head goes, in order. Said under the
            // lock, so a line added meanwhile cannot slip in ahead of them.
            while (queue.isNotEmpty()) {
                val head = queue.first()
                val ready = head.spans ?: break
                queue.removeFirst()
                head.deliver(ready)
            }
        }
        return true
    }

    companion object {
        /** How long a line waits for its language; the identifier takes milliseconds. */
        const val WAIT_MS = 1_000L
    }
}
