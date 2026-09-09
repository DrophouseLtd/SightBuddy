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
import com.example.sightbuddy.ui.theme.actionButtonBackground
import com.example.sightbuddy.ui.theme.actionButtonText
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    highContrast: Boolean,
    whiteMode: Boolean,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onDeleteData: suspend () -> Boolean = { false },
    apiKeyPresent: Boolean = false,
    onSaveApiKey: (String) -> Unit = {},
    onSpeechRateCycle: () -> Unit = {},
    sttModelsReady: Boolean = false,
    sttDownloadPercent: Int? = null,
    onRequestSttDownload: () -> Unit = {},
    onFeatureHideBlocked: () -> Unit = {},
    onCrashReportingChange: (Boolean) -> Unit = {},
) {
    val textPreview by settingsManager.textPreviewEnabled.collectAsState()
    val scanLight by settingsManager.scanLightEnabled.collectAsState()
    val scanColour by settingsManager.scanColourEnabled.collectAsState()
    val imageChat by settingsManager.imageChatEnabled.collectAsState()
    val textChat by settingsManager.textChatEnabled.collectAsState()
    val discover by settingsManager.discoverEnabled.collectAsState()
    val find by settingsManager.findEnabled.collectAsState()
    val highContrastMode by settingsManager.highContrastEnabled.collectAsState()
    val buttonNav by settingsManager.useButtonNav.collectAsState()
    val holdToSpeak by settingsManager.holdToSpeak.collectAsState()
    val autoCapture by settingsManager.autoCaptureEnabled.collectAsState()
    val llmModel by settingsManager.llmModel.collectAsState()
    val crashReporting by settingsManager.crashReportingEnabled.collectAsState()
    val ttsRate by settingsManager.ttsSpeechRate.collectAsState()
    val featureActivationAnnouncements by settingsManager.featureActivationAnnouncementsEnabled.collectAsState()
    var showCocoObjectSettings by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
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

    // System back closes the current settings view instead of exiting the app:
    // the object sub-list returns to the settings list; the settings list closes.
    androidx.activity.compose.BackHandler {
        if (showCocoObjectSettings) showCocoObjectSettings = false else onBack()
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

            SectionHeader("Cloud AI — bring your own key", textColor)

            SettingsNavRow(
                label = "OpenAI API key",
                description = if (apiKeyPresent) {
                    "Key saved. AI chat features enabled. Tap to change or remove"
                } else {
                    "Add your own OpenAI API key to enable Image chat and AI questions"
                },
                labelColor = labelColor,
                highContrast = highContrast,
                whiteMode = whiteMode,
                onClick = { showApiKeyDialog = true },
                contentDescription = if (apiKeyPresent) {
                    "OpenAI API key. A key is saved and AI features are enabled. Double tap to change or remove it."
                } else {
                    "OpenAI API key. Not set. Double tap to add your own key and enable AI features."
                },
            )

            // Model picker — one selected at a time. Costs go to the user's own key.
            if (apiKeyPresent) {
                SettingsManager.LLM_MODEL_OPTIONS.forEach { option ->
                    SettingsChoiceRow(
                        label = option.label,
                        description = option.description,
                        selected = llmModel == option.id,
                        labelColor = labelColor,
                        highContrast = highContrast,
                        whiteMode = whiteMode,
                        onClick = { settingsManager.setLlmModel(option.id) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            SectionHeader("Voice recognition", textColor)

            SettingsNavRow(
                label = "Voice recognition models",
                description = when {
                    sttModelsReady -> "Downloaded. Speech is recognised on this device"
                    sttDownloadPercent != null -> "Downloading… $sttDownloadPercent%"
                    else -> "Not downloaded. Tap to download (154 MB)"
                },
                labelColor = labelColor,
                highContrast = highContrast,
                whiteMode = whiteMode,
                onClick = {
                    if (!sttModelsReady && sttDownloadPercent == null) onRequestSttDownload()
                },
            )

            Spacer(modifier = Modifier.height(24.dp))

            SectionHeader("Features — show in carousel", textColor)

            if (apiKeyPresent) {
                SettingsToggle(
                    label = "Image chat",
                    description = "Show Image chat in the carousel",
                    checked = imageChat,
                    onCheckedChange = { settingsManager.setImageChat(it) },
                    labelColor = labelColor,
                    highContrast = highContrast,
                    whiteMode = whiteMode
                )
            }

            SettingsToggle(
                label = "Text chat",
                description = "Show Text chat (read text) in the carousel",
                checked = textChat,
                onCheckedChange = { if (!settingsManager.setTextChat(it)) onFeatureHideBlocked() },
                labelColor = labelColor,
                highContrast = highContrast,
                whiteMode = whiteMode
            )

            SettingsToggle(
                label = "Discover objects",
                description = "Show Discover objects in the carousel",
                checked = discover,
                onCheckedChange = { if (!settingsManager.setDiscover(it)) onFeatureHideBlocked() },
                labelColor = labelColor,
                highContrast = highContrast,
                whiteMode = whiteMode
            )

            SettingsToggle(
                label = "Find objects",
                description = "Show Find objects in the carousel",
                checked = find,
                onCheckedChange = { if (!settingsManager.setFind(it)) onFeatureHideBlocked() },
                labelColor = labelColor,
                highContrast = highContrast,
                whiteMode = whiteMode
            )

            SettingsToggle(
                label = "Scan Light",
                description = "Show Scan Light in the carousel",
                checked = scanLight,
                onCheckedChange = { if (!settingsManager.setScanLight(it)) onFeatureHideBlocked() },
                labelColor = labelColor,
                highContrast = highContrast,
                whiteMode = whiteMode
            )

            SettingsToggle(
                label = "Scan Colour",
                description = "Show Scan Colour in the carousel",
                checked = scanColour,
                onCheckedChange = { if (!settingsManager.setScanColour(it)) onFeatureHideBlocked() },
                labelColor = labelColor,
                highContrast = highContrast,
                whiteMode = whiteMode
            )

            Spacer(modifier = Modifier.height(24.dp))

            SectionHeader("User preferences", textColor)

            if (apiKeyPresent) {
                SettingsToggle(
                    label = "Automatic capture",
                    description = "Press Ask with no picture to snap one automatically. When off, press Capture first",
                    checked = autoCapture,
                    onCheckedChange = { settingsManager.setAutoCapture(it) },
                    labelColor = labelColor,
                    highContrast = highContrast,
                    whiteMode = whiteMode
                )

                SettingsToggle(
                    label = "Text preview before AI",
                    description = "Read text aloud first, then ask AI with the Ask button",
                    checked = textPreview,
                    onCheckedChange = { settingsManager.setTextPreview(it) },
                    labelColor = labelColor,
                    highContrast = highContrast,
                    whiteMode = whiteMode
                )
            }

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
                description = "Hold the Ask button while talking. When off, tap once to start and tap again to send",
                checked = holdToSpeak,
                onCheckedChange = {
                    settingsManager.setHoldToSpeak(it)
                    if (!sttModelsReady && sttDownloadPercent == null) onRequestSttDownload()
                },
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
                onClick = { onSpeechRateCycle() },
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

            Spacer(modifier = Modifier.height(24.dp))

            SectionHeader("Privacy", textColor)

            SettingsToggle(
                label = "Send crash reports",
                description = "Share anonymous crash diagnostics so faults can be fixed. No personal data, no tracking",
                checked = crashReporting,
                onCheckedChange = {
                    settingsManager.setCrashReporting(it)
                    onCrashReportingChange(it)
                },
                labelColor = labelColor,
                highContrast = highContrast,
                whiteMode = whiteMode
            )

            Spacer(modifier = Modifier.height(32.dp))
        }

        if (showApiKeyDialog) {
            ApiKeyDialog(
                highContrast = highContrast,
                whiteMode = whiteMode,
                keyPresent = apiKeyPresent,
                onSave = { key ->
                    onSaveApiKey(key)
                    showApiKeyDialog = false
                },
                onRemove = {
                    onSaveApiKey("")
                    showApiKeyDialog = false
                },
                onDismiss = { showApiKeyDialog = false },
            )
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
private fun ApiKeyDialog(
    highContrast: Boolean,
    whiteMode: Boolean,
    keyPresent: Boolean,
    onSave: (String) -> Unit,
    onRemove: () -> Unit,
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
    var keyInput by remember { mutableStateOf("") }
    var keyVisible by remember { mutableStateOf(false) }

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
                    .padding(horizontal = 24.dp, vertical = 28.dp),
            ) {
                Text(
                    text = "OpenAI API key",
                    style = MaterialTheme.typography.headlineSmall,
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Your key is stored encrypted on this device and sent only to " +
                        "OpenAI. Usage is billed to your own OpenAI account.",
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.8f),
                    lineHeight = 20.sp,
                )
                Spacer(modifier = Modifier.height(16.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "API key input field. Paste your OpenAI API key." },
                    placeholder = { Text("sk-…", color = textColor.copy(alpha = 0.5f)) },
                    singleLine = true,
                    visualTransformation = if (keyVisible) {
                        androidx.compose.ui.text.input.VisualTransformation.None
                    } else {
                        androidx.compose.ui.text.input.PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        Text(
                            text = if (keyVisible) "Hide" else "Show",
                            color = if (highContrast) textColor else Color(0xFF3DBAD0),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { keyVisible = !keyVisible }
                                .padding(horizontal = 12.dp)
                                .semantics {
                                    contentDescription =
                                        if (keyVisible) "Hide the API key" else "Show the API key"
                                },
                        )
                    },
                )
                Spacer(modifier = Modifier.height(20.dp))
                ConfirmDialogButton(
                    label = "Save key",
                    background = actionButtonBackground(highContrast, whiteMode, Color(0xFF8EEFCA)),
                    textColor = actionButtonText(highContrast, whiteMode, Color.Black),
                    contentDescription = "Save the entered API key",
                    enabled = keyInput.isNotBlank(),
                    onClick = { onSave(keyInput) },
                )
                if (keyPresent) {
                    Spacer(modifier = Modifier.height(12.dp))
                    ConfirmDialogButton(
                        label = "Remove saved key",
                        background = actionButtonBackground(highContrast, whiteMode, Color(0xFFC62828)),
                        textColor = actionButtonText(highContrast, whiteMode, Color.White),
                        contentDescription = "Remove the saved API key and disable AI features",
                        enabled = true,
                        onClick = onRemove,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                ConfirmDialogButton(
                    label = "Cancel",
                    background = if (highContrast && !whiteMode) Color(0xFF424242) else Color(0xFFE0E0E0),
                    textColor = textColor,
                    contentDescription = "Cancel without changing the key",
                    enabled = true,
                    onClick = onDismiss,
                )
            }
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
                    background = actionButtonBackground(highContrast, whiteMode, Color(0xFFC62828)),
                    textColor = actionButtonText(highContrast, whiteMode, Color.White),
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

/**
 * Single-select row (radio style). Used for the AI model picker so only one
 * option is active; the tick and the spoken state keep it clear without a popup.
 */
@Composable
private fun SettingsChoiceRow(
    label: String,
    description: String,
    selected: Boolean,
    labelColor: Color,
    highContrast: Boolean,
    whiteMode: Boolean,
    onClick: () -> Unit,
) {
    val markColor = if (highContrast) {
        if (whiteMode) Color.Black else Color.White
    } else {
        Color(0xFF3DBAD0)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .clickable { onClick() }
            .semantics {
                contentDescription = "$label. $description. " +
                    (if (selected) "Selected." else "Not selected. Double tap to select.")
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (selected) "✓" else "○",
            color = if (selected) markColor else labelColor.copy(alpha = 0.5f),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 14.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = labelColor,
                fontSize = 18.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
            Text(
                text = description,
                color = labelColor.copy(alpha = 0.7f),
                fontSize = 14.sp,
            )
        }
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
                } else Color(0xFF3DBAD0),
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
