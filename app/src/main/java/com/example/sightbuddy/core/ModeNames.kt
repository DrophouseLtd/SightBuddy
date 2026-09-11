package com.example.sightbuddy.core

import android.content.Context
import com.example.sightbuddy.R

/**
 * Display names for the carousel features.
 *
 * The feature identifiers ("Image chat", "Text chat", …) are load-bearing: they
 * are compared in dozens of places to decide what the app is doing. They must
 * therefore stay in English and are NOT localised.
 *
 * This maps an identifier to the user-visible name for the active language, and
 * should be used everywhere a mode is shown on screen or spoken aloud — never
 * for comparisons.
 */
object ModeNames {

    const val IMAGE_CHAT = "Image chat"
    const val TEXT_CHAT = "Text chat"
    const val DISCOVER_OBJECTS = "Discover objects"
    const val FIND_OBJECTS = "Find objects"
    const val SCAN_LIGHT = "Scan Light"
    const val SCAN_COLOUR = "Scan Colour"

    fun display(context: Context, mode: String): String = when (mode) {
        IMAGE_CHAT -> context.getString(R.string.help_title_image_chat)
        TEXT_CHAT -> context.getString(R.string.help_title_text_chat)
        DISCOVER_OBJECTS -> context.getString(R.string.help_title_discover_objects)
        FIND_OBJECTS -> context.getString(R.string.help_title_find_objects)
        SCAN_LIGHT -> context.getString(R.string.help_title_scan_light)
        SCAN_COLOUR -> context.getString(R.string.help_title_scan_colour)
        else -> mode
    }
}
