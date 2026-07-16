package com.example.sightbuddy.di

import android.content.Context
import android.provider.Settings
import android.util.Log

private const val TAG = "ProdEnvModule"

private class SsaidDeviceIdProvider(context: Context) : DeviceIdProvider {
    private val id: String = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ANDROID_ID,
    ) ?: "unknown-ssaid"

    override fun getId(): String = id
}

/**
 * No-op: the cloud backend (and with it Play Integrity verification) was
 * decommissioned in v2.0.0 — the app is fully local.
 */
private class NoOpIntegrityTokenProvider : IntegrityTokenProvider {
    override fun getToken(): String = ""
}

private class PlayInAppUpdateChecker(context: Context) : InAppUpdateChecker {
    private val appContext = context.applicationContext

    override fun checkForUpdate(activity: android.app.Activity) {
        val manager = com.google.android.play.core.appupdate.AppUpdateManagerFactory.create(appContext)
        manager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() ==
                com.google.android.play.core.install.model.UpdateAvailability.UPDATE_AVAILABLE &&
                info.isUpdateTypeAllowed(com.google.android.play.core.install.model.AppUpdateType.FLEXIBLE)
            ) {
                try {
                    manager.startUpdateFlowForResult(
                        info,
                        com.google.android.play.core.install.model.AppUpdateType.FLEXIBLE,
                        activity,
                        REQUEST_CODE_UPDATE,
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "In-app update flow failed to start", e)
                }
            }
        }.addOnFailureListener {
            Log.w(TAG, "Update check failed", it)
        }
    }

    companion object {
        private const val REQUEST_CODE_UPDATE = 9001
    }
}

fun createDeviceIdProvider(context: Context): DeviceIdProvider =
    SsaidDeviceIdProvider(context)

fun createIntegrityTokenProvider(@Suppress("UNUSED_PARAMETER") context: Context): IntegrityTokenProvider =
    NoOpIntegrityTokenProvider()

fun createInAppUpdateChecker(context: Context): InAppUpdateChecker =
    PlayInAppUpdateChecker(context)
