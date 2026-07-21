package com.example.sightbuddy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Asks permission for the one-time ~154 MB Whisper voice-model download.
 * "Later" re-asks on the next app launch; "Never" only via Settings.
 */
@Composable
fun SttDownloadDialog(
    highContrast: Boolean,
    whiteMode: Boolean,
    onDownload: () -> Unit,
    onLater: () -> Unit,
    onNever: () -> Unit,
) {
    val cardBg = when {
        highContrast && whiteMode -> Color.White
        highContrast -> Color.Black
        else -> Color.White
    }
    val textColor = when {
        highContrast && whiteMode -> Color.Black
        highContrast -> Color.White
        else -> Color(0xFF1A1A1A)
    }
    val secondaryBg = if (highContrast && !whiteMode) Color(0xFF424242) else Color(0xFFE0E0E0)
    val message =
        "Download voice models to improve speech recognition? " +
            "Speech is then recognised fully on your device, even offline. " +
            "One-time download of about 154 megabytes — Wi-Fi recommended."

    Dialog(
        onDismissRequest = onLater,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(cardBg)
                    .padding(horizontal = 24.dp, vertical = 28.dp)
                    .semantics { contentDescription = message },
            ) {
                Text(
                    text = "Better voice recognition?",
                    style = MaterialTheme.typography.headlineSmall,
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor,
                    lineHeight = 26.sp,
                )
                Spacer(modifier = Modifier.height(24.dp))
                DialogButton(
                    label = "Download now",
                    background = Color(0xFF4CAF50),
                    textColor = Color.White,
                    contentDescription = "Download voice models now. About 154 megabytes.",
                    onClick = onDownload,
                )
                Spacer(modifier = Modifier.height(12.dp))
                DialogButton(
                    label = "Later",
                    background = secondaryBg,
                    textColor = textColor,
                    contentDescription = "Later. Ask again next time the app opens.",
                    onClick = onLater,
                )
                Spacer(modifier = Modifier.height(12.dp))
                DialogButton(
                    label = "Never",
                    background = secondaryBg,
                    textColor = textColor,
                    contentDescription = "Never. Don't ask again. You can still download from settings.",
                    onClick = onNever,
                )
            }
        }
    }
}

@Composable
private fun DialogButton(
    label: String,
    background: Color,
    textColor: Color,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp)
            .semantics { this.contentDescription = contentDescription },
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
