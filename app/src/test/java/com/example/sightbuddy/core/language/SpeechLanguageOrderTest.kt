package com.example.sightbuddy.core.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #26: a line whose language must be found first is said once it is
 * known, and the lines after it wait their turn, so a streamed answer is
 * still said in order. A line with nothing to find goes at once, as every
 * line did before. Time is a fake clock moved by hand.
 */
class SpeechLanguageOrderTest {

    private var nowMs = 0L
    private val timers = mutableListOf<Pair<Long, () -> Unit>>()
    /** How many lines gave up waiting (logged in the app). */
    private var gaveUp = 0

    private val order = SpeechLanguageOrder(
        schedule = { delayMs, action -> timers += (nowMs + delayMs) to action },
        onGaveUp = { gaveUp++ },
    )

    private fun advanceTo(toMs: Long) {
        while (true) {
            val next = timers.filter { it.first <= toMs }.minByOrNull { it.first } ?: break
            timers.remove(next)
            nowMs = next.first
            next.second()
        }
        nowMs = toMs
    }

    /** What was said, with the language tags it was said with. */
    private val said = mutableListOf<Pair<String, List<String>>>()

    /** The lookups not answered yet, by line. */
    private val lookups = mutableMapOf<String, (List<LanguageSpan>) -> Unit>()

    private fun plain(text: String): Boolean = order.add(null) { spans ->
        said += text to spans.map { it.tag }
        true
    }

    private fun lookedUp(text: String): Boolean = order.add({ onFound -> lookups[text] = onFound }) { spans ->
        said += text to spans.map { it.tag }
        true
    }

    private fun found(text: String, tag: String) {
        lookups.getValue(text)(listOf(LanguageSpan(0, text.length, tag)))
    }

    @Test
    fun `a line with nothing to find is said at once`() {
        assertTrue(plain("Left"))
        assertEquals(listOf("Left" to emptyList<String>()), said)
    }

    @Test
    fun `what the voice answers for a line said at once is passed back`() {
        assertFalse(order.add(null) { false })
    }

    @Test
    fun `a line waits for its language, and the lines after it wait for it`() {
        lookedUp("مرحبا بكم")
        plain("Thanks.")
        assertEquals(emptyList<Pair<String, List<String>>>(), said)
        assertTrue(order.waiting())
        found("مرحبا بكم", "ar")
        assertEquals(listOf("مرحبا بكم" to listOf("ar"), "Thanks." to emptyList()), said)
        assertFalse(order.waiting())
    }

    @Test
    fun `languages found out of order are said in order`() {
        lookedUp("مرحبا بكم")
        lookedUp("这是一个测试")
        found("这是一个测试", "zh")
        assertEquals(emptyList<Pair<String, List<String>>>(), said)
        found("مرحبا بكم", "ar")
        assertEquals(listOf("مرحبا بكم", "这是一个测试"), said.map { it.first })
    }

    @Test
    fun `cleared lines are never said, and their late answers change nothing`() {
        lookedUp("مرحبا بكم")
        plain("Thanks.")
        order.clear()
        assertFalse(order.waiting())
        found("مرحبا بكم", "ar")
        advanceTo(SpeechLanguageOrder.WAIT_MS * 2)
        assertEquals(emptyList<Pair<String, List<String>>>(), said)
        plain("Next.")
        assertEquals(listOf("Next."), said.map { it.first })
    }

    @Test
    fun `a language never found is given up on, and the line said in the app's language`() {
        lookedUp("مرحبا بكم")
        advanceTo(SpeechLanguageOrder.WAIT_MS - 1)
        assertEquals(emptyList<Pair<String, List<String>>>(), said)
        advanceTo(SpeechLanguageOrder.WAIT_MS)
        assertEquals(listOf("مرحبا بكم" to emptyList<String>()), said)
        // An answer after that is ignored: the line is not said twice.
        found("مرحبا بكم", "ar")
        assertEquals(1, said.size)
    }

    @Test
    fun `giving up is reported, once, so a phone log shows it`() {
        lookedUp("مرحبا بكم")
        advanceTo(SpeechLanguageOrder.WAIT_MS)
        assertEquals(1, gaveUp)
        // A line whose language came in time, or was cleared, gave nothing up.
        lookedUp("这是一个测试")
        found("这是一个测试", "zh")
        lookedUp("出口")
        order.clear()
        advanceTo(SpeechLanguageOrder.WAIT_MS * 3)
        assertEquals(1, gaveUp)
    }
}
