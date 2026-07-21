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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sightbuddy.R

/**
 * First-launch welcome screen: brand image, skip control, and preview Help / Settings buttons.
 * Spoken tutorial audio is played separately by [com.example.sightbuddy.core.SoundFXService].
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap,
                    contentDescription = stringResource(R.string.welcome_image_cd),
                    contentScale = ContentScale.Fit,
                    // The art is white line work on transparency: tint it to match
                    // the theme so it stays visible on the light background too.
                    colorFilter = ColorFilter.tint(
                        if (darkTheme) Color.White else Color(0xFF1A1A1A)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .sizeIn(maxHeight = 280.dp),
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF4CAF50))
                    .clickable(onClick = onSkip)
                    .padding(horizontal = 32.dp, vertical = 16.dp)
                    .semantics { contentDescription = skipCd },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = skipLabel,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
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
