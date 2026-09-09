package com.example.sightbuddy.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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

/**
 * First-launch welcome screen: brand image, skip control, and preview Help / Settings buttons.
 * Spoken tutorial audio is played separately by [com.example.sightbuddy.core.SoundFXService].
 *
 * Normal mode shows the full-screen brand frame ([R.drawable.welcome_frame]) under the corner
 * buttons. High-contrast mode is left unchanged: the tinted line-art mark on the solid theme
 * background, so the accessibility scheme stays intact.
 */
@Composable
fun WelcomeOverlay(
    darkTheme: Boolean,
    highContrast: Boolean,
    whiteMode: Boolean,
    onSkip: () -> Unit,
    onCornerButtonTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (darkTheme) Color(0xFF121212) else Color.White
    val skipLabel = stringResource(R.string.welcome_skip)
    val skipCd = stringResource(R.string.welcome_skip_cd)
    val helpButtonCd = stringResource(R.string.welcome_help_tap_cd)
    val settingsButtonCd = stringResource(R.string.welcome_settings_tap_cd)
    val imageCd = stringResource(R.string.welcome_image_cd)
    val context = LocalContext.current

    val chipBg = when {
        highContrast && whiteMode -> Color.Black
        highContrast -> Color.White
        darkTheme -> Color.White.copy(alpha = 0.25f)
        else -> Color.Black.copy(alpha = 0.08f)
    }
    val chipText = when {
        highContrast && whiteMode -> Color.White
        highContrast -> Color.Black
        darkTheme -> Color.White
        else -> Color(0xFF1A1A1A)
    }

    val iconBitmap = remember {
        val opts = BitmapFactory.Options().apply {
            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
            inScaled = false
        }
        BitmapFactory.decodeResource(context.resources, R.drawable.sightbuddy_icon, opts)?.asImageBitmap()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bg),
    ) {
        if (!highContrast) {
            // Full-screen brand frame fills the whole screen under the corner buttons.
            Image(
                painter = painterResource(R.drawable.welcome_frame),
                contentDescription = imageCd,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        WelcomeCornerButton(
            label = "?",
            fontSizeSp = 28,
            background = chipBg,
            textColor = chipText,
            contentDescription = helpButtonCd,
            onClick = onCornerButtonTap,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 24.dp, start = 20.dp),
        )

        WelcomeCornerButton(
            label = "Settings",
            fontSizeSp = 10,
            background = chipBg,
            textColor = chipText,
            contentDescription = settingsButtonCd,
            onClick = onCornerButtonTap,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 24.dp, end = 20.dp),
        )

        if (highContrast) {
            // High-contrast: unchanged — tinted line-art mark on the solid background.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp, vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = imageCd,
                        contentScale = ContentScale.Fit,
                        colorFilter = ColorFilter.tint(
                            if (darkTheme) Color.White else Color(0xFF1A1A1A)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .sizeIn(maxHeight = 280.dp),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                WelcomeSkipButton(highContrast, whiteMode, skipLabel, skipCd, onSkip)
            }
        } else {
            // Skip control anchored to the bottom, over the brand frame.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp, vertical = 48.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                WelcomeSkipButton(highContrast, whiteMode, skipLabel, skipCd, onSkip)
            }
        }
    }
}

@Composable
private fun WelcomeSkipButton(
    highContrast: Boolean,
    whiteMode: Boolean,
    label: String,
    contentDescription: String,
    onSkip: () -> Unit,
) {
    // Brand green + black text normally; plain black/white in high contrast.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(actionButtonBackground(highContrast, whiteMode, Color(0xFF8EEFCA)))
            .clickable(onClick = onSkip)
            .padding(horizontal = 32.dp, vertical = 16.dp)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = actionButtonText(highContrast, whiteMode, Color.Black),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun WelcomeCornerButton(
    label: String,
    fontSizeSp: Int,
    background: Color,
    textColor: Color,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(56.dp)
            .background(background, shape = CircleShape)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = fontSizeSp.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}
