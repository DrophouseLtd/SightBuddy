package com.example.sightbuddy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.sightbuddy.R
import com.example.sightbuddy.ui.DialogWindowTitle
import com.example.sightbuddy.ui.buttonSemantics
import com.example.sightbuddy.ui.theme.actionButtonBackground
import com.example.sightbuddy.ui.theme.actionButtonText
import com.example.sightbuddy.core.hints.HelpSection

@Composable
fun HelpDialog(
    title: String,
    body: String,
    /** When not empty, shown instead of [body]: parts under sub-headings. */
    sections: List<HelpSection> = emptyList(),
    darkTheme: Boolean,
    highContrast: Boolean,
    whiteMode: Boolean,
    onClose: () -> Unit,
) {
    val scrim = Color.Black.copy(alpha = 0.72f)
    // Brand colours, except in high contrast, which keeps black and white.
    val brand = com.example.sightbuddy.ui.theme.brandPalette(darkTheme)
    val cardBg = when {
        highContrast -> if (darkTheme) Color(0xFF1E1E1E) else Color.White
        else -> brand.surface
    }
    val textColor = when {
        highContrast -> if (darkTheme) Color.White else Color(0xFF1A1A1A)
        else -> brand.ink
    }
    val titleColor = if (highContrast) textColor else brand.label

    val closeLabel = stringResource(R.string.help_close)
    val closeCd = stringResource(R.string.help_close_cd)

    Dialog(
        onDismissRequest = { onClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        DialogWindowTitle(title)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scrim)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(cardBg)
                    .padding(horizontal = 24.dp, vertical = 28.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = titleColor,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(16.dp))
                // Short parts under sub-headings, each paragraph on its own,
                // so TalkBack can move heading to heading.
                val parts = sections.ifEmpty {
                    listOf(HelpSection(null, body))
                }
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    parts.forEachIndexed { index, part ->
                        if (index > 0) Spacer(modifier = Modifier.height(18.dp))
                        part.heading?.let { heading ->
                            Text(
                                text = heading,
                                style = MaterialTheme.typography.titleMedium,
                                color = textColor,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.semantics { heading() },
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                        part.text.split("\n\n").forEachIndexed { p, paragraph ->
                            if (p > 0) Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = paragraph.trim(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = textColor.copy(alpha = 0.92f),
                                lineHeight = 26.sp,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                HelpActionButton(
                    label = closeLabel,
                    background = actionButtonBackground(highContrast, whiteMode, Color(0xFF8EEFCA)),
                    textColor = actionButtonText(highContrast, whiteMode, Color.Black),
                    contentDescription = closeCd,
                    onClick = { onClose() },
                )
            }
        }
    }
}

@Composable
private fun HelpActionButton(
    label: String,
    background: Color,
    contentDescription: String,
    onClick: () -> Unit,
    textColor: Color = Color.White,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp)
            .buttonSemantics(label),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
