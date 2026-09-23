package com.example.sightbuddy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sightbuddy.R
import com.example.sightbuddy.features.chat.LiveText
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Text chat's live text, in the manner of Google Lens: each block of text in
 * view drawn over the camera where it sits, on a filled box so it reads over
 * any picture, each its own item for TalkBack. Boxes may grow past the printed
 * area to fit the text.
 *
 * Touching a live box pins it ([onPin]): it stays where it is, solid, and the
 * other boxes fade back, so it stands out without any outline. The rest stays
 * live; boxes that would overlap it are left out, so it masks them for the eye
 * and for TalkBack alike. Touching the area around the boxes lets it go
 * ([onResume]; "Live text" for TalkBack, to be double-tapped while a box is
 * pinned).
 *
 * Touches are acted on at once, not on a completed tap: TalkBack still passes
 * a touch on as a hover, so a single tap works with it too; its double tap
 * also works.
 *
 * Each box is keyed by its text, so it stays the same item for TalkBack as it
 * moves, when it is pinned and when it is let go: TalkBack keeps its place.
 * Each also has a selection area of its own, which goes with it, so no
 * selection can be left pointing at a box that is gone, which crashes Compose.
 *
 * The camera preview fills the screen and crops the frame's sides, so blocks
 * are placed the same way. The overlay covers only the band between the corner
 * buttons ([topReserve]) and the text bar and buttons ([bottomReserve]), so it
 * never takes their touches; blocks outside it are left out.
 */
@Composable
fun LiveTextOverlay(
    live: LiveText?,
    pinned: LiveText?,
    topReserve: Dp,
    bottomReserve: Dp,
    modifier: Modifier = Modifier,
    onPin: (LiveText.Block) -> Unit = {},
    onResume: () -> Unit = {},
) {
    val density = LocalDensity.current
    val areaLabel = stringResource(R.string.live_text_area)
    BoxWithConstraints(modifier.fillMaxSize()) {
        val view = ViewBand(
            width = constraints.maxWidth.toFloat(),
            height = constraints.maxHeight.toFloat(),
            top = with(density) { topReserve.toPx() },
            bottom = constraints.maxHeight - with(density) { bottomReserve.toPx() },
        )
        val band = Modifier.padding(top = topReserve, bottom = bottomReserve)
        val pinnedBlock = pinned?.blocks?.firstOrNull()
        val pinnedBox = pinnedBlock?.let { view.place(it, pinned) }

        // Around the boxes: while one is pinned, touching it lets it go. It is
        // there throughout, so TalkBack, on it when it lets go, keeps its place.
        // TalkBack reads it as "Live text", with "Double-tap to activate" only
        // while there is something to let go; when live, there is nothing to do.
        val resume by rememberUpdatedState { if (pinnedBox != null) onResume() }
        val canResume = pinnedBox != null
        Box(
            band
                .fillMaxSize()
                .onAnyTouch { resume() }
                // Takes the taps, as a button would, without being one for TalkBack.
                .pointerInput(Unit) { detectTapGestures { } }
                .semantics {
                    contentDescription = areaLabel
                    if (canResume) onClick { resume(); true }
                },
        )

        // Live boxes first, the pinned one last so it is drawn on top.
        val boxes = buildList {
            val pinnedKey = pinnedBlock?.let { keyOf(it.text) }
            val keys = mutableSetOf<String>()
            if (live != null) {
                for (block in live.blocks) {
                    val key = keyOf(block.text)
                    if (key == pinnedKey || !keys.add(key)) continue
                    val box = view.place(block, live) ?: continue
                    if (pinnedBox != null && box.overlaps(pinnedBox)) continue
                    add(Placed(key, block, live, box, pinned = false))
                }
            }
            if (pinnedKey != null && pinnedBox != null) {
                add(Placed(pinnedKey, pinnedBlock, pinned, pinnedBox, pinned = true))
            }
        }

        Box(band.fillMaxSize()) {
            for (placed in boxes) key(placed.key) {
                // Read when touched, so a box that has moved since pins where it is now.
                val block by rememberUpdatedState(placed.block)
                Box(
                    Modifier
                        .placedAt(placed.box, view, density)
                        .then(
                            if (placed.pinned) Modifier
                            else Modifier.onAnyTouch { onPin(block) }.clickable { onPin(block) }
                        ),
                ) {
                    SelectionContainer {
                        BlockText(
                            block = placed.block,
                            view = view,
                            frame = placed.frame,
                            density = density,
                            look = when {
                                placed.pinned -> Look.PINNED
                                pinnedBox != null -> Look.FADED
                                else -> Look.LIVE
                            },
                        )
                    }
                }
            }
        }
    }
}

