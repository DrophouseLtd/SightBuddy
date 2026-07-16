package com.example.sightbuddy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.sightbuddy.R
import com.example.sightbuddy.core.SettingsManager
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    highContrast: Boolean,
    whiteMode: Boolean,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onDeleteData: suspend () -> Boolean = { false },
) {
    val textPreview by settingsManager.textPreviewEnabled.collectAsState()
    val llmChatEnabled by settingsManager.llmChatEnabled.collectAsState()
    val scanLight by settingsManager.scanLightEnabled.collectAsState()
    val scanColour by settingsManager.scanColourEnabled.collectAsState()
    val highContrastMode by settingsManager.highContrastEnabled.collectAsState()
    val buttonNav by settingsManager.useButtonNav.collectAsState()
    val holdToSpeak by settingsManager.holdToSpeak.collectAsState()
    val ttsRate by settingsManager.ttsSpeechRate.collectAsState()
    val featureActivationAnnouncements by settingsManager.featureActivationAnnouncementsEnabled.collectAsState()
    var showCocoObjectSettings by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val deleteDataLabel = stringResource(R.string.settings_delete_data)
    val deleteDataDescription = stringResource(R.string.settings_delete_data_description)
    val deleteDataCd = stringResource(R.string.settings_delete_data_cd)

    val defaultTextColor = MaterialTheme.colorScheme.onSurface
    val textColor = if (highContrast) {
        if (whiteMode) Color.Black else Color.White
    } else {
        defaultTextColor
    }
    val labelColor = if (highContrast) {
        if (whiteMode) Color.Black else Color.White.copy(alpha = 0.85f)
    } else {
        defaultTextColor
    }

    if (showCocoObjectSettings) {
        CocoObjectsSettingsScreen(
            settingsManager = settingsManager,
            highContrast = highContrast,
            whiteMode = whiteMode,
            modifier = modifier,
            onBack = { showCocoObjectSettings = false },
        )
        return
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.displayMedium,
                color = textColor,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            SectionHeader("Feature settings", textColor)

            SettingsToggle(
                label = "Text preview before AI",
                description = "Read text aloud first, then ask AI with mic",
                checked = textPreview,
                onCheckedChange = { settingsManager.setTextPreview(it) },
                labelColor = labelColor,
                highContrast = highContrast,
                whiteMode = whiteMode
            )

            SettingsToggle(
                label = "LLM Chat",
                description = "Enable cloud AI features (image chat, text questions, smart object search)",
                checked = llmChatEnabled,
                onCheckedChange = { settingsManager.setLlmChat(it) },
                labelColor = labelColor,
                highContrast = highContrast,
                whiteMode = whiteMode
            )

            SettingsToggle(
                label = "Scan Light",
                description = "Enable the light level scanner",
                checked = scanLight,
                onCheckedChange = { settingsManager.setScanLight(it) },
                labelColor = labelColor,
                highContrast = highContrast,
                whiteMode = whiteMode
            )

            SettingsToggle(
                label = "Scan Colour",
                description = "Enable the colour identifier",
                checked = scanColour,
                onCheckedChange = { settingsManager.setScanColour(it) },
                labelColor = labelColor,
                highContrast = highContrast,
                whiteMode = whiteMode
            )

            Spacer(modifier = Modifier.height(24.dp))

            SectionHeader("User preferences", textColor)

            SettingsToggle(
                label = "High contrast mode",
                description = "Solid background instead of camera preview",
                checked = highContrastMode,
                onCheckedChange = { settingsManager.setHighContrast(it) },
                labelColor = labelColor,
                highContrast = highContrast,
                whiteMode = whiteMode
            )

            if (highContrast) {
                SettingsToggle(
                    label = "White mode",
                    description = "Switch between dark and white contrast themes",
                    checked = whiteMode,
                    onCheckedChange = { settingsManager.setWhiteMode(it) },
                    labelColor = labelColor,
                    highContrast = highContrast,
                    whiteMode = whiteMode
                )
            }

            SettingsToggle(
                label = "Button navigation",
                description = "Replace carousel swipe with left / right buttons",
                checked = buttonNav,
                onCheckedChange = { settingsManager.setButtonNav(it) },
                labelColor = labelColor,
                highContrast = highContrast,
                whiteMode = whiteMode
            )

            SettingsToggle(
                label = "Hold to speak",
                description = "Hold the mic while talking. When off, tap once to start recording and tap again to stop",
                checked = holdToSpeak,
                onCheckedChange = { settingsManager.setHoldToSpeak(it) },
                labelColor = labelColor,
                highContrast = highContrast,
                whiteMode = whiteMode
            )

            SettingsNavRow(
                label = "Speech rate",
                description = "Current: ${SettingsManager.ttsRateLabel(ttsRate)}. Tap to change",
                labelColor = labelColor,
                highContrast = highContrast,
                whiteMode = whiteMode,
                onClick = { settingsManager.cycleTtsSpeechRate() },
                contentDescription = "Speech rate. Currently ${SettingsManager.ttsRateLabel(ttsRate)}. " +
                    "Double tap to switch to the next speed.",
            )

            SettingsToggle(
                label = "Feature activation announcements",
                description = "Turn off to bypass feature activation announcements",
                checked = featureActivationAnnouncements,
                onCheckedChange = { settingsManager.setFeatureActivationAnnouncements(it) },
                labelColor = labelColor,
                highContrast = highContrast,
                whiteMode = whiteMode
            )

            SettingsNavRow(
                label = "Object list",
                description = "Show or hide items in the Find objects picker",
                labelColor = labelColor,
                highContrast = highContrast,
                whiteMode = whiteMode,
                onClick = { showCocoObjectSettings = true },
            )

            Spacer(modifier = Modifier.height(24.dp))

            SectionHeader("Privacy", textColor)

            SettingsNavRow(
                label = deleteDataLabel,
                description = deleteDataDescription,
                labelColor = labelColor,
                highContrast = highContrast,
                whiteMode = whiteMode,
                onClick = { if (!isDeleting) showDeleteConfirm = true },
                contentDescription = deleteDataCd,
            )

            Spacer(modifier = Modifier.height(32.dp))
        }

        if (showDeleteConfirm) {
            DeleteDataConfirmDialog(
                highContrast = highContrast,
                whiteMode = whiteMode,
                isDeleting = isDeleting,
                onConfirm = {
                    scope.launch {
                        isDeleting = true
                        val ok = onDeleteData()
                        isDeleting = false
                        showDeleteConfirm = false
                        if (!ok) {
                            // Caller handles user feedback (e.g. TTS) on failure.
                        }
                    }
                },
                onDismiss = {
                    if (!isDeleting) showDeleteConfirm = false
                },
            )
        }

        // Back button — top-right corner
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp)
                .size(64.dp)
                .background(
                    when {
                        highContrast && whiteMode -> Color.Black
                        highContrast -> Color.White
                        else -> Color.White.copy(alpha = 0.15f)
                    },
                    shape = CircleShape
                )
                .clickable { onBack() }
                .semantics { contentDescription = "Close settings" },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Back",
                color = when {
                    highContrast && whiteMode -> Color.White
                    highContrast -> Color.Black
                    else -> textColor
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun DeleteDataConfirmDialog(
    highContrast: Boolean,
    whiteMode: Boolean,
    isDeleting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scrim = Color.Black.copy(alpha = 0.72f)
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
    val message = stringResource(R.string.delete_data_confirm_message)
    val yesLabel = stringResource(R.string.delete_data_confirm_yes)
    val noLabel = stringResource(R.string.delete_data_confirm_no)
    val yesCd = stringResource(R.string.delete_data_confirm_yes_cd)
    val noCd = stringResource(R.string.delete_data_confirm_no_cd)

    Dialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
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
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor,
                    lineHeight = 26.sp,
                )
                Spacer(modifier = Modifier.height(24.dp))
                ConfirmDialogButton(
                    label = yesLabel,
                    background = Color(0xFFC62828),
                    textColor = Color.White,
                    contentDescription = yesCd,
                    enabled = !isDeleting,
                    onClick = onConfirm,
                )
                Spacer(modifier = Modifier.height(12.dp))
                ConfirmDialogButton(
                    label = noLabel,
                    background = if (highContrast) {
                        if (whiteMode) Color(0xFFE0E0E0) else Color(0xFF424242)
                    } else {
                        Color(0xFFE0E0E0)
                    },
                    textColor = textColor,
                    contentDescription = noCd,
                    enabled = !isDeleting,
                    onClick = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun ConfirmDialogButton(
    label: String,
    background: Color,
    textColor: Color,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) background else background.copy(alpha = 0.5f))
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 14.dp)
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

