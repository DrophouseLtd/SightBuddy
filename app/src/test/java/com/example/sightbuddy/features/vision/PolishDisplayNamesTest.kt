package com.example.sightbuddy.features.vision

import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class PolishDisplayNamesTest {
    private fun inLanguage(tag: String, block: () -> Unit) {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag(tag))
            block()
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test fun polishNamesDoNotChangeEnglishFinnishOrUnknownIdentifiers() {
        inLanguage("pl") {
            assertEquals("kubek", CocoFinnish.displayName("cup"))
            assertEquals("mysz komputerowa", CocoFinnish.displayName("mouse"))
            assertEquals("unknown-detector-label", CocoFinnish.displayName("unknown-detector-label"))
            // Only the displayed words change; no new voice resolver is introduced.
            assertTrue(CocoFinnish.aliases().isEmpty())
        }
        inLanguage("en") { assertEquals("cup", CocoFinnish.displayName("cup")) }
        inLanguage("fi") { assertEquals("kuppi", CocoFinnish.displayName("cup")) }
    }

    @Test fun allExistingDetectorNamesHavePolishDisplayWords() {
        val field = CocoFinnish::class.java.getDeclaredField("NAMES")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val originalNames = field.get(CocoFinnish) as Map<String, String>
        assertEquals(80, originalNames.size)
        val polishField = PolishObjectNames::class.java.getDeclaredField("names")
        polishField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val polishNames = polishField.get(PolishObjectNames) as Map<String, String>
        assertEquals(originalNames.keys, polishNames.keys)
        val unchangedPolishWords = setOf("frisbee", "hot dog", "pizza", "zebra", "laptop")
        inLanguage("pl") {
            for (id in originalNames.keys) {
                val display = CocoFinnish.displayName(id)
                assertTrue(id, display.isNotBlank())
                if (id !in unchangedPolishWords) assertNotEquals(id, id, display)
            }
        }
    }
}
