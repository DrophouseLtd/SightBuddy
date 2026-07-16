package com.example.sightbuddy.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextScriptPlaybackTest {

    @Test
    fun seekBackIndex_clampsToZero() {
        assertEquals(0, TextScriptPlayback.seekBackIndex(3, 20))
        assertEquals(0, TextScriptPlayback.seekBackIndex(0, 20))
        assertEquals(5, TextScriptPlayback.seekBackIndex(25, 20))
    }

    @Test
    fun seekForward_atEndIsNoOp() {
        val result = TextScriptPlayback.seekForward(10, 10, 20)
        assertTrue(result is TextScriptPlayback.SeekForwardResult.NoOp)
    }

    @Test
    fun seekForward_nearEndMarksAtEnd() {
        val result = TextScriptPlayback.seekForward(8, 10, 20)
        assertTrue(result is TextScriptPlayback.SeekForwardResult.AtEnd)
    }

    @Test
    fun seekForward_advancesIndex() {
        val result = TextScriptPlayback.seekForward(0, 100, 20)
        assertEquals(20, (result as TextScriptPlayback.SeekForwardResult.PlayFrom).index)
    }

    @Test
    fun indexForResume_rewindsFromEnd() {
        assertEquals(95, TextScriptPlayback.indexForResume(100, 100, 5))
    }

    @Test
    fun indexForResume_rewindsFromMiddle() {
        assertEquals(15, TextScriptPlayback.indexForResume(20, 100, 5))
    }

    @Test
    fun indexForResume_clampsAtZero() {
        assertEquals(0, TextScriptPlayback.indexForResume(2, 100, 5))
    }

    @Test
    fun absolutePosition_mapsSegmentOffsetIntoScript() {
        assertEquals(25, TextScriptPlayback.absolutePosition(20, 5, 100))
        assertEquals(100, TextScriptPlayback.absolutePosition(95, 20, 100))
        assertEquals(0, TextScriptPlayback.absolutePosition(0, -5, 100))
    }
}
