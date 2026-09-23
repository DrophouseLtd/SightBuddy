package com.example.sightbuddy.features.chat

/**
 * Decides what Text chat's live text shows, frame by frame.
 *
 * The words stay put: they are replaced at most every [holdMs], and only when
 * the text in view is new ([InstantTextReader]). Between replacements each
 * shown block only moves, to where the same words are in the new frame, so the
 * boxes follow the camera. A replacement is a new [LiveText.generation].
 *
 * [resume] starts again from nothing, so the text in view comes up as new:
 * after the user has let go of a pinned box.
 *
 * The live text is cleared once no text has been seen for [keepMs].
 */
class LiveTextTracker(
    private val holdMs: Long = 3_000L,
    private val keepMs: Long = 1_500L,
) {
    private val reader = InstantTextReader()
    private var current: LiveText? = null
    private var shownAtMs = 0L
    private var seenAtMs = 0L
    private var generation = 0

    fun reset() {
        current = null
        reader.reset()
    }

    fun resume() {
        reader.reset()
        shownAtMs = Long.MIN_VALUE / 2
    }

    /** [frameWidthPx] and [frameHeightPx] are the frame [blocks] were found in. */
    fun update(blocks: List<LiveText.Block>, frameWidthPx: Int, frameHeightPx: Int, nowMs: Long): LiveText? {
        if (blocks.isEmpty()) {
            if (nowMs - seenAtMs > keepMs) {
                current = null
                reader.reset()
            }
            return current
        }
        seenAtMs = nowMs
        val shown = current
        val all = blocks.joinToString(" ") { it.text }
        val dueForNew = shown == null || nowMs - shownAtMs >= holdMs
        if (dueForNew && reader.shouldRead(all)) {
            generation++
            shownAtMs = nowMs
            current = LiveText(
                blocks,
                frameWidthPx,
                frameHeightPx,
                generation,
            )
            return current
        }
        if (shown == null) return null
        // Same words, new places.
        current = shown.copy(
            blocks = shown.blocks.map { old ->
                val match = blocks.maxByOrNull { similarity(it.text, old.text) }
                    ?.takeIf { similarity(it.text, old.text) >= SAME_TEXT }
                if (match == null) old
                else old.copy(
                    left = match.left, top = match.top, right = match.right, bottom = match.bottom,
                    lineHeightPx = match.lineHeightPx,
                )
            },
            frameWidthPx = frameWidthPx,
            frameHeightPx = frameHeightPx,
        )
        return current
    }

    private fun similarity(a: String, b: String): Float {
        val wa = wordsOf(a)
        val wb = wordsOf(b)
        if (wa.isEmpty() || wb.isEmpty()) return 0f
        return (wa intersect wb).size.toFloat() / (wa union wb).size
    }

    private fun wordsOf(text: String): Set<String> =
        text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }.toSet()

    private companion object {
        /** Words in common for a block in a new frame to be the one shown. */
        const val SAME_TEXT = 0.5f
    }
}
