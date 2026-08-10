package com.wild.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.wild.android.ble.BleHostSessionState
import com.wild.android.ble.DeviceSessionUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class BleForegroundService : Service() {
    private var notificationJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val app = application as WildApplication
        // Android can enforce the foreground-service deadline before the first
        // onStartCommand callback on older vendor builds, so promote immediately.
        startForeground(
            NotificationId,
            buildNotification(
                buildNotificationState(
                    scanning = app.bleManager.isScanning.value,
                    sessions = app.bleManager.sessions.value.values.toList(),
                ),
            ),
        )
        notificationJob = app.appScope.launch {
            combine(app.bleManager.isScanning, app.bleManager.sessions) { scanning, sessions ->
                buildNotificationState(scanning, sessions.values.toList())
            }
                .distinctUntilChanged()
                .collect { state ->
                    val manager = notificationManagerCompat()
                    manager.notify(NotificationId, buildNotification(state))
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as WildApplication
        val state = buildNotificationState(
            scanning = app.bleManager.isScanning.value,
            sessions = app.bleManager.sessions.value.values.toList(),
        )
        startForeground(NotificationId, buildNotification(state))
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        restartIfBleWorkShouldSurvive("task_removed")
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        notificationJob?.cancel()
        notificationJob = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        restartIfBleWorkShouldSurvive("destroy")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(state: NotificationState): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, ChannelId)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("WILD Android")
            .setContentText(state.summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(state.details))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channel = NotificationChannel(
            ChannelId,
            "BLE background runtime",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Keeps CE32 / CE64 BLE connections active when the UI is not on screen."
        }
        notificationManagerCompat().createNotificationChannel(channel)
    }

    private fun buildNotificationState(
        scanning: Boolean,
        sessions: List<DeviceSessionUiState>,
    ): NotificationState {
        val connectedCount = sessions.count { it.isConnected }
        val previewCount = sessions.count { it.hostState == BleHostSessionState.Previewing }
        val recordingCount = sessions.count {
            it.hostState == BleHostSessionState.Recording || it.hostState == BleHostSessionState.StartingRecording
        }
        val activeNames = sessions.filter { it.isConnected }.joinToString { it.name }
        val summary = buildString {
            when {
                recordingCount > 0 -> append("$recordingCount recording")
                previewCount > 0 -> append("$previewCount previewing")
                connectedCount > 0 -> append("$connectedCount connected")
                scanning -> append("Scanning for devices")
                else -> append("BLE runtime active")
            }
        }
        val details = buildString {
            if (connectedCount > 0) {
                append("Connected: ")
                append(activeNames)
            } else if (scanning) {
                append("Scanning for nearby CE32 / CE64 / WILD devices.")
            } else {
                append("Waiting for BLE activity.")
            }
        }
        return NotificationState(summary = summary, details = details)
    }

    private data class NotificationState(
        val summary: String,
        val details: String,
    )

    private fun restartIfBleWorkShouldSurvive(reason: String) {
        val app = application as? WildApplication ?: return
        if (app.shouldKeepBackgroundBleRuntime()) {
            Log.d("BleForegroundService", "restart requested after $reason")
            start(this)
        }
    }

    private fun notificationManagerCompat(): NotificationManager {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getSystemService(NotificationManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        }
    }

    companion object {
        private const val ChannelId = "wild_ble_runtime"
        private const val NotificationId = 3201

        fun start(context: Context) {
            val intent = Intent(context, BleForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BleForegroundService::class.java))
        }
    }
}
