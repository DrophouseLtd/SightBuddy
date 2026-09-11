package com.example.sightbuddy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
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
    cloudSttOffered: Boolean = false,
    onRequestCloudStt: () -> Unit = {},
    currentLanguageName: String = "",
    otherLanguageName: String = "",
    onSwitchLanguage: () -> Unit = {},
    onFeatureHideBlocked: () -> Unit = {},
    onCrashReportingChange: (Boolean) -> Unit = {},
    whisperSupported: Boolean = true,
    onLeaveFeedback: () -> Unit = {},
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
    val loopCarousel by settingsManager.loopCarousel.collectAsState()
    val pitchFeedback by settingsManager.pitchFeedback.collectAsState()
    val cloudStt by settingsManager.cloudStt.collectAsState()
    val featureActivationAnnouncements by settingsManager.featureActivationAnnouncementsEnabled.collectAsState()
    var showCocoObjectSettings by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val context = androidx.compose.ui.platform.LocalContext.current
    val closeSettingsCd = stringResource(R.string.cd_close_settings)
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
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.displayMedium,
                color = textColor,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // First row: the language everything else is presented in.
            SectionHeader(stringResource(R.string.settings_section_language), textColor)

            SettingsNavRow(
                label = stringResource(R.string.settings_language),
                description = stringResource(R.string.settings_language_desc, currentLanguageName),
                labelColor = labelColor,
                highContrast = highContrast,
                whiteMode = whiteMode,
                onClick = onSwitchLanguage,
                contentDescription = stringResource(
                    R.string.settings_language_cd,
                    currentLanguageName,
                    otherLanguageName,
                ),
            )

            Spacer(modifier = Modifier.height(24.dp))

            SectionHeader(stringResource(R.string.settings_section_cloud_ai), textColor)

            SettingsNavRow(
                label = stringResource(R.string.settings_api_key),
                description = stringResource(
                    if (apiKeyPresent) R.string.settings_api_key_desc_set
                    else R.string.settings_api_key_desc_unset
                ),
                labelColor = labelColor,
                highContrast = highContrast,
                whiteMode = whiteMode,
                onClick = { showApiKeyDialog = true },
                contentDescription = stringResource(
                    if (apiKeyPresent) R.string.settings_api_key_cd_set
                    else R.string.settings_api_key_cd_unset
                ),
            )

            // Model picker — one selected at a time. Costs go to the user's own key.
            if (apiKeyPresent) {
                SettingsManager.LLM_MODEL_OPTIONS.forEach { option ->
                    SettingsChoiceRow(
                        label = stringResource(option.labelRes),
                        description = stringResource(option.descriptionRes),
                        selected = llmModel == option.id,
                        labelColor = labelColor,
                        highContrast = highContrast,
                        whiteMode = whiteMode,
                        onClick = { settingsManager.setLlmModel(option.id) },
                    )
                }
            }

            // Directly under the model picker: it is the same decision, about which
            // OpenAI service the user's key pays for.
            if (apiKeyPresent && cloudSttOffered) {
                SettingsToggle(
                    label = stringResource(R.string.settings_cloud_stt),
                    description = stringResource(R.string.settings_cloud_stt_desc),
                    checked = cloudStt,
                    onCheckedChange = { wanted ->
                        // Turning it on always goes through the dialog; turning it off
                        // is immediate, because nothing needs consenting to.
                        if (wanted) onRequestCloudStt() else settingsManager.setCloudStt(false)
                    },
                    labelColor = labelColor,
                    highContrast = highContrast,
                    whiteMode = whiteMode
                )
            }

            // Hidden entirely in languages the on-device model cannot serve: the
            // bundled Whisper build is base.en, so non-English users stay on the
            // system recogniser and are never offered a 154 MB download.
            if (whisperSupported) {
                Spacer(modifier = Modifier.height(24.dp))

                SectionHeader(stringResource(R.string.settings_section_voice), textColor)

                SettingsNavRow(
                    label = stringResource(R.string.settings_stt_models),
                    description = when {
                        sttModelsReady -> stringResource(R.string.settings_stt_downloaded)
                        sttDownloadPercent != null ->
                            stringResource(R.string.settings_stt_downloading, sttDownloadPercent)
                        else -> stringResource(R.string.settings_stt_not_downloaded)
                    },
                    labelColor = labelColor,
                    highContrast = highContrast,
                    whiteMode = whiteMode,
                    onClick = {
                        if (!sttModelsReady && sttDownloadPercent == null) onRequestSttDownload()
                    },
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            SectionHeader(stringResource(R.string.settings_section_features), textColor)

            if (apiKeyPresent) {
                SettingsToggle(
                    label = stringResource(R.string.help_title_image_chat),
                    description = stringResource(
                        R.string.settings_show_in_carousel,
                        stringResource(R.string.help_title_image_chat),
                    ),
                    checked = imageChat,
                    onCheckedChange = { settingsManager.setImageChat(it) },
                    labelColor = labelColor,
                    highContrast = highContrast,
                    whiteMode = whiteMode
                )
            }

            SettingsToggle(
                label = stringResource(R.string.help_title_text_chat),
                description = stringResource(
                        R.string.settings_show_in_carousel,
                        stringResource(R.string.help_title_text_chat),
                    ),
                checked = textChat,
                onCheckedChange = { if (!settingsManager.setTextChat(it)) onFeatureHideBlocked() },
                labelColor = labelColor,
                highContrast = highContrast,
                whiteMode = whiteMode
            )

            SettingsToggle(
                label = stringResource(R.string.help_title_discover_objects),
                description = stringResource(
                        R.string.settings_show_in_carousel,
                        stringResource(R.string.help_title_discover_objects),
                    ),
                checked = discover,
                onCheckedChange = { if (!settingsManager.setDiscover(it)) onFeatureHideBlocked() },
                labelColor = labelColor,
                highContrast = highContrast,
                whiteMode = whiteMode
            )

            SettingsToggle(
                label = stringResource(R.string.help_title_find_objects),
                description = stringResource(
                        R.string.settings_show_in_carousel,
                        stringResource(R.string.help_title_find_objects),
                    ),
                checked = find,
                onCheckedChange = { if (!settingsManager.setFind(it)) onFeatureHideBlocked() },
                labelColor = labelColor,
                highContrast = highContrast,
                whiteMode = whiteMode
            )

            SettingsToggle(
                label = stringResource(R.string.help_title_scan_light),
                description = stringResource(
                        R.string.settings_show_in_carousel,
                        stringResource(R.string.help_title_scan_light),
                    ),
                checked = scanLight,
                onCheckedChange = { if (!settingsManager.setScanLight(it)) onFeatureHideBlocked() },
                labelColor = labelColor,
                highContrast = highContrast,
                whiteMode = whiteMode
            )

            SettingsToggle(
                label = stringResource(R.string.help_title_scan_colour),
                description = stringResource(
                        R.string.settings_show_in_carousel,
                        stringResource(R.string.help_title_scan_colour),
                    ),
                checked = scanColour,
                onCheckedChange = { if (!settingsManager.setScanColour(it)) onFeatureHideBlocked() },
                labelColor = labelColor,
                highContrast = highContrast,
                whiteMode = whiteMode
            )

            Spacer(modifier = Modifier.height(24.dp))

            SectionHeader(stringResource(R.string.settings_section_prefs), textColor)

            if (apiKeyPresent) {
                SettingsToggle(
                    label = stringResource(R.string.settings_auto_capture),
                    description = stringResource(R.string.settings_auto_capture_desc),
                    checked = autoCapture,
                    onCheckedChange = { settingsManager.setAutoCapture(it) },
                    labelColor = labelColor,
                    highContrast = highContrast,
                    whiteMode = whiteMode
                )

                SettingsToggle(
                    label = stringResource(R.string.settings_text_preview),
                    description = stringResource(R.string.settings_text_preview_desc),
                    checked = textPreview,
                    onCheckedChange = { settingsManager.setTextPreview(it) },
                    labelColor = labelColor,
                    highContrast = highContrast,
                    whiteMode = whiteMode
                )
            }

            SettingsToggle(
                label = stringResource(R.string.settings_high_contrast),
                description = stringResource(R.string.settings_high_contrast_desc),
                checked = highContrastMode,
                onCheckedChange = { settingsManager.setHighContrast(it) },
                labelColor = labelColor,
                highContrast = highContrast,
                whiteMode = whiteMode
            )

            if (highContrast) {
                SettingsToggle(
                    label = stringResource(R.string.settings_white_mode),
                    description = stringResource(R.string.settings_white_mode_desc),
                    checked = whiteMode,
                    onCheckedChange = { settingsManager.setWhiteMode(it) },
                    labelColor = labelColor,
                    highContrast = highContrast,
                    whiteMode = whiteMode
                )
            }

            SettingsToggle(
                label = stringResource(R.string.settings_button_nav),
                description = stringResource(R.string.settings_button_nav_desc),
                checked = buttonNav,
                onCheckedChange = { settingsManager.setButtonNav(it) },
                labelColor = labelColor,
                highContrast = highContrast,
                whiteMode = whiteMode
            )

            SettingsToggle(
                label = stringResource(R.string.settings_pitch_feedback),
                description = stringResource(R.string.settings_pitch_feedback_desc),
                checked = pitchFeedback,
                onCheckedChange = { settingsManager.setPitchFeedback(it) },
                labelColor = labelColor,
                highContrast = highContrast,
                whiteMode = whiteMode
            )

            SettingsToggle(
                label = stringResource(R.string.settings_loop_carousel),
                description = stringResource(R.string.settings_loop_carousel_desc),
                checked = loopCarousel,
                onCheckedChange = { settingsManager.setLoopCarousel(it) },
                labelColor = labelColor,
                highContrast = highContrast,
                whiteMode = whiteMode
            )

            // Withdrawn; see HOLD_TO_SPEAK_ENABLED in MainActivity.
            if (false) {
                SettingsToggle(
                    label = stringResource(R.string.settings_hold_to_speak),
                    description = stringResource(R.string.settings_hold_to_speak_desc),
                    checked = holdToSpeak,
                    // Nothing to do with the voice models: this only changes how the
                    // Ask button behaves, so it never offers a download.
                    onCheckedChange = { settingsManager.setHoldToSpeak(it) },
                    labelColor = labelColor,
                    highContrast = highContrast,
                    whiteMode = whiteMode
                )
            }

            SettingsNavRow(
                label = stringResource(R.string.settings_speech_rate),
                description = stringResource(
                    R.string.settings_speech_rate_desc,
                    SettingsManager.ttsRateLabel(context, ttsRate),
                ),
                labelColor = labelColor,
                highContrast = highContrast,
                whiteMode = whiteMode,
                onClick = { onSpeechRateCycle() },
                contentDescription = stringResource(
                    R.string.settings_speech_rate_cd,
                    SettingsManager.ttsRateLabel(context, ttsRate),
                ),
            )

            SettingsToggle(
                label = stringResource(R.string.settings_feature_announcements),
                description = stringResource(R.string.settings_feature_announcements_desc),
                checked = featureActivationAnnouncements,
                onCheckedChange = { settingsManager.setFeatureActivationAnnouncements(it) },
                labelColor = labelColor,
                highContrast = highContrast,
                whiteMode = whiteMode
            )

            SettingsNavRow(
                label = stringResource(R.string.settings_object_list),
                description = stringResource(R.string.settings_object_list_desc),
                labelColor = labelColor,
                highContrast = highContrast,
                whiteMode = whiteMode,
                onClick = { showCocoObjectSettings = true },
            )

            Spacer(modifier = Modifier.height(24.dp))

            Spacer(modifier = Modifier.height(24.dp))

            SectionHeader(stringResource(R.string.settings_section_privacy), textColor)

            SettingsToggle(
                label = stringResource(R.string.settings_crash_reports),
                description = stringResource(R.string.settings_crash_reports_desc),
                checked = crashReporting,
                onCheckedChange = {
                    settingsManager.setCrashReporting(it)
                    onCrashReportingChange(it)
                },
                labelColor = labelColor,
                highContrast = highContrast,
                whiteMode = whiteMode
            )

            Spacer(modifier = Modifier.height(24.dp))

            SectionHeader(stringResource(R.string.settings_feedback_section), textColor)

            ConfirmDialogButton(
                label = stringResource(R.string.settings_feedback_button),
                background = actionButtonBackground(highContrast, whiteMode, Color(0xFF8EEFCA)),
                textColor = actionButtonText(highContrast, whiteMode, Color.Black),
                contentDescription = stringResource(R.string.settings_feedback_button_cd),
                enabled = true,
                onClick = onLeaveFeedback,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Spoken/visible fallback for devices with no mail app configured,
            // where the mailto link cannot open anything.
            // Selectable so the address can be long-pressed and copied on devices
            // with no mail app, where the mailto link opens nothing.
            SelectionContainer {
                Text(
                    text = stringResource(R.string.settings_feedback_fallback),
                    color = labelColor.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

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
                .semantics { contentDescription = closeSettingsCd },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.btn_back),
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
    val apiInputCd = stringResource(R.string.settings_api_input_cd)
    val showCd = stringResource(R.string.settings_show_cd)
    val hideCd = stringResource(R.string.settings_hide_cd)
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
                    text = stringResource(R.string.settings_api_key),
                    style = MaterialTheme.typography.headlineSmall,
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_api_dialog_body),
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
                        .semantics { contentDescription = apiInputCd },
                    placeholder = { Text("sk-…", color = textColor.copy(alpha = 0.5f)) },
                    singleLine = true,
                    visualTransformation = if (keyVisible) {
                        androidx.compose.ui.text.input.VisualTransformation.None
                    } else {
                        androidx.compose.ui.text.input.PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        Text(
                            text = stringResource(if (keyVisible) R.string.settings_hide else R.string.settings_show),
                            color = if (highContrast) textColor else Color(0xFF3DBAD0),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { keyVisible = !keyVisible }
                                .padding(horizontal = 12.dp)
                                .semantics {
                                    contentDescription =
                                        if (keyVisible) hideCd else showCd
                                },
                        )
                    },
                )
                Spacer(modifier = Modifier.height(20.dp))
                ConfirmDialogButton(
                    label = stringResource(R.string.settings_save_key),
                    background = actionButtonBackground(highContrast, whiteMode, Color(0xFF8EEFCA)),
                    textColor = actionButtonText(highContrast, whiteMode, Color.Black),
                    contentDescription = stringResource(R.string.settings_save_key_cd),
                    enabled = keyInput.isNotBlank(),
                    onClick = { onSave(keyInput) },
                )
                if (keyPresent) {
                    Spacer(modifier = Modifier.height(12.dp))
                    ConfirmDialogButton(
                        label = stringResource(R.string.settings_remove_key),
                        background = actionButtonBackground(highContrast, whiteMode, Color(0xFFC62828)),
                        textColor = actionButtonText(highContrast, whiteMode, Color.White),
                        contentDescription = stringResource(R.string.settings_remove_key_cd),
                        enabled = true,
                        onClick = onRemove,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                ConfirmDialogButton(
                    label = stringResource(R.string.settings_cancel),
                    background = if (highContrast && !whiteMode) Color(0xFF424242) else Color(0xFFE0E0E0),
                    textColor = textColor,
                    contentDescription = stringResource(R.string.settings_cancel_cd),
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
    val rowPrefix = stringResource(R.string.cd_row, label, description)
    val selectedCd = stringResource(R.string.cd_selected)
    val notSelectedCd = stringResource(R.string.cd_not_selected)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .clickable { onClick() }
            .semantics {
                contentDescription = rowPrefix + (if (selected) selectedCd else notSelectedCd)
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
    val rowPrefix = stringResource(R.string.cd_row, label, description)
    val onCd = stringResource(R.string.cd_currently_on)
    val offCd = stringResource(R.string.cd_currently_off)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .semantics {
                contentDescription = rowPrefix + (if (checked) onCd else offCd)
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
