package com.wild.android.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.time.ZonedDateTime
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt
import org.json.JSONArray
import org.json.JSONObject

private const val RssiHistoryWindowMs = 5 * 60 * 1_000L
private const val RssiSampleIntervalMs = 500L
private const val RssiHistoryMaxSamples = 600

internal fun previewPrimeDelayForInboundCommand(commandId: Int): Long? {
    return when (commandId) {
        0x82 -> 175L
        0x90 -> 225L
        0x91, 0x92 -> 275L
        else -> null
    }
}

internal fun shouldPromoteSystemParamSyncFallback(
    initialSyncCompleted: Boolean,
    initialSyncStarted: Boolean,
    legacyReadyPromoted: Boolean,
): Boolean {
    if (initialSyncCompleted || !initialSyncStarted) {
        return false
    }
    if (legacyReadyPromoted) {
        return true
    }
    // Desktop parity: ordinary system-parameter reads should not be treated as
    // proof that the device has finished the time-sync handshake.
    return false
}

internal fun appendRssiHistorySample(
    history: List<RssiSampleUiState>,
    rssiDbm: Int,
    timestampMs: Long,
): List<RssiSampleUiState> {
    val cutoffMs = timestampMs - RssiHistoryWindowMs
    val retained = history.filter { sample -> sample.timestampMs >= cutoffMs }
    if (rssiDbm >= 0 || retained.lastOrNull()?.let { timestampMs - it.timestampMs < RssiSampleIntervalMs } == true) {
        return retained
    }

    return (retained + RssiSampleUiState(timestampMs, rssiDbm))
        .takeLast(RssiHistoryMaxSamples)
}

