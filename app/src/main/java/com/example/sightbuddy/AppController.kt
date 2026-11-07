package com.example.sightbuddy

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.sightbuddy.BuildConfig
import com.example.sightbuddy.R
import com.example.sightbuddy.core.ApiKeyStore
import com.example.sightbuddy.core.AppShortcuts
import com.example.sightbuddy.core.AppWork
import com.example.sightbuddy.core.BeepService
import com.example.sightbuddy.core.CameraXManager
import com.example.sightbuddy.core.CueBeat
import com.example.sightbuddy.core.DirectionCues
import com.example.sightbuddy.core.deviceCues
import com.example.sightbuddy.core.FeedbackDetails
import com.example.sightbuddy.core.FeedbackEmail
import com.example.sightbuddy.core.Cues
import com.example.sightbuddy.core.HapticManager
import com.example.sightbuddy.core.LanguageStore
import com.example.sightbuddy.core.LaunchFocus
import com.example.sightbuddy.core.ModeNames
import com.example.sightbuddy.core.ModelDownloadService
import com.example.sightbuddy.core.ModelSetup
import com.example.sightbuddy.core.NoticeDelivery
import com.example.sightbuddy.core.NetworkStatusManager
import com.example.sightbuddy.core.OpenAiTransport
import com.example.sightbuddy.core.PitchToneService
import com.example.sightbuddy.core.PrivateLog
import com.example.sightbuddy.core.SettingsManager
import com.example.sightbuddy.core.SoundFXService
import com.example.sightbuddy.core.TTSService
import com.example.sightbuddy.core.TermsStore
import com.example.sightbuddy.core.TextScriptPlayer
import com.example.sightbuddy.core.VolumeKeys
import com.example.sightbuddy.core.createCrashReporter
import com.example.sightbuddy.core.hints.FeatureHelpLibrary
import com.example.sightbuddy.core.hints.HelpContent
import com.example.sightbuddy.core.stt.CloudTranscriber
import com.example.sightbuddy.core.stt.SpeechAvailability
import com.example.sightbuddy.core.stt.SpeechEngine
import com.example.sightbuddy.core.stt.SttChoice
import com.example.sightbuddy.core.stt.SttModelManager
import com.example.sightbuddy.core.stt.VoiceInputService
import com.example.sightbuddy.features.chat.CaptureGate
import com.example.sightbuddy.features.chat.ChatKeywords
import com.example.sightbuddy.features.chat.ChatSession
import com.example.sightbuddy.core.language.MissingVoiceHint
import com.example.sightbuddy.features.chat.AnswerSpeaker
import com.example.sightbuddy.features.chat.ChatWorking
import com.example.sightbuddy.core.llm.StreamLog
import com.example.sightbuddy.features.chat.ChatText
import com.example.sightbuddy.features.chat.DocumentChatViewModel
import com.example.sightbuddy.features.chat.ImageChatViewModel
import com.example.sightbuddy.features.chat.LiveReadQueue
import com.example.sightbuddy.features.chat.LiveText
import com.example.sightbuddy.features.chat.LiveTextGate
import com.example.sightbuddy.features.chat.LiveTextTracker
import com.example.sightbuddy.features.chat.LocalTextExtractor
import com.example.sightbuddy.features.chat.MessageMenu
import com.example.sightbuddy.features.chat.OcrDebug
import com.example.sightbuddy.features.chat.PageCornerGuide
import com.example.sightbuddy.features.chat.PageCornerModel
import com.example.sightbuddy.features.chat.PageCorners
import com.example.sightbuddy.features.chat.PageLock
import com.example.sightbuddy.features.chat.TextSections
import com.example.sightbuddy.features.vision.CocoFinnish
import com.example.sightbuddy.features.vision.ColorIdAnalyzer
import com.example.sightbuddy.features.vision.DetectionRules
import com.example.sightbuddy.features.vision.DiscoverConfirm
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
import kotlinx.coroutines.NonCancellable
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
import android.os.SystemClock
import android.text.format.Formatter
import androidx.camera.core.ImageProxy
import com.example.sightbuddy.core.Memory
import com.example.sightbuddy.core.MemoryEntry
import com.example.sightbuddy.core.MemoryNames
import com.example.sightbuddy.core.MemoryStore
import com.example.sightbuddy.core.llm.LocalGemma
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job

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

/** How recently the app must have asked for a stop to call the stop the user's. */
private const val USER_STOP_WINDOW_MS = 1_000L


/** How long to wait for a snapped frame to be encoded before giving up on it. */
private const val CAPTURE_ENCODE_TIMEOUT_MS = 4_000L

private const val SESSION_IDLE_TIMEOUT_MS = 30 * 60 * 1000L
private const val SESSION_CHECK_INTERVAL_MS = 15_000L

/** Utterance ids of the live text's reading start with this, one per box. */
private const val LIVE_TEXT_READING_ID = "LIVE_TEXT_READING"

/** A block with fewer letters than this is not worth showing as live text. */
private const val LIVE_TEXT_MIN_LETTERS = 2

/** How long a new answer waits for the one it replaces to stop (issue #24). */
private const val PREVIOUS_ANSWER_STOP_MS = 3_000L

/** How often the missing-voice hint checks whether the reading has ended (issue #26). */
private const val MISSING_VOICE_CHECK_MS = 500L

/** The working tick: how long an answer may take before it starts, and its beat. */
private const val WORKING_TICK_DELAY_MS = 700L
private const val WORKING_TICK_INTERVAL_MS = 1400L

/** How long a capture waits for its full-size photo before reading the frame instead. */
private const val STILL_TIMEOUT_MS = 3_000L

