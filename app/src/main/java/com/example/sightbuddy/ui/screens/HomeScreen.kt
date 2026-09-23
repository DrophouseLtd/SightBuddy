package com.example.sightbuddy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.key
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import com.example.sightbuddy.core.TextScriptPlayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.example.sightbuddy.ui.theme.brandPalette
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sightbuddy.R
import com.example.sightbuddy.core.ModeNames
import com.example.sightbuddy.features.chat.LiveText
import com.example.sightbuddy.ui.buttonSemantics
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import com.example.sightbuddy.ui.theme.Brand
import com.example.sightbuddy.ui.theme.BrandPalette

/** How many times the feature list repeats when looping; the user starts in the middle. */
private const val LOOP_RANGE = 200

/**
 * Text chat's Back / Play / Forward row. Hidden while the chat layout is being
 * tried out; the controls and their wiring are intact, flip this to bring them
 * back above the bottom buttons.
 */
private const val SHOW_TEXT_PLAYBACK_ROW = false

/** Features the user talks to: they get the conversation, the text box and buttons. */

/** How far a swipe at the end of the carousel travels before it counts. */
private const val EDGE_SWIPE_DP = 60

/** The bottom row is sized as if this many square buttons shared it… */
private const val BUTTON_ROW_SLOTS = 4

/** …at this share of a square's height. */
private const val BUTTON_HEIGHT_SHARE = 0.8f

/** The feature bar along the bottom, as Envision's. */
private val FEATURE_BAR_HEIGHT = 64.dp

/** Live text stays below the top bar… */
private val LIVE_TEXT_TOP_RESERVE = 96.dp

/** …and above the text bar and the buttons along the bottom. */
private val LIVE_TEXT_BOTTOM_RESERVE = 240.dp

/** Space the top bar takes on every feature. */
private val TOP_ZONE_HEIGHT = 96.dp
private const val MORE_PER_ROW = 4

/** A switch in the More row: a feature setting, shown by its title only. */
data class BarToggle(val label: String, val checked: Boolean, val onToggle: (Boolean) -> Unit)

/** A button in the More row; [checked] is null for a plain button (Save chat). */
private data class MoreItem(val label: String, val checked: Boolean?, val onClick: () -> Unit)

