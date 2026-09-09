package com.example.sightbuddy

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.sightbuddy.BuildConfig
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.sightbuddy.core.OpenAiTransport
import com.example.sightbuddy.core.BeepService
import com.example.sightbuddy.core.CameraXManager
import com.example.sightbuddy.core.HapticManager
import com.example.sightbuddy.core.NetworkStatusManager
import com.example.sightbuddy.core.SettingsManager
import com.example.sightbuddy.core.SoundFXService
import com.example.sightbuddy.core.TermsStore
import com.example.sightbuddy.core.WelcomeStore
import com.example.sightbuddy.core.ApiKeyStore
import com.example.sightbuddy.core.createCrashReporter
import com.example.sightbuddy.core.hints.FeatureHelpLibrary
import com.example.sightbuddy.core.hints.HelpContent
import com.example.sightbuddy.core.hints.HintId
import com.example.sightbuddy.core.hints.HintStore
import com.example.sightbuddy.R
import com.example.sightbuddy.core.SoundFXService.SFX
import com.example.sightbuddy.core.TextScriptPlayer
import com.example.sightbuddy.core.TTSService
import com.example.sightbuddy.core.stt.SttModelManager
import com.example.sightbuddy.core.stt.VoiceInputService
import com.example.sightbuddy.features.chat.DocumentChatViewModel
import com.example.sightbuddy.features.chat.ImageChatViewModel
import com.example.sightbuddy.features.chat.LocalTextExtractor
import com.example.sightbuddy.features.vision.ColorIdAnalyzer
import com.example.sightbuddy.features.vision.LightLevelAnalyzer
import com.example.sightbuddy.features.vision.ObjectCommandResolver
import com.example.sightbuddy.features.vision.TFLiteObjectAnalyzer
import com.example.sightbuddy.ui.screens.HelpDialog
import com.example.sightbuddy.ui.screens.HomeScreen
import com.example.sightbuddy.ui.screens.ObjectPickerDialog
import com.example.sightbuddy.ui.screens.SettingsScreen
import com.example.sightbuddy.ui.screens.SttDownloadDialog
import com.example.sightbuddy.ui.screens.TermsAcceptanceOverlay
import com.example.sightbuddy.ui.screens.WelcomeOverlay
import com.example.sightbuddy.ui.theme.SIghtbuddyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay

/** Minimum gap between accepted Ask taps (tap-to-speak mode). */
private const val MIC_TAP_DEBOUNCE_MS = 500L

private const val SESSION_IDLE_TIMEOUT_MS = 30 * 60 * 1000L
private const val SESSION_CHECK_INTERVAL_MS = 15_000L
private const val SESSION_WORD_LIMIT = 500

class MainActivity : ComponentActivity() {
    private lateinit var cameraXManager: CameraXManager
    private lateinit var networkStatusManager: NetworkStatusManager
    private lateinit var ttsService: TTSService
    private lateinit var voiceInputService: VoiceInputService
    private lateinit var sttModelManager: SttModelManager
    private lateinit var hapticManager: HapticManager
    private lateinit var beepService: BeepService
    private lateinit var soundFXService: SoundFXService
    private lateinit var settingsManager: SettingsManager
    private lateinit var welcomeStore: WelcomeStore
    private lateinit var termsStore: TermsStore
    private lateinit var hintStore: HintStore
    private lateinit var apiKeyStore: ApiKeyStore

    private lateinit var colorIdAnalyzer: ColorIdAnalyzer
    private lateinit var lightLevelAnalyzer: LightLevelAnalyzer
    private lateinit var objectAnalyzer: TFLiteObjectAnalyzer
    private lateinit var objectCommandResolver: ObjectCommandResolver

    private lateinit var documentChatViewModel: DocumentChatViewModel
    private lateinit var imageChatViewModel: ImageChatViewModel
    private lateinit var localTextExtractor: LocalTextExtractor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        cameraXManager = CameraXManager(this)
        networkStatusManager = NetworkStatusManager(this)
        ttsService = TTSService(this)
        sttModelManager = SttModelManager(this, BuildConfig.STT_MODEL_BASE_URL)
        voiceInputService = VoiceInputService(this, sttModelManager)
        // Whisper models are downloaded only on user request (Settings or the
        // first-launch prompt) — no automatic 154 MB download.
        hapticManager = HapticManager(this)
        beepService = BeepService()
        soundFXService = SoundFXService(this)
        settingsManager = SettingsManager(this)
        welcomeStore = WelcomeStore(this)
        termsStore = TermsStore(this)
        hintStore = HintStore(this)

        // Honour the saved crash-diagnostics choice before anything can crash.
        createCrashReporter().setEnabled(settingsManager.crashReportingEnabled.value)

        // Bring-your-own-key: all AI calls go directly to OpenAI with the
        // user's key. No proxy, no server of ours.
        apiKeyStore = ApiKeyStore(this)
        val llmTransport = OpenAiTransport { apiKeyStore.getKey() }

        colorIdAnalyzer = ColorIdAnalyzer()
        lightLevelAnalyzer = LightLevelAnalyzer()
        objectAnalyzer = TFLiteObjectAnalyzer(this)
        val modelProvider = { settingsManager.llmModel.value }
        objectCommandResolver = ObjectCommandResolver(transport = llmTransport)
        documentChatViewModel = DocumentChatViewModel(
            transport = llmTransport,
            modelProvider = modelProvider,
        )
        imageChatViewModel = ImageChatViewModel(
            transport = llmTransport,
            modelProvider = modelProvider,
        )
        localTextExtractor = LocalTextExtractor()

