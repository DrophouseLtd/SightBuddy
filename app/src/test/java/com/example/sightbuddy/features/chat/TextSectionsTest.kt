package com.example.sightbuddy.features.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class TextSectionsTest {

    @Test
    fun `each block is its own paragraph`() {
        val text = TextSections.join(
            listOf(
                listOf("Koolla on väliä"),
                listOf("Niin sanotusti varma nakki", "ja pieni riski."),
            )
        )
        assertEquals("Koolla on väliä\n\nNiin sanotusti varma nakki ja pieni riski.", text)
    }

    @Test
    fun `a word broken at the line end is joined`() {
        assertEquals("Pirkanmaan aluepäällikkömme", TextSections.paragraph(listOf("Pirkanmaan alue-", "päällikkömme")))
    }

    @Test
    fun `a hyphen before a capital or a number stays`() {
        assertEquals("EU- Suomi", TextSections.paragraph(listOf("EU-", "Suomi")))
        assertEquals("vuosina 2020- 2025", TextSections.paragraph(listOf("vuosina 2020-", "2025")))
    }

    @Test
    fun `empty blocks and lines are skipped`() {
        assertEquals("Yksi\n\nKaksi", TextSections.join(listOf(listOf("Yksi", "  "), emptyList(), listOf("Kaksi"))))
    }
}
