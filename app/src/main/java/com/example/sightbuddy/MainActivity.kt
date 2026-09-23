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
import androidx.compose.runtime.derivedStateOf
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
import com.example.sightbuddy.core.CueBeat
import com.example.sightbuddy.core.DirectionCues
import com.example.sightbuddy.core.HapticManager
import com.example.sightbuddy.core.LanguageStore
import com.example.sightbuddy.core.ModeNames
import com.example.sightbuddy.core.NetworkStatusManager
import com.example.sightbuddy.core.SettingsManager
import com.example.sightbuddy.core.SoundFXService
import com.example.sightbuddy.core.TermsStore
import com.example.sightbuddy.core.ApiKeyStore
import com.example.sightbuddy.core.createCrashReporter
import com.example.sightbuddy.core.hints.FeatureHelpLibrary
import com.example.sightbuddy.core.hints.HelpContent
import com.example.sightbuddy.core.PitchToneService
import com.example.sightbuddy.R
import com.example.sightbuddy.core.SoundFXService.SFX
import com.example.sightbuddy.core.TextScriptPlayer
import com.example.sightbuddy.core.TTSService
import com.example.sightbuddy.core.stt.CloudTranscriber
import com.example.sightbuddy.core.stt.SpeechAvailability
import com.example.sightbuddy.core.stt.SpeechEngine
import com.example.sightbuddy.core.stt.SttModelManager
import com.example.sightbuddy.core.stt.SttChoice
import com.example.sightbuddy.core.ModelSetup
import com.example.sightbuddy.core.ModelDownloadService
import kotlinx.coroutines.flow.map
import com.example.sightbuddy.core.stt.VoiceInputService
import com.example.sightbuddy.features.chat.DocumentChatViewModel
import com.example.sightbuddy.features.chat.ImageChatViewModel
import com.example.sightbuddy.features.chat.LocalTextExtractor
import com.example.sightbuddy.features.chat.LiveTextTracker
import com.example.sightbuddy.features.chat.LiveReadQueue
import com.example.sightbuddy.features.chat.TextAimGuide
import com.example.sightbuddy.features.chat.OcrDebug
import com.example.sightbuddy.features.chat.PageTracker
import com.example.sightbuddy.features.chat.TextSections
import com.example.sightbuddy.features.chat.LiveText
import com.example.sightbuddy.features.chat.PaperFinder
import com.example.sightbuddy.ui.screens.OcrDebugOverlay
import com.example.sightbuddy.ui.isScreenReaderOn
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
import com.example.sightbuddy.ui.screens.ModelSetupDialog
import com.example.sightbuddy.ui.screens.MemoriesScreen
import com.example.sightbuddy.ui.screens.BarToggle
import com.example.sightbuddy.ui.screens.SttModelsDialog
import com.example.sightbuddy.ui.screens.GemmaDownloadDialog
import com.example.sightbuddy.ui.screens.TermsAcceptanceOverlay
import com.example.sightbuddy.ui.screens.TranscriptEntry
import com.example.sightbuddy.ui.theme.SIghtbuddyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import com.example.sightbuddy.core.PrivateLog
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.lifecycleScope
import android.content.Context
import android.os.SystemClock
import android.util.Base64
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.sightbuddy.core.MemoryEntry
import com.example.sightbuddy.core.MemoryNames
import com.example.sightbuddy.core.MemoryStore
import com.example.sightbuddy.core.llm.LocalGemma
import com.example.sightbuddy.core.llm.PromptTooLongException
import java.io.File

