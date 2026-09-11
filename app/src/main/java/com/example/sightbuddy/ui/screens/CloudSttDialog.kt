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
 * Asks before any audio leaves the device.
 *
 * Turning this on changes where speech is processed and it costs the user money,
 * so it is never enabled silently. The body names both plainly.
 *
 * It asks two different questions. Without the on-device model the question is
 * whether to spend anything at all. With the model already installed the money
 * buys nothing the phone cannot already do, so the question becomes why spend it,
 * and both buttons say which of the two the user is choosing.
 */
@Composable
fun CloudSttDialog(
    highContrast: Boolean,
    whiteMode: Boolean,
    /** True when the free on-device model is already installed. */
    localModelPresent: Boolean,
    onEnable: () -> Unit,
    onDecline: () -> Unit,
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
    // With the free model already on the phone, the question is no longer whether
    // to spend anything, it is why spend it when you need not.
    val message = stringResource(
        if (localModelPresent) R.string.cloud_stt_local_body else R.string.cloud_stt_dialog_body
    )

    Dialog(
        onDismissRequest = onDecline,
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
                    text = stringResource(
                        if (localModelPresent) R.string.cloud_stt_local_title
                        else R.string.cloud_stt_dialog_title
                    ),
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
                CloudSttButton(
                    label = stringResource(
                        if (localModelPresent) R.string.cloud_stt_local_enable
                        else R.string.cloud_stt_enable
                    ),
                    background = actionButtonBackground(highContrast, whiteMode, Color(0xFF8EEFCA)),
                    textColor = actionButtonText(highContrast, whiteMode, Color.Black),
                    contentDescription = stringResource(
                        if (localModelPresent) R.string.cloud_stt_local_enable_cd
                        else R.string.cloud_stt_enable_cd
                    ),
                    onClick = onEnable,
                )
                Spacer(modifier = Modifier.height(12.dp))
                CloudSttButton(
                    label = stringResource(
                        if (localModelPresent) R.string.cloud_stt_local_decline
                        else R.string.cloud_stt_decline
                    ),
                    background = secondaryBg,
                    textColor = textColor,
                    contentDescription = stringResource(
                        if (localModelPresent) R.string.cloud_stt_local_decline_cd
                        else R.string.cloud_stt_decline_cd
                    ),
                    onClick = onDecline,
                )
            }
        }
    }
}

@Composable
private fun CloudSttButton(
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
