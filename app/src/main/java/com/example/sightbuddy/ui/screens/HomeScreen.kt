package com.example.sightbuddy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    holdToSpeak: Boolean = false,
    isRecording: Boolean = false,
    onModeSelected: (String) -> Unit = {},
    onMicPressed: () -> Unit = {},
    onMicReleased: (holdMs: Long) -> Unit = {},
    onMicTapped: () -> Unit = {},
    showTextPlaybackControls: Boolean = false,
    textPlaybackEnabled: Boolean = false,
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
    val pagerState = rememberPagerState(pageCount = { pages.size })
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
        else -> Color(0xFF2196F3).copy(alpha = 0.8f)
    }
    val micPrimaryText = when {
        highContrast && whiteMode -> Color.White
        highContrast -> Color.Black
        else -> Color.White
    }
    val helpButtonCd = stringResource(R.string.help_button_cd)

    LaunchedEffect(pagerState.currentPage) {
        onModeSelected(pages[pagerState.currentPage])
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
            val pageTitle = pages[page]
            val isActive = activeMode == pageTitle

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .background(color = maskColor, shape = MaterialTheme.shapes.large)
                    .semantics {
                        contentDescription = if (isActive)
                            "$pageTitle active. Swipe left or right to change modes."
                        else
                            "$pageTitle."
                    }
            ) {
                // Title at top center
                Text(
                    text = splitFeatureTitle(pageTitle),
                    style = MaterialTheme.typography.displayMedium,
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp),
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

                if (showMic || showPlaybackRow) {
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        if (showPlaybackRow) {
                            PlaybackTransportButton(
                                label = "Back",
                                contentDescription =
                                    "Backwards. Tap skips back twenty characters. " +
                                        "Hold one second to restart from the beginning.",
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
                                    onPlaybackSeekBack()
                                },
                                onDisabledInteraction = {
                                    onPlaybackTransportInteraction()
                                    onPlaybackDisabled()
                                },
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }

                        if (showMic) {
                            val micCd = if (holdToSpeak) {
                                "Hold mic button and speak"
                            } else if (isRecording) {
                                "Recording. Tap to stop"
                            } else {
                                "Mic. Tap to start recording, tap again to stop"
                            }
                            Box(
                                modifier = Modifier
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
                                    text = if (!holdToSpeak && isRecording) "Stop" else "Mic",
                                    color = micPrimaryText,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }

                        if (showPlaybackRow) {
                            Spacer(modifier = Modifier.width(12.dp))
                            PlaybackTransportButton(
                                label = "Fwd",
                                contentDescription =
                                    "Forward. Tap skips ahead twenty characters. " +
                                        "Hold one second to play or stop reading.",
                                enabled = textPlaybackEnabled,
                                background = micPrimaryBg,
                                textColor = micPrimaryText,
                                holdThresholdMs = TextScriptPlayer.HOLD_FORWARD_TOGGLE_MS,
                                onHoldTriggered = {
                                    onPlaybackTransportInteraction()
                                    onPlaybackPauseToggle()
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
                            .semantics { contentDescription = "Take picture" },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Take\nPicture",
                            color = if (highContrast && whiteMode) Color.White else Color.Black,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                if (isActive && pageTitle == "Find objects") {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(y = actionOffsetFromMic)
                            .fillMaxWidth(0.7f)
                            .background(
                                when {
                                    highContrast && whiteMode -> Color.Black
                                    highContrast -> Color.White
                                    else -> Color(0xFF2196F3)
                                },
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { onBrowseObjects?.invoke() }
                            .padding(vertical = 18.dp)
                            .semantics {
                                contentDescription =
                                    "Browse objects list. Tap to choose an object to find."
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Browse Objects",
                            color = micPrimaryText,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Nav buttons — inside the card at the bottom
                if (useButtonNav) {
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
                                .background(
                                    when {
                                        highContrast && whiteMode -> Color.Black
                                        highContrast -> Color.White
                                        else -> Color.White.copy(alpha = 0.2f)
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    val prev =
                                        (pagerState.currentPage - 1).coerceAtLeast(0)
                                    scope.launch {
                                        pagerState.animateScrollToPage(prev)
                                    }
                                }
                                .semantics { contentDescription = "Previous mode" },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Previous",
                                color = if (highContrast) micPrimaryText else textColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp)
                                .height(52.dp)
                                .background(
                                    when {
                                        highContrast && whiteMode -> Color.Black
                                        highContrast -> Color.White
                                        else -> Color.White.copy(alpha = 0.2f)
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    val next = (pagerState.currentPage + 1)
                                        .coerceAtMost(pages.size - 1)
                                    scope.launch {
                                        pagerState.animateScrollToPage(next)
                                    }
                                }
                                .semantics { contentDescription = "Next mode" },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Next",
                                color = if (highContrast) micPrimaryText else textColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
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
                .semantics { contentDescription = "Open settings" },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Settings",
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
