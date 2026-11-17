package com.example.sightbuddy.core.llm

import com.google.ai.edge.litertlm.Backend

/** Vision CPU is supported by the LiteRT-LM 0.17.1 executor, not just LLM CPU. */
internal fun localBackendAttempts(): List<Pair<Backend, Backend>> = listOf(
    Backend.GPU() to Backend.GPU(),
    Backend.CPU() to Backend.CPU(),
)
