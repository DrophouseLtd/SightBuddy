package com.example.sightbuddy.di

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityManager
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenProvider
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.TimeUnit

private const val TAG = "ProdEnvModule"
private const val CLOUD_PROJECT_NUMBER = 460898507558L

private class SsaidDeviceIdProvider(context: Context) : DeviceIdProvider {
    private val id: String = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ANDROID_ID,
    ) ?: "unknown-ssaid"

    override fun getId(): String = id
}

/**
 * Requests real Play Integrity tokens via the Standard API.
 *
 * Warm-up ([prepareIntegrityToken]) runs eagerly in the constructor on a
 * background thread. By the time the user triggers an AI call the provider
 * is usually ready; if not, [getToken] returns empty and the server rejects
 * the request with 403 (the app shows "AI service error").
 */
private class PlayIntegrityTokenProvider(context: Context) : IntegrityTokenProvider {
    private val manager: StandardIntegrityManager =
        IntegrityManagerFactory.createStandard(context.applicationContext)

    @Volatile
    private var provider: StandardIntegrityTokenProvider? = null

    init {
        manager.prepareIntegrityToken(
            StandardIntegrityManager.PrepareIntegrityTokenRequest.builder()
                .setCloudProjectNumber(CLOUD_PROJECT_NUMBER)
                .build(),
        ).addOnSuccessListener { provider = it }
            .addOnFailureListener { Log.e(TAG, "Integrity warm-up failed", it) }
    }

    override fun getToken(): String {
        val p = provider
        if (p == null) {
            Log.w(TAG, "Integrity provider not ready — warm-up may still be in progress")
            return ""
        }
        return try {
            val response = Tasks.await(
                p.request(
                    StandardIntegrityManager.StandardIntegrityTokenRequest.builder().build(),
                ),
                10, TimeUnit.SECONDS,
            )
            response.token()
        } catch (e: Exception) {
            Log.e(TAG, "Integrity token request failed", e)
            ""
        }
    }
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

fun createIntegrityTokenProvider(context: Context): IntegrityTokenProvider =
    PlayIntegrityTokenProvider(context)

fun createInAppUpdateChecker(context: Context): InAppUpdateChecker =
    PlayInAppUpdateChecker(context)
