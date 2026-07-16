package com.example.sightbuddy.core

import android.content.Context

/** Persists acceptance of privacy policy and terms of use (required before first tutorial). */
class TermsStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasAcceptedTerms(): Boolean = prefs.getBoolean(KEY_TERMS_ACCEPTED, false)

    fun markTermsAccepted() {
        prefs.edit().putBoolean(KEY_TERMS_ACCEPTED, true).apply()
    }

    companion object {
        private const val PREFS = "sight_buddy_terms"
        private const val KEY_TERMS_ACCEPTED = "terms_accepted"
        const val TERMS_OF_USE_URL =
            "https://www.drophouse.uk/products/sightbuddy/termsofuse"
        const val PRIVACY_POLICY_URL =
            "https://www.drophouse.uk/products/sightbuddy/privacypolicy"
    }
}
