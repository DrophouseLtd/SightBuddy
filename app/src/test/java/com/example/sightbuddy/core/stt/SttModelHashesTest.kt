package com.example.sightbuddy.core.stt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SttModelHashesTest {

    @Test
    fun everyWhisperFileHasAPinnedSha256() {
        val files = SttModelManager.FILES
        assertEquals(
            setOf(SttModelManager.ENCODER, SttModelManager.DECODER, SttModelManager.TOKENS, SttModelManager.VAD),
            files.keys,
        )
        for ((name, pinned) in files) {
            assertTrue("$name: ${pinned.sha256}", pinned.sha256.matches(Regex("[0-9a-f]{64}")))
        }
        assertEquals(files.size, files.values.map { it.sha256 }.toSet().size)
    }
}
