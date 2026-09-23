package com.example.sightbuddy.features.chat

import com.example.sightbuddy.features.chat.LocalTextExtractor.TextFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaperFinderTest {

    private val w = PaperFinder.WIDTH
    private val h = PaperFinder.HEIGHT

    /** A dark table with a white sheet from ([x0], [y0]) to ([x1], [y1]) in pixels, printed with dark lines. */
    private fun scene(x0: Int, y0: Int, x1: Int, y1: Int): IntArray = IntArray(w * h) { i ->
        val x = i % w
        val y = i / w
        val onSheet = x in x0 until x1 && y in y0 until y1
        val printed = onSheet && y % 6 == 0 && x in (x0 + 5) until (x1 - 5)
        when {
            printed -> 40
            onSheet -> 230
            else -> 60
        }
    }

    @Test
    fun `a sheet on a dark table is found through its print`() {
        val paper = PaperFinder.find(scene(30, 40, 90, 130), w, h)
        assertNotNull(paper)
        assertEquals(30f / w, paper!!.left, 0.01f)
        assertEquals(90f / w, paper.right, 0.01f)
        assertEquals(40f / h, paper.top, 0.01f)
        assertEquals(130f / h, paper.bottom, 0.01f)
        assertTrue(paper.solidity > 0.8f)
    }

    private val frameAspect = 1440f / 1920f

    /** An upright A4 sheet: 0.5 of the frame wide, 0.53 high, 1.41 times taller than wide. */
    private val sheet = PaperFinder.Paper(0.25f, 0.2f, 0.75f, 0.73f, share = 0.25f, solidity = 0.9f)

    private fun text(l: Float, t: Float, r: Float, b: Float, lineH: Int) =
        PageTracker.Block(l, t, r, b, chars = 60, lineHeightPx = lineH, text = "Koolla on väliä pienet yksiöt")

    @Test
    fun `an upright A4 sheet comes before the text, pictures and margins included`() {
        val textPage = TextFrame(0.3f, 0.3f, 0.6f, 0.5f, chars = 200, lineHeightPx = 30)
        val choice = PaperFinder.choose(textPage, sheet, frameAspect, listOf(text(0.3f, 0.3f, 0.6f, 0.5f, lineH = 30)))
        assertTrue(choice.paperUsed)
        assertEquals(0.25f, choice.target!!.left)
        assertEquals(30, choice.target!!.lineHeightPx)
    }

    @Test
    fun `readability comes from the text on the sheet, not beside it`() {
        val choice = PaperFinder.choose(
            null, sheet, frameAspect,
            listOf(text(0.3f, 0.3f, 0.6f, 0.4f, lineH = 12), text(0.8f, 0.3f, 0.95f, 0.4f, lineH = 40)),
        )
        assertEquals(12, choice.target!!.lineHeightPx)
        assertEquals(TextAimGuide.Direction.CLOSER, TextAimGuide.directionFor(choice.target!!))
    }

    @Test
    fun `a sheet with no readable text yet needs the phone closer`() {
        val choice = PaperFinder.choose(null, sheet, frameAspect, emptyList())
        assertTrue(choice.paperUsed)
        assertEquals(TextAimGuide.Direction.CLOSER, TextAimGuide.directionFor(choice.target!!))
    }

    @Test
    fun `a sheet running off one side is still used, to say which way`() {
        val offLeft = PaperFinder.Paper(0.0f, 0.2f, 0.45f, 0.67f, share = 0.2f, solidity = 0.9f)
        val choice = PaperFinder.choose(null, offLeft, frameAspect, emptyList())
        assertTrue(choice.paperUsed)
        assertEquals(TextAimGuide.Direction.LEFT, TextAimGuide.directionFor(choice.target!!))
    }

    @Test
    fun `paper across the whole view is left to the text`() {
        val textPage = TextFrame(0.3f, 0.3f, 0.6f, 0.5f, chars = 200, lineHeightPx = 30)
        val close = sheet.copy(left = 0f, right = 1f)
        val choice = PaperFinder.choose(textPage, close, frameAspect, emptyList())
        assertFalse(choice.paperUsed)
        assertEquals(textPage, choice.target)
    }

    @Test
    fun `a spread, a square and a pale floor are not sheets`() {
        val spread = PaperFinder.Paper(0.05f, 0.35f, 0.95f, 0.65f, share = 0.25f, solidity = 0.9f)
        val square = PaperFinder.Paper(0.2f, 0.3f, 0.8f, 0.75f, share = 0.25f, solidity = 0.9f)
        assertFalse(PaperFinder.isSheet(spread, frameAspect))
        assertFalse(PaperFinder.isSheet(square, frameAspect))
        assertFalse(PaperFinder.isSheet(sheet.copy(solidity = 0.4f), frameAspect))
        assertTrue(PaperFinder.isSheet(sheet, frameAspect))
    }
}
