package com.example.sightbuddy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.sightbuddy.R
import com.example.sightbuddy.ui.theme.actionButtonBackground
import com.example.sightbuddy.ui.theme.actionButtonText
import kotlinx.coroutines.launch

private enum class HelpPane {
    INSTRUCTIONS,
    FEEDBACK_FORM,
    FEEDBACK_THANKS,
}

@Composable
fun HelpDialog(
    title: String,
    body: String,
    darkTheme: Boolean,
    highContrast: Boolean,
    whiteMode: Boolean,
    onClose: () -> Unit,
    onSubmitFeedback: suspend (String) -> Boolean,
) {
    val scrim = Color.Black.copy(alpha = 0.72f)
    val cardBg = if (darkTheme) Color(0xFF1E1E1E) else Color.White
    val textColor = if (darkTheme) Color.White else Color(0xFF1A1A1A)
    val mutedColor = textColor.copy(alpha = 0.75f)
    val fieldBorder = if (darkTheme) Color.Gray else Color(0xFF9E9E9E)

    val closeLabel = stringResource(R.string.help_close)
    val closeCd = stringResource(R.string.help_close_cd)
    val leaveFeedbackLabel = stringResource(R.string.help_leave_feedback)
    val leaveFeedbackCd = stringResource(R.string.help_leave_feedback_cd)
    val privacyNotice = stringResource(R.string.feedback_privacy_notice)
    val feedbackHint = stringResource(R.string.feedback_field_hint)
    val submitLabel = stringResource(R.string.feedback_submit)
    val submitCd = stringResource(R.string.feedback_submit_cd)
    val backLabel = stringResource(R.string.feedback_back_to_help)
    val thanksTitle = stringResource(R.string.feedback_thanks_title)
    val thanksBody = stringResource(R.string.feedback_thanks_body)
    val submitFailed = stringResource(R.string.feedback_submit_failed)

    var pane by remember { mutableStateOf(HelpPane.INSTRUCTIONS) }
    var feedbackText by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun resetAndClose() {
        pane = HelpPane.INSTRUCTIONS
        feedbackText = ""
        isSubmitting = false
        submitError = false
        onClose()
    }

    Dialog(
        onDismissRequest = { resetAndClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scrim)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(cardBg)
                    .padding(horizontal = 24.dp, vertical = 28.dp),
            ) {
                when (pane) {
                    HelpPane.INSTRUCTIONS -> {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = textColor,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = body,
                            style = MaterialTheme.typography.bodyLarge,
                            color = textColor.copy(alpha = 0.92f),
                            lineHeight = 26.sp,
                            modifier = Modifier
                                .heightIn(max = 360.dp)
                                .verticalScroll(rememberScrollState()),
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        HelpActionButton(
                            label = closeLabel,
                            background = actionButtonBackground(highContrast, whiteMode, Color(0xFF8EEFCA)),
                            textColor = actionButtonText(highContrast, whiteMode, Color.Black),
                            contentDescription = closeCd,
                            onClick = { resetAndClose() },
                        )
                        // Feedback flow removed with the backend (v2.0.0, fully local app).
                    }

                    HelpPane.FEEDBACK_FORM -> {
                        Text(
                            text = leaveFeedbackLabel,
                            style = MaterialTheme.typography.headlineSmall,
                            color = textColor,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = privacyNotice,
                            style = MaterialTheme.typography.bodySmall,
                            color = mutedColor,
                            lineHeight = 20.sp,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = feedbackText,
                            onValueChange = { feedbackText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp, max = 200.dp)
                                .semantics { contentDescription = feedbackHint },
                            placeholder = {
                                Text(feedbackHint, color = mutedColor)
                            },
                            enabled = !isSubmitting,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = textColor,
                                unfocusedTextColor = textColor,
                                cursorColor = textColor,
                                focusedBorderColor = if (highContrast) textColor else Color(0xFF3DBAD0),
                                unfocusedBorderColor = fieldBorder,
                            ),
                        )
                        if (submitError) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = submitFailed,
                                color = Color(0xFFE57373),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        HelpActionButton(
                            label = if (isSubmitting) "…" else submitLabel,
                            background = actionButtonBackground(highContrast, whiteMode, Color(0xFF8EEFCA)),
                            textColor = actionButtonText(highContrast, whiteMode, Color.Black),
                            contentDescription = submitCd,
                            onClick = {
                                val text = feedbackText.trim()
                                if (text.isEmpty() || isSubmitting) return@HelpActionButton
                                scope.launch {
                                    isSubmitting = true
                                    submitError = false
                                    val ok = onSubmitFeedback(text)
                                    isSubmitting = false
                                    if (ok) {
                                        pane = HelpPane.FEEDBACK_THANKS
                                    } else {
                                        submitError = true
                                    }
                                }
                            },
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        HelpActionButton(
                            label = backLabel,
                            background = if (darkTheme) Color(0xFF424242) else Color(0xFFE0E0E0),
                            textColor = textColor,
                            contentDescription = backLabel,
                            onClick = {
                                submitError = false
                                pane = HelpPane.INSTRUCTIONS
                            },
                        )
                    }

                    HelpPane.FEEDBACK_THANKS -> {
                        Text(
                            text = thanksTitle,
                            style = MaterialTheme.typography.headlineSmall,
                            color = textColor,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = thanksBody,
                            style = MaterialTheme.typography.bodyLarge,
                            color = textColor.copy(alpha = 0.92f),
                            lineHeight = 26.sp,
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        HelpActionButton(
                            label = closeLabel,
                            background = actionButtonBackground(highContrast, whiteMode, Color(0xFF8EEFCA)),
                            textColor = actionButtonText(highContrast, whiteMode, Color.Black),
                            contentDescription = closeCd,
                            onClick = { resetAndClose() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HelpActionButton(
    label: String,
    background: Color,
    contentDescription: String,
    onClick: () -> Unit,
    textColor: Color = Color.White,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
