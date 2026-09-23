package com.example.sightbuddy

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.sightbuddy.BuildConfig
import com.example.sightbuddy.R
import com.example.sightbuddy.core.ApiKeyStore
import com.example.sightbuddy.core.AppWork
import com.example.sightbuddy.core.BeepService
import com.example.sightbuddy.core.CameraXManager
import com.example.sightbuddy.core.CueBeat
import com.example.sightbuddy.core.DirectionCues
import com.example.sightbuddy.core.HapticManager
import com.example.sightbuddy.core.LanguageStore
import com.example.sightbuddy.core.ModeNames
import com.example.sightbuddy.core.ModelDownloadService
import com.example.sightbuddy.core.ModelSetup
import com.example.sightbuddy.core.NetworkStatusManager
import com.example.sightbuddy.core.OpenAiTransport
import com.example.sightbuddy.core.PitchToneService
import com.example.sightbuddy.core.PrivateLog
import com.example.sightbuddy.core.SettingsManager
import com.example.sightbuddy.core.SoundFXService
import com.example.sightbuddy.core.SoundFXService.SFX
import com.example.sightbuddy.core.TTSService
import com.example.sightbuddy.core.TermsStore
import com.example.sightbuddy.core.TextScriptPlayer
import com.example.sightbuddy.core.createCrashReporter
import com.example.sightbuddy.core.hints.FeatureHelpLibrary
import com.example.sightbuddy.core.hints.HelpContent
import com.example.sightbuddy.core.stt.CloudTranscriber
import com.example.sightbuddy.core.stt.SpeechAvailability
import com.example.sightbuddy.core.stt.SpeechEngine
import com.example.sightbuddy.core.stt.SttChoice
import com.example.sightbuddy.core.stt.SttModelManager
import com.example.sightbuddy.core.stt.VoiceInputService
import com.example.sightbuddy.features.chat.ChatSession
import com.example.sightbuddy.features.chat.DocumentChatViewModel
import com.example.sightbuddy.features.chat.ImageChatViewModel
import com.example.sightbuddy.features.chat.LiveReadQueue
import com.example.sightbuddy.features.chat.LiveText
import com.example.sightbuddy.features.chat.LiveTextTracker
import com.example.sightbuddy.features.chat.LocalTextExtractor
import com.example.sightbuddy.features.chat.OcrDebug
import com.example.sightbuddy.features.chat.PageTracker
import com.example.sightbuddy.features.chat.PaperFinder
import com.example.sightbuddy.features.chat.TextAimGuide
import com.example.sightbuddy.features.chat.TextSections
import com.example.sightbuddy.features.vision.CocoFinnish
import com.example.sightbuddy.features.vision.ColorIdAnalyzer
import com.example.sightbuddy.features.vision.LightLevelAnalyzer
import com.example.sightbuddy.features.vision.ObjectCommandResolver
import com.example.sightbuddy.features.vision.TFLiteObjectAnalyzer
import com.example.sightbuddy.ui.isScreenReaderOn
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
import com.example.sightbuddy.ui.theme.SIghtbuddyTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Build
import android.text.format.Formatter
import androidx.camera.core.ImageProxy
import com.example.sightbuddy.core.MemoryEntry
import com.example.sightbuddy.core.MemoryNames
import com.example.sightbuddy.core.MemoryStore
import com.example.sightbuddy.core.llm.LocalGemma
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.CompletableDeferred

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

/**
 * Longer side of the copy of the frame text guidance reads, out of Text chat's
 * 1920: about 70% of the pixels. Half (960) read several times faster, but
 * magazine text at arm's length was then too small to be found at all: 9
 * blocks a frame where the full frame had 70 to 110; 1280 still missed some.
 * Capture reads the full frame.
 */
private const val AIM_FRAME_LONG_SIDE = 1600

/** A page box smaller than this share of the frame is part of a page: Capture reads everything. */
private const val MIN_CAPTURE_PAGE_AREA = 0.15f

/** Utterance ids of the live text's reading start with this, one per box. */
private const val LIVE_TEXT_READING_ID = "LIVE_TEXT_READING"

/** A block with fewer letters than this is not worth showing as live text. */
private const val LIVE_TEXT_MIN_LETTERS = 2

/** The working tick: how long an answer may take before it starts, and its beat. */
private const val WORKING_TICK_DELAY_MS = 700L
private const val WORKING_TICK_INTERVAL_MS = 1400L

/** How recent the guide's page must be for Capture to read only that page. */
private const val AIMED_PAGE_FRESH_MS = 2_000L


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
 * After "Let me take a picture" or "Looking for text" finishes, a moment to hold
 * still before the shutter or the guidance.
 */
private const val AUTO_CAPTURE_PAUSE_MS = 500L

/**
 * The user's side of the chat while they are still speaking: shown from the
 * moment Ask is pressed, so their question is always the first thing in the
 * chat, and replaced by their words once those are recognised.
 */
private const val SPEAKING_PLACEHOLDER = "…"

/** How long a "…" with no words ever arriving is left before it is taken down. */
private const val SPEAKING_PLACEHOLDER_MAX_MS = 8_000L

/** The longest the shutter waits on the note, should its end never be reported. */
private const val AUTO_CAPTURE_NOTE_MAX_MS = 5_000L

/**
 * Slack added to the recording cap when the microphone takes the audio floor, so
 * the hold always outlives the longest possible recording and still expires by
 * itself if the release is somehow missed.
 */
private const val MIC_FLOOR_GRACE_MS = 5_000L

/**
 * The app's state and behaviour, out of the composable: what is on screen, the
 * feature sessions, recording, capture, the camera pipeline, and the effects
 * that react to them. SightBuddyApp keeps what only a composable can do
 * (launchers, the lifecycle observer, collecting flows so the screen redraws)
 * and launches the effects here with the same keys as before.
 *
 * Kept out of the composable because a composable this size ran interpreted:
 * ART refuses to compile a method over its instruction limit.
 *
 * Values that live in flows are read through getters (`flow.value`), so the
 * logic always sees the current value; the screens collect the same flows to
 * redraw.
 */
