package com.example.sightbuddy.core.language

import java.util.concurrent.ConcurrentHashMap

/**
 * The utterance ids of a text the app's voice says in parts, one part per
 * voice (issue #26: a mixed answer, English then Arabic). Whoever spoke the
 * text still sees one utterance: the last part has the text's own id, so
 * its end is the text's end, and the others ids of their own, whose ends
 * are not reported. Progress in any part is reported under the text's id,
 * at its place in the whole text: the page reader follows its place by it.
 */
class SpokenParts {
    /** Where each part being said starts in its whole text. */
    private val offsets = ConcurrentHashMap<String, Int>()

    fun idOf(utteranceId: String, index: Int, last: Boolean): String =
        if (last) utteranceId else "$utteranceId$MARK$index"

    /** A part that is not the text's last: its end is not the text's end. */
    fun isInnerPart(id: String): Boolean = id.contains(MARK)

    /** The part [id] starts at [offset] in its whole text. */
    fun started(id: String, offset: Int) {
        offsets[id] = offset
    }

    /** [utteranceId] is said whole now, in one part. */
    fun forget(utteranceId: String) {
        offsets.remove(utteranceId)
    }

    /** The part [id] has ended or was stopped. */
    fun ended(id: String) {
        offsets.remove(id)
    }

    /** The whole text's id, and where [offset] in the part [id] is in it. */
    fun inWhole(id: String, offset: Int): Pair<String, Int> =
        id.substringBefore(MARK) to offset + (offsets[id] ?: 0)

    private companion object {
        const val MARK = "#part"
    }
}
