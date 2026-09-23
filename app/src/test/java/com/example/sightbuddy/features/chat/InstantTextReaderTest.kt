package com.example.sightbuddy.features.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstantTextReaderTest {

    @Test
    fun `new text is read once`() {
        val reader = InstantTextReader()
        assertTrue(reader.shouldRead("Fire exit, keep clear"))
        assertFalse(reader.shouldRead("Fire exit, keep clear"))
    }

    @Test
    fun `a slightly different reading of the same sign stays quiet`() {
        val reader = InstantTextReader()
        assertTrue(reader.shouldRead("Fire exit keep clear at all times"))
        assertFalse(reader.shouldRead("Fire exit keep clcar at all times"))
    }

    @Test
    fun `different text is read`() {
        val reader = InstantTextReader()
        assertTrue(reader.shouldRead("Fire exit, keep clear"))
        assertTrue(reader.shouldRead("Push to open"))
    }

    @Test
    fun `stray characters are ignored`() {
        assertFalse(InstantTextReader().shouldRead(" a "))
    }
}
