package com.example.sightbuddy.core.stt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SttModelHashesTest {

    @Test
    fun multilingualManifestIsNotLegacy() {
        assertEquals("base-encoder.int8.onnx", SttModelManager.ENCODER)
        assertEquals("0b8fb1304b6109976038efff5ace81720e00386f3ff6b54ee8c75291ca0a1e11", SttModelManager.FILES.getValue(SttModelManager.ENCODER).sha256)
    }

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
