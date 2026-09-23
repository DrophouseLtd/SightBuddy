package com.example.sightbuddy.features.chat

/**
 * Turns the recogniser's blocks into readable text: one paragraph per block,
 * with a blank line between them, so a title, a caption and each section of a
 * page stay apart in the chat and the voice pauses between them.
 *
 * Within a block the recogniser breaks the text where the printed lines end.
 * Those lines are joined back into running text, and a word hyphenated across
 * a line end ("vuokra-" then "laisen") is put back together.
 */
object TextSections {

    /** [blocks] in reading order, each as its printed lines. */
    fun join(blocks: List<List<String>>): String =
        blocks.map { paragraph(it) }.filter { it.isNotEmpty() }.joinToString("\n\n")

    fun paragraph(lines: List<String>): String {
        val out = StringBuilder()
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            // A word broken at the line end carries on in lower case.
            val brokenWord = out.length > 1 && out.endsWith("-") &&
                out[out.length - 2].isLetter() && line.first().isLowerCase()
            when {
                out.isEmpty() -> out.append(line)
                brokenWord -> {
                    out.setLength(out.length - 1)
                    out.append(line)
                }
                else -> out.append(' ').append(line)
            }
        }
        return out.toString()
    }
}
