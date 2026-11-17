package com.example.sightbuddy.core.llm

import org.junit.Assert.*
import org.junit.Test

class LocalModelCatalogTest {
    @Test fun e2bIsPinnedAndOnlyCatalogEntry() {
        assertEquals(LocalModelCatalog.E2B, LocalModelCatalog.fromId(null))
        assertNull(LocalModelCatalog.fromId("unknown"))
        assertEquals(listOf(LocalModelCatalog.E2B), LocalModelCatalog.all)
        assertTrue(LocalModelCatalog.all.size == 1)
    }

    @Test fun pinnedModelHasStableMetadataAndLocalizedName() {
        val e2b = LocalModelCatalog.E2B
        assertEquals("gemma-4-E2B-it.litertlm", e2b.fileName)
        assertTrue(e2b.url.contains("6e5c4f1e395deb959c494953478fa5cec4b8008f"))
        assertEquals("181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c", e2b.sha256)
        assertEquals(2588147712L, e2b.sizeBytes)
        assertTrue(e2b.nameRes != 0)
        assertFalse(e2b.experimental)
    }
}
