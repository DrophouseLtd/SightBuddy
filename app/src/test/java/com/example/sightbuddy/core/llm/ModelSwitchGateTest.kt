package com.example.sightbuddy.core.llm

import org.junit.Assert.*
import org.junit.Test

class ModelSwitchGateTest {
    @Test fun busyInferenceRefusesSwitchWithoutWaiting() {
        val gate = ModelGate()
        assertTrue(gate.tryEnter())
        assertFalse(gate.tryEnter())
        gate.leave()
        assertTrue(gate.tryEnter())
        gate.leave()
    }
}
