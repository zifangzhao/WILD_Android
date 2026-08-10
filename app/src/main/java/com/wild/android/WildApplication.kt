package com.wild.android

import android.app.Application
import com.wild.android.ble.BleHostSessionState
import com.wild.android.ble.Ce32BleManager
import com.wild.android.ble.DeviceSessionUiState
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

    private val uiForeground = MutableStateFlow(false)

    override fun onCreate() {
        super.onCreate()

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
        uiForeground.value = foreground
    }

    fun shouldKeepBackgroundBleRuntime(): Boolean {
        return !uiForeground.value && shouldPreserveBleWorkAcrossUiExit()
    }

    fun shouldPreserveBleWorkAcrossUiExit(): Boolean {
        return bleManager.isScanning.value ||
            bleManager.sessions.value.values.any(::shouldKeepBleRuntimeAlive)
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
