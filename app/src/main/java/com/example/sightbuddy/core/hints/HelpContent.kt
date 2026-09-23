package com.example.sightbuddy.core.hints

/** Title and instructional body for the help dialog. */
data class HelpContent(
    val title: String,
    val body: String = "",
    /** When present, shown instead of [body]: short parts under sub-headings. */
    val sections: List<HelpSection> = emptyList(),
)

/** One part of a help text; paragraphs within [text] are split on blank lines. */
data class HelpSection(
    val heading: String?,
    val text: String,
)
