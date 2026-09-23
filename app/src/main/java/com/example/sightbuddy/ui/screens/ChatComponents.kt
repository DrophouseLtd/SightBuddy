package com.example.sightbuddy.ui.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.sightbuddy.ui.theme.BrandPalette
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sightbuddy.R
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.text.TextStyle

/** One message in a feature's history: scanned text, the user's words, or a reply. */
data class TranscriptEntry(val kind: Kind, val text: String) {
    enum class Kind {
        SCANNED_TEXT,
        QUESTION,
        ANSWER,

        /**
         * The app's own words: "Looking for text", "No text was detected",
         * "Capture text first". They stay in the conversation like any other
         * message, and are left out when a chat is saved.
         */
        SYSTEM,
    }
}

/**
 * The session's messages, newest at the bottom and scrolled into view. Each one
 * is its own block of selectable text, so the screen reader can step through it
 * word by word or letter by letter to spell out a name the voice said.
 */
@Composable
fun ChatMessages(
    entries: List<TranscriptEntry>,
    pendingQuestion: String?,
    working: Boolean,
    emptyText: String?,
    textColor: Color,
    accent: Color,
    highContrast: Boolean,
    scroll: ScrollState,
    modifier: Modifier = Modifier,
    /** Drawn over the camera: see-through, cloudy bubbles. */
    faint: Boolean = false,
    /** Brand bubbles on a solid background; null in high contrast. */
    palette: BrandPalette? = null,
    /** Scroll to the newest message as messages arrive (off for reading a saved chat). */
    followLatest: Boolean = true,
) {
    val scannedLabel = stringResource(R.string.transcript_scanned_text)
    val questionLabel = stringResource(R.string.transcript_question)
    val answerLabel = stringResource(R.string.transcript_answer)
    val systemLabel = stringResource(R.string.transcript_system)
    fun labelOf(kind: TranscriptEntry.Kind) = when (kind) {
        TranscriptEntry.Kind.SCANNED_TEXT -> scannedLabel
        TranscriptEntry.Kind.QUESTION -> questionLabel
        TranscriptEntry.Kind.ANSWER -> answerLabel
        TranscriptEntry.Kind.SYSTEM -> systemLabel
    }

    // The question being answered shows straight away, before its answer does.
    val shown = if (pendingQuestion != null &&
        entries.lastOrNull()?.let { it.kind == TranscriptEntry.Kind.QUESTION && it.text == pendingQuestion } != true
    ) {
        entries + TranscriptEntry(TranscriptEntry.Kind.QUESTION, pendingQuestion)
    } else {
        entries
    }

    LaunchedEffect(shown.size, working) {
        if (!followLatest) return@LaunchedEffect
        withFrameNanos { }
        scroll.animateScrollTo(scroll.maxValue)
    }

    Column(
        modifier = modifier.verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // An empty chat opens with a message like any other, which gives way to
        // the conversation once there is one.
        if (shown.isEmpty() && !working && emptyText != null) {
            MessageBubble(
                label = answerLabel,
                text = emptyText,
                fromUser = false,
                highContrast = highContrast,
                textColor = textColor,
                accent = accent,
                faint = faint,
                palette = palette,
            )
        }
        shown.forEach { entry ->
            MessageBubble(
                label = labelOf(entry.kind),
                text = entry.text,
                fromUser = entry.kind == TranscriptEntry.Kind.QUESTION,
                highContrast = highContrast,
                textColor = textColor,
                accent = accent,
                faint = faint,
                palette = palette,
            )
        }
        if (working) {
            // Shown, never announced: it came after every capture.
            Text(
                text = stringResource(R.string.chat_working),
                color = textColor.copy(alpha = 0.75f),
                fontSize = 16.sp,
            )
        }
    }
}

@Composable
private fun MessageBubble(
    label: String,
    text: String,
    fromUser: Boolean,
    highContrast: Boolean,
    textColor: Color,
    accent: Color,
    faint: Boolean = false,
    palette: BrandPalette? = null,
) {
    // Messaging-app shape: the corner nearest the speaker's side is tighter.
    val shape = if (palette != null && !faint && !highContrast) {
        if (fromUser) RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp) else RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
    } else {
        RoundedCornerShape(16.dp)
    }
    val branded = palette != null && !faint && !highContrast
    // High contrast is solid everywhere, over the camera too: black or white
    // behind, whichever the text is not.
    val hcBackground = if (textColor.luminance() > 0.5f) Color.Black else Color.White
    val ink = when {
        highContrast -> textColor
        faint -> Color.White.copy(alpha = 0.75f)
        branded -> palette!!.ink
        else -> textColor
    }
    val labelColor = if (branded) palette!!.label else ink.copy(alpha = ink.alpha * 0.75f)
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (fromUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .clip(shape)
                .then(
                    when {
                        highContrast -> Modifier
                            .background(hcBackground)
                            .border(2.dp, textColor, shape)
                        // A light haze over the picture, whatever the theme.
                        faint -> Modifier.background(
                            if (fromUser) accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.16f)
                        )
                        branded -> Modifier
                            .background(if (fromUser) palette!!.outgoing else palette!!.surface)
                            .border(1.dp, palette.edge, shape)
                        else -> Modifier.background(
                            if (fromUser) accent.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.10f)
                        )
                    }
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            // A heading per message, so TalkBack can jump message to message.
            Text(
                text = label,
                color = labelColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            SelectionContainer {
                Text(
                    text = text,
                    color = ink,
                    fontSize = 18.sp,
                    lineHeight = 26.sp,
                )
            }
        }
    }
}

/** A bottom-row button: square-cornered, reads only its label. */
@Composable
fun SquareButton(
    label: String,
    background: Color,
    textColor: Color,
    modifier: Modifier,
    shape: Shape = RoundedCornerShape(4.dp),
    /**
     * What TalkBack calls the button, when that is not the visible label. A
     * label that changes under the user's finger is read out again each time it
     * changes; this keeps the spoken name still while the text moves.
     */
    spokenLabel: String = label,
    /** What the button is doing, when it is doing something: "Listening". */
    state: String? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(background)
            .clickable(onClick = onClick)
            // A label may be split over lines to fit; it is read as one.
            .clearAndSetSemantics {
                contentDescription = spokenLabel.replace('\n', ' ')
                role = Role.Button
                if (state != null) stateDescription = state
            },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = TextStyle(
                color = textColor,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
            maxLines = label.count { it == '\n' } + 1,
            // Shrinks to fit instead of breaking a word across two lines.
            autoSize = TextAutoSize.StepBased(
                minFontSize = 12.sp,
                maxFontSize = 20.sp,
            ),
            modifier = Modifier.padding(6.dp),
        )
    }
}
