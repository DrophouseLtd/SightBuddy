package com.example.sightbuddy.core.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Issue #13: a chat answer may run to a page (a cap of 1,200 tokens), and on
 * the phone Gemma's window (4,096 tokens) holds the prompt and the reply
 * together. A long scan must not be refused for the room a short answer
 * never uses.
 */
class ContextBudgetTest {

    private fun reply(prompt: Int, requested: Int = 1200) =
        ContextBudget.replyTokens(promptTokens = prompt, requested = requested, window = 4096, floor = 150)

    @Test
    fun `with room in the window the reply gets the cap asked for`() {
        assertEquals(1200, reply(prompt = 1000))
        assertEquals(1200, reply(prompt = 4096 - 1200))
    }

    @Test
    fun `a long prompt leaves the reply what is left of the window`() {
        assertEquals(596, reply(prompt = 3500))
        assertEquals(150, reply(prompt = 4096 - 150))
    }

    @Test
    fun `a prompt that leaves less than an ordinary answer is refused`() {
        assertNull(reply(prompt = 4096 - 149))
        assertNull(reply(prompt = 5000))
    }

    @Test
    fun `a small cap is never raised`() {
        assertEquals(20, reply(prompt = 1000, requested = 20))
        assertEquals(20, reply(prompt = 4076, requested = 20))
        assertNull(reply(prompt = 4077, requested = 20))
    }
}
