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
import com.example.sightbuddy.core.DeleteDataTransport
import com.example.sightbuddy.core.FeedbackTransport
import com.example.sightbuddy.core.CameraXManager
import com.example.sightbuddy.core.HapticManager
import com.example.sightbuddy.core.NetworkStatusManager
import com.example.sightbuddy.core.SettingsManager
import com.example.sightbuddy.core.SoundFXService
import com.example.sightbuddy.core.TermsStore
import com.example.sightbuddy.core.WelcomeStore
import com.example.sightbuddy.di.createDeviceIdProvider
import com.example.sightbuddy.di.createInAppUpdateChecker
import com.example.sightbuddy.di.createIntegrityTokenProvider
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
    private lateinit var feedbackTransport: FeedbackTransport
    private lateinit var deleteDataTransport: DeleteDataTransport

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
        // Fetch the Whisper model in the background (no-op when already present
        // or when downloads are disabled); load the engine once available.
        MainScope().launch {
            if (sttModelManager.downloadIfNeeded()) {
                voiceInputService.loadEngineIfReady()
            }
        }
        hapticManager = HapticManager(this)
        beepService = BeepService()
        soundFXService = SoundFXService(this)
        settingsManager = SettingsManager(this)
        welcomeStore = WelcomeStore(this)
        termsStore = TermsStore(this)
        hintStore = HintStore(this)

        val deviceIdProvider = createDeviceIdProvider(this)
        val integrityProvider = createIntegrityTokenProvider(this)
        val installId = deviceIdProvider.getId()

        feedbackTransport = FeedbackTransport(
            feedbackUrl = BuildConfig.SUPABASE_FEEDBACK_URL,
            supabaseAnonKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
            installId = installId,
            integrityTokenProvider = integrityProvider,
        )
        deleteDataTransport = DeleteDataTransport(
            deleteDataUrl = BuildConfig.SUPABASE_DELETE_DATA_URL,
            supabaseAnonKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
            installId = installId,
            integrityTokenProvider = integrityProvider,
        )
        val llmTransport = OpenAiTransport(
            chatProxyUrl = BuildConfig.SUPABASE_CHAT_URL,
            supabaseAnonKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
            installId = installId,
            integrityTokenProvider = integrityProvider,
        )
        val onQuotaExhausted: () -> Unit = {
            runOnUiThread {
                settingsManager.setLlmChat(false)
                ttsService.speak(
                    "Daily AI limit reached. Chat features will reset tomorrow.",
                    flush = true,
                )
            }
        }
        val onInstallRestricted: () -> Unit = {
            runOnUiThread {
                settingsManager.setLlmChat(false)
                ttsService.speak(
                    "Cloud AI is unavailable for two days after deleting your data.",
                    flush = true,
                )
            }
        }

        colorIdAnalyzer = ColorIdAnalyzer()
        lightLevelAnalyzer = LightLevelAnalyzer()
        objectAnalyzer = TFLiteObjectAnalyzer(this)
        objectCommandResolver = ObjectCommandResolver(
            transport = llmTransport,
            onQuotaExhausted = onQuotaExhausted,
            onInstallRestricted = onInstallRestricted,
        )
        documentChatViewModel = DocumentChatViewModel(
            transport = llmTransport,
            onQuotaExhausted = onQuotaExhausted,
            onInstallRestricted = onInstallRestricted,
        )
        imageChatViewModel = ImageChatViewModel(
            transport = llmTransport,
            onQuotaExhausted = onQuotaExhausted,
            onInstallRestricted = onInstallRestricted,
        )
        localTextExtractor = LocalTextExtractor()

        createInAppUpdateChecker(this).checkForUpdate(this)

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
                        feedbackTransport = feedbackTransport,
                        deleteDataTransport = deleteDataTransport,
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
    feedbackTransport: FeedbackTransport,
    deleteDataTransport: DeleteDataTransport,
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

    // True when a hold-mode mic press was rejected (no picture yet) so the
    // matching release must not speak the "please hold" hint over the message.
    var micBlockedPress by remember { mutableStateOf(false) }

    /**
     * Message to speak instead of recording when the chat mode has nothing
     * captured yet (same wording the voice-command router uses); null = proceed.
     */
    fun missingCaptureMessage(mode: String?): String? = when (mode) {
        "Image chat" -> if (!imageScanned) "Please take a picture first." else null
        "Text chat" -> if (!textScanned) "Please take a picture of the text first." else null
        else -> null
    }
    var lastSessionActivityAtMs by remember { mutableStateOf(System.currentTimeMillis()) }

    val textPreviewEnabled by settingsManager.textPreviewEnabled.collectAsState()
    val llmChatEnabled by settingsManager.llmChatEnabled.collectAsState()
    val isOnline by networkStatusManager.isOnline.collectAsState()
    val scanLightEnabled by settingsManager.scanLightEnabled.collectAsState()
    val scanColourEnabled by settingsManager.scanColourEnabled.collectAsState()
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
    val ttsRate by settingsManager.ttsSpeechRate.collectAsState()
    var lastAppliedTtsRate by remember { mutableStateOf<Float?>(null) }

    // Apply speech rate on start and whenever the setting changes; speak a short
    // sample on user-initiated changes so the new speed is heard immediately.
    LaunchedEffect(ttsRate, ttsInitialized) {
        if (!ttsInitialized) return@LaunchedEffect
        ttsService.setSpeechRate(ttsRate)
        if (lastAppliedTtsRate != null && lastAppliedTtsRate != ttsRate) {
            ttsService.speak(
                "Speech rate ${SettingsManager.ttsRateLabel(ttsRate)}",
                flush = true,
            )
        }
        lastAppliedTtsRate = ttsRate
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

    LaunchedEffect(appInForeground, isListening, showWelcome, showTerms, showHelp, tutorialBlocking) {
        ttsService.setSuppressAppSpeech(
            !appInForeground || isListening || showWelcome || showTerms || showHelp ||
                tutorialBlocking,
        )
        // Do not stop STT when isListening — mic press already called cutAllAudio(); stopping here
        // would cancel SpeechRecognizer immediately after startListening().
        if (!appInForeground || showWelcome || showTerms || showHelp) {
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

    val pages = remember(scanLightEnabled, scanColourEnabled, llmChatEnabled) {
        buildList {
            if (llmChatEnabled) add("Image chat")
            add("Text chat")
            add("Discover objects")
            add("Find objects")
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

    // Voice input outcome feedback: auto-stop sounds and "nothing heard" prompts.
    LaunchedEffect(Unit) {
        voiceInputService.events.collect { event ->
            if (!appInForeground) return@collect
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

    // Voice command handler — only after permissions and welcome are done
    LaunchedEffect(showWelcome, showTerms, tutorialBlocking, appInForeground) {
        if (!appInForeground || showWelcome || showTerms || tutorialBlocking) {
            return@LaunchedEffect
        }
        voiceInputService.recognizedText.collectLatest { command ->
            if (!appInForeground) return@collectLatest
            if (command.isNotEmpty()) {
                touchSessionActivity()
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
                            soundFXService.play(SFX.LISTENING)
                            val response = imageChatViewModel.askFollowUp(command)
                            ttsService.speak(response, flush = true)
                            touchSessionActivity()
                        } else {
                            ttsService.speak("Please take a picture first.", flush = true)
                        }
                    }
                    "Text chat" -> {
                        if (!llmChatEnabled) {
                            ttsService.speak("LLM chat is turned off in settings.", flush = true)
                            return@collectLatest
                        }
                        if (textScanned) {
                            soundFXService.play(SFX.LISTENING)
                            val response = documentChatViewModel.askFollowUp(command)
                            speakOutsidePlayer(response)
                            touchSessionActivity()
                        } else {
                            ttsService.speak("Please take a picture of the text first.", flush = true)
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
                        if (triggerScan) {
                            triggerScan = false
                            soundFXService.play(SFX.CAMERA_CLICK)
                            val response = withContext(Dispatchers.Default) {
                                val bitmap = imageProxyToBitmap(proxy)
                                val text = imageChatViewModel.processImage(bitmap)
                                bitmap.recycle()
                                text
                            }
                            imageScanned = true
                            ttsService.speak(response, flush = true)
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
                        if (triggerScan) {
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
                                    speakOutsidePlayer("I couldn't detect any text in the image.")
                                } else {
                                    documentChatViewModel.setExtractedText(extractedText)
                                    textScanned = true
                                    loadTextPlaybackScript(
                                        documentChatViewModel.extractedTextForPlayback(),
                                    )
                                }
                            } else {
                                val response = withContext(Dispatchers.Default) {
                                    val bitmap = imageProxyToBitmap(proxy)
                                    val text = documentChatViewModel.processDocument(bitmap)
                                    bitmap.recycle()
                                    text
                                }
                                textScanned = true
                                loadTextPlaybackScript(
                                    documentChatViewModel.firstAssistantResponseForPlayback()
                                        .ifBlank { response },
                                )
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
                        onDeleteData = {
                            val ok = withContext(Dispatchers.IO) {
                                deleteDataTransport.deleteMyData()
                            }
                            if (ok) {
                                settingsManager.setLlmChat(false)
                                documentChatViewModel.resetSession()
                                imageChatViewModel.resetSession()
                                ttsService.speak(
                                    "Your data was deleted. Cloud AI is unavailable for two days.",
                                    flush = true,
                                )
                            } else {
                                ttsService.speak(
                                    "Could not delete your data. Check your connection and try again.",
                                    flush = true,
                                )
                            }
                            ok
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
                            activeMode = mode
                            announceModeActivated(mode)
                            // Preserve Image/Text chat cache across feature switches.
                            // Session reset is user-driven via "Take Picture".
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
                            val captureMsg = missingCaptureMessage(activeMode)
                            if (captureMsg != null) {
                                // No picture yet — skip recording entirely.
                                micBlockedPress = true
                                cutAllAudio()
                                ttsService.speak(captureMsg, flush = true)
                            } else {
                                micBlockedPress = false
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
                        if (activeMode in micModes && !micBlockedPress) {
                            voiceInputService.stopListening()
                            if (holdMs < 1000L) {
                                ttsService.speak(
                                    "Please hold the mic button and speak",
                                    flush = true
                                )
                            } else {
                                soundFXService.play(SFX.STOP_LISTENING)
                                // Empty results are announced via voiceInputService.events.
                            }
                        }
                        micBlockedPress = false
                    },
                    onMicTapped = {
                        val micModes = buildSet {
                            if (llmChatEnabled) add("Image chat")
                            if (llmChatEnabled) add("Text chat")
                            add("Find objects")
                        }
                        if (activeMode in micModes) {
                            if (isListening) {
                                voiceInputService.stopListening()
                                soundFXService.play(SFX.STOP_LISTENING)
                            } else {
                                val captureMsg = missingCaptureMessage(activeMode)
                                if (captureMsg != null) {
                                    cutAllAudio()
                                    ttsService.speak(captureMsg, flush = true)
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
                        showSettings = true
                    },
                    showTextPlaybackControls = activeMode == "Text chat",
                    textPlaybackEnabled = textPlaybackReady,
                    onPlaybackPauseToggle = { textScriptPlayer.togglePause() },
                    onPlaybackTransportInteraction = {
                        tryShowAutoHint(HintId.TEXT_CHAT_PLAYBACK_CONTROLS)
                    },
                    onPlaybackSeekBack = { textScriptPlayer.seekBack() },
                    onPlaybackSeekForward = { textScriptPlayer.seekForward() },
                    onPlaybackRestartFromBeginning = { textScriptPlayer.restartFromBeginning() },
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
                            onClose = { closeHelp() },
                            onSubmitFeedback = { text ->
                                withContext(Dispatchers.IO) {
                                    feedbackTransport.submit(text)
                                }
                            },
                        )
                    }
                }

                if (showObjectPicker) {
                    ObjectPickerDialog(
                        visibleObjects = visibleCocoObjects,
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
