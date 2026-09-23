package com.example.sightbuddy.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable

/**
 * Sight Buddy's colours, from the Play listing: mint background, purple name,
 * the owl's sky blue. Laid out like a messaging app: a tinted wall, answers in
 * plain bubbles on the left, the user's questions in coloured bubbles on the
 * right.
 *
 * Every text colour here was checked against its background for WCAG AA
 * (4.5:1); most pairs pass 7:1. High contrast mode does not use these: it keeps
 * its black and white.
 */
object Brand {
    val Mint = Color(0xFF8EEFCA)
    val Purple = Color(0xFF5B2A9E)
    val Sky = Color(0xFF3FB8D0)
}

data class BrandPalette(
    /** Behind the chat and lists. */
    val wall: Color,
    /** Answers, cards and dialogs. */
    val surface: Color,
    /** The user's messages. */
    val outgoing: Color,
    /** Body text on [wall], [surface] and [outgoing]. */
    val ink: Color,
    val inkMuted: Color,
    /** Message labels and headings: the brand colour, readable on its background. */
    val label: Color,
    /** A thin edge so a bubble stands off the wall (decoration only). */
    val edge: Color,
    val primaryBg: Color,
    val primaryText: Color,
    val secondaryBg: Color,
    val secondaryText: Color,
    /** The selected feature tab. */
    val selected: Color,
    /**
     * The owl's blue, as text or a control colour: section headings, switches
     * that are on, focused fields. A deeper shade in light so it passes as text.
     */
    val accent: Color,
    /** On [accent]: a switch's thumb. */
    val onAccent: Color,
)

val BrandLight = BrandPalette(
    wall = Color(0xFFD3F5E6),
    surface = Color.White,
    outgoing = Color(0xFFE9DEFB),
    ink = Color(0xFF16181D),
    inkMuted = Color(0xFF3D4A45),
    label = Brand.Purple,
    edge = Color(0xFFA9DCC7),
    primaryBg = Brand.Purple,
    primaryText = Color.White,
    secondaryBg = Color.White,
    secondaryText = Brand.Purple,
    selected = Brand.Purple,
    accent = Color(0xFF16707F),
    onAccent = Color.White,
)

val BrandDark = BrandPalette(
    wall = Color(0xFF0C1F1B),
    surface = Color(0xFF1B322D),
    outgoing = Color(0xFF3E2A6E),
    ink = Color.White,
    inkMuted = Color(0xFFC9D8D2),
    label = Brand.Mint,
    edge = Color(0xFF2C4A43),
    primaryBg = Brand.Mint,
    primaryText = Color.Black,
    secondaryBg = Color(0xFF1B322D),
    secondaryText = Color.White,
    selected = Brand.Mint,
    accent = Brand.Sky,
    onAccent = Color.Black,
)

fun brandPalette(darkTheme: Boolean): BrandPalette = if (darkTheme) BrandDark else BrandLight

/** The palette for the phone's current theme, for screens not handed one. */
@Composable
fun currentBrand(): BrandPalette = brandPalette(androidx.compose.foundation.isSystemInDarkTheme())
