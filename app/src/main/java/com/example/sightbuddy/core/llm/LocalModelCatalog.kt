package com.example.sightbuddy.core.llm

import com.example.sightbuddy.R

/** Immutable, pinned metadata; selecting an entry never downloads it. */
data class LocalModelSpec(
    val id: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val url: String,
    val nameRes: Int,
    val experimental: Boolean = false,
)

object LocalModelCatalog {
    val E2B = LocalModelSpec(
        id = "e2b",
        fileName = "gemma-4-E2B-it.litertlm",
        sizeBytes = 2_588_147_712L,
        sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
        url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/6e5c4f1e395deb959c494953478fa5cec4b8008f/gemma-4-E2B-it.litertlm",
        nameRes = R.string.local_model_e2b,
    )
    // The larger experimental E4B entry was removed after a Polish pilot showed
    // no quality advantage (slower, larger); the selection infrastructure stays,
    // so another pinned model can be added here without further code changes.
    val all = listOf(E2B)
    fun fromId(id: String?): LocalModelSpec? = if (id == null) E2B else all.firstOrNull { it.id == id }
}
