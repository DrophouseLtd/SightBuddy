package com.example.sightbuddy.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.sightbuddy.R

/**
 * Shown once after the user declines a better speech option.
 *
 * Its job is to set an expectation rather than to sell anything: the platform
 * recogniser will sometimes cut a sentence short, and someone who does not know
 * that will read it as the app being broken. It also says where the offer lives
 * afterwards, so declining is not a one-way door. One button, and no second ask.
 */
@Composable
fun ChoiceRespectedDialog(
    highContrast: Boolean,
    whiteMode: Boolean,
    onDismiss: () -> Unit,
) = NoticeDialog(
    title = stringResource(R.string.choice_respected_title),
    message = stringResource(R.string.choice_respected_body),
    buttonLabel = stringResource(R.string.choice_respected_ok),
    highContrast = highContrast,
    whiteMode = whiteMode,
    onDismiss = onDismiss,
)
