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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sightbuddy.features.chat.OcrDebug
import kotlinx.coroutines.delay
import android.graphics.Paint

/**
 * Dev builds only: the text recogniser's view of the camera, drawn over it.
 *
 * Green boxes: each block of text found, labelled with its median line height
 * in frame pixels. White frame: the whole analysed frame.
 *
 * The preview is switched to fit-centre in dev builds so the frame drawn here
 * is the frame the recogniser sees. Invisible to TalkBack and to touch.
 */
@Composable
fun OcrDebugOverlay(modifier: Modifier = Modifier) {
    val frame by OcrDebug.frame.collectAsState()
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
            fun rect(l: Float, t: Float, r: Float, b: Float, color: Color, width: Float) =
                drawRect(
                    color = color,
                    topLeft = Offset(x(l), y(t)),
                    size = Size(x(r) - x(l), y(b) - y(t)),
                    style = Stroke(width = width),
                )

            rect(0f, 0f, 1f, 1f, Color.White, 2f)
            val color = Color(0xFF00E676)
            f.blocks.forEach { b ->
                drawRect(
                    color = color.copy(alpha = 0.15f),
                    topLeft = Offset(x(b.left), y(b.top)),
                    size = Size(x(b.right) - x(b.left), y(b.bottom) - y(b.top)),
                )
                rect(b.left, b.top, b.right, b.bottom, color, 3f)
                label("${b.lineHeightPx}px", x(b.left) + 4f, y(b.top) - 6f, android.graphics.Color.GREEN)
            }
        }

        val hud = if (f == null) {
            "OCR debug: no frame yet"
        } else {
            buildString {
                append("src   ").append(f.source)
                append("   age ").append(now.longValue - f.atMs).appendLine(" ms")
                append("frame ").append(f.widthPx).append('x').append(f.heightPx)
                append("   ocr ").append(f.ocrMs).appendLine(" ms")
                append("blocks ").append(f.blocks.size)
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
