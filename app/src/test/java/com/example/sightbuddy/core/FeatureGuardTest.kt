package com.example.sightbuddy.core

import android.content.ContextWrapper
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The carousel is never empty, and Image chat counts towards that while it can
 * be shown, that is while there is an AI to answer it (a key with Use API on,
 * or the on-device model).
 *
 * In 2.3.0 every feature but Image chat could be switched off, but Image
 * chat could not be left on its own.
 */
class FeatureGuardTest {

    private fun settings(prefs: SharedPreferences = SettingsUpgradeTest.FakePrefs(emptyMap())) =
        SettingsManager(object : ContextWrapper(null) {
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs
        })

    /** The five on-device features' switches, in carousel order. */
    private val localSwitches: List<(SettingsManager, Boolean) -> Boolean> = listOf(
        SettingsManager::setTextChat,
        SettingsManager::setDiscover,
        SettingsManager::setFind,
        SettingsManager::setScanLight,
        SettingsManager::setScanColour,
    )

    @Test
    fun imageChatCanBeTheOnlyFeatureOn() {
        val s = settings()
        s.setImageChatAvailable(true)

        localSwitches.forEach { assertTrue(it(s, false)) }

        assertEquals(listOf(ModeNames.IMAGE_CHAT), s.shownFeatures(imageChatAvailable = true))
    }

    @Test
    fun imageChatCannotBeSwitchedOffWhenItIsTheLastFeature() {
        val s = settings()
        s.setImageChatAvailable(true)
        localSwitches.forEach { it(s, false) }

        assertFalse(s.setImageChat(false))
        assertTrue(s.imageChatEnabled.value)
    }

    @Test
    fun imageChatWithNoAiDoesNotCount() {
        val s = settings()
        s.setImageChatAvailable(false)

        localSwitches.dropLast(1).forEach { assertTrue(it(s, false)) }
        assertFalse(s.setScanColour(false))
        // Not in the carousel, so switching it off leaves the carousel as it is.
        assertTrue(s.setImageChat(false))

        assertEquals(listOf(ModeNames.SCAN_COLOUR), s.shownFeatures(imageChatAvailable = false))
    }

    @Test
    fun losingImageChatWhileItIsTheOnlyFeatureBringsTextChatBack() {
        val prefs = SettingsUpgradeTest.FakePrefs(emptyMap())
        val s = settings(prefs)
        s.setImageChatAvailable(true)
        localSwitches.forEach { it(s, false) }

        // The key removed, Use API off with no on-device model, or the model deleted.
        s.setImageChatAvailable(false)

        assertEquals(listOf(ModeNames.TEXT_CHAT), s.shownFeatures(imageChatAvailable = false))
        // And it stays on: the next start reads it back.
        assertTrue(settings(prefs).textChatEnabled.value)
    }

    /** An earlier run left Image chat alone, and its AI is gone by the next start. */
    @Test
    fun aCarouselLeftEmptySinceTheLastRunGetsTextChatBack() {
        val prefs = SettingsUpgradeTest.FakePrefs(
            mapOf(
                "text_chat_enabled" to false,
                "discover_enabled" to false,
                "find_enabled" to false,
                "scan_light_enabled" to false,
                "scan_colour_enabled" to false,
                "image_chat_enabled" to true,
            ),
        )
        val s = settings(prefs)

        s.setImageChatAvailable(false)

        assertEquals(listOf(ModeNames.TEXT_CHAT), s.shownFeatures(imageChatAvailable = false))
    }

    /**
     * Every state reachable from a fresh install, by any switch or by Image
     * chat's AI coming and going, shows at least one feature.
     */
    @Test
    fun noCombinationLeavesNone() {
        data class State(val features: List<Boolean>, val imageChatAvailable: Boolean)

        val keys = listOf(
            "image_chat_enabled", "text_chat_enabled", "discover_enabled",
            "find_enabled", "scan_light_enabled", "scan_colour_enabled",
        )
        fun open(state: State): SettingsManager =
            settings(SettingsUpgradeTest.FakePrefs(keys.zip(state.features).toMap()))
                .also { it.setImageChatAvailable(state.imageChatAvailable) }
        fun SettingsManager.state(imageChatAvailable: Boolean) = State(
            listOf(
                imageChatEnabled.value, textChatEnabled.value, discoverEnabled.value,
                findEnabled.value, scanLightEnabled.value, scanColourEnabled.value,
            ),
            imageChatAvailable,
        )
        val switches: List<(SettingsManager, Boolean) -> Boolean> =
            listOf<(SettingsManager, Boolean) -> Boolean>(SettingsManager::setImageChat) + localSwitches

        val start = State(List(6) { true }, imageChatAvailable = false)
        val seen = mutableSetOf(start)
        val queue = ArrayDeque(listOf(start))
        // Each step: one switch flipped, or Image chat's AI coming or going.
        val steps: List<(SettingsManager, State) -> Boolean> =
            switches.mapIndexed { i, switch ->
                { s: SettingsManager, state: State -> switch(s, !state.features[i]); state.imageChatAvailable }
            } + { s: SettingsManager, state: State ->
                s.setImageChatAvailable(!state.imageChatAvailable)
                !state.imageChatAvailable
            }
        while (queue.isNotEmpty()) {
            val state = queue.removeFirst()
            steps.forEach { step ->
                val s = open(state)
                val available = step(s, state)
                assertTrue("Nothing shown after a step from $state", s.shownFeatures(available).isNotEmpty())
                val reached = s.state(available)
                if (seen.add(reached)) queue.addLast(reached)
            }
        }
        // Image chat alone was among them.
        assertTrue(seen.any { it.imageChatAvailable && it.features == listOf(true, false, false, false, false, false) })
    }
}
