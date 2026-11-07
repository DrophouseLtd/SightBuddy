package com.example.sightbuddy.core

/**
 * The launcher's long-press menu on the app icon, as Seeing AI has: up to four
 * features, each opening the app on it. They are the first features on the
 * feature bar, in its order, so a feature switched off in Settings is not
 * offered. Published as dynamic shortcuts (AppController.publishShortcuts),
 * whose intents keep their flags, so an app already running is brought back
 * to the feature rather than started again.
 */
object AppShortcuts {
    /** The feature a shortcut opens, as its identifier (ModeNames). */
    const val EXTRA_FEATURE = "com.example.sightbuddy.FEATURE"

    /** The most a launcher menu shows comfortably; Seeing AI offers four. */
    const val MAX = 4

    private val FEATURES = listOf(
        ModeNames.IMAGE_CHAT,
        ModeNames.TEXT_CHAT,
        ModeNames.DISCOVER_OBJECTS,
        ModeNames.FIND_OBJECTS,
        ModeNames.SCAN_LIGHT,
        ModeNames.SCAN_COLOUR,
    )

    /** The features offered, given the feature bar's [pages] and what the launcher takes. */
    fun offered(pages: List<String>, launcherMax: Int = MAX): List<String> =
        pages.filter { it in FEATURES }.take(minOf(MAX, launcherMax))

    /** One lasting id per feature: publishing again updates a shortcut rather than adding one. */
    fun id(mode: String): String = "feature_" + mode.lowercase().replace(' ', '_')

    /** The feature a shortcut's [extra] names, or null for anything else. */
    fun featureOf(extra: String?): String? = extra?.takeIf { it in FEATURES }
}
