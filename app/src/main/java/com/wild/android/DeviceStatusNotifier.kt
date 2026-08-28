package com.wild.android

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.wild.android.ble.BleHostSessionState
import com.wild.android.ble.DeviceSessionUiState

/**
 * Turns significant device transitions into quiet, rate-limited Android notifications.
 *
 * Live sample and telemetry updates are intentionally ignored: the operator is
 * notified only when a compatible device appears, a device links, loses its
 * link, changes recording state, or reports a new failure. Notifications are
 * deliberately silent: the live console remains the primary operating view,
 * while the notification shade is a compact history when the app is in the
 * background.
 */
class DeviceStatusNotifier(
    private val context: Context,
) {
    private val notificationManager: NotificationManager by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.getSystemService(NotificationManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        }
    }
    private var hasBaseline = false
    private var snapshots = emptyMap<String, DeviceStatusSnapshot>()
    private val lastAlertAtMs = mutableMapOf<String, Long>()

    init {
        createChannel()
    }

    fun observe(sessions: Collection<DeviceSessionUiState>) {
        val nextSnapshots = sessions.associate { session ->
            session.id to DeviceStatusSnapshot.from(session)
        }
        if (!hasBaseline) {
            snapshots = nextSnapshots
            hasBaseline = true
            return
        }

        sessions.forEach { session ->
            val previous = snapshots[session.id]
            val alert = when {
                previous == null -> deviceInRangeAlertFor(session)
                !previous.eligibleForRangeAlert && session.bulkConnectEligible ->
                    deviceInRangeAlertFor(session)
                else -> deviceStatusAlertForTransition(previous, session)
            } ?: return@forEach
            if (shouldShow(alert, session.id)) {
                show(alert, session)
            }
        }
        snapshots = nextSnapshots
    }

    private fun show(alert: DeviceStatusAlert, session: DeviceSessionUiState) {
        if (!canPostNotifications()) {
            return
        }
        createChannel()
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            alert.notificationId(session.id),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, ChannelId)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(alert.title)
            .setContentText(alert.message(deviceAlertDeviceName(session)))
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    alert.message(deviceAlertDeviceName(session)),
                ),
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()
        notificationManager.notify(alert.notificationId(session.id), notification)
    }

    private fun shouldShow(alert: DeviceStatusAlert, deviceId: String): Boolean {
        val nowMs = System.currentTimeMillis()
        val key = "$deviceId:${alert.kind.name}"
        val previousAtMs = lastAlertAtMs[key]
        if (previousAtMs != null && nowMs - previousAtMs < alert.cooldownMs()) {
            return false
        }
        lastAlertAtMs[key] = nowMs
        return true
    }

    private fun canPostNotifications(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        notificationManager.createNotificationChannel(
            NotificationChannel(
                ChannelId,
                "WILD device status",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Quiet, rate-limited status updates for WILD devices."
                enableVibration(false)
            },
        )
    }

    private companion object {
        // A new channel ID makes the quieter default take effect on phones
        // where the previous audible channel has already been created.
        const val ChannelId = "wild_device_status_v2"
    }
}

internal data class DeviceStatusSnapshot(
    val linked: Boolean,
    val recording: Boolean,
    val failure: String,
    val eligibleForRangeAlert: Boolean,
) {
    companion object {
        fun from(session: DeviceSessionUiState): DeviceStatusSnapshot {
            return DeviceStatusSnapshot(
                linked = session.isConnected,
                recording = session.isRecordingLike,
                failure = session.lastFailure,
                eligibleForRangeAlert = session.bulkConnectEligible,
            )
        }
    }
}

internal enum class DeviceStatusAlertKind {
    DeviceInRange,
    Connected,
    ConnectionLost,
    RecordingStarted,
    RecordingStopped,
    Problem,
}

internal data class DeviceStatusAlert(
    val kind: DeviceStatusAlertKind,
    val detail: String = "",
) {
    val title: String
        get() = when (kind) {
            DeviceStatusAlertKind.DeviceInRange -> "New WILD device in range"
            DeviceStatusAlertKind.Connected -> "Device connected"
            DeviceStatusAlertKind.ConnectionLost -> "Device connection lost"
            DeviceStatusAlertKind.RecordingStarted -> "Recording started"
            DeviceStatusAlertKind.RecordingStopped -> "Recording stopped"
            DeviceStatusAlertKind.Problem -> "Device needs attention"
        }

    fun message(deviceName: String): String {
        return when (kind) {
            DeviceStatusAlertKind.DeviceInRange -> "$deviceName is advertising nearby."
            DeviceStatusAlertKind.Connected -> "$deviceName is linked and ready."
            DeviceStatusAlertKind.ConnectionLost -> "$deviceName lost its BLE link and is reconnecting."
            DeviceStatusAlertKind.RecordingStarted -> "$deviceName is recording."
            DeviceStatusAlertKind.RecordingStopped -> "$deviceName stopped recording."
            DeviceStatusAlertKind.Problem -> "$deviceName: $detail"
        }
    }

    fun notificationId(deviceId: String): Int {
        return "${deviceId}_${kind.name}".hashCode()
    }

    fun cooldownMs(): Long {
        return when (kind) {
            DeviceStatusAlertKind.DeviceInRange -> NewDeviceCooldownMs
            DeviceStatusAlertKind.Problem -> ProblemCooldownMs
            else -> RoutineStatusCooldownMs
        }
    }

    private companion object {
        const val RoutineStatusCooldownMs = 2 * 60 * 1_000L
        const val ProblemCooldownMs = 5 * 60 * 1_000L
        const val NewDeviceCooldownMs = 30 * 60 * 1_000L
    }
}

internal fun deviceInRangeAlertFor(
    session: DeviceSessionUiState,
): DeviceStatusAlert? {
    return if (session.bulkConnectEligible) {
        DeviceStatusAlert(DeviceStatusAlertKind.DeviceInRange)
    } else {
        null
    }
}

internal fun deviceStatusAlertForTransition(
    previous: DeviceStatusSnapshot,
    current: DeviceSessionUiState,
): DeviceStatusAlert? {
    val currentSnapshot = DeviceStatusSnapshot.from(current)
    return when {
        currentSnapshot.failure.isNotBlank() && currentSnapshot.failure != previous.failure ->
            DeviceStatusAlert(DeviceStatusAlertKind.Problem, currentSnapshot.failure)

        previous.linked && !currentSnapshot.linked &&
            current.hostState in setOf(BleHostSessionState.Reconnecting, BleHostSessionState.Error) ->
            DeviceStatusAlert(DeviceStatusAlertKind.ConnectionLost)

        !previous.recording && currentSnapshot.recording ->
            DeviceStatusAlert(DeviceStatusAlertKind.RecordingStarted)

        previous.recording && !currentSnapshot.recording && currentSnapshot.linked ->
            DeviceStatusAlert(DeviceStatusAlertKind.RecordingStopped)

        !previous.linked && currentSnapshot.linked ->
            DeviceStatusAlert(DeviceStatusAlertKind.Connected)

        else -> null
    }
}

private fun deviceAlertDeviceName(session: DeviceSessionUiState): String {
    return session.name.trim().takeIf { it.isNotBlank() } ?: session.address
}