        enableEdgeToEdge()
        setContent {
            val highContrast by settingsManager.highContrastEnabled.collectAsState()
            val whiteMode by settingsManager.whiteModeEnabled.collectAsState()
            val effectiveDarkTheme = if (highContrast) !whiteMode else isSystemInDarkTheme()

            SIghtbuddyTheme(darkTheme = effectiveDarkTheme) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SightBuddyApp(
                        cameraXManager = cameraXManager,
                        ttsService = ttsService,
                        voiceInputService = voiceInputService,
                        hapticManager = hapticManager,
                        beepService = beepService,
                        soundFXService = soundFXService,
                        welcomeStore = welcomeStore,
                        termsStore = termsStore,
                        hintStore = hintStore,
                        apiKeyStore = apiKeyStore,
                        sttModelManager = sttModelManager,
                        settingsManager = settingsManager,
                        networkStatusManager = networkStatusManager,
                        colorIdAnalyzer = colorIdAnalyzer,
                        lightLevelAnalyzer = lightLevelAnalyzer,
                        objectAnalyzer = objectAnalyzer,
                        objectCommandResolver = objectCommandResolver,
                        documentChatViewModel = documentChatViewModel,
                        imageChatViewModel = imageChatViewModel,
                        localTextExtractor = localTextExtractor,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraXManager.shutdown()
        networkStatusManager.shutdown()
        ttsService.shutdown()
        voiceInputService.shutdown()
        objectAnalyzer.close()
        beepService.shutdown()
        soundFXService.shutdown()
        documentChatViewModel.close()
        localTextExtractor.close()
    }
}

@Composable
fun SightBuddyApp(
    cameraXManager: CameraXManager,
    ttsService: TTSService,
    voiceInputService: VoiceInputService,
    hapticManager: HapticManager,
    beepService: BeepService,
    soundFXService: SoundFXService,
    welcomeStore: WelcomeStore,
    termsStore: TermsStore,
    hintStore: HintStore,
    apiKeyStore: ApiKeyStore,
    sttModelManager: SttModelManager,
    settingsManager: SettingsManager,
    networkStatusManager: NetworkStatusManager,
    colorIdAnalyzer: ColorIdAnalyzer,
    lightLevelAnalyzer: LightLevelAnalyzer,
    objectAnalyzer: TFLiteObjectAnalyzer,
    objectCommandResolver: ObjectCommandResolver,
    documentChatViewModel: DocumentChatViewModel,
    imageChatViewModel: ImageChatViewModel,
    localTextExtractor: LocalTextExtractor,
    modifier: Modifier = Modifier
) {
    fun closeFrame(proxy: androidx.camera.core.ImageProxy) {
        cameraXManager.markFrameConsumed()
        cameraXManager.markFrameClosed()
        proxy.close()
    }

    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    var appInForeground by remember {
        mutableStateOf(
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
        )
    }

    var hasCameraPermission by remember { mutableStateOf(false) }
    var hasAudioPermission by remember { mutableStateOf(false) }
    var permissionsResolved by remember { mutableStateOf(false) }
    var welcomeCompleted by remember { mutableStateOf(welcomeStore.hasCompletedWelcome()) }
    var termsAccepted by remember { mutableStateOf(termsStore.hasAcceptedTerms()) }
    var showWelcome by remember { mutableStateOf(false) }
    val pendingWelcome = !welcomeCompleted
    var activeMode by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var helpContent by remember { mutableStateOf<HelpContent?>(null) }
    var lastAnnouncedMode by remember { mutableStateOf<String?>(null) }

    val permissionsGranted = hasCameraPermission && hasAudioPermission
    val showTerms = permissionsResolved && permissionsGranted && !termsAccepted
    val onboardingWait =
        pendingWelcome && !showWelcome && !showTerms &&
            (!permissionsResolved || !permissionsGranted)
    val tutorialBlocking =
        showTerms || (pendingWelcome && (!permissionsResolved || showWelcome))

    fun dismissWelcome() {
        soundFXService.stopTutorial()
        welcomeStore.markWelcomeCompleted()
        welcomeCompleted = true
        showWelcome = false
    }

    fun acceptTerms() {
        termsStore.markTermsAccepted()
        termsAccepted = true
    }

    fun openUrlInBrowser(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w("SightBuddyApp", "Could not open URL: $url", e)
        }
    }

    var findObjectTarget by remember { mutableStateOf("") }
    var showObjectPicker by remember { mutableStateOf(false) }
    var unresolvedFindObjectCount by remember { mutableStateOf(0) }
    var lastUnresolvedFindObjectInput by remember { mutableStateOf("") }

    var textScanned by remember { mutableStateOf(false) }
    var imageScanned by remember { mutableStateOf(false) }
    var triggerScan by remember { mutableStateOf(false) }

    // Mic-first "ask straight away": pressing the mic in Image/Text chat with no
    // capture yet records the (optional) question, then on release snaps a frame
    // and sends frame + question together. Pairing is order-independent: the
    // pipeline stores the frame, the transcription resolves the question, and a
    // LaunchedEffect fires once both are ready.
    var pendingCaptureBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var pendingQuestion by remember { mutableStateOf<String?>(null) } // null = unresolved, "" = describe
    var captureForQuestion by remember { mutableStateOf(false) }       // signal pipeline to grab+store a frame
    var micFirstActive by remember { mutableStateOf(false) }
    var micFirstMode by remember { mutableStateOf<String?>(null) }
    // Independent scope for the mic-first request so consuming the trigger state
    // (which recomposes the pairing effect) cannot cancel the in-flight call.
    val micFirstScope = rememberCoroutineScope()
    // Hold-mode: a press rejected because auto-capture is off must not act on release.
    var micCaptureBlocked by remember { mutableStateOf(false) }
    // Tap-mode debounce: ignore a second Ask tap fired within this window.
    var lastMicTapMs by remember { mutableStateOf(0L) }

    // Whisper model download — user-consented only, never automatic (154 MB).
    val sttModelDownloadState by sttModelManager.downloadState.collectAsState()
    val sttModelsReady = sttModelDownloadState is SttModelManager.DownloadState.Ready
    val sttDownloadPercent =
        (sttModelDownloadState as? SttModelManager.DownloadState.Downloading)?.percent
    var showSttDownloadDialog by remember { mutableStateOf(false) }
    var sttPromptShownThisLaunch by remember { mutableStateOf(false) }

    fun startSttDownload() {
        showSttDownloadDialog = false
        // A download decision has been made — don't auto-prompt again this launch.
        sttPromptShownThisLaunch = true
        MainScope().launch {
            // force: the user asked for this download, often from inside Settings.
            ttsService.speak(
                "Downloading voice models. This may take a few minutes.",
                flush = false,
                force = true,
            )
            val ok = sttModelManager.downloadIfNeeded()
            if (ok) {
                voiceInputService.loadEngineIfReady()
                ttsService.speak(
                    "Voice models installed. Speech recognition upgraded.",
                    flush = false,
                    force = true,
                )
            } else {
                ttsService.speak(
                    "Voice model download failed. You can retry from settings.",
                    flush = false,
                    force = true,
                )
            }
        }
    }

