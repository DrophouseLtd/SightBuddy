package com.example.sightbuddy.features.vision

import com.example.sightbuddy.core.OpenAiTransport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class CocoPolishTest {
    @Test fun polishNamesAndAliasesResolveWithoutChangingEnglishIdentifiers() {
        val old = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("pl"))
            val resolver = ObjectCommandResolver(OpenAiTransport(apiKeyProvider = { "" }))
            for ((spoken, expected) in listOf("książka" to "book", "książkę" to "book", "krzesło" to "chair", "telefon" to "cell phone")) {
                val result = runBlocking { resolver.resolve(spoken, llmEnabled = false) }
                assertTrue("$spoken: $result", result is ObjectCommandResolver.ResolveResult.Activate)
                assertEquals(expected, (result as ObjectCommandResolver.ResolveResult.Activate).item)
            }
            assertEquals("książka", CocoFinnish.displayName("book"))
            assertTrue(CocoPolish.aliases().isNotEmpty())
            Locale.setDefault(Locale.ENGLISH)
            assertTrue(CocoPolish.aliases().isEmpty())
            assertEquals("book", CocoFinnish.displayName("book"))
        } finally { Locale.setDefault(old) }
    }
    @Test fun allCocoLabelsHavePolishNames() {
        val old = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("pl"))
            assertTrue(COCO_OBJECTS.all { CocoPolish.names.containsKey(it) })
        } finally { Locale.setDefault(old) }
    }
}
