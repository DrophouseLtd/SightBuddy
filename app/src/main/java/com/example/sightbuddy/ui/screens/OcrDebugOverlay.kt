package com.example.sightbuddy.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sightbuddy.features.chat.OcrDebug
import com.example.sightbuddy.features.chat.TextAimGuide
import kotlinx.coroutines.delay
import android.graphics.Paint

/**
 * Dev builds only: the text recogniser's view of the camera, drawn over it.
 *
 * - Green boxes: the page the tracker chose; blue: other text it kept; grey:
 *   blocks it dropped as not text. Each is labelled with its median line
 *   height in frame pixels, red below [TextAimGuide.MIN_LINE_PX].
 * - Cyan: the paper found by brightness, solid when it is sheet-shaped and
 *   the guide steers by it, dashed when the text pages decide.
 * - Magenta: every text page found. Red box: what the guide steers by, the
 *   sheet or the text page, dashed while held through a frame that missed it.
 * - Yellow: the centred zone to enter, dashed the one to leave. Orange dashes:
 *   the edge margin, text inside which counts as running off that edge. White
 *   frame: the whole analysed frame. Read and capture frames are drawn white.
 *
 * The preview is switched to fit-centre in dev builds so the frame drawn here
 * is the frame the recogniser sees. Invisible to TalkBack and to touch.
 */
@Composable
fun OcrDebugOverlay(modifier: Modifier = Modifier) {
    val frame by OcrDebug.frame.collectAsState()
    val guide by OcrDebug.guide.collectAsState()
    // Ticks so the age of the last frame stays current.
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(250)
            now.longValue = System.currentTimeMillis()
        }
    }

    Box(modifier.clearAndSetSemantics { }) {
        val f = frame
        val track = guide?.track
        Canvas(Modifier.fillMaxSize()) {
            if (f == null) return@Canvas
            // Fit the analysed frame into the view, as the fit-centre preview does.
            val scale = minOf(size.width / f.widthPx, size.height / f.heightPx)
            val fw = f.widthPx * scale
            val fh = f.heightPx * scale
            val ox = (size.width - fw) / 2f
            val oy = (size.height - fh) / 2f
            fun x(v: Float) = ox + v * fw
            fun y(v: Float) = oy + v * fh
            fun rect(l: Float, t: Float, r: Float, b: Float, color: Color, width: Float, dashed: Boolean = false) =
                drawRect(
                    color = color,
                    topLeft = Offset(x(l), y(t)),
                    size = Size(x(r) - x(l), y(b) - y(t)),
                    style = Stroke(
                        width = width,
                        pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(12f, 8f)) else null,
                    ),
                )

            rect(0f, 0f, 1f, 1f, Color.White, 2f)
            val m = TextAimGuide.EDGE_MARGIN
            rect(m, m, 1f - m, 1f - m, Color(0xFFFF9800), 2f, dashed = true)
            val c = TextAimGuide.CENTRE_ENTER
            rect(0.5f - c, 0.5f - c, 0.5f + c, 0.5f + c, Color.Yellow, 2f)
            val cl = TextAimGuide.CENTRE_LEAVE
            rect(0.5f - cl, 0.5f - cl, 0.5f + cl, 0.5f + cl, Color.Yellow, 2f, dashed = true)
            drawLine(Color.Yellow, Offset(x(0.48f), y(0.5f)), Offset(x(0.52f), y(0.5f)), 2f)
            drawLine(Color.Yellow, Offset(x(0.5f), y(0.48f)), Offset(x(0.5f), y(0.52f)), 2f)

            f.blocks.forEachIndexed { i, b ->
                val color = when {
                    track == null -> Color.White
                    i in track.chosen -> Color(0xFF00E676)
                    i in track.kept -> Color(0xFF40C4FF)
                    else -> Color.Gray
                }
                drawRect(
                    color = color.copy(alpha = 0.15f),
                    topLeft = Offset(x(b.left), y(b.top)),
                    size = Size(x(b.right) - x(b.left), y(b.bottom) - y(b.top)),
                )
                rect(b.left, b.top, b.right, b.bottom, color, 3f)
                label(
                    "${b.lineHeightPx}px",
                    x(b.left) + 4f,
                    y(b.top) - 6f,
                    if (b.lineHeightPx < TextAimGuide.MIN_LINE_PX) android.graphics.Color.RED else android.graphics.Color.GREEN,
                )
            }

            track?.pages?.forEach { p -> rect(p.left, p.top, p.right, p.bottom, Color.Magenta, 3f) }
            guide?.paper?.let { p -> rect(p.left, p.top, p.right, p.bottom, Color.Cyan, 5f, dashed = !guide!!.paperUsed) }
            guide?.target?.let { u ->
                rect(u.left, u.top, u.right, u.bottom, Color.Red, 6f, dashed = track?.held == true && guide?.paperUsed != true)
                drawCircle(Color.Red, 10f, Offset(x((u.left + u.right) / 2f), y((u.top + u.bottom) / 2f)))
            }
        }

        val hud = if (f == null) {
            "OCR debug: no frame yet"
        } else {
            val chosen = f.blocks.filterIndexed { i, _ -> track != null && i in track.chosen }
            val line = guide?.target?.lineHeightPx?.let { "${it}px" } ?: "-"
            buildString {
                append("step  ").append(guide?.step ?: "-")
                if (track?.held == true) append(" (held)")
                if (guide?.paperUsed == true) append(" paper")
                appendLine()
                append("src   ").append(f.source)
                append("   age ").append(now.longValue - f.atMs).appendLine(" ms")
                append("frame ").append(f.widthPx).append('x').append(f.heightPx)
                append("   ocr ").append(f.ocrMs).appendLine(" ms")
                append("page/kept/all ").append(chosen.size).append('/')
                append(track?.kept?.size ?: 0).append('/').append(f.blocks.size)
                append("   line ").append(line)
            }
        }
        Text(
            text = hud,
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 72.dp)
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(6.dp),
        )
    }
}

private fun DrawScope.label(text: String, x: Float, y: Float, color: Int) {
    val paint = Paint().apply {
        this.color = color
        textSize = 28f
        isAntiAlias = true
        setShadowLayer(3f, 0f, 0f, android.graphics.Color.BLACK)
    }
    drawContext.canvas.nativeCanvas.drawText(text, x, y, paint)
}
