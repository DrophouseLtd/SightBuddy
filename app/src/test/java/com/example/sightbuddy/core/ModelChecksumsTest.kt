package com.example.sightbuddy.core

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.MessageDigest

class ModelChecksumsTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun emptyInputHasTheKnownHash() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            ModelChecksums.sha256(ByteArrayInputStream(ByteArray(0))),
        )
    }

    @Test
    fun abcHasTheKnownHash() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            ModelChecksums.sha256(ByteArrayInputStream("abc".toByteArray())),
        )
    }

    /** A stream made up as it is read, a few bytes at a time, never held whole. */
    private class Generated(private val size: Int) : InputStream() {
        private var at = 0
        override fun read(): Int = if (at >= size) -1 else (at++ * 31 and 0xff)
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (at >= size) return -1
            // Short reads, as a network or file stream gives them.
            val n = minOf(len, 7_919, size - at)
            for (i in 0 until n) b[off + i] = (at++ * 31 and 0xff).toByte()
            return n
        }
    }

    @Test
    fun aStreamLongerThanAnyBufferIsHashedWhole() {
        val size = 3 * 1024 * 1024 + 17
        val whole = ByteArray(size) { (it * 31 and 0xff).toByte() }
        val expected = MessageDigest.getInstance("SHA-256").digest(whole).joinToString("") { "%02x".format(it) }
        assertEquals(expected, ModelChecksums.sha256(Generated(size)))
    }

    @Test
    fun aFileIsHashedWhole() {
        val file = temp.newFile("model.bin").apply { writeText("abc") }
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", ModelChecksums.sha256(file))
    }
}