private class Placed(val key: String, val block: LiveText.Block, val frame: LiveText, val box: Rect, val pinned: Boolean)

/** A box's identity: its words, so it stays the same item while its text does. */
private fun keyOf(text: String): String = text.lowercase().filter { it.isLetterOrDigit() }

/**
 * Acts at the first touch, or TalkBack's hover over it, before any tap
 * completes. The handler is set up once, so [action] must read the latest
 * state itself (see rememberUpdatedState at the call sites).
 */
private fun Modifier.onAnyTouch(action: () -> Unit): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial)
            action()
        }
    }
}

/** The view, and the band of it the live text may use, in pixels. */
private class ViewBand(val width: Float, val height: Float, val top: Float, val bottom: Float) {
    /** Where [block] lands on screen, or null when it falls outside the band. */
    fun place(block: LiveText.Block, frame: LiveText): Rect? {
        val frameW = frame.frameWidthPx.toFloat()
        val frameH = frame.frameHeightPx.toFloat()
        // The preview fills the view and crops the rest, centred.
        val scale = scaleOf(frame)
        val offsetX = (width - frameW * scale) / 2f
        val offsetY = (height - frameH * scale) / 2f
        val rect = Rect(
            left = (offsetX + block.left * frameW * scale).coerceAtLeast(0f),
            top = offsetY + block.top * frameH * scale,
            right = (offsetX + block.right * frameW * scale).coerceAtMost(width),
            bottom = offsetY + block.bottom * frameH * scale,
        )
        return rect.takeIf { it.top >= top && it.bottom <= bottom && it.width >= MIN_WIDTH_PX }
    }

    fun scaleOf(frame: LiveText): Float =
        max(width / frame.frameWidthPx, height / frame.frameHeightPx)
}

/** Puts a box where [box] is on screen, within the live band. */
private fun Modifier.placedAt(box: Rect, view: ViewBand, density: Density): Modifier =
    offset { IntOffset(box.left.roundToInt(), (box.top - view.top).roundToInt()) }
        .width(with(density) { box.width.toDp() })

/** How a box is drawn: live, pinned, or faded back behind a pinned one. */
private enum class Look(val background: Float, val ink: Float) {
    LIVE(background = 0.75f, ink = 1f),
    PINNED(background = 1f, ink = 1f),
    FADED(background = 0.35f, ink = 0.55f),
}

@Composable
private fun BlockText(block: LiveText.Block, view: ViewBand, frame: LiveText, density: Density, look: Look) {
    // About the printed size, kept readable.
    val fontSp = with(density) { (block.lineHeightPx * view.scaleOf(frame) * GLYPH_SHARE).toDp().toSp().value }
        .coerceIn(MIN_FONT_SP, MAX_FONT_SP)
    Text(
        text = block.text,
        color = Color.Black.copy(alpha = look.ink),
        fontSize = fontSp.sp,
        lineHeight = (fontSp * 1.2f).sp,
        modifier = Modifier
            .background(Color.White.copy(alpha = look.background), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

/** Share of a line's box the letters take up. */
private const val GLYPH_SHARE = 0.7f
private const val MIN_FONT_SP = 12f
private const val MAX_FONT_SP = 24f
/** Narrower than this on screen, a block cannot show its text. */
private const val MIN_WIDTH_PX = 48f
