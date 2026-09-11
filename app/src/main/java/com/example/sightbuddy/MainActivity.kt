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
import androidx.compose.ui.res.stringResource
import com.example.sightbuddy.core.OpenAiTransport
import com.example.sightbuddy.core.BeepService
import com.example.sightbuddy.core.CameraXManager
import com.example.sightbuddy.core.HapticManager
import com.example.sightbuddy.core.LanguageStore
import com.example.sightbuddy.core.ModeNames
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
import com.example.sightbuddy.core.PitchToneService
import com.example.sightbuddy.core.hints.HintStore
import com.example.sightbuddy.R
import com.example.sightbuddy.core.SoundFXService.SFX
import com.example.sightbuddy.core.TextScriptPlayer
import com.example.sightbuddy.core.TTSService
import com.example.sightbuddy.core.stt.CloudTranscriber
import com.example.sightbuddy.core.stt.SpeechAvailability
import com.example.sightbuddy.core.stt.SpeechEngine
import com.example.sightbuddy.core.stt.SttModelManager
import com.example.sightbuddy.core.stt.VoiceInputService
import com.example.sightbuddy.features.chat.DocumentChatViewModel
import com.example.sightbuddy.features.chat.ImageChatViewModel
import com.example.sightbuddy.features.chat.LocalTextExtractor
import com.example.sightbuddy.features.vision.CocoFinnish
import com.example.sightbuddy.features.vision.ColorIdAnalyzer
import com.example.sightbuddy.features.vision.LightLevelAnalyzer
import com.example.sightbuddy.features.vision.ObjectCommandResolver
import com.example.sightbuddy.features.vision.TFLiteObjectAnalyzer
import com.example.sightbuddy.ui.screens.HelpDialog
import com.example.sightbuddy.ui.screens.HomeScreen
import com.example.sightbuddy.ui.screens.ObjectPickerDialog
import com.example.sightbuddy.ui.screens.SettingsScreen
import com.example.sightbuddy.ui.screens.ChoiceRespectedDialog
import com.example.sightbuddy.ui.screens.CloudSttDialog
import com.example.sightbuddy.ui.screens.LanguageSwitchDialog
import com.example.sightbuddy.ui.screens.SttDownloadDialog
import com.example.sightbuddy.ui.screens.TermsAcceptanceOverlay
import com.example.sightbuddy.ui.screens.WelcomeOverlay
import com.example.sightbuddy.ui.theme.SIghtbuddyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay

/** Support inbox shown and linked from Settings. */
private const val FEEDBACK_EMAIL = "contact@drophouse.uk"

/** Minimum gap between accepted Ask taps (tap-to-speak mode). */
private const val MIC_TAP_DEBOUNCE_MS = 500L

/**
 * How long after a recording ends by itself a mic press is swallowed.
 *
 * The platform recogniser decides on its own when the user has stopped talking,
 * and that regularly lands in the instant the user is reaching for stop. The
 * button is a toggle over whether a recording is running, so that press would
 * read as start and begin a fresh recording rather than ending anything.
 */
private const val MIC_RESTART_GUARD_MS = 2_000L

/**
 * Whether hold-to-speak is offered at all.
 *
 * Withdrawn rather than deleted. It never sat well with the platform recogniser,
 * which ends recordings on its own timing, so holding the button decided nothing
 * and the two fought each other. Settings is crowded enough that a switch which
 * only works on some paths costs more than it gives. The gesture handling is
 * untouched behind this, so bringing it back is this line plus the advice for a
 * press too short to speak into, which was removed with it.
 */
private const val HOLD_TO_SPEAK_ENABLED = false

/** How recently the app must have asked for a stop to call the stop the user's. */
private const val USER_STOP_WINDOW_MS = 1_000L


/** How long to wait for a snapped frame to be encoded before giving up on it. */
private const val CAPTURE_ENCODE_TIMEOUT_MS = 4_000L

private const val SESSION_IDLE_TIMEOUT_MS = 30 * 60 * 1000L
private const val SESSION_CHECK_INTERVAL_MS = 15_000L
private const val SESSION_WORD_LIMIT = 500

/**
 * Whether a captured frame and its answers survive a move to another feature.
 *
 * Off for now: with it on there was no way to tell whether a reply described the
 * picture just taken or one left over from an earlier visit. Nothing about the
 * caching was removed, so turning this back on restores the old behaviour if the
 * feedback says the cache was worth keeping.
 */
private const val KEEP_CAPTURES_ACROSS_FEATURES = false

/**
 * How long a mic-first request waits for the camera to hand over a frame before
 * giving up. The frame comes from the preview flow, which can be paused or
 * unbound; without this the request simply stalls and nothing is ever spoken.
 */
