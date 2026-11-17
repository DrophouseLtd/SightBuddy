package com.example.sightbuddy.core.llm

import org.junit.Assert.*
import org.junit.Test

class LocalModelCatalogTest {
    @Test fun e2bIsDefaultAndUnknownSelectionDoesNotChooseE4b() {
        assertEquals(LocalModelCatalog.E2B, LocalModelCatalog.fromId(null))
        assertNull(LocalModelCatalog.fromId("unknown"))
    }

    @Test fun pinnedModelsAreDistinctAndHaveIndependentFiles() {
        val e2b = LocalModelCatalog.E2B
        val e4b = LocalModelCatalog.E4B
        assertNotEquals(e2b.fileName, e4b.fileName)
        assertTrue(e2b.url.contains("6e5c4f1e395deb959c494953478fa5cec4b8008f"))
        assertEquals("181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c", e2b.sha256)
        assertEquals("gemma-4-E4B-it.litertlm", e4b.fileName)
        assertEquals(3659530240L, e4b.sizeBytes)
        assertEquals("0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0", e4b.sha256)
        assertTrue(e4b.url.contains("f254b5ba6e36f7d89cc7892e268e9877bc20f0a0"))
        assertTrue(e4b.experimental)
    }
}
