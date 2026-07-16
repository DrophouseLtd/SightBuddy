package com.example.sightbuddy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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

@Composable
fun TermsAcceptanceOverlay(
    darkTheme: Boolean,
    onTermsOfUse: () -> Unit,
    onPrivacyPolicy: () -> Unit,
    onAccept: () -> Unit,
    modifier: Modifier = Modifier,
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

            TermsActionButton(
                label = acceptLabel,
                background = Color(0xFF4CAF50),
                textColor = Color.White,
                contentDescription = acceptCd,
                onClick = onAccept,
            )
        }
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
