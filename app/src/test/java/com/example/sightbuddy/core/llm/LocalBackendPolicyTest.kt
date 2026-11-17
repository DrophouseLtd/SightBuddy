package com.example.sightbuddy.core.llm

import com.google.ai.edge.litertlm.Backend
import org.junit.Assert.*
import org.junit.Test

class LocalBackendPolicyTest {
    @Test fun cpuFallbackDoesNotRetainGpuVisionDependency() {
        val attempts = localBackendAttempts()
        assertEquals(2, attempts.size)
        assertTrue(attempts[0].first is Backend.GPU)
        assertTrue(attempts[0].second is Backend.GPU)
        assertTrue(attempts[1].first is Backend.CPU)
        assertTrue(attempts[1].second is Backend.CPU)
    }
}