    /**
     * Message to speak instead of recording when the chat mode has nothing
     * captured yet (same wording the voice-command router uses); null = proceed.
     */
    /** Image/Text chat with nothing captured yet → use the mic-first capture flow. */
    fun modeNeedsCapture(mode: String?): Boolean = when (mode) {
        "Image chat" -> !imageScanned
        "Text chat" -> !textScanned
        else -> false
    }

    /** Clear any in-progress mic-first capture (e.g. on leaving the feature). */
    fun cancelMicFirst() {
        micFirstActive = false
        micFirstMode = null
        captureForQuestion = false
        pendingQuestion = null
        pendingCaptureBitmap?.recycle()
        pendingCaptureBitmap = null
    }

    /** Begin a mic-first capture: stop recording, snap a frame, await the question. */
    fun beginMicFirstCapture(mode: String) {
        cancelMicFirst()
        micFirstActive = true
        micFirstMode = mode
        captureForQuestion = true
        voiceInputService.stopListening()
        soundFXService.play(SFX.CAMERA_CLICK)
    }

    /** Guard for async responses: only speak if the user is still in the feature. */
    fun featureStillActive(mode: String): Boolean =
        activeMode == mode && !showSettings && !showHelp && appInForeground

    var lastSessionActivityAtMs by remember { mutableStateOf(System.currentTimeMillis()) }

    val textPreviewEnabled by settingsManager.textPreviewEnabled.collectAsState()
    // BYOK: AI features are enabled exactly when the user has saved their own key.
    val llmChatEnabled by apiKeyStore.keyPresent.collectAsState()
    val isOnline by networkStatusManager.isOnline.collectAsState()
    val scanLightEnabled by settingsManager.scanLightEnabled.collectAsState()
    val scanColourEnabled by settingsManager.scanColourEnabled.collectAsState()
    val imageChatEnabled by settingsManager.imageChatEnabled.collectAsState()
    val textChatEnabled by settingsManager.textChatEnabled.collectAsState()
    val discoverEnabled by settingsManager.discoverEnabled.collectAsState()
    val findEnabled by settingsManager.findEnabled.collectAsState()
    val highContrast by settingsManager.highContrastEnabled.collectAsState()
    val whiteMode by settingsManager.whiteModeEnabled.collectAsState()
    val effectiveDarkTheme = if (highContrast) !whiteMode else isSystemInDarkTheme()
    val useButtonNav by settingsManager.useButtonNav.collectAsState()
    val featureActivationAnnouncements by settingsManager.featureActivationAnnouncementsEnabled.collectAsState()
    val hiddenCocoObjects by settingsManager.hiddenCocoObjects.collectAsState()
    val visibleCocoObjects = remember(hiddenCocoObjects) {
        settingsManager.visibleCocoObjects()
    }
    val effectiveTextPreviewEnabled = textPreviewEnabled || !llmChatEnabled
    val textScriptPlayer = remember { TextScriptPlayer(ttsService) }
    val playbackState by textScriptPlayer.playbackState.collectAsState()
    var textPlaybackReady by remember { mutableStateOf(false) }
    var offlineNoticeShown by remember { mutableStateOf(false) }

    fun cutAllTtsFeedback() {
        voiceInputService.stopListening()
        textScriptPlayer.interrupt()
        ttsService.stop()
    }

    fun cutAllAudio() {
        cutAllTtsFeedback()
        soundFXService.stop()
        soundFXService.stopHint()
    }

    fun openHelp(content: HelpContent, playHintEarcon: Boolean) {
        cutAllTtsFeedback()
        soundFXService.stop()
        soundFXService.stopHint()
        helpContent = content
        showHelp = true
        if (playHintEarcon) {
            soundFXService.playHint()
        }
    }

    fun closeHelp() {
        showHelp = false
        helpContent = null
        soundFXService.stopHint()
    }

    fun openFeatureHelp(featureName: String) {
        val content = FeatureHelpLibrary.featureHelp(context, featureName) ?: return
        openHelp(content, playHintEarcon = false)
    }

    fun tryShowAutoHint(hintId: HintId) {
        if (hintStore.hasShown(hintId)) return
        hintStore.markShown(hintId)
        openHelp(FeatureHelpLibrary.hintHelp(context, hintId), playHintEarcon = true)
    }

    fun pauseForBackground() {
        voiceInputService.stopListening()
        cutAllAudio()
        cancelMicFirst()
        imageChatViewModel.cancelActiveRequest()
        documentChatViewModel.cancelActiveRequest()
        cameraXManager.stopCamera()
    }

    fun loadTextPlaybackScript(text: String) {
        textScriptPlayer.loadScript(text)
        textPlaybackReady = textScriptPlayer.hasScript
        textScriptPlayer.playFromStart()
    }

    fun clearTextPlayback() {
        textScriptPlayer.clear()
        textPlaybackReady = false
    }

    fun speakOutsidePlayer(message: String) {
        textScriptPlayer.interrupt()
        ttsService.speak(message, flush = true)
    }

    val ttsInitialized by ttsService.isInitialized.collectAsState()
    val isListening by voiceInputService.isListening.collectAsState()
    val holdToSpeak by settingsManager.holdToSpeak.collectAsState()
    val autoCaptureEnabled by settingsManager.autoCaptureEnabled.collectAsState()
    val ttsRate by settingsManager.ttsSpeechRate.collectAsState()

    // Apply speech rate on start and whenever the setting changes. Announcements
    // are made at the interaction sites (Settings row, playback hold buttons).
    LaunchedEffect(ttsRate, ttsInitialized) {
        if (ttsInitialized) ttsService.setSpeechRate(ttsRate)
    }
    val now = { System.currentTimeMillis() }

