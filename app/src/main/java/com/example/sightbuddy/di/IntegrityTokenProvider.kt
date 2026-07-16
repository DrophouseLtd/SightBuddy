package com.example.sightbuddy.di

/**
 * Supplies an integrity token sent with every Supabase Edge request.
 *
 * - **dev** → static `"test-token"` (accepted only when the Edge project has
 *   `ALLOW_TEST_INTEGRITY_BYPASS=true`)
 * - **prod** → Play Integrity Standard API via [EnvironmentModule] in the prod flavor
 */
interface IntegrityTokenProvider {
    fun getToken(): String
}
