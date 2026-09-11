package com.example.sightbuddy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sightbuddy.R
import com.example.sightbuddy.ui.theme.actionButtonBackground
import com.example.sightbuddy.ui.theme.actionButtonText

@Composable
fun TermsAcceptanceOverlay(
    darkTheme: Boolean,
    highContrast: Boolean,
    whiteMode: Boolean,
    onTermsOfUse: () -> Unit,
    onPrivacyPolicy: () -> Unit,
    onAccept: () -> Unit,
    modifier: Modifier = Modifier,
    languageChosen: Boolean = true,
    currentLanguage: String = "en",
    onSelectLanguage: (String) -> Unit = {},
) {
    val bg = if (darkTheme) Color(0xFF121212) else Color.White
    val textColor = if (darkTheme) Color.White else Color(0xFF1A1A1A)
    val secondaryBg = if (darkTheme) Color(0xFF424242) else Color(0xFFE0E0E0)

    val prompt = stringResource(R.string.terms_prompt)
    val promptCd = stringResource(R.string.terms_prompt_cd)
    val termsOfUseLabel = stringResource(R.string.terms_of_use)
    val termsOfUseCd = stringResource(R.string.terms_of_use_cd)
    val privacyPolicyLabel = stringResource(R.string.terms_privacy_policy)
    val privacyPolicyCd = stringResource(R.string.terms_privacy_policy_cd)
    val acceptLabel = stringResource(R.string.terms_accept)
    val acceptCd = stringResource(R.string.terms_accept_cd)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bg)
            .semantics { contentDescription = promptCd },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Mandatory language choice — the app cannot be entered until one is
            // picked, and picking one re-renders this screen in that language.
            Text(
                text = stringResource(R.string.language_title),
                style = MaterialTheme.typography.headlineSmall,
                color = textColor,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                LanguageChoiceButton(
                    label = stringResource(R.string.language_english),
                    contentDescription = stringResource(R.string.language_english_cd),
                    selected = languageChosen && currentLanguage == "en",
                    highContrast = highContrast,
                    whiteMode = whiteMode,
                    fallbackBg = secondaryBg,
                    fallbackText = textColor,
                    onClick = { onSelectLanguage("en") },
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(12.dp))
                LanguageChoiceButton(
                    label = stringResource(R.string.language_finnish),
                    contentDescription = stringResource(R.string.language_finnish_cd),
                    selected = languageChosen && currentLanguage == "fi",
                    highContrast = highContrast,
                    whiteMode = whiteMode,
                    fallbackBg = secondaryBg,
                    fallbackText = textColor,
                    onClick = { onSelectLanguage("fi") },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(36.dp))

            Text(
                text = prompt,
                style = MaterialTheme.typography.headlineSmall,
                color = textColor,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 32.sp,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(40.dp))

            TermsActionButton(
                label = termsOfUseLabel,
                background = secondaryBg,
                textColor = textColor,
                contentDescription = termsOfUseCd,
                onClick = onTermsOfUse,
            )

            Spacer(modifier = Modifier.height(16.dp))

            TermsActionButton(
                label = privacyPolicyLabel,
                background = secondaryBg,
                textColor = textColor,
                contentDescription = privacyPolicyCd,
                onClick = onPrivacyPolicy,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Dimmed and inert until a language has been chosen.
            TermsActionButton(
                label = acceptLabel,
                background = actionButtonBackground(highContrast, whiteMode, Color(0xFF8EEFCA))
                    .let { if (languageChosen) it else it.copy(alpha = 0.4f) },
                textColor = actionButtonText(highContrast, whiteMode, Color.Black),
                contentDescription = acceptCd,
                onClick = { if (languageChosen) onAccept() },
            )
        }
    }
}

@Composable
private fun LanguageChoiceButton(
    label: String,
    contentDescription: String,
    selected: Boolean,
    highContrast: Boolean,
    whiteMode: Boolean,
    fallbackBg: Color,
    fallbackText: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) {
                    actionButtonBackground(highContrast, whiteMode, Color(0xFF3DBAD0))
                } else {
                    fallbackBg
                }
            )
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) {
                actionButtonText(highContrast, whiteMode, Color.White)
            } else {
                fallbackText
            },
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TermsActionButton(
    label: String,
    background: Color,
    textColor: Color,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}
