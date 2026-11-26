package com.example.sightbuddy.core

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * The app's display language, chosen once during onboarding and applied to the
 * whole resource stack.
 *
 * Kept out of [SettingsManager] deliberately: the choice has to be read in
 * `attachBaseContext`, before any UI or view model exists, so it is a plain
 * SharedPreferences lookup with no coroutine machinery behind it.
 *
 * Applying the locale through [wrap] means every localised resource follows
 * automatically — `values-fi` for text, including the spoken direction cues —
 * without any per-call-site language branching.
 */
class LanguageStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** False until the user has made the mandatory onboarding choice. */
    fun hasChosen(): Boolean = prefs.contains(KEY_LANGUAGE)

    fun language(): String = prefs.getString(KEY_LANGUAGE, LANG_EN) ?: LANG_EN

    fun isFinnish(): Boolean = language() == LANG_FI

    fun isEnglish(): Boolean = language() == LANG_EN

    fun supportsWhisper(): Boolean = supportsWhisper(language())

    fun setLanguage(tag: String) {
        prefs.edit().putString(KEY_LANGUAGE, tag).apply()
    }

    companion object {
        private const val PREFS = "sight_buddy_language"
        private const val KEY_LANGUAGE = "app_language"

        const val LANG_EN = "en"
        const val LANG_FI = "fi"
        const val LANG_PL = "pl"

        fun supportsWhisper(tag: String): Boolean = tag in setOf(LANG_EN, LANG_FI, LANG_PL)

        fun nextLanguage(tag: String): String = when (tag) {
            LANG_EN -> LANG_FI
            LANG_FI -> LANG_PL
            else -> LANG_EN
        }

        fun languageNameRes(tag: String): Int = when (tag) {
            LANG_FI -> com.example.sightbuddy.R.string.language_name_finnish
            LANG_PL -> com.example.sightbuddy.R.string.language_name_polish
            else -> com.example.sightbuddy.R.string.language_name_english
        }

        fun localeOf(tag: String): Locale = Locale.forLanguageTag(tag)

        /** Returns [base] re-configured so all resources resolve in [tag]. */
        fun wrap(base: Context, tag: String): Context {
            val locale = localeOf(tag)
            Locale.setDefault(locale)
            val config = Configuration(base.resources.configuration)
            config.setLocale(locale)
            config.setLayoutDirection(locale)
            return base.createConfigurationContext(config)
        }
    }
}