class Ce32BleManager(
    context: Context,
    private val parentScope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val bluetoothManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        appContext.getSystemService(BluetoothManager::class.java)
    } else {
        @Suppress("DEPRECATION")
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }
    private val ioScope = CoroutineScope(parentScope.coroutineContext + SupervisorJob() + Dispatchers.IO)
    private val handles = linkedMapOf<String, SessionHandle>()
    private val tracePalette = listOf(
        0xFF238CF6.toInt(),
        0xFF2BA66B.toInt(),
        0xFFF08A18.toInt(),
        0xFF7C3AED.toInt(),
        0xFFFF2D55.toInt(),
        0xFFD94343.toInt(),
    )
    private var traceCursor = 0
    private var manualConnectInProgressCount = 0
    private var manualConnectBatchDepth = 0
    private var reconnectScanPauseDepth = 0
    private var resumeScanAfterManualConnect = false
    private var lastScanStartAtMs = 0L
    private var lastScanResultAtMs = 0L
    private val discoveryCachePrefs: SharedPreferences =
        appContext.getSharedPreferences(DiscoveryCachePrefsName, Context.MODE_PRIVATE)
    @Volatile
    private var cancelFleetConnectRequested = false
    private val syncLogLock = Any()
    private val syncLogSessionId = Instant.now().toString().replace(":", "").replace("-", "")
    private val bulkConnectMutex = Mutex()

    private val _sessions = MutableStateFlow<Map<String, DeviceSessionUiState>>(emptyMap())
    val sessions: StateFlow<Map<String, DeviceSessionUiState>> = _sessions.asStateFlow()

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _statusBanner = MutableStateFlow("")
    val statusBanner: StateFlow<String> = _statusBanner.asStateFlow()

    private val _fleetConnectActive = MutableStateFlow(false)
    val fleetConnectActive: StateFlow<Boolean> = _fleetConnectActive.asStateFlow()

    private val _fleetConnectPendingCount = MutableStateFlow(0)
    val fleetConnectPendingCount: StateFlow<Int> = _fleetConnectPendingCount.asStateFlow()

    init {
        restoreCachedDiscoverySessions()
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            lastScanResultAtMs = System.currentTimeMillis()
            upsertDiscoveredDevice(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            if (results.isNotEmpty()) {
                lastScanResultAtMs = System.currentTimeMillis()
            }
            results.forEach(::upsertDiscoveredDevice)
        }

        override fun onScanFailed(errorCode: Int) {
            _isScanning.value = false
            _statusBanner.value = "BLE scan failed: $errorCode"
        }
    }

    val adapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    fun setActiveSession(deviceId: String) {
        setActiveSessionInternal(deviceId)
    }

    private fun setActiveSessionInternal(deviceId: String?) {
        _activeSessionId.value = deviceId
        _sessions.update { sessions ->
            sessions.mapValues { (id, session) ->
                session.copy(isActive = id == deviceId)
            }
        }
    }

    fun clearBanner() {
        _statusBanner.value = ""
    }

    fun clearRssiHistory() {
        _sessions.update { sessions ->
            sessions.mapValues { (_, session) -> session.copy(rssiHistory = emptyList()) }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            _statusBanner.value = "Bluetooth LE scanner is unavailable."
            return
        }

        if (_isScanning.value) {
            val nowMs = System.currentTimeMillis()
            val lastActivityAtMs = maxOf(lastScanStartAtMs, lastScanResultAtMs)
            if (lastActivityAtMs > 0L && nowMs - lastActivityAtMs < ScanRestartStallMs) {
                return
            }
            Log.w("Ce32BleManager", "scan restart requested after ${nowMs - lastActivityAtMs}ms without results")
            runCatching { scanner.stopScan(scanCallback) }
        }

        _statusBanner.value = ""
        _isScanning.value = true
        lastScanStartAtMs = System.currentTimeMillis()
        scanner.startScan(
            emptyList(),
            ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build(),
            scanCallback,
        )
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        _isScanning.value = false
        lastScanStartAtMs = 0L
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(
        deviceId: String,
        makeActive: Boolean = true,
        fromReconnect: Boolean = false,
        batchConnect: Boolean = false,
        fastBootstrap: Boolean = false,
    ) {
        val handle = handles[deviceId]
        if (handle == null) {
            _statusBanner.value = "Unknown BLE device."
            return
        }

        val sessionBeforeConnect = currentState(deviceId)
        Log.d(
            "Ce32BleManager",
            "connect request id=$deviceId hostState=${sessionBeforeConnect?.hostState} status=${sessionBeforeConnect?.statusText} fromReconnect=$fromReconnect batchConnect=$batchConnect fastBootstrap=$fastBootstrap",
        )

        if (sessionBeforeConnect?.hostState in setOf(
                BleHostSessionState.Connecting,
                BleHostSessionState.Connected,
                BleHostSessionState.Syncing,
                BleHostSessionState.Synced,
                BleHostSessionState.Previewing,
                BleHostSessionState.StartingRecording,
                BleHostSessionState.Recording,
                BleHostSessionState.StoppingRecording,
            )
        ) {
            Log.d("Ce32BleManager", "connect skipped for $deviceId because hostState=${sessionBeforeConnect?.hostState}")
            return
        }

        if (!fromReconnect) {
            cancelReconnectFlow(handle)
            handle.cameraPreviewStreamingEnabled = false
            beginManualConnectPhase(handle)
            if (!batchConnect) {
                pauseScanForManualConnect()
                pausePreviewSessionsBeforeAdditionalConnect(excludingDeviceIds = setOf(deviceId))
            }
        }

        handle.disconnectRequestedByUser = false
        handle.expectedDisconnectReason = ""
        handle.fastBootstrapRequested = fastBootstrap
        stopConnectWatchdog(handle)
        val hadExistingGatt = handle.gatt != null
        try {
            handle.gatt?.close()
        } catch (_: Throwable) {
        }
        handle.gatt = null
        handle.dataService = null
        handle.txCharacteristic = null
        handle.legacyTxCharacteristic = null
        handle.rxCharacteristic = null
        cancelMtuNegotiationFallback(handle)
        handle.mtu = 23
        handle.mtuNegotiationPending = false
        handle.previewBuffer.clear()
        handle.parser.reset()
        resetBleLinkStats(handle)
        stopCameraPreviewPolling(handle)
        stopInitialBootstrap(handle)
        clearRecordAckTimeouts(handle)
        clearPreviewFallbackTracking(handle)
        clearPreviewPrimeTracking(handle)
        // Preserve a fresh fast-bootstrap request until the initial bootstrap job consumes it.
        handle.legacyReadyPromoted = false
        handle.legacyHandshakeAckSeen = false
        handle.legacyConfigBusyUntilMs = 0L
        if (!fromReconnect) {
            handle.notificationRxCount = 0
            handle.legacyConfigBusyCount = 0
        }

        updateSession(deviceId) {
            appendEvent(
                it.copy(
                hostState = BleHostSessionState.Connecting,
                statusText = "Connecting...",
                syncText = "Sync: pending",
                lastFailure = "",
                bleLinkStats = null,
                notificationRxCount = if (fromReconnect) it.notificationRxCount else 0,
                legacyConfigBusyCount = if (fromReconnect) it.legacyConfigBusyCount else 0,
                cameraPreviewStreaming = if (fromReconnect) it.cameraPreviewStreaming else false,
                ),
                "Connect requested",
            )
        }

        if (makeActive) {
            setActiveSession(deviceId)
        }

        var connectCooldownMs = 0L
        if (resumeScanAfterManualConnect) {
            connectCooldownMs = maxOf(connectCooldownMs, scanPauseConnectGattCooldownMs())
        }
        if (fromReconnect) {
            connectCooldownMs = maxOf(connectCooldownMs, reconnectConnectGattCooldownMs())
        } else if (hadExistingGatt || sessionBeforeConnect?.hostState == BleHostSessionState.Error) {
            connectCooldownMs = maxOf(connectCooldownMs, retryConnectGattCooldownMs())
        }
        if (connectCooldownMs > 0L) {
            Log.d(
                "Ce32BleManager",
                "connect cooldown device=$deviceId waitMs=$connectCooldownMs fromReconnect=$fromReconnect priorState=${sessionBeforeConnect?.hostState}",
            )
            delay(connectCooldownMs)
        }

        val connectDevice = resolveConnectDevice(handle)
        val callback = SessionGattCallback(deviceId)
        val useTransportLeConnect = shouldUseTransportLeConnect(handle, fromReconnect)
        val useAutoConnect = shouldUseLegacyAutoConnect(handle, fromReconnect)
        val (gatt, transportLabel) = connectGattWithPreferredTransport(
            connectDevice = connectDevice,
            callback = callback,
            useAutoConnect = useAutoConnect,
            useTransportLeConnect = useTransportLeConnect,
        )
        Log.d(
            "Ce32BleManager",
            "connectGatt started for $deviceId transport=$transportLabel autoConnect=$useAutoConnect retryAttempt=${handle.reconnectAttemptCount} resolvedDevice=${connectDevice.address}",
        )
        handle.gatt = gatt
        startConnectWatchdog(handle, gatt)
    }

    @SuppressLint("MissingPermission")
    suspend fun connectSeries(deviceIds: List<String>, makeFirstActive: Boolean = true) {
        val orderedIds = deviceIds
            .distinct()
            .filter { deviceId ->
                currentState(deviceId)?.hostState !in setOf(
                    BleHostSessionState.Connecting,
                    BleHostSessionState.Connected,
                    BleHostSessionState.Syncing,
                    BleHostSessionState.Synced,
                    BleHostSessionState.Previewing,
                    BleHostSessionState.StartingRecording,
                    BleHostSessionState.Recording,
                    BleHostSessionState.StoppingRecording,
                )
            }
        if (orderedIds.isEmpty()) {
            return
        }

        bulkConnectMutex.withLock {
            cancelFleetConnectRequested = false
            _fleetConnectActive.value = true
            _fleetConnectPendingCount.value = orderedIds.size
            manualConnectBatchDepth += 1
            try {
                pauseScanForManualConnect()
                pausePreviewSessionsBeforeAdditionalConnect(excludingDeviceIds = orderedIds.toSet())
                for ((index, deviceId) in orderedIds.withIndex()) {
                    if (cancelFleetConnectRequested) {
                        _statusBanner.value = "Connect queue stopped."
                        break
                    }
                    connect(
                        deviceId = deviceId,
                        makeActive = makeFirstActive && index == 0,
                        fromReconnect = false,
                        batchConnect = true,
                    )
                    _fleetConnectPendingCount.value = (orderedIds.size - index - 1).coerceAtLeast(0)
                    waitForConnectBatchAdvance(deviceId)
                }
            } finally {
                manualConnectBatchDepth = (manualConnectBatchDepth - 1).coerceAtLeast(0)
                cancelFleetConnectRequested = false
                _fleetConnectActive.value = false
                _fleetConnectPendingCount.value = 0
                if (manualConnectBatchDepth == 0 && manualConnectInProgressCount == 0) {
                    resumeScanAfterManualConnectIfNeeded()
                    resumePausedPreviewAfterAdditionalConnect()
                }
            }
        }
    }

    fun cancelPendingFleetConnect() {
        if (!_fleetConnectActive.value) {
            return
        }
        cancelFleetConnectRequested = true
        _statusBanner.value = if (_fleetConnectPendingCount.value > 0) {
            "Stopping connect queue after the current link."
        } else {
            "Current link is in progress. Use Cancel Link to stop it."
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect(deviceId: String, reason: String = "Disconnected", userRequested: Boolean = true) {
        val handle = handles[deviceId] ?: return
        cancelReconnectFlow(handle)
        handle.cameraPreviewStreamingEnabled = false
        stopCameraPreviewPolling(handle)
        stopInitialBootstrap(handle)
        stopConnectWatchdog(handle)
        clearRecordAckTimeouts(handle)
        clearPreviewFallbackTracking(handle)
        clearPreviewPrimeTracking(handle)
        if (!handle.reconnectPending) {
            finishManualConnectPhase(handle)
        }
        handle.disconnectRequestedByUser = userRequested
        handle.expectedDisconnectReason = reason
        handle.periodicPackedTimeJob?.cancel()
        handle.periodicPackedTimeJob = null
        updateSession(deviceId) {
            appendEvent(
                it.copy(
                hostState = BleHostSessionState.Disconnecting,
                statusText = if (userRequested) "Disconnecting..." else it.statusText,
                ),
                if (userRequested) "Disconnect requested" else reason,
            )
        }
        handle.gatt?.disconnect() ?: run {
            finalizeDisconnect(deviceId, reason)
        }
    }

    suspend fun startPreview(deviceIds: List<String>) {
        val targets = connectedTargets(deviceIds)
        if (targets.isEmpty()) {
            _statusBanner.value = "No connected BLE device is available for preview."
            return
        }

        if (targets.any { handle -> currentState(handle.id)?.isAcquisitionDisabled() == true }) {
            _statusBanner.value = "Set sampling rate before preview."
            return
        }

        if (runPreviewGroupResyncIfNeeded(targets)) {
            return
        }

        val startTargets = targets.filter { handle ->
            currentState(handle.id)?.let(::canStartPreview) == true
        }
        if (startTargets.isEmpty()) {
            _statusBanner.value = "Preview start requires a BLE session that has reached syncing or synced state."
            return
        }
        if (targets.size > 1 && startTargets.size != targets.size) {
            _statusBanner.value = "Every selected device must finish sync before preview starts."
            return
        }

        startPreviewInternal(startTargets)
    }

    private suspend fun startPreviewInternal(targets: List<SessionHandle>) {
        for (handle in targets) {
            val previousState = currentState(handle.id)
            val rollbackState = previousState?.hostState ?: BleHostSessionState.Synced
            val rollbackStatusText = previousState?.statusText
                ?.takeIf { it.isNotBlank() }
                ?: statusTextForState(rollbackState)
            clearPreviewFallbackTracking(handle)
            updateSession(handle.id) {
                appendEvent(
                    it.copy(
                        hostState = BleHostSessionState.Previewing,
                        statusText = "Previewing",
                        lastFailure = "",
                        lastMessage = "Preview start requested",
                    ),
                    "Preview start requested",
                )
            }
            val selection = currentState(handle.id)?.previewSelection ?: PreviewSelection.Default
            val previewStartOk = writeCommand(handle, Ce32Protocol.buildPreviewStart(), "preview start") &&
                writeCommand(handle, Ce32Protocol.buildPreviewSelect(selection), "preview selection") &&
                writeCommand(handle, Ce32Protocol.buildPreviewStart(), "preview start")
            if (!previewStartOk) {
                updateSession(handle.id) {
                    appendEvent(
                        it.copy(
                            hostState = rollbackState,
                            statusText = rollbackStatusText,
                            lastFailure = "Preview start write failed",
                            lastMessage = "Preview start write failed",
                        ),
                        "Preview start write failed",
                    )
                }
                continue
            }

            tryQueuePreviewPrime(handle, initialDelayMs = 150)
            val baseline = currentState(handle.id)
            handle.previewFallbackPending = true
            handle.usesRecordingPreviewFallback = false
            startPreviewStartupMonitor(
                handle = handle,
                rollbackState = rollbackState,
                rollbackStatusText = rollbackStatusText,
                baselinePreviewPacketCount = baseline?.previewPacketCount ?: 0,
            )
        }
    }

    suspend fun stopPreview(deviceIds: List<String>) {
        val targets = connectedTargets(deviceIds)
        val fallbackTargets = targets.filter { handle ->
            handle.usesRecordingPreviewFallback
        }
        val directPreviewTargets = targets.filter { handle ->
            !handle.usesRecordingPreviewFallback &&
                currentState(handle.id)?.let(::canStopPreview) == true
        }
        if (fallbackTargets.isEmpty() && directPreviewTargets.isEmpty()) {
            _statusBanner.value = "No selected device is previewing."
            return
        }

        for (handle in directPreviewTargets) {
            clearPreviewFallbackTracking(handle)
            clearPreviewPrimeTracking(handle)
            writeCommand(handle, Ce32Protocol.buildPreviewStop(), "preview stop")
            updateSession(handle.id) {
                it.copy(hostState = BleHostSessionState.Synced, statusText = "Synced")
            }
        }

        stopRecordingInternal(fallbackTargets)
    }

    suspend fun startRecording(deviceIds: List<String>) {
        val targets = connectedTargets(deviceIds)
        if (targets.isEmpty()) {
            _statusBanner.value = "No connected BLE device is available for recording."
            return
        }

        if (targets.any { handle -> currentState(handle.id)?.isAcquisitionDisabled() == true }) {
            _statusBanner.value = "Set sampling rate before recording."
            return
        }

        val startTargets = targets.filter { handle ->
            currentState(handle.id)?.let(::canStartRecording) == true
        }
        if (startTargets.isEmpty()) {
            _statusBanner.value = "Recording start requires a synced or previewing BLE session."
            return
        }
        if (targets.size > 1 && startTargets.size != targets.size) {
            _statusBanner.value = "Every selected device must already be synced or previewing before recording starts."
            return
        }

        startRecordingInternal(startTargets, previewFallback = false)
    }

    suspend fun stopRecording(deviceIds: List<String>) {
        val stopTargets = connectedTargets(deviceIds).filter { handle ->
            currentState(handle.id)?.let(::canStopRecording) == true
        }
        if (stopTargets.isEmpty()) {
            _statusBanner.value = "No selected device is recording."
            return
        }

        stopRecordingInternal(stopTargets)
    }

    suspend fun forceStopRecording(deviceIds: List<String>) {
        val targets = connectedTargets(deviceIds)
        if (targets.isEmpty()) {
            _statusBanner.value = "No connected BLE device is available for a record stop."
            return
        }

        val acknowledgedTargets = targets.filter { handle ->
            currentState(handle.id)?.let(::canStopRecording) == true
        }
        if (acknowledgedTargets.isNotEmpty()) {
            stopRecordingInternal(acknowledgedTargets)
        }

        val forcedTargets = targets.filterNot { handle -> acknowledgedTargets.any { it.id == handle.id } }
        for (handle in forcedTargets) {
            val previousState = currentState(handle.id)
            val rollbackState = previousState?.hostState ?: BleHostSessionState.Previewing
            val rollbackStatusText = previousState?.statusText
                ?.takeIf { it.isNotBlank() }
                ?: statusTextForState(rollbackState)
            cancelRecordStartAckTimeout(handle)
            cancelRecordStopAckTimeout(handle)
            clearPreviewFallbackTracking(handle)
            clearPreviewPrimeTracking(handle)
            updateSession(handle.id) {
                appendEvent(
                    it.copy(
                        hostState = BleHostSessionState.StoppingRecording,
                        statusText = "Stopping recording...",
                        lastFailure = "",
                        lastMessage = "Forced record stop requested",
                    ),
                    "Forced record stop requested",
                )
            }
            val stopOk = writeCommand(
                handle,
                Ce32Protocol.buildRecordStopAndPreviewStop(),
                "record stop sequence",
            )
            handle.periodicPackedTimeJob?.cancel()
            handle.periodicPackedTimeJob = null
            if (!stopOk) {
                updateSession(handle.id) {
                    appendEvent(
                        it.copy(
                            hostState = rollbackState,
                            statusText = rollbackStatusText,
                            lastFailure = "Forced record stop write failed",
                            lastMessage = "Forced record stop write failed",
                        ),
                        "Forced record stop write failed",
                    )
                }
                continue
            }

            updateSession(handle.id) {
                appendEvent(
                    it.copy(
                        hostState = BleHostSessionState.Synced,
                        statusText = "Synced",
                        lastFailure = "",
                        lastMessage = "Forced record stop sent",
                    ),
                    "Forced record stop sent",
                )
            }
        }
    }

    private suspend fun startRecordingInternal(
        targets: List<SessionHandle>,
        previewFallback: Boolean,
    ) {
        for (handle in targets) {
            val previousState = currentState(handle.id)
            val rollbackState = previousState?.hostState ?: BleHostSessionState.Synced
            val rollbackStatusText = previousState?.statusText
                ?.takeIf { it.isNotBlank() }
                ?: statusTextForState(rollbackState)
            cancelPreviewStartupMonitor(handle)
            handle.previewFallbackPending = false
            handle.usesRecordingPreviewFallback = previewFallback
            updateSession(handle.id) {
                appendEvent(
                    it.copy(
                        hostState = BleHostSessionState.StartingRecording,
                        statusText = "Starting recording...",
                        recordingSeconds = 0L,
                        lastFailure = "",
                        lastMessage = if (previewFallback) "Preview fallback requested" else "Record start requested",
                    ),
                    if (previewFallback) "Preview fallback requested" else "Record start requested",
                )
            }
            val selection = currentState(handle.id)?.previewSelection ?: PreviewSelection.Default
            val recordStartOk = writeCommand(handle, Ce32Protocol.buildRecordStart(), "record start") &&
                writeCommand(handle, Ce32Protocol.buildPreviewStart(), "preview start during record") &&
                writeCommand(handle, Ce32Protocol.buildPreviewSelect(selection), "preview selection") &&
                writeCommand(handle, Ce32Protocol.buildPreviewStart(), "preview start")
            if (!recordStartOk) {
                updateSession(handle.id) {
                    appendEvent(
                        it.copy(
                            hostState = rollbackState,
                            statusText = rollbackStatusText,
                            lastFailure = "Record start write failed",
                            lastMessage = "Record start write failed",
                        ),
                        "Record start write failed",
                    )
                }
                handle.usesRecordingPreviewFallback = false
                continue
            }
            clearPreviewPrimeTracking(handle)
            handle.lastStopRecordingRequestAtMs = 0L
            startPackedTimeLoop(handle)
            startRecordStartAckTimeout(handle, rollbackState, rollbackStatusText)
        }
    }

    private suspend fun stopRecordingInternal(targets: List<SessionHandle>) {
        for (handle in targets) {
            val previousState = currentState(handle.id)
            val rollbackState = previousState?.hostState ?: BleHostSessionState.Recording
            val rollbackStatusText = previousState?.statusText
                ?.takeIf { it.isNotBlank() }
                ?: statusTextForState(rollbackState)
            cancelRecordStartAckTimeout(handle)
            cancelRecordStopAckTimeout(handle)
            clearPreviewFallbackTracking(handle)
            clearPreviewPrimeTracking(handle)
            updateSession(handle.id) {
                appendEvent(
                    it.copy(
                        hostState = BleHostSessionState.StoppingRecording,
                        statusText = "Stopping recording...",
                        lastFailure = "",
                        lastMessage = "Record stop requested",
                    ),
                    "Record stop requested",
                )
            }
            val stopOk = writeCommand(
                handle,
                Ce32Protocol.buildRecordStopAndPreviewStop(),
                "record stop sequence",
            )
            handle.periodicPackedTimeJob?.cancel()
            handle.periodicPackedTimeJob = null
            if (!stopOk) {
                updateSession(handle.id) {
                    appendEvent(
                        it.copy(
                            hostState = rollbackState,
                            statusText = rollbackStatusText,
                            lastFailure = "Record stop write failed",
                            lastMessage = "Record stop write failed",
                        ),
                        "Record stop write failed",
                    )
                }
                continue
            }
            handle.lastStopRecordingRequestAtMs = System.currentTimeMillis()
            startRecordStopAckTimeout(handle)
        }
    }

    suspend fun setPreviewSelection(deviceIds: List<String>, selection: PreviewSelection) {
        for (handle in connectedTargets(deviceIds)) {
            updateSession(handle.id) {
                it.copy(
                    previewSelection = selection,
                    parsedSystemParams = it.parsedSystemParams?.copy(
                        previewChannelBankRaw = selection.toProtocolByte().toInt() and 0xFF,
                    ),
                )
            }
            writeCommand(handle, Ce32Protocol.buildPreviewSelect(selection), "preview source select")
        }
    }

    suspend fun requestResync(deviceIds: List<String>, suppressRtcWrite: Boolean = false) {
        for (handle in connectedTargets(deviceIds)) {
            stopInitialBootstrap(handle)
            handle.suppressRtcWriteDuringResync = suppressRtcWrite
            updateSession(handle.id) {
                it.copy(
                    hostState = BleHostSessionState.Syncing,
                    statusText = "Syncing...",
                    syncText = "Sync: starting",
                    lastSyncMetric = null,
                    liveSync = null,
                    awaitingLiveSync = true,
                )
            }
            writeCommand(handle, Ce32Protocol.buildSyncReset(), "sync reset")
            writeCommand(handle, Ce32Protocol.buildSyncStart(), "sync start")
            startInitialBootstrap(handle)
        }
    }

    suspend fun requestSystemParams(deviceIds: List<String>) {
        for (handle in connectedTargets(deviceIds)) {
            writeCommand(handle, Ce32Protocol.buildReadSystemParams(), "read system params")
        }
    }

    suspend fun requestDspParams(deviceIds: List<String>) {
        for (handle in connectedTargets(deviceIds)) {
            writeCommand(handle, Ce32Protocol.buildReadDspParams(0), "read dsp1")
            writeCommand(handle, Ce32Protocol.buildReadDspParams(1), "read dsp2")
        }
    }

    suspend fun requestAllParams(deviceIds: List<String>) {
        val targets = connectedTargets(deviceIds)
        if (targets.isEmpty()) {
            _statusBanner.value = "No connected BLE device is available for parameter reads."
            return
        }

        for (handle in targets) {
            writeCommand(handle, Ce32Protocol.buildReadSystemParams(), "read system params")
            writeCommand(handle, Ce32Protocol.buildReadDspParams(0), "read dsp1")
            writeCommand(handle, Ce32Protocol.buildReadDspParams(1), "read dsp2")
        }
    }

    suspend fun setStreamRates(deviceIds: List<String>, ephysRate: Int, cameraEnabled: Boolean, adcEnabled: Boolean) {
        val targets = connectedTargets(deviceIds)
        if (targets.isEmpty()) {
            _statusBanner.value = "No connected BLE device is available for stream-rate updates."
            return
        }

        val safeEphysRate = ephysRate.coerceAtLeast(1)
        for (handle in targets) {
            val currentParams = currentState(handle.id)?.parsedSystemParams
            if (currentParams == null) {
                _statusBanner.value = "Read system params before updating stream rates on ${handle.latestName.ifBlank { handle.id }}."
                continue
            }
            val nextSamplingRates = MutableList(Ce32Protocol.MaxChannelGroup) { index ->
                currentParams.samplingRates.getOrElse(index) { 0 }
            }
            nextSamplingRates[0] = safeEphysRate
            nextSamplingRates[1] = if (cameraEnabled) Ce32Protocol.CameraStreamRateHz else 0
            nextSamplingRates[2] = if (adcEnabled) Ce32Protocol.AdcStreamRateHz else 0

            val success = writeCommand(
                handle,
                Ce32Protocol.buildStreamFsCommand(nextSamplingRates),
                "stream sampling-rate update",
            )
            if (!success) {
                continue
            }

            updateSession(handle.id) {
                it.copy(
                    parsedSystemParams = currentParams.copy(
                        fs = safeEphysRate,
                        samplingRates = nextSamplingRates.toList(),
                    ),
                    lastMessage = "Stream sampling-rate update requested",
                )
            }

            delay(120)
            writeCommand(handle, Ce32Protocol.buildReadSystemParams(), "verify system params")
        }
    }

    suspend fun stageAcquisitionSystemProfile(
        deviceIds: List<String>,
        vbattThresholdRaw: Int,
        audioRatio: Int,
        cameraRatio: Int,
    ) {
        for (handle in connectedTargets(deviceIds)) {
            updateSession(handle.id) { current ->
                val parsed = current.parsedSystemParams ?: return@updateSession current
                current.copy(
                    parsedSystemParams = parsed.copy(
                        vbattThresholdRaw = vbattThresholdRaw.coerceIn(0, 0xFFFF),
                        audioRatio = audioRatio.coerceIn(0, 0xFF),
                        cameraRatio = cameraRatio.coerceIn(0, 0xFF),
                    ),
                    lastMessage = "Acquisition system fields staged",
                )
            }
        }
    }

    suspend fun applyAcquisitionSystemProfile(
        deviceId: String,
        vbattThresholdRaw: Int,
        audioRatio: Int,
        cameraRatio: Int,
    ) {
        applyAcquisitionSystemProfile(
            deviceIds = listOf(deviceId),
            vbattThresholdRaw = vbattThresholdRaw,
            audioRatio = audioRatio,
            cameraRatio = cameraRatio,
        )
    }

    suspend fun applyAcquisitionSystemProfile(
        deviceIds: List<String>,
        vbattThresholdRaw: Int,
        audioRatio: Int,
        cameraRatio: Int,
    ) {
        val targets = connectedTargets(deviceIds)
        if (targets.isEmpty()) {
            _statusBanner.value = "No connected BLE device is available for acquisition-system updates."
            return
        }

        stageAcquisitionSystemProfile(
            deviceIds = targets.map { it.id },
            vbattThresholdRaw = vbattThresholdRaw,
            audioRatio = audioRatio,
            cameraRatio = cameraRatio,
        )
        uploadSystemParams(targets.map { it.id })
    }

    suspend fun setQuickCustomFs(deviceIds: List<String>, ephysRate: Int) {
        val targets = connectedTargets(deviceIds)
        if (targets.isEmpty()) {
            _statusBanner.value = "No connected BLE device is available for quick FS updates."
            return
        }

        val safeEphysRate = ephysRate.coerceAtLeast(1)
        for (handle in targets) {
            val success = writeCommand(
                handle,
                Ce32Protocol.buildQuickFsCommand(safeEphysRate),
                "custom sampling-rate update",
            )
            if (!success) {
                continue
            }

            updateSession(handle.id) {
                val parsed = it.parsedSystemParams
                if (parsed == null) {
                    it.copy(lastMessage = "Custom sampling-rate update requested")
                } else {
                    val nextSamplingRates = parsed.samplingRates.toMutableList()
                    while (nextSamplingRates.size < Ce32Protocol.MaxChannelGroup) {
                        nextSamplingRates.add(0)
                    }
                    nextSamplingRates[0] = safeEphysRate
                    it.copy(
                        parsedSystemParams = parsed.copy(
                            fs = safeEphysRate,
                            samplingRates = nextSamplingRates,
                        ),
                        lastMessage = "Custom sampling-rate update requested",
                    )
                }
            }
        }
    }

    suspend fun requestSnapshot(deviceId: String, preview: Boolean) {
        handles[deviceId]?.let { handle ->
            writeCommand(handle, Ce32Protocol.buildSnapshotRequest(preview), if (preview) "camera preview snapshot" else "snapshot")
        }
    }

    suspend fun setCameraPreviewStreaming(deviceId: String, enabled: Boolean) {
        val handle = handles[deviceId] ?: return
        handle.cameraPreviewStreamingEnabled = enabled
        if (!enabled) {
            stopCameraPreviewPolling(handle)
            updateSession(deviceId) {
                appendEvent(
                    it.copy(
                        cameraPreviewStreaming = false,
                        lastMessage = "Camera live preview stopped",
                    ),
                    "Camera live preview stopped",
                )
            }
            return
        }

        updateSession(deviceId) {
            appendEvent(
                it.copy(
                    cameraPreviewStreaming = true,
                    lastMessage = "Camera live preview started",
                ),
                "Camera live preview started",
            )
        }
        startCameraPreviewPolling(handle)
    }

    suspend fun requestCameraParams(deviceId: String) {
        requestCameraParams(listOf(deviceId))
    }

    suspend fun requestCameraParams(deviceIds: List<String>) {
        for (handle in connectedTargets(deviceIds)) {
            writeCommand(handle, Ce32Protocol.buildReadCameraParams(), "camera param read")
        }
    }

    suspend fun setCameraParams(deviceId: String, reg0: Int, reg1: Int) {
        setCameraParams(listOf(deviceId), reg0, reg1)
    }

    suspend fun setCameraParams(deviceIds: List<String>, reg0: Int, reg1: Int) {
        for (handle in connectedTargets(deviceIds)) {
            val success = writeCommand(handle, Ce32Protocol.buildCameraParamUpdate(reg0, reg1), "camera param update")
            if (success) {
                updateSession(handle.id) {
                    it.copy(
                        parsedCameraParams = ParsedCameraParams(reg0 = reg0, reg1 = reg1),
                        lastMessage = "Camera parameter update requested",
                    )
                }
                delay(120)
                writeCommand(handle, Ce32Protocol.buildReadCameraParams(), "verify camera params")
            }
        }
    }

    suspend fun setStimEnabled(deviceId: String, enabled: Boolean) {
        setStimEnabled(listOf(deviceId), enabled)
    }

    suspend fun setStimEnabled(deviceIds: List<String>, enabled: Boolean) {
        for (handle in connectedTargets(deviceIds)) {
            val success = writeCommand(handle, Ce32Protocol.buildStimEnable(enabled), if (enabled) "stim enable" else "stim disable")
            if (success) {
                updateSession(handle.id) {
                    it.copy(
                        parsedSystemParams = it.parsedSystemParams?.copy(stimMode = if (enabled) 1 else 0),
                        lastMessage = if (enabled) "Stim enabled" else "Stim disabled",
                    )
                }
            }
        }
    }

    suspend fun setTriggerGain(deviceId: String, channelId: Int, gain: Float) {
        setTriggerGain(listOf(deviceId), channelId, gain)
    }

    suspend fun setTriggerGain(deviceIds: List<String>, channelId: Int, gain: Float) {
        for (handle in connectedTargets(deviceIds)) {
            val success = writeCommand(handle, Ce32Protocol.buildTriggerGainUpdate(channelId, gain), "trigger gain update ch${channelId + 1}")
            if (success) {
                updateSession(handle.id) {
                    val parsed = it.parsedSystemParams
                    if (parsed == null || channelId !in parsed.triggerGains.indices) {
                        it
                    } else {
                        val nextGains = parsed.triggerGains.toMutableList().apply { this[channelId] = gain }
                        it.copy(
                            parsedSystemParams = parsed.copy(triggerGains = nextGains),
                            lastMessage = "Trigger gain update requested",
                        )
                    }
                }
            }
        }
    }

    suspend fun setStimIntensity(deviceId: String, channelId: Int, intensityPercent: Float) {
        setStimIntensity(listOf(deviceId), channelId, intensityPercent)
    }

    suspend fun setStimIntensity(deviceIds: List<String>, channelId: Int, intensityPercent: Float) {
        for (handle in connectedTargets(deviceIds)) {
            val success = writeCommand(handle, Ce32Protocol.buildStimIntensity(channelId, intensityPercent), "stim intensity update ch${channelId + 1}")
            if (success) {
                updateSession(handle.id) {
                    val parsed = it.parsedSystemParams
                    if (parsed == null || channelId !in parsed.stimIntensities.indices) {
                        it
                    } else {
                        val scaled = ((intensityPercent.coerceIn(0f, 100f) / 100f) * 65535f).toInt().coerceIn(0, 0xFFFF)
                        val nextIntensities = parsed.stimIntensities.toMutableList().apply { this[channelId] = scaled }
                        it.copy(
                            parsedSystemParams = parsed.copy(stimIntensities = nextIntensities),
                            lastMessage = "Stim intensity update requested",
                        )
                    }
                }
            }
        }
    }

    suspend fun setTriggerThreshold(deviceId: String, channelId: Int, threshold: Float) {
        setTriggerThreshold(listOf(deviceId), channelId, threshold)
    }

    suspend fun setTriggerThreshold(deviceIds: List<String>, channelId: Int, threshold: Float) {
        for (handle in connectedTargets(deviceIds)) {
            writeCommand(handle, Ce32Protocol.buildTriggerThreshold(channelId, threshold), "trigger threshold update ch${channelId + 1}")
        }
    }

    suspend fun forceTrigger(deviceId: String, index: Int) {
        forceTrigger(listOf(deviceId), index)
    }

    suspend fun forceTrigger(deviceIds: List<String>, index: Int) {
        for (handle in connectedTargets(deviceIds)) {
            writeCommand(handle, Ce32Protocol.buildForceTrigger(index), "force trigger ${index + 1}")
        }
    }

    suspend fun setStimParams(
        deviceId: String,
        channelId: Int,
        delayUnits: Float,
        randomDelayUnits: Float,
        durationUnits: Float,
        intervalUnits: Float,
        cycles: Int,
    ) {
        setStimParams(
            deviceIds = listOf(deviceId),
            channelId = channelId,
            delayUnits = delayUnits,
            randomDelayUnits = randomDelayUnits,
            durationUnits = durationUnits,
            intervalUnits = intervalUnits,
            cycles = cycles,
        )
    }

    suspend fun setStimParams(
        deviceIds: List<String>,
        channelId: Int,
        delayUnits: Float,
        randomDelayUnits: Float,
        durationUnits: Float,
        intervalUnits: Float,
        cycles: Int,
    ) {
        for (handle in connectedTargets(deviceIds)) {
            val success = writeCommand(
                handle,
                Ce32Protocol.buildStimParamUpdate(
                    channelId = channelId,
                    delayUnits = delayUnits,
                    randomDelayUnits = randomDelayUnits,
                    durationUnits = durationUnits,
                    intervalUnits = intervalUnits,
                    cycles = cycles,
                ),
                "stim param update ch${channelId + 1}",
            )
            if (success) {
                updateSession(handle.id) { current ->
                    val parsed = current.parsedSystemParams
                    if (parsed == null || channelId !in 0..1) {
                        current
                    } else {
                        val nextIntervals = parsed.stimIntervals.toMutableList().apply { this[channelId] = (intervalUnits.coerceAtLeast(0f) * 10f).toInt() }
                        val nextDelays = parsed.stimDelays.toMutableList().apply { this[channelId] = (delayUnits.coerceAtLeast(0f) * 10f).toInt() }
                        val nextRandom = parsed.stimRandomDelays.toMutableList().apply { this[channelId] = (randomDelayUnits.coerceAtLeast(0f) * 10f).toInt() }
                        val nextWidths = parsed.pulseWidths.toMutableList().apply { this[channelId] = (durationUnits.coerceAtLeast(0f) * 10f).toInt() }
                        val nextCounts = parsed.pulseCounts.toMutableList().apply { this[channelId] = cycles.coerceAtLeast(0) }
                        current.copy(
                            parsedSystemParams = parsed.copy(
                                stimIntervals = nextIntervals,
                                stimDelays = nextDelays,
                                stimRandomDelays = nextRandom,
                                pulseWidths = nextWidths,
                                pulseCounts = nextCounts,
                            ),
                            lastMessage = "Stim param update requested",
                        )
                    }
                }
            }
        }
    }

    suspend fun setDspLiveParams(
        deviceId: String,
        dspIndex: Int,
        maOrder: Int,
        filterType: Int,
        formula: Int,
        channels: List<Int>,
    ) {
        setDspLiveParams(
            deviceIds = listOf(deviceId),
            dspIndex = dspIndex,
            maOrder = maOrder,
            filterType = filterType,
            formula = formula,
            channels = channels,
        )
    }

    suspend fun setDspLiveParams(
        deviceIds: List<String>,
        dspIndex: Int,
        maOrder: Int,
        filterType: Int,
        formula: Int,
        channels: List<Int>,
    ) {
        for (handle in connectedTargets(deviceIds)) {
            val success = writeCommand(
                handle,
                Ce32Protocol.buildDspLiveUpdate(
                    dspIndex = dspIndex,
                    maOrder = maOrder,
                    filterType = filterType,
                    formula = formula,
                    channels = channels,
                ),
                "dsp live update ${dspIndex + 1}",
            )
            if (success) {
                updateSession(handle.id) { current ->
                    val next = ParsedDspParams(
                        formula = formula.coerceAtLeast(0),
                        filterType = filterType.coerceAtLeast(0),
                        func2 = if (dspIndex == 0) current.parsedDsp1Params?.func2 ?: 0 else current.parsedDsp2Params?.func2 ?: 0,
                        maOrder = maOrder.coerceAtLeast(0),
                        channels = List(4) { index -> channels.getOrElse(index) { 0 }.coerceAtLeast(0) },
                    )
                    if (dspIndex == 0) {
                        current.copy(parsedDsp1Params = next, lastMessage = "DSP0 live update requested")
                    } else {
                        current.copy(parsedDsp2Params = next, lastMessage = "DSP1 live update requested")
                    }
                }
            }
        }
    }

    suspend fun stageClosedLoopProfile(
        deviceId: String,
        closedLoopMode: Int,
        triggerTrainStart: Int,
        triggerTrainDuration: Int,
        randomTriggerMin: Int,
        randomTriggerMax: Int,
        clParam1: List<Float>,
        clParam2: List<Float>,
        stimChannels: List<Int>,
    ) {
        stageClosedLoopProfile(
            deviceIds = listOf(deviceId),
            closedLoopMode = closedLoopMode,
            triggerTrainStart = triggerTrainStart,
            triggerTrainDuration = triggerTrainDuration,
            randomTriggerMin = randomTriggerMin,
            randomTriggerMax = randomTriggerMax,
            clParam1 = clParam1,
            clParam2 = clParam2,
            stimChannels = stimChannels,
        )
    }

    suspend fun stageClosedLoopProfile(
        deviceIds: List<String>,
        closedLoopMode: Int,
        triggerTrainStart: Int,
        triggerTrainDuration: Int,
        randomTriggerMin: Int,
        randomTriggerMax: Int,
        clParam1: List<Float>,
        clParam2: List<Float>,
        stimChannels: List<Int>,
    ) {
        for (handle in connectedTargets(deviceIds)) {
            updateSession(handle.id) { current ->
                val parsed = current.parsedSystemParams ?: return@updateSession current
                current.copy(
                    parsedSystemParams = parsed.copy(
                        closedLoopMode = closedLoopMode.coerceAtLeast(0),
                        triggerTrainStart = triggerTrainStart.coerceAtLeast(0),
                        triggerTrainDuration = triggerTrainDuration.coerceAtLeast(0),
                        randomTriggerMin = randomTriggerMin.coerceAtLeast(0),
                        randomTriggerMax = randomTriggerMax.coerceAtLeast(0),
                        clParam1 = List(4) { index -> clParam1.getOrElse(index) { parsed.clParam1.getOrElse(index) { 0f } } },
                        clParam2 = List(4) { index -> clParam2.getOrElse(index) { parsed.clParam2.getOrElse(index) { 0f } } },
                        stimChannels = List(4) { index -> stimChannels.getOrElse(index) { parsed.stimChannels.getOrElse(index) { 0 } }.coerceAtLeast(0) },
                    ),
                    lastMessage = "Closed-loop system profile staged",
                )
            }
        }
    }

    suspend fun uploadSystemParams(deviceId: String) {
        uploadSystemParams(listOf(deviceId))
    }

    suspend fun uploadSystemParams(deviceIds: List<String>) {
        val targets = connectedTargets(deviceIds)
        if (targets.isEmpty()) {
            _statusBanner.value = "No connected BLE device is available for system parameter uploads."
            return
        }

        var skippedCount = 0
        var uploadedCount = 0
        for (handle in targets) {
            val parsed = currentState(handle.id)?.parsedSystemParams
            val payload = handle.systemParamPayload
            if (parsed == null || payload == null) {
                skippedCount += 1
                continue
            }

            val success = writeCommand(handle, Ce32Protocol.buildSystemParamUpload(payload, parsed), "system parameter upload")
            if (success) {
                uploadedCount += 1
                updateSession(handle.id) { it.copy(lastMessage = "System parameter upload requested") }
                delay(120)
                writeCommand(handle, Ce32Protocol.buildReadSystemParams(), "verify system params")
            }
        }

        if (skippedCount > 0) {
            _statusBanner.value = if (uploadedCount > 0) {
                "Scoped system upload skipped $skippedCount target(s) without a loaded system payload."
            } else {
                "Read system params before sending a full system upload."
            }
        }
    }

    suspend fun uploadDspParams(deviceId: String, dspIndex: Int) {
        uploadDspParams(listOf(deviceId), dspIndex)
    }

    suspend fun uploadDspParams(deviceIds: List<String>, dspIndex: Int) {
        val targets = connectedTargets(deviceIds)
        if (targets.isEmpty()) {
            _statusBanner.value = "No connected BLE device is available for DSP${dspIndex + 1} uploads."
            return
        }

        var skippedCount = 0
        var uploadedCount = 0
        for (handle in targets) {
            val parsed = if (dspIndex == 0) currentState(handle.id)?.parsedDsp1Params else currentState(handle.id)?.parsedDsp2Params
            val payload = if (dspIndex == 0) handle.dsp1ParamPayload else handle.dsp2ParamPayload
            if (parsed == null || payload == null) {
                skippedCount += 1
                continue
            }

            val success = writeCommand(handle, Ce32Protocol.buildDspParamUpload(dspIndex, payload, parsed), "dsp${dspIndex + 1} parameter upload")
            if (success) {
                uploadedCount += 1
                updateSession(handle.id) { it.copy(lastMessage = "DSP${dspIndex + 1} parameter upload requested") }
                delay(120)
                writeCommand(handle, Ce32Protocol.buildReadDspParams(dspIndex), "verify dsp${dspIndex + 1}")
            }
        }

        if (skippedCount > 0) {
            _statusBanner.value = if (uploadedCount > 0) {
                "Scoped DSP${dspIndex + 1} upload skipped $skippedCount target(s) without a loaded DSP payload."
            } else {
                "Read DSP${dspIndex + 1} params before sending a full DSP upload."
            }
        }
    }

    suspend fun uploadAllParams(deviceId: String) {
        uploadAllParams(listOf(deviceId))
    }

    suspend fun uploadAllParams(deviceIds: List<String>) {
        uploadSystemParams(deviceIds)
        delay(120)
        uploadDspParams(deviceIds, 0)
        delay(120)
        uploadDspParams(deviceIds, 1)
    }

    suspend fun setRole(deviceId: String, mode: Int, label: String) {
        val handle = handles[deviceId] ?: return
        handle.expectedDisconnectReason = "Role override: $label"
        val success = writeCommand(handle, Ce32Protocol.buildRoleSwitch(mode), "role switch")
        if (success) {
            disconnect(deviceId, reason = "Role override: $label", userRequested = false)
        }
    }

    suspend fun setLed(deviceIds: List<String>, enabled: Boolean) {
        for (handle in connectedTargets(deviceIds)) {
            if (writeCommand(handle, Ce32Protocol.buildLedCommand(enabled), "led control")) {
                updateSession(handle.id) { it.copy(ledOn = enabled) }
            }
        }
    }

    suspend fun setGpio0(deviceIds: List<String>, mode: GpioMode) {
        for (handle in connectedTargets(deviceIds)) {
            if (writeCommand(handle, Ce32Protocol.buildGpio0Command(mode), "gpio0 ${mode.label.lowercase()}")) {
                updateSession(handle.id) { it.copy(gpio0Mode = mode) }
            }
        }
    }

    suspend fun setGpio1(deviceIds: List<String>, mode: GpioMode) {
        for (handle in connectedTargets(deviceIds)) {
            if (writeCommand(handle, Ce32Protocol.buildGpio1Command(mode), "gpio1 ${mode.label.lowercase()}")) {
                updateSession(handle.id) { it.copy(gpio1Mode = mode) }
            }
        }
    }

    suspend fun setTriggerWaveform(deviceIds: List<String>, enabled: Boolean) {
        for (handle in connectedTargets(deviceIds)) {
            val captureFile = if (enabled) {
                try {
                    buildTriggerWaveformCaptureFile(handle, System.currentTimeMillis())
                } catch (error: Throwable) {
                    _statusBanner.value = "Failed to prepare trigger-waveform file: ${error.message ?: "unknown error"}"
                    continue
                }
            } else {
                null
            }
            if (writeCommand(handle, Ce32Protocol.buildTriggerWaveform(enabled), "trigger waveform")) {
                if (enabled) {
                    handle.triggerWaveformCaptureFile = captureFile
                    val summary = "Trigger waveform capture armed: ${captureFile?.absolutePath}"
                    updateSession(handle.id) {
                        appendEvent(
                            it.copy(
                                triggerWaveformEnabled = true,
                                triggeredWaveformBlockCount = 0,
                                lastTriggeredWaveformBytes = 0,
                                triggerWaveformCaptureBytes = 0L,
                                triggerWaveformCapturePath = captureFile?.absolutePath.orEmpty(),
                                triggerWaveformCaptureActive = true,
                                lastFailure = "",
                                lastMessage = summary,
                            ),
                            "Trigger waveform capture armed",
                        )
                    }
                    _statusBanner.value = summary
                } else {
                    handle.triggerWaveformCaptureFile = null
                    val summary = currentState(handle.id)?.triggerWaveformCapturePath
                        ?.takeIf { it.isNotBlank() }
                        ?.let { "Trigger waveform capture stopped: $it" }
                        ?: "Trigger waveform disabled"
                    updateSession(handle.id) {
                        appendEvent(
                            it.copy(
                                triggerWaveformEnabled = false,
                                triggerWaveformCaptureActive = false,
                                lastFailure = "",
                                lastMessage = summary,
                            ),
                            "Trigger waveform capture stopped",
                        )
                    }
                    _statusBanner.value = summary
                }
            } else if (enabled) {
                captureFile?.delete()
            }
        }
    }

    suspend fun requestImpedance(deviceId: String) {
        requestImpedance(listOf(deviceId))
    }

    suspend fun requestImpedance(deviceIds: List<String>) {
        for (handle in connectedTargets(deviceIds)) {
            writeCommand(handle, Ce32Protocol.buildImpedanceTest(), "impedance test")
        }
    }

    suspend fun requestSleep(deviceId: String) {
        handles[deviceId]?.let { handle ->
            val success = writeCommand(handle, Ce32Protocol.buildEnterSleep(), "sleep")
            if (success) {
                disconnect(deviceId, reason = "Sleep requested", userRequested = false)
            }
        }
    }

    suspend fun requestSoftwareReset(deviceId: String) {
        handles[deviceId]?.let { handle ->
            val success = writeCommand(handle, Ce32Protocol.buildSoftwareReset(), "software reset")
            if (success) {
                disconnect(deviceId, reason = "Software reset requested", userRequested = false)
            }
        }
    }

    suspend fun requestBootloader(deviceId: String) {
        handles[deviceId]?.let { handle ->
            val success = writeCommand(handle, Ce32Protocol.buildSystemBootloader(), "system bootloader")
            if (success) {
                disconnect(deviceId, reason = "System bootloader requested", userRequested = false)
            }
        }
    }

    suspend fun requestFirmwareUpdate(deviceId: String) {
        handles[deviceId]?.let { handle ->
            val success = writeCommand(handle, Ce32Protocol.buildFirmwareImageUpdate(), "firmware update")
            if (success) {
                disconnect(deviceId, reason = "Firmware update requested", userRequested = false)
            }
        }
    }

    suspend fun refreshRecordList(deviceId: String) {
        val handle = handles[deviceId] ?: return
        if (!isConnected(handle.id)) {
            _statusBanner.value = "Connect to the device before reading BLE records."
            return
        }

        waitForRecordOperationReady(handle, "Record refresh")
        val records = mutableListOf<RecordSummary>()
        var previousSector = Ce32Protocol.SectorData
        for (blockIndex in 0 until Ce32Protocol.BleLogBlockScanLimit) {
            val block = requestLogBlock(handle, blockIndex) ?: break
            val countRaw = littleEndianUInt(block, 0).toInt()
            val count = minOf(countRaw, Ce32Protocol.BleLogEntriesPerBlock)
            if (count == 0) {
                break
            }

            repeat(count) { index ->
                val endSector = littleEndianUInt(block, 4 + index * 4)
                if (endSector == 0L) {
                    return@repeat
                }
                val startSector = Ce32Protocol.alignUpAu(previousSector)
                val sizeSectors = (endSector - startSector).coerceAtLeast(0L)
                records += RecordSummary(
                    index = records.size,
                    startSector = startSector,
                    endSector = endSector,
                    sizeSectors = sizeSectors,
                )
                previousSector = endSector
            }

            if (countRaw != Ce32Protocol.BleLogEntriesPerBlock) {
                break
            }
        }

        updateSession(deviceId) {
            it.copy(records = records, lastMessage = "Loaded ${records.size} BLE record(s)")
        }
    }

    suspend fun deleteLastRecord(deviceId: String) {
        val handle = handles[deviceId] ?: return
        requestDeleteRecords(handle, 1)
        refreshRecordList(deviceId)
    }

    suspend fun deleteAllRecords(deviceId: String) {
        val handle = handles[deviceId] ?: return
        while (true) {
            val remaining = requestDeleteRecords(handle, 1) ?: break
            if (remaining <= 0) {
                break
            }
            delay(150)
        }
        refreshRecordList(deviceId)
    }

    fun showSyncLogPath(deviceId: String) {
        val handle = handles[deviceId]
        if (handle == null) {
            _statusBanner.value = "Connect to a BLE device before opening the sync log path."
            return
        }

        runCatching {
            val file = ensureSyncLogFile(handle)
            val summary = "Sync log: ${file.absolutePath}"
            updateSession(handle.id) {
                appendEvent(
                    it.copy(syncLogPath = file.absolutePath, lastFailure = "", lastMessage = summary),
                    summary,
                )
            }
            _statusBanner.value = summary
        }.onFailure { error ->
            _statusBanner.value = "Failed to prepare sync log path: ${error.message ?: "unknown error"}"
        }
    }

    fun showTriggerWaveformPath(deviceId: String) {
        val session = currentState(deviceId)
        val path = session?.triggerWaveformCapturePath?.takeIf { it.isNotBlank() }
        if (path != null) {
            _statusBanner.value = "Trigger waveform: $path"
        } else {
            _statusBanner.value = "Enable trigger waveform capture before opening the waveform path."
        }
    }

    fun showRecordExportPath(deviceId: String) {
        val handle = handles[deviceId]
        if (handle == null) {
            _statusBanner.value = "Connect to a BLE device before opening the record export path."
            return
        }

        runCatching {
            val directory = ensureRecordExportRoot(handle)
            val summary = "Record exports: ${directory.absolutePath}"
            updateSession(handle.id) {
                appendEvent(
                    it.copy(recordExportPath = directory.absolutePath, lastFailure = "", lastMessage = summary),
                    summary,
                )
            }
            _statusBanner.value = summary
        }.onFailure { error ->
            _statusBanner.value = "Failed to prepare record export path: ${error.message ?: "unknown error"}"
        }
    }

    fun showImpedanceExportPath(deviceId: String) {
        val handle = handles[deviceId]
        if (handle == null) {
            _statusBanner.value = "Connect to a BLE device before opening the impedance export path."
            return
        }

        runCatching {
            val directory = ensureImpedanceExportRoot(handle)
            val summary = "Impedance CSVs: ${directory.absolutePath}"
            updateSession(handle.id) {
                appendEvent(
                    it.copy(impedanceExportPath = directory.absolutePath, lastFailure = "", lastMessage = summary),
                    summary,
                )
            }
            _statusBanner.value = summary
        }.onFailure { error ->
            _statusBanner.value = "Failed to prepare impedance export path: ${error.message ?: "unknown error"}"
        }
    }

    suspend fun exportRecord(deviceId: String, recordIndex: Int) {
        val handle = handles[deviceId] ?: return
        val session = currentState(deviceId) ?: return
        val record = session.records.getOrNull(recordIndex)
        if (record == null) {
            _statusBanner.value = "Refresh the BLE record list before exporting."
            return
        }

        waitForRecordOperationReady(handle, "Record export")
        val exportRoot = ensureRecordExportRoot(handle)
        val fileName = buildRecordInfoFileName(record)
        val outputFile = File(exportRoot, fileName)
        exportRecordInfoToFile(handle, listOf(record), outputFile)
    }

    suspend fun exportAllRecords(deviceId: String) {
        val handle = handles[deviceId] ?: return
        val session = currentState(deviceId) ?: return
        if (session.records.isEmpty()) {
            _statusBanner.value = "Refresh the BLE record list before exporting."
            return
        }

        waitForRecordOperationReady(handle, "Record export")
        val outputFile = File(
            ensureRecordExportRoot(handle),
            "record_index_${System.currentTimeMillis()}.csv",
        )
        exportRecordInfoToFile(handle, session.records, outputFile)
    }

    suspend fun exportImpedanceSnapshot(deviceId: String) {
        val handle = handles[deviceId] ?: return
        val session = currentState(deviceId) ?: return
        val snapshot = session.impedanceSnapshot
        if (snapshot.magnitudeValues.isEmpty() &&
            snapshot.phaseValues.isEmpty() &&
            snapshot.crosstalkByDriveChannel.isEmpty()
        ) {
            _statusBanner.value = "Run an impedance test before exporting results."
            return
        }

        val exportRoot = ensureImpedanceExportRoot(handle)
        val timestamp = formatImpedanceTimestamp(
            if (snapshot.updatedAtMs > 0L) snapshot.updatedAtMs else System.currentTimeMillis(),
        )
        val deviceLabel = sanitizePathComponent(handle.latestName.ifBlank { handle.id })
        val impedanceFile = File(exportRoot, "${deviceLabel}_Impedance_${timestamp}.csv")
        val crosstalkFile = File(exportRoot, "${deviceLabel}_Crosstalk_${timestamp}.csv")

        val startSummary = "Saving impedance bundle to ${exportRoot.absolutePath}"
        updateSession(handle.id) {
            appendEvent(
                it.copy(impedanceExportPath = exportRoot.absolutePath, lastFailure = "", lastMessage = startSummary),
                startSummary,
            )
        }
        _statusBanner.value = startSummary

        try {
            withContext(Dispatchers.IO) {
                exportRoot.mkdirs()
                writeImpedanceMagnitudeCsv(snapshot, impedanceFile)
                if (snapshot.crosstalkByDriveChannel.isNotEmpty()) {
                    writeImpedanceCrosstalkCsv(snapshot.crosstalkByDriveChannel, crosstalkFile)
                }
            }

            val summary = buildString {
                append("Saved impedance")
                if (snapshot.crosstalkByDriveChannel.isNotEmpty()) {
                    append(" + crosstalk")
                }
                append(" CSV to ")
                append(exportRoot.absolutePath)
            }
            updateSession(handle.id) {
                appendEvent(
                    it.copy(impedanceExportPath = exportRoot.absolutePath, lastFailure = "", lastMessage = summary),
                    summary,
                )
            }
            _statusBanner.value = summary
        } catch (error: Throwable) {
            val failure = "Failed to export impedance results: ${error.message ?: "unknown error"}"
            updateSession(handle.id) {
                appendEvent(
                    it.copy(impedanceExportPath = exportRoot.absolutePath, lastFailure = failure, lastMessage = failure),
                    failure,
                )
            }
            _statusBanner.value = failure
        }
    }

    private fun connectedTargets(deviceIds: List<String>): List<SessionHandle> {
        return deviceIds.mapNotNull { handles[it] }.filter { isConnected(it.id) }
    }

    private suspend fun exportRecordInfoToFile(
        handle: SessionHandle,
        records: List<RecordSummary>,
        outputFile: File,
        updateBanner: Boolean = true,
    ): Boolean {
        if (records.isEmpty()) {
            _statusBanner.value = "Refresh the BLE record list before exporting."
            return false
        }

        val session = currentState(handle.id) ?: return false
        val exportPath = outputFile.parentFile?.absolutePath ?: outputFile.absolutePath
        val startSummary = if (records.size == 1) {
            "Saving record info for Record ${records.first().index + 1} to ${outputFile.name}"
        } else {
            "Saving BLE record index to ${outputFile.name}"
        }
        updateSession(handle.id) {
            appendEvent(
                it.copy(recordExportPath = exportPath, lastFailure = "", lastMessage = startSummary),
                startSummary,
            )
        }
        if (updateBanner) {
            _statusBanner.value = startSummary
        }

        return try {
            withContext(Dispatchers.IO) {
                outputFile.parentFile?.mkdirs()
                outputFile.bufferedWriter().use { writer ->
                    writer.appendLine("device_name,device_id,record_index,start_sector,end_sector,size_sectors,size_mb")
                    records.forEach { record ->
                        writer.append(session.name.csvField())
                        writer.append(',')
                        writer.append(session.id.csvField())
                        writer.append(',')
                        writer.append((record.index + 1).toString())
                        writer.append(',')
                        writer.append(record.startSector.toString())
                        writer.append(',')
                        writer.append(record.endSector.toString())
                        writer.append(',')
                        writer.append(record.sizeSectors.toString())
                        writer.append(',')
                        writer.append(String.format(Locale.US, "%.4f", record.sizeMb))
                        writer.appendLine()
                    }
                }
            }

            val summary = if (records.size == 1) {
                "Saved record info to ${outputFile.absolutePath}"
            } else {
                "Saved BLE record index to ${outputFile.absolutePath}"
            }
            updateSession(handle.id) {
                appendEvent(
                    it.copy(recordExportPath = exportPath, lastFailure = "", lastMessage = summary),
                    summary,
                )
            }
            if (updateBanner) {
                _statusBanner.value = summary
            }
            true
        } catch (t: Throwable) {
            outputFile.delete()
            val failure = if (records.size == 1) {
                "Record info export failed: ${t.message ?: "unknown error"}"
            } else {
                "BLE record index export failed: ${t.message ?: "unknown error"}"
            }
            updateSession(handle.id) {
                appendEvent(
                    it.copy(recordExportPath = exportPath, lastFailure = failure, lastMessage = failure),
                    failure,
                )
            }
            _statusBanner.value = failure
            false
        }
    }

    private fun ensureRecordExportRoot(handle: SessionHandle): File {
        val deviceFolderName = sanitizePathComponent(handle.latestName.ifBlank { handle.id })
        val root = appContext.getExternalFilesDir("ble_records")
            ?: File(appContext.filesDir, "ble_records")
        val deviceRoot = File(root, deviceFolderName)
        if (!deviceRoot.exists()) {
            deviceRoot.mkdirs()
        }
        return deviceRoot
    }

    private fun ensureImpedanceExportRoot(handle: SessionHandle): File {
        val deviceFolderName = sanitizePathComponent(handle.latestName.ifBlank { handle.id })
        val root = appContext.getExternalFilesDir("ble_impedance")
            ?: File(appContext.filesDir, "ble_impedance")
        val deviceRoot = File(root, deviceFolderName)
        if (!deviceRoot.exists()) {
            deviceRoot.mkdirs()
        }
        return deviceRoot
    }

    private fun ensureTriggerWaveformExportRoot(handle: SessionHandle): File {
        val deviceFolderName = sanitizePathComponent(handle.latestName.ifBlank { handle.id })
        val root = appContext.getExternalFilesDir("ble_waveforms")
            ?: File(appContext.filesDir, "ble_waveforms")
        val deviceRoot = File(root, deviceFolderName)
        if (!deviceRoot.exists()) {
            deviceRoot.mkdirs()
        }
        return deviceRoot
    }

    private fun buildTriggerWaveformCaptureFile(handle: SessionHandle, timestampMs: Long): File {
        val exportRoot = ensureTriggerWaveformExportRoot(handle)
        val deviceLabel = sanitizePathComponent(handle.latestName.ifBlank { handle.id })
        return File(exportRoot, "${deviceLabel}_waveform_${formatImpedanceTimestamp(timestampMs)}.dat")
    }

    private fun appendTriggerWaveformBlock(handle: SessionHandle, payload: ByteArray) {
        ioScope.launch {
            val file = handle.triggerWaveformCaptureFile ?: return@launch
            try {
                handle.triggerWaveformWriteMutex.withLock {
                    FileOutputStream(file, true).use { stream ->
                        stream.write(payload)
                    }
                }
                updateSession(handle.id) {
                    it.copy(
                        triggerWaveformCaptureBytes = it.triggerWaveformCaptureBytes + payload.size.toLong(),
                        triggerWaveformCaptureActive = true,
                    )
                }
            } catch (error: Throwable) {
                handle.triggerWaveformCaptureFile = null
                val failure = "Trigger waveform capture failed: ${error.message ?: "unknown error"}"
                updateSession(handle.id) {
                    appendEvent(
                        it.copy(
                            triggerWaveformCaptureActive = false,
                            lastFailure = failure,
                            lastMessage = failure,
                        ),
                        failure,
                    )
                }
                _statusBanner.value = failure
            }
        }
    }

    private fun buildRecordInfoFileName(record: RecordSummary): String {
        return "record_${(record.index + 1).toString().padStart(3, '0')}_${record.startSector}_${record.endSector}.csv"
    }

    private fun formatImpedanceTimestamp(timestampMs: Long): String {
        return java.text.SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(java.util.Date(timestampMs))
    }

    private fun sanitizePathComponent(value: String): String {
        val cleaned = value.trim().map { ch ->
            when {
                ch.isLetterOrDigit() -> ch
                ch == '-' || ch == '_' -> ch
                else -> '_'
            }
        }.joinToString("")
        return cleaned.ifBlank { "ble_device" }
    }

    private fun String.csvField(): String {
        val escaped = replace("\"", "\"\"")
        return "\"$escaped\""
    }

    private fun writeImpedanceMagnitudeCsv(
        snapshot: com.wild.android.ble.ImpedanceSnapshotUiState,
        outputFile: File,
    ) {
        outputFile.parentFile?.mkdirs()
        outputFile.bufferedWriter().use { writer ->
            if (snapshot.phaseValues.isNotEmpty()) {
                writer.appendLine("Channel,Impedance(kOhm),Phase(Degrees)")
            } else {
                writer.appendLine("Channel,Impedance(kOhm)")
            }
            snapshot.magnitudeValues.forEachIndexed { index, value ->
                writer.append(index.toString())
                writer.append(',')
                writer.append(value.toString())
                if (snapshot.phaseValues.isNotEmpty()) {
                    writer.append(',')
                    writer.append(snapshot.phaseValues.getOrNull(index)?.toString().orEmpty())
                }
                writer.appendLine()
            }
            if (snapshot.magnitudeValues.isEmpty() && snapshot.phaseValues.isNotEmpty()) {
                snapshot.phaseValues.forEachIndexed { index, value ->
                    writer.append(index.toString())
                    writer.append(',')
                    writer.append(',')
                    writer.append(value.toString())
                    writer.appendLine()
                }
            }
        }
    }

    private fun writeImpedanceCrosstalkCsv(
        crosstalkByDriveChannel: Map<Int, List<Int>>,
        outputFile: File,
    ) {
        val sortedChannels = crosstalkByDriveChannel.toSortedMap()
        val rowCount = sortedChannels.values.maxOfOrNull { it.size } ?: 0
        val testCurrent = 3.8e-9
        outputFile.parentFile?.mkdirs()
        outputFile.bufferedWriter().use { writer ->
            writer.append("Signal Amplitude(mV)")
            sortedChannels.keys.forEach { channel ->
                writer.append(',')
                writer.append("Channel_$channel")
            }
            writer.appendLine()

            repeat(rowCount) { rowIndex ->
                writer.append(rowIndex.toString())
                sortedChannels.values.forEach { values ->
                    writer.append(',')
                    val raw = values.getOrNull(rowIndex)
                    if (raw != null) {
                        val scaled = raw * testCurrent * 1e3 * 1e3
                        writer.append(String.format(Locale.US, "%.6f", scaled))
                    }
                }
                writer.appendLine()
            }
        }
    }

    private fun ensureSyncLogFile(handle: SessionHandle): File {
        val deviceFolderName = sanitizePathComponent(handle.latestName.ifBlank { handle.id })
        val root = appContext.getExternalFilesDir("sync_logs")
            ?: File(appContext.filesDir, "sync_logs")
        val deviceRoot = File(root, deviceFolderName)
        if (!deviceRoot.exists()) {
            deviceRoot.mkdirs()
        }
        val file = File(deviceRoot, "sync_results.csv")
        synchronized(syncLogLock) {
            if (!file.exists() || file.length() == 0L) {
                file.writeText(
                    "utc_iso,session_id,device_name,device_id,record_type,mode,samples,offset_s,accuracy_s,delay_s,rtc_diff_s,t0_sec,t0_subsec,t1_sec,t1_subsec,t2_sec,t2_subsec,host_proc_us,pc_rx_time_s,dev_time_s,estimated_delay_s,computed_offset_s\n",
                )
            }
        }
        return file
    }

    private fun appendSyncLog(
        handle: SessionHandle,
        recordType: String,
        mode: Int? = null,
        samples: Int? = null,
        offsetSec: Double? = null,
        accuracySec: Double? = null,
        delaySec: Double? = null,
        rtcDiffSec: Double? = null,
        t0Sec: Long? = null,
        t0SubSec: Long? = null,
        t1Sec: Long? = null,
        t1SubSec: Long? = null,
        t2Sec: Long? = null,
        t2SubSec: Long? = null,
        hostProcUs: Long? = null,
        pcRxTimeSec: Double? = null,
        devTimeSec: Double? = null,
        estimatedDelaySec: Double? = null,
        computedOffsetSec: Double? = null,
    ) {
        ioScope.launch {
            runCatching {
                val file = ensureSyncLogFile(handle)
                updateSession(handle.id) { it.copy(syncLogPath = file.absolutePath) }
                val line = listOf(
                    Instant.now().toString(),
                    syncLogSessionId,
                    handle.latestName.csvField(),
                    handle.id.csvField(),
                    recordType.csvField(),
                    mode?.toString().orEmpty(),
                    samples?.toString().orEmpty(),
                    offsetSec?.let(::formatSyncLogNumber).orEmpty(),
                    accuracySec?.let(::formatSyncLogNumber).orEmpty(),
                    delaySec?.let(::formatSyncLogNumber).orEmpty(),
                    rtcDiffSec?.let(::formatSyncLogNumber).orEmpty(),
                    t0Sec?.toString().orEmpty(),
                    t0SubSec?.toString().orEmpty(),
                    t1Sec?.toString().orEmpty(),
                    t1SubSec?.toString().orEmpty(),
                    t2Sec?.toString().orEmpty(),
                    t2SubSec?.toString().orEmpty(),
                    hostProcUs?.toString().orEmpty(),
                    pcRxTimeSec?.let(::formatSyncLogNumber).orEmpty(),
                    devTimeSec?.let(::formatSyncLogNumber).orEmpty(),
                    estimatedDelaySec?.let(::formatSyncLogNumber).orEmpty(),
                    computedOffsetSec?.let(::formatSyncLogNumber).orEmpty(),
                ).joinToString(",")
                synchronized(syncLogLock) {
                    file.appendText(line + "\n")
                }
            }
        }
    }

    private fun formatSyncLogNumber(value: Double): String {
        return java.lang.Double.toString(value)
    }

    private fun syncStampFromTime(time: ZonedDateTime): SyncLogStamp {
        val seconds = (time.minute * 60 + time.second).toLong()
        val subSeconds = (time.nano / 100_000).coerceIn(0, 9_999).toLong()
        return SyncLogStamp(seconds = seconds, subSeconds = subSeconds)
    }

    private fun parseSyncStamp(payload: ByteArray, offset: Int): SyncLogStamp? {
        if (payload.size < offset + 8) {
            return null
        }

        return SyncLogStamp(
            seconds = ByteBuffer.wrap(payload, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFF_FFFFL,
            subSeconds = ByteBuffer.wrap(payload, offset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFF_FFFFL,
        )
    }

    private fun syncDiffSeconds(a: SyncLogStamp, b: SyncLogStamp): Double {
        val delta = (a.seconds + a.subSeconds / 10_000.0) - (b.seconds + b.subSeconds / 10_000.0)
        return when {
            delta > SyncHalfWrapSeconds -> delta - SyncWrapSeconds
            delta < -SyncHalfWrapSeconds -> delta + SyncWrapSeconds
            else -> delta
        }
    }

    private fun DeviceSessionUiState.isAcquisitionDisabled(): Boolean {
        val params = parsedSystemParams ?: return false
        return params.ephysSamplingRate <= 0 &&
            params.baseFs <= 0 &&
            params.channelCounts.none { it > 0 }
    }

    private fun canStartPreview(session: DeviceSessionUiState): Boolean {
        return session.hostState == BleHostSessionState.Connected ||
            session.hostState == BleHostSessionState.Syncing ||
            session.hostState == BleHostSessionState.Synced
    }

    private fun canStopPreview(session: DeviceSessionUiState): Boolean {
        return session.hostState == BleHostSessionState.Previewing
    }

    private fun canStartRecording(session: DeviceSessionUiState): Boolean {
        return session.hostState == BleHostSessionState.Synced ||
            session.hostState == BleHostSessionState.Previewing
    }

    private fun canStopRecording(session: DeviceSessionUiState): Boolean {
        return session.hostState == BleHostSessionState.StartingRecording ||
            session.hostState == BleHostSessionState.Recording ||
            session.hostState == BleHostSessionState.StoppingRecording
    }

    private fun isTransitioningState(state: BleHostSessionState): Boolean {
        return state == BleHostSessionState.Connecting ||
            state == BleHostSessionState.StartingRecording ||
            state == BleHostSessionState.StoppingRecording ||
            state == BleHostSessionState.Reconnecting ||
            state == BleHostSessionState.Disconnecting
    }

    private fun isTerminalState(state: BleHostSessionState): Boolean {
        return state == BleHostSessionState.Disconnected ||
            state == BleHostSessionState.Error
    }

    private fun canRunPreviewGroupResync(targets: List<SessionHandle>): Boolean {
        if (targets.size < 2) {
            return false
        }

        var anyPreviewing = false
        var anyNeedsRestart = false
        for (handle in targets) {
            val session = currentState(handle.id) ?: return false
            if (handle.usesRecordingPreviewFallback ||
                session.hostState == BleHostSessionState.Recording ||
                isTransitioningState(session.hostState) ||
                isTerminalState(session.hostState)
            ) {
                return false
            }

            when {
                session.hostState == BleHostSessionState.Previewing -> anyPreviewing = true
                canStartPreview(session) -> anyNeedsRestart = true
                else -> return false
            }
        }

        return anyPreviewing && anyNeedsRestart
    }

    private suspend fun runPreviewGroupResyncIfNeeded(targets: List<SessionHandle>): Boolean {
        if (!canRunPreviewGroupResync(targets)) {
            return false
        }

        val previewingTargets = targets.filter { currentState(it.id)?.hostState == BleHostSessionState.Previewing }
        if (previewingTargets.isEmpty()) {
            return false
        }

        val resyncMessage = "Preview re-sync waiting for group restart"
        previewingTargets.forEach { handle ->
            clearPreviewFallbackTracking(handle)
            clearPreviewPrimeTracking(handle)
            updateSession(handle.id) {
                appendEvent(
                    it.copy(
                        hostState = BleHostSessionState.Synced,
                        statusText = "Synced",
                        lastFailure = "",
                        lastMessage = resyncMessage,
                    ),
                    resyncMessage,
                )
            }
        }
        targets
            .filterNot { handle -> previewingTargets.any { it.id == handle.id } }
            .forEach { handle ->
                updateSession(handle.id) {
                    appendEvent(
                        it.copy(
                            lastFailure = "",
                            lastMessage = resyncMessage,
                        ),
                        resyncMessage,
                    )
                }
            }

        var stopOk = true
        for (handle in previewingTargets) {
            if (!writeCommand(handle, Ce32Protocol.buildPreviewStop(), "preview group re-sync stop")) {
                stopOk = false
                break
            }
        }
        if (!stopOk) {
            previewingTargets.forEach { handle ->
                updateSession(handle.id) {
                    appendEvent(
                        it.copy(
                            hostState = BleHostSessionState.Previewing,
                            statusText = "Previewing",
                            lastFailure = "Preview re-sync stop write failed",
                            lastMessage = "Preview re-sync stop write failed",
                        ),
                        "Preview re-sync stop write failed",
                    )
                }
            }
            return false
        }

        waitForPreviewResyncSettle(previewingTargets)
        val restartTargets = connectedTargets(targets.map { it.id })
        if (restartTargets.isEmpty()) {
            previewingTargets.forEach { handle ->
                updateSession(handle.id) {
                    appendEvent(
                        it.copy(
                            lastFailure = "Preview re-sync restart skipped: no BLE sessions remained connected",
                            lastMessage = "Preview re-sync restart skipped: no BLE sessions remained connected",
                        ),
                        "Preview re-sync restart skipped: no BLE sessions remained connected",
                    )
                }
            }
            return false
        }

        startPreviewInternal(restartTargets)
        return true
    }

    private suspend fun waitForPreviewResyncSettle(previewingTargets: List<SessionHandle>) {
        val lastPreviewCounts = linkedMapOf<String, Int>()
        previewingTargets.forEach { handle ->
            currentState(handle.id)?.let { session ->
                lastPreviewCounts[handle.id] = session.previewPacketCount
            }
        }
        if (lastPreviewCounts.isEmpty()) {
            return
        }

        val deadlineAtMs = System.currentTimeMillis() + PreviewGroupResyncMaxWaitMs
        var lastPreviewAdvanceAtMs = System.currentTimeMillis()
        while (System.currentTimeMillis() < deadlineAtMs) {
            delay(60)

            val idsToRemove = mutableListOf<String>()
            val countUpdates = mutableListOf<Pair<String, Int>>()
            var advanced = false
            lastPreviewCounts.entries.toList().forEach { entry ->
                val deviceId = entry.key
                val lastCount = entry.value
                val session = currentState(deviceId)
                if (session == null || !isConnected(deviceId)) {
                    idsToRemove += deviceId
                    return@forEach
                }

                val currentCount = session.previewPacketCount
                if (currentCount != lastCount) {
                    countUpdates += deviceId to currentCount
                    lastPreviewAdvanceAtMs = System.currentTimeMillis()
                    advanced = true
                }
            }

            countUpdates.forEach { (deviceId, count) ->
                lastPreviewCounts[deviceId] = count
            }
            idsToRemove.forEach { deviceId ->
                lastPreviewCounts.remove(deviceId)
            }
            if (lastPreviewCounts.isEmpty()) {
                return
            }

            if (!advanced && System.currentTimeMillis() - lastPreviewAdvanceAtMs >= PreviewGroupResyncQuietWindowMs) {
                return
            }
        }

        lastPreviewCounts.keys.toList().forEach { deviceId ->
            updateSession(deviceId) {
                appendEvent(
                    it.copy(lastMessage = "Preview re-sync wait reached ${PreviewGroupResyncMaxWaitMs} ms; restarting anyway"),
                    "Preview re-sync wait reached ${PreviewGroupResyncMaxWaitMs} ms; restarting anyway",
                )
            }
        }
    }

    private fun isConnected(deviceId: String): Boolean {
        return currentState(deviceId)?.isConnected == true
    }

    private fun currentState(deviceId: String): DeviceSessionUiState? = _sessions.value[deviceId]

    private fun restoreCachedDiscoverySessions() {
        val rawCache = discoveryCachePrefs.getString(DiscoveryCacheKey, null) ?: return
        val restoredSessions = linkedMapOf<String, DeviceSessionUiState>()
        val nowMs = System.currentTimeMillis()

        runCatching {
            val entries = JSONArray(rawCache)
            for (index in 0 until entries.length()) {
                val entry = entries.optJSONObject(index) ?: continue
                val id = entry.optString("id").trim()
                if (id.isEmpty()) {
                    continue
                }

                val lastSeenAtMs = entry.optLong("lastSeenAtMs", 0L)
                if (lastSeenAtMs <= 0L || nowMs - lastSeenAtMs > DiscoveryCacheMaxAgeMs) {
                    continue
                }

                val device = runCatching { adapter?.getRemoteDevice(id) }.getOrNull() ?: continue
                val traceColorArgb = entry.optInt("traceColorArgb", nextTraceColor())
                val handle = handles.getOrPut(id) {
                    SessionHandle(
                        id = id,
                        device = device,
                        traceColorArgb = traceColorArgb,
                    )
                }
                val cachedName = entry.optString("name").trim()
                if (cachedName.isNotEmpty()) {
                    handle.latestName = cachedName
                }
                handle.latestRssi = if (entry.has("rssi")) entry.optInt("rssi") else null

                restoredSessions[id] = DeviceSessionUiState(
                    id = id,
                    name = handle.latestName.ifEmpty { id },
                    address = id,
                    traceColorArgb = traceColorArgb,
                    advertisedServiceMatch = entry.optBoolean("advertisedServiceMatch", false),
                    namePrefixMatch = entry.optBoolean("namePrefixMatch", false),
                    verifiedTransport = entry.optBoolean("verifiedTransport", false),
                    roleTag = entry.optString("roleTag").trim(),
                    functionTag = entry.optString("functionTag").trim(),
                    rssi = handle.latestRssi,
                    advertisedVoltage = if (entry.has("advertisedVoltage")) entry.optDouble("advertisedVoltage") else null,
                    hasAdvertisementTelemetry = entry.optBoolean("hasAdvertisementTelemetry", false),
                    lastSeenAtMs = lastSeenAtMs,
                )
            }
        }.onFailure { error ->
            Log.w("Ce32BleManager", "failed to restore discovery cache", error)
        }

        if (restoredSessions.isEmpty()) {
            return
        }

        _sessions.update { current -> current + restoredSessions }
        if (_activeSessionId.value == null) {
            setActiveSessionInternal(restoredSessions.values.first().id)
        }
    }

    private fun persistDiscoverySessions() {
        val cachedSessions = _sessions.value.values
            .asSequence()
            .filter { session ->
                session.lastSeenAtMs > 0L &&
                    (session.bulkConnectEligible || session.verifiedTransport || session.isConnected)
            }
            .sortedByDescending(DeviceSessionUiState::lastSeenAtMs)
            .take(DiscoveryCacheMaxEntries)
            .toList()

        if (cachedSessions.isEmpty()) {
            discoveryCachePrefs.edit().remove(DiscoveryCacheKey).apply()
            return
        }

        val payload = JSONArray()
        cachedSessions.forEach { session ->
            payload.put(
                JSONObject().apply {
                    put("id", session.id)
                    put("name", session.name)
                    put("traceColorArgb", session.traceColorArgb)
                    put("advertisedServiceMatch", session.advertisedServiceMatch)
                    put("namePrefixMatch", session.namePrefixMatch)
                    put("verifiedTransport", session.verifiedTransport)
                    put("hasAdvertisementTelemetry", session.hasAdvertisementTelemetry)
                    put("lastSeenAtMs", session.lastSeenAtMs)
                    if (session.rssi != null) {
                        put("rssi", session.rssi)
                    }
                    if (session.advertisedVoltage != null) {
                        put("advertisedVoltage", session.advertisedVoltage)
                    }
                    if (session.roleTag.isNotBlank()) {
                        put("roleTag", session.roleTag)
                    }
                    if (session.functionTag.isNotBlank()) {
                        put("functionTag", session.functionTag)
                    }
                },
            )
        }
        discoveryCachePrefs.edit().putString(DiscoveryCacheKey, payload.toString()).apply()
    }

    private fun upsertDiscoveredDevice(result: ScanResult) {
        val device = result.device ?: return
        val record = result.scanRecord
        val name = record?.deviceName ?: device.name
        val trimmedName = name?.trim().orEmpty()
        val id = device.address ?: return
        val seenAtMs = System.currentTimeMillis()
        val serviceMatch = Ce32Protocol.hasExpectedService(record)
        val nameMatch = Ce32Protocol.matchesKnownNamePrefix(name)
        val hasManufacturerPayload = Ce32AdvertisementTelemetry.hasManufacturerPayload(record)
        val advertisedVoltage = Ce32AdvertisementTelemetry.parseVoltage(record)
        val hasAdvertisementTelemetry = hasManufacturerPayload || advertisedVoltage != null
        val knownSession = handles.containsKey(id) || _sessions.value.containsKey(id)
        if (!nameMatch && !serviceMatch && !knownSession) {
            return
        }
        val handle = handles.getOrPut(id) {
            SessionHandle(
                id = id,
                device = device,
                traceColorArgb = nextTraceColor(),
            )
        }
        handle.device = device
        handle.latestName = trimmedName.ifEmpty { handle.latestName }
        handle.latestRssi = result.rssi
        val detectedRoleTag = detectBleRoleTagFromName(handle.latestName)

        _sessions.update { current ->
            val previous = current[id]
            val roleTag = previous?.roleTag.orEmpty().ifBlank { detectedRoleTag }
            val functionTag = previous?.functionTag.orEmpty().ifBlank {
                detectBleFunctionTagFromRoleTag(roleTag)
            }
            val next = (previous ?: DeviceSessionUiState(
                id = id,
                name = handle.latestName.ifEmpty { id },
                address = id,
                traceColorArgb = handle.traceColorArgb,
            )).copy(
                name = handle.latestName.ifEmpty { previous?.name ?: id },
                rssi = result.rssi,
                rssiHistory = appendRssiHistorySample(
                    history = previous?.rssiHistory.orEmpty(),
                    rssiDbm = result.rssi,
                    timestampMs = seenAtMs,
                ),
                advertisedVoltage = advertisedVoltage ?: previous?.advertisedVoltage,
                hasAdvertisementTelemetry = hasAdvertisementTelemetry || previous?.hasAdvertisementTelemetry == true,
                advertisedServiceMatch = serviceMatch || previous?.advertisedServiceMatch == true,
                namePrefixMatch = nameMatch || previous?.namePrefixMatch == true,
                lastSeenAtMs = seenAtMs,
                roleTag = roleTag,
                functionTag = functionTag,
                isActive = _activeSessionId.value == id,
            )
            current + (id to next)
        }
        persistDiscoverySessions()

        if (_activeSessionId.value == null && (serviceMatch || nameMatch)) {
            setActiveSession(id)
        }
    }

    private fun nextTraceColor(): Int {
        val color = tracePalette[traceCursor % tracePalette.size]
        traceCursor += 1
        return color
    }

    private fun detectBleRoleTagFromName(deviceName: String?): String {
        if (deviceName.isNullOrBlank()) {
            return ""
        }

        return when {
            deviceName.contains("CE64S_") || deviceName.contains("CE32S_") -> "S"
            deviceName.contains("CE64X_") || deviceName.contains("CE32X_") -> "M_S0"
            else -> ""
        }
    }

    private fun detectBleFunctionTagFromRoleTag(roleTag: String): String {
        val role = roleTag.trim()
        if (role.isEmpty()) {
            return ""
        }

        return when (role[0].uppercaseChar()) {
            'M' -> "Host"
            'S' -> "Device"
            else -> ""
        }
    }

    private fun formatBleRoleMessage(roleTag: String, functionTag: String): String {
        val cleanRole = roleTag.trim()
        val cleanFunction = functionTag.trim()
        if (cleanRole.isEmpty()) {
            return cleanFunction
        }
        if (cleanFunction.isEmpty()) {
            return cleanRole
        }
        return "$cleanRole ($cleanFunction)"
    }

    private fun tryParseBleHandshakeRoleMessage(message: String): BleRoleInfo? {
        val value = message.trim()
        if (value.isEmpty() || value.length > 8 || value.any(Char::isWhitespace)) {
            return null
        }

        return when (value[0].uppercaseChar()) {
            'M' -> {
                if (value.length > 1) {
                    val next = value[1]
                    if (!next.isDigit() && next != '_' && next != '-' && next != '+') {
                        return null
                    }
                }
                BleRoleInfo(roleTag = value, functionTag = "Host")
            }

            'S' -> {
                if (value.length > 1 && value.drop(1).any { !it.isDigit() && it != '_' && it != '-' && it != '+' }) {
                    return null
                }
                BleRoleInfo(roleTag = value, functionTag = "Device")
            }

            else -> null
        }
    }

    private fun appendEvent(session: DeviceSessionUiState, summary: String): DeviceSessionUiState {
        val timestampMs = System.currentTimeMillis()
        val nextEvent = SessionEventUiState(
            timestampMs = timestampMs,
            summary = summary,
        )
        val mergedEvents = if (session.recentEvents.lastOrNull()?.summary == summary) {
            session.recentEvents.dropLast(1) + nextEvent
        } else {
            session.recentEvents + nextEvent
        }
        val nextEvents = mergedEvents.takeLast(MaxRecentEvents)
        return session.copy(recentEvents = nextEvents)
    }

    private fun eventForCommand(label: String): String? {
        return when {
            label == "packed time sync" -> null
            label == "camera preview poll" -> null
            label.startsWith("verify ") -> null
            label.startsWith("log block ") -> null
            else -> "TX $label"
        }
    }

    private fun cancelReconnectFlow(handle: SessionHandle) {
        handle.reconnectPending = false
        handle.reconnectAttemptCount = 0
        handle.reconnectJob?.cancel()
        handle.reconnectJob = null
        handle.resumePreviewAfterReconnect = false
        handle.resumePackedTimeAfterReconnect = false
        handle.resumeCameraPreviewAfterReconnect = false
    }

    private fun beginManualConnectPhase(handle: SessionHandle) {
        if (handle.manualConnectInProgress) {
            return
        }
        handle.manualConnectInProgress = true
        manualConnectInProgressCount += 1
    }

    private fun finishManualConnectPhase(handle: SessionHandle) {
        if (!handle.manualConnectInProgress) {
            return
        }
        handle.manualConnectInProgress = false
        manualConnectInProgressCount = (manualConnectInProgressCount - 1).coerceAtLeast(0)
        if (manualConnectInProgressCount == 0 && manualConnectBatchDepth == 0) {
            ioScope.launch {
                resumeScanAfterManualConnectIfNeeded()
                resumePausedPreviewAfterAdditionalConnect()
            }
        }
    }

    private fun pauseScanForManualConnect() {
        if (!shouldPauseScanForConnect()) {
            return
        }
        if (_isScanning.value) {
            stopScan()
            resumeScanAfterManualConnect = true
        }
    }

    private fun shouldPauseScanForConnect(): Boolean {
        // Android 5 field logs showed scan traffic still active during connectGatt(),
        // and those connects consistently failed with status 133 immediately after
        // the platform reported a nominal connection.
        return true
    }

    private fun beginReconnectConnectPhase() {
        if (reconnectScanPauseDepth == 0) {
            pauseScanForManualConnect()
        }
        reconnectScanPauseDepth += 1
    }

    private fun finishReconnectConnectPhase() {
        if (reconnectScanPauseDepth <= 0) {
            return
        }
        reconnectScanPauseDepth -= 1
        if (reconnectScanPauseDepth == 0 && manualConnectInProgressCount == 0 && manualConnectBatchDepth == 0) {
            ioScope.launch {
                resumeScanAfterManualConnectIfNeeded()
            }
        }
    }

    private fun resumeScanAfterManualConnectIfNeeded() {
        if (!resumeScanAfterManualConnect) {
            return
        }
        if (reconnectScanPauseDepth > 0 || handles.values.any { it.reconnectPending }) {
            return
        }
        resumeScanAfterManualConnect = false
        startScan()
    }

    private fun previewOnlySessionsForAdditionalConnect(excludingDeviceIds: Set<String>): List<SessionHandle> {
        return handles.values.filter { existing ->
            existing.id !in excludingDeviceIds &&
                currentState(existing.id)?.hostState == BleHostSessionState.Previewing &&
                !existing.usesRecordingPreviewFallback
        }
    }

    private suspend fun pausePreviewSessionsBeforeAdditionalConnect(excludingDeviceIds: Set<String>) {
        val targets = previewOnlySessionsForAdditionalConnect(excludingDeviceIds)
        if (targets.isEmpty()) {
            return
        }

        for (handle in targets) {
            handle.previewFallbackPending = false
            clearPreviewPrimeTracking(handle)
            updateSession(handle.id) {
                appendEvent(
                    it.copy(
                        hostState = BleHostSessionState.Synced,
                        statusText = "Synced",
                        lastMessage = "Preview paused before additional connect",
                    ),
                    "Preview paused before additional connect",
                )
            }
            handle.resumePreviewAfterAdditionalConnect = true
        }

        for (handle in targets) {
            val stopOk = writeCommand(handle, Ce32Protocol.buildPreviewStop(), "preview pause before additional connect")
            if (!stopOk) {
                handle.resumePreviewAfterAdditionalConnect = false
                updateSession(handle.id) {
                    appendEvent(
                        it.copy(
                            hostState = BleHostSessionState.Previewing,
                            statusText = "Previewing",
                            lastFailure = "Preview pause before connect write failed",
                            lastMessage = "Preview pause before connect write failed",
                        ),
                        "Preview pause before connect write failed",
                    )
                }
            }
        }
    }

    private suspend fun resumePausedPreviewAfterAdditionalConnect() {
        val targets = handles.values.filter { it.resumePreviewAfterAdditionalConnect }
        if (targets.isEmpty()) {
            return
        }

        for (handle in targets) {
            handle.resumePreviewAfterAdditionalConnect = false
            val session = currentState(handle.id)
            if (session == null || session.hostState != BleHostSessionState.Synced || !session.isConnected) {
                continue
            }
            startPreview(listOf(handle.id))
        }
    }

    private suspend fun waitForConnectBatchAdvance(deviceId: String) {
        val deadlineMs = System.currentTimeMillis() + BulkConnectAdvanceTimeoutMs
        while (System.currentTimeMillis() < deadlineMs) {
            val session = currentState(deviceId)
            val state = session?.hostState
            if (state == null ||
                isReadyForNextBatchConnect(session)
            ) {
                return
            }
            delay(100)
        }
        val stalledSession = currentState(deviceId)
        Log.w(
            "Ce32BleManager",
            "batch connect advance timeout device=$deviceId state=${stalledSession?.hostState} verified=${stalledSession?.verifiedTransport} status=${stalledSession?.statusText} sync=${stalledSession?.syncText}",
        )
    }

    private fun isReadyForNextBatchConnect(session: DeviceSessionUiState): Boolean {
        // Once the link has reached Syncing, GATT services/notifications are already configured
        // and the device-specific bootstrap is running. Waiting for full Synced completion here
        // unnecessarily serializes multi-device linking and can stall the queue for the timeout
        // window even though the transport is already ready for the next connect.
        return session.hostState in setOf(
            BleHostSessionState.Syncing,
            BleHostSessionState.Synced,
            BleHostSessionState.Previewing,
            BleHostSessionState.StartingRecording,
            BleHostSessionState.Recording,
            BleHostSessionState.StoppingRecording,
            BleHostSessionState.Disconnected,
            BleHostSessionState.Error,
        )
    }

    private fun isPreviewPrimeCapableState(state: BleHostSessionState): Boolean {
        return state == BleHostSessionState.Previewing
    }

    private fun clearPreviewPrimeTracking(handle: SessionHandle) {
        handle.previewPrimeGeneration += 1
        handle.lastPreviewPrimeAtMs = 0L
    }

    private fun shouldContinuePreviewPrime(handle: SessionHandle, generation: Int): Boolean {
        val session = currentState(handle.id) ?: return false
        return isPreviewPrimeCapableState(session.hostState) &&
            session.previewPacketCount == 0 &&
            !handle.usesRecordingPreviewFallback &&
            handle.previewPrimeGeneration == generation
    }

    private fun cancelPreviewStartupMonitor(handle: SessionHandle) {
        handle.previewStartupMonitorJob?.cancel()
        handle.previewStartupMonitorJob = null
    }

    private fun clearPreviewFallbackTracking(handle: SessionHandle) {
        cancelPreviewStartupMonitor(handle)
        handle.previewFallbackPending = false
        handle.usesRecordingPreviewFallback = false
    }

    private fun stopConnectWatchdog(handle: SessionHandle) {
        handle.connectTimeoutJob?.cancel()
        handle.connectTimeoutJob = null
    }

    private fun cancelMtuNegotiationFallback(handle: SessionHandle) {
        handle.mtuNegotiationFallbackJob?.cancel()
        handle.mtuNegotiationFallbackJob = null
    }

    private fun cancelRecordRefresh(handle: SessionHandle) {
        handle.recordRefreshJob?.cancel()
        handle.recordRefreshJob = null
    }

    private fun cancelRecordStartAckTimeout(handle: SessionHandle) {
        handle.recordStartAckTimeoutJob?.cancel()
        handle.recordStartAckTimeoutJob = null
    }

    private fun cancelRecordStopAckTimeout(handle: SessionHandle) {
        handle.recordStopAckTimeoutJob?.cancel()
        handle.recordStopAckTimeoutJob = null
    }

    private fun clearRecordAckTimeouts(handle: SessionHandle) {
        cancelRecordRefresh(handle)
        cancelRecordStartAckTimeout(handle)
        cancelRecordStopAckTimeout(handle)
    }

    private fun queueRecordListRefreshAfterStop(handle: SessionHandle) {
        cancelRecordRefresh(handle)
        val refreshJob = ioScope.launch {
            delay(PostStopRecordRefreshDelayMs)
            if (!isConnected(handle.id) || handle.disconnectRequestedByUser || handle.reconnectPending) {
                return@launch
            }
            // Let refreshRecordList apply the same busy wait policy used by manual record actions
            // so late or missing stop acks still get one best-effort record index refresh.
            refreshRecordList(handle.id)
        }
        handle.recordRefreshJob = refreshJob
        refreshJob.invokeOnCompletion {
            if (handle.recordRefreshJob === refreshJob) {
                handle.recordRefreshJob = null
            }
        }
    }

    private fun startRecordStartAckTimeout(
        handle: SessionHandle,
        rollbackState: BleHostSessionState,
        rollbackStatusText: String,
    ) {
        cancelRecordStartAckTimeout(handle)
        handle.recordStartAckTimeoutJob = ioScope.launch {
            delay(RecordStartAckTimeoutMs)
            val session = currentState(handle.id) ?: return@launch
            if (session.hostState != BleHostSessionState.StartingRecording) {
                return@launch
            }

            handle.periodicPackedTimeJob?.cancel()
            handle.periodicPackedTimeJob = null
            handle.lastStopRecordingRequestAtMs = System.currentTimeMillis()
            val stopSent = writeCommand(
                handle,
                Ce32Protocol.buildRecordStopAndPreviewStop(),
                "record start timeout stop",
            )
            updateSession(handle.id) {
                val timeoutMessage = if (stopSent) {
                    "Record start confirmation timed out; stop sent"
                } else {
                    "Record start confirmation timed out; stop write failed"
                }
                appendEvent(
                    it.copy(
                        hostState = rollbackState,
                        statusText = rollbackStatusText,
                        lastFailure = "$timeoutMessage after ${RecordStartAckTimeoutMs} ms.",
                        lastMessage = timeoutMessage,
                    ),
                    timeoutMessage,
                )
            }
            handle.usesRecordingPreviewFallback = false
        }
    }

    private fun startRecordStopAckTimeout(handle: SessionHandle) {
        cancelRecordStopAckTimeout(handle)
        handle.recordStopAckTimeoutJob = ioScope.launch {
            delay(RecordStopAckTimeoutMs)
            val session = currentState(handle.id) ?: return@launch
            if (session.hostState != BleHostSessionState.StoppingRecording) {
                return@launch
            }

            updateSession(handle.id) {
                appendEvent(
                    it.copy(
                        lastFailure = "Timed out waiting ${RecordStopAckTimeoutMs} ms for record stop confirmation.",
                        lastMessage = "Record stop confirmation still pending",
                    ),
                    "Record stop confirmation still pending",
                )
            }
            queueRecordListRefreshAfterStop(handle)
        }
    }

    private suspend fun sendPreviewPrimeSequence(handle: SessionHandle, attemptIndex: Int) {
        if (!isConnected(handle.id)) {
            return
        }

        val selection = currentState(handle.id)?.previewSelection ?: PreviewSelection.Default
        val preferStopFirst = attemptIndex >= 2 || (attemptIndex >= 1 && handle.useLegacyWakePrefix)
        if (preferStopFirst) {
            writeCommand(handle, Ce32Protocol.buildPreviewStop(), "preview prime stop")
            writeCommand(handle, Ce32Protocol.buildPreviewSelect(selection), "preview prime selection")
            writeCommand(handle, Ce32Protocol.buildPreviewStart(), "preview prime start")
            writeCommand(handle, Ce32Protocol.buildPreviewStart(), "preview prime start")
        } else if (attemptIndex > 0) {
            writeCommand(handle, Ce32Protocol.buildPreviewSelect(selection), "preview prime selection")
            writeCommand(handle, Ce32Protocol.buildPreviewStart(), "preview prime start")
        } else {
            writeCommand(handle, Ce32Protocol.buildPreviewStart(), "preview prime start")
            writeCommand(handle, Ce32Protocol.buildPreviewSelect(selection), "preview prime selection")
            writeCommand(handle, Ce32Protocol.buildPreviewStart(), "preview prime start")
        }
    }

    private fun tryQueuePreviewPrime(handle: SessionHandle, initialDelayMs: Long = 150L): Boolean {
        val session = currentState(handle.id) ?: return false
        if (!isPreviewPrimeCapableState(session.hostState) || session.previewPacketCount > 0 || handle.usesRecordingPreviewFallback) {
            return false
        }

        val nowMs = System.currentTimeMillis()
        if (nowMs - handle.lastPreviewPrimeAtMs < PreviewPrimeThrottleMs) {
            return false
        }

        handle.lastPreviewPrimeAtMs = nowMs
        val generation = handle.previewPrimeGeneration + 1
        handle.previewPrimeGeneration = generation
        ioScope.launch {
            primePreviewAfterSync(handle, generation, initialDelayMs)
        }
        return true
    }

    private suspend fun primePreviewAfterSync(
        handle: SessionHandle,
        generation: Int,
        initialDelayMs: Long = 150L,
    ) {
        if (!shouldContinuePreviewPrime(handle, generation)) {
            return
        }

        try {
            val attemptDelaysMs = longArrayOf(initialDelayMs.coerceAtLeast(0L), 450L, 900L)
            for (attempt in attemptDelaysMs.indices) {
                val delayMs = attemptDelaysMs[attempt]
                if (delayMs > 0L) {
                    delay(delayMs)
                }

                if (!shouldContinuePreviewPrime(handle, generation)) {
                    return
                }

                sendPreviewPrimeSequence(handle, attempt)
            }

            if (shouldContinuePreviewPrime(handle, generation)) {
                val session = currentState(handle.id) ?: return
                if (session.commandRxCount > 0 && session.recTimePacketCount == 0) {
                    updateSession(handle.id) {
                        appendEvent(
                            it.copy(lastMessage = "Preview startup still silent after prime retries"),
                            "Preview startup still silent after prime retries",
                        )
                    }
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun startPreviewStartupMonitor(
        handle: SessionHandle,
        rollbackState: BleHostSessionState,
        rollbackStatusText: String,
        baselinePreviewPacketCount: Int,
    ) {
        cancelPreviewStartupMonitor(handle)
        handle.previewStartupMonitorJob = ioScope.launch {
            try {
                delay(PreviewFallbackDelayMs)
            } catch (_: Throwable) {
                return@launch
            } finally {
                handle.previewStartupMonitorJob = null
            }

            if (!handle.previewFallbackPending) {
                return@launch
            }

            val session = currentState(handle.id) ?: return@launch
            handle.previewFallbackPending = false
            if (session.hostState != BleHostSessionState.Previewing || session.isRecordingLike) {
                return@launch
            }

            if (session.previewPacketCount > baselinePreviewPacketCount) {
                return@launch
            }

            clearPreviewPrimeTracking(handle)
            val stopSent = writeCommand(handle, Ce32Protocol.buildPreviewStop(), "preview timeout stop")
            val failure = if (stopSent) {
                "Preview did not receive waveform data; preview stopped"
            } else {
                "Preview did not receive waveform data; stop write failed"
            }
            updateSession(handle.id) {
                appendEvent(
                    it.copy(
                        hostState = rollbackState,
                        statusText = rollbackStatusText,
                        lastFailure = failure,
                        lastMessage = failure,
                    ),
                    failure,
                )
            }
        }
    }

    private fun startConnectWatchdog(handle: SessionHandle, gatt: BluetoothGatt) {
        stopConnectWatchdog(handle)
        handle.connectTimeoutJob = ioScope.launch {
            delay(ConnectLinkTimeoutMs)
            if (handle.gatt !== gatt) {
                return@launch
            }

            val session = currentState(handle.id) ?: return@launch
            val failure = when (session.hostState) {
                BleHostSessionState.Connecting -> "Connect timed out before a live BLE link was established"
                BleHostSessionState.Connected -> "Connect completed without a live BLE link"
                else -> return@launch
            }
            updateSession(handle.id) {
                appendEvent(
                    it.copy(
                        hostState = BleHostSessionState.Error,
                        statusText = "Error",
                        syncText = if (handle.reconnectPending) "Sync: reconnect failed" else "Sync: connect failed",
                        lastFailure = failure,
                        lastMessage = failure,
                    ),
                    failure,
                )
            }
            forceDisconnectLink(handle, failure)
        }
    }

    @SuppressLint("MissingPermission")
    private fun forceDisconnectLink(handle: SessionHandle, reason: String) {
        stopConnectWatchdog(handle)
        cancelMtuNegotiationFallback(handle)
        clearRecordAckTimeouts(handle)
        clearPreviewFallbackTracking(handle)
        clearPreviewPrimeTracking(handle)
        if (!handle.reconnectPending) {
            finishManualConnectPhase(handle)
        }
        handle.disconnectRequestedByUser = false
        handle.expectedDisconnectReason = reason
        val gatt = handle.gatt
        if (gatt == null) {
            finalizeDisconnect(handle.id, reason)
            handle.expectedDisconnectReason = ""
            return
        }

        try {
            gatt.disconnect()
        } catch (_: Throwable) {
            finalizeDisconnect(handle.id, reason)
            handle.expectedDisconnectReason = ""
            return
        }

        ioScope.launch {
            delay(DisconnectFallbackMs)
            val attached = handles[handle.id] ?: return@launch
            if (attached.gatt !== gatt) {
                return@launch
            }
            finalizeDisconnect(handle.id, reason)
            attached.expectedDisconnectReason = ""
        }
    }

    private fun shouldAutoReconnect(handle: SessionHandle, previous: DeviceSessionUiState?): Boolean {
        if (handle.disconnectRequestedByUser || handle.expectedDisconnectReason.isNotBlank()) {
            return false
        }
        return previous?.hostState in setOf(
            BleHostSessionState.Connected,
            BleHostSessionState.Syncing,
            BleHostSessionState.Synced,
            BleHostSessionState.Previewing,
            BleHostSessionState.StartingRecording,
            BleHostSessionState.Recording,
            BleHostSessionState.StoppingRecording,
            BleHostSessionState.Reconnecting,
        )
    }

    private fun shouldRetryInitialConnect(
        handle: SessionHandle,
        previous: DeviceSessionUiState?,
        status: Int,
    ): Boolean {
        if (handle.disconnectRequestedByUser || handle.expectedDisconnectReason.isNotBlank()) {
            return false
        }
        if (previous?.hostState != BleHostSessionState.Connecting) {
            return false
        }
        if (isLegacyPlatform133ConnectFailure(previous, status)) {
            // Android 5 user-initiated connects are more stable with a follow-up
            // direct connect attempt after a clean cooldown than with an immediate
            // hard failure or autoConnect=true on the first try.
            return handle.reconnectAttemptCount == 0
        }
        return status in RetryableInitialConnectStatuses
    }

    private fun isLegacyPlatform133ConnectFailure(
        previous: DeviceSessionUiState?,
        status: Int,
    ): Boolean {
        return Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP &&
            previous?.hostState == BleHostSessionState.Connecting &&
            status == 133
    }

    private fun shouldUseTransportLeConnect(
        handle: SessionHandle,
        fromReconnect: Boolean,
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return false
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP) {
            // Live Nexus 5 traces showed the reflected TRANSPORT_LE connect path
            // failing with status 133 immediately after the stack reported a
            // nominal connection. Keep Android 5 on the plain legacy path.
            return false
        }
        if (!fromReconnect) {
            return true
        }
        return handle.reconnectAttemptCount % 2 == 0
    }

    @SuppressLint("MissingPermission")
    private fun connectGattWithPreferredTransport(
        connectDevice: BluetoothDevice,
        callback: BluetoothGattCallback,
        useAutoConnect: Boolean,
        useTransportLeConnect: Boolean,
    ): Pair<BluetoothGatt, String> {
        if (useTransportLeConnect) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return connectDevice.connectGatt(
                    appContext,
                    useAutoConnect,
                    callback,
                    BluetoothDevice.TRANSPORT_LE,
                ) to "LE"
            }

            tryConnectGattWithReflectedTransport(
                connectDevice = connectDevice,
                callback = callback,
                useAutoConnect = useAutoConnect,
            )?.let { reflectedGatt ->
                return reflectedGatt to "LE_REFLECT"
            }
        }

        @Suppress("DEPRECATION")
        return connectDevice.connectGatt(appContext, useAutoConnect, callback) to "LEGACY"
    }

    @SuppressLint("DiscouragedPrivateApi", "MissingPermission")
    private fun tryConnectGattWithReflectedTransport(
        connectDevice: BluetoothDevice,
        callback: BluetoothGattCallback,
        useAutoConnect: Boolean,
    ): BluetoothGatt? {
        return runCatching {
            val connectGattMethod = BluetoothDevice::class.java.getDeclaredMethod(
                "connectGatt",
                Context::class.java,
                Boolean::class.javaPrimitiveType,
                BluetoothGattCallback::class.java,
                Int::class.javaPrimitiveType,
            ).apply {
                isAccessible = true
            }
            connectGattMethod.invoke(
                connectDevice,
                appContext,
                useAutoConnect,
                callback,
                BluetoothDevice.TRANSPORT_LE,
            ) as BluetoothGatt
        }.onFailure { error ->
            Log.w(
                "Ce32BleManager",
                "reflective LE transport connectGatt unavailable for ${connectDevice.address}: ${error.message}",
            )
        }.getOrNull()
    }

    @Suppress("UNUSED_PARAMETER")
    private fun shouldUseLegacyAutoConnect(
        _handle: SessionHandle,
        _fromReconnect: Boolean,
    ): Boolean {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
            return false
        }
        // Live Nexus 5 testing regressed when manual connects switched to
        // autoConnect=true. Keep Android 5 on direct connects and let the
        // retry path handle a clean second attempt if the stack returns 133.
        return false
    }

    private fun resolveConnectDevice(handle: SessionHandle): BluetoothDevice {
        return handle.device
    }

    private fun scanPauseConnectGattCooldownMs(): Long {
        return if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP) {
            3000L
        } else {
            ScanPauseConnectGattCooldownMs
        }
    }

    private fun retryConnectGattCooldownMs(): Long {
        return if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP) {
            3000L
        } else {
            RetryConnectGattCooldownMs
        }
    }

    private fun reconnectConnectGattCooldownMs(): Long {
        return if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP) {
            3200L
        } else {
            ReconnectConnectGattCooldownMs
        }
    }

    private fun captureReconnectIntent(handle: SessionHandle, previous: DeviceSessionUiState?) {
        val priorState = previous?.hostState
        handle.resumePreviewAfterReconnect = priorState in setOf(
            BleHostSessionState.Previewing,
            BleHostSessionState.StartingRecording,
            BleHostSessionState.Recording,
            BleHostSessionState.StoppingRecording,
        )
        handle.resumePackedTimeAfterReconnect = priorState in setOf(
            BleHostSessionState.StartingRecording,
            BleHostSessionState.Recording,
            BleHostSessionState.StoppingRecording,
        )
        handle.resumeCameraPreviewAfterReconnect = handle.cameraPreviewStreamingEnabled
    }

    private fun isReconnectStable(session: DeviceSessionUiState?): Boolean {
        return session?.hostState in setOf(
            BleHostSessionState.Synced,
            BleHostSessionState.Previewing,
            BleHostSessionState.StartingRecording,
            BleHostSessionState.Recording,
            BleHostSessionState.StoppingRecording,
        )
    }

    private suspend fun waitForReconnectStabilization(handle: SessionHandle): Boolean {
        val deadlineMs = System.currentTimeMillis() + ReconnectStabilizationTimeoutMs
        while (System.currentTimeMillis() < deadlineMs) {
            if (handle.disconnectRequestedByUser || !handle.reconnectPending) {
                return false
            }

            val session = currentState(handle.id)
            if (isReconnectStable(session)) {
                return true
            }

            if (session == null || session.hostState == BleHostSessionState.Error || session.hostState == BleHostSessionState.Disconnected) {
                return false
            }

            delay(100)
        }
        return isReconnectStable(currentState(handle.id))
    }

    private suspend fun restoreMonitoringAfterReconnect(handle: SessionHandle) {
        val session = currentState(handle.id)
        val alreadyLiveAfterReconnect = isAlreadyStreamingAfterReconnect(session)
        val resumingRecordingLike = handle.resumePackedTimeAfterReconnect ||
            session?.hostState == BleHostSessionState.Recording ||
            session?.hostState == BleHostSessionState.StoppingRecording ||
            (session?.recTimePacketCount ?: 0) > 0

        if (!handle.resumePreviewAfterReconnect) {
            if (handle.resumeCameraPreviewAfterReconnect) {
                startCameraPreviewPolling(handle)
                handle.resumeCameraPreviewAfterReconnect = false
            }
            return
        }

        if (alreadyLiveAfterReconnect) {
            if (resumingRecordingLike) {
                startPackedTimeLoop(handle)
            }
            updateSession(handle.id) {
                appendEvent(
                    it.copy(
                        lastMessage = if (resumingRecordingLike) {
                            "Recording telemetry already active after reconnect"
                        } else {
                            "Preview stream already active after reconnect"
                        },
                    ),
                    if (resumingRecordingLike) {
                        "Recording telemetry already active after reconnect"
                    } else {
                        "Preview stream already active after reconnect"
                    },
                )
            }
            handle.resumePreviewAfterReconnect = false
            handle.resumePackedTimeAfterReconnect = false
            if (handle.resumeCameraPreviewAfterReconnect) {
                startCameraPreviewPolling(handle)
                handle.resumeCameraPreviewAfterReconnect = false
            }
            return
        }

        val selection = currentState(handle.id)?.previewSelection ?: PreviewSelection.Default
        writeCommand(handle, Ce32Protocol.buildPreviewStart(), "preview restore start")
        writeCommand(handle, Ce32Protocol.buildPreviewSelect(selection), "preview restore selection")
        writeCommand(handle, Ce32Protocol.buildPreviewStart(), "preview restore start")
        if (resumingRecordingLike) {
            startPackedTimeLoop(handle)
        }
        updateSession(handle.id) {
            appendEvent(
                it.copy(
                    hostState = if (resumingRecordingLike) it.hostState else BleHostSessionState.Previewing,
                    statusText = if (resumingRecordingLike) it.statusText else "Previewing",
                    lastMessage = "Preview monitoring restore requested",
                ),
                "Preview monitoring restore requested",
            )
        }
        if (!resumingRecordingLike) {
            tryQueuePreviewPrime(handle, initialDelayMs = 150L)
        }
        handle.resumePreviewAfterReconnect = false
        handle.resumePackedTimeAfterReconnect = false
        if (handle.resumeCameraPreviewAfterReconnect) {
            startCameraPreviewPolling(handle)
            handle.resumeCameraPreviewAfterReconnect = false
        }
    }

    private fun isAlreadyStreamingAfterReconnect(session: DeviceSessionUiState?): Boolean {
        if (session == null) {
            return false
        }

        if (session.previewPacketCount > 0 || session.recTimePacketCount > 0) {
            return true
        }

        return session.hostState == BleHostSessionState.Previewing ||
            session.hostState == BleHostSessionState.Recording ||
            session.hostState == BleHostSessionState.StoppingRecording
    }

    private fun startReconnectFlow(handle: SessionHandle, reason: String) {
        if (handle.reconnectPending || handle.reconnectJob?.isActive == true) {
            return
        }

        handle.reconnectPending = true
        handle.reconnectAttemptCount = 0
        updateSession(handle.id) {
            appendEvent(
                it.copy(
                    hostState = BleHostSessionState.Reconnecting,
                    statusText = "Reconnecting...",
                    syncText = "Sync: reconnect pending",
                    lastMessage = reason,
                ),
                "Reconnect scheduled",
            )
        }

        handle.reconnectJob = ioScope.launch {
            beginReconnectConnectPhase()
            try {
                while (handle.reconnectPending &&
                    !handle.disconnectRequestedByUser &&
                    handle.reconnectAttemptCount < MaxReconnectAttempts
                ) {
                    handle.reconnectAttemptCount += 1
                    val attempt = handle.reconnectAttemptCount
                    updateSession(handle.id) {
                        appendEvent(
                            it.copy(
                                hostState = BleHostSessionState.Reconnecting,
                                statusText = "Reconnecting...",
                                syncText = "Sync: reconnect attempt $attempt/$MaxReconnectAttempts",
                                lastMessage = "Reconnect attempt $attempt",
                            ),
                            "Reconnect attempt $attempt/$MaxReconnectAttempts",
                        )
                    }

                    delay(ReconnectDelayMs)
                    if (!handle.reconnectPending || handle.disconnectRequestedByUser) {
                        return@launch
                    }

                    connect(handle.id, makeActive = false, fromReconnect = true)
                    val stabilized = waitForReconnectStabilization(handle)
                    if (stabilized) {
                        handle.reconnectPending = false
                        handle.reconnectAttemptCount = 0
                        restoreMonitoringAfterReconnect(handle)
                        updateSession(handle.id) {
                            appendEvent(
                                it.copy(lastMessage = "Reconnect stabilized"),
                                "Reconnect stabilized",
                            )
                        }
                        return@launch
                    }
                }

                if (handle.reconnectPending && !handle.disconnectRequestedByUser) {
                    handle.reconnectPending = false
                    handle.reconnectAttemptCount = 0
                    handle.resumePreviewAfterReconnect = false
                    handle.resumePackedTimeAfterReconnect = false
                    handle.resumeCameraPreviewAfterReconnect = false
                    updateSession(handle.id) {
                        appendEvent(
                            it.copy(
                                hostState = BleHostSessionState.Error,
                                statusText = "Error",
                                syncText = "Sync: reconnect failed",
                                lastFailure = "Reconnect attempts exhausted",
                                lastMessage = "Reconnect attempts exhausted",
                            ),
                            "Reconnect attempts exhausted",
                        )
                    }
                }
            } finally {
                finishManualConnectPhase(handle)
                finishReconnectConnectPhase()
                handle.reconnectJob = null
            }
        }
    }

    private fun isRecordOperationBusy(session: DeviceSessionUiState, handle: SessionHandle, nowMs: Long): Boolean {
        val stopStillSettling = handle.lastStopRecordingRequestAtMs > 0L &&
            (nowMs - handle.lastStopRecordingRequestAtMs) < RecordBusyGraceWindowMs
        val recorderBusy = session.hostState in setOf(
            BleHostSessionState.StartingRecording,
            BleHostSessionState.Recording,
            BleHostSessionState.StoppingRecording,
        )
        val syncBusy = session.hostState in setOf(
            BleHostSessionState.Syncing,
            BleHostSessionState.Reconnecting,
        )
        return stopStillSettling || recorderBusy || syncBusy
    }

    private suspend fun waitForRecordOperationReady(handle: SessionHandle, label: String) {
        var deferred = false
        val deadlineMs = System.currentTimeMillis() + RecordBusyGraceWindowMs
        while (System.currentTimeMillis() < deadlineMs) {
            val session = currentState(handle.id) ?: return
            if (!isRecordOperationBusy(session, handle, System.currentTimeMillis())) {
                break
            }

            if (!deferred) {
                deferred = true
                val summary = "$label deferred until recorder is idle"
                _statusBanner.value = "Waiting for recorder idle before continuing..."
                updateSession(handle.id) {
                    appendEvent(it.copy(lastMessage = summary), summary)
                }
            }
            delay(150)
        }

        if (deferred) {
            val session = currentState(handle.id) ?: return
            if (isRecordOperationBusy(session, handle, System.currentTimeMillis())) {
                val summary = "$label still sees recorder busy after wait window; issuing request anyway"
                updateSession(handle.id) {
                    appendEvent(it.copy(lastMessage = summary), summary)
                }
            }
        }
    }

    private fun stopCameraPreviewPolling(handle: SessionHandle) {
        handle.cameraPreviewPollJob?.cancel()
        handle.cameraPreviewPollJob = null
        handle.cameraPreviewRequestPending = false
        handle.cameraPreviewRequestStartedAtMs = 0L
    }

    private fun startCameraPreviewPolling(handle: SessionHandle) {
        stopCameraPreviewPolling(handle)
        if (!handle.cameraPreviewStreamingEnabled) {
            return
        }

        handle.cameraPreviewPollJob = ioScope.launch {
            while (handle.cameraPreviewStreamingEnabled) {
                if (!isConnected(handle.id)) {
                    break
                }

                val nowMs = System.currentTimeMillis()
                val frameTimedOut = handle.cameraPreviewRequestPending &&
                    nowMs - handle.cameraPreviewRequestStartedAtMs >= CameraPreviewFrameTimeoutMs
                if (!handle.cameraPreviewRequestPending || frameTimedOut) {
                    handle.cameraPreviewRequestPending = true
                    handle.cameraPreviewRequestStartedAtMs = nowMs
                    val sent = writeCommand(
                        handle,
                        Ce32Protocol.buildSnapshotRequest(preview = true),
                        "camera preview poll",
                        surfaceMessage = false,
                        surfaceEvent = false,
                    )
                    if (!sent) {
                        handle.cameraPreviewRequestPending = false
                    }
                }

                delay(CameraPreviewPollIntervalMs)
            }
        }
    }

    private fun stopInitialBootstrap(handle: SessionHandle, clearSyncStarted: Boolean = true) {
        handle.initialBootstrapJob?.cancel()
        handle.initialBootstrapJob = null
        if (clearSyncStarted) {
            handle.initialSyncStarted = false
            handle.initialSyncCompleted = false
            handle.bootstrapReadyObserved = false
        }
    }

    private fun startInitialBootstrap(handle: SessionHandle) {
        stopInitialBootstrap(handle)
        handle.bootstrapSystemParamsReceived = false
        handle.bootstrapDspReadRequested = false
        handle.initialBootstrapJob = ioScope.launch {
            var bootstrapFinishedWithoutSync = false
            try {
                val bootstrapStartedAtMs = System.currentTimeMillis()
                if (handle.useLegacyWakePrefix) {
                    delay(InitialLegacyHandshakeDelayMs)
                } else {
                    delay(InitialHandshakeSettleMs)
                }

                if (handle.fastBootstrapRequested) {
                    handle.fastBootstrapRequested = false
                    val fastHandshakeOk = writeCommand(
                        handle,
                        Ce32Protocol.buildFastHandshake(),
                        "fast handshake",
                    )
                    if (fastHandshakeOk) {
                        updateSession(handle.id) {
                            val nextState = when (it.hostState) {
                                BleHostSessionState.Previewing,
                                BleHostSessionState.StartingRecording,
                                BleHostSessionState.Recording,
                                BleHostSessionState.StoppingRecording -> it.hostState
                                else -> BleHostSessionState.Syncing
                            }
                            appendEvent(
                                it.copy(
                                    hostState = nextState,
                                    statusText = statusTextForState(nextState),
                                    syncText = "Sync: fast bootstrap requested",
                                    lastFailure = "",
                                    lastMessage = "Fast BLE handshake requested",
                                ),
                                "Fast BLE handshake requested",
                            )
                        }
                    }
                }

                var handshakeAttempts = 0
                var lastHandshakeAtMs = 0L
                var deviceInfoAttempts = 0
                var nextDeviceInfoAtMs = bootstrapStartedAtMs + InitialDeviceInfoInitialDelayMs
                while (isConnected(handle.id) && !handle.disconnectRequestedByUser && !handle.reconnectPending) {
                    val session = currentState(handle.id) ?: return@launch
                    if (isReconnectStable(session)) {
                        return@launch
                    }

                    val legacyBusyRemainingMs = (handle.legacyConfigBusyUntilMs - System.currentTimeMillis()).coerceAtLeast(0L)
                    if (legacyBusyRemainingMs > 0L) {
                        delay(legacyBusyRemainingMs.coerceAtMost(InitialHandshakeRetryMs))
                        continue
                    }

                    val nowMs = System.currentTimeMillis()
                    if (!handle.initialSyncStarted &&
                        !handle.bootstrapReadyObserved &&
                        handshakeAttempts < InitialHandshakeMaxAttempts &&
                        (handshakeAttempts == 0 || nowMs - lastHandshakeAtMs >= InitialHandshakeRetryMs)
                    ) {
                        writeCommand(
                            handle,
                            Ce32Protocol.buildSyncStart(),
                            if (handle.useLegacyWakePrefix) "legacy initial handshake" else "initial handshake",
                        )
                        handshakeAttempts += 1
                        lastHandshakeAtMs = nowMs
                    }

                    if (!handle.bootstrapSystemParamsReceived &&
                        session.parsedSystemParams == null &&
                        deviceInfoAttempts < InitialDeviceInfoMaxAttempts &&
                        nowMs >= nextDeviceInfoAtMs
                    ) {
                        val firstDeviceInfoRequest = deviceInfoAttempts == 0
                        writeCommand(
                            handle,
                            Ce32Protocol.buildReadSystemParams(),
                            if (firstDeviceInfoRequest) "read system params" else "bootstrap system params",
                            surfaceEvent = firstDeviceInfoRequest,
                        )
                        if (firstDeviceInfoRequest) {
                            writeCommand(
                                handle,
                                Ce32Protocol.buildReadDspParams(0),
                                "bootstrap dsp1",
                                surfaceMessage = false,
                                surfaceEvent = false,
                            )
                            writeCommand(
                                handle,
                                Ce32Protocol.buildReadDspParams(1),
                                "bootstrap dsp2",
                                surfaceMessage = false,
                                surfaceEvent = false,
                            )
                        }
                        deviceInfoAttempts += 1
                        nextDeviceInfoAtMs = nowMs + InitialDeviceInfoRetryMs
                    }

                    val handshakeSettled = handle.initialSyncCompleted || handle.bootstrapReadyObserved
                    val deviceInfoSettled = handle.bootstrapSystemParamsReceived
                    if (handshakeSettled && deviceInfoSettled) {
                        break
                    }
                    if (nowMs - bootstrapStartedAtMs >= InitialBootstrapTimeoutMs) {
                        bootstrapFinishedWithoutSync = true
                        return@launch
                    }
                    delay(InitialBootstrapPollMs)
                }

                bootstrapFinishedWithoutSync = true
            } finally {
                if (bootstrapFinishedWithoutSync) {
                    markInitialBootstrapTimeout(handle)
                }
                handle.initialBootstrapJob = null
            }
        }
    }

    private fun markInitialBootstrapTimeout(handle: SessionHandle) {
        val session = currentState(handle.id) ?: return
        Log.d(
            "Ce32BleManager",
            "bootstrap exit device=${handle.id} connected=${isConnected(handle.id)} " +
                "state=${session.hostState} reconnect=${handle.reconnectPending} " +
                "syncComplete=${handle.initialSyncCompleted} ready=${handle.bootstrapReadyObserved}",
        )
        if (!isConnected(handle.id) ||
            isReconnectStable(session) ||
            handle.disconnectRequestedByUser ||
            handle.reconnectPending
        ) {
            return
        }

        updateSession(handle.id) {
            appendEvent(
                it.copy(
                    hostState = BleHostSessionState.Connected,
                    statusText = "Connected",
                    syncText = "Sync: manual read needed",
                    awaitingLiveSync = false,
                    lastFailure = "Initial handshake did not finish automatically",
                    lastMessage = "Initial handshake did not finish automatically",
                ),
                "Initial handshake did not finish automatically",
            )
        }
    }

    private fun requestSystemParamsIfNeeded(handle: SessionHandle, reasonLabel: String) {
        val session = currentState(handle.id) ?: return
        if (!isConnected(handle.id) || handle.disconnectRequestedByUser || handle.reconnectPending) {
            return
        }
        if (handle.bootstrapSystemParamsReceived || session.parsedSystemParams != null) {
            return
        }
        ioScope.launch {
            if (!isConnected(handle.id) || handle.disconnectRequestedByUser || handle.reconnectPending) {
                return@launch
            }
            writeCommand(
                handle,
                Ce32Protocol.buildReadSystemParams(),
                reasonLabel,
                surfaceEvent = false,
            )
        }
    }

    private fun requestDspParamsIfNeeded(handle: SessionHandle, reasonLabel: String) {
        val session = currentState(handle.id) ?: return
        if (!isConnected(handle.id) || handle.disconnectRequestedByUser || handle.reconnectPending) {
            return
        }

        val missingDsp1 = session.parsedDsp1Params == null || session.dsp1ParamHex.isBlank()
        val missingDsp2 = session.parsedDsp2Params == null || session.dsp2ParamHex.isBlank()
        if ((!missingDsp1 && !missingDsp2) || handle.bootstrapDspReadRequested) {
            return
        }

        handle.bootstrapDspReadRequested = true
        ioScope.launch {
            if (!isConnected(handle.id) || handle.disconnectRequestedByUser || handle.reconnectPending) {
                return@launch
            }
            if (missingDsp1) {
                writeCommand(
                    handle,
                    Ce32Protocol.buildReadDspParams(0),
                    "$reasonLabel dsp1",
                    surfaceEvent = false,
                )
            }
            if (missingDsp2) {
                writeCommand(
                    handle,
                    Ce32Protocol.buildReadDspParams(1),
                    "$reasonLabel dsp2",
                    surfaceEvent = false,
                )
            }
        }
    }

    private fun updateSession(deviceId: String, transform: (DeviceSessionUiState) -> DeviceSessionUiState) {
        _sessions.update { current ->
            val previous = current[deviceId] ?: return@update current
            current + (deviceId to transform(previous))
        }
    }

    private suspend fun requestLogBlock(handle: SessionHandle, blockIndex: Int): ByteArray? {
        val deferred = CompletableDeferred<ByteArray>()
        handle.pendingLogBlock = deferred
        val writeOk = writeCommand(handle, Ce32Protocol.buildLogBlockRequest(blockIndex), "log block $blockIndex")
        if (!writeOk) {
            handle.pendingLogBlock = null
            return null
        }
        return withTimeoutOrNull(3_500) { deferred.await() }.also {
            handle.pendingLogBlock = null
        }
    }

    private suspend fun requestDeleteRecords(handle: SessionHandle, deleteCount: Int): Long? {
        waitForRecordOperationReady(handle, "Delete ${deleteCount} record(s)")
        val deferred = CompletableDeferred<Long>()
        handle.pendingDeleteAck = deferred
        val writeOk = writeCommand(handle, Ce32Protocol.buildDeleteRecords(deleteCount), "delete record")
        if (!writeOk) {
            handle.pendingDeleteAck = null
            return null
        }
        return withTimeoutOrNull(3_500) { deferred.await() }.also {
            handle.pendingDeleteAck = null
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun configureSession(handle: SessionHandle, gatt: BluetoothGatt) {
        val dataService = gatt.services.firstOrNull { it.uuid == Ce32Protocol.ServiceUuid }
        val infoService = gatt.services.firstOrNull { it.uuid == Ce32Protocol.DeviceInfoServiceUuid }
        if (dataService == null) {
            val failure = "BLE data service FFF0 was not found."
            updateSession(handle.id) {
                appendEvent(
                    it.copy(
                    hostState = BleHostSessionState.Error,
                    statusText = "Error",
                    verifiedTransport = false,
                    lastFailure = failure,
                    lastMessage = failure,
                    ),
                    "FFF0 data service missing",
                )
            }
            forceDisconnectLink(handle, failure)
            return
        }

        handle.dataService = dataService
        handle.txCharacteristic = dataService.getCharacteristic(Ce32Protocol.TxUuid)
        handle.legacyTxCharacteristic = dataService.getCharacteristic(Ce32Protocol.LegacyTxUuid)
        handle.rxCharacteristic = dataService.getCharacteristic(Ce32Protocol.RxUuid)
        handle.swVersion = null
        handle.hwVersion = null
        handle.useLegacyWakePrefix = false

        if (handle.txCharacteristic == null || handle.rxCharacteristic == null) {
            val failure = "TX/RX characteristics were not found."
            updateSession(handle.id) {
                appendEvent(
                    it.copy(
                    hostState = BleHostSessionState.Error,
                    statusText = "Error",
                    lastFailure = failure,
                    lastMessage = failure,
                    ),
                    "TX/RX characteristics missing",
                )
            }
            forceDisconnectLink(handle, failure)
            return
        }

        val rx = handle.rxCharacteristic ?: return
        val tx = handle.txCharacteristic ?: return
        val legacyTx = handle.legacyTxCharacteristic
        Log.d(
            "Ce32BleManager",
            "gatt transport device=${handle.id} txProps=0x${tx.properties.toString(16)} " +
                "rxProps=0x${rx.properties.toString(16)} " +
                "rxCccd=${rx.getDescriptor(ClientCharacteristicConfigUuid) != null} " +
                "legacyTx=${legacyTx != null} legacyTxProps=${legacyTx?.properties?.toString(16) ?: "-"}",
        )

        val notificationsEnabled = enableNotifications(gatt, handle)
        if (!notificationsEnabled) {
            val failure = "Failed to enable BLE notifications."
            updateSession(handle.id) {
                appendEvent(
                    it.copy(
                    hostState = BleHostSessionState.Error,
                    statusText = "Error",
                    lastFailure = failure,
                    lastMessage = failure,
                    ),
                    "Notification enable failed",
                )
            }
            forceDisconnectLink(handle, failure)
            return
        }

        // 180A metadata is optional; do not let it block the transport bring-up path.
        val swVersion = infoService?.getCharacteristic(Ce32Protocol.SwVersionUuid)?.let { readCharacteristic(gatt, handle, it) }?.decodeToString()
            ?.trim('\u0000', ' ')
        val hwVersion = infoService?.getCharacteristic(Ce32Protocol.HwVersionUuid)?.let { readCharacteristic(gatt, handle, it) }?.decodeToString()
            ?.trim('\u0000', ' ')

        handle.swVersion = swVersion
        handle.hwVersion = hwVersion
        handle.useLegacyWakePrefix = Ce32Protocol.isLegacyWakeFirmware(swVersion)
        Log.d(
            "Ce32BleManager",
            "firmware metadata device=${handle.id} sw=${swVersion ?: "-"} hw=${hwVersion ?: "-"} " +
                "legacyWake=${handle.useLegacyWakePrefix}",
        )

        stopConnectWatchdog(handle)
        updateSession(handle.id) {
            appendEvent(
                it.copy(
                hostState = BleHostSessionState.Syncing,
                statusText = "Syncing...",
                syncText = "Sync: handshake started",
                verifiedTransport = true,
                awaitingLiveSync = true,
                swVersion = swVersion,
                hwVersion = hwVersion,
                isLegacyWakeFirmware = handle.useLegacyWakePrefix,
                lastMessage = "BLE link ready",
                ),
                "BLE link ready",
            )
        }
        persistDiscoverySessions()

        startInitialBootstrap(handle)
        if (!handle.reconnectPending) {
            finishManualConnectPhase(handle)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun writeCommand(
        handle: SessionHandle,
        command: ByteArray,
        label: String,
        surfaceMessage: Boolean = true,
        surfaceEvent: Boolean = true,
        beforeFirstChunkSend: ((ByteArray) -> Unit)? = null,
    ): Boolean {
        val gatt = handle.gatt ?: return false
        val tx = handle.txCharacteristic ?: return false

        return handle.writeMutex.withLock {
            if (handle.useLegacyWakePrefix) {
                val wakeCharacteristic = handle.legacyTxCharacteristic ?: tx
                if (!writeChunk(gatt, handle, wakeCharacteristic, byteArrayOf(0x80.toByte()), "legacy wake")) {
                    markWriteFailure(handle.id, "$label failed during legacy wake")
                    return@withLock false
                }
            }

            Log.d(
                "Ce32BleManager",
                "tx device=${handle.id} label=$label bytes=${Ce32Protocol.bytesToHex(command)}",
            )
            val mtuChunk = (handle.mtu - 3).coerceAtLeast(20)
            var offset = 0
            var firstChunk = true
            while (offset < command.size) {
                if (firstChunk) {
                    beforeFirstChunkSend?.invoke(command)
                    firstChunk = false
                }
                val nextOffset = (offset + mtuChunk).coerceAtMost(command.size)
                val chunk = command.copyOfRange(offset, nextOffset)
                if (!writeChunk(gatt, handle, tx, chunk, label)) {
                    markWriteFailure(handle.id, "$label write failed")
                    return@withLock false
                }
                offset = nextOffset
            }

            updateSession(handle.id) {
                var next = it.copy(
                    lastFailure = "",
                    lastMessage = if (surfaceMessage) label else it.lastMessage,
                    commandTxCount = it.commandTxCount + 1,
                )
                val event = if (surfaceEvent) eventForCommand(label) else null
                if (event != null) {
                    next = appendEvent(next, event)
                }
                next
            }
            true
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun writeChunk(
        gatt: BluetoothGatt,
        handle: SessionHandle,
        characteristic: BluetoothGattCharacteristic,
        payload: ByteArray,
        label: String,
    ): Boolean {
        val supportsWriteWithResponse =
            (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
        val supportsWriteWithoutResponse =
            (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
        val writeType = when {
            supportsWriteWithResponse -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            supportsWriteWithoutResponse -> BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            else -> {
                markWriteFailure(handle.id, "$label characteristic is not writable")
                return false
            }
        }
        val deferred = CompletableDeferred<Boolean>()
        handle.pendingWrite = deferred
        val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(characteristic, payload, writeType) == BluetoothStatusCodesCompat.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            run {
                characteristic.writeType = writeType
                characteristic.value = payload
                gatt.writeCharacteristic(characteristic)
            }
        }
        if (!started) {
            handle.pendingWrite = null
            return false
        }

        if (writeType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) {
            handle.pendingWrite = null
            return true
        }

        val success = withTimeoutOrNull(1_500) { deferred.await() } ?: false
        if (!success) {
            markWriteFailure(handle.id, "$label timed out")
        }
        return success
    }

    @SuppressLint("MissingPermission")
    private suspend fun readCharacteristic(
        gatt: BluetoothGatt,
        handle: SessionHandle,
        characteristic: BluetoothGattCharacteristic,
    ): ByteArray? {
        val deferred = CompletableDeferred<ByteArray?>()
        handle.pendingRead = deferred
        handle.pendingReadUuid = characteristic.uuid
        @Suppress("DEPRECATION")
        val started = gatt.readCharacteristic(characteristic)
        if (!started) {
            handle.pendingRead = null
            handle.pendingReadUuid = null
            return null
        }

        return withTimeoutOrNull(1_500) { deferred.await() }?.also {
            handle.pendingRead = null
            handle.pendingReadUuid = null
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun enableNotifications(gatt: BluetoothGatt, handle: SessionHandle): Boolean {
        val characteristic = handle.rxCharacteristic ?: return false
        val supportsNotify =
            (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
        val supportsIndicate =
            (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
        val value = when {
            supportsNotify -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            supportsIndicate -> BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            else -> return false
        }
        if (!gatt.setCharacteristicNotification(characteristic, true)) {
            return false
        }

        val descriptor = characteristic.getDescriptor(ClientCharacteristicConfigUuid)
            ?: return false
        Log.d(
            "Ce32BleManager",
            "enabling ${if (supportsNotify) "notify" else "indicate"} for ${handle.id}",
        )

        val deferred = CompletableDeferred<Boolean>()
        handle.pendingDescriptorWrite = deferred
        val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, value) == BluetoothStatusCodesCompat.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            run {
                descriptor.value = value
                gatt.writeDescriptor(descriptor)
            }
        }
        if (!started) {
            handle.pendingDescriptorWrite = null
            return false
        }

        return withTimeoutOrNull(3_000) { deferred.await() } ?: false
    }

    @SuppressLint("MissingPermission")
    private fun continueDeferredServiceDiscovery(
        handle: SessionHandle,
        gatt: BluetoothGatt,
        message: String,
        eventSummary: String,
    ) {
        val shouldStart = synchronized(handle) {
            if (handle.gatt !== gatt || !handle.mtuNegotiationPending) {
                false
            } else {
                handle.mtuNegotiationPending = false
                cancelMtuNegotiationFallback(handle)
                true
            }
        }
        if (!shouldStart) {
            return
        }

        updateSession(handle.id) {
            appendEvent(
                it.copy(lastMessage = message),
                eventSummary,
            )
        }

        if (!gatt.discoverServices()) {
            val failure = "Service discovery could not be started."
            updateSession(handle.id) {
                appendEvent(
                    it.copy(
                        hostState = BleHostSessionState.Error,
                        statusText = "Error",
                        lastFailure = failure,
                        lastMessage = failure,
                    ),
                    failure,
                )
            }
            forceDisconnectLink(handle, failure)
        }
    }

    private fun startMtuNegotiationFallback(handle: SessionHandle, gatt: BluetoothGatt) {
        cancelMtuNegotiationFallback(handle)
        handle.mtuNegotiationFallbackJob = ioScope.launch {
            delay(MtuCallbackFallbackMs)
            continueDeferredServiceDiscovery(
                handle = handle,
                gatt = gatt,
                message = "Discovering services...",
                eventSummary = "MTU callback timed out; continuing with service discovery",
            )
        }
    }

    private fun markWriteFailure(deviceId: String, failure: String) {
        updateSession(deviceId) {
            appendEvent(
                it.copy(lastFailure = failure, lastMessage = failure),
                failure,
            )
        }
    }

    private fun startPackedTimeLoop(handle: SessionHandle) {
        handle.periodicPackedTimeJob?.cancel()
        handle.periodicPackedTimeJob = ioScope.launch {
            while (true) {
                delay(1_000)
                if (!isConnected(handle.id)) {
                    break
                }
                writeCommand(handle, Ce32Protocol.buildPackedTimeMeasurement(), "packed time sync")
            }
        }
    }

    private fun finalizeDisconnect(deviceId: String, reason: String) {
        val handle = handles[deviceId] ?: return
        stopConnectWatchdog(handle)
        cancelMtuNegotiationFallback(handle)
        clearRecordAckTimeouts(handle)
        clearPreviewFallbackTracking(handle)
        clearPreviewPrimeTracking(handle)
        if (!handle.reconnectPending) {
            finishManualConnectPhase(handle)
        }
        handle.pendingWrite?.complete(false)
        handle.pendingRead?.complete(null)
        handle.pendingDescriptorWrite?.complete(false)
        handle.periodicPackedTimeJob?.cancel()
        handle.periodicPackedTimeJob = null
        handle.triggerWaveformCaptureFile = null
        handle.previewBuffer.clear()
        stopCameraPreviewPolling(handle)
        stopInitialBootstrap(handle)

        tryRefreshGatt(handle.gatt, reason)
        try {
            handle.gatt?.close()
        } catch (_: Throwable) {
        }

        handle.gatt = null
        handle.dataService = null
        handle.txCharacteristic = null
        handle.legacyTxCharacteristic = null
        handle.rxCharacteristic = null
        handle.mtu = 23
        handle.mtuNegotiationPending = false
        handle.fastBootstrapRequested = false
        handle.legacyReadyPromoted = false
        handle.legacyHandshakeAckSeen = false
        handle.legacyConfigBusyUntilMs = 0L
        handle.parser.reset()
        if (!handle.reconnectPending) {
            resetBleLinkStats(handle)
            resetLiveSyncStats(handle)
        }
        val platformBle133Failure =
            reason.startsWith("Android 5 BLE stack failed before service discovery", ignoreCase = true)
        val nextFailure = when {
            handle.disconnectRequestedByUser || reason == "Disconnected" -> ""
            reason.isBlank() -> ""
            else -> reason
        }
        val disconnectedStatusText = if (platformBle133Failure) {
            "Android 5 BLE 133"
        } else {
            "Disconnected"
        }

        updateSession(deviceId) {
            val reconnecting = handle.reconnectPending
            val next = if (reconnecting) {
                it.copy(
                    hostState = BleHostSessionState.Reconnecting,
                    statusText = "Reconnecting...",
                    syncText = "Sync: reconnect pending",
                    lastFailure = nextFailure,
                    triggerWaveformCaptureActive = false,
                    lastMessage = reason,
                )
            } else {
                handle.cameraPreviewStreamingEnabled = false
                it.copy(
                    hostState = BleHostSessionState.Disconnected,
                    statusText = disconnectedStatusText,
                    syncText = "",
                    previewPoints = emptyList(),
                    recordingSeconds = 0L,
                    previewPacketCount = 0,
                    recTimePacketCount = 0,
                    notificationRxCount = 0,
                    legacyConfigBusyCount = 0,
                    bleLinkStats = null,
                    lastFailure = nextFailure,
                    lastSyncMetric = null,
                    liveSync = null,
                    awaitingLiveSync = false,
                    cameraPreviewStreaming = false,
                    triggerWaveformEnabled = false,
                    triggerWaveformCaptureActive = false,
                    lastMessage = reason,
                )
            }
            appendEvent(next, if (reconnecting) "Link dropped, reconnect pending" else reason)
        }

        if (!handle.reconnectPending && _activeSessionId.value == deviceId) {
            val fallbackActiveId = _sessions.value.values
                .firstOrNull { it.id != deviceId && it.isConnected }
                ?.id
            setActiveSessionInternal(fallbackActiveId)
        }
    }

    @SuppressLint("DiscouragedPrivateApi")
    private fun tryRefreshGatt(gatt: BluetoothGatt?, reason: String) {
        if (gatt == null) {
            return
        }
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
            return
        }
        runCatching {
            val refresh = BluetoothGatt::class.java.getMethod("refresh").apply {
                isAccessible = true
            }
            val refreshed = refresh.invoke(gatt) as? Boolean
            Log.d("Ce32BleManager", "gatt refresh reason=$reason result=$refreshed")
        }.onFailure { error ->
            Log.d("Ce32BleManager", "gatt refresh skipped reason=$reason error=${error.message}")
        }
    }

    private fun resetBleLinkStats(handle: SessionHandle) {
        handle.bleLinkPacketCount = 0L
        handle.bleLinkSequenceErrorCount = 0
        handle.bleLinkMissingPacketCount = 0L
        handle.bleLinkLastRssiRaw = 0
        handle.bleLinkFirstPacketAtMs = 0L
        handle.bleLinkLastSeq = 0
        handle.bleLinkHasLastSeq = false
        handle.bleLinkLastStatusAtMs = 0L
    }

    private fun resetLiveSyncStats(handle: SessionHandle) {
        handle.liveSyncErrWindow.clear()
        handle.liveSyncErrSumMs = 0.0
        handle.liveSyncErrSumSqMs = 0.0
    }

    private fun liveSyncStatsSnapshot(handle: SessionHandle): Triple<Double, Double, Int> {
        val sampleCount = handle.liveSyncErrWindow.size
        if (sampleCount <= 0) {
            return Triple(0.0, 0.0, 0)
        }

        val mean = handle.liveSyncErrSumMs / sampleCount
        val std = if (sampleCount > 1) {
            val variance = (handle.liveSyncErrSumSqMs - (handle.liveSyncErrSumMs * handle.liveSyncErrSumMs) / sampleCount) / (sampleCount - 1)
            sqrt(variance.coerceAtLeast(0.0))
        } else {
            0.0
        }
        return Triple(mean, std, sampleCount)
    }

    private fun appendLiveSyncSample(handle: SessionHandle, errorMs: Int): Triple<Double, Double, Int> {
        handle.liveSyncErrWindow.addLast(errorMs)
        handle.liveSyncErrSumMs += errorMs
        handle.liveSyncErrSumSqMs += errorMs.toDouble() * errorMs.toDouble()
        while (handle.liveSyncErrWindow.size > LiveSyncErrWindowMax) {
            val oldest = handle.liveSyncErrWindow.removeFirst()
            handle.liveSyncErrSumMs -= oldest
            handle.liveSyncErrSumSqMs -= oldest.toDouble() * oldest.toDouble()
        }
        return liveSyncStatsSnapshot(handle)
    }

    private fun buildSyncMetric(sync: SyncStatus, summary: String): SyncMetricUiState {
        val modeLabel = when (sync.mode) {
            0x01 -> "Init"
            0x02 -> "Periodic"
            0x03 -> "Live"
            0x05 -> "InitTrain"
            else -> "Mode ${sync.mode}"
        }
        return SyncMetricUiState(
            mode = sync.mode,
            modeLabel = modeLabel,
            offsetMs = sync.offsetSeconds?.times(1000.0),
            accuracyMs = sync.accuracySeconds?.times(1000.0),
            delayMs = sync.delaySeconds?.times(1000.0),
            sampleCount = sync.sampleCount,
            summary = summary,
        )
    }

    private fun buildSyncSummaryText(sync: SyncStatus, liveSync: LiveSyncUiState? = null): String {
        val measurementCount = if (liveSync != null) {
            maxOf(sync.sampleCount, liveSync.sampleCount)
        } else {
            sync.sampleCount
        }
        val accuracyMs = when {
            liveSync != null && !liveSync.outlier -> liveSync.rollingStdMs
            else -> (sync.accuracySeconds ?: 0f) * 1000.0f
        }
        return when (sync.mode) {
            0x03 -> {
                if (liveSync == null) {
                    String.format(
                        Locale.US,
                        "Sync[Live] acc=%.1fms n=%d waiting for host timing",
                        accuracyMs,
                        measurementCount,
                    )
                } else if (liveSync.outlier) {
                    String.format(
                        Locale.US,
                        "Sync[Live] dev=%s off=outlier(%+dms) acc=%.1fms n=%d dly=%.1fms",
                        liveSync.deviceClockLabel,
                        liveSync.lastOffsetMs,
                        accuracyMs,
                        measurementCount,
                        liveSync.delayMs,
                    )
                } else {
                    String.format(
                        Locale.US,
                        "Sync[Live] dev=%s off=%+.1fms acc=%.1fms n=%d dly=%.1fms",
                        liveSync.deviceClockLabel,
                        liveSync.rollingMeanMs,
                        accuracyMs,
                        measurementCount,
                        liveSync.delayMs,
                    )
                }
            }

            0x05 -> String.format(
                Locale.US,
                "Sync[InitTrain] off=%.2fms err=%.2fms proc=%.2fms n=%d",
                (sync.offsetSeconds ?: 0f) * 1000.0f,
                (sync.accuracySeconds ?: 0f) * 1000.0f,
                (sync.delaySeconds ?: 0f) * 1000.0f,
                sync.sampleCount,
            )

            else -> {
                val modeText = if (sync.mode == 0x02) "Periodic" else "Init"
                String.format(
                    Locale.US,
                    "Sync[%s] off=%.3fms acc=%.3fms delay=%.3fms n=%d",
                    modeText,
                    (sync.offsetSeconds ?: 0f) * 1000.0f,
                    (sync.accuracySeconds ?: 0f) * 1000.0f,
                    (sync.delaySeconds ?: 0f) * 1000.0f,
                    sync.sampleCount,
                )
            }
        }
    }

    private fun isTrustedInitialSyncCompletion(sync: SyncStatus): Boolean {
        return when (sync.mode) {
            0x05 -> false
            0x01, 0x02 -> sync.sampleCount > 0
            else -> sync.sampleCount > 0
        }
    }

    private fun buildLiveSyncUiState(handle: SessionHandle, sync: SyncStatus): LiveSyncUiState? {
        val devClockSeconds = sync.offsetSeconds ?: return null
        val hostRxSeconds = sync.hostRxSeconds ?: return null
        val devTotalMs = ((devClockSeconds * 1000.0f).roundToInt() % LiveSyncWrapMs).let {
            if (it < 0) it + LiveSyncWrapMs else it
        }
        val pcTotalMs = ((hostRxSeconds * 1000.0f).roundToInt() % LiveSyncWrapMs).let {
            if (it < 0) it + LiveSyncWrapMs else it
        }
        val delayMs = ((sync.delaySeconds ?: 0f) * 1000.0f).roundToInt()
        var pcAtDevMs = pcTotalMs - delayMs
        if (pcAtDevMs < 0) {
            pcAtDevMs += LiveSyncWrapMs
        } else if (pcAtDevMs >= LiveSyncWrapMs) {
            pcAtDevMs -= LiveSyncWrapMs
        }

        var offsetMs = pcAtDevMs - devTotalMs
        if (offsetMs > LiveSyncHalfWrapMs) {
            offsetMs -= LiveSyncWrapMs
        } else if (offsetMs < -LiveSyncHalfWrapMs) {
            offsetMs += LiveSyncWrapMs
        }

        val outlier = abs(offsetMs) > LiveSyncErrOutlierAbsMs
        val (meanMs, stdMs, sampleCount) = if (outlier) {
            liveSyncStatsSnapshot(handle)
        } else {
            appendLiveSyncSample(handle, offsetMs)
        }

        val minutes = (devTotalMs / 60_000) % 60
        val seconds = (devTotalMs / 1_000) % 60
        val millis = devTotalMs % 1_000
        return LiveSyncUiState(
            deviceClockLabel = String.format(Locale.US, "%02d:%02d.%03d", minutes, seconds, millis),
            rollingMeanMs = meanMs,
            rollingStdMs = stdMs,
            sampleCount = sampleCount,
            delayMs = (sync.delaySeconds ?: 0f) * 1000.0,
            lastOffsetMs = offsetMs,
            outlier = outlier,
        )
    }

    private fun littleEndianUShort(payload: ByteArray, offset: Int): Int {
        val b0 = payload[offset].toInt() and 0xFF
        val b1 = payload[offset + 1].toInt() and 0xFF
        return b0 or (b1 shl 8)
    }

    private fun littleEndianUInt(payload: ByteArray, offset: Int): Long {
        val b0 = payload[offset].toLong() and 0xFF
        val b1 = payload[offset + 1].toLong() and 0xFF
        val b2 = payload[offset + 2].toLong() and 0xFF
        val b3 = payload[offset + 3].toLong() and 0xFF
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    private fun processBleLinkTestPacket(
        handle: SessionHandle,
        payload: ByteArray,
    ): BleLinkStatusUpdate {
        val seq = littleEndianUShort(payload, 0)
        val rssiRaw = payload[2].toInt() and 0xFF
        val nowMs = System.currentTimeMillis()

        var packetOk = true
        handle.bleLinkPacketCount += 1
        handle.bleLinkLastRssiRaw = rssiRaw
        if (handle.bleLinkFirstPacketAtMs == 0L) {
            handle.bleLinkFirstPacketAtMs = nowMs
        }
        if (handle.bleLinkHasLastSeq) {
            val delta = (seq - handle.bleLinkLastSeq) and 0xFFFF
            if (delta != 1) {
                packetOk = false
                handle.bleLinkSequenceErrorCount += 1
                if (delta > 1 && delta < 0x8000) {
                    handle.bleLinkMissingPacketCount += (delta - 1).toLong()
                }
            }
        }
        handle.bleLinkLastSeq = seq
        handle.bleLinkHasLastSeq = true

        val elapsedMs = (nowMs - handle.bleLinkFirstPacketAtMs).coerceAtLeast(0L)
        val rxRateHz = if (elapsedMs > 0L) {
            handle.bleLinkPacketCount * 1000.0 / elapsedMs.toDouble()
        } else {
            0.0
        }
        val stats = BleLinkStatsUiState(
            packetCount = handle.bleLinkPacketCount,
            missingPacketCount = handle.bleLinkMissingPacketCount,
            sequenceErrorCount = handle.bleLinkSequenceErrorCount,
            lastRssiRaw = handle.bleLinkLastRssiRaw,
            rxRateHz = rxRateHz,
        )
        val summary = String.format(
            Locale.US,
            "BLE link RSSI 0x%02X, packets %d, lost %d (%.1f%%), seqErr %d, rx %.2f/s",
            stats.lastRssiRaw,
            stats.packetCount,
            stats.missingPacketCount,
            stats.lossPercent,
            stats.sequenceErrorCount,
            stats.rxRateHz,
        )
        val statusDue = handle.bleLinkLastStatusAtMs == 0L ||
            nowMs - handle.bleLinkLastStatusAtMs >= BleLinkStatusIntervalMs
        val emitStatus = statusDue || !packetOk
        if (emitStatus) {
            handle.bleLinkLastStatusAtMs = nowMs
        }
        return BleLinkStatusUpdate(
            stats = stats,
            summary = summary,
            emitEvent = emitStatus,
        )
    }

    private inner class SessionGattCallback(
        private val deviceId: String,
    ) : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val handle = handles[deviceId] ?: return
            Log.d(
                "Ce32BleManager",
                "gatt state device=$deviceId status=$status newState=$newState sameGatt=${handle.gatt === gatt} reconnectPending=${handle.reconnectPending}",
            )
            if (handle.gatt != null && handle.gatt !== gatt) {
                try {
                    gatt.close()
                } catch (_: Throwable) {
                }
                return
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    handle.gatt = gatt
                    val requestMtu = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        runCatching { gatt.requestMtu(247) }.getOrDefault(false)
                    } else {
                        false
                    }
                    handle.mtuNegotiationPending = requestMtu
                    updateSession(deviceId) {
                        appendEvent(
                            it.copy(
                            hostState = BleHostSessionState.Connected,
                            statusText = "Connected",
                            lastMessage = if (requestMtu) "Negotiating MTU..." else "Discovering services...",
                            ),
                            "Link connected",
                        )
                    }
                    if (!requestMtu) {
                        gatt.discoverServices()
                    } else {
                        startMtuNegotiationFallback(handle, gatt)
                    }
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    val previousState = currentState(deviceId)
                    val legacyPlatform133 = isLegacyPlatform133ConnectFailure(previousState, status)
                    val retryInitialConnect = shouldRetryInitialConnect(handle, previousState, status)
                    if (handle.gatt == null &&
                        previousState?.hostState in setOf(
                            BleHostSessionState.Disconnected,
                            BleHostSessionState.Reconnecting,
                        )
                        && !retryInitialConnect
                    ) {
                        return
                    }
                    val reconnectRequested = retryInitialConnect ||
                        (!legacyPlatform133 && shouldAutoReconnect(handle, previousState))
                    if (reconnectRequested && !handle.reconnectPending) {
                        if (!retryInitialConnect) {
                            captureReconnectIntent(handle, previousState)
                        }
                        startReconnectFlow(
                            handle,
                            if (retryInitialConnect) {
                                "BLE connect failed ($status); retrying"
                            } else {
                                "BLE link dropped"
                            },
                        )
                    }
                    val reason = if (handle.expectedDisconnectReason.isNotBlank()) {
                        handle.expectedDisconnectReason
                    } else if (handle.disconnectRequestedByUser) {
                        "Disconnected"
                    } else if (legacyPlatform133) {
                        "Android 5 BLE stack failed before service discovery (133)"
                    } else if (previousState?.hostState == BleHostSessionState.Connecting &&
                        status != BluetoothGatt.GATT_SUCCESS
                    ) {
                        "BLE connect failed: $status"
                    } else if (status != BluetoothGatt.GATT_SUCCESS) {
                        "BLE link dropped ($status)"
                    } else {
                        "BLE link dropped"
                    }
                    if (legacyPlatform133 && !retryInitialConnect && !handle.reconnectPending) {
                        _statusBanner.value = "Android 5 BLE stack returned 133 before service discovery. Try a newer phone."
                    }
                    finalizeDisconnect(deviceId, reason)
                    handle.disconnectRequestedByUser = false
                    handle.expectedDisconnectReason = ""
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            val handle = handles[deviceId] ?: return
            Log.d("Ce32BleManager", "mtu changed device=$deviceId mtu=$mtu status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                handle.mtu = mtu
            }
            continueDeferredServiceDiscovery(
                handle = handle,
                gatt = gatt,
                message = "Discovering services...",
                eventSummary = if (status == BluetoothGatt.GATT_SUCCESS) {
                    "MTU ready"
                } else {
                    "MTU request failed; continuing with service discovery"
                },
            )
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val handle = handles[deviceId] ?: return
            Log.d("Ce32BleManager", "services discovered device=$deviceId status=$status")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                val failure = "Service discovery failed: $status"
                updateSession(deviceId) {
                    appendEvent(
                        it.copy(
                        hostState = BleHostSessionState.Error,
                        statusText = "Error",
                        lastFailure = failure,
                        lastMessage = failure,
                        ),
                        failure,
                    )
                }
                forceDisconnectLink(handle, failure)
                return
            }
            ioScope.launch {
                configureSession(handle, gatt)
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            handles[deviceId]?.pendingWrite?.complete(status == BluetoothGatt.GATT_SUCCESS)
            handles[deviceId]?.pendingWrite = null
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            val handle = handles[deviceId] ?: return
            if (handle.pendingReadUuid == characteristic.uuid) {
                handle.pendingRead?.complete(if (status == BluetoothGatt.GATT_SUCCESS) characteristic.value else null)
                handle.pendingRead = null
                handle.pendingReadUuid = null
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            val handle = handles[deviceId] ?: return
            if (handle.pendingReadUuid == characteristic.uuid) {
                handle.pendingRead?.complete(if (status == BluetoothGatt.GATT_SUCCESS) value else null)
                handle.pendingRead = null
                handle.pendingReadUuid = null
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            handles[deviceId]?.pendingDescriptorWrite?.complete(status == BluetoothGatt.GATT_SUCCESS)
            handles[deviceId]?.pendingDescriptorWrite = null
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val data = characteristic.value ?: return
            val handle = handles[deviceId] ?: return
            handleIncomingNotification(handle, data)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            val handle = handles[deviceId] ?: return
            handleIncomingNotification(handle, value)
        }
    }

    private fun handleIncomingNotification(handle: SessionHandle, value: ByteArray) {
        if (value.isEmpty()) {
            return
        }
        handle.notificationRxCount += 1
        val session = currentState(handle.id)
        val shouldDebugNotification =
            handle.notificationRxCount <= 12 ||
                ((session?.previewPacketCount ?: 0) == 0 &&
                    session?.hostState in setOf(
                        BleHostSessionState.Previewing,
                        BleHostSessionState.StartingRecording,
                        BleHostSessionState.Recording,
                    ))
        if (shouldDebugNotification) {
            val previewBytes = value.copyOfRange(0, value.size.coerceAtMost(24))
            Log.d(
                "Ce32BleManager",
                "rx notify device=${handle.id} count=${handle.notificationRxCount} size=${value.size} head=${Ce32Protocol.bytesToHex(previewBytes)}",
            )
        }
        if (value.contentEquals(LegacyConfigModeBusyMessage)) {
            noteConfigModeBusy(handle)
            return
        }
        handle.parser.push(value)
    }

    private fun handleOutOfFrameNotification(handle: SessionHandle, rawBytes: ByteArray) {
        if (!handle.useLegacyWakePrefix || rawBytes.isEmpty()) {
            return
        }

        Log.d(
            "Ce32BleManager",
            "rx out-of-frame device=${handle.id} size=${rawBytes.size} bytes=${Ce32Protocol.bytesToHex(rawBytes.copyOfRange(0, rawBytes.size.coerceAtMost(24)))}",
        )

        rawBytes.forEach { byte ->
            if ((byte.toInt() and 0xFF) == 0x80) {
                promoteLegacyReadyState(handle, "Legacy BLE handshake acknowledged")
            }
        }
    }

    private fun noteConfigModeBusy(handle: SessionHandle) {
        handle.legacyConfigBusyCount += 1
        val nowMs = System.currentTimeMillis()
        val alreadyBusy = handle.legacyConfigBusyUntilMs > nowMs
        handle.legacyConfigBusyUntilMs = maxOf(handle.legacyConfigBusyUntilMs, nowMs + LegacyConfigModeBusyBackoffMs)
        updateSession(handle.id) {
            val next = it.copy(
                notificationRxCount = handle.notificationRxCount,
                legacyConfigBusyCount = handle.legacyConfigBusyCount,
                lastMessage = "BLE config mode busy",
            )
            if (alreadyBusy) {
                next
            } else {
                appendEvent(next, "BLE config mode busy")
            }
        }
    }

    private fun promoteLegacyReadyState(handle: SessionHandle, message: String) {
        handle.legacyHandshakeAckSeen = true
        if (handle.legacyReadyPromoted) {
            return
        }

        handle.legacyReadyPromoted = true
        noteBootstrapReadyState(
            handle = handle,
            syncText = "Sync: legacy wake acknowledged",
            message = message,
        )
    }

    private fun noteBootstrapReadyState(
        handle: SessionHandle,
        syncText: String,
        message: String,
    ) {
        handle.bootstrapReadyObserved = true
        handle.initialSyncStarted = true
        updateSession(handle.id) {
            val nextState = when (it.hostState) {
                BleHostSessionState.Previewing,
                BleHostSessionState.StartingRecording,
                BleHostSessionState.Recording,
                BleHostSessionState.StoppingRecording -> it.hostState
                BleHostSessionState.Synced -> BleHostSessionState.Synced
                else -> BleHostSessionState.Syncing
            }
            val next = it.copy(
                hostState = nextState,
                statusText = statusTextForState(nextState),
                syncText = syncText,
                lastFailure = "",
                lastMessage = message,
            )
            appendEvent(next, message)
        }

        ioScope.launch {
            if (!isConnected(handle.id) || handle.disconnectRequestedByUser || handle.reconnectPending) {
                return@launch
            }
            writeCommand(handle, Ce32Protocol.buildReadSystemParams(), "read system params", surfaceEvent = false)
        }
    }

    private fun synchronizedHostState(hostState: BleHostSessionState): BleHostSessionState {
        return when (hostState) {
            BleHostSessionState.StartingRecording -> BleHostSessionState.StartingRecording
            BleHostSessionState.StoppingRecording -> BleHostSessionState.Synced
            BleHostSessionState.Previewing -> BleHostSessionState.Previewing
            BleHostSessionState.Recording -> BleHostSessionState.Recording
            else -> BleHostSessionState.Synced
        }
    }

    private fun handleFrame(deviceId: String, commandId: Int, payload: ByteArray) {
        val handle = handles[deviceId] ?: return
        val sessionBeforeUpdate = currentState(deviceId)
        val shouldDebugFrame =
            commandId in setOf(0x82, 0x8E, 0x8F, 0x90, 0x91, 0x92, 0xAD, 0xAF) ||
                ((sessionBeforeUpdate?.previewPacketCount ?: 0) == 0 &&
                    sessionBeforeUpdate?.hostState in setOf(
                        BleHostSessionState.Previewing,
                        BleHostSessionState.StartingRecording,
                        BleHostSessionState.Recording,
                    ))
        if (shouldDebugFrame) {
            Log.d(
                "Ce32BleManager",
                "rx frame device=$deviceId cmd=${formatCommandId(commandId)} payload=${payload.size} head=${Ce32Protocol.bytesToHex(payload.copyOfRange(0, payload.size.coerceAtMost(24)))}",
            )
        }
        updateSession(deviceId) {
            it.copy(
                commandRxCount = it.commandRxCount + 1,
                notificationRxCount = handle.notificationRxCount,
                legacyConfigBusyCount = handle.legacyConfigBusyCount,
            )
        }

        when (commandId) {
            0x10 -> {
                val stim = Ce32Protocol.parseStimControl(payload) ?: return
                updateSession(deviceId) {
                    val nextStimStatuses = it.stimControlStatuses.toMutableMap().apply {
                        this[stim.id] = stim
                    }
                    appendEvent(
                        it.copy(
                        stimControlStatus = stim,
                        stimControlStatuses = nextStimStatuses,
                        lastMessage = "Stim control update: ${stim.stateLabel}",
                        ),
                        "Stim ${stim.stateLabel}",
                    )
                }
            }

            0x82 -> {
                handle.initialSyncStarted = true
                val sync = Ce32Protocol.parseSyncStatus(payload) ?: return
                val syncComplete = isTrustedInitialSyncCompletion(sync)
                if (syncComplete) {
                    handle.initialSyncCompleted = true
                    stopInitialBootstrap(handle, clearSyncStarted = false)
                }
                if (sync.mode == 0x01) {
                    resetLiveSyncStats(handle)
                }
                val syncLine = buildSyncSummaryText(sync)
                val syncMetric = buildSyncMetric(sync, syncLine)
                appendSyncLog(
                    handle,
                    recordType = "sync_metric_0x82",
                    mode = sync.mode,
                    samples = sync.sampleCount,
                    offsetSec = sync.offsetSeconds?.toDouble(),
                    accuracySec = sync.accuracySeconds?.toDouble(),
                    delaySec = sync.delaySeconds?.toDouble(),
                )
                val stopAcknowledged = currentState(deviceId)?.hostState == BleHostSessionState.StoppingRecording
                if (stopAcknowledged) {
                    cancelRecordStopAckTimeout(handle)
                    handle.lastStopRecordingRequestAtMs = 0L
                }
                updateSession(deviceId) {
                    val nextState = if (syncComplete) {
                        synchronizedHostState(it.hostState)
                    } else {
                        it.hostState
                    }
                    val syncMessage = when {
                        stopAcknowledged -> "Record stop acknowledged via sync resume"
                        syncComplete -> "Sync complete"
                        else -> "Sync completion pending"
                    }
                    val next = it.copy(
                        hostState = nextState,
                        statusText = statusTextForState(nextState),
                        syncText = syncLine,
                        lastSyncMetric = syncMetric,
                        liveSync = null,
                        awaitingLiveSync = false,
                        lastMessage = syncMessage,
                    )
                    appendEvent(next, if (syncComplete || stopAcknowledged) syncLine else "$syncLine (sync incomplete)")
                }
                if (syncComplete && sync.mode == 0x01 && !handle.suppressRtcWriteDuringResync) {
                    ioScope.launch {
                        writeCommand(handle, Ce32Protocol.buildRtcSetCommand(), "rtc set")
                    }
                }
                if (stopAcknowledged) {
                    queueRecordListRefreshAfterStop(handle)
                }
                if (syncComplete) {
                    requestSystemParamsIfNeeded(handle, "sync-complete system params")
                    requestDspParamsIfNeeded(handle, "sync-complete dsp params")
                    previewPrimeDelayForInboundCommand(commandId)?.let { delayMs ->
                        tryQueuePreviewPrime(handle, initialDelayMs = delayMs)
                    }
                }
            }

            0x8B -> {
                updateSession(deviceId) {
                    appendEvent(
                        it.copy(
                            lastMessage = if (handle.suppressRtcWriteDuringResync) {
                                "RTC set request suppressed for manual resync"
                            } else {
                                "RTC set requested by device"
                            },
                        ),
                        if (handle.suppressRtcWriteDuringResync) {
                            "RTC set request suppressed for manual resync"
                        } else {
                            "RTC set requested by device"
                        },
                    )
                }
                if (!handle.suppressRtcWriteDuringResync) {
                    ioScope.launch {
                        writeCommand(handle, Ce32Protocol.buildRtcSetCommand(), "rtc set")
                    }
                }
            }

            0x8C -> {
                handle.initialSyncStarted = true
                val hostRx = ZonedDateTime.now()
                val deviceT0 = parseSyncStamp(payload, 0)
                updateSession(deviceId) {
                    appendEvent(
                        it.copy(lastMessage = "Sync probe 0x8C received"),
                        "Sync probe 0x8C received",
                    )
                }
                ioScope.launch {
                    writeCommand(handle, Ce32Protocol.build8CReply(payload, now = hostRx), "sync 0x8c reply")
                    if (deviceT0 != null) {
                        val hostT1 = syncStampFromTime(hostRx)
                        appendSyncLog(
                            handle,
                            recordType = "sync_meas_0x8C",
                            rtcDiffSec = syncDiffSeconds(hostT1, deviceT0),
                            t0Sec = deviceT0.seconds,
                            t0SubSec = deviceT0.subSeconds,
                            t1Sec = hostT1.seconds,
                            t1SubSec = hostT1.subSeconds,
                        )
                    }
                }
            }

            0x8D -> {
                handle.initialSyncStarted = true
                val hostRx = ZonedDateTime.now()
                val hostRxMono = System.nanoTime()
                val deviceT0 = parseSyncStamp(payload, 0)
                updateSession(deviceId) {
                    appendEvent(
                        it.copy(lastMessage = "Sync probe 0x8D received"),
                        "Sync probe 0x8D received",
                    )
                }
                ioScope.launch {
                    var hostTx = hostRx
                    var hostTxMono = hostRxMono
                    val command = Ce32Protocol.build8DReply(payload, hostRx = hostRx, hostTx = hostRx)
                    writeCommand(
                        handle,
                        command,
                        "sync 0x8d reply",
                        beforeFirstChunkSend = {
                            hostTxMono = System.nanoTime()
                            hostTx = ZonedDateTime.now()
                            Ce32Protocol.patch8DReplyTxStamp(it, hostTx)
                        },
                    )
                    if (deviceT0 != null) {
                        val hostT1 = syncStampFromTime(hostRx)
                        val hostT2 = syncStampFromTime(hostTx)
                        appendSyncLog(
                            handle,
                            recordType = "sync_meas_0x8D",
                            rtcDiffSec = syncDiffSeconds(hostT1, deviceT0),
                            t0Sec = deviceT0.seconds,
                            t0SubSec = deviceT0.subSeconds,
                            t1Sec = hostT1.seconds,
                            t1SubSec = hostT1.subSeconds,
                            t2Sec = hostT2.seconds,
                            t2SubSec = hostT2.subSeconds,
                            hostProcUs = ((hostTxMono - hostRxMono) / 1_000L).coerceAtLeast(0L),
                        )
                    }
                }
            }

            0x8E -> {
                val event = Ce32Protocol.parseRecordStartEvent(payload) ?: return
                val isRecordStartEvent = event.eventCode == 0x30
                if (isRecordStartEvent) {
                    if (handle.previewFallbackPending) {
                        handle.previewFallbackPending = false
                        cancelPreviewStartupMonitor(handle)
                    }
                    cancelRecordStartAckTimeout(handle)
                    handle.usesRecordingPreviewFallback = false
                }
                val timestampLabel = formatRecordStartEventLabel(event)
                updateSession(deviceId) {
                    val wasStartingRecording = it.hostState == BleHostSessionState.StartingRecording
                    val nextState = when {
                        isRecordStartEvent && it.hostState != BleHostSessionState.StoppingRecording ->
                            BleHostSessionState.Recording

                        else -> it.hostState
                    }
                    val nextMessage = when {
                        isRecordStartEvent && timestampLabel != null && wasStartingRecording ->
                            "Record start acknowledged at $timestampLabel"

                        isRecordStartEvent && timestampLabel != null ->
                            "Record start timestamp $timestampLabel"

                        isRecordStartEvent && wasStartingRecording ->
                            "Record start acknowledged via 0x8E"

                        isRecordStartEvent ->
                            "Record start event received"

                        else ->
                            "RTC event ${formatCommandId(event.eventCode)} received"
                    }
                    appendEvent(
                        it.copy(
                            hostState = nextState,
                            statusText = statusTextForState(nextState),
                            recordingSeconds = if (isRecordStartEvent) 0L else it.recordingSeconds,
                            lastMessage = nextMessage,
                        ),
                        nextMessage,
                    )
                }
            }

            0x8F -> {
                val sync = Ce32Protocol.parseSyncStatus(payload) ?: return
                handle.initialSyncStarted = true
                if (!handle.initialSyncCompleted) {
                    handle.initialSyncCompleted = true
                    stopInitialBootstrap(handle, clearSyncStarted = false)
                }
                val liveSync = buildLiveSyncUiState(handle, sync)
                val summary = buildSyncSummaryText(sync, liveSync)
                val syncMetric = buildSyncMetric(sync, summary)
                appendSyncLog(
                    handle,
                    recordType = "sync_live_0x8F",
                    mode = 0x03,
                    delaySec = sync.delaySeconds?.toDouble(),
                    pcRxTimeSec = sync.hostRxSeconds?.toDouble(),
                    devTimeSec = sync.offsetSeconds?.toDouble(),
                    estimatedDelaySec = sync.delaySeconds?.toDouble(),
                    computedOffsetSec = liveSync?.lastOffsetMs?.div(1000.0),
                )
                val stopAcknowledged = currentState(deviceId)?.hostState == BleHostSessionState.StoppingRecording
                if (stopAcknowledged) {
                    cancelRecordStopAckTimeout(handle)
                    handle.lastStopRecordingRequestAtMs = 0L
                }
                updateSession(deviceId) {
                    val nextState = synchronizedHostState(it.hostState)
                    val next = it.copy(
                        hostState = nextState,
                        statusText = statusTextForState(nextState),
                        syncText = summary,
                        lastSyncMetric = syncMetric,
                        liveSync = liveSync,
                        awaitingLiveSync = false,
                        lastMessage = if (stopAcknowledged) "Record stop acknowledged via sync resume" else "Live sync update",
                    )
                    if (stopAcknowledged) appendEvent(next, "Record stop acknowledged via sync resume") else next
                }
                if (stopAcknowledged) {
                    queueRecordListRefreshAfterStop(handle)
                }
            }

            0xEE -> {
                val linkUpdate = processBleLinkTestPacket(handle, payload)
                updateSession(deviceId) {
                    val next = it.copy(
                        bleLinkStats = linkUpdate.stats,
                        lastMessage = linkUpdate.summary,
                    )
                    if (linkUpdate.emitEvent) {
                        appendEvent(next, linkUpdate.summary)
                    } else {
                        next
                    }
                }
            }

            0x85 -> {
                val message = Ce32Protocol.parseAsciiMessage(payload)
                updateSession(deviceId) {
                    val detectedRoleTag = detectBleRoleTagFromName(it.name)
                    val parsedRole = tryParseBleHandshakeRoleMessage(message)
                    val roleTag = parsedRole?.roleTag?.ifBlank {
                        it.roleTag.ifBlank { detectedRoleTag }
                    } ?: it.roleTag.ifBlank { detectedRoleTag }
                    val functionTag = parsedRole?.functionTag?.ifBlank {
                        it.functionTag.ifBlank { detectBleFunctionTagFromRoleTag(roleTag) }
                    } ?: it.functionTag.ifBlank { detectBleFunctionTagFromRoleTag(roleTag) }
                    val displayMessage = when {
                        parsedRole != null -> formatBleRoleMessage(roleTag, functionTag)
                        message.isBlank() -> formatBleRoleMessage(roleTag, functionTag)
                        else -> message
                    }
                    val next = it.copy(
                        roleTag = roleTag,
                        functionTag = functionTag,
                        lastMessage = displayMessage,
                    )
                    if (displayMessage.isBlank()) {
                        next
                    } else {
                        appendEvent(next, displayMessage)
                    }
                }
            }

            0x90 -> {
                handle.bootstrapSystemParamsReceived = true
                val shouldPromoteFallbackSync =
                    shouldPromoteSystemParamSyncFallback(
                        initialSyncCompleted = handle.initialSyncCompleted,
                        initialSyncStarted = handle.initialSyncStarted,
                        legacyReadyPromoted = handle.legacyReadyPromoted,
                    )
                if (shouldPromoteFallbackSync) {
                    handle.initialSyncCompleted = true
                    stopInitialBootstrap(handle, clearSyncStarted = false)
                }
                val parsed = Ce32Protocol.parseSystemParams(payload)
                handle.systemParamPayload = payload.copyOf()
                updateSession(deviceId) {
                    appendEvent(
                        it.copy(
                        hostState = if (shouldPromoteFallbackSync) {
                            synchronizedHostState(it.hostState)
                        } else {
                            it.hostState
                        },
                        statusText = statusTextForState(
                            if (shouldPromoteFallbackSync) {
                                synchronizedHostState(it.hostState)
                            } else {
                                it.hostState
                            },
                        ),
                        syncText = when {
                            shouldPromoteFallbackSync -> "Sync: system params fallback"
                            it.syncText.isNotBlank() -> it.syncText
                            handle.initialSyncCompleted -> "Sync: system params received"
                            else -> ""
                        },
                        parsedSystemParams = parsed ?: it.parsedSystemParams,
                        previewSelection = parsed?.previewSelection ?: it.previewSelection,
                        swVersion = it.swVersion ?: parsed?.firmwareVersion?.toString(),
                        hwVersion = it.hwVersion ?: parsed?.hardwareVersion?.toString(),
                        systemParamHex = Ce32Protocol.bytesToHex(payload),
                        awaitingLiveSync = if (shouldPromoteFallbackSync) {
                            false
                        } else {
                            it.awaitingLiveSync
                        },
                        lastMessage = if (shouldPromoteFallbackSync) {
                            "System params updated; sync fallback applied"
                        } else if (handle.initialSyncCompleted) {
                            "System params updated after sync"
                        } else {
                            "System params updated"
                        },
                        ),
                        if (shouldPromoteFallbackSync) {
                            "System params updated; sync fallback applied"
                        } else if (handle.initialSyncCompleted) {
                            "System params updated after sync"
                        } else {
                            "System params updated"
                        },
                    )
                }
                requestDspParamsIfNeeded(handle, "system params bootstrap")
                previewPrimeDelayForInboundCommand(commandId)?.let { delayMs ->
                    tryQueuePreviewPrime(handle, initialDelayMs = delayMs)
                }
            }

            0x91 -> {
                val parsedDsp = Ce32Protocol.parseDspParams(payload)
                handle.dsp1ParamPayload = payload.copyOf()
                updateSession(deviceId) {
                    appendEvent(
                        it.copy(
                        parsedDsp1Params = parsedDsp ?: it.parsedDsp1Params,
                        dsp1ParamHex = Ce32Protocol.bytesToHex(payload),
                        lastMessage = "DSP1 params updated",
                        ),
                        "DSP1 params updated",
                    )
                }
                previewPrimeDelayForInboundCommand(commandId)?.let { delayMs ->
                    tryQueuePreviewPrime(handle, initialDelayMs = delayMs)
                }
            }

            0x92 -> {
                val parsedDsp = Ce32Protocol.parseDspParams(payload)
                handle.dsp2ParamPayload = payload.copyOf()
                updateSession(deviceId) {
                    appendEvent(
                        it.copy(
                        parsedDsp2Params = parsedDsp ?: it.parsedDsp2Params,
                        dsp2ParamHex = Ce32Protocol.bytesToHex(payload),
                        lastMessage = "DSP2 params updated",
                        ),
                        "DSP2 params updated",
                    )
                }
                previewPrimeDelayForInboundCommand(commandId)?.let { delayMs ->
                    tryQueuePreviewPrime(handle, initialDelayMs = delayMs)
                }
            }

            0x9D -> {
                val parsedCamera = Ce32Protocol.parseCameraParams(payload)
                updateSession(deviceId) {
                    appendEvent(
                        it.copy(
                        parsedCameraParams = parsedCamera ?: it.parsedCameraParams,
                        cameraParamHex = Ce32Protocol.bytesToHex(payload),
                        lastMessage = "Camera params updated",
                        ),
                        "Camera params updated",
                    )
                }
                requestSystemParamsIfNeeded(handle, "live-sync system params")
            }

            Ce32Protocol.CameraSnapshotCommand,
            Ce32Protocol.CameraPreviewCommand -> {
                val row = Ce32Protocol.parseCameraRow(commandId, payload) ?: return
                val targetBuffer = if (row.isPreview) {
                    handle.cameraPreviewBuffer
                } else {
                    handle.cameraSnapshotBuffer
                }
                val rowOffset = row.row * row.pixels
                row.bytes.copyInto(targetBuffer, destinationOffset = rowOffset)

                if (row.row == row.pixels - 1) {
                    if (row.isPreview) {
                        handle.cameraPreviewRequestPending = false
                        handle.cameraPreviewRequestStartedAtMs = 0L
                        handle.cameraPreviewFrameId += 1
                        updateSession(deviceId) {
                            it.copy(
                                cameraPreviewPixels = row.pixels,
                                cameraPreviewImage = targetBuffer.clone(),
                                cameraPreviewFrameId = handle.cameraPreviewFrameId,
                                cameraPreviewStreaming = handle.cameraPreviewStreamingEnabled,
                                lastMessage = "Camera preview frame updated",
                            )
                        }
                    } else {
                        handle.cameraSnapshotFrameId += 1
                        updateSession(deviceId) {
                            appendEvent(
                                it.copy(
                                cameraSnapshotPixels = row.pixels,
                                cameraSnapshotImage = targetBuffer.clone(),
                                cameraSnapshotFrameId = handle.cameraSnapshotFrameId,
                                lastMessage = "Snapshot updated",
                                ),
                                "Snapshot updated",
                            )
                        }
                    }
                }
            }

            0xAD -> {
                val preview = Ce32Protocol.parsePreviewPacket(payload) ?: return
                if (handle.previewFallbackPending) {
                    handle.previewFallbackPending = false
                    cancelPreviewStartupMonitor(handle)
                }
                val firstPreviewPacket = currentState(deviceId)?.previewPacketCount == 0
                if (firstPreviewPacket) {
                    Log.d(
                        "Ce32BleManager",
                        "preview packet device=$deviceId samples=${preview.samples.size} voltage=${preview.voltage} usedMb=${preview.usedSpaceMb}",
                    )
                }
                handle.previewBuffer.append(preview.samples)
                val nextPoints = handle.previewBuffer.snapshot(PreviewUiPointCapacity)
                updateSession(deviceId) {
                    val startingRecordingFromPreviewFallback =
                        it.hostState == BleHostSessionState.StartingRecording && handle.usesRecordingPreviewFallback
                    if (startingRecordingFromPreviewFallback) {
                        cancelRecordStartAckTimeout(handle)
                    }
                    val nextState = when {
                        startingRecordingFromPreviewFallback -> BleHostSessionState.Recording
                        it.hostState == BleHostSessionState.StartingRecording -> BleHostSessionState.StartingRecording
                        it.hostState == BleHostSessionState.Recording -> BleHostSessionState.Recording
                        it.hostState == BleHostSessionState.StoppingRecording -> BleHostSessionState.StoppingRecording
                        else -> BleHostSessionState.Previewing
                    }
                    val next = it.copy(
                        hostState = nextState,
                        statusText = statusTextForState(nextState),
                        previewPacketCount = it.previewPacketCount + 1,
                        previewPoints = nextPoints,
                        voltage = preview.voltage,
                        usedSpaceMb = preview.usedSpaceMb,
                        digitalFlags = preview.digitalFlags,
                        lastMessage = if (startingRecordingFromPreviewFallback) {
                            "Record start acknowledged via preview fallback"
                        } else {
                            "Preview stream active"
                        },
                    )
                    val eventSummary = when {
                        startingRecordingFromPreviewFallback -> "Record start acknowledged via preview fallback"
                        nextState == BleHostSessionState.Previewing &&
                            (it.hostState != BleHostSessionState.Previewing || it.previewPacketCount == 0) ->
                            "Preview stream active"
                        else -> null
                    }
                    if (eventSummary != null) {
                        appendEvent(next, eventSummary)
                    } else {
                        next
                    }
                }
            }

            0xAF -> {
                val packet = Ce32Protocol.parseRecTimePacket(payload) ?: return
                val startingRecording = currentState(deviceId)?.hostState == BleHostSessionState.StartingRecording
                val firstRecTimePacket = currentState(deviceId)?.recTimePacketCount == 0
                if (startingRecording || firstRecTimePacket) {
                    Log.d(
                        "Ce32BleManager",
                        "record telemetry device=$deviceId seconds=${packet.recordingSeconds} voltage=${packet.voltage} usedMb=${packet.usedSpaceMb}",
                    )
                }
                if (startingRecording) {
                    cancelRecordStartAckTimeout(handle)
                }
                updateSession(deviceId) {
                    val stoppingRecording = it.hostState == BleHostSessionState.StoppingRecording
                    val nextState = if (stoppingRecording) {
                        BleHostSessionState.StoppingRecording
                    } else {
                        BleHostSessionState.Recording
                    }
                    val next = it.copy(
                        hostState = nextState,
                        statusText = statusTextForState(nextState),
                        recTimePacketCount = it.recTimePacketCount + 1,
                        recordingSeconds = packet.recordingSeconds,
                        voltage = packet.voltage ?: it.voltage,
                        usedSpaceMb = packet.usedSpaceMb ?: it.usedSpaceMb,
                        lastMessage = if (startingRecording) "Record start acknowledged via rec-time stream" else "Recording telemetry active",
                    )
                    val eventSummary = when {
                        startingRecording -> "Record start acknowledged via rec-time stream"
                        it.recTimePacketCount == 0 -> "Recording telemetry active"
                        !stoppingRecording && it.hostState != BleHostSessionState.Recording -> "Recording telemetry active"
                        else -> null
                    }
                    if (eventSummary != null) {
                        appendEvent(next, eventSummary)
                    } else {
                        next
                    }
                }
            }

            0xAE -> {
                updateSession(deviceId) {
                    it.copy(
                        triggeredWaveformBlockCount = it.triggeredWaveformBlockCount + 1,
                        lastTriggeredWaveformBytes = payload.size,
                        lastMessage = "Triggered waveform block received",
                    )
                }
                appendTriggerWaveformBlock(handle, payload)
            }

            0xB0, 0xB1, 0xC0, 0xF0, 0xF1, 0xF2, 0xF3, 0xF4 -> {
                val summary = buildWindowsParityFrameSummary(commandId, payload)
                updateSession(deviceId) {
                    appendEvent(
                        it.copy(lastMessage = summary),
                        summary,
                    )
                }
            }

            0x51 -> {
                val values = Ce32Protocol.parseUnsignedShortList(payload)
                val updatedAtMs = System.currentTimeMillis()
                updateSession(deviceId) {
                    val nextSnapshot = it.impedanceSnapshot.copy(
                        magnitudeValues = values,
                        updatedAtMs = updatedAtMs,
                    )
                    appendEvent(
                        it.copy(
                            impedanceSnapshot = nextSnapshot,
                            lastMessage = "Impedance magnitude: ${values.take(4).joinToString()}${if (values.size > 4) "..." else ""}",
                        ),
                        "Impedance magnitude ready",
                    )
                }
            }

            0x52 -> {
                val packet = Ce32Protocol.parseUnsignedShortList(payload)
                if (packet.isEmpty()) {
                    return
                }
                val driveChannel = packet.first()
                val values = packet.drop(1)
                val updatedAtMs = System.currentTimeMillis()
                updateSession(deviceId) {
                    val nextMap = it.impedanceSnapshot.crosstalkByDriveChannel.toMutableMap().apply {
                        put(driveChannel, values)
                    }
                    val nextSnapshot = it.impedanceSnapshot.copy(
                        crosstalkByDriveChannel = nextMap.toMap(),
                        updatedAtMs = updatedAtMs,
                    )
                    appendEvent(
                        it.copy(
                            impedanceSnapshot = nextSnapshot,
                            lastMessage = "Crosstalk column ch$driveChannel ready",
                        ),
                        "Crosstalk column ch$driveChannel ready",
                    )
                }
            }

            0x53 -> {
                val values = Ce32Protocol.parseSignedShortList(payload)
                val updatedAtMs = System.currentTimeMillis()
                updateSession(deviceId) {
                    val nextSnapshot = it.impedanceSnapshot.copy(
                        phaseValues = values,
                        updatedAtMs = updatedAtMs,
                    )
                    appendEvent(
                        it.copy(
                            impedanceSnapshot = nextSnapshot,
                            lastMessage = "Impedance phase: ${values.take(4).joinToString()}${if (values.size > 4) "..." else ""}",
                        ),
                        "Impedance phase ready",
                    )
                }
            }

            0x94 -> {
                handle.pendingLogBlock?.complete(payload)
            }

            0x95 -> {
                val remaining = Ce32Protocol.parseDeleteAck(payload) ?: 0L
                handle.pendingDeleteAck?.complete(remaining)
                updateSession(deviceId) {
                    appendEvent(
                        it.copy(lastMessage = "Records remaining: $remaining"),
                        "Records remaining: $remaining",
                    )
                }
            }

            else -> {
                updateSession(deviceId) {
                    it.copy(lastMessage = "Command ${formatCommandId(commandId)} received")
                }
            }
        }
    }

    private fun buildWindowsParityFrameSummary(commandId: Int, payload: ByteArray): String {
        return when (commandId) {
            0xB0, 0xB1 -> "Interface frame ${formatCommandId(commandId)} ${summarizeShortPayload(payload)}"
            0xC0 -> "Control-port frame ${formatCommandId(commandId)} ${summarizeShortPayload(payload)}"
            else -> "Diagnostic frame ${formatCommandId(commandId)} received (${payload.size} bytes)"
        }
    }

    private fun summarizeShortPayload(payload: ByteArray): String {
        return payload.joinToString("-") { value ->
            (value.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0')
        }
    }

    private fun formatCommandId(commandId: Int): String =
        "0x${commandId.toString(16).uppercase().padStart(2, '0')}"

    private inner class SessionHandle(
        val id: String,
        var device: BluetoothDevice,
        val traceColorArgb: Int,
    ) {
        var gatt: BluetoothGatt? = null
        var dataService: BluetoothGattService? = null
        var txCharacteristic: BluetoothGattCharacteristic? = null
        var legacyTxCharacteristic: BluetoothGattCharacteristic? = null
        var rxCharacteristic: BluetoothGattCharacteristic? = null
        var swVersion: String? = null
        var hwVersion: String? = null
        var latestName: String = device.name.orEmpty()
        var latestRssi: Int? = null
        var mtu: Int = 23
        var mtuNegotiationPending: Boolean = false
        var mtuNegotiationFallbackJob: Job? = null
        var useLegacyWakePrefix: Boolean = false
        var disconnectRequestedByUser = false
        var expectedDisconnectReason: String = ""
        var suppressRtcWriteDuringResync = false
        var fastBootstrapRequested: Boolean = false
        var legacyReadyPromoted: Boolean = false
        var legacyHandshakeAckSeen: Boolean = false
        var legacyConfigBusyUntilMs: Long = 0L
        var notificationRxCount: Int = 0
        var legacyConfigBusyCount: Int = 0
        val writeMutex = Mutex()
        val previewBuffer = PreviewBuffer(PreviewBufferCapacity)
        val parser = Ce32FrameParser(
            onFrame = { commandId, payload ->
                handleFrame(id, commandId, payload)
            },
            onOutOfFrameBytes = { rawBytes ->
                handleOutOfFrameNotification(this, rawBytes)
            },
        )
        var pendingWrite: CompletableDeferred<Boolean>? = null
        var pendingRead: CompletableDeferred<ByteArray?>? = null
        var pendingReadUuid: UUID? = null
        var pendingDescriptorWrite: CompletableDeferred<Boolean>? = null
        var pendingLogBlock: CompletableDeferred<ByteArray>? = null
        var pendingDeleteAck: CompletableDeferred<Long>? = null
        var periodicPackedTimeJob: Job? = null
        var connectTimeoutJob: Job? = null
        var recordStartAckTimeoutJob: Job? = null
        var recordStopAckTimeoutJob: Job? = null
        var recordRefreshJob: Job? = null
        var previewStartupMonitorJob: Job? = null
        var reconnectJob: Job? = null
        var reconnectPending: Boolean = false
        var reconnectAttemptCount: Int = 0
        var manualConnectInProgress: Boolean = false
        var resumePreviewAfterAdditionalConnect: Boolean = false
        var resumePreviewAfterReconnect: Boolean = false
        var resumePackedTimeAfterReconnect: Boolean = false
        var resumeCameraPreviewAfterReconnect: Boolean = false
        var lastStopRecordingRequestAtMs: Long = 0L
        var lastPreviewPrimeAtMs: Long = 0L
        var previewPrimeGeneration: Int = 0
        var previewFallbackPending: Boolean = false
        var usesRecordingPreviewFallback: Boolean = false
        var cameraPreviewPollJob: Job? = null
        var cameraPreviewStreamingEnabled: Boolean = false
        var cameraPreviewRequestPending: Boolean = false
        var cameraPreviewRequestStartedAtMs: Long = 0L
        var initialBootstrapJob: Job? = null
        var initialSyncStarted: Boolean = false
        var initialSyncCompleted: Boolean = false
        var bootstrapSystemParamsReceived: Boolean = false
        var bootstrapDspReadRequested: Boolean = false
        var bootstrapReadyObserved: Boolean = false
        var bleLinkPacketCount: Long = 0L
        var bleLinkSequenceErrorCount: Int = 0
        var bleLinkMissingPacketCount: Long = 0L
        var bleLinkLastRssiRaw: Int = 0
        var bleLinkFirstPacketAtMs: Long = 0L
        var bleLinkLastSeq: Int = 0
        var bleLinkHasLastSeq: Boolean = false
        var bleLinkLastStatusAtMs: Long = 0L
        val liveSyncErrWindow: ArrayDeque<Int> = ArrayDeque()
        var liveSyncErrSumMs: Double = 0.0
        var liveSyncErrSumSqMs: Double = 0.0
        var systemParamPayload: ByteArray? = null
        var dsp1ParamPayload: ByteArray? = null
        var dsp2ParamPayload: ByteArray? = null
        var triggerWaveformCaptureFile: File? = null
        val triggerWaveformWriteMutex = Mutex()
        val cameraPreviewBuffer: ByteArray = ByteArray(Ce32Protocol.CameraPreviewPixels * Ce32Protocol.CameraPreviewPixels)
        val cameraSnapshotBuffer: ByteArray = ByteArray(Ce32Protocol.CameraSnapshotPixels * Ce32Protocol.CameraSnapshotPixels)
        var cameraPreviewFrameId: Int = 0
        var cameraSnapshotFrameId: Int = 0
    }

    private object BluetoothStatusCodesCompat {
        const val SUCCESS = 0
    }

    private data class BleLinkStatusUpdate(
        val stats: BleLinkStatsUiState,
        val summary: String,
        val emitEvent: Boolean,
    )

    private data class BleRoleInfo(
        val roleTag: String,
        val functionTag: String,
    )

    private data class SyncLogStamp(
        val seconds: Long,
        val subSeconds: Long,
    )

    private fun statusTextForState(state: BleHostSessionState): String {
        return when (state) {
            BleHostSessionState.Disconnected -> "Disconnected"
            BleHostSessionState.Connecting -> "Connecting..."
            BleHostSessionState.Connected -> "Connected"
            BleHostSessionState.Syncing -> "Syncing..."
            BleHostSessionState.Synced -> "Synced"
            BleHostSessionState.Previewing -> "Previewing"
            BleHostSessionState.StartingRecording -> "Starting recording..."
            BleHostSessionState.Recording -> "Recording"
            BleHostSessionState.StoppingRecording -> "Stopping recording..."
            BleHostSessionState.Reconnecting -> "Reconnecting..."
            BleHostSessionState.Disconnecting -> "Disconnecting..."
            BleHostSessionState.Error -> "Error"
        }
    }

    private fun formatRecordStartEventLabel(event: RecordStartEvent): String? {
        val year = event.year ?: return null
        val month = event.month ?: return null
        val day = event.day ?: return null
        val hour = event.hour ?: return null
        val minute = event.minute ?: return null
        val second = event.second ?: return null
        return String.format(
            Locale.US,
            "%04d-%02d-%02d %02d:%02d:%02d",
            year,
            month,
            day,
            hour,
            minute,
            second,
        )
    }

    private companion object {
        const val DiscoveryCachePrefsName = "ce32_ble_discovery_cache"
        const val DiscoveryCacheKey = "sessions_json"
        const val DiscoveryCacheMaxEntries = 16
        const val DiscoveryCacheMaxAgeMs = 6 * 60 * 60 * 1000L
        const val PreviewBufferCapacity = 32_768
        const val PreviewUiPointCapacity = 16_384
        const val MaxRecentEvents = 18
        const val BleLinkStatusIntervalMs = 500L
        const val CameraPreviewPollIntervalMs = 700L
        const val CameraPreviewFrameTimeoutMs = 2_000L
        const val PreviewFallbackDelayMs = 3_000L
        const val PreviewGroupResyncMaxWaitMs = 600L
        const val PreviewGroupResyncQuietWindowMs = 200L
        const val PreviewPrimeThrottleMs = 250L
        const val InitialBootstrapPollMs = 100L
        const val InitialHandshakeSettleMs = 0L
        const val InitialHandshakeRetryMs = 500L
        const val InitialHandshakeMaxAttempts = 11
        const val InitialBootstrapTimeoutMs = 18_500L
        const val InitialDeviceInfoInitialDelayMs = 400L
        const val InitialDeviceInfoRetryMs = 1_500L
        const val InitialLegacyHandshakeDelayMs = 1_200L
        const val InitialDeviceInfoMaxAttempts = 12
        const val LegacyConfigModeBusyBackoffMs = 1_500L
        const val RecordStartAckTimeoutMs = 1_500L
        const val RecordStopAckTimeoutMs = 4_000L
        const val MtuCallbackFallbackMs = 1_500L
        const val ConnectLinkTimeoutMs = 10_000L
        const val BulkConnectAdvanceTimeoutMs = 25000L
        const val ScanPauseConnectGattCooldownMs = 350L
        const val RetryConnectGattCooldownMs = 650L
        const val ReconnectConnectGattCooldownMs = 900L
        const val DisconnectFallbackMs = 500L
        const val ScanRestartStallMs = 8_000L
        const val RecordBusyGraceWindowMs = 2_500L
        const val PostStopRecordRefreshDelayMs = 600L
        const val ReconnectDelayMs = 1_500L
        const val ReconnectStabilizationTimeoutMs = 6_000L
        const val MaxReconnectAttempts = 3
        const val LiveSyncErrWindowMax = 50
        const val LiveSyncErrOutlierAbsMs = 120_000
        const val LiveSyncWrapMs = 3_600_000
        const val LiveSyncHalfWrapMs = LiveSyncWrapMs / 2
        const val SyncWrapSeconds = 3_600.0
        const val SyncHalfWrapSeconds = SyncWrapSeconds / 2.0
        val RetryableInitialConnectStatuses = setOf(8, 133, 257)
        val LegacyConfigModeBusyMessage: ByteArray = "<CONFIG MODE BUSY>".encodeToByteArray()
        val ClientCharacteristicConfigUuid: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
