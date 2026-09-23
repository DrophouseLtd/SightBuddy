package com.example.sightbuddy

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.example.sightbuddy.core.LanguageStore
import com.example.sightbuddy.core.ModeNames
import com.example.sightbuddy.core.ModelSetup
import com.example.sightbuddy.core.SettingsManager
import com.example.sightbuddy.core.TermsStore
import com.example.sightbuddy.core.TextScriptPlayer
import com.example.sightbuddy.core.createCrashReporter
import com.example.sightbuddy.core.stt.SttChoice
import com.example.sightbuddy.core.stt.SttModelManager
import com.example.sightbuddy.features.chat.OcrDebug
import com.example.sightbuddy.features.vision.CocoFinnish
import com.example.sightbuddy.ui.screens.BarToggle
import com.example.sightbuddy.ui.screens.ChoiceRespectedDialog
import com.example.sightbuddy.ui.screens.CloudSttDialog
import com.example.sightbuddy.ui.screens.GemmaDownloadDialog
import com.example.sightbuddy.ui.screens.HelpDialog
import com.example.sightbuddy.ui.screens.HomeScreen
import com.example.sightbuddy.ui.screens.LanguageSwitchDialog
import com.example.sightbuddy.ui.screens.MemoriesScreen
import com.example.sightbuddy.ui.screens.ModelSetupDialog
import com.example.sightbuddy.ui.screens.ObjectPickerDialog
import com.example.sightbuddy.ui.screens.OcrDebugOverlay
import com.example.sightbuddy.ui.screens.SettingsScreen
import com.example.sightbuddy.ui.screens.SttModelsDialog
import com.example.sightbuddy.ui.screens.TermsAcceptanceOverlay
import com.example.sightbuddy.ui.screens.TranscriptEntry
import kotlinx.coroutines.launch
import android.app.Activity
import android.text.format.Formatter
import androidx.camera.view.PreviewView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner

/*
 * The app's screens, drawn from [AppController]. Each host collects the flows it
 * shows, so it redraws when they change; the controller's getters read the same
 * flows for the logic. One host per screen keeps every composable well under
 * ART's compiler limit.
 */

@Composable
fun AppScreens(c: AppController, modifier: Modifier = Modifier) {
    val highContrast by c.settingsManager.highContrastEnabled.collectAsState()
    val whiteMode by c.settingsManager.whiteModeEnabled.collectAsState()
    val effectiveDarkTheme = if (highContrast) !whiteMode else isSystemInDarkTheme()
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (c.showTerms) {
            TermsHost(c, effectiveDarkTheme)
        } else if (c.onboardingWait) {
            OnboardingWait(effectiveDarkTheme)
        } else if (c.showMemories) {
            MemoriesHost(c, effectiveDarkTheme, highContrast, whiteMode)
        } else if (c.showSettings) {
            SettingsHost(c, effectiveDarkTheme, highContrast, whiteMode)
        } else {
            FeaturesHost(c, effectiveDarkTheme, highContrast, whiteMode)
        }
        AppDialogs(c, highContrast, whiteMode)
    }
}

