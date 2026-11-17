package com.example.sightbuddy.core.llm

/** Immutable, pinned metadata; selecting an entry never downloads it. */
data class LocalModelSpec(
    val id: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val url: String,
    val experimental: Boolean = false,
)

object LocalModelCatalog {
    val E2B = LocalModelSpec(
        id = "e2b",
        fileName = "gemma-4-E2B-it.litertlm",
        sizeBytes = 2_588_147_712L,
        sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
        url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/6e5c4f1e395deb959c494953478fa5cec4b8008f/gemma-4-E2B-it.litertlm",
    )
    val E4B = LocalModelSpec(
        id = "e4b",
        fileName = "gemma-4-E4B-it.litertlm",
        sizeBytes = 3_659_530_240L,
        sha256 = "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0",
        url = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/f254b5ba6e36f7d89cc7892e268e9877bc20f0a0/gemma-4-E4B-it.litertlm",
        experimental = true,
    )
    val all = listOf(E2B, E4B)
    fun fromId(id: String?): LocalModelSpec? = if (id == null) E2B else all.firstOrNull { it.id == id }
}
