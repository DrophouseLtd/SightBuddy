package com.example.sightbuddy.di

/**
 * Provides a persistent device/install identifier for quota tracking.
 *
 * - **dev** → deterministic mock ID (safe for local testing)
 * - **prod** → Android SSAID (survives uninstalls, unique per signing key + user)
 */
interface DeviceIdProvider {
    fun getId(): String
}
