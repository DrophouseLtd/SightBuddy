package com.example.sightbuddy.core.llm

import org.junit.Assert.assertTrue
import org.junit.Test

class GemmaModelHashTest {

    @Test
    fun theModelHasAPinnedSha256() {
        assertTrue(LocalGemma.E2B_SHA256, LocalGemma.E2B_SHA256.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun theUrlIsPinnedToOneRevision() {
        // A branch name would let the file change under the same URL.
        assertTrue(LocalGemma.E2B_URL, LocalGemma.E2B_URL.contains(Regex("/resolve/[0-9a-f]{40}/")))
    }
}
