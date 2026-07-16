package com.example.sightbuddy.di

import android.app.Activity
import android.content.Context

private class MockDeviceIdProvider : DeviceIdProvider {
    override fun getId(): String = "00000000-dev-mock-0000-000000000000"
}

private class MockIntegrityTokenProvider : IntegrityTokenProvider {
    override fun getToken(): String = "test-token"
}

private class NoOpUpdateChecker : InAppUpdateChecker {
    override fun checkForUpdate(@Suppress("UNUSED_PARAMETER") activity: Activity) {}
}

fun createDeviceIdProvider(@Suppress("UNUSED_PARAMETER") context: Context): DeviceIdProvider =
    MockDeviceIdProvider()

fun createIntegrityTokenProvider(@Suppress("UNUSED_PARAMETER") context: Context): IntegrityTokenProvider =
    MockIntegrityTokenProvider()

fun createInAppUpdateChecker(@Suppress("UNUSED_PARAMETER") context: Context): InAppUpdateChecker =
    NoOpUpdateChecker()
