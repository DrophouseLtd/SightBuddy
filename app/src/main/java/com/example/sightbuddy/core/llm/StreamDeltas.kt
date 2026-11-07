package com.example.sightbuddy.core.llm

/**
 * The messages of a streamed answer (issue #24), as LiteRT-LM's
 * `MessageCallback.onMessage` hands them over, turned into new text only.
 *
 * Whether each message holds the text written since the last one or all of
 * it so far is decided in LiteRT-LM's native code, out of sight of its Kotlin
 * side (`Message.toString()` is only its text parts joined). Both are taken:
 * once the answer so far is long enough to tell ([TELL_CHARS]), a message
 * that starts with all of it is taken as the whole text again, and only what
 * follows is new. [cumulative] says which it was, for the log.
 */
class StreamDeltas {
    private val whole = StringBuilder()

    /** The answer so far. */
    val text: String get() = whole.toString()

    /** Null until known; then true when messages hold the whole text so far. */
    private var wholeEachTime: Boolean? = null

    val cumulative: Boolean get() = wholeEachTime == true

    /** The new text in [chunk]; empty when there is none. */
    fun next(chunk: String): String {
        if (chunk.isEmpty()) return ""
        if (wholeEachTime == null && whole.length >= TELL_CHARS) {
            wholeEachTime = chunk.length >= whole.length && chunk.startsWith(whole)
        }
        val new = if (wholeEachTime == true && chunk.startsWith(whole)) {
            chunk.substring(whole.length)
        } else {
            chunk
        }
        whole.append(new)
        return new
    }

    companion object {
        /** Shorter than this, a repeat of the start is as likely to be new text. */
        private const val TELL_CHARS = 4

        /** How much of each message the dev log shows. */
        private const val LOGGED_CHARS = 30

        /**
         * One dev-log line for a message: its number, when it came, its
         * length, how much of it was new, and how it starts.
         */
        fun logLine(index: Int, atMs: Long, chunk: String, new: String): String {
            val start = chunk.take(LOGGED_CHARS).replace("\n", "\\n")
            return "delta $index at $atMs ms: ${chunk.length} chars, new ${new.length}, \"$start\""
        }
    }
}
