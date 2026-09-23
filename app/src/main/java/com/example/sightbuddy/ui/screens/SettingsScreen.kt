package com.example.sightbuddy.ui.screens

import com.example.sightbuddy.ui.theme.currentBrand
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.sightbuddy.R
import com.example.sightbuddy.ui.DialogWindowTitle
import com.example.sightbuddy.ui.buttonSemantics
import com.example.sightbuddy.core.SettingsManager
import com.example.sightbuddy.ui.theme.actionButtonBackground
import com.example.sightbuddy.ui.theme.actionButtonText
import kotlinx.coroutines.launch
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.semantics.isTraversalGroup
import android.content.ClipData
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.example.sightbuddy.core.ApiKeyStore

@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    highContrast: Boolean,
    whiteMode: Boolean,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    apiKeyPresent: Boolean = false,
    savedKeyPreview: String = "",
    onSaveApiKey: (String) -> Unit = {},
    onSpeechRateCycle: () -> Unit = {},
    sttModelsReady: Boolean = false,
    sttDownloadPercent: Int? = null,
    onRequestSttDownload: () -> Unit = {},
    onManageSttModels: () -> Unit = {},
    cloudSttOffered: Boolean = false,
    onRequestCloudStt: () -> Unit = {},
    currentLanguageName: String = "",
    otherLanguageName: String = "",
    onSwitchLanguage: () -> Unit = {},
    onFeatureHideBlocked: () -> Unit = {},
    onCrashReportingChange: (Boolean) -> Unit = {},
    whisperSupported: Boolean = true,
    gemmaInstalled: Boolean = false,
    gemmaDownloadPercent: Int? = null,
    onManageGemma: () -> Unit = {},
    onRequestGemmaDownload: () -> Unit = {},
    sttChoiceLabel: String = "",
    setupOffered: Boolean = false,
    setupDescription: String = "",
    onRequestSetup: () -> Unit = {},
    gemmaDownloadable: Boolean = true,
    gemmaRecommended: Boolean = true,
    whisperRecommended: Boolean = true,
    gemmaPartialPercent: Int = 0,
    onSttChoiceCycle: () -> Unit = {},
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
    val holdToSpeak by settingsManager.holdToSpeak.collectAsState()
    val autoCapture by settingsManager.autoCaptureEnabled.collectAsState()
    val textScanGuidance by settingsManager.textScanGuidance.collectAsState()
    val liveText by settingsManager.liveText.collectAsState()
    val llmModel by settingsManager.llmModel.collectAsState()
    val crashReporting by settingsManager.crashReportingEnabled.collectAsState()
    val ttsRate by settingsManager.ttsSpeechRate.collectAsState()
    val loopCarousel by settingsManager.loopCarousel.collectAsState()
    val pitchFeedback by settingsManager.pitchFeedback.collectAsState()
    val hapticFeedback by settingsManager.hapticFeedback.collectAsState()
    val cameraPreview by settingsManager.cameraPreview.collectAsState()
    val startFeaturesMuted by settingsManager.startFeaturesMuted.collectAsState()
    val cloudStt by settingsManager.cloudStt.collectAsState()
    val useApi by settingsManager.useApi.collectAsState()
    val useLocalChat by settingsManager.useLocalChat.collectAsState()
    var showCocoObjectSettings by remember { mutableStateOf(false) }
    var showLicences by remember { mutableStateOf(false) }
    // Advanced settings, folded away until asked for.
    var advancedOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val backLabel = stringResource(R.string.btn_back)

    val brand = currentBrand()
    val textColor = if (highContrast) {
        if (whiteMode) Color.Black else Color.White
    } else {
        brand.ink
    }
    val labelColor = if (highContrast) {
        if (whiteMode) Color.Black else Color.White.copy(alpha = 0.85f)
    } else {
        brand.ink
    }
    // Section headings in the owl's blue.
    val headerColor = if (highContrast) textColor else brand.accent

    // System back closes the current settings view instead of exiting the app:
    // the object sub-list returns to the settings list; the settings list closes.
    BackHandler {
        when {
            showCocoObjectSettings -> showCocoObjectSettings = false
            showLicences -> showLicences = false
            else -> onBack()
        }
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

    if (showLicences) {
        LicencesScreen(
            textColor = textColor,
            labelColor = labelColor,
            headerColor = headerColor,
            highContrast = highContrast,
            whiteMode = whiteMode,
            onBack = { showLicences = false },
            modifier = modifier,
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
            // No page title: TalkBack already announces Settings as it opens, so a
            // heading here was the same word twice. The space keeps the first row
            // clear of the Back button.
            Spacer(modifier = Modifier.height(72.dp))

            // Every section says how many settings it holds, and every row its
            // place in the section ("2 of 5"): TalkBack users cannot see where a
            // list ends. Rows are listed first so the count is known.

            // First: the language everything else is presented in.
            SettingsSection(stringResource(R.string.settings_section_language), headerColor, rowsOf {
                SettingsNavRow(
                    label = stringResource(R.string.settings_language),
                    description = stringResource(R.string.settings_language_desc, currentLanguageName),
                    labelColor = labelColor,
                    highContrast = highContrast,
                    whiteMode = whiteMode,
                    onClick = onSwitchLanguage,
                    contentDescription = stringResource(R.string.settings_language_cd, currentLanguageName),
                    action = stringResource(R.string.settings_language_action, otherLanguageName),
                )
            })

            // Model library: the setup offer until a model is installed, then the
            // installed models and how they are used. Downloads of single models are
            // under Advanced.
            val libraryRows = buildList<SettingRow> {
                if (setupOffered) add {
                    SettingsNavRow(
                        label = stringResource(R.string.settings_setup),
                        description = setupDescription,
                        labelColor = labelColor,
                        highContrast = highContrast,
                        whiteMode = whiteMode,
                        onClick = onRequestSetup,
                    )
                }
                // The models recommended for this phone, downloaded or not, and any
                // other model once it is on the device.
                if (sttModelsReady || sttDownloadPercent != null || (whisperSupported && whisperRecommended)) add {
                    SettingsNavRow(
                        label = stringResource(R.string.settings_stt_models),
                        description = when {
                            sttModelsReady -> stringResource(R.string.settings_stt_downloaded)
                            sttDownloadPercent != null ->
                                stringResource(R.string.settings_stt_downloading, sttDownloadPercent)
                            else -> stringResource(R.string.settings_stt_not_downloaded)
                        },
                        action = if (!sttModelsReady && sttDownloadPercent == null) {
                            stringResource(R.string.settings_action_download)
                        } else null,
                        labelColor = labelColor,
                        highContrast = highContrast,
                        whiteMode = whiteMode,
                        onClick = {
                            when {
                                sttModelsReady -> onManageSttModels()
                                sttDownloadPercent == null -> onRequestSttDownload()
                            }
                        },
                    )
                }
                if (gemmaInstalled || gemmaDownloadPercent != null || (gemmaDownloadable && gemmaRecommended)) add {
                    SettingsNavRow(
                        label = stringResource(R.string.settings_gemma_model),
                        description = when {
                            gemmaInstalled -> stringResource(R.string.settings_gemma_downloaded)
                            gemmaDownloadPercent != null ->
                                stringResource(R.string.settings_stt_downloading, gemmaDownloadPercent)
                            gemmaPartialPercent > 0 ->
                                stringResource(R.string.settings_gemma_paused, gemmaPartialPercent)
                            else -> stringResource(R.string.settings_gemma_not_downloaded)
                        },
                        action = when {
                            gemmaInstalled || gemmaDownloadPercent != null -> null
                            gemmaPartialPercent > 0 -> stringResource(R.string.settings_action_continue_download)
                            else -> stringResource(R.string.settings_action_download)
                        },
                        labelColor = labelColor,
                        highContrast = highContrast,
                        whiteMode = whiteMode,
                        onClick = {
                            when {
                                gemmaInstalled -> onManageGemma()
                                gemmaDownloadPercent == null -> onRequestGemmaDownload()
                            }
                        },
                    )
                }
                // Which recogniser runs, cycled like the speech rate. Only a choice
                // once a speech model is on the device.
                if (whisperSupported && (sttModelsReady || gemmaInstalled)) add {
                    SettingsNavRow(
                        label = stringResource(R.string.settings_stt_engine),
                        description = stringResource(R.string.settings_stt_engine_desc, sttChoiceLabel),
                        labelColor = labelColor,
                        highContrast = highContrast,
                        whiteMode = whiteMode,
                        onClick = onSttChoiceCycle,
                        contentDescription = stringResource(R.string.settings_stt_engine_cd, sttChoiceLabel),
                        action = stringResource(R.string.settings_cycle_action),
                    )
                }
                if (gemmaInstalled && gemmaDownloadable) add {
                    SettingsToggle(
                        label = stringResource(R.string.settings_use_local_chat),
                        description = stringResource(R.string.settings_use_local_chat_desc),
                        checked = useLocalChat,
                        onCheckedChange = { settingsManager.setUseLocalChat(it) },
                        labelColor = labelColor,
                        highContrast = highContrast,
                        whiteMode = whiteMode
                    )
                }
            }
            if (libraryRows.isNotEmpty()) {
                SettingsSection(stringResource(R.string.settings_section_models), headerColor, libraryRows)
            }

            SettingsSection(stringResource(R.string.settings_section_features), headerColor, buildList<SettingRow> {
                if (apiKeyPresent) add {
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
                add {
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
                }
                add {
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
                }
                add {
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
                }
                add {
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
                }
                add {
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
                }
            })

            SettingsSection(stringResource(R.string.settings_section_prefs), headerColor, buildList<SettingRow> {
                add {
                    SettingsToggle(
                        label = stringResource(R.string.settings_text_scan_guidance),
                        description = stringResource(R.string.settings_text_scan_guidance_desc),
                        checked = textScanGuidance,
                        onCheckedChange = { settingsManager.setTextScanGuidance(it) },
                        labelColor = labelColor,
                        highContrast = highContrast,
                        whiteMode = whiteMode
                    )
                }
                add {
                    SettingsToggle(
                        label = stringResource(R.string.settings_live_text),
                        description = stringResource(R.string.settings_live_text_desc),
                        checked = liveText,
                        onCheckedChange = { settingsManager.setLiveText(it) },
                        labelColor = labelColor,
                        highContrast = highContrast,
                        whiteMode = whiteMode
                    )
                }
                if (apiKeyPresent) {
                    add {
                        SettingsToggle(
                            label = stringResource(R.string.settings_auto_capture),
                            description = stringResource(R.string.settings_auto_capture_desc),
                            checked = autoCapture,
                            onCheckedChange = { settingsManager.setAutoCapture(it) },
                            labelColor = labelColor,
                            highContrast = highContrast,
                            whiteMode = whiteMode
                        )
                    }
                    add {
                        SettingsToggle(
                            label = stringResource(R.string.settings_text_preview),
                            description = stringResource(R.string.settings_text_preview_desc),
                            // The stored setting is the opposite, "read the raw text
                            // first", so the switch shows summarization as on only
                            // when it really is. Off by default.
                            checked = !textPreview,
                            onCheckedChange = { settingsManager.setTextPreview(!it) },
                            labelColor = labelColor,
                            highContrast = highContrast,
                            whiteMode = whiteMode
                        )
                    }
                }
                add {
                    SettingsToggle(
                        label = stringResource(R.string.settings_camera_preview),
                        description = stringResource(R.string.settings_camera_preview_desc),
                        checked = cameraPreview,
                        onCheckedChange = { settingsManager.setCameraPreview(it) },
                        labelColor = labelColor,
                        highContrast = highContrast,
                        whiteMode = whiteMode
                    )
                }
                add {
                    SettingsToggle(
                        label = stringResource(R.string.settings_high_contrast),
                        description = stringResource(R.string.settings_high_contrast_desc),
                        checked = highContrastMode,
                        onCheckedChange = { settingsManager.setHighContrast(it) },
                        labelColor = labelColor,
                        highContrast = highContrast,
                        whiteMode = whiteMode
                    )
                }
                if (highContrast) add {
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
                add {
                    SettingsToggle(
                        label = stringResource(R.string.settings_pitch_feedback),
                        description = stringResource(R.string.settings_pitch_feedback_desc),
                        checked = pitchFeedback,
                        onCheckedChange = { settingsManager.setPitchFeedback(it) },
                        labelColor = labelColor,
                        highContrast = highContrast,
                        whiteMode = whiteMode
                    )
                }
                add {
                    SettingsToggle(
                        label = stringResource(R.string.settings_haptic_feedback),
                        description = stringResource(R.string.settings_haptic_feedback_desc),
                        checked = hapticFeedback,
                        onCheckedChange = { settingsManager.setHapticFeedback(it) },
                        labelColor = labelColor,
                        highContrast = highContrast,
                        whiteMode = whiteMode
                    )
                }
                add {
                    SettingsToggle(
                        label = stringResource(R.string.settings_start_features_muted),
                        description = stringResource(R.string.settings_start_features_muted_desc),
                        checked = startFeaturesMuted,
                        onCheckedChange = { settingsManager.setStartFeaturesMuted(it) },
                        labelColor = labelColor,
                        highContrast = highContrast,
                        whiteMode = whiteMode
                    )
                }
                add {
                    SettingsToggle(
                        label = stringResource(R.string.settings_loop_carousel),
                        description = stringResource(R.string.settings_loop_carousel_desc),
                        checked = loopCarousel,
                        onCheckedChange = { settingsManager.setLoopCarousel(it) },
                        labelColor = labelColor,
                        highContrast = highContrast,
                        whiteMode = whiteMode
                    )
                }
                // Withdrawn; see HOLD_TO_SPEAK_ENABLED in MainActivity.
                if (false) add {
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
                add {
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
                        action = stringResource(R.string.settings_cycle_action),
                    )
                }
                add {
                    SettingsNavRow(
                        label = stringResource(R.string.settings_object_list),
                        description = stringResource(R.string.settings_object_list_desc),
                        labelColor = labelColor,
                        highContrast = highContrast,
                        whiteMode = whiteMode,
                        onClick = { showCocoObjectSettings = true },
                    )
                }
            })

            SettingsSection(stringResource(R.string.settings_section_privacy), headerColor, buildList<SettingRow> {
                add {
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
                }
                add {
                    SettingsNavRow(
                        label = stringResource(R.string.settings_licences),
                        description = stringResource(R.string.settings_licences_desc),
                        labelColor = labelColor,
                        highContrast = highContrast,
                        whiteMode = whiteMode,
                        onClick = { showLicences = true },
                    )
                }
            })

            // Advanced: one row that folds the rest out, in place. Inside for now:
            // OpenAI, which most people never need to open.
            SettingsSection(stringResource(R.string.settings_section_advanced), headerColor, rowsOf {
                SettingsExpandRow(
                    label = stringResource(R.string.settings_advanced),
                    expanded = advancedOpen,
                    labelColor = labelColor,
                    onToggle = { advancedOpen = !advancedOpen },
                )
            })

            if (advancedOpen) {
                // Any model on any phone, for those who want to try; the setup offer
                // above is the recommendation.
                val downloadRows = buildList<SettingRow> {
                    // Only what the library above does not already offer.
                    if (whisperSupported && !whisperRecommended && !sttModelsReady && sttDownloadPercent == null) add {
                        SettingsNavRow(
                            label = stringResource(R.string.settings_stt_models),
                            description = stringResource(R.string.settings_stt_not_downloaded),
                            action = stringResource(R.string.settings_action_download),
                            labelColor = labelColor,
                            highContrast = highContrast,
                            whiteMode = whiteMode,
                            onClick = onRequestSttDownload,
                        )
                    }
                    if (gemmaDownloadable && !gemmaRecommended && !gemmaInstalled && gemmaDownloadPercent == null) add {
                        SettingsNavRow(
                            label = stringResource(R.string.settings_gemma_model),
                            description = stringResource(
                                if (gemmaRecommended) R.string.settings_gemma_not_downloaded
                                else R.string.settings_gemma_not_recommended
                            ),
                            action = stringResource(R.string.settings_action_download),
                            labelColor = labelColor,
                            highContrast = highContrast,
                            whiteMode = whiteMode,
                            onClick = onRequestGemmaDownload,
                        )
                    }
                }
                if (downloadRows.isNotEmpty()) {
                    SettingsSection(stringResource(R.string.settings_section_downloads), headerColor, downloadRows)
                }
                SettingsSection(stringResource(R.string.settings_section_cloud_ai), headerColor, buildList<SettingRow> {
                    add {
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
                            action = stringResource(
                                if (apiKeyPresent) R.string.settings_api_key_action_set
                                else R.string.settings_api_key_action_unset
                            ),
                        )
                    }
                    // Hidden until there is a key to use. Off means nothing goes to OpenAI.
                    if (apiKeyPresent) add {
                        SettingsToggle(
                            label = stringResource(R.string.settings_use_api),
                            description = stringResource(R.string.settings_use_api_desc),
                            checked = useApi,
                            onCheckedChange = { settingsManager.setUseApi(it) },
                            labelColor = labelColor,
                            highContrast = highContrast,
                            whiteMode = whiteMode
                        )
                    }
                    // Model picker — one selected at a time. Costs go to the user's own key.
                    if (apiKeyPresent && useApi) {
                        SettingsManager.LLM_MODEL_OPTIONS.forEach { option ->
                            add {
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
                    }
                    // Directly under the model picker: it is the same decision, about
                    // which OpenAI service the user's key pays for.
                    if (apiKeyPresent && useApi && cloudSttOffered) add {
                        SettingsToggle(
                            label = stringResource(R.string.settings_cloud_stt),
                            description = stringResource(R.string.settings_cloud_stt_desc),
                            checked = cloudStt,
                            onCheckedChange = { wanted ->
                                // Turning it on always goes through the dialog; turning it
                                // off is immediate, because nothing needs consenting to.
                                if (wanted) onRequestCloudStt() else settingsManager.setCloudStt(false)
                            },
                            labelColor = labelColor,
                            highContrast = highContrast,
                            whiteMode = whiteMode
                        )
                    }
                })
            }

            SettingsSection(stringResource(R.string.settings_feedback_section), headerColor, rowsOf {
                ConfirmDialogButton(
                    label = stringResource(R.string.settings_feedback_button),
                    background = actionButtonBackground(highContrast, whiteMode, Color(0xFF8EEFCA)),
                    textColor = actionButtonText(highContrast, whiteMode, Color.Black),
                    contentDescription = stringResource(R.string.settings_feedback_button_cd),
                    enabled = true,
                    onClick = onLeaveFeedback,
                )
            }, spaceAfter = false)

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

            Spacer(modifier = Modifier.height(24.dp))

            // Last on the page, so feedback and support can ask for it.
            Text(
                text = stringResource(
                    R.string.settings_version,
                    com.example.sightbuddy.BuildConfig.VERSION_NAME,
                    com.example.sightbuddy.BuildConfig.VERSION_CODE,
                ),
                color = labelColor.copy(alpha = 0.7f),
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(32.dp))
        }

        if (showApiKeyDialog) {
            ApiKeyDialog(
                highContrast = highContrast,
                whiteMode = whiteMode,
                keyPresent = apiKeyPresent,
                savedKeyPreview = savedKeyPreview,
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



        // Back: styled and placed like the feature top bar's buttons (four equal
        // columns), in the column Settings was opened from.
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                // First in TalkBack's order, as it is on screen: drawn after the
                // list, it came last, so focus left on it (where the button that
                // opened this screen was) made a swipe right find nothing and a
                // swipe left walk the list backwards.
                .semantics { isTraversalGroup = true; traversalIndex = -1f }
                .fillMaxWidth()
                .padding(top = 24.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            repeat(3) { Spacer(modifier = Modifier.weight(1f)) }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        when {
                            highContrast && whiteMode -> Color.Black
                            highContrast -> Color.White
                            else -> Color.White.copy(alpha = 0.25f)
                        }
                    )
                    .clickable { onBack() }
                    .buttonSemantics(backLabel),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.btn_back),
                    color = when {
                        highContrast && whiteMode -> Color.White
                        highContrast -> Color.Black
                        else -> textColor
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ApiKeyDialog(
    highContrast: Boolean,
    whiteMode: Boolean,
    keyPresent: Boolean,
    savedKeyPreview: String,
    onSave: (String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val apiInputCd = stringResource(
        if (keyPresent) R.string.settings_api_input_replace_cd else R.string.settings_api_input_cd
    )
    val savedKeyCd = stringResource(R.string.settings_saved_key_cd, savedKeyPreview)
    val copyLabel = stringResource(R.string.settings_copy_saved_key)
    val copiedLabel = stringResource(R.string.settings_copied)
    val copyCd = stringResource(R.string.settings_copy_saved_key_cd)
    val clipboard = LocalClipboard.current
    val clipScope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    val showCd = stringResource(R.string.settings_show_cd)
    val hideCd = stringResource(R.string.settings_hide_cd)
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
    var keyInput by remember { mutableStateOf("") }
    var keyVisible by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        // Draws under the keyboard so imePadding below can make room for it.
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        DialogWindowTitle(stringResource(R.string.settings_api_key))
        // A key is not a password: keep password managers such as LastPass from
        // offering to fill or save it.
        val dialogView = LocalView.current
        DisposableEffect(dialogView) {
            dialogView.importantForAutofill =
                View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            onDispose { }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .systemBarsPadding()
                .imePadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Scrolls, so the buttons stay reachable while the keyboard is open.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(cardBg)
                    .verticalScroll(rememberScrollState())
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
                // The saved key, shortened, so the user can tell which key it is.
                // Selectable and copyable; the full key never leaves the store.
                if (keyPresent && savedKeyPreview.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .semantics(mergeDescendants = true) { contentDescription = savedKeyCd },
                        ) {
                            Text(
                                text = stringResource(R.string.settings_saved_key),
                                color = textColor.copy(alpha = 0.8f),
                                fontSize = 14.sp,
                            )
                            SelectionContainer {
                                Text(
                                    text = savedKeyPreview,
                                    color = textColor,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                        Text(
                            text = if (copied) copiedLabel else copyLabel,
                            color = if (highContrast) textColor else currentBrand().accent,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable {
                                    clipScope.launch {
                                        clipboard.setClipEntry(
                                            ClipEntry(
                                                ClipData.newPlainText("key", savedKeyPreview)
                                            )
                                        )
                                    }
                                    copied = true
                                }
                                .padding(12.dp)
                                .buttonSemantics(if (copied) copiedLabel else copyLabel),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = apiInputCd },
                    placeholder = {
                        Text(
                            stringResource(
                                if (keyPresent) R.string.settings_api_placeholder_replace
                                else R.string.settings_api_placeholder
                            ),
                            color = textColor.copy(alpha = 0.5f),
                        )
                    },
                    singleLine = true,
                    // Plain text, not a password field: TalkBack said "password", and
                    // password managers offered to fill and save it.
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        autoCorrectEnabled = false,
                    ),
                    visualTransformation = if (keyVisible) {
                        VisualTransformation.None
                    } else {
                        KeyPreviewTransformation
                    },
                    // Only acts on what is typed here, so it only appears once
                    // there is something to show; the saved key is shown above.
                    trailingIcon = if (keyInput.isEmpty()) null else {
                        {
                            Text(
                                text = stringResource(if (keyVisible) R.string.settings_hide else R.string.settings_show),
                                color = if (highContrast) textColor else currentBrand().accent,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable { keyVisible = !keyVisible }
                                    .padding(horizontal = 12.dp)
                                    .buttonSemantics(
                                        stringResource(if (keyVisible) R.string.settings_hide else R.string.settings_show)
                                    ),
                            )
                        }
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

/**
 * Shows an entered key the way the saved one is shown: its start, an ellipsis,
 * and its last four. Hides most of it without being a password field, and reads
 * aloud as something short rather than a hundred and sixty bullets.
 */
private object KeyPreviewTransformation : VisualTransformation {
    private const val HEAD = ApiKeyStore.PREVIEW_HEAD
    private const val TAIL = ApiKeyStore.PREVIEW_TAIL

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.length <= HEAD + TAIL) {
            return TransformedText(
                text, OffsetMapping.Identity
            )
        }
        val shown = ApiKeyStore.preview(raw)
        val tailStart = raw.length - TAIL
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = when {
                offset <= HEAD -> offset
                offset >= tailStart -> HEAD + 1 + (offset - tailStart)
                else -> HEAD
            }

            override fun transformedToOriginal(offset: Int): Int = when {
                offset <= HEAD -> offset
                else -> tailStart + (offset - HEAD - 1)
            }
        }
        return TransformedText(
            AnnotatedString(shown), mapping
        )
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
            .buttonSemantics(spokenRow(label, LocalRowPosition.current)),
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

/**
 * What a settings row says, in parts: "Title. Description. 2 of 6". A part's own
 * full stop is dropped so none is said twice, and blank parts are skipped.
 */
private fun spokenRow(vararg parts: String): String =
    parts.map { it.trim().trimEnd('.') }.filter { it.isNotEmpty() }.joinToString(". ")

/**
 * A row that opens something or steps a value along. [action] is what a double
 * tap does, said by TalkBack as its own hint ("Double-tap to switch to Finnish"),
 * so the row itself says only what it is.
 */
@Composable
private fun SettingsNavRow(
    label: String,
    description: String,
    labelColor: Color,
    highContrast: Boolean,
    whiteMode: Boolean,
    onClick: () -> Unit,
    contentDescription: String? = null,
    action: String? = null,
) {
    val spoken = spokenRow(contentDescription ?: spokenRow(label, description), LocalRowPosition.current)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .clickable(onClickLabel = action) { onClick() }
            .clearAndSetSemantics {
                this.contentDescription = spoken
                role = Role.Button
                onClick(label = action) { onClick(); true }
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
        currentBrand().accent
    }
    // A radio button: TalkBack says "Selected" or "Not selected" itself, where
    // the user's TalkBack settings put the state.
    val spoken = spokenRow(label, description, LocalRowPosition.current)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .selectable(selected = selected, role = Role.RadioButton) { onClick() }
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.RadioButton
                this.selected = selected
                onClick { onClick(); true }
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

/** One row of a settings section. */
private typealias SettingRow = @Composable () -> Unit

private fun rowsOf(row: SettingRow): List<SettingRow> = listOf(row)

/** A row's place in its section, "2 of 5", for TalkBack. Empty outside a section. */
private val LocalRowPosition = compositionLocalOf { "" }

/**
 * A header that says how many settings follow ("Features, 5 settings") and the
 * rows, each told its place so it can say "2 of 5".
 */
@Composable
private fun SettingsSection(
    title: String,
    color: Color,
    rows: List<SettingRow>,
    spaceAfter: Boolean = true,
) {
    if (rows.isEmpty()) return
    SectionHeader(title, color, rows.size)
    rows.forEachIndexed { index, row ->
        // A lone row has no place to tell: "1 of 1" said nothing.
        val position = if (rows.size > 1) stringResource(R.string.settings_row_position, index + 1, rows.size) else ""
        CompositionLocalProvider(LocalRowPosition provides position) { row() }
    }
    if (spaceAfter) Spacer(modifier = Modifier.height(24.dp))
}

@Composable
private fun SectionHeader(title: String, color: Color, count: Int) {
    val spoken = pluralStringResource(R.plurals.settings_section_count, count, title, count)
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = color,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clearAndSetSemantics {
                contentDescription = spoken
                heading()
            }
    )
}

/** Folds a group of settings out below it, or away again. No pop-up. */
@Composable
private fun SettingsExpandRow(
    label: String,
    expanded: Boolean,
    labelColor: Color,
    onToggle: () -> Unit,
) {
    val position = LocalRowPosition.current
    val state = stringResource(if (expanded) R.string.state_expanded else R.string.state_collapsed)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 10.dp)
            .clearAndSetSemantics {
                contentDescription = spokenRow(label, position)
                stateDescription = state
                role = Role.Button
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = labelColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (expanded) "▾" else "▸",
            color = labelColor.copy(alpha = 0.8f),
            fontSize = 24.sp,
        )
    }
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
    // The whole row is the switch: one focus stop, and a double tap anywhere on
    // it toggles. A real switch, as in Android's own settings: TalkBack says
    // "On" or "Off" where the user's TalkBack settings put the state, then the
    // title, description and place ("Off. Haptic feedback. … 3 of 6. Switch"),
    // and after a toggle only the new state. The description stays fixed, so a
    // toggle is not read out in full again.
    val spoken = spokenRow(label, description, LocalRowPosition.current)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 10.dp)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Switch
                toggleableState = ToggleableState(checked)
                onClick { onCheckedChange(!checked); true }
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
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = when {
                    highContrast && whiteMode -> Color.White
                    highContrast -> Color.Black
                    else -> currentBrand().onAccent
                },
                checkedTrackColor = if (highContrast) {
                    if (whiteMode) Color.Black else Color.White
                } else currentBrand().accent,
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