private const val MIC_FIRST_CAPTURE_TIMEOUT_MS = 6_000L

/**
 * Slack added to the recording cap when the microphone takes the audio floor, so
 * the hold always outlives the longest possible recording and still expires by
 * itself if the release is somehow missed.
 */
private const val MIC_FLOOR_GRACE_MS = 5_000L

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
    private lateinit var languageStore: LanguageStore
    private lateinit var hintStore: HintStore
    private val pitchToneService = PitchToneService()
    private lateinit var cloudTranscriber: CloudTranscriber
    private lateinit var apiKeyStore: ApiKeyStore

    private lateinit var colorIdAnalyzer: ColorIdAnalyzer
    private lateinit var lightLevelAnalyzer: LightLevelAnalyzer
    private lateinit var objectAnalyzer: TFLiteObjectAnalyzer
    private lateinit var objectCommandResolver: ObjectCommandResolver

    private lateinit var documentChatViewModel: DocumentChatViewModel
    private lateinit var imageChatViewModel: ImageChatViewModel
    private lateinit var localTextExtractor: LocalTextExtractor

    /**
     * Apply the chosen display language before any resource is resolved, so
     * values-fi strings and raw-fi earcons are picked up automatically.
     */
    override fun attachBaseContext(newBase: android.content.Context) {
        val tag = LanguageStore(newBase).language()
        super.attachBaseContext(LanguageStore.wrap(newBase, tag))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        languageStore = LanguageStore(this)
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
        val llmTransport = OpenAiTransport(
            apiKeyProvider = { apiKeyStore.getKey() },
            // Spoken to the user, so they follow the app language.
            errors = OpenAiTransport.Errors(
                noKey = getString(R.string.error_no_api_key),
                network = getString(R.string.error_no_network),
                keyRejected = getString(R.string.error_key_rejected),
                rateLimited = getString(R.string.error_rate_limited),
                service = { code -> getString(R.string.error_service, code) },
            ),
        )

        colorIdAnalyzer = ColorIdAnalyzer()
        lightLevelAnalyzer = LightLevelAnalyzer()
        objectAnalyzer = TFLiteObjectAnalyzer(this)
        val modelProvider = { settingsManager.llmModel.value }
        cloudTranscriber = CloudTranscriber(llmTransport)
        objectCommandResolver = ObjectCommandResolver(
            transport = llmTransport,
            // The resolver has no Context of its own, so its spoken fallbacks are
            // handed in already localised.
            messages = ObjectCommandResolver.Messages(
                didNotCatch = getString(R.string.resolver_did_not_catch),
                couldNotMatch = getString(R.string.resolver_could_not_match),
                sayAnotherWay = getString(R.string.resolver_say_another_way),
                notSupported = getString(R.string.resolver_not_supported),
                notOnList = { raw -> getString(R.string.resolver_not_on_list, raw) },
            ),
        )
        documentChatViewModel = DocumentChatViewModel(
            context = this,
            transport = llmTransport,
            modelProvider = modelProvider,
        )
        imageChatViewModel = ImageChatViewModel(
            context = this,
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
                        pitchToneService = pitchToneService,
                        cloudTranscriber = cloudTranscriber,
                        welcomeStore = welcomeStore,
                        termsStore = termsStore,
                        languageStore = languageStore,
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
        pitchToneService.release()
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
    pitchToneService: PitchToneService,
    cloudTranscriber: CloudTranscriber,
    welcomeStore: WelcomeStore,
    termsStore: TermsStore,
    languageStore: LanguageStore,
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
    // Torch is runtime-only: off on every launch, and forced off whenever the app
    // is backgrounded (pauseForBackground unbinds the camera, which clears it).
    var torchOn by remember { mutableStateOf(false) }
    // On-device Whisper ships as base.en — English only. In any other language the
    // app stays on the system recogniser: no download prompt, no Settings row.
    // Only English ships a recorded spoken tutorial; other languages speak the
    // equivalent string through TTS. (The mic cues are wordless earcons and
    // serve every language, so they are not language-gated.)
    val hasRecordedTutorial = remember { !languageStore.isFinnish() }
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

    /**
     * Opens the mail app addressed to the support inbox. If the device has no
     * mail app the address is spoken instead — a silent no-op would leave a
     * blind user with no idea what happened. Settings also shows it as text.
     */
    fun openFeedbackEmail() {
        try {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$FEEDBACK_EMAIL")).apply {
                putExtra(Intent.EXTRA_SUBJECT, "Sight Buddy feedback")
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w("SightBuddyApp", "No mail app for $FEEDBACK_EMAIL", e)
            ttsService.speak(
                context.getString(R.string.settings_feedback_fallback),
                flush = true,
                force = true,
            )
        }
    }

    var findObjectTarget by remember { mutableStateOf("") }
    var showObjectPicker by remember { mutableStateOf(false) }

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
    var micFirstAttempt by remember { mutableStateOf(0) }               // guards the capture watchdog
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

    // One place decides how the app language and the download state combine. See
    // SpeechAvailability for why these two questions belong together.
    // BYOK: AI features, and cloud transcription with them, exist only with a key.
    val llmChatEnabled by apiKeyStore.keyPresent.collectAsState()
    val cloudSttEnabled by settingsManager.cloudStt.collectAsState()
    val speech = remember(sttModelsReady, cloudSttEnabled, llmChatEnabled) {
        SpeechAvailability(
            modelServesLanguage = !languageStore.isFinnish(),
            modelDownloaded = sttModelsReady,
            cloudEnabled = cloudSttEnabled && llmChatEnabled,
        )
    }
    val whisperSupported = speech.downloadRelevant

    val sttDownloadPercent =
        (sttModelDownloadState as? SttModelManager.DownloadState.Downloading)?.percent
    var showSttDownloadDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var sttPromptShownThisLaunch by remember { mutableStateOf(false) }

    fun startSttDownload() {
        showSttDownloadDialog = false
        // A download decision has been made — don't auto-prompt again this launch.
        sttPromptShownThisLaunch = true
        MainScope().launch {
            // force: the user asked for this download, often from inside Settings.
            ttsService.speak(
                context.getString(R.string.spoken_models_downloading),
                flush = false,
                force = true,
            )
            val ok = sttModelManager.downloadIfNeeded()
            if (ok) {
                voiceInputService.loadEngineIfReady()
                ttsService.speak(
                    context.getString(R.string.spoken_models_installed),
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
    /**
     * A picture has been taken and is still being described.
     *
     * The gap between the shutter and the answer is a second or two, and users
     * speak into it. Treating that as "no picture yet" told them to take one they
     * had just taken, which is the single most confusing thing the app did.
     */
    var captureInFlight by remember { mutableStateOf(false) }

    /** Set when a question arrives mid-capture, so the description is not spoken. */
    var questionSupersededCapture by remember { mutableStateOf(false) }

    fun modeNeedsCapture(mode: String?): Boolean = when (mode) {
        // A capture in flight counts as having one: a picture is on its way, so the
        // mic should open for a question rather than refuse.
        "Image chat" -> !imageScanned && !captureInFlight
        "Text chat" -> !textScanned
        else -> false
    }

    /** Clear any in-progress mic-first capture (e.g. on leaving the feature). */
    fun cancelMicFirst() {
        ttsService.releaseMicrophoneFloor()
        micFirstActive = false
        micFirstMode = null
        captureForQuestion = false
        pendingQuestion = null
        pendingCaptureBitmap?.recycle()
        pendingCaptureBitmap = null
    }

    /**
     * Declare that this recording is a mic-first question, before a word is said.
     *
     * It has to happen at press rather than at release. The platform recogniser
     * decides for itself when the user has stopped, so the transcript can arrive
     * while the button is still held. If the app has not been told yet that this
     * is a question, that transcript is handled as an ordinary command and the
     * capture then waits for a question that has already gone elsewhere.
     */
    fun armMicFirst(mode: String) {
        cancelMicFirst()
        micFirstActive = true
        micFirstMode = mode
    }

    /** Snap the frame for an armed mic-first question and await the pairing. */
    fun beginMicFirstCapture(mode: String) {
        // Deliberately no cancelMicFirst here: a question that has already been
        // transcribed must survive until the frame arrives to pair with it.
        micFirstActive = true
        micFirstMode = mode
        captureForQuestion = true
        voiceInputService.stopListening()
        // Alongside, not instead: the end-of-recording cue lands at the same moment
        // in auto-capture, and whichever started second used to cut the other off.
        soundFXService.playAlongside(SFX.CAMERA_CLICK)

        // Watchdog. The token guards against an older attempt firing on a newer
        // one after the user retries.
        micFirstAttempt += 1
        val attempt = micFirstAttempt
        micFirstScope.launch {
            delay(MIC_FIRST_CAPTURE_TIMEOUT_MS)
            if (attempt != micFirstAttempt || !micFirstActive) return@launch
            if (pendingCaptureBitmap != null) return@launch
            Log.w("SightBuddyApp", "Mic-first capture timed out: no frame from the camera")
            cancelMicFirst()
            ttsService.speak(context.getString(R.string.spoken_capture_failed), flush = true)
        }
    }

    /** Guard for async responses: only speak if the user is still in the feature. */
    fun featureStillActive(mode: String): Boolean =
        activeMode == mode && !showSettings && !showHelp && appInForeground

    var lastSessionActivityAtMs by remember { mutableStateOf(System.currentTimeMillis()) }

    val textPreviewEnabled by settingsManager.textPreviewEnabled.collectAsState()
    // BYOK: AI features are enabled exactly when the user has saved their own key.

    val cloudSttAsked by settingsManager.cloudSttAsked.collectAsState()
    var showCloudSttDialog by remember { mutableStateOf(false) }

    /**
     * Finishing the download turns the paid transcription off.
     *
     * Someone who enabled it before the model arrived should not keep paying for
     * what they now have for free. Only the moment of completion does this, not
     * every launch, so a user who deliberately chooses the API afterwards keeps
     * that choice.
     */
    var modelWasReady by remember { mutableStateOf(sttModelsReady) }
    LaunchedEffect(sttModelsReady) {
        if (sttModelsReady && !modelWasReady && cloudSttEnabled) {
            settingsManager.setCloudStt(false)
        }
        modelWasReady = sttModelsReady
    }

    /**
     * Follows a declined offer, once. Someone who stays on the platform recogniser
     * will meet its habit of cutting sentences short, and will read that as the app
     * failing unless they were told to expect it.
     */
    var showChoiceRespectedDialog by remember { mutableStateOf(false) }

    /**
     * Who is offered OpenAI transcription.
     *
     * Everyone whose speech the on-device model cannot handle, which is every
     * non-English user, plus English users who have not downloaded it. An English
     * user running Whisper already has the good path and does not need to spend
     * anything, so they are not offered it.
     */
    // Always offered, so the setting can always be found and turned off. It used
    // to hide once the free model was installed, which meant someone who had turned
    // it on earlier could not see what they were still paying for.
    val cloudSttOffered = true

    // The service only uses it when the user has said yes and a key exists.
    LaunchedEffect(cloudSttEnabled, llmChatEnabled) {
        voiceInputService.cloudTranscriber =
            if (cloudSttEnabled && llmChatEnabled) cloudTranscriber else null
    }

    /**
     * Offered unprompted only where the alternative is genuinely poor: a language
     * the on-device model cannot serve at all. English users are left to find it
     * in Settings, because the platform recogniser handles English well enough.
     * Asked once, whatever the answer.
     */
    LaunchedEffect(llmChatEnabled, cloudSttAsked, showWelcome, showTerms, tutorialBlocking) {
        if (showWelcome || showTerms || tutorialBlocking) return@LaunchedEffect
        if (cloudSttAsked || cloudSttEnabled || !llmChatEnabled) return@LaunchedEffect
        // Anyone not already running the on-device model: every non-English user,
        // and English users who have not downloaded it. Someone already on Whisper
        // has the good path for free and is not asked to pay for another.
        if (speech.engine == SpeechEngine.WHISPER) return@LaunchedEffect
        showCloudSttDialog = true
    }
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
    val loopCarousel by settingsManager.loopCarousel.collectAsState()
    val pitchFeedback by settingsManager.pitchFeedback.collectAsState()
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

    /**
     * Play the mic-on cue, then open the mic. The cue is a wordless earcon, so it
     * serves every language; VoiceInputService waits it out before recording.
     */
    // Mic feedback differs by who owns the recording.
    //
    // When the app owns it there is no other sound, so the two-tone pair marks the
    // ends and the difference between them says which end it was. On the platform
    // path the system beeps as well, and the old pair were pitched like the beep
    // and landed a beat after it: two tones fighting, which is what sounded
    // distorted. The perks are short and unpitched, so they sit beside the beep
    // rather than arguing with it, and start and stop differ there too: one sample
    // for both ends left no way to tell which end had just happened.
    fun cueThenListen() {
        // Recording takes the floor before the mic opens: speech stops at once and
        // nothing new is queued behind it.
        ttsService.takeMicrophoneFloor(
            VoiceInputService.MAX_RECORDING_MS + MIC_FLOOR_GRACE_MS
        )
        soundFXService.play(
            if (voiceInputService.usesSystemEarcons()) SFX.RECORDING_PERK else SFX.LISTENING
        )
        voiceInputService.startListening()
    }

    // When the app last asked the recogniser to stop. A recording the user ended
    // must not arm the restart guard, or a deliberate follow-up asked straight
    // afterwards is swallowed and nothing happens at all.
    var userStopAtMs by remember { mutableStateOf(0L) }

    /** Stop at the user's request, as opposed to the recogniser giving up. */
    fun stopListeningByUser() {
        userStopAtMs = System.currentTimeMillis()
        voiceInputService.stopListening()
        // The floor exists to keep speech out of an open mic, and the user has just
        // closed it. Holding it any longer swallowed whatever the app said next,
        // which is how the hold-to-speak prompt went missing on a short press: the
        // press took the floor, the release spoke, and nothing came out.
        ttsService.releaseMicrophoneFloor()
    }

    fun cueStopListening() {
        soundFXService.play(
            if (voiceInputService.usesSystemEarcons()) SFX.RECORDING_PERK_STOP else SFX.STOP_LISTENING
        )
    }

    fun pauseForBackground() {
        pitchToneService.stop()
        ttsService.releaseMicrophoneFloor()
        voiceInputService.stopListening()
        cutAllAudio()
        cancelMicFirst()
        // Drop a capture that was queued but never consumed. Without this it fires
        // against the first frame after the camera rebinds, so returning to the app
        // starts describing the scene on its own.
        triggerScan = false
        imageChatViewModel.cancelActiveRequest()
        documentChatViewModel.cancelActiveRequest()
        // Leaving the app drops the torch for good — it must never burn in the
        // background. (Overlays like Settings unbind the camera but keep the
        // request, so the light returns with the preview.)
        cameraXManager.clearTorchRequest()
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
    val holdToSpeakSetting by settingsManager.holdToSpeak.collectAsState()
    val holdToSpeak = holdToSpeakSetting && HOLD_TO_SPEAK_ENABLED
    val autoCaptureSetting by settingsManager.autoCaptureEnabled.collectAsState()
    // Works on every path now. It used to be restricted to recognisers the app
    // controls, because the frame was snapped by the gesture that ended the
    // recording and the platform recogniser ends recordings by itself. The frame
    // follows the recording ending instead, so there is no gesture to miss.
    val autoCaptureEnabled = autoCaptureSetting
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
        ttsService.speak(
                context.getString(R.string.spoken_mode_activated, ModeNames.display(context, mode)),
                flush = true,
            )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> appInForeground = true
                Lifecycle.Event.ON_STOP -> {
                    appInForeground = false
                    pauseForBackground()
                    // Camera unbind kills the light; keep the UI in step with it.
                    torchOn = false
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

    /**
     * Drop the captured frame and everything derived from it. Split out of
     * [expireSessionCaches] so leaving a feature can reuse it without the spoken
     * "session expired" notice.
     */
    fun stopFindObjectFeedback() {
        soundFXService.stop()
        pitchToneService.stop()
    }

    fun clearCapturedFrame() {
        imageScanned = false
        textScanned = false
        imageChatViewModel.resetSession()
        documentChatViewModel.resetSession()
        clearTextPlayback()
        pendingCaptureBitmap?.recycle()
        pendingCaptureBitmap = null
    }

    /**
     * Opening Settings ends whatever the app was in the middle of.
     *
     * Coming back used to resume it: a queued announcement about a picture from
     * before, or an auto-capture answering against that old frame with no shutter
     * to mark it. Anything half-finished is dropped here, so returning to a
     * feature always starts from nothing and every picture is announced by its
     * own shutter.
     */
    fun endSessionForSettings() {
        cutAllAudio()
        pitchToneService.stop()
        cancelMicFirst()
        triggerScan = false
        captureForQuestion = false
        imageChatViewModel.cancelActiveRequest()
        documentChatViewModel.cancelActiveRequest()
        clearCapturedFrame()
    }

    fun expireSessionCaches() {
        findObjectTarget = ""
        showObjectPicker = false
        imageScanned = false
        textScanned = false
        triggerScan = false
        imageChatViewModel.resetSession()
        documentChatViewModel.resetSession()
        clearTextPlayback()
        if (appInForeground) {
            ttsService.speak(
                context.getString(R.string.spoken_session_expired),
                flush = true,
            )
        }
        lastSessionActivityAtMs = now()
        Log.i("SightBuddyApp", "Session caches cleared due to idle/size policy")
    }

    LaunchedEffect(showWelcome, appInForeground) {
        if (showWelcome && appInForeground) {
            if (hasRecordedTutorial) {
                soundFXService.playTutorial { dismissWelcome() }
            } else {
                // No recorded tutorial in this language — speak it instead, and
                // dismiss on completion exactly as the recording would.
                ttsService.speak(
                    context.getString(R.string.tutorial_spoken),
                    flush = true,
                    force = true,
                    onDone = { dismissWelcome() },
                )
            }
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
            ttsService.speak(context.getString(R.string.spoken_offline), flush = true)
        }
    }

    // Settings is a hard stop, not a pause; see endSessionForSettings.
    LaunchedEffect(showSettings) {
        if (showSettings) endSessionForSettings()
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
        // The bundled Whisper model is English-only (base.en), so never offer it
        // in another language — those users stay on the system recogniser.
        if (!whisperSupported) return@LaunchedEffect
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
    // When the last recording ended, so a press arriving right after the platform
    // cut it off is not mistaken for a request to start another.
    var recordingEndedAtMs by remember { mutableStateOf(0L) }
    var wasListening by remember { mutableStateOf(false) }
    // Set when a press was swallowed, so its release is swallowed with it.
    var micPressSwallowed by remember { mutableStateOf(false) }
    LaunchedEffect(isListening) {
        if (wasListening && !isListening) {
            val endedByUser = System.currentTimeMillis() - userStopAtMs < USER_STOP_WINDOW_MS
            recordingEndedAtMs = if (endedByUser) 0L else System.currentTimeMillis()
            // A mic-first question snaps its frame here too, for the same reason.
            // It used to be snapped by the gesture that stopped the recording, but
            // the platform recogniser usually stops first, so that gesture never
            // came and the question waited for a picture nobody had taken.
            val armedMode = micFirstMode
            if (micFirstActive && armedMode != null &&
                pendingCaptureBitmap == null && !captureForQuestion
            ) {
                beginMicFirstCapture(armedMode)
            }
            // The one place a recording actually ends, so the one place to say so.
            // The platform recogniser stops on its own timing and its own beep
            // follows the notification volume, which is often turned down to
            // nothing; this cue follows the app volume and always sounds. It is
            // also exactly what turns the button back to Ask, so the sound and the
            // button can no longer disagree.
            cueStopListening()
        }
        wasListening = isListening
    }

    /**
     * True when a press should be ignored: the recogniser has only just stopped on
     * its own, and the user is almost certainly finishing the gesture they began
     * while it was still running. Only the platform recogniser stops by itself.
     */
    fun micPressIsStaleStop(): Boolean =
        voiceInputService.usesSystemEarcons() &&
            !isListening &&
            System.currentTimeMillis() - recordingEndedAtMs < MIC_RESTART_GUARD_MS

    var appHasStartedOnce by remember { mutableStateOf(false) }
    LaunchedEffect(appInForeground, ttsInitialized) {
        if (!appInForeground) return@LaunchedEffect
        if (!appHasStartedOnce) {
            appHasStartedOnce = true
            return@LaunchedEffect // first launch keeps the normal onboarding flow
        }
        cutAllAudio()
        // Coming back announces the feature the user is in, and nothing else.
        // Anything left half-finished stays silent until the user asks again.
        val mode = activeMode
        if (ttsInitialized && !showSettings && !showHelp && !tutorialBlocking && mode != null) {
            // Clear the guard so the announcement is allowed to repeat; it honours
            // the Settings switch either way.
            lastAnnouncedMode = null
            announceModeActivated(mode)
            // Find objects is the one feature that keeps working across a pause, so
            // it also says what it is still hunting for. Queued, not flushed, so it
            // follows the feature name instead of cutting it off.
            if (mode == ModeNames.FIND_OBJECTS && findObjectTarget.isNotBlank()) {
                ttsService.speak(
                    context.getString(
                        R.string.spoken_looking_for,
                        CocoFinnish.displayName(findObjectTarget),
                    ),
                    flush = false,
                )
            }
        } else {
            lastAnnouncedMode = mode
        }
    }

    // Voice input outcome feedback: auto-stop sounds and "nothing heard" prompts.
    LaunchedEffect(Unit) {
        voiceInputService.events.collect { event ->
            ttsService.releaseMicrophoneFloor()
            if (!appInForeground) return@collect
            // Mic-first with no speech = "just describe the scene": pair an empty
            // question with the snapped frame instead of prompting to repeat.
            if (micFirstActive) {
                pendingQuestion = ""
                return@collect
            }
            when (event) {
                is VoiceInputService.SttEvent.AutoStopped -> {
                    if (event.noSpeech) {
                        ttsService.speak(
                            context.getString(R.string.spoken_nothing_heard),
                            flush = true,
                        )
                    }
                }
                VoiceInputService.SttEvent.Empty -> {
                    ttsService.speak(context.getString(R.string.spoken_repeat), flush = true)
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
            ttsService.releaseMicrophoneFloor()
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
                                    ttsService.speak(context.getString(R.string.spoken_looking_for, CocoFinnish.displayName(resolution.item)), flush = true)
                                    Log.i(
                                        "SightBuddyApp",
                                        "Find-objects target activated via ${resolution.source} with confidence ${resolution.confidence}"
                                    )
                                }
                                is ObjectCommandResolver.ResolveResult.Clarify -> {
                                    // Say what went wrong and stop there. The user retries by
                                    // speaking again, or opens the list themselves.
                                    ttsService.speak(resolution.message, flush = true)
                                }
                                is ObjectCommandResolver.ResolveResult.Unavailable -> {
                                    ttsService.speak(resolution.message, flush = true)
                                    Log.i("SightBuddyApp", "Find-objects unavailable request: $command")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("SightBuddyApp", "Find-objects resolver error for '$command'", e)
                            ttsService.speak(context.getString(R.string.spoken_repeat), flush = true)
                        }
                    }
                    "Image chat" -> {
                        if (!llmChatEnabled) {
                            ttsService.speak(context.getString(R.string.spoken_llm_off), flush = true)
                            return@collectLatest
                        }
                        if (imageScanned) {
                            val response = imageChatViewModel.askFollowUp(command)
                            if (featureStillActive("Image chat")) ttsService.speak(response, flush = true)
                            touchSessionActivity()
                        } else if (captureInFlight) {
                            // The picture is still being described. Asking something
                            // specific makes that description redundant, so wait only
                            // for the frame to be encoded, drop the description, and
                            // answer the question against the same picture. One call
                            // instead of two, and no answer to a question nobody asked.
                            questionSupersededCapture = true
                            val ready = withTimeoutOrNull(CAPTURE_ENCODE_TIMEOUT_MS) {
                                while (!imageChatViewModel.hasCachedImage()) delay(50)
                                true
                            }
                            if (ready == null) {
                                questionSupersededCapture = false
                                ttsService.speak(
                                    context.getString(R.string.spoken_capture_first),
                                    flush = true,
                                )
                            } else {
                                imageChatViewModel.cancelActiveRequest()
                                val response = imageChatViewModel.askFollowUp(command)
                                if (featureStillActive("Image chat")) {
                                    ttsService.speak(response, flush = true)
                                }
                                touchSessionActivity()
                            }
                        } else {
                            ttsService.speak(context.getString(R.string.spoken_capture_first), flush = true)
                        }
                    }
                    "Text chat" -> {
                        if (!llmChatEnabled) {
                            ttsService.speak(context.getString(R.string.spoken_llm_off), flush = true)
                            return@collectLatest
                        }
                        if (textScanned) {
                            val response = documentChatViewModel.askFollowUp(command)
                            if (featureStillActive("Text chat")) speakOutsidePlayer(response)
                            touchSessionActivity()
                        } else {
                            ttsService.speak(context.getString(R.string.spoken_capture_text_first), flush = true)
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
                        ttsService.speakWithCooldown(context.getString(result.nameRes))
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
                        ttsService.speakWithCooldown(context.getString(result.descriptionRes))
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
                            ttsService.speakWithCooldown(CocoFinnish.displayName(topDetection.label))
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

                                if (pitchFeedback) {
                                    // A held note that climbs the scale as the object
                                    // approaches the middle. Nothing is said, so the
                                    // user can sweep the phone and hear it rise.
                                    lastDirection = ""
                                    pitchToneService.start(
                                        pitchToneService.stepFor(proximity)
                                    )
                                } else if (direction != lastDirection) {
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
                                    pitchToneService.stop()
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
                            questionSupersededCapture = false
                            captureInFlight = true
                            soundFXService.play(SFX.CAMERA_CLICK)
                            val response = try {
                                withContext(Dispatchers.Default) {
                                    val bitmap = imageProxyToBitmap(proxy)
                                    val text = imageChatViewModel.processImage(bitmap)
                                    bitmap.recycle()
                                    text
                                }
                            } finally {
                                captureInFlight = false
                            }
                            imageScanned = true
                            // A question asked while this was running has already been
                            // answered against the same picture, so the general
                            // description is stale and would talk over that answer.
                            if (questionSupersededCapture) {
                                questionSupersededCapture = false
                            } else if (featureStillActive("Image chat")) {
                                ttsService.speak(response, flush = true)
                            }
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
                                        speakOutsidePlayer(context.getString(R.string.spoken_no_text_detected))
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
                languageChosen = languageStore.hasChosen(),
                currentLanguage = languageStore.language(),
                onSelectLanguage = { tag ->
                    if (!languageStore.hasChosen() || languageStore.language() != tag) {
                        languageStore.setLanguage(tag)
                        // Recreate so attachBaseContext re-resolves every resource
                        // (strings and the spoken earcons) in the new language.
                        (context as? android.app.Activity)?.recreate()
                    }
                },
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
                                    context.getString(R.string.spoken_api_key_saved)
                                } else {
                                    context.getString(R.string.spoken_api_key_removed)
                                },
                                flush = true,
                                force = true,
                            )
                        },
                        onSpeechRateCycle = {
                            val newRate = settingsManager.cycleTtsSpeechRate()
                            // Apply before announcing. The effect that mirrors this
                            // setting runs after recomposition, so without this the
                            // preview is spoken at the rate we just moved away from.
                            ttsService.setSpeechRate(newRate)
                            ttsService.speak(
                                context.getString(R.string.spoken_speech_rate, SettingsManager.ttsRateLabel(context, newRate)),
                                flush = true,
                                force = true,
                            )
                        },
                        whisperSupported = whisperSupported,
                        onLeaveFeedback = { openFeedbackEmail() },
                        sttModelsReady = sttModelsReady,
                        sttDownloadPercent = sttDownloadPercent,
                        // Nothing about the on-device model may surface in a language
                        // it cannot serve, from any entry point.
                        onRequestSttDownload = {
                            if (whisperSupported) showSttDownloadDialog = true
                        },
                        cloudSttOffered = cloudSttOffered,
                        onRequestCloudStt = { showCloudSttDialog = true },
                        currentLanguageName = stringResource(
                            if (languageStore.isFinnish()) R.string.language_finnish
                            else R.string.language_english
                        ),
                        otherLanguageName = stringResource(
                            if (languageStore.isFinnish()) R.string.language_english
                            else R.string.language_finnish
                        ),
                        onSwitchLanguage = { showLanguageDialog = true },
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
                    loopCarousel = loopCarousel,
                    torchOn = torchOn,
                    torchAvailable = cameraXManager.deviceHasFlash(),
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
                    onModeSelected = { mode ->
                        if (activeMode != mode) {
                            cutAllAudio()
                            cancelMicFirst()
                            imageChatViewModel.cancelActiveRequest()
                            documentChatViewModel.cancelActiveRequest()
                            activeMode = mode
                            // Clear first, announce second. clearCapturedFrame stops
                            // playback, so announcing before it cut the announcement
                            // off mid-word.
                            //
                            // Leaving a feature drops its captured frame, so an answer
                            // can only ever describe the picture just taken. The caching
                            // itself is untouched: flip KEEP_CAPTURES_ACROSS_FEATURES
                            // back to true to restore it.
                            stopFindObjectFeedback()
                            if (!KEEP_CAPTURES_ACROSS_FEATURES) clearCapturedFrame()
                            triggerScan = false
                            announceModeActivated(mode)
                            touchSessionActivity()

                            if (mode == "Find objects" && findObjectTarget.isNotBlank()) {
                                // Re-announce cached target so users know tracking is still active.
                                ttsService.speak(context.getString(R.string.spoken_looking_for, CocoFinnish.displayName(findObjectTarget)), flush = false)
                            }
                        }
                    },
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
                            } else {
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
                        if (tapAccepted && micPressIsStaleStop()) {
                            // Swallowed: this is the tail of a stop, not a new start.
                            Log.i("SightBuddyApp", "Ignoring mic tap just after the recogniser stopped")
                        } else if (tapAccepted && activeMode in micModes) {
                            if (isListening) {
                                stopListeningByUser()
                            } else {
                                val mode = activeMode
                                if (mode != null && modeNeedsCapture(mode) && !autoCaptureEnabled) {
                                    cutAllAudio()
                                    ttsService.speak(
                                        if (mode == ModeNames.TEXT_CHAT) {
                                            context.getString(R.string.spoken_capture_text_first)
                                        } else {
                                            context.getString(R.string.spoken_capture_first)
                                        },
                                        flush = true,
                                    )
                                } else {
                                    cutAllAudio()
                                    if (mode != null && modeNeedsCapture(mode)) armMicFirst(mode)
                                    cueThenListen()
                                }
                            }
                        }
                    },
                    onTakePicture = if (activeMode == "Image chat" || activeMode == "Text chat") {
                        {
                            // A new picture starts a new session, so whatever is being
                            // said about the old one is cut rather than left to finish
                            // over the top of the new answer.
                            cutAllAudio()
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
                            ttsService.speak(context.getString(R.string.spoken_looking_for, CocoFinnish.displayName(selected)), flush = true)
                            touchSessionActivity()
                        },
                        onDismiss = { showObjectPicker = false }
                    )
                }
            }
        } else {
            Text(text = context.getString(R.string.waiting_camera_permission))
        }

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
                    (context as? android.app.Activity)?.recreate()
                },
                onDismiss = { showLanguageDialog = false },
            )
        }

        if (showSttDownloadDialog && whisperSupported) {
            SttDownloadDialog(
                highContrast = highContrast,
                whiteMode = whiteMode,
                onDownload = { startSttDownload() },
                onLater = {
                    settingsManager.setSttDownloadChoice(SettingsManager.STT_CHOICE_LATER)
                    showSttDownloadDialog = false
                    showChoiceRespectedDialog = true
                },
                onNever = {
                    settingsManager.setSttDownloadChoice(SettingsManager.STT_CHOICE_NEVER)
                    showSttDownloadDialog = false
                    showChoiceRespectedDialog = true
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
