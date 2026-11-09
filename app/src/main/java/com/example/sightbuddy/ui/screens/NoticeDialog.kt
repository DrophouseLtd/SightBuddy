package com.example.sightbuddy.ui.screens

import com.example.sightbuddy.ui.theme.currentBrand
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.sightbuddy.ui.DialogWindowTitle
import com.example.sightbuddy.ui.buttonSemantics
import com.example.sightbuddy.ui.theme.actionButtonBackground
import com.example.sightbuddy.ui.theme.actionButtonText

/**
 * A message and one button: something the user should know, said once. The
 * title is the window's title, so TalkBack says it when the pop-up opens.
 */
@Composable
fun NoticeDialog(
    title: String,
    message: String,
    buttonLabel: String,
    highContrast: Boolean,
    whiteMode: Boolean,
    onDismiss: () -> Unit,
) {
    val cardBg = when {
        highContrast && whiteMode -> Color.White
        highContrast -> Color.Black
        else -> currentBrand().surface
    }
    val textColor = when {
        highContrast && whiteMode -> Color.Black
        highContrast -> Color.White
        else -> currentBrand().ink
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        DialogWindowTitle(title)
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
                    .padding(horizontal = 24.dp, vertical = 28.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (highContrast) textColor else currentBrand().label,
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            actionButtonBackground(highContrast, whiteMode, Color(0xFF8EEFCA))
                        )
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 16.dp)
                        .buttonSemantics(buttonLabel),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = buttonLabel,
                        color = actionButtonText(highContrast, whiteMode, Color.Black),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
