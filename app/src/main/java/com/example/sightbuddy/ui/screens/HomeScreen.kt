package com.example.sightbuddy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.key
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sightbuddy.R
import com.example.sightbuddy.core.ModeNames
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How many times the feature list repeats when looping; the user starts in the middle. */
private const val LOOP_RANGE = 200

private fun splitFeatureTitle(title: String): String =
    title.split(" ").joinToString("\n")

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    pages: List<String>,
    activeMode: String? = null,
    llmChatEnabled: Boolean = true,
    highContrast: Boolean = false,
    whiteMode: Boolean = false,
    useButtonNav: Boolean = false,
    loopCarousel: Boolean = false,
    torchOn: Boolean = false,
    torchAvailable: Boolean = false,
    onToggleTorch: (Boolean) -> Unit = {},
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
) {
    // Looping is a long virtual range rather than a jump at the ends, so a swipe
    // past the last feature carries straight on to the first instead of refusing
    // to move. Without this the setting only worked for the buttons.
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

    val defaultTextColor = MaterialTheme.colorScheme.onSurface
    val maskColor = if (highContrast) {
        if (whiteMode) Color.White else Color.Black
    } else {
        Color.Transparent
    }
    val textColor = if (highContrast) {
        if (whiteMode) Color.Black else Color.White
    } else {
        defaultTextColor
    }
    val micPrimaryBg = when {
        highContrast && whiteMode -> Color.Black
        highContrast -> Color.White
        else -> Color(0xFF3DBAD0).copy(alpha = 0.8f)
    }
    val micPrimaryText = when {
        highContrast && whiteMode -> Color.White
        highContrast -> Color.Black
        else -> Color.White
    }
    val helpButtonCd = stringResource(R.string.help_button_cd)
    val context = androidx.compose.ui.platform.LocalContext.current
    val captureLabel = stringResource(R.string.btn_capture)
    val prevModeCd = stringResource(R.string.cd_previous_mode)
    val nextModeCd = stringResource(R.string.cd_next_mode)
    val openSettingsCd = stringResource(R.string.cd_open_settings)

    LaunchedEffect(pagerState.currentPage) {
        onModeSelected(pages[realPage(pagerState.currentPage)])
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Full-screen mask layer
        if (highContrast) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(maskColor)
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !useButtonNav
        ) { page ->
            val pageTitle = pages[realPage(page)]
            val isActive = activeMode == pageTitle
            val pageTitleDisplay = ModeNames.display(context, pageTitle)
            val activeModeCd = stringResource(R.string.cd_mode_active, pageTitleDisplay)
            val inactiveModeCd = stringResource(R.string.cd_mode_inactive, pageTitleDisplay)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .background(color = maskColor, shape = MaterialTheme.shapes.large)
                    .semantics {
                        contentDescription = if (isActive) {
                            activeModeCd
                        } else {
                            inactiveModeCd
                        }
                    }
            ) {
                // Title at top center
                // Kept clear of the corner buttons, and shrunk for languages whose
                // feature names are single long compounds ("Tekstikeskustelu") rather
                // than two short words ("Text chat"), which would otherwise run
                // underneath the Help and Settings buttons.
                val titleText = splitFeatureTitle(ModeNames.display(context, pageTitle))
                val longestWord = titleText.split("\n").maxOf { it.length }
                val baseTitleSize = MaterialTheme.typography.displayMedium.fontSize
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.displayMedium,
                    fontSize = when {
                        longestWord >= 15 -> baseTitleSize * 0.60f
                        longestWord >= 12 -> baseTitleSize * 0.75f
                        else -> baseTitleSize
                    },
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp, start = 88.dp, end = 88.dp),
                    textAlign = TextAlign.Center
                )

                // --- Zone layout inside the card ---
                // Top region: title (~80dp from top)
                // Mic button: centered at ~40% from top
                // Action button (Take Picture / Browse Objects): midpoint between mic and nav
                // Nav buttons: at the bottom of the card

                val micModes = buildSet {
                    if (llmChatEnabled) add("Image chat")
                    if (llmChatEnabled) add("Text chat")
                    add("Find objects")
                }
                val showMic = isActive && activeMode in micModes
                val showPlaybackRow = isActive && showTextPlaybackControls && pageTitle == "Text chat"

                // Mic button — fixed centre position, identical on every feature.
                if (showMic) {
                    val micCd = stringResource(
                        when {
                            holdToSpeak -> R.string.cd_ask_hold
                            isRecording -> R.string.cd_ask_listening
                            else -> R.string.cd_ask_tap
                        }
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(100.dp)
                            .background(micPrimaryBg, shape = CircleShape)
                            .pointerInput(holdToSpeak) {
                                detectTapGestures(
                                    onPress = {
                                        if (holdToSpeak) {
                                            val pressTime = System.currentTimeMillis()
                                            onMicPressed()
                                            tryAwaitRelease()
                                            val holdMs = System.currentTimeMillis() - pressTime
                                            onMicReleased(holdMs)
                                        }
                                    },
                                    onTap = {
                                        if (!holdToSpeak) onMicTapped()
                                    },
                                )
                            }
                            .semantics {
                                contentDescription = micCd
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(if (!holdToSpeak && isRecording) R.string.btn_send else R.string.btn_ask),
                            color = micPrimaryText,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                // Text playback transport row (Back · Play · Forward). Sits above
                // the mic when both show — so the mic never moves — and centres
                // itself when there is no mic (AI features off).
                if (showPlaybackRow) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(y = if (showMic) (-110).dp else 0.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        PlaybackTransportButton(
                            label = stringResource(R.string.btn_back),
                            contentDescription = stringResource(R.string.cd_playback_back),
                            enabled = textPlaybackEnabled,
                            background = micPrimaryBg,
                            textColor = micPrimaryText,
                            holdThresholdMs = TextScriptPlayer.HOLD_BACK_RESTART_MS,
                            onHoldTriggered = {
                                onPlaybackTransportInteraction()
                                onPlaybackRateStep(false)
                            },
                            onShortRelease = {
                                onPlaybackTransportInteraction()
                                onPlaybackSeekBack()
                            },
                            onDisabledInteraction = {
                                onPlaybackTransportInteraction()
                                onPlaybackDisabled()
                            },
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        PlaybackTransportButton(
                            label = stringResource(if (playbackPlaying) R.string.btn_pause else R.string.btn_play),
                            contentDescription = stringResource(
                                if (playbackPlaying) R.string.cd_playback_pause else R.string.cd_playback_play
                            ),
                            enabled = textPlaybackEnabled,
                            background = micPrimaryBg,
                            textColor = micPrimaryText,
                            holdThresholdMs = TextScriptPlayer.HOLD_BACK_RESTART_MS,
                            onHoldTriggered = {
                                onPlaybackTransportInteraction()
                                onPlaybackRestartFromBeginning()
                            },
                            onShortRelease = {
                                onPlaybackTransportInteraction()
                                onPlaybackPauseToggle()
                            },
                            onDisabledInteraction = {
                                onPlaybackTransportInteraction()
                                onPlaybackDisabled()
                            },
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        PlaybackTransportButton(
                            label = stringResource(R.string.btn_forward),
                            contentDescription = stringResource(R.string.cd_playback_forward),
                            enabled = textPlaybackEnabled,
                            background = micPrimaryBg,
                            textColor = micPrimaryText,
                            holdThresholdMs = TextScriptPlayer.HOLD_FORWARD_TOGGLE_MS,
                            onHoldTriggered = {
                                onPlaybackTransportInteraction()
                                onPlaybackRateStep(true)
                            },
                            onShortRelease = {
                                onPlaybackTransportInteraction()
                                onPlaybackSeekForward()
                            },
                            onDisabledInteraction = {
                                onPlaybackTransportInteraction()
                                onPlaybackDisabled()
                            },
                        )
                    }
                }

                // Colour scanner focus frame — marks the sampled centre region.
                // Two-tone border stays visible over any camera scene and in both
                // high-contrast themes (where it hints aim over the solid mask).
                if (isActive && pageTitle == "Scan Colour") {
                    val focusFrameCd = stringResource(R.string.cd_colour_focus_frame)
                    val frameOuter = if (highContrast && whiteMode) Color.Black else Color.White
                    val frameInner = if (highContrast && whiteMode) Color.White else Color.Black
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(130.dp)
                            .border(4.dp, frameOuter, RoundedCornerShape(14.dp))
                            .padding(4.dp)
                            .border(2.dp, frameInner, RoundedCornerShape(10.dp))
                            .semantics { contentDescription = focusFrameCd },
                    )
                }

                // Keep action controls a fixed distance below the centered mic.
                // This makes placement stable across devices and avoids overlap.
                val actionOffsetFromMic = 190.dp

                if (isActive && (pageTitle == "Image chat" || pageTitle == "Text chat")) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(y = actionOffsetFromMic)
                            .size(80.dp)
                            .background(
                                if (highContrast && whiteMode) Color.Black else Color.White,
                                shape = CircleShape
                            )
                            .clickable { onTakePicture?.invoke() }
                            .semantics { contentDescription = captureLabel },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = captureLabel,
                            color = if (highContrast && whiteMode) Color.White else Color.Black,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                if (isActive && pageTitle == "Find objects") {
                    val browseObjectsCd = stringResource(R.string.cd_browse_objects)
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(y = actionOffsetFromMic)
                            .fillMaxWidth(0.7f)
                            .background(
                                when {
                                    highContrast && whiteMode -> Color.Black
                                    highContrast -> Color.White
                                    else -> Color(0xFF3DBAD0)
                                },
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { onBrowseObjects?.invoke() }
                            .padding(vertical = 18.dp)
                            .semantics { contentDescription = browseObjectsCd },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.btn_browse_objects),
                            color = micPrimaryText,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Nav buttons — inside the card at the bottom
                if (useButtonNav) {
                    // At an end of the carousel, the button that cannot go anywhere is
                    // hidden rather than disabled, but its space is kept so the other
                    // button never shifts under a finger. Looping keeps both.
                    val current = realPage(pagerState.currentPage)
                    val showPrev = looping || current > 0
                    val showNext = looping || current < pages.size - 1
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                                .height(52.dp)
                        ) {
                            if (showPrev) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            when {
                                                highContrast && whiteMode -> Color.Black
                                                highContrast -> Color.White
                                                else -> Color.White.copy(alpha = 0.2f)
                                            },
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            val prev = if (looping) pagerState.currentPage - 1
                                            else (pagerState.currentPage - 1).coerceAtLeast(0)
                                            scope.launch {
                                                pagerState.animateScrollToPage(prev)
                                            }
                                        }
                                        .semantics { contentDescription = prevModeCd },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        stringResource(R.string.btn_previous),
                                        color = if (highContrast) micPrimaryText else textColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp)
                                .height(52.dp)
                        ) {
                            if (showNext) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            when {
                                                highContrast && whiteMode -> Color.Black
                                                highContrast -> Color.White
                                                else -> Color.White.copy(alpha = 0.2f)
                                            },
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            val next = if (looping) pagerState.currentPage + 1
                                            else (pagerState.currentPage + 1).coerceAtMost(pages.size - 1)
                                            scope.launch {
                                                pagerState.animateScrollToPage(next)
                                            }
                                        }
                                        .semantics { contentDescription = nextModeCd },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        stringResource(R.string.btn_next),
                                        color = if (highContrast) micPrimaryText else textColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Torch — directly under Help, on the camera frame where it is needed.
        // It used to live in Settings, which meant leaving the view you wanted lit
        // in order to light it. Runtime-only: never persisted, and cleared whenever
        // the camera unbinds.
        //
        // Read outside the semantics lambda: stringResource cannot be called in one.
        val torchLabel = stringResource(R.string.torch_short)
        val torchOnCd = stringResource(R.string.torch_on_cd)
        val torchOffCd = stringResource(R.string.torch_off_cd)
        if (torchAvailable) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    // 24 top + 56 button + 12 gap: sits under Help, same left edge.
                    .padding(top = 92.dp, start = 20.dp)
                    .size(56.dp)
                    .background(
                        when {
                            highContrast && whiteMode -> if (torchOn) Color.Black else Color.White.copy(alpha = 0.25f)
                            highContrast -> if (torchOn) Color.White else Color.White.copy(alpha = 0.25f)
                            torchOn -> Color(0xFF8EEFCA)
                            else -> Color.White.copy(alpha = 0.25f)
                        },
                        shape = CircleShape,
                    )
                    .clickable { onToggleTorch(!torchOn) }
                    .semantics { contentDescription = if (torchOn) torchOnCd else torchOffCd },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = torchLabel,
                    color = when {
                        highContrast && whiteMode -> if (torchOn) Color.White else Color.Black
                        highContrast -> if (torchOn) Color.Black else Color.White
                        torchOn -> Color.Black
                        else -> textColor
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // Help button — top-left corner (mirrors Settings on the right)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 24.dp, start = 20.dp)
                .size(56.dp)
                .background(
                    when {
                        highContrast && whiteMode -> Color.Black
                        highContrast -> Color.White
                        else -> Color.White.copy(alpha = 0.25f)
                    },
                    shape = CircleShape,
                )
                .clickable { onOpenHelp() }
                .semantics { contentDescription = helpButtonCd },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "?",
                color = if (highContrast && whiteMode) Color.White
                else if (highContrast) Color.Black
                else textColor,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }

        // Settings button — top-right corner, always visible
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 24.dp, end = 20.dp)
                .size(56.dp)
                .background(
                    when {
                        highContrast && whiteMode -> Color.Black
                        highContrast -> Color.White
                        else -> Color.White.copy(alpha = 0.25f)
                    },
                    shape = CircleShape,
                )
                .clickable { onOpenSettings() }
                .semantics { contentDescription = openSettingsCd },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.btn_settings),
                color = if (highContrast && whiteMode) Color.White
                else if (highContrast) Color.Black
                else textColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
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
            .semantics { this.contentDescription = contentDescription },
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
