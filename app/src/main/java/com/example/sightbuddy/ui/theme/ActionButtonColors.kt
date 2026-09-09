package com.example.sightbuddy.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Colours for a filled action button that must respect high-contrast mode.
 *
 * In high contrast every action button drops its brand colour and becomes plain
 * black/white (matching the rest of the accessibility scheme): black on the white
 * theme, white on the black theme. Otherwise the brand colour is used as-is.
 */
fun actionButtonBackground(highContrast: Boolean, whiteMode: Boolean, brand: Color): Color = when {
    highContrast && whiteMode -> Color.Black
    highContrast -> Color.White
    else -> brand
}

fun actionButtonText(highContrast: Boolean, whiteMode: Boolean, brand: Color): Color = when {
    highContrast && whiteMode -> Color.White
    highContrast -> Color.Black
    else -> brand
}
