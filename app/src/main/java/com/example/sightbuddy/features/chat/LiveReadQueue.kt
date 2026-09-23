package com.example.sightbuddy.features.chat

/**
 * What Text chat's live text reads out, box by box.
 *
 * Every frame hands over the boxes on screen ([update]). A box is queued once:
 * not while it waits or is being read, and not again for [memoryMs] after it
 * was queued, even when the recogniser reads it a little differently or only
 * in part, since boxes are compared by the words they share. New boxes join
 * the end of the queue; nothing already being read is cut short. Boxes that
 * have left the screen leave the queue, and when no text is on screen at all
 * the queue is emptied. The queue holds at most [maxQueuedWords] words, and a
 * single box longer than that is cut to it.
 *
 * Pure bookkeeping: the caller says each box ([next]) and reports it done.
 */
class LiveReadQueue(
    private val memoryMs: Long = 120_000L,
    private val maxQueuedWords: Int = 300,
) {
    private class Item(val text: String, val words: Set<String>)

    private val memory = ArrayDeque<Pair<Long, Set<String>>>()
    private val queue = ArrayDeque<Item>()

    /** Forgets everything: the text in view is new again. */
    fun reset() {
        memory.clear()
        queue.clear()
    }

    /** Empties the queue but keeps the memory: nothing waiting is read. */
    fun clearQueue() {
        queue.clear()
    }

    /**
     * [visible] are the boxes on screen now, in reading order. Returns true
     * when there is none and the queue was emptied, so the caller can stop the
     * box being read too.
     */
    fun update(visible: List<String>, nowMs: Long): Boolean {
        while (memory.isNotEmpty() && nowMs - memory.first().first > memoryMs) memory.removeFirst()
        if (visible.isEmpty()) {
            queue.clear()
            return true
        }
        val shown = visible.map { it to wordsOf(it) }.filter { it.second.isNotEmpty() }
        // Only what is on screen is read.
        queue.removeAll { item -> shown.none { same(it.second, item.words) } }
        var queuedWords = queue.sumOf { it.words.size }
        for ((text, words) in shown) {
            if (memory.any { same(it.second, words) }) continue
            if (queuedWords >= maxQueuedWords) break
            val item = limited(text)
            queue.addLast(Item(item, words))
            memory.addLast(nowMs to words)
            queuedWords += wordsOf(item).size
        }
        return false
    }

    /** The next box to say, or null when the queue is empty. */
    fun next(): String? = queue.removeFirstOrNull()?.text

    private fun limited(text: String): String {
        val words = text.split(WHITESPACE)
        return if (words.size <= maxQueuedWords) text else words.take(maxQueuedWords).joinToString(" ")
    }

    /** Most of the shorter one's words are in the other: the same box, read again or in part. */
    private fun same(a: Set<String>, b: Set<String>): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        return (a intersect b).size.toFloat() / minOf(a.size, b.size) >= SAME_SHARE
    }

    private fun wordsOf(text: String): Set<String> =
        text.lowercase().split(NOT_A_WORD).filter { it.isNotEmpty() }.toSet()

    private companion object {
        const val SAME_SHARE = 0.7f
        val NOT_A_WORD = Regex("[^\\p{L}\\p{N}]+")
        val WHITESPACE = Regex("\\s+")
    }
}
