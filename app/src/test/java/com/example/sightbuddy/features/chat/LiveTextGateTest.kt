package com.example.sightbuddy.features.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Live text: the text in view is read out and drawn as boxes, Preview on or
 * off. With Preview off it was once not read out at all.
 */
class LiveTextGateTest {

    private fun gate(
        idle: Boolean = true,
        settingOn: Boolean = true,
        chatStarted: Boolean = false,
    ) = LiveTextGate(idle = idle, settingOn = settingOn, chatStarted = chatStarted)

    @Test
    fun `the text in view is read out and drawn, preview on or off`() {
        // The user, 2026-10-31: with Preview off too, the boxes are on screen,
        // so a screen reader can find a box and select it.
        val g = gate()
        assertTrue(g.reads)
        assertTrue(g.draws)
    }

    @Test
    fun `once the chat has started it stops`() {
        val g = gate(chatStarted = true)
        assertFalse(g.reads)
        assertFalse(g.draws)
    }

    @Test
    fun `the setting off, nothing is read or drawn`() {
        val g = gate(settingOn = false)
        assertFalse(g.reads)
        assertFalse(g.draws)
    }

    @Test
    fun `while Text chat is busy, nothing is read or drawn`() {
        val g = gate(idle = false)
        assertFalse(g.reads)
        assertFalse(g.draws)
    }
}
