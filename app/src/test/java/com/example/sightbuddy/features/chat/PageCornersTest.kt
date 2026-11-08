package com.example.sightbuddy.features.chat

import com.example.sightbuddy.features.chat.PageCorners.Corner
import com.example.sightbuddy.features.chat.PageCorners.Corners
import com.example.sightbuddy.features.chat.PageCorners.INPUT_SIZE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageCornersTest {

    private fun corners(vararg xy: Float, confidence: Float = 0.8f, w: Int = 720, h: Int = 960) =
        Corners((0 until 4).map { Corner(xy[2 * it], xy[2 * it + 1], confidence) }, w, h)

    /** A page with a margin of [m] all round, corners in the model's order. */
    private fun page(m: Float = 0.1f, w: Int = 720, h: Int = 960) =
        corners(m, m, 1f - m, m, 1f - m, 1f - m, m, 1f - m, w = w, h = h)

    @Test
    fun `an upright frame is centred between black bars at the sides`() {
        assertEquals(PageCorners.Letterbox(720, 960, 168, 224, 28, 0), PageCorners.letterbox(720, 960))
        assertEquals(PageCorners.Letterbox(1440, 1920, 168, 224, 28, 0), PageCorners.letterbox(1440, 1920))
        assertEquals(PageCorners.Letterbox(960, 720, 224, 168, 0, 28), PageCorners.letterbox(960, 720))
    }

    /**
     * A 720 x 960 test pattern, prepared as the Python reference prepares it
     * (OpenCV `INTER_LINEAR`, black padding, ImageNet normalising): the same
     * to float rounding.
     */
    @Test
    fun `the input matches the reference preparation`() {
        val w = 720
        val h = 960
        val pixels = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            val r = (x * 7 + y * 3) % 256
            val g = (x / 3 + y / 5) % 256
            val b = (x xor y) and 255
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        val input = PageCorners.input(pixels, PageCorners.letterbox(w, h))
        val plane = INPUT_SIZE * INPUT_SIZE
        fun at(c: Int, y: Int, x: Int) = input[c * plane + y * INPUT_SIZE + x]
        // Channel, row, column, and the reference's value.
        listOf(
            Triple(Triple(0, 0, 0), -2.117904f, "red in the left bar"),
            Triple(Triple(0, 0, 28), -1.843908f, "first column of the frame"),
            Triple(Triple(0, 100, 100), 0.176813f, "red inside"),
            Triple(Triple(1, 50, 60), -0.477591f, "green inside"),
            Triple(Triple(2, 223, 195), 0.165054f, "blue, last column of the frame"),
            Triple(Triple(2, 223, 196), -1.804444f, "blue in the right bar"),
            Triple(Triple(1, 112, 112), 1.745798f, "green in the middle"),
            Triple(Triple(0, 7, 31), 1.238548f, "red near the top"),
        ).forEach { (where, expected, what) ->
            val (c, y, x) = where
            assertEquals(what, expected, at(c, y, x), 1e-5f)
        }
        // Whole channels.
        val sums = (0 until 3).map { c -> (0 until plane).sumOf { input[c * plane + it].toDouble() } }
        assertEquals(-24189.448, sums[0], 0.01)
        assertEquals(-16876.232, sums[1], 0.01)
        assertEquals(-7281.068, sums[2], 0.01)
    }

    @Test
    fun `each corner is its heatmap's highest point, mapped back to the frame`() {
        val box = PageCorners.letterbox(720, 960)
        val heatmaps = FloatArray(PageCorners.OUTPUT_LENGTH) { 0.01f }
        val plane = INPUT_SIZE * INPUT_SIZE
        fun peak(k: Int, x: Int, y: Int, v: Float) { heatmaps[k * plane + y * INPUT_SIZE + x] = v }
        peak(PageCorners.TOP_LEFT, 28 + 42, 56, 0.9f)
        peak(PageCorners.TOP_RIGHT, 28 + 126, 56, 0.7f)
        peak(PageCorners.BOTTOM_RIGHT, 28 + 126, 168, 0.6f)
        // Out of view: a weak point in the black bar.
        peak(PageCorners.BOTTOM_LEFT, 10, 168, 0.15f)
        val c = PageCorners.decode(heatmaps, box)
        assertEquals(0.25f, c[0].x, 1e-6f)
        assertEquals(0.25f, c[0].y, 1e-6f)
        assertEquals(0.9f, c[0].confidence, 1e-6f)
        assertEquals(0.75f, c[1].x, 1e-6f)
        assertEquals(0.75f, c[2].y, 1e-6f)
        assertTrue(c[3].x < 0f)
        assertEquals(0.15f, c[3].confidence, 1e-6f)
        assertEquals(3, c.seenCount())
        assertFalse(c.allSeen())
        assertEquals(720, c.widthPx)
    }

    @Test
    fun `a flat heatmap gives its first point`() {
        val box = PageCorners.letterbox(720, 960)
        val c = PageCorners.decode(FloatArray(PageCorners.OUTPUT_LENGTH) { 0.05f }, box)
        assertEquals(-28f / 168f, c[0].x, 1e-6f)
        assertEquals(0f, c[0].y, 1e-6f)
    }

    @Test
    fun `the page's share of the frame`() {
        assertEquals(0.64f, PageCorners.areaShare(page(0.1f)), 1e-5f)
        assertEquals(1f, PageCorners.areaShare(page(0f)), 1e-5f)
        // A trapezium, as a page tilted away looks: top 0.4 wide, bottom 0.8, 0.8 tall.
        val tilted = corners(0.3f, 0.1f, 0.7f, 0.1f, 0.9f, 0.9f, 0.1f, 0.9f)
        assertEquals(0.48f, PageCorners.areaShare(tilted), 1e-5f)
    }

    @Test
    fun `movement is the furthest corner's, against the diagonal`() {
        val a = page(0.1f)
        assertEquals(0f, PageCorners.movement(a, a), 1e-6f)
        // 0.1 of 720 across and of 960 down: 72 and 96 px, 120 px of a 1200 px diagonal.
        val moved = a.copy(corners = a.corners.mapIndexed { i, c -> if (i == 2) c.copy(x = c.x + 0.1f, y = c.y + 0.1f) else c })
        assertEquals(0.1f, PageCorners.movement(a, moved), 1e-5f)
    }

    @Test
    fun `only corners going round clockwise make a page`() {
        assertTrue(PageCorners.isConvex(page()))
        // Top-right and bottom-right swapped: a bow tie.
        assertFalse(PageCorners.isConvex(corners(0.1f, 0.1f, 0.9f, 0.9f, 0.9f, 0.1f, 0.1f, 0.9f)))
        // Mirrored: the corners go the other way round.
        assertFalse(PageCorners.isConvex(corners(0.9f, 0.1f, 0.1f, 0.1f, 0.1f, 0.9f, 0.9f, 0.9f)))
        // All on one line.
        assertFalse(PageCorners.isConvex(corners(0.1f, 0.5f, 0.3f, 0.5f, 0.6f, 0.5f, 0.9f, 0.5f)))
    }

    @Test
    fun `the flattened page takes its longer edges`() {
        // On a 1440 x 1920 frame: top 0.4 wide (576 px), bottom 0.8 (1152 px), 0.8 tall with slanted sides.
        val tilted = corners(0.3f, 0.1f, 0.7f, 0.1f, 0.9f, 0.9f, 0.1f, 0.9f, w = 1440, h = 1920)
        val size = PageCorners.warpSize(tilted, 1440, 1920)
        assertNotNull(size)
        assertEquals(1152, size!!.width)
        // Side: 0.2 of 1440 across (288 px) and 0.8 of 1920 down (1536 px).
        assertEquals(1563, size.height)
    }

    @Test
    fun `the flattened page is never longer than the frame`() {
        // A long thin diagonal page: its long side is held to the frame's 1920.
        val diagonal = corners(0.0f, 0.05f, 0.05f, 0.0f, 1.0f, 0.95f, 0.95f, 1.0f, w = 1440, h = 1920)
        val size = PageCorners.warpSize(diagonal, 1440, 1920)!!
        assertTrue(size.height <= 1920 && size.width <= 1920)
        assertEquals(1920, maxOf(size.width, size.height))
    }

    @Test
    fun `a page too small to read on its own is not cropped to`() {
        val tiny = corners(0.50f, 0.50f, 0.52f, 0.50f, 0.52f, 0.52f, 0.50f, 0.52f, w = 1440, h = 1920)
        assertNull(PageCorners.warpSize(tiny, 1440, 1920))
    }

    @Test
    fun `the crop reaches a little past the corners, inside the frame`() {
        val grown = PageCorners.grown(page(0.1f), grow = 0.1f)
        assertEquals(0.06f, grown[0].x, 1e-5f)
        assertEquals(0.94f, grown[2].y, 1e-5f)
        val clamped = PageCorners.grown(page(0f), grow = 0.1f)
        assertEquals(0f, clamped[0].x, 0f)
        assertEquals(1f, clamped[2].x, 0f)
    }

    @Test
    fun `the log line gives the confidences and the area`() {
        assertEquals("conf=0.80,0.80,0.80,0.80 area=0.64", PageCorners.summary(page(0.1f)))
    }

    @Test
    fun `an outline wider than tall is an open book, and the half nearer the middle is kept`() {
        // The phone, 2026-10-31: both pages outlined as one, 576 by 384 px.
        val spread = corners(0.15f, 0.3f, 0.95f, 0.3f, 0.95f, 0.7f, 0.15f, 0.7f)
        val page = PageCorners.oneOfSpread(spread)
        val expected = listOf(0.15f to 0.3f, 0.55f to 0.3f, 0.55f to 0.7f, 0.15f to 0.7f)
        expected.forEachIndexed { k, (x, y) ->
            assertEquals(x, page[k].x, 1e-5f)
            assertEquals(y, page[k].y, 1e-5f)
        }
        assertTrue(page.allSeen())
        // The other side when that half is nearer the middle.
        val right = PageCorners.oneOfSpread(corners(0.05f, 0.3f, 0.85f, 0.3f, 0.85f, 0.7f, 0.05f, 0.7f))
        assertEquals(0.45f, right[PageCorners.TOP_LEFT].x, 1e-5f)
        assertEquals(0.85f, right[PageCorners.TOP_RIGHT].x, 1e-5f)
    }

    @Test
    fun `an open book in perspective, only a little wider than tall, is still a spread`() {
        // The phone, 2026-10-31: both pages read as about 1.1 times wider than tall.
        val spread = corners(0.02f, 0.35f, 0.92f, 0.35f, 0.92f, 0.95f, 0.02f, 0.95f)
        val page = PageCorners.oneOfSpread(spread)
        // The right half, nearer the middle.
        assertEquals(0.47f, page[PageCorners.TOP_LEFT].x, 1e-5f)
        assertEquals(0.92f, page[PageCorners.TOP_RIGHT].x, 1e-5f)
    }

    @Test
    fun `corners mixed from both pages of a book are not page-shaped`() {
        // The phone, 2026-10-31: the right page's top corners with the left
        // page's bottom-left; the bottom edge twice the top.
        assertFalse(PageCorners.pageShaped(corners(0.39f, 0.40f, 0.80f, 0.38f, 0.86f, 0.91f, 0.02f, 0.92f)))
        // A page seen at a slant, its far edge a little shorter, is one.
        assertTrue(PageCorners.pageShaped(corners(0.2f, 0.2f, 0.8f, 0.2f, 0.85f, 0.8f, 0.15f, 0.8f)))
    }

    @Test
    fun `an upright page, a nearly square one, or a page partly seen is kept whole`() {
        val upright = page(0.1f)
        assertEquals(upright, PageCorners.oneOfSpread(upright))
        // 432 by 480 px: under the spread's proportion.
        val square = corners(0.2f, 0.25f, 0.8f, 0.25f, 0.8f, 0.75f, 0.2f, 0.75f)
        assertEquals(square, PageCorners.oneOfSpread(square))
        val partly = Corners(
            listOf(Corner(0.15f, 0.3f, 0.8f), Corner(0.95f, 0.3f, 0.1f), Corner(0.95f, 0.7f, 0.8f), Corner(0.15f, 0.7f, 0.8f)),
            720,
            960,
        )
        assertEquals(partly, PageCorners.oneOfSpread(partly))
    }

    @Test
    fun `a capture of an open book is cropped to one page`() {
        val spread = corners(0.15f, 0.3f, 0.95f, 0.3f, 0.95f, 0.7f, 0.15f, 0.7f)
        val plan = PageCorners.cropPlan(spread, null)
        assertEquals(0.55f, plan.corners!![PageCorners.TOP_RIGHT].x, 1e-5f)
    }
}
