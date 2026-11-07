package com.example.sightbuddy.features.chat

import com.example.sightbuddy.core.OpenAiTransport

/**
 * Speaks one AI answer as it is written (issue #24), and decides everything
 * about that which is not Android: which sentences are said, when the answer
 * stops being said, what the growing bubble shows, and the dev log's line.
 * `AppController.streamAnswer` feeds it on the main thread.
 *
 * - Each complete sentence ([SentenceStream]) goes to [say]; the first is
 *   flagged, so it can cut what was being said before (and the page reader).
 * - Once a sentence cannot be said, nothing more of this answer is: the user
 *   has left the chat ([stillHere]: another feature, a new chat, Settings,
 *   the background, a newer answer), or the voice refused it ([say] returned
 *   false: Mute, a recording holding the floor, a pop-up). An answer must not
 *   resume half way once that is over.
 * - An answer that never came part by part (an error said in its place, or
 *   OpenAI's answer, which comes whole) is said whole when it comes, as every
 *   answer was before.
 * - A cancelled answer says nothing more.
 * - An answer that fails part way (the model failed, or ran out of time)
 *   says the failure after what was said, without its unfinished sentence.
 */
class AnswerSpeaker(
    private val stillHere: () -> Boolean,
    /** Says a sentence; false when the voice would not take it. */
    private val say: (sentence: String, first: Boolean) -> Boolean,
    private val now: () -> Long,
    /** The first sentence is here: the answer is no longer waited for. */
    private val onFirstSentence: () -> Unit = {},
) {
    private val splitter = SentenceStream()
    private val streamed = StringBuilder()
    private val startedAt = now()
    private var deltas = 0
    private var sentences = 0
    private var firstSpokenAt: Long? = null
    private var endedAt: Long? = null
    private var silenced = false
    private var firstReported = false

    /**
     * What the bubble shows while the answer is written: the complete
     * sentences so far, with the answer's own line breaks. Not each token:
     * TalkBack re-reads a message under its focus as it changes.
     */
    var shown: String = ""
        private set

    /** One part of the answer, as written. */
    fun add(delta: String) {
        if (delta.isEmpty()) return
        deltas++
        streamed.append(delta)
        val complete = splitter.append(delta)
        if (complete.isEmpty()) return
        shown = streamed.substring(0, streamed.length - splitter.held).trimEnd()
        complete.forEach(::speak)
    }

    /**
     * The answer is over; [answer] is what it came to (or
     * [OpenAiTransport.CANCELLED]). [failedPartWay]: the model failed or was
     * stopped after writing part of it, and [answer] is the failure to say:
     * it is said after the sentences said so far, and the unfinished one is
     * dropped.
     */
    fun finish(answer: String, failedPartWay: Boolean = false) {
        endedAt = now()
        val rest = splitter.finish()
        when {
            answer == OpenAiTransport.CANCELLED -> return
            failedPartWay || streamed.isBlank() -> if (answer.isNotBlank()) speak(answer.trim())
            else -> rest?.let(::speak)
        }
    }

    /** Whether any of the answer came part by part. */
    val streamedAny: Boolean get() = streamed.isNotBlank()

    private fun speak(sentence: String) {
        if (!firstReported) {
            firstReported = true
            onFirstSentence()
        }
        if (silenced) return
        if (!stillHere()) {
            silenced = true
            return
        }
        val first = firstSpokenAt == null
        if (!say(sentence, first)) {
            silenced = true
            return
        }
        sentences++
        if (first) firstSpokenAt = now()
    }

    /** The dev log's line for this answer, under `STREAM-ANSWER`. */
    fun logLine(): String {
        val spoken = firstSpokenAt?.let { "first sentence spoken at ${it - startedAt} ms" } ?: "nothing spoken"
        val ended = endedAt?.let { "whole answer at ${it - startedAt} ms" } ?: "not finished"
        return "$spoken; $ended; deltas $deltas; sentences spoken $sentences"
    }
}