class MainActivity : ComponentActivity() {
    private lateinit var cameraXManager: CameraXManager
    private lateinit var networkStatusManager: NetworkStatusManager
    private lateinit var ttsService: TTSService
    private lateinit var voiceInputService: VoiceInputService
    private lateinit var localGemma: LocalGemma
    private lateinit var memoryStore: MemoryStore
    private lateinit var sttModelManager: SttModelManager
    private lateinit var hapticManager: HapticManager
    private lateinit var beepService: BeepService
    private lateinit var soundFXService: SoundFXService
    private lateinit var settingsManager: SettingsManager
    private lateinit var termsStore: TermsStore
    private lateinit var languageStore: LanguageStore
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
     * the values-fi strings are picked up automatically.
     */
    override fun attachBaseContext(newBase: Context) {
        val tag = LanguageStore(newBase).language()
        super.attachBaseContext(LanguageStore.wrap(newBase, tag))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        languageStore = LanguageStore(this)
        // First: the voice service reads the user's switches from its first moment.
        settingsManager = SettingsManager(this)
        cameraXManager = CameraXManager(this)
        networkStatusManager = NetworkStatusManager(this)
        ttsService = TTSService(this)
        sttModelManager = SttModelManager(this, BuildConfig.STT_MODEL_BASE_URL)
        // Loaded from the UI once the user's switches are known; see SightBuddyApp.
        localGemma = LocalGemma(this)
        memoryStore = MemoryStore(this)
        runGemmaEval(intent)
        voiceInputService = VoiceInputService(
            this,
            sttModelManager,
            localGemma,
            sttChoice = { SttChoice.fromKey(settingsManager.sttChoice.value) },
        )
        // Whisper models are downloaded only on user request (Settings or the
        // first-launch prompt) — no automatic 154 MB download.
        hapticManager = HapticManager(this)
        beepService = BeepService()
        soundFXService = SoundFXService(this)
        // The spoken tutorial is gone, and its "already seen" flag with it.
        deleteSharedPreferences("sight_buddy_welcome")
        termsStore = TermsStore(this)
        // TalkBack says the window's title whenever the window comes up, on launch
        // and back from each permission dialog. Until the terms are accepted that
        // is "Choose your language", the first thing to do, not "Sight Buddy".
        if (!termsStore.hasAcceptedTerms()) title = getString(R.string.language_title)

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
                apiOff = getString(R.string.error_api_off),
                localFailed = getString(R.string.error_local_failed),
                tooLong = getString(R.string.error_too_long),
                chatFull = getString(R.string.error_chat_full),
            ),
            apiAllowed = { settingsManager.useApi.value },
            online = { networkStatusManager.isOnline.value },
            local = localGemma,
            // Any language: Gemma gets the same prompts as OpenAI, which ask for the
            // reply in the app language (Finnish too). It is not offered in Finnish,
            // but someone who downloads it from Advanced gets it.
            localAllowed = { settingsManager.useLocalChat.value },
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
        if (OcrDebug.enabled) OcrDebug.init(getExternalFilesDir("ocr"))

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
                        termsStore = termsStore,
                        languageStore = languageStore,
                        apiKeyStore = apiKeyStore,
                        localGemma = localGemma,
                        memoryStore = memoryStore,
                        nameWithGemma = ::nameWithGemma,
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        runGemmaEval(intent)
    }

    /**
     * A short name for a saved chat from Gemma, different from every name in
     * [taken]: identical or at least 80% alike counts as the same, and Gemma is
     * asked again. Null when Gemma cannot answer, so the plain name stays.
     */
    private fun nameWithGemma(
        entries: List<MemoryEntry>,
        taken: List<String>,
    ): String? {
        val chat = entries.joinToString("\n") { "${it.kind.lowercase()}: ${it.text}" }.take(3000)
        val refused = mutableListOf<String>()
        repeat(4) { attempt ->
            val avoid = (taken.takeLast(30) + refused).distinct()
            val prompt = "Give this saved conversation a short title of 2 to 5 words. " +
                "Reply with the title only." + OpenAiTransport.replyLanguageInstruction() +
                (if (avoid.isEmpty()) "" else " It must be clearly different from these titles: " + avoid.joinToString("; ") + ".") +
                "\n\n" + chat
            val body = org.json.JSONObject()
                .put("messages", org.json.JSONArray().put(org.json.JSONObject().put("role", "user").put("content", prompt)))
                .put("max_tokens", 20)
                // A little more variety each time it has to try again.
                .put("temperature", 0.3 + 0.3 * attempt)
            // A chat too long to name keeps its plain name.
            val raw = try {
                localGemma.complete(body)
            } catch (e: PromptTooLongException) {
                null
            } ?: return null
            val name = MemoryNames.clean(raw)
            if (name.isEmpty()) return@repeat
            if (!MemoryNames.clashes(name, taken)) return name
            refused += name
        }
        return refused.firstOrNull()?.let { MemoryNames.numbered(it, taken) }
    }

    /**
     * Debug builds only: runs Gemma on an image file and logs the answer (tag
     * GEMMA-EVAL), for comparing models on the same test images over adb:
     *   adb shell am start -n <app id>/com.example.sightbuddy.MainActivity      *     --es gemma_eval_image <path readable by the app> --es prompt "..."
     */
    private fun runGemmaEval(intent: Intent?) {
        if (!BuildConfig.DEBUG) return
        val path = intent?.getStringExtra("gemma_eval_image") ?: return
        val prompt = intent.getStringExtra("prompt") ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val image = Base64.encodeToString(File(path).readBytes(), Base64.NO_WRAP)
            val content = org.json.JSONArray()
                .put(org.json.JSONObject().put("type", "text").put("text", prompt))
                .put(org.json.JSONObject().put("type", "image_url")
                    .put("image_url", org.json.JSONObject().put("url", "data:image/jpeg;base64,$image")))
            val body = org.json.JSONObject()
                .put("messages", org.json.JSONArray().put(org.json.JSONObject().put("role", "user").put("content", content)))
                .put("max_tokens", 200)
                .put("temperature", 0.0)
            val started = SystemClock.elapsedRealtime()
            val answer = runCatching { localGemma.complete(body) }.getOrElse { "failed: $it" }
            Log.i("GEMMA-EVAL", "${java.io.File(path).name} ${android.os.SystemClock.elapsedRealtime() - started} ms: $answer")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraXManager.shutdown()
        networkStatusManager.shutdown()
        ttsService.shutdown()
        voiceInputService.shutdown()
        // Stop any answer, then free the model off the main thread: release waits
        // for the model call to end, and waiting here would freeze the screen.
        localGemma.cancel()
        Thread { localGemma.release() }.start()
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
    termsStore: TermsStore,
    languageStore: LanguageStore,
    apiKeyStore: ApiKeyStore,
    localGemma: LocalGemma,
    memoryStore: MemoryStore,
    nameWithGemma: (List<MemoryEntry>, List<String>) -> String?,
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
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    // Survive the recreate a language choice causes, so the start-up request is
    // made once, and Accept asks again only once.
    val startupPermissionsAsked = rememberSaveable { mutableStateOf(false) }
    val termsPermissionRetried = rememberSaveable { mutableStateOf(false) }
    val c = remember {
        AppController(
            context = context,
            cameraXManager = cameraXManager,
            ttsService = ttsService,
            voiceInputService = voiceInputService,
            hapticManager = hapticManager,
            beepService = beepService,
            soundFXService = soundFXService,
            pitchToneService = pitchToneService,
            cloudTranscriber = cloudTranscriber,
            termsStore = termsStore,
            languageStore = languageStore,
            apiKeyStore = apiKeyStore,
            localGemma = localGemma,
            memoryStore = memoryStore,
            nameWithGemma = nameWithGemma,
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
            scope = scope,
            startupPermissionsAskedState = startupPermissionsAsked,
            termsPermissionRetriedState = termsPermissionRetried,
            initiallyInForeground = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
        )
    }

    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        c.onPermissionResult(ok)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions -> c.onStartupPermissionsResult(permissions) }
    c.requestPermission = { askPermission.launch(it) }
    c.requestPermissions = { permissionLauncher.launch(it) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> c.onLifecycleStart()
                Lifecycle.Event.ON_STOP -> c.onLifecycleStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        c.appInForeground = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // The effects' keys that live in flows, collected here so a change relaunches
    // an effect exactly as before. Keys that are the controller's own state are
    // read from it directly.
    val gemmaInstalled by localGemma.installedState.collectAsState()
    val useLocalChat by settingsManager.useLocalChat.collectAsState()
    settingsManager.sttChoice.collectAsState().value
    val sttModelDownloadState by sttModelManager.downloadState.collectAsState()
    val sttModelsReady = sttModelDownloadState is SttModelManager.DownloadState.Ready
    val apiKeyPresent by apiKeyStore.keyPresent.collectAsState()
    val useApi by settingsManager.useApi.collectAsState()
    val apiAllowed = apiKeyPresent && useApi
    val llmChatEnabled = apiAllowed || (gemmaInstalled && useLocalChat)
    val cloudSttEnabled by settingsManager.cloudStt.collectAsState()
    val cloudSttAsked by settingsManager.cloudSttAsked.collectAsState()
    val ttsRate by settingsManager.ttsSpeechRate.collectAsState()
    val ttsInitialized by ttsService.isInitialized.collectAsState()
    val isListening by voiceInputService.isListening.collectAsState()
    val startFeaturesMuted by settingsManager.startFeaturesMuted.collectAsState()
    val isOnline by networkStatusManager.isOnline.collectAsState()
    val textPreviewEnabled by settingsManager.textPreviewEnabled.collectAsState()
    // chatWorking follows these.
    imageChatViewModel.isProcessing.collectAsState().value
    documentChatViewModel.isProcessing.collectAsState().value
    val effectiveStt = c.effectiveStt
    val effectiveTextPreviewEnabled = textPreviewEnabled || !llmChatEnabled

    with(c) {
        LaunchedEffect(gemmaInstalled, effectiveStt, useLocalChat) { syncGemmaLoaded() }
        LaunchedEffect(sttModelsReady) { onSttModelsReadyChanged() }
        LaunchedEffect(cloudSttEnabled, apiAllowed) { syncCloudTranscriber() }
        LaunchedEffect(apiAllowed, cloudSttAsked, showTerms, onboardingBlocking) { offerCloudStt() }
        LaunchedEffect(ttsRate, ttsInitialized) { applySpeechRate() }
        LaunchedEffect(appInForeground, isListening, showTerms, popupOpen, showSettings, onboardingBlocking) {
            syncAppSpeech()
        }
        LaunchedEffect(popupOpen) { onPopupChanged() }
        LaunchedEffect(chatWorking, popupOpen, showSettings, isListening, appInForeground) { workingTick() }
        LaunchedEffect(showSettings, showObjectPicker) { onSettingsOrPickerChanged() }
        LaunchedEffect(activeMode, startFeaturesMuted, showSettings) { applyStartMuted() }
        LaunchedEffect(Unit) { requestStartupPermissions() }
        LaunchedEffect(appInForeground) { refreshPermissions() }
        LaunchedEffect(isOnline, llmChatEnabled, ttsInitialized, showTerms, onboardingBlocking, appInForeground) {
            noticeOffline()
        }
        LaunchedEffect(showSettings) { onSettingsChanged() }
        LaunchedEffect(cameraDisabled) { onCameraDisabledChanged() }
        LaunchedEffect(onboardingBlocking, showTerms, appInForeground) { offerModelSetup() }
        LaunchedEffect(isListening) { onListeningChanged() }
        LaunchedEffect(appInForeground, ttsInitialized) { onReturnToApp() }
        LaunchedEffect(Unit) { collectVoiceEvents() }
        LaunchedEffect(pendingCaptureBitmap, pendingQuestion, micFirstActive) { pairMicFirst() }
        LaunchedEffect(showTerms, onboardingBlocking, appInForeground) { collectCommands() }
        LaunchedEffect(showTerms, onboardingBlocking, appInForeground) { expireIdleSessions() }
        LaunchedEffect(
            hasCameraPermission,
            activeMode,
            effectiveTextPreviewEnabled,
            cameraDisabled,
            llmChatEnabled,
            showObjectPicker,
            appInForeground,
        ) { runFramePipeline() }
    }

    AppScreens(c, modifier)
}