    fun announceModeActivated(mode: String) {
        if (showWelcome || showTerms || tutorialBlocking || !ttsInitialized) return
        if (!featureActivationAnnouncements) {
            lastAnnouncedMode = mode
            return
        }
        if (lastAnnouncedMode == mode) return
        lastAnnouncedMode = mode
        ttsService.speak("$mode activated", flush = true)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> appInForeground = true
                Lifecycle.Event.ON_STOP -> {
                    appInForeground = false
                    pauseForBackground()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        appInForeground = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(
        appInForeground, isListening, showWelcome, showTerms, showHelp, showSettings,
        tutorialBlocking,
    ) {
        // Settings is included: feature output must never leak over it. Settings'
        // own confirmations use speak(force = true) and still come through.
        ttsService.setSuppressAppSpeech(
            !appInForeground || isListening || showWelcome || showTerms || showHelp ||
                showSettings || tutorialBlocking,
        )
        // Do not stop STT when isListening — mic press already called cutAllAudio(); stopping here
        // would cancel SpeechRecognizer immediately after startListening().
        if (!appInForeground || showWelcome || showTerms || showHelp || showSettings) {
            cutAllTtsFeedback()
        }
    }

    LaunchedEffect(showHelp) {
        if (showHelp) {
            cutAllTtsFeedback()
        }
    }

    LaunchedEffect(showSettings, showObjectPicker) {
        if (showSettings || showObjectPicker) {
            cutAllAudio()
        }
    }

    fun touchSessionActivity() {
        lastSessionActivityAtMs = now()
    }

    fun expireSessionCaches() {
        findObjectTarget = ""
        showObjectPicker = false
        unresolvedFindObjectCount = 0
        lastUnresolvedFindObjectInput = ""
        imageScanned = false
        textScanned = false
        triggerScan = false
        imageChatViewModel.resetSession()
        documentChatViewModel.resetSession()
        clearTextPlayback()
        if (appInForeground) {
            ttsService.speak(
                "Session expired for optimal performance. Please scan again.",
                flush = true,
            )
        }
        lastSessionActivityAtMs = now()
        Log.i("SightBuddyApp", "Session caches cleared due to idle/size policy")
    }

    LaunchedEffect(showWelcome, appInForeground) {
        if (showWelcome && appInForeground) {
            soundFXService.playTutorial { dismissWelcome() }
        } else {
            soundFXService.stopTutorial()
        }
    }

    // Camera off in settings, welcome, or when app is not visible (background / lock screen).
    // High contrast must NOT disable the camera: analyzers need frames even though the
    // preview is visually covered by HomeScreen's full-screen mask.
    val cameraDisabled =
        !appInForeground || showSettings || showWelcome || showTerms ||
            showHelp || tutorialBlocking

    val pages = remember(
        scanLightEnabled, scanColourEnabled, llmChatEnabled,
        imageChatEnabled, textChatEnabled, discoverEnabled, findEnabled,
    ) {
        buildList {
            if (llmChatEnabled && imageChatEnabled) add("Image chat")
            if (textChatEnabled) add("Text chat")
            if (discoverEnabled) add("Discover objects")
            if (findEnabled) add("Find objects")
            if (scanLightEnabled) add("Scan Light")
            if (scanColourEnabled) add("Scan Colour")
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            hasCameraPermission = permissions[Manifest.permission.CAMERA] == true
            hasAudioPermission = permissions[Manifest.permission.RECORD_AUDIO] == true
            permissionsResolved = true
            if (pendingWelcome && !(hasCameraPermission && hasAudioPermission)) {
                welcomeStore.markWelcomeCompleted()
                welcomeCompleted = true
            }
        }
    )

    LaunchedEffect(Unit) {
        val cameraGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        val audioGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        hasCameraPermission = cameraGranted
        hasAudioPermission = audioGranted
        if (cameraGranted && audioGranted) {
            permissionsResolved = true
        } else {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
            )
        }
    }

    LaunchedEffect(pendingWelcome, permissionsResolved, permissionsGranted, termsAccepted) {
        showWelcome = pendingWelcome && permissionsResolved && permissionsGranted && termsAccepted
    }

    // HomeScreen selects the first page before TTS is ready; announce once both are set.
    LaunchedEffect(ttsInitialized, activeMode, showWelcome, tutorialBlocking, appInForeground) {
        if (!appInForeground || !ttsInitialized || showWelcome || showTerms || tutorialBlocking) {
            return@LaunchedEffect
        }
        val mode = activeMode ?: pages.firstOrNull() ?: return@LaunchedEffect
        announceModeActivated(mode)
    }

    // Wait for TTS: cold launch speaks nothing until initialized, so we must not
    // mark the notice shown until we can actually deliver the announcement.
    LaunchedEffect(
        isOnline,
        llmChatEnabled,
        ttsInitialized,
        showWelcome,
        showTerms,
        tutorialBlocking,
        appInForeground,
    ) {
        if (!appInForeground || showWelcome || showTerms || tutorialBlocking) {
            return@LaunchedEffect
        }
        if (!offlineNoticeShown && !isOnline && llmChatEnabled && ttsInitialized) {
            offlineNoticeShown = true
            ttsService.speak(
                "Your device seems to be offline. Chat features need internet.",
                flush = true
            )
        }
    }

    // Unbind camera when disabled (background / settings / overlays)
    LaunchedEffect(cameraDisabled) {
        if (cameraDisabled) {
            cameraXManager.stopCamera()
        }
    }

    // Offer the voice-model download once onboarding is out of the way. Shown on
    // every launch while undecided or "Later"; never again after "Never".
    LaunchedEffect(tutorialBlocking, showWelcome, showTerms, appInForeground) {
        if (tutorialBlocking || showWelcome || showTerms || !appInForeground) return@LaunchedEffect
        if (sttPromptShownThisLaunch || sttModelManager.isReady()) return@LaunchedEffect
        // Never interrupt an in-progress download with the prompt.
        if (sttModelDownloadState is SttModelManager.DownloadState.Downloading) return@LaunchedEffect
        if (settingsManager.sttDownloadChoice.value == SettingsManager.STT_CHOICE_NEVER) {
            return@LaunchedEffect
        }
        sttPromptShownThisLaunch = true
        showSttDownloadDialog = true
    }

    // Returning to the app must be silent, with one exception: Find objects with a
    // cached target, where re-announcing tells the user tracking is still live.
    // Anything queued by the previous session is cut rather than replayed.
    var appHasStartedOnce by remember { mutableStateOf(false) }
    LaunchedEffect(appInForeground, ttsInitialized) {
        if (!appInForeground) return@LaunchedEffect
        if (!appHasStartedOnce) {
            appHasStartedOnce = true
            return@LaunchedEffect // first launch keeps the normal onboarding flow
        }
        cutAllAudio()
        // Suppress the "<mode> activated" announcement on resume.
        lastAnnouncedMode = activeMode
        if (ttsInitialized && !showSettings && !showHelp && !tutorialBlocking &&
            activeMode == "Find objects" && findObjectTarget.isNotBlank()
        ) {
            ttsService.speak("Looking for $findObjectTarget", flush = true)
        }
    }

    // Voice input outcome feedback: auto-stop sounds and "nothing heard" prompts.
    LaunchedEffect(Unit) {
        voiceInputService.events.collect { event ->
            if (!appInForeground) return@collect
            // Mic-first with no speech = "just describe the scene": pair an empty
            // question with the snapped frame instead of prompting to repeat.
            if (micFirstActive) {
                pendingQuestion = ""
                return@collect
            }
            when (event) {
                is VoiceInputService.SttEvent.AutoStopped -> {
                    soundFXService.play(SFX.STOP_LISTENING)
                    if (event.noSpeech) {
                        ttsService.speak(
                            "I didn't hear anything. Please try again.",
                            flush = true,
                        )
                    }
                }
                VoiceInputService.SttEvent.Empty -> {
                    ttsService.speak("I'm sorry, could you please repeat?", flush = true)
                }
            }
        }
    }

    // Mic-first pairing: once the snapped frame and the (optional) question are
    // both ready, send them together and speak the answer.
    LaunchedEffect(pendingCaptureBitmap, pendingQuestion, micFirstActive) {
        val bmp = pendingCaptureBitmap
        val q = pendingQuestion
        val mode = micFirstMode
        if (!micFirstActive || bmp == null || q == null || mode == null) return@LaunchedEffect
        // Consume the trigger atomically. NOTE: these writes change this effect's
        // keys, so the effect itself is cancelled/relaunched — the real work must
        // run in an independent scope or it would be killed mid-request.
        micFirstActive = false
        micFirstMode = null
        pendingCaptureBitmap = null
        pendingQuestion = null
        micFirstScope.launch {
            try {
                when (mode) {
                    "Image chat" -> {
                        imageChatViewModel.resetSession()
                        val response = withContext(Dispatchers.Default) {
                            imageChatViewModel.processImage(bmp, q)
                        }
                        imageScanned = true
                        touchSessionActivity()
                        if (featureStillActive("Image chat")) ttsService.speak(response, flush = true)
                    }
                    "Text chat" -> {
                        documentChatViewModel.resetSession()
                        clearTextPlayback()
                        val response = withContext(Dispatchers.Default) {
                            documentChatViewModel.processDocument(bmp, q)
                        }
                        // Only "scanned" if OCR actually found text — otherwise the
                        // next Ask would do a follow-up against an empty document.
                        textScanned = documentChatViewModel.hasCachedDocument()
                        touchSessionActivity()
                        if (featureStillActive("Text chat")) speakOutsidePlayer(response)
                    }
                }
            } finally {
                bmp.recycle()
            }
        }
    }

    // Voice command handler — only after permissions and welcome are done
    LaunchedEffect(showWelcome, showTerms, tutorialBlocking, appInForeground) {
        if (!appInForeground || showWelcome || showTerms || tutorialBlocking) {
            return@LaunchedEffect
        }
        voiceInputService.recognizedText.collectLatest { command ->
            if (!appInForeground) return@collectLatest
            if (command.isNotEmpty()) {
                touchSessionActivity()
                if (micFirstActive) {
                    // Mic-first: this is the question paired with the snapped frame.
                    pendingQuestion = command
                    return@collectLatest
                }
                when (activeMode) {
                    "Find objects" -> {
                        try {
                            val resolution = withTimeout(10_000L) {
                                objectCommandResolver.resolve(command, llmEnabled = llmChatEnabled)
                            }
                            when (resolution) {
                                is ObjectCommandResolver.ResolveResult.Activate -> {
                                    findObjectTarget = resolution.item
                                    unresolvedFindObjectCount = 0
                                    lastUnresolvedFindObjectInput = ""
                                    ttsService.speak("Looking for ${resolution.item}", flush = true)
                                    Log.i(
                                        "SightBuddyApp",
                                        "Find-objects target activated via ${resolution.source} with confidence ${resolution.confidence}"
                                    )
                                }
                                is ObjectCommandResolver.ResolveResult.Clarify -> {
                                    val sameAsPrevious = lastUnresolvedFindObjectInput.equals(command, ignoreCase = true)
                                    if (sameAsPrevious) unresolvedFindObjectCount += 1 else unresolvedFindObjectCount = 1
                                    lastUnresolvedFindObjectInput = command

                                    ttsService.speak(resolution.message, flush = true)

                                    if (unresolvedFindObjectCount >= 2) {
                                        unresolvedFindObjectCount = 0
                                        showObjectPicker = true
                                        ttsService.speak("Opening object list for easier selection.", flush = true)
                                    }
                                }
                                is ObjectCommandResolver.ResolveResult.Unavailable -> {
                                    val sameAsPrevious = lastUnresolvedFindObjectInput.equals(command, ignoreCase = true)
                                    if (sameAsPrevious) unresolvedFindObjectCount += 1 else unresolvedFindObjectCount = 1
                                    lastUnresolvedFindObjectInput = command

                                    ttsService.speak(resolution.message, flush = true)
                                    Log.i("SightBuddyApp", "Find-objects unavailable request: $command")
                                    if (unresolvedFindObjectCount >= 2) {
                                        unresolvedFindObjectCount = 0
                                        showObjectPicker = true
                                        ttsService.speak("Opening object list. Please choose an object.", flush = true)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("SightBuddyApp", "Find-objects resolver error for '$command'", e)
                            ttsService.speak("I'm sorry, could you please repeat?", flush = true)
                        }
                    }
                    "Image chat" -> {
                        if (!llmChatEnabled) {
                            ttsService.speak("LLM chat is turned off in settings.", flush = true)
                            return@collectLatest
                        }
                        if (imageScanned) {
                            val response = imageChatViewModel.askFollowUp(command)
                            if (featureStillActive("Image chat")) ttsService.speak(response, flush = true)
                            touchSessionActivity()
                        } else {
                            ttsService.speak("Please capture first.", flush = true)
                        }
                    }
                    "Text chat" -> {
                        if (!llmChatEnabled) {
                            ttsService.speak("LLM chat is turned off in settings.", flush = true)
                            return@collectLatest
                        }
                        if (textScanned) {
                            val response = documentChatViewModel.askFollowUp(command)
                            if (featureStillActive("Text chat")) speakOutsidePlayer(response)
                            touchSessionActivity()
                        } else {
                            ttsService.speak("Please capture the text first.", flush = true)
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(showWelcome, showTerms, tutorialBlocking, appInForeground) {
        if (!appInForeground || showWelcome || showTerms || tutorialBlocking) {
            return@LaunchedEffect
        }
        while (true) {
            delay(SESSION_CHECK_INTERVAL_MS)
            if (!appInForeground) continue
            val hasCachedSession = findObjectTarget.isNotBlank() ||
                imageChatViewModel.hasCachedImage() ||
                documentChatViewModel.hasCachedDocument()
            if (!hasCachedSession) continue

            val totalWords = imageChatViewModel.totalHistoryWordCount() +
                documentChatViewModel.totalHistoryWordCount()
            val idleElapsedMs = now() - lastSessionActivityAtMs

            if (idleElapsedMs >= SESSION_IDLE_TIMEOUT_MS || totalWords > SESSION_WORD_LIMIT) {
                expireSessionCaches()
            }
        }
    }

    // Frame processing pipeline — only runs when camera is active and app is foregrounded
    LaunchedEffect(
        hasCameraPermission,
        activeMode,
        effectiveTextPreviewEnabled,
        cameraDisabled,
        llmChatEnabled,
        showObjectPicker,
        showWelcome,
        appInForeground,
    ) {
        if (!appInForeground || !hasCameraPermission || cameraDisabled) {
            cameraXManager.stopCamera()
            return@LaunchedEffect
        }

        when (activeMode) {
            null -> {
                cameraXManager.throttleIntervalMs = 500L
                cameraXManager.frameFlow.collectLatest { proxy -> closeFrame(proxy) }
            }

            "Scan Colour" -> {
                cameraXManager.throttleIntervalMs = 1000L
                cameraXManager.frameFlow.collectLatest { proxy ->
                    try {
                        val result = withContext(Dispatchers.Default) {
                            colorIdAnalyzer.analyze(proxy)
                        }
                        ttsService.speakWithCooldown(result.name)
                    } catch (e: Exception) {
                        Log.e("SightBuddyApp", "Colour ID analysis failed", e)
                    } finally {
                        closeFrame(proxy)
                    }
                }
            }

            "Scan Light" -> {
                cameraXManager.throttleIntervalMs = 500L
                cameraXManager.frameFlow.collectLatest { proxy ->
                    try {
                        val result = withContext(Dispatchers.Default) {
                            lightLevelAnalyzer.analyze(proxy)
                        }
                        ttsService.speakWithCooldown(result.description)
                    } catch (e: Exception) {
                        Log.e("SightBuddyApp", "Light level analysis failed", e)
                    } finally {
                        closeFrame(proxy)
                    }
                }
            }

            "Discover objects" -> {
                cameraXManager.throttleIntervalMs = 300L
                cameraXManager.frameFlow.collectLatest { proxy ->
                    try {
                        val (detections) = withContext(Dispatchers.Default) {
                            val bitmap = imageProxyToBitmap(proxy)
                            val dets = objectAnalyzer.analyze(bitmap)
                            bitmap.recycle()
                            Pair(dets, null)
                        }
                        if (detections.isNotEmpty()) {
                            val topDetection = detections.maxByOrNull { it.confidence }!!
                            ttsService.speakWithCooldown(topDetection.label)
                        }
                    } catch (e: Exception) {
                        Log.e("SightBuddyApp", "Object recognition failed", e)
                    } finally {
                        closeFrame(proxy)
                    }
                }
            }

            "Find objects" -> {
                cameraXManager.throttleIntervalMs = 200L
                var lastDirection = ""
                var hadObject = false
                cameraXManager.frameFlow.collectLatest { proxy ->
                    try {
                        if (showObjectPicker) {
                            soundFXService.stop()
                            closeFrame(proxy)
                            return@collectLatest
                        }
                        if (findObjectTarget.isNotEmpty()) {
                            val result = withContext(Dispatchers.Default) {
                                val bitmap = imageProxyToBitmap(proxy)
                                val detections = objectAnalyzer.analyze(bitmap)
                                bitmap.recycle()
                                detections.find {
                                    it.label.equals(findObjectTarget, ignoreCase = true)
                                }
                            }
                            if (result != null) {
                                if (!hadObject) hadObject = true

                                val cx = (result.boundingBox.left + result.boundingBox.right) / 2f
                                val cy = (result.boundingBox.top + result.boundingBox.bottom) / 2f
                                val offX = cx - 0.5f
                                val offY = cy - 0.5f
                                val proximity = (1f - (Math.sqrt(
                                    (offX * offX + offY * offY).toDouble()
                                ).toFloat() * 2f)).coerceIn(0f, 1f)
                                val pct = (proximity * 100).toInt()

                                val direction = if (pct >= 80) "centre"
                                else if (Math.abs(offX) > Math.abs(offY)) {
                                    if (offX < -0.1f) "left" else if (offX > 0.1f) "right" else "centre"
                                } else {
                                    if (offY < -0.1f) "up" else if (offY > 0.1f) "down" else "centre"
                                }

                                if (direction != lastDirection) {
                                    lastDirection = direction
                                    val sfx = when (direction) {
                                        "left"   -> SFX.DIRECTION_LEFT
                                        "right"  -> SFX.DIRECTION_RIGHT
                                        "up"     -> SFX.DIRECTION_UP
                                        "down"   -> SFX.DIRECTION_DOWN
                                        else     -> SFX.DIRECTION_CENTRE
                                    }
                                    soundFXService.play(sfx, loop = -1)
                                }
                            } else {
                                if (hadObject) {
                                    hadObject = false
                                    lastDirection = ""
                                    soundFXService.stop()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("SightBuddyApp", "Find object analysis failed", e)
                    } finally {
                        closeFrame(proxy)
                    }
                }
            }

            "Image chat" -> {
                cameraXManager.throttleIntervalMs = 500L
                cameraXManager.frameFlow.collectLatest { proxy ->
                    try {
                        if (captureForQuestion) {
                            // Mic-first: store the snapped frame; the pairing effect
                            // sends it once the transcribed question resolves.
                            captureForQuestion = false
                            pendingCaptureBitmap = withContext(Dispatchers.Default) {
                                imageProxyToBitmap(proxy)
                            }
                        } else if (triggerScan) {
                            triggerScan = false
                            soundFXService.play(SFX.CAMERA_CLICK)
                            val response = withContext(Dispatchers.Default) {
                                val bitmap = imageProxyToBitmap(proxy)
                                val text = imageChatViewModel.processImage(bitmap)
                                bitmap.recycle()
                                text
                            }
                            imageScanned = true
                            if (featureStillActive("Image chat")) ttsService.speak(response, flush = true)
                        }
                    } catch (e: Exception) {
                        Log.e("SightBuddyApp", "Image chat failed", e)
                    } finally {
                        closeFrame(proxy)
                    }
                }
            }

            "Text chat" -> {
                cameraXManager.throttleIntervalMs = 500L
                cameraXManager.frameFlow.collectLatest { proxy ->
                    try {
                        if (captureForQuestion) {
                            captureForQuestion = false
                            pendingCaptureBitmap = withContext(Dispatchers.Default) {
                                imageProxyToBitmap(proxy)
                            }
                        } else if (triggerScan) {
                            triggerScan = false
                            soundFXService.play(SFX.CAMERA_CLICK)

                            if (effectiveTextPreviewEnabled) {
                                val extractedText = withContext(Dispatchers.Default) {
                                    val bitmap = imageProxyToBitmap(proxy)
                                    val text = localTextExtractor.extractText(bitmap)
                                    bitmap.recycle()
                                    text
                                }
                                if (extractedText.isBlank()) {
                                    if (featureStillActive("Text chat")) {
                                        speakOutsidePlayer("I couldn't detect any text in the image.")
                                    }
                                } else {
                                    documentChatViewModel.setExtractedText(extractedText)
                                    textScanned = true
                                    if (featureStillActive("Text chat")) {
                                        loadTextPlaybackScript(
                                            documentChatViewModel.extractedTextForPlayback(),
                                        )
                                    }
                                }
                            } else {
                                val response = withContext(Dispatchers.Default) {
                                    val bitmap = imageProxyToBitmap(proxy)
                                    val text = documentChatViewModel.processDocument(bitmap)
                                    bitmap.recycle()
                                    text
                                }
                                textScanned = documentChatViewModel.hasCachedDocument()
                                if (featureStillActive("Text chat")) {
                                    loadTextPlaybackScript(
                                        documentChatViewModel.firstAssistantResponseForPlayback()
                                            .ifBlank { response },
                                    )
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("SightBuddyApp", "Text chat failed", e)
                    } finally {
                        closeFrame(proxy)
                    }
                }
            }

            else -> {
                cameraXManager.throttleIntervalMs = 500L
                cameraXManager.frameFlow.collectLatest { proxy -> closeFrame(proxy) }
            }
        }
    }

    // --- UI Layer ---

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (showTerms) {
            TermsAcceptanceOverlay(
                darkTheme = effectiveDarkTheme,
                highContrast = highContrast,
                whiteMode = whiteMode,
                onTermsOfUse = { openUrlInBrowser(TermsStore.TERMS_OF_USE_URL) },
                onPrivacyPolicy = { openUrlInBrowser(TermsStore.PRIVACY_POLICY_URL) },
                onAccept = { acceptTerms() },
                modifier = Modifier.fillMaxSize(),
            )
        } else if (showWelcome) {
            WelcomeOverlay(
                darkTheme = effectiveDarkTheme,
                highContrast = highContrast,
                whiteMode = whiteMode,
                onSkip = { dismissWelcome() },
                onCornerButtonTap = { soundFXService.playHintOverlay() },
                modifier = Modifier.fillMaxSize(),
            )
        } else if (onboardingWait) {
            val waitBg = if (effectiveDarkTheme) Color.Black else Color.White
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(waitBg),
            )
        } else if (hasCameraPermission) {
            if (showSettings) {
                // Settings overlay — solid background, no camera
                val settingsBg = if (effectiveDarkTheme) Color.Black else Color.White
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
                        apiKeyPresent = llmChatEnabled,
                        onSaveApiKey = { raw ->
                            apiKeyStore.setKey(raw)
                            ttsService.speak(
                                if (apiKeyStore.keyPresent.value) {
                                    "API key saved. AI chat features are now enabled."
                                } else {
                                    "API key removed. AI chat features are disabled."
                                },
                                flush = true,
                                force = true,
                            )
                        },
                        onSpeechRateCycle = {
                            val newRate = settingsManager.cycleTtsSpeechRate()
                            ttsService.speak(
                                "Speech rate ${SettingsManager.ttsRateLabel(newRate)}",
                                flush = true,
                                force = true,
                            )
                        },
                        sttModelsReady = sttModelsReady,
                        sttDownloadPercent = sttDownloadPercent,
                        onRequestSttDownload = { showSttDownloadDialog = true },
                        onFeatureHideBlocked = {
                            ttsService.speak(
                                "At least one feature must stay on.",
                                flush = true,
                                force = true,
                            )
                        },
                        onCrashReportingChange = { enabled ->
                            createCrashReporter().setEnabled(enabled)
                        },
                    )
                }
            } else {
                // Camera preview — only rendered when not disabled
                if (!cameraDisabled) {
                    androidx.compose.ui.viewinterop.AndroidView(
                        factory = { ctx ->
                            androidx.camera.view.PreviewView(ctx).apply {
                                implementationMode =
                                    androidx.camera.view.PreviewView.ImplementationMode.COMPATIBLE
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
                    pages = pages,
                    activeMode = activeMode,
                    llmChatEnabled = llmChatEnabled,
                    highContrast = highContrast,
                    whiteMode = whiteMode,
                    useButtonNav = useButtonNav,
                    holdToSpeak = holdToSpeak,
                    isRecording = isListening,
                    onModeSelected = { mode ->
                        if (activeMode != mode) {
                            cutAllAudio()
                            cancelMicFirst()
                            imageChatViewModel.cancelActiveRequest()
                            documentChatViewModel.cancelActiveRequest()
                            activeMode = mode
                            announceModeActivated(mode)
                            // Preserve Image/Text chat cache across feature switches.
                            // Session reset is user-driven via Capture.
                            triggerScan = false
                            touchSessionActivity()

                            if (mode == "Find objects" && findObjectTarget.isNotBlank()) {
                                // Re-announce cached target so users know tracking is still active.
                                ttsService.speak("Looking for $findObjectTarget", flush = false)
                            }
                        }
                    },
                    onMicPressed = {
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
                                    if (mode == "Text chat") "Please capture the text first."
                                    else "Please capture first.",
                                    flush = true,
                                )
                            } else {
                                micCaptureBlocked = false
                                cutAllAudio()
                                soundFXService.play(SFX.LISTENING)
                                voiceInputService.startListening()
                            }
                        }
                    },
                    onMicReleased = { holdMs ->
                        val micModes = buildSet {
                            if (llmChatEnabled) add("Image chat")
                            if (llmChatEnabled) add("Text chat")
                            add("Find objects")
                        }
                        if (activeMode in micModes && !micCaptureBlocked) {
                            val mode = activeMode
                            if (mode != null && modeNeedsCapture(mode)) {
                                // Mic-first: snap a frame and send it with the question.
                                beginMicFirstCapture(mode)
                            } else {
                                voiceInputService.stopListening()
                                if (holdMs < 1000L) {
                                    ttsService.speak(
                                        "Please hold the Ask button and speak",
                                        flush = true
                                    )
                                } else {
                                    soundFXService.play(SFX.STOP_LISTENING)
                                    // Empty results are announced via voiceInputService.events.
                                }
                            }
                        }
                        micCaptureBlocked = false
                    },
                    onMicTapped = {
                        val micModes = buildSet {
                            if (llmChatEnabled) add("Image chat")
                            if (llmChatEnabled) add("Text chat")
                            add("Find objects")
                        }
                        // Debounce: a stray double-tap would otherwise start and
                        // immediately stop a recording, yielding an empty result.
                        val tapNow = System.currentTimeMillis()
                        val tapAccepted = tapNow - lastMicTapMs >= MIC_TAP_DEBOUNCE_MS
                        if (tapAccepted) lastMicTapMs = tapNow
                        if (tapAccepted && activeMode in micModes) {
                            if (isListening) {
                                val mode = activeMode
                                if (mode != null && modeNeedsCapture(mode)) {
                                    beginMicFirstCapture(mode)
                                } else {
                                    voiceInputService.stopListening()
                                    soundFXService.play(SFX.STOP_LISTENING)
                                }
                            } else {
                                val mode = activeMode
                                if (mode != null && modeNeedsCapture(mode) && !autoCaptureEnabled) {
                                    cutAllAudio()
                                    ttsService.speak(
                                        if (mode == "Text chat") "Please capture the text first."
                                        else "Please capture first.",
                                        flush = true,
                                    )
                                } else {
                                    cutAllAudio()
                                    soundFXService.play(SFX.LISTENING)
                                    voiceInputService.startListening()
                                }
                            }
                        }
                    },
                    onTakePicture = if (activeMode == "Image chat" || activeMode == "Text chat") {
                        {
                            cancelMicFirst()
                            when (activeMode) {
                                "Image chat" -> {
                                    imageChatViewModel.resetSession()
                                    imageScanned = false
                                    touchSessionActivity()
                                }
                                "Text chat" -> {
                                    documentChatViewModel.resetSession()
                                    textScanned = false
                                    clearTextPlayback()
                                    touchSessionActivity()
                                }
                            }
                            triggerScan = true
                        }
                    } else null,
                    onBrowseObjects = if (activeMode == "Find objects") {
                        { showObjectPicker = true }
                    } else null,
                    onOpenHelp = { openFeatureHelp(activeMode ?: pages.first()) },
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
                    onPlaybackTransportInteraction = {
                        tryShowAutoHint(HintId.TEXT_CHAT_PLAYBACK_CONTROLS)
                    },
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
                            SettingsManager.ttsRateLabel(newRate),
                            flush = true,
                            utteranceId = "RATE_ANNOUNCE",
                        ) {
                            if (wasPlaying) textScriptPlayer.resumeAtCursor()
                        }
                    },
                    onPlaybackDisabled = {
                        speakOutsidePlayer("Please scan text first")
                    },
                )

                if (showHelp) {
                    helpContent?.let { content ->
                        HelpDialog(
                            title = content.title,
                            body = content.body,
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
                            ttsService.speak("Looking for $selected", flush = true)
                            touchSessionActivity()
                        },
                        onDismiss = { showObjectPicker = false }
                    )
                }
            }
        } else {
            Text(text = "Waiting for Camera Permission...")
        }

        // Voice-model download dialog — rendered at the top level so it also
        // appears over the Settings screen (where the download row lives).
        if (showSttDownloadDialog) {
            SttDownloadDialog(
                highContrast = highContrast,
                whiteMode = whiteMode,
                onDownload = { startSttDownload() },
                onLater = {
                    settingsManager.setSttDownloadChoice(SettingsManager.STT_CHOICE_LATER)
                    showSttDownloadDialog = false
                },
                onNever = {
                    settingsManager.setSttDownloadChoice(SettingsManager.STT_CHOICE_NEVER)
                    showSttDownloadDialog = false
                },
            )
        }
    }
}

/**
 * Convert an ImageProxy (YUV_420_888) to a correctly-rotated Bitmap.
 */
private fun imageProxyToBitmap(imageProxy: androidx.camera.core.ImageProxy): android.graphics.Bitmap {
    val width = imageProxy.width
    val height = imageProxy.height

    val yPlane = imageProxy.planes[0]
    val uPlane = imageProxy.planes[1]
    val vPlane = imageProxy.planes[2]

    val yBuffer = yPlane.buffer
    val uBuffer = uPlane.buffer
    val vBuffer = vPlane.buffer

    val yRowStride = yPlane.rowStride
    val uvRowStride = uPlane.rowStride
    val uvPixelStride = uPlane.pixelStride

    val pixels = IntArray(width * height)

    for (row in 0 until height) {
        for (col in 0 until width) {
            val yIndex = row * yRowStride + col
            val uvRow = row shr 1
            val uvCol = col shr 1
            val uvIndex = uvRow * uvRowStride + uvCol * uvPixelStride

            val y = (yBuffer.get(yIndex).toInt() and 0xFF).toFloat()
            val u = (uBuffer.get(uvIndex).toInt() and 0xFF).toFloat() - 128f
            val v = (vBuffer.get(uvIndex).toInt() and 0xFF).toFloat() - 128f

            val r = (y + 1.370f * v).toInt().coerceIn(0, 255)
            val g = (y - 0.337f * u - 0.698f * v).toInt().coerceIn(0, 255)
            val b = (y + 1.732f * u).toInt().coerceIn(0, 255)

            pixels[row * width + col] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    val rawBitmap = android.graphics.Bitmap.createBitmap(
        width, height, android.graphics.Bitmap.Config.ARGB_8888
    )
    rawBitmap.setPixels(pixels, 0, width, 0, 0, width, height)

    val rotation = imageProxy.imageInfo.rotationDegrees
    return if (rotation != 0) {
        val matrix = android.graphics.Matrix()
        matrix.postRotate(rotation.toFloat())
        val rotated = android.graphics.Bitmap.createBitmap(
            rawBitmap, 0, 0, width, height, matrix, true
        )
        if (rotated != rawBitmap) rawBitmap.recycle()
        rotated
    } else {
        rawBitmap
    }
}
