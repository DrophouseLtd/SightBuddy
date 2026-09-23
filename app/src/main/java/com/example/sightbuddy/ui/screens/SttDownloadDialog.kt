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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.sightbuddy.R
import com.example.sightbuddy.ui.DialogWindowTitle
import com.example.sightbuddy.ui.buttonSemantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.sightbuddy.ui.theme.actionButtonBackground
import com.example.sightbuddy.ui.theme.actionButtonText

/**
 * The model setup offer: what suits this phone, its size and the free space.
 * "Later" re-asks on the next app launch; "Never" only via Settings.
 */
@Composable
fun ModelSetupDialog(
    highContrast: Boolean,
    whiteMode: Boolean,
    message: String,
    onDownload: () -> Unit,
    onLater: () -> Unit,
    onNever: () -> Unit,
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
    val secondaryBg = when {
        highContrast && !whiteMode -> Color(0xFF424242)
        highContrast -> Color(0xFFE0E0E0)
        else -> currentBrand().wall
    }

    Dialog(
        onDismissRequest = onLater,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // The window and the card share one title, as the Understood pop-up does:
        // TalkBack says "Onboarding", then the message.
        val title = stringResource(R.string.setup_title)
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
                    modifier = Modifier.semantics { heading() },
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
                    label = stringResource(R.string.setup_yes),
                    background = actionButtonBackground(highContrast, whiteMode, Color(0xFF8EEFCA)),
                    textColor = actionButtonText(highContrast, whiteMode, Color.Black),
                    contentDescription = stringResource(R.string.setup_yes_cd),
                    onClick = onDownload,
                )
                Spacer(modifier = Modifier.height(12.dp))
                DialogButton(
                    label = stringResource(R.string.stt_later),
                    background = secondaryBg,
                    textColor = textColor,
                    contentDescription = stringResource(R.string.stt_later_cd),
                    onClick = onLater,
                )
                Spacer(modifier = Modifier.height(12.dp))
                DialogButton(
                    label = stringResource(R.string.stt_never),
                    background = secondaryBg,
                    textColor = textColor,
                    contentDescription = stringResource(R.string.stt_never_cd),
                    onClick = onNever,
                )
            }
        }
    }
}

/**
 * Opened from Settings once the voice models are on the device: what they take
 * up, and a way to remove them. Deleting asks once more before it happens.
 */
@Composable
fun SttModelsDialog(
    highContrast: Boolean,
    whiteMode: Boolean,
    sizeLabel: String,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    title: String = stringResource(R.string.settings_stt_models),
    bodyRes: Int = R.string.stt_models_body,
    confirmRes: Int = R.string.stt_models_delete_confirm,
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
    val secondaryBg = when {
        highContrast && !whiteMode -> Color(0xFF424242)
        highContrast -> Color(0xFFE0E0E0)
        else -> currentBrand().wall
    }
    var confirming by remember { mutableStateOf(false) }
    val message = stringResource(
        if (confirming) confirmRes else bodyRes,
        sizeLabel,
    )

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
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor,
                    lineHeight = 26.sp,
                    // Spoken when it changes to the confirmation.
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                Spacer(modifier = Modifier.height(24.dp))
                if (confirming) {
                    DialogButton(
                        label = stringResource(R.string.stt_models_delete_yes),
                        background = actionButtonBackground(highContrast, whiteMode, Color(0xFFC62828)),
                        textColor = actionButtonText(highContrast, whiteMode, Color.White),
                        contentDescription = stringResource(R.string.stt_models_delete_yes_cd),
                        onClick = onDelete,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    DialogButton(
                        label = stringResource(R.string.stt_models_keep),
                        background = secondaryBg,
                        textColor = textColor,
                        contentDescription = stringResource(R.string.stt_models_keep_cd),
                        onClick = onDismiss,
                    )
                } else {
                    DialogButton(
                        label = stringResource(R.string.stt_models_delete),
                        background = actionButtonBackground(highContrast, whiteMode, Color(0xFFC62828)),
                        textColor = actionButtonText(highContrast, whiteMode, Color.White),
                        contentDescription = stringResource(R.string.stt_models_delete_cd),
                        onClick = { confirming = true },
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    DialogButton(
                        label = stringResource(R.string.help_close),
                        background = secondaryBg,
                        textColor = textColor,
                        contentDescription = stringResource(R.string.stt_models_close_cd),
                        onClick = onDismiss,
                    )
                }
            }
        }
    }
}

/** Asks before the ~2.6 GB Gemma download. */
@Composable
fun GemmaDownloadDialog(
    highContrast: Boolean,
    whiteMode: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
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
    val secondaryBg = when {
        highContrast && !whiteMode -> Color(0xFF424242)
        highContrast -> Color(0xFFE0E0E0)
        else -> currentBrand().wall
    }
    val title = stringResource(R.string.gemma_download_title)

    Dialog(
        onDismissRequest = onCancel,
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
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.gemma_download_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor,
                    lineHeight = 26.sp,
                )
                Spacer(modifier = Modifier.height(24.dp))
                DialogButton(
                    label = stringResource(R.string.gemma_download_now),
                    background = actionButtonBackground(highContrast, whiteMode, Color(0xFF8EEFCA)),
                    textColor = actionButtonText(highContrast, whiteMode, Color.Black),
                    contentDescription = stringResource(R.string.gemma_download_now_cd),
                    onClick = onDownload,
                )
                Spacer(modifier = Modifier.height(12.dp))
                DialogButton(
                    label = stringResource(R.string.gemma_download_cancel),
                    background = secondaryBg,
                    textColor = textColor,
                    contentDescription = stringResource(R.string.gemma_download_cancel),
                    onClick = onCancel,
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
