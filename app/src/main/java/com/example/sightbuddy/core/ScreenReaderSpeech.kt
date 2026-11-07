package com.example.sightbuddy.core

import com.example.sightbuddy.core.language.LanguageSpan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The app's speech on its way to the screen reader (issue #15): the lines the
 * screen puts on two invisible live-region nodes for TalkBack to read
 * ([lines]), and when each line counts as said.
 *
 * - A line goes to TalkBack as soon as it is ready (issue #24): at once, or
 *   [HANDOVER_GAP_MS] after the line before, so the screen shows each line
 *   in a frame of its own and TalkBack is told of each separately and in
 *   order. TalkBack queues them itself. They used to wait until the line
 *   before counted as said, by an estimate of speech; at the user's
 *   TalkBack speed that was far slower than the speech, and TalkBack fell
 *   silent for 5-10 s at a time (the phone, 2026-10-27). Never guess the
 *   user's TalkBack speed.
 * - Each line is a node of its own (the screen keys it by [Line.serial]): a
 *   node appearing is told to TalkBack as a subtree change, which TalkBack
 *   queues behind what it is saying. A node's text changing is told as a
 *   content change, and TalkBack drops a content change still queued when a
 *   newer one comes (its utterance group "content_change", cleared on each
 *   new one): lines handed over faster than TalkBack says them would be
 *   lost. The lines take turns between the two places; a line stays on its
 *   node until [NODE_HOLD_MS] has passed or the line two after it takes its
 *   place, so TalkBack has time to read it.
 * - TalkBack never reports that it has finished. A line counts as said after
 *   [SpeechRoute.estimateMs], one after another; its onDone and
 *   [isSpeaking] follow that. Nothing waits for it before handing over.
 * - Flush and queue as with the app's voice. A flush line empties both nodes
 *   and drops the queued lines not yet handed over, with the onDone of every
 *   line before it (the app's voice drops them for speech it cuts off).
 * - [stop] empties both nodes and drops every onDone. What TalkBack already
 *   has goes on: the app cannot silence it (a touch or a swipe does).
 */
class ScreenReaderSpeech(
    private val now: () -> Long,
    /** Runs the action after the delay; on the main thread in the app. */
    private val schedule: (delayMs: Long, action: () -> Unit) -> Unit,
) {
    /**
     * One line on a node. [urgent]: assertive, cutting what TalkBack is saying.
     * [languages]: the stretches of it in another language with a voice (issue
     * #26), tagged for TalkBack so it switches voice for them.
     */
    data class Line(
        val text: String,
        val urgent: Boolean,
        val serial: Long,
        val languages: List<LanguageSpan> = emptyList(),
    )

    private val _lines = MutableStateFlow<List<Line?>>(EMPTY)

    /** The two places' lines; null is an empty place. */
    val lines: StateFlow<List<Line?>> = _lines.asStateFlow()

    private class Queued(val line: Line, val showAtMs: Long, val endAtMs: Long, val onDone: (() -> Unit)?)

    /** Lines not yet counted as said, oldest first. */
    private val queue = ArrayList<Queued>()
    private var serial = 0L
    private var nextNode = 0

    /** When the last line went, or goes, to a node. */
    private var lastShowAtMs = Long.MIN_VALUE / 2

    /** When the last line counts as said. */
    private var lastEndAtMs = Long.MIN_VALUE / 2

    fun say(
        text: String,
        flush: Boolean,
        urgent: Boolean,
        languages: List<LanguageSpan> = emptyList(),
        onDone: (() -> Unit)? = null,
    ) {
        val t = now()
        val item = synchronized(this) {
            if (flush) {
                queue.clear()
                _lines.value = EMPTY
                lastEndAtMs = Long.MIN_VALUE / 2
            }
            val showAt = maxOf(t, lastShowAtMs + HANDOVER_GAP_MS)
            val endAt = maxOf(showAt, lastEndAtMs) + SpeechRoute.estimateMs(text)
            lastShowAtMs = showAt
            lastEndAtMs = endAt
            serial += 1
            Queued(Line(text, urgent, serial, languages), showAt, endAt, onDone).also { queue += it }
        }
        if (item.showAtMs <= t) show(item) else schedule(item.showAtMs - t) { show(item) }
        schedule(item.endAtMs - t) { finish(item) }
    }

    /** Empties both nodes and drops every line not yet said, with its onDone. */
    fun stop() {
        synchronized(this) {
            queue.clear()
            _lines.value = EMPTY
            lastEndAtMs = Long.MIN_VALUE / 2
        }
    }

    /** True until the last line handed over counts as said. */
    fun isSpeaking(): Boolean = synchronized(this) { queue.isNotEmpty() }

    private fun show(item: Queued) {
        synchronized(this) {
            // Dropped by a flush or a stop before its turn came.
            if (item !in queue) return
            val node = nextNode
            nextNode = 1 - node
            _lines.value = _lines.value.mapIndexed { i, line -> if (i == node) item.line else line }
        }
        schedule(NODE_HOLD_MS) { takeOff(item.line) }
    }

    private fun finish(item: Queued) {
        val done = synchronized(this) {
            if (!queue.remove(item)) return
            item.onDone
        }
        done?.invoke()
    }

    /** Empties [line]'s node, unless a later line is on it by now. */
    private fun takeOff(line: Line) {
        synchronized(this) {
            if (line in _lines.value) _lines.value = _lines.value.map { if (it == line) null else it }
        }
    }

    companion object {
        /**
         * How long a line stays on its node. TalkBack takes the text when the
         * node appears (within a frame or two), so a long page need not sit
         * there, early in TalkBack's reading order, for the minutes it takes.
         */
        const val NODE_HOLD_MS = 3_000L

        /**
         * The least time between two lines going to TalkBack: a technical
         * gap, not a guess at speech. More than Compose's 100 ms batching of
         * accessibility changes, so each line is its own change, told in order.
         */
        const val HANDOVER_GAP_MS = 150L

        private val EMPTY: List<Line?> = listOf(null, null)
    }
}