class AppController(
    val context: Context,
    val cameraXManager: CameraXManager,
    val ttsService: TTSService,
    val voiceInputService: VoiceInputService,
    val hapticManager: HapticManager,
    val beepService: BeepService,
    val soundFXService: SoundFXService,
    val pitchToneService: PitchToneService,
    val cloudTranscriber: CloudTranscriber,
    val termsStore: TermsStore,
    val languageStore: LanguageStore,
    val apiKeyStore: ApiKeyStore,
    val localGemma: LocalGemma,
    val memoryStore: MemoryStore,
    val nameWithGemma: (List<MemoryEntry>, List<String>) -> String?,
    val sttModelManager: SttModelManager,
    val settingsManager: SettingsManager,
    val networkStatusManager: NetworkStatusManager,
    val colorIdAnalyzer: ColorIdAnalyzer,
    val lightLevelAnalyzer: LightLevelAnalyzer,
    val objectAnalyzer: TFLiteObjectAnalyzer,
    val objectCommandResolver: ObjectCommandResolver,
    val documentChatViewModel: DocumentChatViewModel,
    val imageChatViewModel: ImageChatViewModel,
    val localTextExtractor: LocalTextExtractor,
    /** The composition's scope: work started here ends with the screen. */
    private val scope: CoroutineScope,
    /** Saveable in the composable, so they survive the recreate a language change causes. */
    startupPermissionsAskedState: MutableState<Boolean>,
    termsPermissionRetriedState: MutableState<Boolean>,
    initiallyInForeground: Boolean,
) {
    /** Android's permission dialog for one permission; set by the composable. */
    var requestPermission: (String) -> Unit = {}

    /** The start-up request for several permissions at once; set by the composable. */
    var requestPermissions: (Array<String>) -> Unit = {}

    /** Android's answer to [ensurePermission]'s request. */
    fun onPermissionResult(ok: Boolean) {
        val permission = permissionAsked
        permissionAsked = null
        when (permission) {
            Manifest.permission.CAMERA -> hasCameraPermission = ok
            Manifest.permission.RECORD_AUDIO -> hasAudioPermission = ok
        }
        val reason = when (permission) {
            Manifest.permission.CAMERA -> R.string.spoken_permission_camera
            Manifest.permission.RECORD_AUDIO -> R.string.spoken_permission_mic
            else -> null
        }
        if (!ok && reason != null) ttsService.speak(context.getString(reason), flush = true, force = true)
    }

    /** Android's answer to the start-up request, or to Accept asking once more. */
    fun onStartupPermissionsResult(permissions: Map<String, Boolean>) {
        hasCameraPermission = permissions[Manifest.permission.CAMERA] == true
        hasAudioPermission = permissions[Manifest.permission.RECORD_AUDIO] == true
        permissionsResolved = true
        if (acceptAfterPermissions) {
            acceptAfterPermissions = false
            acceptTerms()
        }
    }

    fun onLifecycleStart() {
        appInForeground = true
    }

    fun onLifecycleStop() {
        appInForeground = false
        pauseForBackground()
        // Camera unbind kills the light; keep the UI in step with it.
        torchOn = false
    }

    fun closeFrame(proxy: ImageProxy) {
        cameraXManager.markFrameConsumed()
        cameraXManager.markFrameClosed()
        proxy.close()
    }

    val directionCues = DirectionCues(context, ttsService)

    var appInForeground by mutableStateOf(initiallyInForeground)

    var hasCameraPermission by mutableStateOf(false)
    var hasAudioPermission by mutableStateOf(false)
    var permissionsResolved by mutableStateOf(false)
    // Survives the recreate a language choice causes, so the start-up request is
    // made once: after that, only Accept asks again.
    var startupPermissionsAsked by startupPermissionsAskedState

    // Runtime permissions, asked again whenever something needs one. Android shows
    // its own dialog; after two refusals it answers "no" by itself, so a refusal is
    // always followed by what the permission is for and where to allow it. Nothing
    // is hidden without a permission, and nothing tries to work around one.
    //   Microphone: Ask (Image chat, Text chat, Find objects).
    //   Camera: choosing a feature, Capture, Ask in a chat, Preview.
    //   Notifications: starting a model download (the progress notification only).
    //   Vibration needs no asking: Android grants it at install.
    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    var permissionAsked by mutableStateOf<String?>(null)

    /** True when [permission] is granted; otherwise asks for it (once at a time) and returns false. */
    fun ensurePermission(permission: String): Boolean {
        if (granted(permission)) return true
        if (permissionAsked == null) {
            permissionAsked = permission
            requestPermission(permission)
        }
        return false
    }
    var termsAccepted by mutableStateOf(termsStore.hasAcceptedTerms())
    var activeMode by mutableStateOf<String?>(null)
    var showSettings by mutableStateOf(false)
    // Torch is runtime-only: off on every launch, and forced off whenever the app
    // is backgrounded (pauseForBackground unbinds the camera, which clears it).
    var torchOn by mutableStateOf(false)
    // On-device Whisper ships as base.en — English only. In any other language the
    // app stays on the system recogniser: no download prompt, no Settings row.
    var showHelp by mutableStateOf(false)
    var helpContent by mutableStateOf<HelpContent?>(null)
    /** Text chat's text in view, shown over the camera while Preview is on. */
    var liveText by mutableStateOf<LiveText?>(null)
    val lastSpokenLine get() = ttsService.lastSpoken.value
    val liveTextTracker = LiveTextTracker()
    /** What the live text reads out, box by box, each once. */
    val liveReadQueue = LiveReadQueue()
    /** True while the app's voice is reading live text. */
    var liveTextReading by mutableStateOf(false)
    /** Where the live text's reading carries on when the speech engine reports back. */
    val liveTextScope get() = scope
    /** The live text box the user pinned, held where it was; the rest stays live. */
    var pinnedLiveText by mutableStateOf<LiveText?>(null)
    var cameraPreviewOn by mutableStateOf(false)

    // First launch: the permission dialogs, then Terms whatever the answers (a
    // refusal once skipped the terms), then straight on to the app and its model
    // setup offer. Features ask again when they need a permission (see
    // ensurePermission).
    val showTerms get() = permissionsResolved && !termsAccepted
    val onboardingWait get() = !termsAccepted && !showTerms && !permissionsResolved
    val onboardingBlocking get() = showTerms || onboardingWait

    fun acceptTerms() {
        termsStore.markTermsAccepted()
        termsAccepted = true
        (context as? Activity)?.title = context.getString(R.string.app_name)
    }

    // Accept on Terms with a permission still missing: Android's dialog once
    // more, then on to the tutorial whatever the answer. A second chance at a
    // natural moment; never a gate (after two refusals Android answers "no"
    // itself, and a gate there would trap the user on this screen).
    var termsPermissionRetried by termsPermissionRetriedState
    var acceptAfterPermissions by mutableStateOf(false)

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

    var findObjectTarget by mutableStateOf("")
    var showObjectPicker by mutableStateOf(false)

    var textScanned by mutableStateOf(false)
    var imageScanned by mutableStateOf(false)
    var triggerScan by mutableStateOf(false)

    // Mic-first "ask straight away": pressing the mic in Image/Text chat with no
    // capture yet records the (optional) question, then on release snaps a frame
    // and sends frame + question together. Pairing is order-independent: the
    // pipeline stores the frame, the transcription resolves the question, and a
    // LaunchedEffect fires once both are ready.
    var pendingCaptureBitmap by mutableStateOf<Bitmap?>(null)
    var pendingQuestion by mutableStateOf<String?>(null) // null = unresolved, "" = describe
    // A question on its way to the AI, shown in the chat until its answer lands.
    var chatPendingQuestion by mutableStateOf<String?>(null)
    // "Let me take a picture": said, and kept in the chat, whenever the app takes
    // the picture itself. One per session, since only a session's first picture
    // is ever automatic.
    var autoCaptureNote by mutableStateOf<String?>(null)
    // Mute on the features without input. Lasts until the feature is left.
    var ttsMuted by mutableStateOf(false)
    val startFeaturesMuted get() = settingsManager.startFeaturesMuted.value
    // Text chat's aim guidance: started by Capture, over once the picture is taken.
    var textGuideActive by mutableStateOf(false)
    // Between Capture (or an early question) and the guidance actually running:
    // "Looking for text" is being said. Counts as guiding for a second press.
    var textGuideStarting by mutableStateOf(false)
    // Bumped whenever a pending start must not go ahead after all.
    var textGuideToken by mutableStateOf(0)
    /** What is on screen in the feature being used; see [ChatSession]. */
    val chat = ChatSession()

    /**
     * Sight Buddy's own words: said, and written into the conversation where
     * the user can read them again. They stay until the feature is left, and a
     * saved chat leaves them out.
     */
    fun systemMessage(text: String, flush: Boolean = true, force: Boolean = false) {
        if (text.isBlank()) return
        chat.system(text)
        ttsService.speak(text, flush = flush, force = force)
    }
    var captureForQuestion by mutableStateOf(false)       // signal pipeline to grab+store a frame
    var micFirstAttempt by mutableStateOf(0)               // guards the capture watchdog
    var micFirstActive by mutableStateOf(false)
    var micFirstMode by mutableStateOf<String?>(null)
    // Independent scope for the mic-first request so consuming the trigger state
    // (which recomposes the pairing effect) cannot cancel the in-flight call.
    val micFirstScope get() = scope
    // Hold-mode: a press rejected because auto-capture is off must not act on release.
    var micCaptureBlocked by mutableStateOf(false)
    // Tap-mode debounce: ignore a second Ask tap fired within this window.
    var lastMicTapMs by mutableStateOf(0L)

    // Whisper model download — user-consented only, never automatic (154 MB).
    val sttModelDownloadState get() = sttModelManager.downloadState.value
    val sttModelsReady get() = sttModelDownloadState is SttModelManager.DownloadState.Ready

    // One place decides how the app language and the download state combine. See
    // SpeechAvailability for why these two questions belong together.
    // AI answers come from OpenAI (a key, and Use API on) or the on-device model.
    // Cloud transcription needs the API itself.
    val apiKeyPresent get() = apiKeyStore.keyPresent.value
    val useApi get() = settingsManager.useApi.value
    // Derived states, not plain vals: handlers created before Gemma finished
    // loading must read the current value, not the one from their composition.
    val apiAllowed get() = apiKeyPresent && useApi
    // Installed is enough: a question asked while it loads waits for it.
    val gemmaInstalled get() = localGemma.installedState.value
    val sttChoiceKey get() = settingsManager.sttChoice.value
    val gemmaDownloadPercent get() = localGemma.downloadPercent.value
    val useLocalChat get() = settingsManager.useLocalChat.value
    // Gemma answers in any language (Finnish prompts as for OpenAI); it is only
    // recommended, and used for speech, in English.
    val english get() = !languageStore.isFinnish()
    val localAiReady get() = gemmaInstalled && useLocalChat
    // Gemma holds about 2 GB: load it only while something will use it.
    // The phone's specs decide the recommended setup. Re-read when models come
    // or go, since that changes the free space.
    // Read again only when models come or go, or Settings opens: it asks the disk.
    private var freeSpaceKey: Triple<Boolean, Boolean, Boolean>? = null
    private var freeSpaceRead = 0L
    val freeSpace: Long
        get() {
            val key = Triple(sttModelsReady, gemmaInstalled, showSettings)
            if (key != freeSpaceKey) {
                freeSpaceKey = key
                freeSpaceRead = ModelSetup.freeBytes(context)
            }
            return freeSpaceRead
        }
    // Follows the free space, as the remembered value it replaces did: reading
    // the RAM is a call into the system.
    private var deviceTierFor: Long? = null
    private var deviceTierRead = ModelSetup.Tier.NONE
    val deviceTier: ModelSetup.Tier
        get() {
            val space = freeSpace
            if (space != deviceTierFor) {
                deviceTierFor = space
                deviceTierRead = ModelSetup.tier(ModelSetup.totalRamBytes(context), space, english = english)
            }
            return deviceTierRead
        }
    val setupModelsRes get() =
        if (deviceTier == ModelSetup.Tier.FULL) R.string.setup_models_full else R.string.setup_models_whisper
    val setupOffered get() = deviceTier != ModelSetup.Tier.NONE && !sttModelsReady && !gemmaInstalled &&
        gemmaDownloadPercent == null && sttModelDownloadState !is SttModelManager.DownloadState.Downloading
    val effectiveStt get() = SttChoice.effective(
        chosen = SttChoice.fromKey(sttChoiceKey),
        whisperReady = sttModelsReady,
        gemmaInstalled = gemmaInstalled,
        english = !languageStore.isFinnish(),
    )
    suspend fun syncGemmaLoaded() {
        val forSpeech = effectiveStt == SttChoice.GEMMA
        if (gemmaInstalled && (useLocalChat || forSpeech)) {
            localGemma.loadIfPresent()
        } else {
            withContext(Dispatchers.Default) { localGemma.release() }
            // Whisper stands in for speech when Gemma is gone.
            if (!gemmaInstalled) voiceInputService.loadEngineIfReady()
        }
    }
    val llmChatEnabled get() = apiAllowed || localAiReady
    // The active feature's own settings, as switches in the More row. The same
    // settings as in Settings, so the two always agree.
    val autoCaptureOn get() = settingsManager.autoCaptureEnabled.value
    val textGuidanceOn get() = settingsManager.textScanGuidance.value
    val textPreviewOn get() = settingsManager.textPreviewEnabled.value
    val liveTextOn get() = settingsManager.liveText.value
    val pitchOn get() = settingsManager.pitchFeedback.value
    val savedKeyPreview get() = apiKeyStore.keyPreview.value
    val cloudSttEnabled get() = settingsManager.cloudStt.value
    val speech get() = run {
        SpeechAvailability(
            modelServesLanguage = !languageStore.isFinnish(),
            modelDownloaded = sttModelsReady || gemmaInstalled,
            cloudEnabled = cloudSttEnabled && apiAllowed,
            localEnabled = effectiveStt != SttChoice.ANDROID,
        )
    }
    val whisperSupported get() = speech.downloadRelevant

    val sttDownloadPercent get() =
        (sttModelDownloadState as? SttModelManager.DownloadState.Downloading)?.percent
    var showSetupDialog by mutableStateOf(false)
    var showMemories by mutableStateOf(false)
    var moreOpen by mutableStateOf(false)
    var showSttModelsDialog by mutableStateOf(false)
    var showGemmaDialog by mutableStateOf(false)
    var showGemmaDownloadDialog by mutableStateOf(false)
    var showLanguageDialog by mutableStateOf(false)
    var sttPromptShownThisLaunch by mutableStateOf(false)

    fun sizeLabel(bytes: Long) = Formatter.formatShortFileSize(context, bytes)

    /**
     * A confirmation from Settings or a model download: said wherever the user
     * is, and never shown over the camera. These outlive the screen they were
     * asked from — a download finishes long after Settings is closed — and the
     * line over the camera belongs to the feature being used.
     */
    fun confirmation(text: String, flush: Boolean = true) {
        ttsService.speak(text, flush = flush, force = true, caption = false)
    }

    /** Speaks why not, and returns false, when [bytes] will not fit. */
    fun checkSpace(bytes: Long): Boolean {
        if (ModelSetup.hasSpaceFor(context, bytes)) return true
        confirmation(context.getString(R.string.spoken_no_space), flush = true)
        return false
    }

    // Downloads run under ModelDownloadService, so they go on with the phone locked.
    fun askForDownloadNotification() {
        if (Build.VERSION.SDK_INT >= 33) ensurePermission(Manifest.permission.POST_NOTIFICATIONS)
    }

    suspend fun downloadWhisperTracked(): Boolean = ModelDownloadService.track(
        context,
        title = context.getString(R.string.settings_stt_models),
        progress = sttModelManager.downloadState.map { (it as? SttModelManager.DownloadState.Downloading)?.percent },
    ) { sttModelManager.downloadIfNeeded() }

    suspend fun downloadGemmaTracked(): Boolean = ModelDownloadService.track(
        context,
        title = context.getString(R.string.settings_gemma_model),
        progress = localGemma.downloadPercent,
    ) { localGemma.download() }

    /** Gemma on its own, from Settings. */
    fun startGemmaDownload() {
        if (!checkSpace(ModelSetup.GEMMA_BYTES)) return
        askForDownloadNotification()
        scope.launch {
            confirmation(context.getString(R.string.spoken_gemma_downloading), flush = true)
            val ok = AppWork.finish { downloadGemmaTracked() }
            confirmation(
                context.getString(
                    if (ok) R.string.spoken_gemma_installed else R.string.spoken_gemma_download_failed
                ),
                flush = false,
            )
        }
    }

    fun deleteGemma() {
        scope.launch {
            val deleted = AppWork.finish { withContext(Dispatchers.IO) { localGemma.deleteModel() } }
            confirmation(
                context.getString(
                    if (deleted) R.string.spoken_gemma_deleted else R.string.spoken_models_delete_failed
                ),
                flush = true,
            )
        }
    }

    /** The recommended setup for [tier]: Whisper, then Gemma for the full one. */
    fun startModelSetup(tier: ModelSetup.Tier) {
        showSetupDialog = false
        sttPromptShownThisLaunch = true
        if (!checkSpace(ModelSetup.packageBytes(tier))) return
        askForDownloadNotification()
        scope.launch {
            confirmation(
                context.getString(R.string.spoken_setup_downloading, sizeLabel(ModelSetup.packageBytes(tier))),
                flush = true,
            )
            val (whisperOk, gemmaOk) = AppWork.finish {
                val whisper = downloadWhisperTracked()
                // Usable while Gemma downloads; skipped if the screen has gone.
                if (whisper) scope.launch { voiceInputService.loadEngineIfReady() }
                whisper to (tier != ModelSetup.Tier.FULL || downloadGemmaTracked())
            }
            confirmation(
                context.getString(
                    if (whisperOk && gemmaOk) R.string.spoken_setup_done else R.string.spoken_setup_failed
                ),
                flush = false,
            )
        }
    }

    fun startSttDownload() {
        // A download decision has been made — don't auto-prompt again this launch.
        sttPromptShownThisLaunch = true
        if (!checkSpace(ModelSetup.WHISPER_BYTES)) return
        askForDownloadNotification()
        scope.launch {
            // force: the user asked for this download, often from inside Settings.
            confirmation(
                context.getString(R.string.spoken_models_downloading),
                flush = false,
            )
            val ok = AppWork.finish { downloadWhisperTracked() }
            if (ok) {
                voiceInputService.loadEngineIfReady()
                confirmation(
                    context.getString(R.string.spoken_models_installed),
                    flush = false,
                )
            } else {
                confirmation(
                    "Voice model download failed. You can retry from settings.",
                    flush = false,
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
    var captureInFlight by mutableStateOf(false)

    /** Set when a question arrives mid-capture, so the description is not spoken. */
    var questionSupersededCapture by mutableStateOf(false)

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
        chatPendingQuestion = null
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

    /**
     * Says, and notes in the chat, that the app is about to take the picture
     * itself, so an automatic capture is never a surprise.
     */
    /**
     * The guidance's picture: for the question waiting on it, if one is, and
     * otherwise read out as Capture always has.
     */
    fun captureGuidedText() {
        if (micFirstActive && micFirstMode == ModeNames.TEXT_CHAT) {
            captureForQuestion = true
            soundFXService.playAlongside(SFX.CAMERA_CLICK)
        } else {
            triggerScan = true
        }
    }

    /**
     * Says [note] and keeps it in the chat, then calls [onSaid] once it has
     * finished, or at once if it could not be spoken. Never waits longer than
     * [AUTO_CAPTURE_NOTE_MAX_MS].
     */
    fun announceAutoCapture(
        note: String = context.getString(R.string.spoken_taking_picture),
        onSaid: () -> Unit,
    ) {
        autoCaptureNote = note
        chat.system(note)
        // The recording is over by now; nothing is left to protect from speech.
        ttsService.releaseMicrophoneFloor()
        val said = CompletableDeferred<Unit>()
        val queued = ttsService.speak(note, flush = true, onDone = { said.complete(Unit) })
        micFirstScope.launch {
            if (queued) withTimeoutOrNull(AUTO_CAPTURE_NOTE_MAX_MS) { said.await() }
            onSaid()
        }
    }

    /**
     * Starts Text chat's aim guidance: "Looking for text", kept in the chat like
     * Image chat's "Let me take a picture", and only once it has been said (and
     * a moment after) do the guidance cues begin, so the two never overlap.
     */
    fun startTextGuidance() {
        textGuideToken += 1
        val token = textGuideToken
        textGuideStarting = true
        announceAutoCapture(context.getString(R.string.spoken_looking_for_text)) {
            micFirstScope.launch {
                delay(AUTO_CAPTURE_PAUSE_MS)
                if (token != textGuideToken || !textGuideStarting) return@launch
                textGuideStarting = false
                textGuideActive = true
            }
        }
    }

    /** Called off a pending or running guidance, so a late start is dropped. */
    fun stopTextGuidance() {
        textGuideToken += 1
        textGuideStarting = false
        textGuideActive = false
    }

    /** Snap the frame for an armed mic-first question and await the pairing. */
    fun beginMicFirstCapture(mode: String) {
        // Deliberately no cancelMicFirst here: a question that has already been
        // transcribed must survive until the frame arrives to pair with it.
        micFirstActive = true
        micFirstMode = mode
        voiceInputService.stopListening()

        // The token guards against an older attempt firing on a newer one after
        // the user retries.
        micFirstAttempt += 1
        val attempt = micFirstAttempt

        // Text chat asked before any text was scanned: rather than a blind
        // picture, guide the camera onto the text, and the guidance takes the
        // picture for the question once the text is centred.
        if (mode == ModeNames.TEXT_CHAT && settingsManager.textScanGuidance.value) {
            startTextGuidance()
            return
        }
        fun stillThisAttempt() = attempt == micFirstAttempt && micFirstActive

        // "Let me take a picture", a second's pause to hold still, then the shutter.
        announceAutoCapture {
            micFirstScope.launch {
                delay(AUTO_CAPTURE_PAUSE_MS)
                if (!stillThisAttempt()) return@launch
                captureForQuestion = true
                soundFXService.playAlongside(SFX.CAMERA_CLICK)

                // Watchdog, from the moment the frame is asked for.
                delay(MIC_FIRST_CAPTURE_TIMEOUT_MS)
                if (!stillThisAttempt() || pendingCaptureBitmap != null) return@launch
                Log.w("SightBuddyApp", "Mic-first capture timed out: no frame from the camera")
                val unanswered = chatPendingQuestion
                cancelMicFirst()
                autoCaptureNote = null
                if (!unanswered.isNullOrBlank()) chat.user(unanswered)
                systemMessage(context.getString(R.string.spoken_capture_failed))
            }
        }
    }

    /** Guard for async responses: only speak if the user is still in the feature. */
    fun featureStillActive(mode: String): Boolean =
        activeMode == mode && !showSettings && !showHelp && appInForeground

    var lastSessionActivityAtMs by mutableStateOf(System.currentTimeMillis())

    val textPreviewEnabled get() = settingsManager.textPreviewEnabled.value
    // BYOK: AI features are enabled exactly when the user has saved their own key.

    val cloudSttAsked get() = settingsManager.cloudSttAsked.value
    var showCloudSttDialog by mutableStateOf(false)

    /**
     * Finishing the download turns the paid transcription off.
     *
     * Someone who enabled it before the model arrived should not keep paying for
     * what they now have for free. Only the moment of completion does this, not
     * every launch, so a user who deliberately chooses the API afterwards keeps
     * that choice.
     */
    var modelWasReady by mutableStateOf(sttModelsReady)
    suspend fun onSttModelsReadyChanged() {
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
    var showChoiceRespectedDialog by mutableStateOf(false)

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
    val cloudSttOffered get() = true

    // The service only uses it when the user has said yes and a key exists.
    suspend fun syncCloudTranscriber() {
        voiceInputService.cloudTranscriber =
            if (cloudSttEnabled && apiAllowed) cloudTranscriber else null
    }

    /**
     * Offered unprompted only where the alternative is genuinely poor: a language
     * the on-device model cannot serve at all. English users are left to find it
     * in Settings, because the platform recogniser handles English well enough.
     * Asked once, whatever the answer.
     */
    suspend fun offerCloudStt() {
        if (showTerms || onboardingBlocking) return
        if (cloudSttAsked || cloudSttEnabled || !apiAllowed) return
        // Anyone not already running the on-device model: every non-English user,
        // and English users who have not downloaded it. Someone already on Whisper
        // has the good path for free and is not asked to pay for another.
        if (speech.engine == SpeechEngine.WHISPER) return
        showCloudSttDialog = true
    }
    val isOnline get() = networkStatusManager.isOnline.value
    val scanLightEnabled get() = settingsManager.scanLightEnabled.value
    val scanColourEnabled get() = settingsManager.scanColourEnabled.value
    val imageChatEnabled get() = settingsManager.imageChatEnabled.value
    val textChatEnabled get() = settingsManager.textChatEnabled.value
    val discoverEnabled get() = settingsManager.discoverEnabled.value
    val findEnabled get() = settingsManager.findEnabled.value
    val highContrast get() = settingsManager.highContrastEnabled.value
    val whiteMode get() = settingsManager.whiteModeEnabled.value
    val loopCarousel get() = settingsManager.loopCarousel.value
    val pitchFeedback get() = settingsManager.pitchFeedback.value
    val hapticFeedback get() = settingsManager.hapticFeedback.value
    val cameraPreviewSetting get() = settingsManager.cameraPreview.value
    val hiddenCocoObjects get() = settingsManager.hiddenCocoObjects.value
    val visibleCocoObjects get() = run {
        settingsManager.visibleCocoObjects()
    }
    val effectiveTextPreviewEnabled get() = textPreviewEnabled || !llmChatEnabled
    val textScriptPlayer = TextScriptPlayer(ttsService)
    val playbackState get() = textScriptPlayer.playbackState.value
    var textPlaybackReady by mutableStateOf(false)
    // What the reader was given, for the on-screen text. Session only: cleared
    // with the playback, never stored.
    var playbackTranscript by mutableStateOf("")
    val imageChatHistory get() = imageChatViewModel.chatHistory.value
    val documentChatHistory get() = documentChatViewModel.chatHistory.value
    val imageChatProcessing get() = imageChatViewModel.isProcessing.value
    val documentChatProcessing get() = documentChatViewModel.isProcessing.value
    /** What is on screen: every voice, oldest first. */
    val transcriptEntries get() = chat.messages
    var offlineNoticeShown by mutableStateOf(false)

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

    /** Help opens only when asked for, from the Help button: never on its own. */
    fun openHelp(content: HelpContent) {
        cutAllTtsFeedback()
        soundFXService.stop()
        soundFXService.stopHint()
        helpContent = content
        showHelp = true
    }

    fun closeHelp() {
        showHelp = false
        helpContent = null
        soundFXService.stopHint()
    }

    fun openFeatureHelp(featureName: String) {
        val content = FeatureHelpLibrary.featureHelp(context, featureName) ?: return
        openHelp(content)
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
        if (hapticFeedback) {
            hapticManager.tap()
        } else {
            soundFXService.play(
                if (voiceInputService.usesSystemEarcons()) SFX.RECORDING_PERK else SFX.LISTENING
            )
        }
        voiceInputService.startListening()
    }

    // When the app last asked the recogniser to stop. A recording the user ended
    // must not arm the restart guard, or a deliberate follow-up asked straight
    // afterwards is swallowed and nothing happens at all.
    var userStopAtMs by mutableStateOf(0L)

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
        if (hapticFeedback) {
            hapticManager.tap()
        } else {
            soundFXService.play(
                if (voiceInputService.usesSystemEarcons()) SFX.RECORDING_PERK_STOP else SFX.STOP_LISTENING
            )
        }
    }

    fun pauseForBackground() {
        soundFXService.stopLoop()
        stopTextGuidance()
        pitchToneService.stop()
        hapticManager.stopPulse()
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
        playbackTranscript = text.trim()
        textScriptPlayer.loadScript(text)
        textPlaybackReady = textScriptPlayer.hasScript
        textScriptPlayer.playFromStart()
    }

    fun clearTextPlayback() {
        playbackTranscript = ""
        textScriptPlayer.clear()
        textPlaybackReady = false
    }

    fun speakOutsidePlayer(message: String) {
        textScriptPlayer.interrupt()
        ttsService.speak(message, flush = true)
    }

    val ttsInitialized get() = ttsService.isInitialized.value
    val isListening get() = voiceInputService.isListening.value
    val holdToSpeakSetting get() = settingsManager.holdToSpeak.value
    val holdToSpeak get() = holdToSpeakSetting && HOLD_TO_SPEAK_ENABLED
    val autoCaptureSetting get() = settingsManager.autoCaptureEnabled.value
    // Works on every path now. It used to be restricted to recognisers the app
    // controls, because the frame was snapped by the gesture that ended the
    // recording and the platform recogniser ends recordings by itself. The frame
    // follows the recording ending instead, so there is no gesture to miss.
    val autoCaptureEnabled get() = autoCaptureSetting
    val ttsRate get() = settingsManager.ttsSpeechRate.value

    // Apply speech rate on start and whenever the setting changes. Announcements
    // are made at the interaction sites (Settings row, playback hold buttons).
    suspend fun applySpeechRate() {
        if (ttsInitialized) ttsService.setSpeechRate(ttsRate)
    }
    val now get() = { System.currentTimeMillis() }


    // Any pop-up over the features. While one is open the features are silent and
    // the camera stops, so nothing (live text, detections) competes with it.
    val popupOpen get() = showHelp || showMemories || showSetupDialog || showSttModelsDialog || showGemmaDialog ||
        showGemmaDownloadDialog || showLanguageDialog || showCloudSttDialog ||
        showChoiceRespectedDialog || showObjectPicker

    suspend fun syncAppSpeech() {
        // Settings is included: feature output must never leak over it. Settings'
        // own confirmations use speak(force = true) and still come through.
        ttsService.setSuppressAppSpeech(
            !appInForeground || isListening || showTerms || popupOpen ||
                showSettings || onboardingBlocking,
        )
        // Do not stop STT when isListening — mic press already called cutAllAudio(); stopping here
        // would cancel SpeechRecognizer immediately after startListening().
        if (!appInForeground || showTerms || popupOpen || showSettings) {
            cutAllTtsFeedback()
        }
    }

    suspend fun onPopupChanged() {
        if (popupOpen) {
            cutAllTtsFeedback()
        }
    }

    // The chat is working on an answer: a photo or a question on its way.
    val chatWorking get() = (activeMode == ModeNames.IMAGE_CHAT || activeMode == ModeNames.TEXT_CHAT) &&
        (captureInFlight ||
            (chatPendingQuestion != null && chatPendingQuestion != SPEAKING_PLACEHOLDER) ||
            if (activeMode == ModeNames.IMAGE_CHAT) imageChatProcessing else documentChatProcessing)

    // A soft tick while it works, as Seeing AI and Envision do, so a wait of
    // several seconds is not silence. Not for a quick answer: it starts after
    // 0.7 s. Never over a pop-up or Settings, and not while recording.
    suspend fun workingTick() {
        if (!chatWorking || popupOpen || showSettings || isListening || !appInForeground) {
            soundFXService.stopLoop()
            return
        }
        delay(WORKING_TICK_DELAY_MS)
        try {
            // The tick, through the cue pool, which is heard on every phone
            // tried. The loop beside it (SoundFXService.Loop) is off: turn it on
            // there and take this tick out, rather than running both.
            soundFXService.startLoop(SoundFXService.Loop.LOADING)
            while (true) {
                soundFXService.playAlongside(SoundFXService.SFX.WORKING)
                delay(WORKING_TICK_INTERVAL_MS)
            }
        } finally {
            // The answer arrived, or the user moved on: either changes this
            // effect's keys, which cancels it here.
            soundFXService.stopLoop()
        }
    }

    suspend fun onSettingsOrPickerChanged() {
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
        hapticManager.stopPulse()
    }

    fun clearCapturedFrame() {
        // A feature's spoken line goes with it.
        ttsService.clearLastSpoken()
        ttsMuted = false
        ttsService.muted = false
        stopTextGuidance()
        chat.clear()
        autoCaptureNote = null
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
    /**
     * Saves the active feature's chat to Memories: at once under a plain name,
     * then under the name the model gives it. Gemma names it when it is here;
     * otherwise the plain name (feature, date and time) stays.
     */
    fun saveCurrentChat() {
        val mode = activeMode ?: return
        // The app's own words are not the conversation: "Looking for text" and
        // "No text was detected" stay on screen but are not worth keeping.
        val entries = chat.forSaving()
        if (entries.isEmpty()) {
            systemMessage(context.getString(R.string.memories_nothing), force = true)
            return
        }
        val stamp = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date())
        val taken = memoryStore.names()
        val plain = ModeNames.display(context, mode) + ", " + stamp
        val saved = memoryStore.save(
            name = if (MemoryNames.clashes(plain, taken)) {
                MemoryNames.numbered(plain, taken)
            } else plain,
            feature = mode,
            entries = entries.map { MemoryEntry(it.kind.name, it.text) },
        )
        val nameWithModel = localAiReady
        scope.launch {
            // Named and saved even if the screen goes; only the saying is the screen's.
            val named = AppWork.finish {
                val name = if (nameWithModel) {
                    withContext(Dispatchers.IO) { nameWithGemma(saved.entries, taken) }
                } else null
                if (name != null) memoryStore.rename(saved.id, name)
                name
            }
            systemMessage(
                context.getString(R.string.memories_saved, named ?: saved.name),
                force = true,
            )
        }
    }

    fun endSessionForSettings() {
        cutAllAudio()
        pitchToneService.stop()
        hapticManager.stopPulse()
        cancelMicFirst()
        triggerScan = false
        captureForQuestion = false
        imageChatViewModel.cancelActiveRequest()
        documentChatViewModel.cancelActiveRequest()
        clearCapturedFrame()
    }

    // A feature with Mute starts muted when the setting says so: on arriving at
    // it, and on coming back from Settings, which ends the session and unmutes.
    suspend fun applyStartMuted() {
        if (showSettings) return
        val mode = activeMode ?: return
        val muted = startFeaturesMuted && mode !in ModeNames.TAKES_INPUT
        ttsMuted = muted
        ttsService.muted = muted
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
            systemMessage(context.getString(R.string.spoken_session_expired))
        }
        lastSessionActivityAtMs = now()
        Log.i("SightBuddyApp", "Session caches cleared due to idle/size policy")
    }

    // Camera off in settings, welcome, or when app is not visible (background / lock screen).
    // High contrast must NOT disable the camera: analyzers need frames even though the
    // preview is visually covered by HomeScreen's full-screen mask.
    val cameraDisabled get() =
        !appInForeground || showSettings || showTerms ||
            popupOpen || onboardingBlocking

    val pages get() = run {
        buildList {
            if (llmChatEnabled && imageChatEnabled) add("Image chat")
            if (textChatEnabled) add("Text chat")
            if (discoverEnabled) add("Discover objects")
            if (findEnabled) add("Find objects")
            if (scanLightEnabled) add("Scan Light")
            if (scanColourEnabled) add("Scan Colour")
        }
    }


    suspend fun requestStartupPermissions() {
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
        if (cameraGranted && audioGranted || startupPermissionsAsked) {
            permissionsResolved = true
        } else {
            startupPermissionsAsked = true
            requestPermissions(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
            )
        }
    }

    // Permissions changed in the phone's settings take effect on return.
    suspend fun refreshPermissions() {
        if (!appInForeground) return
        hasCameraPermission = granted(Manifest.permission.CAMERA)
        hasAudioPermission = granted(Manifest.permission.RECORD_AUDIO)
    }

    // Wait for TTS: cold launch speaks nothing until initialized, so we must not
    // mark the notice shown until we can actually deliver the announcement.
    suspend fun noticeOffline() {
        if (!appInForeground || showTerms || onboardingBlocking) {
            return
        }
        // Only when answers depend on the network: the on-device model works offline.
        if (!offlineNoticeShown && !isOnline && apiAllowed && !localAiReady && ttsInitialized) {
            offlineNoticeShown = true
            systemMessage(context.getString(R.string.spoken_offline))
        }
    }

    // Settings is a hard stop, not a pause; see endSessionForSettings.
    suspend fun onSettingsChanged() {
        if (showSettings) endSessionForSettings()
    }

    // Unbind camera when disabled (background / settings / overlays)
    suspend fun onCameraDisabledChanged() {
        if (cameraDisabled) {
            cameraXManager.stopCamera()
        } else if (hasCameraPermission && appInForeground) {
            // Whatever unbound it (a pop-up, Settings) is over: show the scene again.
            cameraXManager.restartPreview()
        }
    }

    // Offer the voice-model download once onboarding is out of the way. Shown on
    // every launch while undecided or "Later"; never again after "Never".
    suspend fun offerModelSetup() {
        if (onboardingBlocking || showTerms || !appInForeground) return
        // Offered once per launch until a model is installed; tier 1 phones (and
        // every non-English user) are never offered anything.
        if (!setupOffered || sttPromptShownThisLaunch) return
        // Never interrupt an in-progress download with the prompt.
        if (sttModelDownloadState is SttModelManager.DownloadState.Downloading) return
        if (settingsManager.sttDownloadChoice.value == SettingsManager.STT_CHOICE_NEVER) {
            return
        }
        sttPromptShownThisLaunch = true
        showSetupDialog = true
    }

    // Returning to the app must be silent, with one exception: Find objects with a
    // cached target, where re-announcing tells the user tracking is still live.
    // Anything queued by the previous session is cut rather than replayed.
    // When the last recording ended, so a press arriving right after the platform
    // cut it off is not mistaken for a request to start another.
    var recordingEndedAtMs by mutableStateOf(0L)
    var wasListening by mutableStateOf(false)
    // Set when a press was swallowed, so its release is swallowed with it.
    var micPressSwallowed by mutableStateOf(false)
    suspend fun onListeningChanged() {
        if (!isListening) soundFXService.stopLoop()
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
            // Should no words and no outcome ever arrive, the "…" does not stay.
            if (chatPendingQuestion == SPEAKING_PLACEHOLDER) {
                micFirstScope.launch {
                    delay(SPEAKING_PLACEHOLDER_MAX_MS)
                    if (chatPendingQuestion == SPEAKING_PLACEHOLDER) chatPendingQuestion = null
                }
            }
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

    var appHasStartedOnce by mutableStateOf(false)
    suspend fun onReturnToApp() {
        if (!appInForeground) return
        if (!appHasStartedOnce) {
            appHasStartedOnce = true
            return // first launch keeps the normal onboarding flow
        }
        cutAllAudio()
        // Coming back is silent, but for one thing: Find objects keeps working
        // across a pause, so it says what it is still hunting for. Anything left
        // half-finished stays silent until the user asks again.
        val mode = activeMode
        if (ttsInitialized && !showSettings && !showHelp && !onboardingBlocking && mode != null) {
            if (mode == ModeNames.FIND_OBJECTS && findObjectTarget.isNotBlank()) {
                ttsService.speak(
                    context.getString(
                        R.string.spoken_looking_for,
                        CocoFinnish.displayName(findObjectTarget),
                    ),
                    flush = false,
                )
            }
        }
    }

    // Voice input outcome feedback: auto-stop sounds and "nothing heard" prompts.
    suspend fun collectVoiceEvents() {
        voiceInputService.events.collect { event ->
            ttsService.releaseMicrophoneFloor()
            if (!appInForeground) return@collect
            // Mic-first with no speech = "just describe the scene": pair an empty
            // question with the snapped frame instead of prompting to repeat.
            // Nothing was said: the "…" has no words coming.
            if (chatPendingQuestion == SPEAKING_PLACEHOLDER) chatPendingQuestion = null
            if (micFirstActive) {
                pendingQuestion = ""
                return@collect
            }
            when (event) {
                // force: the answer to the user's own recording. The platform
                // recogniser reports "nothing heard" before it reports that it has
                // stopped, so app speech is still held back for the open mic and
                // the prompt was shown on screen without ever being said.
                is VoiceInputService.SttEvent.AutoStopped -> {
                    if (event.noSpeech) {
                        systemMessage(context.getString(R.string.spoken_nothing_heard), force = true)
                    }
                }
                VoiceInputService.SttEvent.Empty -> {
                    systemMessage(context.getString(R.string.spoken_repeat), force = true)
                }
            }
        }
    }

    // Mic-first pairing: once the snapped frame and the (optional) question are
    // both ready, send them together and speak the answer.
    suspend fun pairMicFirst() {
        val bmp = pendingCaptureBitmap
        val q = pendingQuestion
        val mode = micFirstMode
        if (!micFirstActive || bmp == null || q == null || mode == null) return
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
                        chat.user(q)
                        chat.model(response)
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
                        chat.user(q)
                        chat.model(response)
                        if (featureStillActive("Text chat")) speakOutsidePlayer(response)
                    }
                }
            } finally {
                bmp.recycle()
                // A typed question shown while its picture was taken: answered now.
                if (chatPendingQuestion == q) chatPendingQuestion = null
            }
        }
    }

    /**
     * Text chat aims the camera for the user before there is any text: nothing
     * scanned yet, automatic capture on, and nothing else going on (a recording,
     * a question, the reader speaking).
     */
    fun textAimGuidanceWanted(): Boolean =
        textGuideActive &&
            featureStillActive(ModeNames.TEXT_CHAT) &&
            // A question asked before scanning waits on this guidance for its picture.
            (!micFirstActive || micFirstMode == ModeNames.TEXT_CHAT) &&
            !isListening &&
            !documentChatViewModel.isProcessing.value &&
            textScriptPlayer.currentState != TextScriptPlayer.State.PLAYING

    /**
     * Text chat reads text it sees by itself whenever it is otherwise idle: not
     * guiding a capture, not reading a capture out, and no question under way.
     */
    fun instantTextWanted(): Boolean =
        !textGuideActive && !textGuideStarting &&
            featureStillActive(ModeNames.TEXT_CHAT) &&
            !micFirstActive &&
            !isListening &&
            !captureForQuestion &&
            chatPendingQuestion == null &&
            !documentChatViewModel.isProcessing.value &&
            textScriptPlayer.currentState != TextScriptPlayer.State.PLAYING

    /**
     * Live text over the camera: with Preview and the Live text setting on, and
     * only until the chat has started. Once there is a page, a question or a
     * note, the chat's messages are what is on screen, and live text would get
     * in their way.
     */
    fun liveTextWanted(): Boolean =
        instantTextWanted() && cameraPreviewOn && liveTextOn &&
            !textScanned &&
            documentChatViewModel.chatHistory.value.isEmpty() &&
            chat.isEmpty() &&
            playbackTranscript.isBlank()

    /**
     * Says a box of live text: after anything already speaking ([flush] false),
     * or cutting it off. [then] runs when it has been said, on the main thread;
     * the speech engine reports from its own.
     */
    fun sayLiveText(text: String, flush: Boolean, then: () -> Unit = {}) {
        liveTextReading = ttsService.speak(
            text,
            flush = flush,
            utteranceId = LIVE_TEXT_READING_ID + System.nanoTime(),
            onDone = {
                liveTextScope.launch {
                    liveTextReading = false
                    then()
                }
            },
        )
    }

    /**
     * Says the next box in the live text's queue, if none is being said, and
     * the one after when it is done.
     */
    fun readNextLiveBox() {
        // A reading cut off by other speech never reports done; then the flag is stale.
        if (liveTextReading && ttsService.isSpeaking()) return
        if (pinnedLiveText != null || !liveTextWanted()) return
        val text = liveReadQueue.next() ?: return
        sayLiveText(text, flush = false, then = ::readNextLiveBox)
    }

    /** Stops the reading of the live text, not anything else being said. */
    fun stopLiveTextReading() {
        // The flag alone is not enough: a reading cut off some other way never
        // reports done, and stopping then would cut off whatever is speaking now.
        if (liveTextReading && ttsService.isSpeaking()) ttsService.stop()
        liveTextReading = false
    }

    /** Live text off, and no box pinned: the loop starting, the guidance taking over, or a capture. */
    fun clearLiveText() {
        liveText = null
        pinnedLiveText = null
        liveTextTracker.reset()
        liveReadQueue.reset()
    }

    /**
     * A box was touched or tapped (double-tapped with TalkBack): pinned where it
     * is, the rest staying live, and the reading of the live text paused. Its
     * text is said, unless a screen reader has already said it on the touch.
     */
    fun pinLiveTextBox(block: LiveText.Block) {
        val live = liveText ?: return
        if (pinnedLiveText?.blocks?.firstOrNull()?.text == block.text) return
        stopLiveTextReading()
        liveReadQueue.clearQueue()
        pinnedLiveText = live.copy(blocks = listOf(block))
        if (!isScreenReaderOn(context)) sayLiveText(block.text, flush = true)
    }

    /**
     * The area around the boxes was touched: unpinned, and the reading goes on
     * with what has not been read yet. Its memory is kept: wiping it read the
     * first box again every time, and TalkBack users touch the area often as
     * they move a finger around.
     */
    fun resumeLiveText() {
        pinnedLiveText = null
        liveTextTracker.resume()
    }

    /**
     * Speaks a Find objects reply and puts it in the conversation. It answers
     * the user's request, so it is saved with it; the app's asides there go
     * through [systemMessage] like everywhere else.
     */
    fun findReply(message: String) {
        chat.model(message)
        ttsService.speak(message, flush = true)
    }

    /**
     * What to find, spoken or typed. The command and the replies about it go in
     * the history; the running detection callouts ("laptop") do not.
     */
    suspend fun answerFindCommand(command: String) {
        chat.user(command)
        try {
            val resolution = withTimeout(10_000L) {
                objectCommandResolver.resolve(command, llmEnabled = llmChatEnabled)
            }
            when (resolution) {
                is ObjectCommandResolver.ResolveResult.Activate -> {
                    findObjectTarget = resolution.item
                    findReply(context.getString(R.string.spoken_looking_for, CocoFinnish.displayName(resolution.item)))
                    Log.i(
                        "SightBuddyApp",
                        "Find-objects target activated via ${resolution.source} with confidence ${resolution.confidence}"
                    )
                }
                is ObjectCommandResolver.ResolveResult.Clarify -> {
                    // Say what went wrong and stop there. The user retries by
                    // speaking again, or opens the list themselves.
                    findReply(resolution.message)
                }
                is ObjectCommandResolver.ResolveResult.Unavailable -> {
                    findReply(resolution.message)
                    PrivateLog.i("SightBuddyApp") { "Find-objects unavailable request: $command" }
                }
            }
        } catch (e: Exception) {
            Log.e("SightBuddyApp", "Find-objects resolver error", e)
            findReply(context.getString(R.string.spoken_repeat))
        }
    }

    /**
     * A question about the picture or text in the current chat session, however
     * it arrived: spoken, or typed in the chat window.
     */
    suspend fun answerChatQuestion(command: String) {
        chatPendingQuestion = command
        // The question is written with the first thing said back, so the "…"
        // bubble it replaces is never shown beside it.
        var questionWritten = false
        fun writeQuestion() {
            if (!questionWritten) {
                chat.user(command)
                questionWritten = true
            }
        }
        /** [system] marks the app's own words, which a saved chat leaves out. */
        fun reply(text: String, flush: Boolean = true, system: Boolean = false) {
            writeQuestion()
            if (system) chat.system(text) else chat.model(text)
            ttsService.speak(text, flush = flush)
        }
        fun replyOutsidePlayer(text: String) {
            writeQuestion()
            chat.model(text)
            speakOutsidePlayer(text)
        }
        try {
            when (activeMode) {
                "Image chat" -> {
                    if (!llmChatEnabled) {
                        reply(context.getString(R.string.spoken_llm_off), flush = true, system = true)
                        return
                    }
                    if (imageScanned) {
                        val response = imageChatViewModel.askFollowUp(command)
                        if (featureStillActive("Image chat")) reply(response, flush = true)
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
                            reply(
                                context.getString(R.string.spoken_capture_first),
                                flush = true,
                                system = true,
                            )
                        } else {
                            imageChatViewModel.cancelActiveRequest()
                            val response = imageChatViewModel.askFollowUp(command)
                            if (featureStillActive("Image chat")) {
                                reply(response, flush = true)
                            }
                            touchSessionActivity()
                        }
                    } else {
                        reply(context.getString(R.string.spoken_capture_first), flush = true, system = true)
                    }
                }
                "Text chat" -> {
                    if (!llmChatEnabled) {
                        reply(context.getString(R.string.spoken_llm_off), flush = true, system = true)
                        return
                    }
                    if (textScanned) {
                        val response = documentChatViewModel.askFollowUp(command)
                        if (featureStillActive("Text chat")) replyOutsidePlayer(response)
                        touchSessionActivity()
                    } else {
                        reply(context.getString(R.string.spoken_capture_text_first), flush = true, system = true)
                    }
                }
            }
        } finally {
            chatPendingQuestion = null
        }
    }

    /** The Ask button, wherever it is pressed. */
    fun onAskTapped() {
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
            } else if (!ensurePermission(Manifest.permission.RECORD_AUDIO)) {
                // Asked; the user presses Ask again once it is allowed.
            } else if (modeNeedsCapture(activeMode ?: "") && !ensurePermission(Manifest.permission.CAMERA)) {
                // A chat question takes a picture.
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
                    if (mode == ModeNames.IMAGE_CHAT || mode == ModeNames.TEXT_CHAT) {
                        chatPendingQuestion = SPEAKING_PLACEHOLDER
                    }
                }
            }
        }
    }

    /** A feature chosen on the feature bar, by a tap or a swipe. */
    fun selectMode(mode: String) {
        // Every feature looks through the camera.
        if (activeMode != mode && !hasCameraPermission) ensurePermission(Manifest.permission.CAMERA)
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
            touchSessionActivity()

            if (mode == "Find objects" && findObjectTarget.isNotBlank()) {
                // Re-announce cached target so users know tracking is still active.
                ttsService.speak(context.getString(R.string.spoken_looking_for, CocoFinnish.displayName(findObjectTarget)), flush = false)
            }
        }

    }

    /** Capture in Image chat or Text chat: a new picture starts a new session. */
    fun onChatCapture() {
        if (!ensurePermission(Manifest.permission.CAMERA)) return
        if (!(activeMode == ModeNames.TEXT_CHAT && (textGuideActive || textGuideStarting))) chat.clear()
        // Text chat: the first press starts the aim guidance, which takes the
        // picture itself once the text is centred; a second press while it is
        // guiding takes it at once.
        if (activeMode == ModeNames.TEXT_CHAT && (textGuideActive || textGuideStarting)) {
            stopTextGuidance()
            cutAllAudio()
            captureGuidedText()
            return
        }
        autoCaptureNote = null
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
                // With the guidance switched off, Capture takes the picture at once.
                if (settingsManager.textScanGuidance.value) {
                    startTextGuidance()
                    return
                }
            }
        }
        triggerScan = true
    }

    // Voice command handler — only after permissions and welcome are done
    suspend fun collectCommands() {
        if (!appInForeground || showTerms || onboardingBlocking) {
            return
        }
        voiceInputService.recognizedText.collectLatest { command ->
            ttsService.releaseMicrophoneFloor()
            if (!appInForeground) return@collectLatest
            if (command.isNotEmpty()) {
                touchSessionActivity()
                if (micFirstActive) {
                    // Mic-first: this is the question paired with the snapped frame.
                    pendingQuestion = command
                    // In the chat now, not when its answer arrives: a first question
                    // waits for its picture, which can take a while with the guidance.
                    chatPendingQuestion = command
                    return@collectLatest
                }
                when (activeMode) {
                    "Find objects" -> answerFindCommand(command)
                    "Image chat", "Text chat" -> answerChatQuestion(command)
                }
            }
        }
    }

    suspend fun expireIdleSessions() {
        if (!appInForeground || showTerms || onboardingBlocking) {
            return
        }
        while (true) {
            delay(SESSION_CHECK_INTERVAL_MS)
            if (!appInForeground) continue
            val hasCachedSession = findObjectTarget.isNotBlank() ||
                imageChatViewModel.hasCachedImage() ||
                documentChatViewModel.hasCachedDocument()
            if (!hasCachedSession) continue

            val idleElapsedMs = now() - lastSessionActivityAtMs

            // Only idleness ends a chat now; a full chat stays until the user moves on.
            if (idleElapsedMs >= SESSION_IDLE_TIMEOUT_MS) {
                expireSessionCaches()
            }
        }
    }

    // Frame processing pipeline — only runs when camera is active and app is foregrounded
    suspend fun runFramePipeline() {
        if (!appInForeground || !hasCameraPermission || cameraDisabled) {
            cameraXManager.stopCamera()
            return
        }

        cameraXManager.setAnalysisResolution(
            if (activeMode == ModeNames.TEXT_CHAT) CameraXManager.TEXT_ANALYSIS_SIZE
            else CameraXManager.DEFAULT_ANALYSIS_SIZE
        )

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
                    } catch (e: CancellationException) {
                        // Leaving the feature cancels its work: not a failure.
                        throw e
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
                    } catch (e: CancellationException) {
                        // Leaving the feature cancels its work: not a failure.
                        throw e
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
                    } catch (e: CancellationException) {
                        // Leaving the feature cancels its work: not a failure.
                        throw e
                    } catch (e: Exception) {
                        Log.e("SightBuddyApp", "Object recognition failed", e)
                    } finally {
                        closeFrame(proxy)
                    }
                }
            }

            "Find objects" -> {
                cameraXManager.throttleIntervalMs = 200L
                // Directions are said on a steady beat, not on every change.
                val cueBeat = CueBeat<DirectionCues.Cue>()
                var hadObject = false
                cameraXManager.frameFlow.collectLatest { proxy ->
                    try {
                        if (showObjectPicker) {
                            cueBeat.reset()
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
                                    cueBeat.reset()
                                    if (hapticFeedback) {
                                        // The same climb, felt: the pulse quickens
                                        // as the object nears the middle.
                                        hapticManager.proximityPulse(proximity)
                                    } else {
                                        pitchToneService.start(
                                            pitchToneService.stepFor(proximity)
                                        )
                                    }
                                } else {
                                    val cue = when (direction) {
                                        "left"   -> DirectionCues.Cue.LEFT
                                        "right"  -> DirectionCues.Cue.RIGHT
                                        "up"     -> DirectionCues.Cue.UP
                                        "down"   -> DirectionCues.Cue.DOWN
                                        else     -> DirectionCues.Cue.CENTRE
                                    }
                                    cueBeat.next(cue, System.currentTimeMillis())?.let { directionCues.say(it) }
                                }
                            } else {
                                // Missed frames count too: the beat stops once the
                                // object has been gone for a moment.
                                cueBeat.next(null, System.currentTimeMillis())
                                if (hadObject) {
                                    hadObject = false
                                    pitchToneService.stop()
                                    hapticManager.stopPulse()
                                }
                            }
                        }
                    } catch (e: CancellationException) {
                        // Leaving the feature cancels its work: not a failure.
                        throw e
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
                            } else {
                                chat.model(response)
                                if (featureStillActive("Image chat")) {
                                    ttsService.speak(response, flush = true)
                                }
                            }
                        }
                    } catch (e: CancellationException) {
                        // Leaving the feature cancels its work: not a failure.
                        throw e
                    } catch (e: Exception) {
                        Log.e("SightBuddyApp", "Image chat failed", e)
                    } finally {
                        closeFrame(proxy)
                    }
                }
            }

            "Text chat" -> {
                // Aiming reads small frames quickly; it keeps up with the hand
                // only if frames are not held back as well.
                cameraXManager.throttleIntervalMs = 200L
                OcrDebug.newSession()
                val guide = TextAimGuide()
                val pageTracker = PageTracker()
                var guiding = false
                // The page the guide last steered by, so Capture reads that page.
                var aimedPage: LocalTextExtractor.TextFrame? = null
                var aimedPageAtMs = 0L
                clearLiveText()
                cameraXManager.frameFlow.collectLatest { proxy ->
                    // Each run of the guidance starts from nothing.
                    val wanted = !triggerScan && !captureForQuestion && textAimGuidanceWanted()
                    if (wanted && !guiding) {
                        guide.reset()
                        pageTracker.reset()
                    }
                    guiding = wanted
                    try {
                        if (captureForQuestion) {
                            captureForQuestion = false
                            guide.reset()
                            pendingCaptureBitmap = withContext(Dispatchers.Default) {
                                imageProxyToBitmap(proxy)
                            }
                        } else if (!triggerScan && textAimGuidanceWanted()) {
                            clearLiveText()
                            // Aim guidance: say where the text is until it sits in
                            // the middle, then take the picture after a steady hold.
                            val (located, paper) = withContext(Dispatchers.Default) {
                                val bitmap = imageProxyToBitmap(proxy, maxLongSide = AIM_FRAME_LONG_SIDE)
                                val scaleToFull = maxOf(proxy.width, proxy.height).toFloat() / maxOf(bitmap.width, bitmap.height)
                                try {
                                    OcrDebug.maybeSaveFrame(bitmap)
                                    localTextExtractor.locateText(bitmap, scaleToFull) to findPaper(bitmap)
                                } finally {
                                    bitmap.recycle()
                                }
                            }
                            // Re-checked: a question may have started while reading.
                            if (textAimGuidanceWanted()) {
                                val now = System.currentTimeMillis()
                                val track = pageTracker.update(located.blocks, located.widthPx, located.heightPx, now)
                                // The sheet of paper first; the text pages when there is none.
                                val aim = PaperFinder.choose(track.target, paper, located.widthPx.toFloat() / located.heightPx, located.blocks)
                                aim.target?.let { target ->
                                    // Capture leaves out what lies beside this page. A small
                                    // text page is likely part of one: then nothing is left out.
                                    aimedPage = target.takeIf { aim.paperUsed || areaOf(it) >= MIN_CAPTURE_PAGE_AREA }
                                    aimedPageAtMs = now
                                }
                                val step = guide.next(aim.target, now)
                                OcrDebug.guide(step.toString(), track, paper, aim.paperUsed, aim.target)
                                when (step) {
                                    TextAimGuide.Step.Silent -> Unit
                                    TextAimGuide.Step.NoText ->
                                        systemMessage(context.getString(R.string.spoken_no_text_visible), flush = false)
                                    is TextAimGuide.Step.Cue -> directionCues.say(
                                        when (step.direction) {
                                            TextAimGuide.Direction.LEFT -> DirectionCues.Cue.LEFT
                                            TextAimGuide.Direction.RIGHT -> DirectionCues.Cue.RIGHT
                                            TextAimGuide.Direction.UP -> DirectionCues.Cue.UP
                                            TextAimGuide.Direction.DOWN -> DirectionCues.Cue.DOWN
                                            TextAimGuide.Direction.CLOSER -> DirectionCues.Cue.CLOSER
                                            TextAimGuide.Direction.FURTHER -> DirectionCues.Cue.FURTHER
                                            TextAimGuide.Direction.CENTRE -> DirectionCues.Cue.HOLD_STEADY
                                        }
                                    )
                                    TextAimGuide.Step.Capture -> {
                                        textGuideActive = false
                                        captureGuidedText()
                                    }
                                }
                            }
                        } else if (!triggerScan && liveTextWanted()) {
                            // With Preview on, the text in view is shown over the camera
                            // where it sits, like Google Lens, to read with TalkBack or
                            // copy, and read out once each time it changes. It changes
                            // only for new text, so a selection or TalkBack's place is
                            // not lost as the hand moves.
                            val located = withContext(Dispatchers.Default) {
                                val bitmap = imageProxyToBitmap(proxy)
                                try { localTextExtractor.locateText(bitmap, debugSource = "read") } finally { bitmap.recycle() }
                            }
                            val blocks = located.blocks
                                .filter { b -> b.text.count { it.isLetter() } >= LIVE_TEXT_MIN_LETTERS }
                                .map { b ->
                                    LiveText.Block(
                                        text = TextSections.paragraph(b.text.lines()),
                                        left = b.left, top = b.top, right = b.right, bottom = b.bottom,
                                        lineHeightPx = b.lineHeightPx,
                                    )
                                }
                            if (liveTextWanted()) {
                                val shown = liveTextTracker.update(blocks, located.widthPx, located.heightPx, System.currentTimeMillis())
                                liveText = shown
                                // Not while a box is pinned: the user is with that one.
                                if (pinnedLiveText == null) {
                                    val gone = liveReadQueue.update(
                                        shown?.blocks?.map { it.text }.orEmpty(),
                                        System.currentTimeMillis(),
                                    )
                                    // No text on screen: nothing more is read, this one included.
                                    if (gone) stopLiveTextReading()
                                    readNextLiveBox()
                                }
                            }
                        } else if (triggerScan) {
                            guide.reset()
                            triggerScan = false
                            clearLiveText()
                            soundFXService.play(SFX.CAMERA_CLICK)

                            // Only a page seen just now: the phone has not moved since.
                            val page = aimedPage?.takeIf { System.currentTimeMillis() - aimedPageAtMs < AIMED_PAGE_FRESH_MS }
                            aimedPage = null
                            if (effectiveTextPreviewEnabled) {
                                val extractedText = withContext(Dispatchers.Default) {
                                    val bitmap = imageProxyToBitmap(proxy)
                                    val text = localTextExtractor.extractText(bitmap, page = page)
                                    bitmap.recycle()
                                    text
                                }
                                if (extractedText.isBlank()) {
                                    if (featureStillActive("Text chat")) {
                                        textScriptPlayer.interrupt()
                                        systemMessage(context.getString(R.string.spoken_no_text_detected))
                                    }
                                } else {
                                    documentChatViewModel.setExtractedText(extractedText)
                                    textScanned = true
                                    chat.scanned(extractedText)
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
                                chat.model(response)
                                if (featureStillActive("Text chat")) {
                                    loadTextPlaybackScript(
                                        documentChatViewModel.firstAssistantResponseForPlayback()
                                            .ifBlank { response },
                                    )
                                }
                            }
                        }
                    } catch (e: CancellationException) {
                        // Leaving the feature cancels its work: not a failure.
                        throw e
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
}

/** The sheet of paper in [bitmap], looked for in a tiny grey copy of it. */
private fun findPaper(bitmap: Bitmap): PaperFinder.Paper? {
    val small = Bitmap.createScaledBitmap(bitmap, PaperFinder.WIDTH, PaperFinder.HEIGHT, true)
    val pixels = IntArray(PaperFinder.WIDTH * PaperFinder.HEIGHT)
    small.getPixels(pixels, 0, PaperFinder.WIDTH, 0, 0, PaperFinder.WIDTH, PaperFinder.HEIGHT)
    if (small != bitmap) small.recycle()
    val luma = IntArray(pixels.size) { i ->
        val p = pixels[i]
        (((p shr 16) and 0xFF) * 299 + ((p shr 8) and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
    }
    return PaperFinder.find(luma, PaperFinder.WIDTH, PaperFinder.HEIGHT)
}

/** Share of the frame [f] covers. */
private fun areaOf(f: LocalTextExtractor.TextFrame): Float =
    (f.right - f.left).coerceAtLeast(0f) * (f.bottom - f.top).coerceAtLeast(0f)

/**
 * Convert an ImageProxy (YUV_420_888) to a correctly-rotated Bitmap. CameraX's
 * own conversion is native; the per-pixel loop this replaced took too long at
 * the resolution Text chat reads at. With [maxLongSide], the result is scaled
 * down in the same step so its longer side is at most that.
 */
private fun imageProxyToBitmap(
    imageProxy: ImageProxy,
    maxLongSide: Int? = null,
): Bitmap {
    val rawBitmap = imageProxy.toBitmap()
    val rotation = imageProxy.imageInfo.rotationDegrees
    val longSide = maxOf(rawBitmap.width, rawBitmap.height)
    val scale = if (maxLongSide != null && longSide > maxLongSide) maxLongSide.toFloat() / longSide else 1f
    if (rotation == 0 && scale == 1f) return rawBitmap
    val matrix = Matrix()
    matrix.postRotate(rotation.toFloat())
    matrix.postScale(scale, scale)
    val rotated = Bitmap.createBitmap(
        rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true
    )
    if (rotated != rawBitmap) rawBitmap.recycle()
    return rotated
}