@Composable
private fun SettingsNavRow(
    label: String,
    description: String,
    labelColor: Color,
    highContrast: Boolean,
    whiteMode: Boolean,
    onClick: () -> Unit,
    contentDescription: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .clickable { onClick() }
            .semantics {
                this.contentDescription = contentDescription
                    ?: "$label. $description. Double tap to open."
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = labelColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = description,
                color = labelColor.copy(alpha = 0.7f),
                fontSize = 14.sp,
            )
        }
        Text(
            text = "›",
            color = if (highContrast) {
                if (whiteMode) Color.Black else Color.White
            } else {
                labelColor.copy(alpha = 0.6f)
            },
            fontSize = 28.sp,
            fontWeight = FontWeight.Light,
        )
    }
}

@Composable
private fun SectionHeader(title: String, color: Color) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = color,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    )
}

@Composable
private fun SettingsToggle(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    labelColor: Color,
    highContrast: Boolean,
    whiteMode: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .semantics {
                contentDescription = "$label. $description. Currently ${if (checked) "on" else "off"}."
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = labelColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                color = labelColor.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = if (highContrast && whiteMode) Color.White else Color.Black,
                checkedTrackColor = if (highContrast) {
                    if (whiteMode) Color.Black else Color.White
                } else Color(0xFF2196F3),
                // White high-contrast: OFF must read clearly lighter than ON (black track).
                uncheckedThumbColor = if (highContrast && whiteMode) {
                    Color(0xFF757575)
                } else {
                    Color.Gray
                },
                uncheckedTrackColor = if (highContrast && whiteMode) {
                    Color(0xFFD6D6D6)
                } else {
                    Color.DarkGray
                }
            )
        )
    }
}