/**
 * The root view. Along the top: Help, Torch, Preview and Settings. Along the
 * bottom: the feature bar, which also says which feature is showing; the
 * features have no title of their own. Features the user talks to (Image chat,
 * Text chat, Find objects) are a conversation: the session's messages, a box to
 * type in, and their buttons above the feature bar.
 *
 * Preview, on every feature, makes the layer see-through so the camera shows;
 * the messages stay, faint, over it, with the box and the buttons.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    pages: List<String>,
    activeMode: String? = null,
    llmChatEnabled: Boolean = true,
    highContrast: Boolean = false,
    whiteMode: Boolean = false,
    /** The phone's light or dark theme, for the brand colours (not used in high contrast). */
    darkTheme: Boolean = true,
    loopCarousel: Boolean = false,
    torchOn: Boolean = false,
    torchAvailable: Boolean = false,
    onToggleTorch: (Boolean) -> Unit = {},
    /** The active feature's session, oldest first. */
    conversation: List<TranscriptEntry> = emptyList(),
    pendingQuestion: String? = null,
    working: Boolean = false,
    /** Something typed in the box: a question, or in Find objects an object name. */
    onSendText: (String) -> Unit = {},
    holdToSpeak: Boolean = false,
    isRecording: Boolean = false,
    onModeSelected: (String) -> Unit = {},
    onMicPressed: () -> Unit = {},
    onMicReleased: (holdMs: Long) -> Unit = {},
    onMicTapped: () -> Unit = {},
    showTextPlaybackControls: Boolean = false,
    textPlaybackEnabled: Boolean = false,
    playbackPlaying: Boolean = false,
    onPlaybackRateStep: (up: Boolean) -> Unit = {},
    onPlaybackPauseToggle: () -> Unit = {},
    onPlaybackSeekBack: () -> Unit = {},
    onPlaybackSeekForward: () -> Unit = {},
    onPlaybackRestartFromBeginning: () -> Unit = {},
    onPlaybackDisabled: () -> Unit = {},
    /** Fired before any back/forward transport tap or hold (including when disabled). */
    onPlaybackTransportInteraction: () -> Unit = {},
    onTakePicture: (() -> Unit)? = null,
    onBrowseObjects: (() -> Unit)? = null,
    onOpenSettings: () -> Unit = {},
    onOpenHelp: () -> Unit = {},
    /** Mute on the features with nothing else to press: silences their voice. */
    muted: Boolean = false,
    onToggleMute: () -> Unit = {},
    /** Previous at the first feature or Next at the last, with looping off. */
    onStepUnavailable: (forward: Boolean) -> Unit = {},
    /** Text chat's text in view, drawn over the camera while Preview is on. */
    liveText: LiveText? = null,
    /** Preview on or off, so the camera's text is read only while it can be shown. */
    onPreviewChanged: (Boolean) -> Unit = {},
    /** Preview: a setting, and the same switch on screen. */
    previewCamera: Boolean = true,
    /** The Live text setting: off, Text chat opens with a message instead. */
    liveTextOn: Boolean = false,
    onTogglePreview: (Boolean) -> Unit = {},
    /** The live text box the user pinned; see [LiveTextOverlay]. */
    liveTextPinned: LiveText? = null,
    onLiveTextPin: (LiveText.Block) -> Unit = {},
    onLiveTextResume: () -> Unit = {},
    /** Scanning features: the last thing said, shown mid-screen until the next. */
    spokenCaption: String? = null,
    onOpenMemories: () -> Unit = {},
    /** The More row under the top bar: open or not. */
    moreOpen: Boolean = false,
    onToggleMore: () -> Unit = {},
    /** Save chat in the More row; null for features without a chat. */
    onSaveChat: (() -> Unit)? = null,
    /** The active feature's own settings, as switches in the More row. */
    featureToggles: List<BarToggle> = emptyList(),
) {
    // Looping is a long virtual range rather than a jump at the ends, so a swipe
    // past the last feature carries straight on to the first instead of refusing
    // to move. Without this the setting only worked for the buttons.
    val saveChatLabel = stringResource(R.string.bar_save_chat)
    val torchLabel = stringResource(R.string.torch_short)
    val previewLabel = stringResource(R.string.btn_preview)
    val moreItems = buildList {
        onSaveChat?.let { add(MoreItem(saveChatLabel, null, it)) }
        // Runtime only: never persisted, and cleared whenever the camera unbinds.
        if (torchAvailable) add(MoreItem(torchLabel, torchOn) { onToggleTorch(!torchOn) })
        add(MoreItem(previewLabel, previewCamera) { onTogglePreview(!previewCamera) })
        featureToggles.forEach { t -> add(MoreItem(t.label, t.checked) { t.onToggle(!t.checked) }) }
    }
    val moreRows = (moreItems.size + MORE_PER_ROW - 1) / MORE_PER_ROW
    val moreRowsHeight = if (moreOpen) (56.dp + 10.dp) * moreRows else 0.dp
    val looping = loopCarousel && pages.size > 1
    val activeIndex = pages.indexOf(activeMode).coerceAtLeast(0)
    val pagerState = key(looping) {
        rememberPagerState(
            initialPage = if (looping) pages.size * (LOOP_RANGE / 2) + activeIndex else activeIndex,
            pageCount = { if (looping) pages.size * LOOP_RANGE else pages.size },
        )
    }
    fun realPage(page: Int) = if (pages.isEmpty()) 0 else page % pages.size
    val scope = rememberCoroutineScope()

    // Whether a swipe can go that way; false at an end that does not loop.
    fun canStep(forward: Boolean): Boolean {
        val current = realPage(pagerState.currentPage)
        return looping || if (forward) current < pages.size - 1 else current > 0
    }
    val stepUnavailable by androidx.compose.runtime.rememberUpdatedState(onStepUnavailable)
    // The feature bar replaces Previous and Next; hidden while typing.
    val showFeatureBar = pages.size > 1 && !WindowInsets.isImeVisible

    // Preview: the conversation layer goes see-through so the camera shows.
    LaunchedEffect(previewCamera) { onPreviewChanged(previewCamera) }

    val defaultTextColor = MaterialTheme.colorScheme.onSurface
    val palette = brandPalette(darkTheme)
    val layerColor = when {
        highContrast && whiteMode -> Color.White
        highContrast -> Color.Black
        else -> palette.wall
    }
    val onLayerColor = when {
        highContrast && whiteMode -> Color.Black
        highContrast -> Color.White
        else -> palette.ink
    }
    val textColor = if (highContrast) {
        if (whiteMode) Color.Black else Color.White
    } else {
        defaultTextColor
    }
    val accent = palette.selected
    val micPrimaryBg = when {
        highContrast && whiteMode -> Color.Black
        highContrast -> Color.White
        else -> palette.primaryBg
    }
    val micPrimaryText = when {
        highContrast && whiteMode -> Color.White
        highContrast -> Color.Black
        else -> palette.primaryText
    }
    // Bottom-row buttons: solid, so they read over the camera in Preview too.
    val primaryButtonBg = when {
        highContrast && whiteMode -> Color.Black
        highContrast -> Color.White
        else -> palette.primaryBg
    }
    val primaryButtonText = when {
        highContrast && whiteMode -> Color.White
        highContrast -> Color.Black
        else -> palette.primaryText
    }
    val secondaryButtonBg = when {
        highContrast && whiteMode -> Color.Black
        highContrast -> Color.White
        else -> palette.secondaryBg
    }
    val secondaryButtonText = if (highContrast) primaryButtonText else palette.secondaryText
    val context = LocalContext.current

    LaunchedEffect(pagerState.currentPage) {
        onModeSelected(pages[realPage(pagerState.currentPage)])
    }

    Box(modifier = modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                // A swipe past either end, looping off, says there is nothing
                // that way rather than just not moving. Watched, never consumed.
                .pointerInput(looping, pages.size) {
                    val threshold = EDGE_SWIPE_DP.dp.toPx()
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        var dx = 0f
                        var dy = 0f
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            event.changes.firstOrNull()?.let {
                                val d = it.position - it.previousPosition
                                dx += d.x
                                dy += d.y
                            }
                            if (event.changes.none { it.pressed }) break
                        }
                        if (kotlin.math.abs(dx) > threshold && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.5f) {
                            val forward = dx < 0f
                            if (!canStep(forward)) stepUnavailable(forward)
                        }
                    }
                },
            userScrollEnabled = true
        ) { page ->
            val pageTitle = pages[realPage(page)]
            val isActive = activeMode == pageTitle
            val takesInput = pageTitle in ModeNames.TAKES_INPUT
            // Every feature sits on the solid layer until Preview shows the camera.
            val solidLayer = !(isActive && previewCamera)

            // The frame itself says nothing. Touching empty space while looking
            // for a button used to read "Text chat active, swipe…" every time.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (solidLayer) layerColor else Color.Transparent)
            ) {
                // Colour scanner focus frame — marks the sampled centre region.
                // Two-tone border stays visible over any camera scene and in both
                // high-contrast themes (where it hints aim over the solid mask).
                if (isActive && pageTitle == ModeNames.SCAN_COLOUR) {
                    val frameOuter = if (highContrast && whiteMode) Color.Black else Color.White
                    val frameInner = if (highContrast && whiteMode) Color.White else Color.Black
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(130.dp)
                            .border(4.dp, frameOuter, RoundedCornerShape(14.dp))
                            .padding(4.dp)
                            .border(2.dp, frameInner, RoundedCornerShape(10.dp))
                            .clearAndSetSemantics { },
                    )
                }

                if (isActive) {
                    FeatureBody(
                        topReserve = TOP_ZONE_HEIGHT + moreRowsHeight,
                        feature = pageTitle,
                        takesInput = takesInput,
                        llmChatEnabled = llmChatEnabled,
                        showMessages = solidLayer,
                        liveTextShown = liveTextOn && previewCamera,
                        liveTextOn = liveTextOn,
                        conversation = conversation,
                        pendingQuestion = pendingQuestion,
                        working = working,
                        isRecording = isRecording,
                        holdToSpeak = holdToSpeak,
                        highContrast = highContrast,
                        onLayerColor = if (solidLayer) onLayerColor else textColor,
                        accent = accent,
                        primaryButtonBg = primaryButtonBg,
                        primaryButtonText = primaryButtonText,
                        secondaryButtonBg = secondaryButtonBg,
                        secondaryButtonText = secondaryButtonText,
                        palette = if (highContrast) null else palette,
                        bottomReserve = if (showFeatureBar) FEATURE_BAR_HEIGHT else 0.dp,
                        muted = muted,
                        onToggleMute = onToggleMute,
                        onSendText = onSendText,
                        onAsk = onMicTapped,
                        onTakePicture = onTakePicture,
                        onBrowseObjects = onBrowseObjects,
                        playbackRow = {
                            if (SHOW_TEXT_PLAYBACK_ROW && showTextPlaybackControls && pageTitle == ModeNames.TEXT_CHAT) {
                                PlaybackRow(
                                    enabled = textPlaybackEnabled,
                                    playing = playbackPlaying,
                                    background = micPrimaryBg,
                                    textColor = micPrimaryText,
                                    onRateStep = onPlaybackRateStep,
                                    onPauseToggle = onPlaybackPauseToggle,
                                    onSeekBack = onPlaybackSeekBack,
                                    onSeekForward = onPlaybackSeekForward,
                                    onRestart = onPlaybackRestartFromBeginning,
                                    onDisabled = onPlaybackDisabled,
                                    onInteraction = onPlaybackTransportInteraction,
                                )
                            }
                        },
                    )
                }

                // The scanning features show what they last said, in the middle of
                // the screen, until they say something else or the feature is left.
                // Scan Colour keeps its frame clear and shows it just below.
                if (isActive && pageTitle in ModeNames.SCANNING && spokenCaption != null) {
                    // High contrast: solid, in its own colours; otherwise dark on a
                    // pale box, as the live text in Text chat.
                    val captionBg = when {
                        highContrast && whiteMode -> Color.White
                        highContrast -> Color.Black
                        else -> Color.White.copy(alpha = 0.75f)
                    }
                    val captionText = if (highContrast && !whiteMode) Color.White else Color.Black
                    Text(
                        text = spokenCaption,
                        color = captionText,
                        fontSize = 26.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(y = if (pageTitle == ModeNames.SCAN_COLOUR) 120.dp else 0.dp)
                            .padding(horizontal = 32.dp)
                            .background(captionBg, RoundedCornerShape(6.dp))
                            .then(
                                if (highContrast) Modifier.border(2.dp, captionText, RoundedCornerShape(6.dp))
                                else Modifier
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }

                // Live text, over the camera, clear of the buttons above and below.
                if (isActive && previewCamera && pageTitle == ModeNames.TEXT_CHAT && (liveText != null || liveTextPinned != null)) {
                    LiveTextOverlay(
                        live = liveText,
                        topReserve = LIVE_TEXT_TOP_RESERVE,
                        bottomReserve = LIVE_TEXT_BOTTOM_RESERVE,
                        pinned = liveTextPinned,
                        onPin = onLiveTextPin,
                        onResume = onLiveTextResume,
                    )
                }
            }
        }

        // --- Feature bar: every feature, one tap away, as in Envision ---
        if (showFeatureBar) {
            FeatureBar(
                pages = pages,
                current = realPage(pagerState.currentPage),
                highContrast = highContrast,
                background = layerColor,
                textColor = onLayerColor,
                accent = accent,
                onSelect = { target ->
                    // The nearest page showing that feature, looping or not.
                    val from = pagerState.currentPage
                    val to = from + (target - realPage(from))
                    scope.launch { pagerState.animateScrollToPage(to) }
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        // --- Top bar: Help, Torch, Preview, Settings, left to right ---
        // Square-cornered boxes sharing the width, as the feature bar does, on no
        // background of their own. The features have no title: the feature bar
        // says which one is showing.
        val chipBg = when {
            highContrast && whiteMode -> Color.Black
            highContrast -> Color.White
            previewCamera -> Color.Black.copy(alpha = 0.55f)
            else -> palette.surface
        }
        val chipText = when {
            highContrast && whiteMode -> Color.White
            highContrast -> Color.Black
            previewCamera -> Color.White
            else -> palette.ink
        }
        val onBg = when {
            highContrast && whiteMode -> Color.Black
            highContrast -> Color.White
            else -> Brand.Mint
        }
        val onText = when {
            highContrast && whiteMode -> Color.White
            highContrast -> Color.Black
            else -> Color.Black
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 24.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TopBarButton(
                label = stringResource(R.string.btn_help),
                background = chipBg,
                textColor = chipText,
                modifier = Modifier.weight(1f),
                onClick = onOpenHelp,
            )
            TopBarButton(
                label = stringResource(R.string.btn_memories),
                background = chipBg,
                textColor = chipText,
                modifier = Modifier.weight(1f),
                onClick = onOpenMemories,
            )
            TopBarButton(
                label = stringResource(R.string.btn_more),
                background = if (moreOpen) onBg else chipBg,
                textColor = if (moreOpen) onText else chipText,
                modifier = Modifier.weight(1f),
                expanded = moreOpen,
                onClick = onToggleMore,
            )
            TopBarButton(
                label = stringResource(R.string.btn_settings),
                background = chipBg,
                textColor = chipText,
                modifier = Modifier.weight(1f),
                onClick = onOpenSettings,
            )
        }

        // More: a second row of the same buttons, titles only. Save chat, Torch,
        // Preview, then the feature's own settings, the same switches as in
        // Settings. Four to a row, like the bar above.
        if (moreOpen) {
            FlowRow(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = TOP_ZONE_HEIGHT - 6.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                maxItemsInEachRow = MORE_PER_ROW,
            ) {
                moreItems.forEach { item ->
                    val on = item.checked == true
                    TopBarButton(
                        label = item.label,
                        background = if (on) onBg else chipBg,
                        textColor = if (on) onText else chipText,
                        modifier = Modifier.weight(1f),
                        toggled = item.checked,
                        onClick = item.onClick,
                    )
                }
                // Empty cells keep the last row's buttons the width of the others.
                repeat((MORE_PER_ROW - moreItems.size % MORE_PER_ROW) % MORE_PER_ROW) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * A top-bar button: a square-cornered box with its word. The same three kinds
 * as in Settings, read the Android way:
 * - [toggled] makes it a switch ("Off. Torch. Switch"; after a tap, "On");
 * - [expanded] makes it a button that opens something below it ("More, Button,
 *   Collapsed"; after a tap, "Expanded");
 * - otherwise a plain button.
 */
@Composable
private fun TopBarButton(
    label: String,
    background: Color,
    textColor: Color,
    modifier: Modifier,
    toggled: Boolean? = null,
    expanded: Boolean? = null,
    onClick: () -> Unit,
) {
    val expandedState = expanded?.let {
        stringResource(if (it) R.string.state_expanded else R.string.state_collapsed)
    }
    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .then(
                when {
                    toggled != null -> Modifier
                        .toggleable(value = toggled, role = Role.Switch, onValueChange = { onClick() })
                        .clearAndSetSemantics {
                            contentDescription = label
                            role = Role.Switch
                            toggleableState = ToggleableState(toggled)
                            onClick { onClick(); true }
                        }
                    expandedState != null -> Modifier
                        .clickable(onClick = onClick)
                        .clearAndSetSemantics {
                            contentDescription = label
                            role = Role.Button
                            stateDescription = expandedState
                            onClick { onClick(); true }
                        }
                    else -> Modifier
                        .clickable(onClick = onClick)
                        .buttonSemantics(label)
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = TextStyle(
                color = textColor,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(
                minFontSize = 10.sp,
                maxFontSize = 15.sp,
            ),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

/**
 * Everything between the top bar and the feature bar for the active feature:
 * the conversation, the text box and its buttons for features the user talks
 * to; Mute for the rest.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoxScope.FeatureBody(
    topReserve: Dp,
    feature: String,
    takesInput: Boolean,
    llmChatEnabled: Boolean,
    showMessages: Boolean,
    liveTextShown: Boolean,
    liveTextOn: Boolean,
    conversation: List<TranscriptEntry>,
    pendingQuestion: String?,
    working: Boolean,
    isRecording: Boolean,
    holdToSpeak: Boolean,
    highContrast: Boolean,
    onLayerColor: Color,
    accent: Color,
    primaryButtonBg: Color,
    primaryButtonText: Color,
    secondaryButtonBg: Color,
    secondaryButtonText: Color,
    palette: BrandPalette?,
    /** Room kept free at the bottom for the feature bar. */
    bottomReserve: Dp,
    muted: Boolean,
    onToggleMute: () -> Unit,
    onSendText: (String) -> Unit,
    onAsk: () -> Unit,
    onTakePicture: (() -> Unit)?,
    onBrowseObjects: (() -> Unit)?,
    playbackRow: @Composable () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val imeVisible = WindowInsets.isImeVisible
    var draft by remember(feature) { mutableStateOf("") }
    val scroll = rememberScrollState()
    val isChat = feature == ModeNames.IMAGE_CHAT || feature == ModeNames.TEXT_CHAT
    // Ask needs the AI in the chat features; Find objects can always listen.
    val canAsk = if (isChat) llmChatEnabled else feature == ModeNames.FIND_OBJECTS
    val canType = canAsk

    fun sendDraft() {
        val text = draft.trim()
        if (text.isEmpty()) return
        draft = ""
        keyboard?.hide()
        onSendText(text)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topReserve, start = 16.dp, end = 16.dp, bottom = 16.dp + if (imeVisible) 0.dp else bottomReserve)
            // Room for the keyboard, without counting the navigation bar twice.
            .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars)),
    ) {
        if (takesInput) {
            ChatMessages(
                entries = conversation,
                pendingQuestion = pendingQuestion,
                working = working,
                // Text chat has none while live text shows: TalkBack met it
                // together with the live text over the camera, and lost its
                // place when it went.
                emptyText = when {
                    feature == ModeNames.TEXT_CHAT && liveTextShown -> null
                    feature == ModeNames.TEXT_CHAT && !liveTextOn -> stringResource(R.string.chat_empty_text_live_off)
                    feature == ModeNames.TEXT_CHAT -> stringResource(R.string.chat_empty_text)
                    feature == ModeNames.FIND_OBJECTS -> stringResource(R.string.chat_empty_find)
                    else -> stringResource(if (llmChatEnabled) R.string.chat_empty_ai else R.string.chat_empty_text)
                },
                textColor = onLayerColor,
                accent = accent,
                highContrast = highContrast,
                // Over the camera in Preview: faint, cloudy bubbles. Seeing them is
                // secondary; the screen reader reads them either way.
                faint = !showMessages,
                palette = palette,
                scroll = scroll,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        playbackRow()

        if (takesInput && canType) {
            Spacer(modifier = Modifier.height(12.dp))
            // A rounded bar, as in ChatGPT, with Send inside it once something is
            // typed. It sits right on the keyboard while typing.
            val pillShape = RoundedCornerShape(28.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clip(pillShape)
                    .background(
                        when {
                            // Solid in high contrast, over the camera too.
                            highContrast -> if (onLayerColor == Color.Black) Color.White else Color.Black
                            showMessages && palette != null -> palette.surface
                            showMessages -> Color.White.copy(alpha = 0.10f)
                            else -> Color.Black.copy(alpha = 0.55f)
                        }
                    )
                    .then(
                        when {
                            highContrast -> Modifier.border(2.dp, onLayerColor, pillShape)
                            showMessages && palette != null -> Modifier.border(1.dp, palette.edge, pillShape)
                            else -> Modifier
                        }
                    )
                    .padding(start = 20.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier
                        .weight(1f)
                        // No separate label: TalkBack reads the visible hint, once.
                        .padding(vertical = 10.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = onLayerColor, fontSize = 18.sp),
                    cursorBrush = SolidColor(onLayerColor),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { sendDraft() }),
                    decorationBox = { inner ->
                        Box {
                            if (draft.isEmpty()) {
                                Text(
                                    stringResource(
                                        if (feature == ModeNames.FIND_OBJECTS) R.string.chat_input_find_hint else R.string.chat_input_hint
                                    ),
                                    color = onLayerColor.copy(alpha = 0.6f),
                                    fontSize = 18.sp,
                                )
                            }
                            inner()
                        }
                    },
                )
                if (draft.isNotBlank()) {
                    val sendLabel = stringResource(R.string.btn_send)
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(44.dp)
                            .background(primaryButtonBg, CircleShape)
                            .clickable { sendDraft() }
                            .buttonSemantics(sendLabel),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("↑", color = primaryButtonText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Bottom row: square buttons sharing the width evenly, however many there
        // are. Previous and Next join it when button navigation is on.
        // The feature's own buttons; Previous and Next are laid out around them.
        val buttons = buildList {
            if (isChat && onTakePicture != null) {
                add(BottomButton(stringResource(R.string.btn_capture)) {
                    keyboard?.hide()
                    onTakePicture()
                })
            }
            if (feature == ModeNames.FIND_OBJECTS && onBrowseObjects != null) {
                add(BottomButton(stringResource(R.string.btn_objects)) { onBrowseObjects() })
            }
            if (takesInput && canAsk) {
                // Sends what is typed; with nothing typed it records, as Ask
                // always has. On screen it becomes Send while it records; to
                // TalkBack it stays Ask, because a name that changes under the
                // focus is read out again — the button said "Ask" at the end of
                // every recording. The recording has its own cues.
                val sending = draft.isNotBlank()
                val recording = isRecording && !holdToSpeak
                val askLabel = stringResource(if (sending || recording) R.string.btn_send else R.string.btn_ask)
                val askSpoken = stringResource(if (sending) R.string.btn_send else R.string.btn_ask)
                add(BottomButton(askLabel, primary = true, spokenLabel = askSpoken) { if (sending) sendDraft() else onAsk() })
            }
            // Every feature has at least one button. Those that only talk get Mute.
            if (!takesInput) {
                add(
                    BottomButton(
                        stringResource(if (muted) R.string.btn_unmute else R.string.btn_mute),
                        primary = true,
                    ) { onToggleMute() }
                )
            }
        }
        // At an end with looping off they stay, marked unavailable, and say so.
        // Hidden while typing, like any chat app: the text box sits on the keyboard.
        if (buttons.isNotEmpty() && !imeVisible) {
            Spacer(modifier = Modifier.height(12.dp))
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                // One height on every feature, a little under a quarter of the
                // width; fewer buttons grow wider, not taller.
                val gap = 10.dp
                val height = (maxWidth - gap * (BUTTON_ROW_SLOTS - 1)) / BUTTON_ROW_SLOTS * BUTTON_HEIGHT_SHARE
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    buttons.forEach { button ->
                        SquareButton(
                            label = button.label,
                            background = if (button.primary) primaryButtonBg else secondaryButtonBg,
                            textColor = if (button.primary) primaryButtonText else secondaryButtonText,
                            modifier = Modifier
                                .weight(1f)
                                .height(height),
                            spokenLabel = button.spokenLabel,
                            onClick = button.onClick,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Every feature along the bottom, names only, sharing the width evenly (six at
 * most, wider when fewer are switched on). The current one is marked; tapping
 * another goes there.
 */
@Composable
private fun FeatureBar(
    pages: List<String>,
    current: Int,
    highContrast: Boolean,
    background: Color,
    textColor: Color,
    accent: Color,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Each tab says its place in its name ("Find objects, 4 of 4. Tab"). The
    // bar is not a collection for TalkBack: declared as one, TalkBack on the
    // phones tested said no place at all; left to Compose's selectable group,
    // it said it twice. The selected tab carries `selected`: for a tab, Compose reports
    // a change of it as a new selection, and TalkBack moves its focus there and
    // reads it, so a swipe to another feature says "Text chat, 1 of 3". A tap
    // reads the tab again the same way; the user accepted that repeat. Tried and
    // dropped: the selection as a state (a swipe said only "Selected"), and a
    // pane title per page (TalkBack said "Sight Buddy", the window's name).
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(FEATURE_BAR_HEIGHT)
            .background(background)
            .border(width = 1.dp, color = textColor.copy(alpha = 0.25f)),
    ) {
        pages.forEachIndexed { index, page ->
            val selected = index == current
            val name = ModeNames.display(context, page)
            val spoken = stringResource(R.string.feature_position, name, index + 1, pages.size)
            val mark = when {
                highContrast -> textColor
                else -> accent
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(role = Role.Tab) { onSelect(index) }
                    .clearAndSetSemantics {
                        contentDescription = spoken
                        role = Role.Tab
                        this.selected = selected
                        onClick { onSelect(index); true }
                    }
                    .padding(horizontal = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = name,
                    style = TextStyle(
                        color = if (selected) mark else textColor.copy(alpha = 0.85f),
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    ),
                    maxLines = 2,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = 10.sp,
                        maxFontSize = 15.sp,
                    ),
                )
                if (selected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(0.6f)
                            .height(3.dp)
                            .background(mark),
                    )
                }
            }
        }
    }
}

private class BottomButton(
    val label: String,
    val primary: Boolean = false,
    /** What TalkBack calls it, when that is not [label]; see SquareButton. */
    val spokenLabel: String = label,
    val onClick: () -> Unit,
)

/** Text chat's Back · Play · Forward, kept for when SHOW_TEXT_PLAYBACK_ROW returns. */
@Composable
private fun PlaybackRow(
    enabled: Boolean,
    playing: Boolean,
    background: Color,
    textColor: Color,
    onRateStep: (up: Boolean) -> Unit,
    onPauseToggle: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onRestart: () -> Unit,
    onDisabled: () -> Unit,
    onInteraction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        PlaybackTransportButton(
            label = stringResource(R.string.btn_back),
            contentDescription = stringResource(R.string.cd_playback_back),
            enabled = enabled,
            background = background,
            textColor = textColor,
            holdThresholdMs = TextScriptPlayer.HOLD_BACK_RESTART_MS,
            onHoldTriggered = { onInteraction(); onRateStep(false) },
            onShortRelease = { onInteraction(); onSeekBack() },
            onDisabledInteraction = { onInteraction(); onDisabled() },
        )
        Spacer(modifier = Modifier.width(12.dp))
        PlaybackTransportButton(
            label = stringResource(if (playing) R.string.btn_pause else R.string.btn_play),
            contentDescription = stringResource(if (playing) R.string.cd_playback_pause else R.string.cd_playback_play),
            enabled = enabled,
            background = background,
            textColor = textColor,
            holdThresholdMs = TextScriptPlayer.HOLD_BACK_RESTART_MS,
            onHoldTriggered = { onInteraction(); onRestart() },
            onShortRelease = { onInteraction(); onPauseToggle() },
            onDisabledInteraction = { onInteraction(); onDisabled() },
        )
        Spacer(modifier = Modifier.width(12.dp))
        PlaybackTransportButton(
            label = stringResource(R.string.btn_forward),
            contentDescription = stringResource(R.string.cd_playback_forward),
            enabled = enabled,
            background = background,
            textColor = textColor,
            holdThresholdMs = TextScriptPlayer.HOLD_FORWARD_TOGGLE_MS,
            onHoldTriggered = { onInteraction(); onRateStep(true) },
            onShortRelease = { onInteraction(); onSeekForward() },
            onDisabledInteraction = { onInteraction(); onDisabled() },
        )
    }
}

@Composable
private fun PlaybackTransportButton(
    label: String,
    contentDescription: String,
    enabled: Boolean,
    background: Color,
    textColor: Color,
    holdThresholdMs: Long,
    onHoldTriggered: () -> Unit,
    onShortRelease: () -> Unit,
    onDisabledInteraction: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    Box(
        modifier = Modifier
            .size(80.dp)
            .background(background, shape = CircleShape)
            .pointerInput(enabled, holdThresholdMs) {
                detectTapGestures(
                    onPress = {
                        if (!enabled) {
                            var holdTriggered = false
                            val holdJob: Job = scope.launch {
                                delay(holdThresholdMs)
                                holdTriggered = true
                                onDisabledInteraction()
                            }
                            tryAwaitRelease()
                            holdJob.cancel()
                            if (!holdTriggered) {
                                onDisabledInteraction()
                            }
                            return@detectTapGestures
                        }
                        var holdTriggered = false
                        val holdJob: Job = scope.launch {
                            delay(holdThresholdMs)
                            holdTriggered = true
                            onHoldTriggered()
                        }
                        tryAwaitRelease()
                        holdJob.cancel()
                        if (!holdTriggered) {
                            onShortRelease()
                        }
                    },
                )
            }
            // The gestures above are invisible to TalkBack, so they are offered
            // again as actions: double tap is the short press, double tap and hold
            // is the long one.
            .clearAndSetSemantics {
                this.contentDescription = label
                role = Role.Button
                onClick {
                    if (enabled) onShortRelease() else onDisabledInteraction()
                    true
                }
                onLongClick {
                    if (enabled) onHoldTriggered() else onDisabledInteraction()
                    true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}
