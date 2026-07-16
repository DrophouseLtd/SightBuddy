package com.example.sightbuddy.core.hints

import android.content.Context
import com.example.sightbuddy.R

/**
 * Central catalogue of feature help text and one-time automated hint content.
 * Add new [HintId] entries and string resources here as hints are introduced.
 */
object FeatureHelpLibrary {

    fun featureHelp(context: Context, featureName: String): HelpContent? =
        when (featureName) {
            "Image chat" -> HelpContent(
                title = context.getString(R.string.help_title_image_chat),
                body = context.getString(R.string.help_body_image_chat),
            )
            "Text chat" -> HelpContent(
                title = context.getString(R.string.help_title_text_chat),
                body = context.getString(R.string.help_body_text_chat),
            )
            "Discover objects" -> HelpContent(
                title = context.getString(R.string.help_title_discover_objects),
                body = context.getString(R.string.help_body_discover_objects),
            )
            "Find objects" -> HelpContent(
                title = context.getString(R.string.help_title_find_objects),
                body = context.getString(R.string.help_body_find_objects),
            )
            "Scan Light" -> HelpContent(
                title = context.getString(R.string.help_title_scan_light),
                body = context.getString(R.string.help_body_scan_light),
            )
            "Scan Colour" -> HelpContent(
                title = context.getString(R.string.help_title_scan_colour),
                body = context.getString(R.string.help_body_scan_colour),
            )
            else -> null
        }

    fun hintHelp(context: Context, hintId: HintId): HelpContent =
        when (hintId) {
            HintId.TEXT_CHAT_PLAYBACK_CONTROLS -> HelpContent(
                title = context.getString(R.string.help_title_text_playback_controls),
                body = context.getString(R.string.help_body_text_playback_controls),
            )
        }
}
