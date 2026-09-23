package com.example.sightbuddy.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.sightbuddy.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * Keeps a model download alive while the app is in the background.
 *
 * Without it Android pauses the app as soon as the phone locks or the user
 * switches away, and a 2.6 GB download stalls. The download itself still runs in
 * the app's own coroutine; this service only holds the process in the foreground,
 * with a notification showing progress, for as long as [track] runs.
 */
class ModelDownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watcher: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.download_channel), NotificationManager.IMPORTANCE_LOW)
        )
        // Always go foreground first: a service started with startForegroundService
        // that stops without doing so crashes the app, and a download that was
        // already complete ends before the service even starts.
        val first = state.value ?: State(getString(R.string.download_channel), null)
        startForeground(NOTIFICATION_ID, build(first), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        if (state.value == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (watcher == null) {
            watcher = scope.launch {
                state.collect { s ->
                    if (s == null) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        manager.notify(NOTIFICATION_ID, build(s))
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun build(s: State): Notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(R.string.download_notification_title, s.title))
            .setContentText(s.percent?.let { "$it %" } ?: "")
            .setProgress(100, s.percent ?: 0, s.percent == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    private data class State(val title: String, val percent: Int?)

    companion object {
        private const val TAG = "ModelDownloadService"
        private const val CHANNEL = "model_downloads"
        private const val NOTIFICATION_ID = 42

        private val state = MutableStateFlow<State?>(null)

        /** Downloads running now; the service stops when the last one ends. */
        private val active = AtomicInteger()

        /**
         * Runs [block] (a download) with the service holding the app in the
         * foreground, showing [title] and the percentages from [progress].
         */
        suspend fun <T> track(
            context: Context,
            title: String,
            progress: Flow<Int?>,
            block: suspend () -> T,
        ): T = coroutineScope {
            active.incrementAndGet()
            state.value = State(title, null)
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, ModelDownloadService::class.java))
            }.onFailure { Log.w(TAG, "Could not start the download service", it) }
            val follow = launch { progress.collect { p -> if (p != null) state.value = State(title, p) } }
            try {
                block()
            } finally {
                follow.cancel()
                if (active.decrementAndGet() == 0) state.value = null
            }
        }
    }
}