/** How long Capture waits for the photo stream to be bound before taking its picture anyway. */
private const val STILL_BIND_TIMEOUT_MS = 2_000L


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
    /**
     * The feature an app shortcut started the app on. Known here, before the
     * first frame: for anyone past onboarding the features (and the pager's
     * first page) are drawn in that very frame.
     */
    initialFeature: String? = null,
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
    /** Text chat's text in view: read out, and shown over the camera while Preview is on. */
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
     *
     * The body carries the app's and the phone's details only if the user has
     * allowed it in Settings; see [FeedbackEmail].
     */
    fun openFeedbackEmail() {
        val body = FeedbackEmail.body(
            allowed = settingsManager.feedbackDetails.value,
            details = ::feedbackDetails,
            say = { id, args -> if (args.isEmpty()) context.getString(id) else context.getString(id, *args) },
        )
        try {
            // Sent as ACTION_SEND found through a mailto selector: only mail apps
            // take it, and they read the subject and body from the extras, which
            // some mail apps ignore on ACTION_SENDTO.
            val intent = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_EMAIL))
                putExtra(Intent.EXTRA_SUBJECT, "Sight Buddy feedback")
                if (body.isNotEmpty()) putExtra(Intent.EXTRA_TEXT, body)
                selector = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
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

    /** What a feedback email may carry, read now; only the facts [FeedbackDetails] lists. */
    private fun feedbackDetails() = FeedbackDetails(
        appVersion = BuildConfig.VERSION_NAME,
        appBuild = BuildConfig.VERSION_CODE,
        androidVersion = Build.VERSION.RELEASE,
        androidSdk = Build.VERSION.SDK_INT,
        phone = Build.MANUFACTURER.replaceFirstChar { it.uppercase() } + " " + Build.MODEL,
        screenReaderOn = isScreenReaderOn(context),
        appLanguage = context.getString(
            if (languageStore.isFinnish()) R.string.language_name_finnish else R.string.language_name_english
        ),
        featuresShown = pages.map { ModeNames.display(context, it) },
        cameraPreview = settingsManager.cameraPreview.value,
        liveText = settingsManager.liveText.value,
        soundEffects = settingsManager.soundEffects.value,
        vibration = settingsManager.vibration.value,
        pitchFeedback = settingsManager.pitchFeedback.value,
        speechRate = SettingsManager.ttsRateLabel(context, settingsManager.ttsSpeechRate.value),
        // What actually runs: the cloud when chosen and allowed, else the choice
        // as it falls back to what is installed.
        speechRecognition = context.getString(
            if (speech.engine == SpeechEngine.CLOUD) R.string.settings_cloud_stt else effectiveStt.labelRes
        ),
        // On only when it can answer: the switch on and the model on the phone.
        onDeviceChat = localAiReady,
        apiKeySaved = apiKeyPresent,
    )

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
    // The answer being written, shown growing in one bubble a sentence at a
    // time until it is done (issue #24); null when no answer is streaming.
    var chatPendingAnswer by mutableStateOf<String?>(null)
    // The answer's first sentence is here and being said: it is no longer
    // waited for, though the model is still writing the rest (issue #24).
    var answerSpeaking by mutableStateOf(false)
        private set
    // The answer being spoken, by number; a newer one silences the older.
    private var answerNumber = 0
    private var answerJob: Job? = null
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
    /**
     * The chat message whose pop-up menu is open (issue #21), by its place in the
     * chat as shown; null for none. A pop-up like the others: see [popupOpen].
     */
    var messageMenuAt by mutableStateOf<Int?>(null)
        private set
    /** The message TalkBack is to be sent back to now that its menu has closed; see [MessageMenu.focusBack]. */
    var messageFocusBackTo by mutableStateOf<Int?>(null)
        private set
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
        "Text chat" -> CaptureGate.textNeedsPicture(textScanned, readingPage = documentChatProcessing)
        else -> false
    }

    /** Clear any in-progress mic-first capture (e.g. on leaving the feature). */
    fun cancelMicFirst() {
        ttsService.releaseMicrophoneFloor()
        micFirstActive = false
        micFirstMode = null
        captureForQuestion = false
        questionPictureGuided = false
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
     * Find page's picture, taken by the guidance: read as the page it found,
     * cropped to it.
     */
    fun captureGuidedText() {
        // A question waiting on Find page's guidance (Ask finds the page): its picture.
        if (micFirstActive && micFirstMode == ModeNames.TEXT_CHAT) {
            questionPictureGuided = true
            captureForQuestion = true
            return
        }
        captureGuided = true
        triggerScan = true
    }

    /** The question's picture was taken by Find page's guidance: read as its page. */
    private var questionPictureGuided = false

    /**
     * Whether the picture asked for is Find page's, read as its page. Capture's
     * is read whole: everything in view, nothing cut.
     */
    private var captureGuided = false

    /**
     * Text chat's photo stream is bound, or asked for. A bound photo stream
     * keeps the camera working harder and the phone warm, and halves the
     * frames' size, so it is bound only while a capture is coming.
     */
    private var stillsWanted = false

    /** Binds Text chat's photo stream for a capture coming, or lets it go. */
    private fun wantStills(on: Boolean) {
        if (stillsWanted == on) return
        stillsWanted = on
        if (activeMode == ModeNames.TEXT_CHAT) {
            cameraXManager.setAnalysisResolution(CameraXManager.TEXT_ANALYSIS_SIZE, stills = on)
        }
    }

    /** A capture is coming in Text chat: guidance, a picture asked for, or a question to take one for. */
    private fun stillsNeeded(): Boolean =
        textGuideActive || textGuideStarting || triggerScan || captureForQuestion || micFirstActive

    /** Find page's tone is playing. */
    private var pageToneOn = false

    /** Stops Find page's tone, if it plays. */
    private fun stopPageTone() {
        if (!pageToneOn) return
        pageToneOn = false
        cues.stopGuidance()
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
     * Starts Find page's aim guidance: "Looking for text", kept in the chat like
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
        stopPageTone()
    }

    /** Snap the frame for an armed mic-first question and await the pairing. */
    fun beginMicFirstCapture(mode: String) {
        // Deliberately no cancelMicFirst here: a question that has already been
        // transcribed must survive until the frame arrives to pair with it.
        micFirstActive = true
        micFirstMode = mode
        voiceInputService.stopListening()
        // Text chat's picture is a photo: bound while "Let me take a picture" is said.
        if (mode == ModeNames.TEXT_CHAT) wantStills(true)
        // Ask finds the page: Find page's guidance takes the question's picture
        // (captureGuidedText), with no time limit, as Find page has none.
        if (mode == ModeNames.TEXT_CHAT && settingsManager.askFindsPage.value) {
            startTextGuidance()
            return
        }

        // The token guards against an older attempt firing on a newer one after
        // the user retries.
        micFirstAttempt += 1
        val attempt = micFirstAttempt

        fun stillThisAttempt() = attempt == micFirstAttempt && micFirstActive

        // "Let me take a picture", a second's pause to hold still, then the shutter.
        announceAutoCapture {
            micFirstScope.launch {
                delay(AUTO_CAPTURE_PAUSE_MS)
                if (!stillThisAttempt()) return@launch
                val textChat = mode == ModeNames.TEXT_CHAT
                // Text chat's photo stream may still be binding if the note was short;
                // its shutter sounds once the photo is taken (capturedPicture).
                if (textChat) {
                    cameraXManager.awaitBound(STILL_BIND_TIMEOUT_MS)
                    if (!stillThisAttempt()) return@launch
                }
                captureForQuestion = true
                if (!textChat) cues.give(Cues.Cue.SHUTTER, alongside = true)

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
     * The one-time hint (issue #26): the language, as a code, of text that
     * was read with no voice of its own on the phone; null when not shown.
     * Shown once ever ([MissingVoiceHint]), and only once that reading is
     * over, with a feature in front: the pop-up must not cut it.
     */
    var missingVoiceLanguage by mutableStateOf<String?>(null)

    private val missingVoiceHint = MissingVoiceHint(
        shown = { settingsManager.missingVoiceHintShown() },
        markShown = { settingsManager.markMissingVoiceHintShown() },
    )

    /** Waits for the reading to end, then shows the hint; while the hint waits. */
    private var missingVoiceWatch: Job? = null

    init {
        // Called as such a line is said, on whichever thread said it.
        ttsService.onVoiceMissing = { languages ->
            scope.launch {
                missingVoiceHint.voiceMissing(languages)
                if (missingVoiceHint.waiting && missingVoiceWatch?.isActive != true) {
                    missingVoiceWatch = scope.launch { showMissingVoiceHintWhenQuiet() }
                }
            }
        }
    }

    /** Checks every [MISSING_VOICE_CHECK_MS] until the hint has been shown. */
    private suspend fun showMissingVoiceHintWhenQuiet() {
        while (missingVoiceHint.waiting) {
            delay(MISSING_VOICE_CHECK_MS)
            val speechOver = MissingVoiceHint.readingOver(
                speaking = ttsService.isSpeaking(),
                answerWriting = answerJob?.isActive == true,
                pageReading = textScriptPlayer.currentState == TextScriptPlayer.State.PLAYING,
                listening = isListening,
                answerOnItsWay = chatWorking,
            )
            missingVoiceHint.toShow(speechOver, featureInFront = featuresInFront)
                ?.let { missingVoiceLanguage = it }
        }
    }

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
    // Every sound and vibration the app makes, through Settings' Sound effects
    // and Vibration. Nothing plays a sample or vibrates except through it.
    val cues = deviceCues(settingsManager, soundFXService, hapticManager, pitchToneService) {
        voiceInputService.usesSystemEarcons()
    }
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
        voiceInputService.cancelListening()
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
     * serves every language. The mic opens at once, without waiting it out.
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
        // Heard, felt, or both: Settings' Sound effects and Vibration.
        cues.give(Cues.Cue.RECORDING_START)
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
        cues.give(Cues.Cue.RECORDING_STOP)
    }

    fun pauseForBackground() {
        soundFXService.stopLoop()
        stopTextGuidance()
        pitchToneService.stop()
        hapticManager.stopPulse()
        ttsService.releaseMicrophoneFloor()
        voiceInputService.cancelListening()
        voiceInputService.discardPendingTranscript()
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
    val popupOpen get() = screenPopupOpen || showObjectPicker || messageMenuAt != null

    /**
     * The pop-ups that cover the feature screen. Not a message's own menu or
     * the Find objects picker: they belong to the feature in front, so closing
     * one does not move TalkBack to the tab (see LaunchFocus.featureScreenInFront).
     */
    private val screenPopupOpen get() = showHelp || showMemories || showSetupDialog ||
        showSttModelsDialog || showGemmaDialog || showGemmaDownloadDialog || showLanguageDialog ||
        showCloudSttDialog || showChoiceRespectedDialog || missingVoiceLanguage != null

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
            // The camera stops under these, so no frame would stop Find page's tone.
            stopPageTone()
        }
    }

    suspend fun onPopupChanged() {
        if (popupOpen) {
            cutAllTtsFeedback()
            stopPageTone()
        }
    }

    /** A feature is on screen with nothing over it: no onboarding, Settings or pop-up. */
    val featuresInFront get() =
        appInForeground && !onboardingBlocking && !showSettings && !popupOpen && activeMode != null

    val launchFocus = LaunchFocus()

    /** True while the feature bar's selected tab holds the focus it takes at launch. */
    var focusFeatureTab by mutableStateOf(false)

    private val featureScreenInFront get() = LaunchFocus.featureScreenInFront(
        appInForeground = appInForeground,
        onboardingBlocking = onboardingBlocking,
        settingsOpen = showSettings,
        featureShowing = activeMode != null,
        screenPopupOpen = screenPopupOpen,
    )

    /**
     * The selected tab comes first in TalkBack's order whenever the feature
     * screen is about to be met: from the first frame, and while it is away, so
     * it is first again when it comes back. Any change of state (a swipe,
     * Settings, a pop-up, the background) ends it; a touch does not.
     */
    var featureTabFirst by mutableStateOf(true)

    /**
     * Each time the feature screen comes in front (launch, back from the
     * background, Settings or a pop-up closed), the selected feature tab takes
     * TalkBack's focus (see LaunchFocus), until anything changes. A swipe moves
     * nothing: TalkBack reads the new tab itself.
     */
    suspend fun holdLaunchFocus() {
        val inFront = featureScreenInFront
        val arrived = launchFocus.take(inFront)
        if (!inFront) {
            featureTabFirst = true
            return
        }
        if (!arrived) {
            featureTabFirst = false
            return
        }
        featureTabFirst = true
        focusFeatureTab = true
        try {
            awaitCancellation()
        } finally {
            focusFeatureTab = false
        }
    }

    /** The tab, coming on screen during the hold, asks whether to take the focus: once. */
    fun requestLaunchFocus(): Boolean = launchFocus.request()

    // The chat is working on an answer: a photo or a question on its way.
    val chatWorking get() = ChatWorking.working(
        inChat = activeMode == ModeNames.IMAGE_CHAT || activeMode == ModeNames.TEXT_CHAT,
        pictureInFlight = captureInFlight,
        questionWaiting = chatPendingQuestion != null && chatPendingQuestion != SPEAKING_PLACEHOLDER,
        processing = if (activeMode == ModeNames.IMAGE_CHAT) imageChatProcessing else documentChatProcessing,
        answerSpeaking = answerSpeaking,
        aiming = textGuideStarting || textGuideActive,
        takingPicture = activeMode == ModeNames.TEXT_CHAT && (captureForQuestion || triggerScan || photoInProgress),
    )

    /** Text chat's photo is being taken: until its shutter, nothing is worked on. */
    var photoInProgress by mutableStateOf(false)
        private set

    /**
     * Image chat has a picture or an answer on its way: Capture is ignored (see
     * [CaptureGate]), and tells TalkBack it is busy.
     */
    val imageChatBusy get() = CaptureGate.imageChatBusy(
        pictureAsked = triggerScan,
        pictureInFlight = captureInFlight,
        answering = imageChatProcessing,
        questionWaiting = chatPendingQuestion != null && chatPendingQuestion != SPEAKING_PLACEHOLDER,
        answerSpeaking = answerSpeaking,
    )

    // A soft tick while it works, as Seeing AI and Envision do, so a wait of
    // several seconds is not silence. Not for a quick answer: it starts after
    // 0.7 s. Never over a pop-up or Settings, not while recording, and never
    // over speech (ChatWorking.tickBeat).
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
                if (ChatWorking.tickBeat(working = chatWorking, speaking = ttsService.isSpeaking())) {
                    cues.give(Cues.Cue.TICK)
                }
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

    /** The chat starts over, and a message menu open on it goes with it (the places would not match). */
    private fun clearChat() {
        chat.clear()
        chatsCleared++
        messageMenuAt = null
        messageFocusBackTo = null
    }

    /** How many times the chat has started over: a message's place is only good within one. */
    private var chatsCleared = 0

    fun clearCapturedFrame() {
        // A feature's spoken line goes with it.
        ttsService.clearLastSpoken()
        ttsMuted = false
        ttsService.muted = false
        stopTextGuidance()
        clearChat()
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

    /**
     * Share in the More row: the active feature's chat, as Save chat would keep
     * it, to Android's share sheet. With nothing to keep, it says so, as Save
     * chat does.
     */
    fun shareCurrentChat() {
        val mode = activeMode ?: return
        val text = chat.forSharing(::transcriptLabel)
        if (text.isEmpty()) {
            systemMessage(context.getString(R.string.memories_nothing_to_share), force = true)
            return
        }
        // The share sheet only pauses the app, so speech and the working tick
        // would otherwise run on under TalkBack reading the sheet.
        cutAllAudio()
        shareChatText(ModeNames.display(context, mode), text)
    }

    /** Share on a saved chat in Memories: the same text as the chat had on screen. */
    fun shareMemory(memory: Memory) {
        val text = ChatText.ofSaved(memory.entries, ::transcriptLabel)
        if (text.isEmpty()) {
            // Not written into the chat behind Memories: it is not that chat's.
            ttsService.speak(context.getString(R.string.memories_nothing_to_share), flush = true, force = true)
            return
        }
        shareChatText(memory.name, text)
    }

    /**
     * A press and hold on the chat message at [index] opens its menu (issue
     * #21). It is a pop-up like the others ([popupOpen]): the voice and the
     * working tick stop, and the features stay silent until it closes.
     */
    fun openMessageMenu(index: Int) {
        cutAllAudio()
        messageFocusBackTo = null
        messageMenuAt = index
    }

    /**
     * The menu of the message at [index] closes with no choice made: Back or a
     * touch outside ([focusBack], and TalkBack goes back to the message), or the
     * message itself going (a new picture, the screen reader switched off),
     * with nothing to go back to.
     */
    fun closeMessageMenu(index: Int, focusBack: Boolean) {
        if (messageMenuAt != index) return
        messageMenuAt = null
        if (focusBack) handFocusBack(index, MessageMenu.focusBack(choice = null))
    }

    /**
     * A choice in the pop-up menu of [entry], the message at [index]
     * ([MessageMenu]): Copy and Share take that message's text alone; Save chat
     * is the More row's. Save chat's confirmation and Share's sheet leave the
     * focus where it is ([MessageMenu.focusBack]).
     */
    fun chooseFromMessageMenu(index: Int, entry: TranscriptEntry, item: MessageMenu.Item) {
        if (messageMenuAt != index) return
        messageMenuAt = null
        when (item) {
            MessageMenu.Item.COPY -> copyMessage(entry, index)
            MessageMenu.Item.SHARE -> shareMessage(entry)
            MessageMenu.Item.SAVE_CHAT -> saveCurrentChat()
        }
    }

    /** The message at [index] has TalkBack again, or is gone: nothing is owed to it. */
    fun messageFocusReturned(index: Int) {
        if (messageFocusBackTo == index) messageFocusBackTo = null
    }

    private fun copyMessage(entry: TranscriptEntry, at: Int) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val copied = clipboard != null && try {
            val clip = ClipData.newPlainText(transcriptLabel(entry.kind), MessageMenu.textOf(entry))
            clipboard.setPrimaryClip(clip)
            true
        } catch (e: RuntimeException) {
            Log.w("SightBuddyApp", "Could not copy a message", e)
            false
        }
        val notice = MessageMenu.copyNotice(copied, Build.VERSION.SDK_INT)
        val words = when (notice) {
            MessageMenu.Notice.COPIED -> context.getString(R.string.message_menu_copied)
            MessageMenu.Notice.COPY_FAILED -> context.getString(R.string.message_menu_copy_failed)
            null -> null
        }
        handFocusBack(at, MessageMenu.focusBack(MessageMenu.Item.COPY, spoken = words != null), words)
    }

    /**
     * Says [words] (if any) in the app's voice, which moves nothing, and sends
     * TalkBack back to the message at [at] as [how] says. A line cut short never
     * reports done; the focus is then left where it is.
     */
    private fun handFocusBack(at: Int, how: MessageMenu.FocusBack, words: String? = null) {
        val afterSpeech = how == MessageMenu.FocusBack.AFTER_SPEECH
        // Not if another menu has opened meanwhile, or the chat started over.
        val chatThen = chatsCleared
        val onDone: (() -> Unit)? = if (afterSpeech) {
            { scope.launch { if (messageMenuAt == null && chatsCleared == chatThen) messageFocusBackTo = at } }
        } else null
        val queued = words != null && ttsService.speak(words, flush = true, force = true, onDone = onDone)
        // Nothing could be said (the voice not ready): nothing to wait for.
        if (how == MessageMenu.FocusBack.NOW || (afterSpeech && !queued)) messageFocusBackTo = at
    }

    private fun shareMessage(entry: TranscriptEntry) {
        val mode = activeMode ?: return
        // As for the whole chat: nothing runs on under TalkBack reading the sheet.
        cutAllAudio()
        shareChatText(ModeNames.display(context, mode), MessageMenu.textOf(entry))
    }

    /** The one way the app shares: Android's share sheet, plain text. [title] becomes a mail's subject. */
    private fun shareChatText(title: String, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TITLE, title)
        }
        try {
            context.startActivity(Intent.createChooser(send, null))
        } catch (e: Exception) {
            Log.w("SightBuddyApp", "Could not open the share sheet", e)
            // Said, not only logged: a blind user would otherwise hear nothing.
            ttsService.speak(context.getString(R.string.share_failed), flush = true, force = true)
        }
    }

    /** A message's label, as the chat shows it above the message. */
    private fun transcriptLabel(kind: TranscriptEntry.Kind): String = context.getString(
        when (kind) {
            TranscriptEntry.Kind.SCANNED_TEXT -> R.string.transcript_scanned_text
            TranscriptEntry.Kind.QUESTION -> R.string.transcript_question
            TranscriptEntry.Kind.ANSWER -> R.string.transcript_answer
            TranscriptEntry.Kind.SYSTEM -> R.string.transcript_system
        }
    )

    fun endSessionForSettings() {
        cutAllAudio()
        voiceInputService.discardPendingTranscript()
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

    val pages get() = settingsManager.shownFeatures(imageChatAvailable = llmChatEnabled)

    /**
     * Tells the feature guard whether Image chat can be shown, so it counts
     * towards "at least one feature" only then. Launched on [llmChatEnabled].
     */
    fun syncImageChatAvailable() = settingsManager.setImageChatAvailable(llmChatEnabled)

    init {
        // Before the first frame too: Image chat left alone in an earlier run,
        // with its AI gone since, would otherwise leave nothing to show.
        syncImageChatAvailable()
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
    suspend fun onListeningChanged() {
        if (!isListening) soundFXService.stopLoop()
        if (wasListening && !isListening) {
            val endedByUser = System.currentTimeMillis() - userStopAtMs < USER_STOP_WINDOW_MS
            recordingEndedAtMs = if (endedByUser) 0L else System.currentTimeMillis()
            // The mic is closed, so the floor has nothing left to protect. A
            // transcript or an outcome event releases it too, but a recording can
            // end with neither (a platform error), and the floor would then mute
            // the app for up to a minute. Clearing the old transcript at the start
            // of each recording used to release it by accident.
            ttsService.releaseMicrophoneFloor()
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

    /**
     * A model on the phone that failed its SHA-256 check before loading has been
     * deleted: say so once, as other model notices are said. The flag stays set
     * until the voice has taken the line: not while a recording holds the
     * microphone (the line would be dropped), and not before the voice is ready.
     */
    suspend fun collectDamagedModels() = coroutineScope {
        launch {
            localGemma.removedAsDamaged.collect { removed ->
                if (!removed) return@collect
                sayDamagedNotice(context.getString(R.string.spoken_gemma_damaged))
                localGemma.damagedNoticeSaid()
            }
        }
        launch {
            sttModelManager.removedAsDamaged.collect { removed ->
                if (!removed) return@collect
                sayDamagedNotice(context.getString(R.string.spoken_models_damaged))
                sttModelManager.damagedNoticeSaid()
            }
        }
    }

    /** [confirmation], said once the microphone is free and tried again until the voice takes it. */
    private suspend fun sayDamagedNotice(text: String) = NoticeDelivery.deliver(
        micHasFloor = { ttsService.micHasFloor() },
        speak = { ttsService.speak(text, flush = false, force = true, caption = false) },
    )

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
                        chat.user(q)
                        // Before the answer, not after: a question asked while it is
                        // still being said is about this picture, not "Capture first".
                        imageScanned = true
                        streamAnswer(
                            "Image chat",
                            interruptReader = false,
                            cancelRequest = imageChatViewModel::cancelActiveRequest,
                        ) { onDelta -> imageChatViewModel.processImage(bmp, q, onDelta) }
                        touchSessionActivity()
                    }
                    "Text chat" -> if (ChatKeywords.match(q) == ChatKeywords.Keyword.READ_ORIGINAL) {
                        // "Read original" before any scan: this picture's text, as captured, no AI.
                        documentChatViewModel.resetSession()
                        clearTextPlayback()
                        val page = withContext(Dispatchers.Default) { localTextExtractor.extractText(bmp) }
                        documentChatViewModel.setExtractedText(page)
                        textScanned = documentChatViewModel.hasCachedDocument()
                        touchSessionActivity()
                        chat.user(q)
                        if (page.isBlank()) {
                            if (featureStillActive("Text chat")) {
                                systemMessage(context.getString(R.string.spoken_no_text_detected))
                            }
                        } else if (featureStillActive("Text chat")) {
                            readOriginal(page)
                        } else {
                            chat.scanned(page)
                        }
                    } else {
                        documentChatViewModel.resetSession()
                        clearTextPlayback()
                        chat.user(q)
                        streamAnswer(
                            "Text chat",
                            interruptReader = true,
                            cancelRequest = documentChatViewModel::cancelActiveRequest,
                        ) { onDelta -> documentChatViewModel.processDocument(bmp, q, onDelta) }
                        // Only "scanned" if OCR actually found text: otherwise the
                        // next Ask would do a follow-up against an empty document.
                        textScanned = documentChatViewModel.hasCachedDocument()
                        touchSessionActivity()
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
     * Find page aims the camera for the user: its guidance is on, and nothing
     * else is going on (a recording, a question, the reader speaking).
     */
    fun textAimGuidanceWanted(): Boolean =
        textGuideActive &&
            featureStillActive(ModeNames.TEXT_CHAT) &&
            // A question waits on it when Ask finds the page.
            (!micFirstActive || micFirstMode == ModeNames.TEXT_CHAT) &&
            !isListening &&
            !documentChatViewModel.isProcessing.value &&
            textScriptPlayer.currentState != TextScriptPlayer.State.PLAYING

    /** The page-corner model, loaded the first time Text chat wants it. */
    val pageCornerModel: PageCornerModel get() = PageCornerModel.get(context)

    /**
     * Find page guides by the page's corners and reads the page cropped out,
     * unless the model could not be loaded or gave up: then Find page takes
     * the picture at once, read whole, like Capture.
     */
    fun pageCornersOn(): Boolean = !pageCornerModel.failed

    /**
     * The page's corners in the steady aim frame after which the guide took
     * the picture, and when: the capture's crop falls back on them. A manual
     * Capture has none.
     */
    private var steadyPage: PageCorners.Corners? = null
    private var steadyPageAtMs = 0L

    /** The steady corners, if the guide took the picture just now; used once. */
    private fun takeSteadyPage(): PageCorners.Corners? {
        val page = steadyPage?.takeIf { System.currentTimeMillis() - steadyPageAtMs <= PageCornerGuide.STEADY_PAGE_FRESH_MS }
        steadyPage = null
        return page
    }

    /**
     * Reads the captured [frame] with [read], cropped to its page first when
     * [crop] (Find page's picture) and [pageCornersOn]; [read] is told whether
     * it was, since a cropped page has nothing beside it to leave out. Logs the
     * crop, the reading's time and [chars] of what it gave. Blocking: off the
     * main thread.
     */
    private suspend fun readCaptured(
        frame: Bitmap,
        crop: Boolean,
        chars: (String) -> Int = { it.length },
        read: suspend (image: Bitmap, cropped: Boolean) -> String,
    ): String {
        if (!crop || !pageCornersOn()) {
            steadyPage = null
            return read(frame, false)
        }
        val crop = pageCornerModel.cropToPage(frame, takeSteadyPage())
        debugCrop(crop)
        val page = crop.page
        val startedAt = SystemClock.elapsedRealtime()
        return try {
            read(page ?: frame, page != null).also { text ->
                Log.i(
                    PageCornerModel.TAG,
                    "capture ${describe(crop, frame)} readMs=${SystemClock.elapsedRealtime() - startedAt} chars=${chars(text)}",
                )
            }
        } finally {
            page?.recycle()
        }
    }

    /**
     * Text chat's captured picture: a full-size photo, whose letters are about
     * twice as large in pixels as in the analysis [frame], or the frame itself
     * when the photo fails, takes too long or finds no memory. The shutter
     * sounds once the picture is taken, so the phone is held until then.
     * The photo stream is not let go here: that rebinds the camera, whose new
     * frames cancel the frame loop's work, the reading of this very picture
     * (the phone, 2026-10-31: read, answered, then dropped without a word).
     * The frame loop lets it go once the work is done. Blocking: off the main
     * thread.
     */
    private suspend fun capturedPicture(frame: ImageProxy): Bitmap {
        val startedAt = SystemClock.elapsedRealtime()
        withContext(Dispatchers.Main) { photoInProgress = true }
        val still = try {
            withTimeoutOrNull(STILL_TIMEOUT_MS) { cameraXManager.takeStill() }
        } finally {
            withContext(NonCancellable + Dispatchers.Main) { photoInProgress = false }
        }
        withContext(Dispatchers.Main) { cues.give(Cues.Cue.SHUTTER) }
        val photo = still?.let {
            try {
                imageProxyToBitmap(it).also { bitmap ->
                    Log.i(PageCornerModel.TAG, "photo ${bitmap.width}x${bitmap.height} in ${SystemClock.elapsedRealtime() - startedAt} ms")
                }
            } catch (e: OutOfMemoryError) {
                Log.w(PageCornerModel.TAG, "No memory for the photo; reading the frame", e)
                null
            } catch (e: Exception) {
                Log.w(PageCornerModel.TAG, "Photo unreadable; reading the frame", e)
                null
            } finally {
                it.close()
            }
        }
        return photo
            ?: imageProxyToBitmap(frame).also { Log.i(PageCornerModel.TAG, "no photo; frame ${it.width}x${it.height}") }
    }

    /**
     * Find page's picture for a question: the page cut out and flattened, or
     * the whole [picture] when the model finds none. Blocking: off the main thread.
     */
    private fun pageOf(picture: Bitmap): Bitmap {
        if (!pageCornersOn()) return picture
        val crop = pageCornerModel.cropToPage(picture, takeSteadyPage())
        debugCrop(crop)
        Log.i(PageCornerModel.TAG, "capture for a question ${describe(crop, picture)}")
        return crop.page?.also { picture.recycle() } ?: picture
    }

    /** A capture's crop, for the dev overlay's session log. */
    private fun debugCrop(crop: PageCornerModel.Crop) {
        if (!OcrDebug.enabled) return
        val found = crop.found ?: return
        val corners = crop.own ?: found.corners
        val warp = crop.size?.let { "warp ${it.width}x${it.height} ${crop.source.name.lowercase()}" } ?: "whole frame"
        OcrDebug.corners("capture", warp, corners, found.frameMs, PageCorners.areaShare(corners))
    }

    /**
     * What a capture's crop found, for the log: whose corners it used (`own`,
     * `steady` or `none`), the picture's own corners of the page, and the size
     * of what is read, the flattened page or the whole [frame].
     */
    private fun describe(crop: PageCornerModel.Crop, frame: Bitmap): String {
        val found = crop.found ?: return "used=none corners=no model read=whole frame ${frame.width}x${frame.height}"
        val c = crop.own ?: found.corners
        val seen = if (c.allSeen()) "all seen" else "${c.seenCount()} seen"
        val read = crop.size?.let { "page ${it.width}x${it.height}" } ?: "whole frame ${frame.width}x${frame.height}"
        return "used=${crop.source.name.lowercase()} corners=$seen ${PageCorners.summary(c)} " +
            "model input ${c.widthPx}x${c.heightPx} frameMs=${found.frameMs} modelMs=${found.modelMs} read=$read"
    }

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
     * Live text: with the Live text setting on, and only until the chat has
     * started. Once there is a page, a question or a note, the chat's messages
     * are what is on screen, and live text would get in their way. It is read
     * out and drawn with Preview on or off.
     */
    fun liveTextGate(): LiveTextGate = LiveTextGate(
        idle = instantTextWanted(),
        settingOn = liveTextOn,
        chatStarted = textScanned ||
            documentChatViewModel.chatHistory.value.isNotEmpty() ||
            !chat.isEmpty() ||
            playbackTranscript.isNotBlank(),
    )

    /** The text in view is read out. */
    fun liveTextWanted(): Boolean = liveTextGate().reads

    /** The text in view is drawn, over the camera or the plain background, as boxes that can be pinned. */
    fun liveTextDrawn(): Boolean = liveTextGate().draws

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
     * "Read original" in Text chat (issue #13): the scan exactly as it was
     * captured, put in the chat and read by the page reader, with no AI. Asked
     * again, it is written again, after the question.
     */
    fun readOriginal(page: String) {
        chat.scanned(page)
        loadTextPlaybackScript(page)
    }

    /**
     * Speaks an AI answer as it is written (issue #24), then writes it whole
     * into the chat in the bubble's place (in the same frame, so a screen
     * reader's focus on the bubble stays), and returns it.
     * [produce] runs the request, handing each new piece of text to its
     * `onDelta` from the model's thread; the pieces come here on the main
     * thread, where [AnswerSpeaker] says each complete sentence (the first
     * cuts what was being said, and the page reader when [interruptReader]),
     * and the bubble [chatPendingAnswer] grows by whole sentences.
     *
     * Only while the user is still in this [mode]'s chat and this is the
     * latest answer: a newer answer stops this one's request
     * ([cancelRequest]) and says nothing more of it. So does this coroutine
     * being cancelled (a newer spoken question): the blocking model call
     * would otherwise run on to its end before anything else could start.
     */
    private suspend fun streamAnswer(
        mode: String,
        interruptReader: Boolean,
        cancelRequest: () -> Unit,
        produce: suspend (onDelta: (String) -> Unit) -> String,
    ): String = coroutineScope {
        // An answer still being said is replaced: its request stops first, so
        // the model is free for this one, and it says nothing more.
        answerJob?.takeIf { it.isActive }?.let { previous ->
            cancelRequest()
            answerNumber++
            withTimeoutOrNull(PREVIOUS_ANSWER_STOP_MS) { previous.join() }
        }
        val number = ++answerNumber
        answerJob = coroutineContext.job
        // What the replaced answer left: this one is waited for again.
        answerSpeaking = false
        chatPendingAnswer = null
        val chatAtStart = chatsCleared
        fun current() = number == answerNumber
        fun stillHere() = current() && chatsCleared == chatAtStart && featureStillActive(mode)
        val speaker = AnswerSpeaker(
            stillHere = ::stillHere,
            say = { sentence, first ->
                if (first && interruptReader) textScriptPlayer.interrupt()
                ttsService.speak(sentence, flush = first)
            },
            now = SystemClock::elapsedRealtime,
            onFirstSentence = { if (current()) answerSpeaking = true },
        )
        try {
            val deltas = Channel<String>(Channel.UNLIMITED)
            val shown = launch {
                for (delta in deltas) {
                    speaker.add(delta)
                    if (stillHere()) chatPendingAnswer = speaker.shown.ifEmpty { null }
                }
            }
            val work = async(Dispatchers.Default) {
                try {
                    produce { delta -> deltas.trySend(delta) }
                } finally {
                    deltas.close()
                }
            }
            val answer = try {
                work.await()
            } catch (e: CancellationException) {
                cancelRequest()
                throw e
            }
            shown.join()
            // The model failed, or ran out of time, after writing part of it:
            // the failure is said after what was said, and what was said is
            // kept as the app's words, not as an answer (left out of Save chat
            // and Share; the history the AI is told keeps the failure).
            val failedPartWay = speaker.streamedAny && answer == context.getString(R.string.error_local_failed)
            speaker.finish(answer, failedPartWay)
            // Unless cancelled, or the chat has started over meanwhile.
            if (answer != OpenAiTransport.CANCELLED && chatsCleared == chatAtStart) {
                if (failedPartWay) {
                    chat.system(listOf(speaker.shown, answer).filter { it.isNotBlank() }.joinToString("\n\n"))
                } else {
                    chat.model(answer)
                }
            }
            answer
        } finally {
            StreamLog.i { "$mode: ${speaker.logLine()}" }
            if (current()) {
                answerSpeaking = false
                chatPendingAnswer = null
                answerJob = null
            }
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
        /** An AI answer, spoken as it is written (issue #24), then kept whole. */
        suspend fun streamReply(
            mode: String,
            interruptReader: Boolean,
            cancelRequest: () -> Unit,
            produce: suspend (onDelta: (String) -> Unit) -> String,
        ) {
            writeQuestion()
            streamAnswer(mode, interruptReader, cancelRequest, produce)
        }
        try {
            when (activeMode) {
                "Image chat" -> {
                    if (!llmChatEnabled) {
                        reply(context.getString(R.string.spoken_llm_off), flush = true, system = true)
                        return
                    }
                    if (imageScanned) {
                        streamReply("Image chat", false, imageChatViewModel::cancelActiveRequest) { onDelta ->
                            imageChatViewModel.askFollowUp(command, onDelta)
                        }
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
                            streamReply("Image chat", false, imageChatViewModel::cancelActiveRequest) { onDelta ->
                                imageChatViewModel.askFollowUp(command, onDelta)
                            }
                            touchSessionActivity()
                        }
                    } else {
                        reply(context.getString(R.string.spoken_capture_first), flush = true, system = true)
                    }
                }
                "Text chat" -> {
                    // "Read original" needs no AI: the scan is read as it was captured.
                    if (textScanned && ChatKeywords.match(command) == ChatKeywords.Keyword.READ_ORIGINAL) {
                        if (featureStillActive("Text chat")) {
                            writeQuestion()
                            readOriginal(documentChatViewModel.extractedTextForPlayback())
                        }
                        touchSessionActivity()
                        return
                    }
                    if (!llmChatEnabled) {
                        reply(context.getString(R.string.spoken_llm_off), flush = true, system = true)
                        return
                    }
                    if (textScanned) {
                        streamReply("Text chat", true, documentChatViewModel::cancelActiveRequest) { onDelta ->
                            documentChatViewModel.askFollowUp(command, onDelta)
                        }
                        touchSessionActivity()
                    } else if (documentChatProcessing) {
                        // The page just captured is still being read or summarised.
                        // As in Image chat, the question replaces that answer: wait
                        // for the page's text, drop the summary, and answer against
                        // the page. It used to start a new scan instead.
                        val ready = withTimeoutOrNull(CAPTURE_ENCODE_TIMEOUT_MS) {
                            while (!documentChatViewModel.hasCachedDocument() && documentChatProcessing) delay(50)
                            documentChatViewModel.hasCachedDocument()
                        }
                        if (ready != true) {
                            reply(context.getString(R.string.spoken_capture_text_first), flush = true, system = true)
                        } else {
                            documentChatViewModel.cancelActiveRequest()
                            textScanned = true
                            streamReply("Text chat", true, documentChatViewModel::cancelActiveRequest) { onDelta ->
                                documentChatViewModel.askFollowUp(command, onDelta)
                            }
                            touchSessionActivity()
                        }
                    } else {
                        reply(context.getString(R.string.spoken_capture_text_first), flush = true, system = true)
                    }
                }
            }
        } finally {
            chatPendingQuestion = null
        }
    }

    /**
     * Ask with no picture to ask about and Automatic capture off, so Ask does
     * not take one itself. Says so ("Please capture the text first." in Text
     * chat), and nothing is recorded or taken. True when it did.
     */
    fun askNeedsCaptureFirst(mode: String?): Boolean {
        if (mode == null || !modeNeedsCapture(mode)) return false
        if (autoCaptureEnabled) return false
        cutAllAudio()
        ttsService.speak(
            context.getString(
                if (mode == ModeNames.TEXT_CHAT) R.string.spoken_capture_text_first else R.string.spoken_capture_first,
            ),
            flush = true,
        )
        return true
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
            } else if (askNeedsCaptureFirst(activeMode)) {
                // Said to capture first; nothing is recorded.
            } else if (!ensurePermission(Manifest.permission.RECORD_AUDIO)) {
                // Asked; the user presses Ask again once it is allowed.
            } else if (modeNeedsCapture(activeMode ?: "") && !ensurePermission(Manifest.permission.CAMERA)) {
                // A chat question takes a picture.
            } else {
                val mode = activeMode
                cutAllAudio()
                if (mode != null && modeNeedsCapture(mode)) armMicFirst(mode)
                cueThenListen()
                if (mode == ModeNames.IMAGE_CHAT || mode == ModeNames.TEXT_CHAT) {
                    chatPendingQuestion = SPEAKING_PLACEHOLDER
                }
            }
        }
    }

    /** The keyboard is up, which hides Capture and Ask; reported by the screen. */
    private var keyboardUp = false

    /** Something is typed in the text box, which makes the Ask button Send. */
    private var draftTyped = false

    /** The screen's text box: what it does to the buttons the volume keys stand for. */
    fun onTypingChanged(keyboardUp: Boolean, draftTyped: Boolean) {
        this.keyboardUp = keyboardUp
        this.draftTyped = draftTyped
    }

    /**
     * What a volume key does now (Settings, off by default): up is Capture and
     * down is Ask, while that button is on screen, with nothing over it.
     */
    fun volumeKeyAction(key: VolumeKeys.Key): VolumeKeys.Action? {
        val shown = VolumeKeys.buttonsOnScreen(activeMode, llmChatEnabled, keyboardUp, draftTyped)
        return VolumeKeys.actionFor(
            key = key,
            enabled = settingsManager.volumeKeys.value,
            inFront = featuresInFront,
            hasCapture = shown.capture,
            hasAsk = shown.ask,
        )
    }

    /** A volume key let go: exactly the button it stands for. */
    fun onVolumeKey(action: VolumeKeys.Action) {
        when (action) {
            VolumeKeys.Action.CAPTURE -> onChatCapture()
            VolumeKeys.Action.ASK -> onAskTapped()
        }
    }

    /** A feature chosen on the feature bar, by a tap or a swipe. */
    fun selectMode(mode: String) {
        // Every feature looks through the camera.
        if (activeMode != mode && !hasCameraPermission) ensurePermission(Manifest.permission.CAMERA)
        if (activeMode != mode) {
            cutAllAudio()
            // A question still being transcribed belongs to the feature left.
            voiceInputService.discardPendingTranscript()
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

    /**
     * A feature an app shortcut asked for. The feature bar goes there as a tap
     * on its tab does (HomeScreen), then clears it.
     */
    var requestedFeature by mutableStateOf(initialFeature)

    /**
     * An app shortcut chose [mode], at launch or with the app running. The
     * screens and the feature's own pop-ups over the features close, so the
     * feature shows; offers waiting for an answer (a download, the language)
     * stay over it.
     */
    fun openFeatureFromShortcut(mode: String) {
        if (showHelp) closeHelp()
        showObjectPicker = false
        showMemories = false
        showSettings = false
        requestedFeature = mode
    }

    fun onFeatureRequestHandled() {
        requestedFeature = null
    }

    /** The shortcuts the launcher has, so an unchanged list is not sent again. */
    private var publishedShortcuts: List<String>? = null

    /**
     * The launcher's long-press menu: the first features on the bar (see
     * AppShortcuts), named in the app's language. Called whenever the features
     * show, so one switched off in Settings leaves the menu.
     */
    fun publishShortcuts(pages: List<String>) {
        val offered = AppShortcuts.offered(pages, ShortcutManagerCompat.getMaxShortcutCountPerActivity(context))
        if (offered == publishedShortcuts) return
        val shortcuts = offered.mapIndexed { rank, mode ->
            ShortcutInfoCompat.Builder(context, AppShortcuts.id(mode))
                .setShortLabel(ModeNames.display(context, mode))
                .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                .setIntent(
                    Intent(Intent.ACTION_VIEW, null, context, MainActivity::class.java)
                        .putExtra(AppShortcuts.EXTRA_FEATURE, mode)
                        // Running already: back to that screen, not a second one.
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                )
                .setRank(rank)
                .build()
        }
        // Refused while the launcher limits updates: the next time the features show tries again.
        val published = runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) }
            .getOrDefault(false)
        if (published) publishedShortcuts = offered
    }

    /**
     * Capture in Image chat or Text chat: a new picture, at once, starting a new
     * session. In Text chat it reads everything in view; while Find page is
     * guiding, it takes the picture now, whole, and the guidance stops.
     */
    fun onChatCapture() {
        val now = SystemClock.elapsedRealtime()
        if (CaptureGate.debounced(now, lastCapturePressMs)) return
        lastCapturePressMs = now
        val press = CaptureGate.press(
            activeMode,
            imageChatBusy = imageChatBusy,
            textGuiding = textGuideActive || textGuideStarting,
            textPictureComing = triggerScan || photoInProgress || captureForQuestion,
        )
        // Image chat with a picture or an answer on its way: the press would
        // cancel it and start again. The button says it is busy instead. Text
        // chat with its picture on its way: the press would take a second one.
        if (press == CaptureGate.Press.IGNORED) return
        if (!ensurePermission(Manifest.permission.CAMERA)) return
        if (press == CaptureGate.Press.TAKE_NOW) {
            stopTextGuidance()
            cutAllAudio()
            captureGuided = false
            // Guiding for a question: the picture, whole, is that question's.
            if (micFirstActive && micFirstMode == ModeNames.TEXT_CHAT) {
                captureForQuestion = true
                return
            }
            triggerScan = true
            return
        }
        startNewChatSession()
        captureGuided = false
        triggerScan = true
    }

    /** When Capture was last pressed, for [CaptureGate.debounced]; null before any press. */
    private var lastCapturePressMs: Long? = null

    /**
     * Text chat's picture for [triggerScan]: Find page's (or Capture pressed
     * while it guides, the photo stream bound already) a full-size photo, its
     * shutter when the photo is taken (capturedPicture). Capture's, the live
     * frame in hand (1440x1920), its shutter at once: no wait for the camera
     * to switch to photos, so the sound is the picture (decided 2026-10-31).
     * Blocking: off the main thread.
     */
    private suspend fun textPicture(frame: ImageProxy, guided: Boolean): Bitmap {
        if (guided || stillsWanted) return capturedPicture(frame)
        withContext(Dispatchers.Main) { cues.give(Cues.Cue.SHUTTER) }
        return imageProxyToBitmap(frame).also { Log.i(PageCornerModel.TAG, "capture from the live frame ${it.width}x${it.height}") }
    }

    /**
     * Find page in Text chat: a new session, and the guidance finds the page
     * and takes its picture. Pressed again while it guides, nothing happens.
     */
    fun onFindPage() {
        if (activeMode != ModeNames.TEXT_CHAT) return
        if (CaptureGate.findPage(guiding = textGuideActive || textGuideStarting) == CaptureGate.Press.IGNORED) return
        if (!ensurePermission(Manifest.permission.CAMERA)) return
        startNewChatSession()
        // Bound while "Looking for text" is said, ready for the picture.
        wantStills(true)
        startTextGuidance()
    }

    /** A new picture starts a new chat: what was said, asked or read before is let go. */
    private fun startNewChatSession() {
        clearChat()
        autoCaptureNote = null
        // A new picture starts a new session, so whatever is being
        // said about the old one is cut rather than left to finish
        // over the top of the new answer.
        cutAllAudio()
        // The chat starts over: a question still being transcribed belonged to
        // the old one, so it is dropped too (decided 2026-10-24).
        voiceInputService.discardPendingTranscript()
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
    }

    // Voice command handler — only after permissions and welcome are done
    suspend fun collectCommands() {
        if (!appInForeground || showTerms || onboardingBlocking) {
            return
        }
        voiceInputService.transcripts.collectLatest { command ->
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

        // Text chat reads its captures from a full-size photo, whose stream is
        // bound only while a capture is coming (wantStills).
        val textChat = activeMode == ModeNames.TEXT_CHAT
        if (!textChat) stillsWanted = false
        cameraXManager.setAnalysisResolution(
            if (textChat) CameraXManager.TEXT_ANALYSIS_SIZE else CameraXManager.DEFAULT_ANALYSIS_SIZE,
            stills = textChat && stillsWanted,
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
                // A name is said only once seen in several frames in a row, at
                // Discover's own scores (issue #14); Find objects is untouched.
                val discoverConfirm = DiscoverConfirm()
                cameraXManager.frameFlow.collectLatest { proxy ->
                    try {
                        val (detections) = withContext(Dispatchers.Default) {
                            val bitmap = imageProxyToBitmap(proxy)
                            val dets = objectAnalyzer.analyze(bitmap)
                            bitmap.recycle()
                            Pair(dets, null)
                        }
                        discoverConfirm.next(detections.map { it.label to it.confidence })?.let {
                            ttsService.speakWithCooldown(CocoFinnish.displayName(it))
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
                                DetectionRules.findTarget(detections, findObjectTarget) { it.label }
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
                                    // approaches the middle, and a pulse that
                                    // quickens, as Sound effects and Vibration say;
                                    // never silent (FeedbackOutputs.pitchGuidance).
                                    // Nothing is said, so the user can sweep the
                                    // phone and hear it rise.
                                    cueBeat.reset()
                                    cues.guide(proximity)
                                } else {
                                    // Pitch feedback switched off while it ran.
                                    cues.stopGuidance()
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
                            cues.give(Cues.Cue.SHUTTER)
                            try {
                                val bitmap = withContext(Dispatchers.Default) { imageProxyToBitmap(proxy) }
                                try {
                                    // A question asked before the description began waits
                                    // for this picture, which processImage keeps: then it
                                    // runs unsaid, as the description would talk over the
                                    // answer. One asked during it replaces it (streamAnswer).
                                    if (questionSupersededCapture) {
                                        withContext(Dispatchers.Default) { imageChatViewModel.processImage(bitmap) }
                                    } else {
                                        // Spoken as it is written, like an answer (issue #24).
                                        streamAnswer(
                                            "Image chat",
                                            interruptReader = false,
                                            cancelRequest = imageChatViewModel::cancelActiveRequest,
                                        ) { onDelta -> imageChatViewModel.processImage(bitmap, onDelta = onDelta) }
                                    }
                                } finally {
                                    bitmap.recycle()
                                }
                            } finally {
                                captureInFlight = false
                            }
                            imageScanned = true
                            questionSupersededCapture = false
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
                val cornerGuide = PageCornerGuide()
                val pageLock = PageLock()
                // Loaded now, so the first frame of guidance does not wait for it.
                if (pageCornersOn()) scope.launch(Dispatchers.Default) { pageCornerModel.warmUp() }
                var guiding = false
                clearLiveText()

                // Says a step of the guidance.
                fun sayAimStep(step: PageCornerGuide.Step) {
                    when (step) {
                        PageCornerGuide.Step.Silent -> Unit
                        PageCornerGuide.Step.NoText ->
                            systemMessage(context.getString(R.string.spoken_no_text_visible), flush = false)
                        is PageCornerGuide.Step.Cue -> directionCues.say(
                            when (step.direction) {
                                PageCornerGuide.Direction.CLOSER -> DirectionCues.Cue.CLOSER
                                PageCornerGuide.Direction.CENTRE -> DirectionCues.Cue.HOLD_STEADY
                            }
                        )
                        PageCornerGuide.Step.Capture -> {
                            textGuideActive = false
                            captureGuidedText()
                        }
                    }
                }

                cameraXManager.frameFlow.collectLatest { proxy ->
                    // No capture coming (any just taken is read by now): the photo stream goes.
                    if (stillsWanted && !stillsNeeded()) wantStills(false)
                    // Each run of the guidance starts from nothing.
                    val wanted = !triggerScan && textAimGuidanceWanted()
                    // The tone plays only while the guidance does, and not under a pop-up.
                    if (!wanted || popupOpen || !pageCornersOn()) stopPageTone()
                    if (wanted && !guiding) {
                        cornerGuide.reset()
                        pageLock.reset()
                    }
                    guiding = wanted
                    try {
                        if (captureForQuestion) {
                            // Ask before any page, with Automatic capture: the whole
                            // view, or the page Find page's guidance found, kept for
                            // the pairing to read and answer from.
                            captureForQuestion = false
                            val guided = questionPictureGuided
                            questionPictureGuided = false
                            stopTextGuidance()
                            cornerGuide.reset()
                            pageLock.reset()
                            clearLiveText()
                            try {
                                pendingCaptureBitmap = withContext(Dispatchers.Default) {
                                    val picture = capturedPicture(proxy)
                                    if (guided) pageOf(picture) else picture
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                // Said, not left hanging: a guided question has no watchdog,
                                // as the guidance has no time limit.
                                Log.e("SightBuddyApp", "Picture for a question failed", e)
                                val unanswered = chatPendingQuestion
                                cancelMicFirst()
                                autoCaptureNote = null
                                if (!unanswered.isNullOrBlank() && unanswered != SPEAKING_PLACEHOLDER) chat.user(unanswered)
                                systemMessage(context.getString(R.string.spoken_capture_failed))
                            }
                        } else if (!triggerScan && textAimGuidanceWanted() && !pageCornersOn()) {
                            // No model to guide by: the picture at once, read whole.
                            textGuideActive = false
                            captureGuidedText()
                        } else if (!triggerScan && textAimGuidanceWanted()) {
                            clearLiveText()
                            // Find page by the page's four corners: nothing is read
                            // while aiming, only the page's outline found. No
                            // directions: a tone rises as more of the page is found.
                            val found = withContext(Dispatchers.Default) {
                                val bitmap = imageProxyToBitmap(proxy, maxLongSide = PageCorners.SOURCE_LONG_SIDE)
                                try {
                                    OcrDebug.maybeSaveFrame(bitmap)
                                    pageCornerModel.detect(bitmap)
                                } finally {
                                    bitmap.recycle()
                                }
                            }
                            // Null when the model could not run; after a few in a row it
                            // gives up, and the next frame takes the picture at once.
                            if (found != null && textAimGuidanceWanted()) {
                                val now = System.currentTimeMillis()
                                // One page of an open book, not whichever is best this frame.
                                val page = pageLock.update(found.candidates, now)
                                // One page of an open book seen whole, as the picture will be cropped.
                                val step = cornerGuide.next(PageCorners.oneOfSpread(page), now)
                                if (step == PageCornerGuide.Step.Capture) {
                                    steadyPage = page
                                    steadyPageAtMs = now
                                }
                                Log.i(
                                    PageCornerModel.TAG,
                                    "aim frameMs=${found.frameMs} modelMs=${found.modelMs} best ${PageCorners.summary(found.corners)} " +
                                        "lock=${pageLock.mode} page ${PageCorners.summary(page)} seen ${PageCorners.positions(page)} " +
                                        "tone=${"%.2f".format(Locale.US, cornerGuide.found)} step=$step",
                                )
                                if (step != PageCornerGuide.Step.Capture && !popupOpen) {
                                    cues.guide(cornerGuide.found)
                                    pageToneOn = true
                                } else {
                                    stopPageTone()
                                }
                                if (OcrDebug.enabled) {
                                    OcrDebug.corners("aim", step.toString(), page, found.frameMs, PageCorners.areaShare(page))
                                }
                                sayAimStep(step)
                            }
                        } else if (!triggerScan && liveTextWanted()) {
                            // The text in view is read out once each time it changes,
                            // Preview on or off. With Preview on it is also shown over
                            // the camera where it sits, like Google Lens, to read with
                            // TalkBack or copy. It changes only for new text, so a
                            // selection or TalkBack's place is not lost as the hand moves.
                            // A box pinned before Preview went off is let go: nothing is
                            // drawn now to let go of it by, and the reading would wait.
                            if (pinnedLiveText != null && !liveTextDrawn()) resumeLiveText()
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
                            cornerGuide.reset()
                            pageLock.reset()
                            stopPageTone()
                            triggerScan = false
                            // Find page's picture reads its page; Capture's, everything in view.
                            val guided = captureGuided
                            captureGuided = false
                            clearLiveText()

                            if (effectiveTextPreviewEnabled) {
                                val extractedText = withContext(Dispatchers.Default) {
                                    val bitmap = textPicture(proxy, guided)
                                    try {
                                        readCaptured(bitmap, crop = guided) { image, _ ->
                                            localTextExtractor.extractText(image)
                                        }
                                    } finally {
                                        bitmap.recycle()
                                    }
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
                                val bitmap = withContext(Dispatchers.Default) { textPicture(proxy, guided) }
                                try {
                                    // Spoken as it is written, like an answer (issue #24),
                                    // and written into the chat whole at the end.
                                    val response = streamAnswer(
                                        "Text chat",
                                        interruptReader = true,
                                        cancelRequest = documentChatViewModel::cancelActiveRequest,
                                    ) { onDelta ->
                                        readCaptured(
                                            bitmap,
                                            crop = guided,
                                            chars = { documentChatViewModel.extractedTextForPlayback().length },
                                        ) { image, _ -> documentChatViewModel.processDocument(image, onDelta = onDelta) }
                                    }
                                    // Cancelled by a new Capture: the new session is left alone.
                                    if (response != OpenAiTransport.CANCELLED) {
                                        textScanned = documentChatViewModel.hasCachedDocument()
                                    }
                                } finally {
                                    bitmap.recycle()
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
                        // After the work, never during it: letting go rebinds the camera,
                        // and its new frames would cancel this block (capturedPicture).
                        if (stillsWanted && !stillsNeeded()) wantStills(false)
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
