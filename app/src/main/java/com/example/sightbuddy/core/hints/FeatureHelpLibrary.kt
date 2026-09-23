package com.example.sightbuddy.core.hints

import android.content.Context
import com.example.sightbuddy.R

/** Central catalogue of feature help text, opened from the Help button. */
object FeatureHelpLibrary {

    /**
     * A feature's help in short parts under sub-headings, rather than one block:
     * how it works, its own buttons (which speak only their label, so what they
     * do is told here), then the buttons and moves every feature shares.
     */
    fun featureHelp(context: Context, featureName: String): HelpContent? =
        baseHelp(context, featureName)?.let { help ->
            val own = when (featureName) {
                "Image chat" -> R.string.help_buttons_image_chat
                "Text chat" -> R.string.help_buttons_text_chat
                "Find objects" -> R.string.help_buttons_find_objects
                else -> R.string.help_buttons_mute
            }
            help.copy(
                sections = listOf(
                    HelpSection(context.getString(R.string.help_heading_how), help.body),
                    HelpSection(context.getString(R.string.help_heading_buttons), context.getString(own)),
                    HelpSection(context.getString(R.string.help_heading_top), context.getString(R.string.help_common_top)),
                    HelpSection(context.getString(R.string.help_heading_moving), context.getString(R.string.help_common_moving)),
                ),
            )
        }

    private fun baseHelp(context: Context, featureName: String): HelpContent? =
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
}
