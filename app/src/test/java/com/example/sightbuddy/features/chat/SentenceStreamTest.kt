package com.example.sightbuddy.features.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Issue #24: an answer arriving part by part is turned into complete
 * sentences, so each can be spoken as soon as it exists. The pieces come back
 * exactly as given, so the whole answer is preserved for the chat.
 *
 * An end mark counts once what follows it shows it is one (a space, a line
 * break, the answer ending): a model writes "3." and "50" as separate parts.
 */
class SentenceStreamTest {

    @Test
    fun `a complete sentence is handed back as soon as what follows its end arrives`() {
        val stream = SentenceStream()
        assertEquals(emptyList<String>(), stream.append("The shop "))       // no end yet
        assertEquals(emptyList<String>(), stream.append("opens at nine."))  // end, or "nine.5"?
        assertEquals(listOf("The shop opens at nine."), stream.append(" It"))
        assertEquals("It", stream.finish())
    }

    @Test
    fun `an unfinished sentence is held back`() {
        val stream = SentenceStream()
        assertEquals(emptyList<String>(), stream.append("Is it open"))
        assertEquals(listOf("Is it open?"), stream.append("? Yes"))
        assertEquals(emptyList<String>(), stream.append("!"))
        assertEquals("Yes!", stream.finish())
        assertNull(stream.finish())
    }

    @Test
    fun `each finish mark ends its sentence, and the remainder flushes`() {
        val stream = SentenceStream()
        assertEquals(listOf("One."), stream.append("One. Two"))
        assertEquals(listOf("Two!"), stream.append("! Three"))
        assertEquals(listOf("Three?"), stream.append("? Four"))
        assertEquals("Four", stream.finish())
    }

    @Test
    fun `non-Latin ends split a sentence`() {
        val cjk = SentenceStream()
        // No space after a Chinese or Japanese end: the next character shows it.
        assertEquals(listOf("今日は晴れです。"), cjk.append("今日は晴れです。明日"))
        assertEquals(emptyList<String>(), cjk.append("は雨です。"))
        assertEquals("明日は雨です。", cjk.finish())

        val arabic = SentenceStream()
        assertEquals(listOf("هل هو مفتوح؟", "نعم."), arabic.append("هل هو مفتوح؟ نعم. "))
    }

    @Test
    fun `an ellipsis ends a sentence`() {
        val stream = SentenceStream()
        assertEquals(listOf("And then…"), stream.append("And then… nothing"))
        assertEquals(listOf("nothing at all."), stream.append(" at all. "))
    }

    @Test
    fun `a dot between digits is not a sentence end`() {
        val stream = SentenceStream()
        assertEquals(emptyList<String>(), stream.append("It is 3.5 percent and it"))
        assertEquals(listOf("It is 3.5 percent and it waits."), stream.append(" waits.\n"))
    }

    @Test
    fun `a known abbreviation keeps its dot`() {
        val stream = SentenceStream()
        assertEquals(emptyList<String>(), stream.append("Dr. Smith and Mr. Jones"))
        assertEquals(listOf("Dr. Smith and Mr. Jones went home."), stream.append(" went home. "))
    }

    @Test
    fun `the word no at the end of a sentence still ends it`() {
        val stream = SentenceStream()
        assertEquals(listOf("The answer is no."), stream.append("The answer is no. Next"))
        assertEquals(listOf("Next."), stream.append(". "))
    }

    @Test
    fun `closing quotes and brackets belong to their sentence`() {
        val stream = SentenceStream()
        assertEquals(listOf("He said \"stop.\""), stream.append("He said \"stop.\" then"))
        assertEquals(listOf("then left."), stream.append(" left. "))
    }

    @Test
    fun `a line break ends a line of its own, as a heading or a label's line`() {
        // The phone, 2026-10-24: "Read from image" of a label, lines without a
        // full stop, spoke nothing until the last sentence 23 s in.
        val stream = SentenceStream()
        assertEquals(listOf("Instructions"), stream.append("Instructions\nTake two"))
        assertEquals(listOf("Take two"), stream.append("\n"))
        assertEquals(listOf("Ground coffee", "500 g"), stream.append("Ground coffee\n500 g\n\nBest"))
        assertEquals("Best", stream.finish())
    }

    @Test
    fun `a stop at the very end of a part waits for what follows it`() {
        // A token stream splits "3.50" and "e.g." anywhere.
        val number = SentenceStream()
        assertEquals(emptyList<String>(), number.append("It costs 3."))
        assertEquals(emptyList<String>(), number.append("50 a box"))
        assertEquals(listOf("It costs 3.50 a box."), number.append(". Next"))

        val abbreviation = SentenceStream()
        assertEquals(emptyList<String>(), abbreviation.append("Fruit, e."))
        assertEquals(emptyList<String>(), abbreviation.append("g. apples"))
        assertEquals(listOf("Fruit, e.g. apples."), abbreviation.append(". Then"))
    }

    @Test
    fun `a list item's number is not a sentence of its own`() {
        // The audit, 2026-10-27: "1. Take two tablets" was said as "1." and then the rest.
        val stream = SentenceStream()
        assertEquals(listOf("1. Take two tablets."), stream.append("1. Take two tablets.\n2. Drink"))
        assertEquals(listOf("2. Drink water."), stream.append(" water.\n"))
        assertEquals(listOf("Steps", "10. Rest."), stream.append("Steps\n10. Rest. "))
    }

    @Test
    fun `a number ending a sentence inside a line still ends it`() {
        val stream = SentenceStream()
        assertEquals(listOf("There are 5."), stream.append("There are 5. Then more"))
    }

    @Test
    fun `a stop inside a word or a web address is not an end`() {
        val stream = SentenceStream()
        assertEquals(emptyList<String>(), stream.append("Visit www.example.com or call"))
        assertEquals(listOf("Visit www.example.com or call."), stream.append(". Done"))
    }

    @Test
    fun `an abbreviation is matched as a whole word, whatever its case`() {
        val stream = SentenceStream()
        // "Mr." is an abbreviation; "Hammr." is a word ending a sentence.
        assertEquals(listOf("It is a Hammr."), stream.append("It is a Hammr. ETC. follows"))
        assertEquals(listOf("ETC. follows."), stream.append(". "))
    }

    @Test
    fun `the last stop of the answer ends it when the answer ends`() {
        val stream = SentenceStream()
        assertEquals(emptyList<String>(), stream.append("All done."))
        assertEquals("All done.", stream.finish())
    }

    @Test
    fun `the pieces handed back are exactly the answer, in order`() {
        val answer = "First sentence. Second sentence is a little longer! And a third?"
        // Feed it two characters at a time, as a token stream would; the spoken
        // sentences plus the flushed remainder reconstruct the answer exactly.
        val stream = SentenceStream()
        val spoken = mutableListOf<String>()
        answer.chunked(2).forEach { spoken += stream.append(it) }
        assertEquals(answer, (spoken + listOfNotNull(stream.finish())).joinToString(" "))
    }
}
