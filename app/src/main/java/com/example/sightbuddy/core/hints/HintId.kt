package com.example.sightbuddy.core.hints

/**
 * One-time automated hints. Each id is shown at most once per install unless app data is cleared.
 */
enum class HintId(val prefKey: String) {
    TEXT_CHAT_PLAYBACK_CONTROLS("text_chat_playback_controls"),
}
