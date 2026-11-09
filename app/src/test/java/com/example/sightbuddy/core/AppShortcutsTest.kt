package com.example.sightbuddy.core

import com.example.sightbuddy.core.ModeNames.DISCOVER_OBJECTS
import com.example.sightbuddy.core.ModeNames.FIND_OBJECTS
import com.example.sightbuddy.core.ModeNames.IMAGE_CHAT
import com.example.sightbuddy.core.ModeNames.SCAN_COLOUR
import com.example.sightbuddy.core.ModeNames.SCAN_LIGHT
import com.example.sightbuddy.core.ModeNames.TEXT_CHAT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Item 11 (issue #10): the launcher's long-press menu offers up to four
 * features, the first ones on the feature bar, and only those switched on.
 * The shortcut carries its feature; anything else opens the app as usual.
 */
class AppShortcutsTest {

    private val all = listOf(IMAGE_CHAT, TEXT_CHAT, DISCOVER_OBJECTS, FIND_OBJECTS, SCAN_LIGHT, SCAN_COLOUR)

    @Test
    fun theFirstFourFeaturesOnTheBar() {
        assertEquals(listOf(IMAGE_CHAT, TEXT_CHAT, DISCOVER_OBJECTS, FIND_OBJECTS), AppShortcuts.offered(all))
    }

    @Test
    fun aFeatureSwitchedOffIsNotOffered() {
        // No AI, so no Image chat; Discover objects switched off in Settings.
        val pages = listOf(TEXT_CHAT, FIND_OBJECTS, SCAN_LIGHT, SCAN_COLOUR)
        val offered = AppShortcuts.offered(pages)
        assertEquals(pages, offered)
        assertFalse(IMAGE_CHAT in offered)
        assertFalse(DISCOVER_OBJECTS in offered)
    }

    @Test
    fun fewerFeaturesFewerShortcuts() {
        assertEquals(listOf(TEXT_CHAT, SCAN_COLOUR), AppShortcuts.offered(listOf(TEXT_CHAT, SCAN_COLOUR)))
    }

    @Test
    fun neverMoreThanTheLauncherTakes() {
        assertEquals(listOf(IMAGE_CHAT, TEXT_CHAT), AppShortcuts.offered(all, launcherMax = 2))
    }

    @Test
    fun eachShortcutOpensItsFeature() {
        all.forEach { assertEquals(it, AppShortcuts.featureOf(it)) }
    }

    @Test
    fun anythingElseOpensTheAppAsUsual() {
        assertNull(AppShortcuts.featureOf(null))
        assertNull(AppShortcuts.featureOf(""))
        assertNull(AppShortcuts.featureOf("Settings"))
        assertNull(AppShortcuts.featureOf("text chat"))
    }

    @Test
    fun eachFeatureHasItsOwnLastingId() {
        assertEquals("feature_image_chat", AppShortcuts.id(IMAGE_CHAT))
        assertEquals("feature_scan_colour", AppShortcuts.id(SCAN_COLOUR))
        assertEquals(all.size, all.map { AppShortcuts.id(it) }.distinct().size)
    }
}
