package com.wild.android

import android.app.Application
import com.wild.android.ble.BleHostSessionState
import com.wild.android.ble.Ce32BleManager
import com.wild.android.ble.DeviceSessionUiState
import com.wild.android.cloud.FirebaseFleetGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class WildApplication : Application() {
    val appScope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }

    val bleManager: Ce32BleManager by lazy {
        Ce32BleManager(applicationContext, appScope)
    }

    val cloudFleetGateway: FirebaseFleetGateway by lazy {
        FirebaseFleetGateway(applicationContext, appScope)
    }

    val appUpdateChecker: AppUpdateChecker by lazy {
        AppUpdateChecker(applicationContext)
    }

    private val deviceStatusNotifier: DeviceStatusNotifier by lazy {
        DeviceStatusNotifier(applicationContext)
    }

    private val uiForeground = MutableStateFlow(false)
    @Volatile
    private var fullExitRequested = false

    override fun onCreate() {
        super.onCreate()

        appScope.launch {
            bleManager.sessions.collect { sessions ->
                deviceStatusNotifier.observe(sessions.values)
                cloudFleetGateway.publishLocalFleet(sessions.values)
            }
        }

        appScope.launch {
            combine(uiForeground, bleManager.isScanning, bleManager.sessions) { _, _, _ ->
                shouldKeepBackgroundBleRuntime()
            }
                .distinctUntilChanged()
                .collect { keepAlive ->
                    if (keepAlive) {
                        BleForegroundService.start(this@WildApplication)
                    } else {
                        BleForegroundService.stop(this@WildApplication)
                    }
                }
        }
    }

    fun setUiForeground(foreground: Boolean) {
        if (foreground) {
            fullExitRequested = false
        }
        uiForeground.value = foreground
    }

    fun prepareForFullExit() {
        fullExitRequested = true
        uiForeground.value = false
        BleForegroundService.stop(this)
    }

    fun shouldKeepBackgroundBleRuntime(): Boolean {
        return !uiForeground.value && shouldPreserveBleWorkAcrossUiExit()
    }

    fun shouldPreserveBleWorkAcrossUiExit(): Boolean {
        return !fullExitRequested && (
            bleManager.isScanning.value ||
                bleManager.sessions.value.values.any(::shouldKeepBleRuntimeAlive)
            )
    }

    private fun shouldKeepBleRuntimeAlive(session: DeviceSessionUiState): Boolean {
        return session.isConnected ||
            session.isLinkingLike ||
            session.hostState in setOf(
                BleHostSessionState.Syncing,
                BleHostSessionState.Previewing,
                BleHostSessionState.StartingRecording,
                BleHostSessionState.Recording,
                BleHostSessionState.StoppingRecording,
                BleHostSessionState.Reconnecting,
            )
    }
}