@Composable
private fun TermsHost(c: AppController, effectiveDarkTheme: Boolean) {
    with(c) {
        var languageChosen by remember { mutableStateOf(languageStore.hasChosen()) }
        TermsAcceptanceOverlay(
            darkTheme = effectiveDarkTheme,
            languageChosen = languageChosen,
            currentLanguage = languageStore.language(),
            onSelectLanguage = { tag ->
                // The screen is already in the default language, English, so
                // choosing it only records the choice: no recreate, so
                // TalkBack says "Selected" alone instead of reading the
                // button again on a rebuilt screen.
                val change = languageStore.language() != tag
                if (!languageStore.hasChosen() || change) {
                    languageStore.setLanguage(tag)
                    languageChosen = true
                    // Recreate so attachBaseContext re-resolves every resource
                    // (strings and the spoken earcons) in the new language.
                    if (change) (context as? Activity)?.recreate()
                }
            },
            onTermsOfUse = { openUrlInBrowser(TermsStore.TERMS_OF_USE_URL) },
            onPrivacyPolicy = { openUrlInBrowser(TermsStore.PRIVACY_POLICY_URL) },
            onAccept = {
                val missing = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                    .filterNot { granted(it) }
                if (missing.isEmpty() || termsPermissionRetried) {
                    acceptTerms()
                } else {
                    termsPermissionRetried = true
                    acceptAfterPermissions = true
                    requestPermissions(missing.toTypedArray())
                }
            },
            onAcceptWithoutLanguage = {
                // App speech is held back while Terms is open; this is its own reply.
                ttsService.speak(context.getString(R.string.spoken_select_language_first), flush = true, force = true)
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun OnboardingWait(effectiveDarkTheme: Boolean) {
    val waitBg = if (effectiveDarkTheme) Color.Black else Color.White
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(waitBg),
    )
}

@Composable
private fun MemoriesHost(
    c: AppController,
    effectiveDarkTheme: Boolean,
    highContrast: Boolean,
    whiteMode: Boolean,
) {
    with(c) {
        val memories by memoryStore.memories.collectAsState()
        MemoriesScreen(
            memories = memories,
            darkTheme = effectiveDarkTheme,
            highContrast = highContrast,
            whiteMode = whiteMode,
            featureLabel = { ModeNames.display(context, it) },
            onRename = { memory, name -> memoryStore.rename(memory.id, name) },
            onDelete = { memory -> memoryStore.delete(memory.id) },
            onBack = { showMemories = false },
        )
    }
}

@Composable
private fun SettingsHost(
    c: AppController,
    effectiveDarkTheme: Boolean,
    highContrast: Boolean,
    whiteMode: Boolean,
) {
    with(c) {
        // Collected so Settings redraws when they change; the getters they feed
        // (whisperSupported, setupOffered, effectiveStt...) read the same flows.
        val apiKeyPresent by apiKeyStore.keyPresent.collectAsState()
        val savedKeyPreview by apiKeyStore.keyPreview.collectAsState()
        val sttModelDownloadState by sttModelManager.downloadState.collectAsState()
        val sttModelsReady = sttModelDownloadState is SttModelManager.DownloadState.Ready
        val sttDownloadPercent = (sttModelDownloadState as? SttModelManager.DownloadState.Downloading)?.percent
        val gemmaInstalled by localGemma.installedState.collectAsState()
        val gemmaDownloadPercent by localGemma.downloadPercent.collectAsState()
        settingsManager.sttChoice.collectAsState().value
        settingsManager.cloudStt.collectAsState().value
        settingsManager.useApi.collectAsState().value
        val settingsBg = when {
            highContrast -> if (effectiveDarkTheme) Color.Black else Color.White
            else -> com.example.sightbuddy.ui.theme.brandPalette(effectiveDarkTheme).wall
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(settingsBg)
        ) {
            SettingsScreen(
                settingsManager = settingsManager,
                highContrast = highContrast,
                whiteMode = whiteMode,
                modifier = Modifier.fillMaxSize(),
                onBack = { showSettings = false },
                apiKeyPresent = apiKeyPresent,
                savedKeyPreview = savedKeyPreview,
                onSaveApiKey = { raw ->
                    apiKeyStore.setKey(raw)
                    // A newly saved key is meant to be used.
                    if (apiKeyStore.keyPresent.value) settingsManager.setUseApi(true)
                    confirmation(
                        if (apiKeyStore.keyPresent.value) {
                            context.getString(R.string.spoken_api_key_saved)
                        } else {
                            context.getString(R.string.spoken_api_key_removed)
                        },
                        flush = true,
                    )
                },
                onSpeechRateCycle = {
                    val newRate = settingsManager.cycleTtsSpeechRate()
                    // Apply before announcing. The effect that mirrors this
                    // setting runs after recomposition, so without this the
                    // preview is spoken at the rate we just moved away from.
                    ttsService.setSpeechRate(newRate)
                    confirmation(
                        context.getString(R.string.spoken_speech_rate, SettingsManager.ttsRateLabel(context, newRate)),
                        flush = true,
                    )
                },
                whisperSupported = whisperSupported,
                onLeaveFeedback = { openFeedbackEmail() },
                sttModelsReady = sttModelsReady,
                sttDownloadPercent = sttDownloadPercent,
                // Nothing about the on-device model may surface in a language
                // it cannot serve, from any entry point.
                onRequestSttDownload = {
                    if (whisperSupported) startSttDownload()
                },
                setupOffered = setupOffered,
                setupDescription = stringResource(
                    R.string.settings_setup_desc,
                    stringResource(setupModelsRes),
                    sizeLabel(ModelSetup.packageBytes(deviceTier)),
                    sizeLabel(freeSpace),
                ),
                onRequestSetup = { showSetupDialog = true },
                // Downloadable in any language from Advanced; recommended only in English.
                gemmaDownloadable = true,
                gemmaRecommended = deviceTier == ModelSetup.Tier.FULL,
                whisperRecommended = deviceTier != ModelSetup.Tier.NONE,
                gemmaPartialPercent = remember(gemmaDownloadPercent, showSettings) { localGemma.partialPercent() },
                onManageSttModels = { showSttModelsDialog = true },
                gemmaInstalled = gemmaInstalled,
                gemmaDownloadPercent = gemmaDownloadPercent,
                onManageGemma = { showGemmaDialog = true },
                onRequestGemmaDownload = { showGemmaDownloadDialog = true },
                sttChoiceLabel = stringResource(effectiveStt.labelRes),
                onSttChoiceCycle = {
                    val options = SttChoice.available(sttModelsReady, gemmaInstalled)
                    val next = options[(options.indexOf(effectiveStt) + 1) % options.size]
                    settingsManager.setSttChoice(next.key)
                    confirmation(
                        context.getString(R.string.spoken_stt_choice, context.getString(next.labelRes)),
                        flush = true,
                    )
                },
                cloudSttOffered = cloudSttOffered,
                onRequestCloudStt = { showCloudSttDialog = true },
                // Named in the app's language ("switch to Finnish"), not in
                // their own ("Suomi"), which only the language picker uses.
                currentLanguageName = stringResource(
                    if (languageStore.isFinnish()) R.string.language_name_finnish
                    else R.string.language_name_english
                ),
                otherLanguageName = stringResource(
                    if (languageStore.isFinnish()) R.string.language_name_english
                    else R.string.language_name_finnish
                ),
                onSwitchLanguage = { showLanguageDialog = true },
                onFeatureHideBlocked = {
                    confirmation(
                        "At least one feature must stay on.",
                        flush = true,
                    )
                },
                onCrashReportingChange = { enabled ->
                    createCrashReporter().setEnabled(enabled)
                },
            )
        }
    }
}

@Composable
private fun FeaturesHost(
    c: AppController,
    effectiveDarkTheme: Boolean,
    highContrast: Boolean,
    whiteMode: Boolean,
) {
    with(c) {
        val lifecycleOwner = LocalLifecycleOwner.current
        // Collected so the features redraw when they change.
        val loopCarousel by settingsManager.loopCarousel.collectAsState()
        val cameraPreviewSetting by settingsManager.cameraPreview.collectAsState()
        val lastSpokenLine by ttsService.lastSpoken.collectAsState()
        val isListening by voiceInputService.isListening.collectAsState()
        settingsManager.holdToSpeak.collectAsState().value
        val playbackState by textScriptPlayer.playbackState.collectAsState()
        val hiddenCocoObjects by settingsManager.hiddenCocoObjects.collectAsState()
        val visibleCocoObjects = remember(hiddenCocoObjects) { settingsManager.visibleCocoObjects() }
        val imageChatHistory by imageChatViewModel.chatHistory.collectAsState()
        val documentChatHistory by documentChatViewModel.chatHistory.collectAsState()
        imageChatViewModel.isProcessing.collectAsState().value
        documentChatViewModel.isProcessing.collectAsState().value
        // llmChatEnabled and the pages follow these.
        apiKeyStore.keyPresent.collectAsState().value
        settingsManager.useApi.collectAsState().value
        localGemma.installedState.collectAsState().value
        settingsManager.useLocalChat.collectAsState().value
        settingsManager.scanLightEnabled.collectAsState().value
        settingsManager.scanColourEnabled.collectAsState().value
        settingsManager.imageChatEnabled.collectAsState().value
        settingsManager.textChatEnabled.collectAsState().value
        settingsManager.discoverEnabled.collectAsState().value
        settingsManager.findEnabled.collectAsState().value
        // One conversation for every feature, kept by the controller.
        val transcriptEntries = c.transcriptEntries
        val autoCaptureOn by settingsManager.autoCaptureEnabled.collectAsState()
        val textGuidanceOn by settingsManager.textScanGuidance.collectAsState()
        val textPreviewOn by settingsManager.textPreviewEnabled.collectAsState()
        val liveTextOn by settingsManager.liveText.collectAsState()
        val pitchOn by settingsManager.pitchFeedback.collectAsState()
        val featureToggles = when (activeMode) {
            ModeNames.IMAGE_CHAT -> listOf(
                BarToggle(stringResource(R.string.bar_auto_capture), autoCaptureOn) { settingsManager.setAutoCapture(it) },
            )
            ModeNames.TEXT_CHAT -> listOfNotNull(
                BarToggle(stringResource(R.string.bar_guidance), textGuidanceOn) { settingsManager.setTextScanGuidance(it) },
                BarToggle(stringResource(R.string.bar_live_text), liveTextOn) { settingsManager.setLiveText(it) },
                // Shown as "summarise": the stored setting is its opposite (read the raw text).
                BarToggle(stringResource(R.string.bar_summarize), !textPreviewOn) { settingsManager.setTextPreview(!it) }
                    .takeIf { llmChatEnabled },
            )
            ModeNames.FIND_OBJECTS -> listOf(
                BarToggle(stringResource(R.string.bar_pitch), pitchOn) { settingsManager.setPitchFeedback(it) },
            )
            else -> emptyList()
        }
        if (!cameraDisabled) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        implementationMode =
                            PreviewView.ImplementationMode.COMPATIBLE
                        // The OCR overlay needs the whole frame the recogniser
                        // sees, not the centre crop that fills the screen.
                        if (OcrDebug.enabled) {
                            scaleType = PreviewView.ScaleType.FIT_CENTER
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { previewView ->
                    cameraXManager.startCamera(
                        lifecycleOwner,
                        previewView.surfaceProvider
                    )
                }
            )
        }

        HomeScreen(
            modifier = Modifier.fillMaxSize(),
            darkTheme = effectiveDarkTheme,
            pages = pages,
            activeMode = activeMode,
            llmChatEnabled = llmChatEnabled,
            highContrast = highContrast,
            whiteMode = whiteMode,
            loopCarousel = loopCarousel,
            torchOn = torchOn,
            torchAvailable = cameraXManager.deviceHasFlash(),
            conversation = transcriptEntries,
            // Shown until the question itself is in the conversation.
            pendingQuestion = chatPendingQuestion.takeUnless {
                transcriptEntries.lastOrNull { e -> e.kind == TranscriptEntry.Kind.QUESTION }?.text == it
            },
            working = chatWorking,
            onSendText = { text ->
                cutAllAudio()
                touchSessionActivity()
                val mode = activeMode
                if (mode != null && modeNeedsCapture(mode) && autoCaptureEnabled) {
                    // No picture yet: take one and ask with it, exactly as Ask
                    // does, except the question is already here. So there is
                    // no recording to wait for, and nothing to mishear.
                    armMicFirst(mode)
                    pendingQuestion = text
                    chatPendingQuestion = text
                    beginMicFirstCapture(mode)
                } else {
                    micFirstScope.launch {
                        if (mode == ModeNames.FIND_OBJECTS) answerFindCommand(text)
                        else answerChatQuestion(text)
                    }
                }
            },
            onToggleTorch = { wanted ->
                torchOn = wanted
                cameraXManager.setTorch(wanted)
                ttsService.speak(
                    context.getString(if (wanted) R.string.torch_on_spoken else R.string.torch_off_spoken),
                    flush = true,
                    force = true,
                )
            },
            holdToSpeak = holdToSpeak,
            isRecording = isListening,
            onModeSelected = { mode -> selectMode(mode) },
            onMicPressed = {
                if (micPressIsStaleStop()) {
                    // The recogniser has only just stopped by itself; this is
                    // the tail of the user reaching for stop, not a new start.
                    // The release that follows is swallowed with it.
                    micPressSwallowed = true
                    return@HomeScreen
                }
                micPressSwallowed = false
                val micModes = buildSet {
                    if (llmChatEnabled) add("Image chat")
                    if (llmChatEnabled) add("Text chat")
                    add("Find objects")
                }
                if (activeMode in micModes) {
                    val mode = activeMode
                    if (mode != null && modeNeedsCapture(mode) && !autoCaptureEnabled) {
                        // Auto-capture off: require an explicit Capture first.
                        micCaptureBlocked = true
                        cutAllAudio()
                        ttsService.speak(
                            if (mode == ModeNames.TEXT_CHAT) {
                                context.getString(R.string.spoken_capture_text_first)
                            } else {
                                context.getString(R.string.spoken_capture_first)
                            },
                            flush = true,
                        )
                    } else if (ensurePermission(Manifest.permission.RECORD_AUDIO)) {
                        micCaptureBlocked = false
                        cutAllAudio()
                        if (mode != null && modeNeedsCapture(mode)) armMicFirst(mode)
                        cueThenListen()
                    }
                }
            },
            onMicReleased = { holdMs ->
                if (micPressSwallowed) {
                    micPressSwallowed = false
                    return@HomeScreen
                }
                val micModes = buildSet {
                    if (llmChatEnabled) add("Image chat")
                    if (llmChatEnabled) add("Text chat")
                    add("Find objects")
                }
                if (activeMode in micModes && !micCaptureBlocked) {
                    // Just stop. The frame and the cue both follow the recording
                    // ending; see the isListening effect.
                    stopListeningByUser()
                }
                micCaptureBlocked = false
            },
            onMicTapped = { onAskTapped() },
            onTakePicture = if (activeMode == "Image chat" || activeMode == "Text chat") {
                { onChatCapture() }
            } else null,
            onBrowseObjects = if (activeMode == "Find objects") {
                { showObjectPicker = true }
            } else null,
            onOpenHelp = { openFeatureHelp(activeMode ?: pages.first()) },
            onOpenMemories = { showMemories = true },
            moreOpen = moreOpen,
            onToggleMore = { moreOpen = !moreOpen },
            onSaveChat = if (activeMode in ModeNames.TAKES_INPUT) ({ saveCurrentChat() }) else null,
            featureToggles = featureToggles,
            muted = ttsMuted,
            onToggleMute = {
                ttsMuted = !ttsMuted
                ttsService.muted = ttsMuted
                if (ttsMuted) ttsService.stop()
            },
            liveText = liveText.takeIf { activeMode == ModeNames.TEXT_CHAT && liveTextWanted() },
            // Find objects shows its line only until its chat starts.
            spokenCaption = lastSpokenLine.takeIf {
                activeMode in ModeNames.SCANNING && it.isNotBlank() &&
                    !(activeMode == ModeNames.FIND_OBJECTS && !chat.isEmpty())
            },
            onPreviewChanged = { cameraPreviewOn = it },
            previewCamera = cameraPreviewSetting,
            liveTextOn = liveTextOn,
            onTogglePreview = {
                settingsManager.setCameraPreview(it)
                if (it) ensurePermission(Manifest.permission.CAMERA)
            },
            liveTextPinned = pinnedLiveText.takeIf { activeMode == ModeNames.TEXT_CHAT && liveTextWanted() },
            onLiveTextPin = { pinLiveTextBox(it) },
            onLiveTextResume = { resumeLiveText() },
            onStepUnavailable = { forward ->
                // Asked for directly, so heard even with the voice muted.
                ttsService.speak(
                    context.getString(
                        if (forward) R.string.spoken_next_unavailable else R.string.spoken_previous_unavailable
                    ),
                    flush = true,
                    force = true,
                )
            },
            onOpenSettings = {
                cutAllAudio()
                cancelMicFirst()
                imageChatViewModel.cancelActiveRequest()
                documentChatViewModel.cancelActiveRequest()
                showSettings = true
            },
            showTextPlaybackControls = activeMode == "Text chat",
            textPlaybackEnabled = textPlaybackReady,
            playbackPlaying = playbackState == TextScriptPlayer.State.PLAYING,
            onPlaybackPauseToggle = { textScriptPlayer.togglePause() },
            onPlaybackSeekBack = { textScriptPlayer.seekBack() },
            onPlaybackSeekForward = { textScriptPlayer.seekForward() },
            onPlaybackRestartFromBeginning = { textScriptPlayer.restartFromBeginning() },
            onPlaybackRateStep = { up ->
                // Announce the new speed, then resume reading automatically
                // at the new rate from where the reader left off.
                val wasPlaying = playbackState == TextScriptPlayer.State.PLAYING
                textScriptPlayer.interrupt()
                val newRate = settingsManager.stepTtsSpeechRate(up)
                ttsService.setSpeechRate(newRate)
                ttsService.speak(
                    SettingsManager.ttsRateLabel(context, newRate),
                    flush = true,
                    utteranceId = "RATE_ANNOUNCE",
                ) {
                    if (wasPlaying) textScriptPlayer.resumeAtCursor()
                }
            },
            onPlaybackDisabled = {
                speakOutsidePlayer(context.getString(R.string.spoken_scan_text_first))
            },
        )

        // Dev builds: what the recogniser sees, drawn over the camera.
        if (OcrDebug.enabled && activeMode == ModeNames.TEXT_CHAT && !cameraDisabled) {
            OcrDebugOverlay(modifier = Modifier.fillMaxSize())
        }

        if (showHelp) {
            helpContent?.let { content ->
                HelpDialog(
                    title = content.title,
                    body = content.body,
                    sections = content.sections,
                    darkTheme = effectiveDarkTheme,
                    highContrast = highContrast,
                    whiteMode = whiteMode,
                    onClose = { closeHelp() },
                    onSubmitFeedback = { _ ->
                        false // Feedback flow removed with the backend.
                    },
                )
            }
        }

        if (showObjectPicker) {
            ObjectPickerDialog(
                visibleObjects = visibleCocoObjects,
                highContrast = highContrast,
                onObjectSelected = { selected ->
                    findObjectTarget = selected
                    showObjectPicker = false
                    findReply(context.getString(R.string.spoken_looking_for, CocoFinnish.displayName(selected)))
                    touchSessionActivity()
                },
                onDismiss = { showObjectPicker = false }
            )
        }
    }
}

@Composable
private fun AppDialogs(c: AppController, highContrast: Boolean, whiteMode: Boolean) {
    with(c) {
        // The setup offer and the speech choice follow the downloads.
        sttModelManager.downloadState.collectAsState().value
        localGemma.installedState.collectAsState().value
        localGemma.downloadPercent.collectAsState().value
        // Voice-model download dialog — rendered at the top level so it also
        // appears over the Settings screen (where the download row lives).
        if (showChoiceRespectedDialog) {
            ChoiceRespectedDialog(
                highContrast = highContrast,
                whiteMode = whiteMode,
                onDismiss = { showChoiceRespectedDialog = false },
            )
        }

        if (showCloudSttDialog) {
            CloudSttDialog(
                highContrast = highContrast,
                whiteMode = whiteMode,
                localModelPresent = speech.modelDownloaded && speech.modelServesLanguage,
                onEnable = {
                    showCloudSttDialog = false
                    settingsManager.markCloudSttAsked()
                    settingsManager.setCloudStt(true)
                },
                onDecline = {
                    showCloudSttDialog = false
                    settingsManager.markCloudSttAsked()
                    // The follow-up warns that the system recogniser cuts messages
                    // short. Someone keeping the on-device model is not going near
                    // it, so that warning would be simply untrue for them.
                    if (!(speech.modelDownloaded && speech.modelServesLanguage)) {
                        showChoiceRespectedDialog = true
                    }
                },
            )
        }

        if (showLanguageDialog) {
            val target = if (languageStore.isFinnish()) LanguageStore.LANG_EN else LanguageStore.LANG_FI
            LanguageSwitchDialog(
                targetLanguageName = stringResource(
                    if (languageStore.isFinnish()) R.string.language_english
                    else R.string.language_finnish
                ),
                highContrast = highContrast,
                whiteMode = whiteMode,
                onConfirm = {
                    showLanguageDialog = false
                    languageStore.setLanguage(target)
                    // Recreate so attachBaseContext re-resolves every resource in the
                    // new language, exactly as the first-run picker does.
                    (context as? Activity)?.recreate()
                },
                onDismiss = { showLanguageDialog = false },
            )
        }

        if (showSttModelsDialog) {
            val sizeLabel = remember {
                Formatter.formatShortFileSize(context, sttModelManager.sizeOnDisk())
            }
            SttModelsDialog(
                highContrast = highContrast,
                whiteMode = whiteMode,
                sizeLabel = sizeLabel,
                onDelete = {
                    showSttModelsDialog = false
                    val deleted = voiceInputService.unloadEngine() && sttModelManager.deleteModels()
                    if (deleted) {
                        // A deliberate removal: do not offer the download again at
                        // every launch. Settings can still download them.
                        settingsManager.setSttDownloadChoice(SettingsManager.STT_CHOICE_NEVER)
                    }
                    confirmation(
                        context.getString(
                            if (deleted) R.string.spoken_models_deleted else R.string.spoken_models_delete_failed
                        ),
                        flush = true,
                    )
                },
                onDismiss = { showSttModelsDialog = false },
            )
        }

        if (showGemmaDownloadDialog) {
            GemmaDownloadDialog(
                highContrast = highContrast,
                whiteMode = whiteMode,
                onDownload = {
                    showGemmaDownloadDialog = false
                    startGemmaDownload()
                },
                onCancel = { showGemmaDownloadDialog = false },
            )
        }

        if (showGemmaDialog) {
            val sizeLabel = remember {
                Formatter.formatShortFileSize(context, localGemma.sizeOnDisk())
            }
            SttModelsDialog(
                highContrast = highContrast,
                whiteMode = whiteMode,
                sizeLabel = sizeLabel,
                title = stringResource(R.string.settings_gemma_model),
                bodyRes = R.string.gemma_model_body,
                confirmRes = R.string.gemma_model_delete_confirm,
                onDelete = {
                    showGemmaDialog = false
                    deleteGemma()
                },
                onDismiss = { showGemmaDialog = false },
            )
        }

        if (showSetupDialog && setupOffered) {
            ModelSetupDialog(
                highContrast = highContrast,
                whiteMode = whiteMode,
                message = stringResource(
                    R.string.setup_body,
                    stringResource(setupModelsRes),
                    sizeLabel(ModelSetup.packageBytes(deviceTier)),
                    sizeLabel(freeSpace),
                ),
                onDownload = { startModelSetup(deviceTier) },
                onLater = {
                    settingsManager.setSttDownloadChoice(SettingsManager.STT_CHOICE_LATER)
                    showSetupDialog = false
                    // Later is not a refusal: the offer comes back on the next
                    // launch, so no "Understood" follow-up. Only Never gets one.
                },
                onNever = {
                    settingsManager.setSttDownloadChoice(SettingsManager.STT_CHOICE_NEVER)
                    showSetupDialog = false
                    showChoiceRespectedDialog = true
                },
            )
        }
    }
}
