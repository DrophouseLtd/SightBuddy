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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sightbuddy.R
import com.example.sightbuddy.ui.buttonSemantics
import com.example.sightbuddy.ui.theme.Brand

@Composable
fun TermsAcceptanceOverlay(
    darkTheme: Boolean,
    onTermsOfUse: () -> Unit,
    onPrivacyPolicy: () -> Unit,
    onAccept: () -> Unit,
    /** Accept pressed before a language was chosen: the caller says so. */
    onAcceptWithoutLanguage: () -> Unit = {},
    modifier: Modifier = Modifier,
    languageChosen: Boolean = true,
    currentLanguage: String = "en",
    onSelectLanguage: (String) -> Unit = {},
) {
    val brand = com.example.sightbuddy.ui.theme.brandPalette(darkTheme)
    // Shown once, on a fresh install, before any setting can be changed, so high
    // contrast is never on here: brand colours only.
    val bg = brand.wall
    val textColor = brand.ink
    val secondaryBg = brand.surface

    val prompt = stringResource(R.string.terms_prompt)
    val termsOfUseLabel = stringResource(R.string.terms_of_use)
    val termsOfUseCd = stringResource(R.string.terms_of_use_cd)
    val privacyPolicyLabel = stringResource(R.string.terms_privacy_policy)
    val privacyPolicyCd = stringResource(R.string.terms_privacy_policy_cd)
    val acceptLabel = stringResource(R.string.terms_accept)
    val acceptCd = stringResource(R.string.terms_accept_cd)

    Box(
        modifier = modifier
            .fillMaxSize()
            // The background says nothing: the prompt is read where it is shown.
            .background(bg),
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
                // TalkBack hears this as the window's title as the screen opens,
                // so here it is silent, and focus goes to the languages first.
                modifier = Modifier.fillMaxWidth().clearAndSetSemantics { },
            )

            Spacer(modifier = Modifier.height(16.dp))

            // A pair of radio buttons: TalkBack says which is selected, and
            // "Selected" as soon as one is tapped.
            Row(modifier = Modifier.fillMaxWidth().selectableGroup()) {
                LanguageChoiceButton(
                    label = stringResource(R.string.language_english),
                    contentDescription = stringResource(R.string.language_english_cd),
                    selected = languageChosen && currentLanguage == "en",
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
                background = Brand.Mint
                    .let { if (languageChosen) it else it.copy(alpha = 0.4f) },
                textColor = Color.Black,
                contentDescription = acceptCd,
                onClick = { if (languageChosen) onAccept() else onAcceptWithoutLanguage() },
            )
        }
    }
}

@Composable
private fun LanguageChoiceButton(
    label: String,
    contentDescription: String,
    selected: Boolean,
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
                    Brand.Sky
                } else {
                    fallbackBg
                }
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) {
                Color.Black
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
            .buttonSemantics(label),
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
