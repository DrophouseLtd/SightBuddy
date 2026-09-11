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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.sightbuddy.R
import com.example.sightbuddy.ui.theme.actionButtonBackground
import com.example.sightbuddy.ui.theme.actionButtonText

/**
 * Confirms a language change. Switching restarts the activity, because every
 * resource — strings and the spoken cues alike — is resolved at attach time.
 */
@Composable
fun LanguageSwitchDialog(
    targetLanguageName: String,
    highContrast: Boolean,
    whiteMode: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
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
    val message = stringResource(R.string.language_switch_body, targetLanguageName)

    Dialog(
        onDismissRequest = onDismiss,
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
                    text = stringResource(R.string.language_switch_title),
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
                LanguageDialogButton(
                    label = stringResource(R.string.language_switch_confirm),
                    background = actionButtonBackground(highContrast, whiteMode, Color(0xFF8EEFCA)),
                    textColor = actionButtonText(highContrast, whiteMode, Color.Black),
                    contentDescription = stringResource(
                        R.string.language_switch_confirm_cd,
                        targetLanguageName,
                    ),
                    onClick = onConfirm,
                )
                Spacer(modifier = Modifier.height(12.dp))
                LanguageDialogButton(
                    label = stringResource(R.string.language_switch_cancel),
                    background = secondaryBg,
                    textColor = textColor,
                    contentDescription = stringResource(R.string.language_switch_cancel_cd),
                    onClick = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun LanguageDialogButton(
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
