package com.wild.android

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wild.android.ble.BleHostSessionState
import com.wild.android.ble.Ce32BleManager
import com.wild.android.ble.Ce64BleOtaPackage
import com.wild.android.ble.ControlScope
import com.wild.android.ble.DeviceSessionUiState
import com.wild.android.ble.GpioMode
import com.wild.android.ble.PreviewSelection
import com.wild.android.cloud.CloudFleetGatewayState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import java.io.ByteArrayOutputStream

data class WildUiState(
    val sessions: List<DeviceSessionUiState> = emptyList(),
    val activeSessionId: String? = null,
    val isScanning: Boolean = false,
    val statusBanner: String = "",
    val cloudFleet: CloudFleetGatewayState = CloudFleetGatewayState(),
    val fleetConnectActive: Boolean = false,
    val fleetConnectPendingCount: Int = 0,
    val controlScope: ControlScope = ControlScope.ActiveDevice,
    val selectedSessionIds: Set<String> = emptySet(),
    val markedSessionIds: Set<String> = emptySet(),
    val pendingConnectionIds: List<String> = emptyList(),
    val autoImpedanceRunning: Boolean = false,
    val autoImpedanceDeviceId: String? = null,
    val autoImpedanceDeviceName: String = "",
    val autoImpedanceIntervalMinutes: Int = 15,
    val autoImpedanceNextRunAtMs: Long? = null,
    val autoImpedanceStatusMessage: String = "",
    val recordArmEnabled: Boolean = false,
    val recordStartArmed: Boolean = false,
) {
    val activeSession: DeviceSessionUiState?
        get() = sessions.firstOrNull { it.id == activeSessionId }

    val connectedSessions: List<DeviceSessionUiState>
        get() = sessions.filter { it.isConnected }

    val connectingSessions: List<DeviceSessionUiState>
        get() = sessions.filter { it.hostState == BleHostSessionState.Connecting }

    val reconnectingSessions: List<DeviceSessionUiState>
        get() = sessions.filter { it.hostState == BleHostSessionState.Reconnecting }

    val selectedSessions: List<DeviceSessionUiState>
        get() {
            val sessionsById = sessions.associateBy { it.id }
            return selectedSessionIds.mapNotNull { id -> sessionsById[id] }
        }

    val selectedConnectedSessions: List<DeviceSessionUiState>
        get() = selectedSessions.filter { it.isConnected }

    val pendingConnectionSessions: List<DeviceSessionUiState>
        get() {
            val sessionsById = sessions.associateBy { it.id }
            return pendingConnectionIds.mapNotNull { id ->
                sessionsById[id]?.takeIf { !it.isConnected }
            }
        }
}

internal fun resolveControlTargetIds(state: WildUiState): List<String> {
    return when (state.controlScope) {
        ControlScope.ActiveDevice -> listOfNotNull(state.activeSessionId)
        ControlScope.SelectedDevices -> state.selectedConnectedSessions.map { it.id }
        ControlScope.AllConnected -> state.connectedSessions.map { it.id }
    }
}

internal fun resolveConnectedControlTargetIds(state: WildUiState): List<String> {
    val connectedIds = state.connectedSessions.mapTo(hashSetOf()) { it.id }
    return resolveControlTargetIds(state).filter { it in connectedIds }
}

internal fun resolvePendingConnectionTargetIds(state: WildUiState): List<String> {
    return state.pendingConnectionSessions.map { it.id }
}

internal fun resolveRecordLaunchRefreshTargetIds(state: WildUiState): List<String> {
    val scopedTargets = resolveConnectedControlTargetIds(state)
    if (scopedTargets.isNotEmpty()) {
        return scopedTargets
    }

    return listOfNotNull(state.activeSessionId).filter { activeId ->
        state.sessions.any { session -> session.id == activeId && session.isConnected }
    }
}

internal fun resolveControlLaunchReadTargetIds(state: WildUiState): List<String> {
    val scopedTargets = resolveConnectedControlTargetIds(state)
    if (scopedTargets.isNotEmpty()) {
        return scopedTargets
    }

    return listOfNotNull(state.activeSessionId).filter { activeId ->
        state.sessions.any { session -> session.id == activeId && session.isConnected }
    }
}

internal fun resolveLiveLaunchPrefetchTargetIds(state: WildUiState): List<String> {
    val scopedTargets = resolveConnectedControlTargetIds(state)
    if (scopedTargets.isNotEmpty()) {
        return scopedTargets
    }

    return listOfNotNull(state.activeSessionId).filter { activeId ->
        state.sessions.any { session -> session.id == activeId && session.isConnected }
    }
}

internal fun resolvePreviewRouteSessions(state: WildUiState): List<DeviceSessionUiState> {
    val scopedConnected = when (state.controlScope) {
        ControlScope.ActiveDevice -> listOfNotNull(state.activeSession).filter { it.isConnected }
        ControlScope.SelectedDevices -> state.selectedConnectedSessions
        ControlScope.AllConnected -> state.connectedSessions
    }
    if (scopedConnected.isNotEmpty()) {
        return sortPreviewRouteSessions(scopedConnected)
    }

    val activeConnected = listOfNotNull(state.activeSession).filter { it.isConnected }
    if (activeConnected.isNotEmpty()) {
        return sortPreviewRouteSessions(activeConnected)
    }

    val preservedScoped = when (state.controlScope) {
        ControlScope.ActiveDevice -> listOfNotNull(state.activeSession)
        ControlScope.SelectedDevices -> state.selectedSessions
        ControlScope.AllConnected -> state.connectedSessions
    }
    if (preservedScoped.isNotEmpty()) {
        return sortPreviewRouteSessions(preservedScoped)
    }

    val fallback = state.activeSession?.let(::listOf)
        ?: state.sessions.firstOrNull { it.id == state.activeSessionId }?.let(::listOf)
        ?: emptyList()
    return sortPreviewRouteSessions(fallback)
}

internal fun resolvePreviewRouteTargetIds(state: WildUiState): List<String> {
    return resolvePreviewRouteSessions(state)
        .filter { it.isConnected }
        .map { it.id }
}

private fun sortPreviewRouteSessions(
    sessions: List<DeviceSessionUiState>,
): List<DeviceSessionUiState> {
    // Preserve the incoming order so multi-device preview lanes stay stable
    // instead of reshuffling whenever the control focus changes.
    return sessions.distinctBy { it.id }
}

internal fun resolveScopeAfterSelectionChange(
    previousScope: ControlScope,
    sessions: List<DeviceSessionUiState>,
    selectedIds: Set<String>,
): ControlScope {
    return when {
        previousScope == ControlScope.SelectedDevices &&
            sessions.none { session -> session.isConnected && session.id in selectedIds } ->
            ControlScope.ActiveDevice
        else -> previousScope
    }
}

internal fun mergeSelectedIdsForConnectBatch(
    state: WildUiState,
    batchIds: List<String>,
): LinkedHashSet<String> {
    return linkedSetOf<String>().also { selected ->
        selected += state.selectedSessions.map { it.id }
        selected += batchIds
    }
}

internal fun resolveQueueableMarkedIds(state: WildUiState): List<String> {
    val sessionsById = state.sessions.associateBy { it.id }
    return state.markedSessionIds.mapNotNull { id ->
        sessionsById[id]?.takeIf { session ->
            !session.isConnected && !session.isLinkingLike
        }?.id
    }
}

internal fun removeIdsPreservingOrder(
    source: Set<String>,
    idsToRemove: Iterable<String>,
): LinkedHashSet<String> {
    val removalSet = idsToRemove.toHashSet()
    return source.filterNot { id -> id in removalSet }.toCollection(linkedSetOf())
}

internal enum class DeviceRosterPane {
    Connected,
    Linking,
    Verified,
    Nearby,
}

internal data class DeviceRosterBuckets(
    val connected: List<DeviceSessionUiState>,
    val linking: List<DeviceSessionUiState>,
    val verified: List<DeviceSessionUiState>,
    val nearby: List<DeviceSessionUiState>,
)

internal fun buildDeviceRosterBuckets(state: WildUiState): DeviceRosterBuckets {
    val connected = mutableListOf<DeviceSessionUiState>()
    val linking = mutableListOf<DeviceSessionUiState>()
    val verified = mutableListOf<DeviceSessionUiState>()
    val nearby = mutableListOf<DeviceSessionUiState>()

    state.sessions.forEach { session ->
        when {
            session.isConnected -> connected += session
            session.isLinkingLike -> linking += session
            session.bulkConnectEligible -> verified += session
            else -> nearby += session
        }
    }

    return DeviceRosterBuckets(
        connected = connected,
        linking = linking,
        verified = verified,
        nearby = nearby,
    )
}

internal fun normalizeDeviceRosterPane(
    requestedPane: DeviceRosterPane,
    buckets: DeviceRosterBuckets,
): DeviceRosterPane {
    val requestedHasSessions = when (requestedPane) {
        DeviceRosterPane.Connected -> buckets.connected.isNotEmpty()
        DeviceRosterPane.Linking -> buckets.linking.isNotEmpty()
        DeviceRosterPane.Verified -> buckets.verified.isNotEmpty()
        DeviceRosterPane.Nearby -> buckets.nearby.isNotEmpty()
    }
    if (requestedHasSessions) {
        return requestedPane
    }

    return when {
        buckets.connected.isNotEmpty() -> DeviceRosterPane.Connected
        buckets.linking.isNotEmpty() -> DeviceRosterPane.Linking
        buckets.verified.isNotEmpty() -> DeviceRosterPane.Verified
        else -> DeviceRosterPane.Nearby
    }
}

private data class ShellState(
    val activeSessionId: String?,
    val isScanning: Boolean,
    val statusBanner: String,
    val fleetConnectActive: Boolean,
    val fleetConnectPendingCount: Int,
    val cloudFleet: CloudFleetGatewayState,
)

private data class AutoImpedanceState(
    val running: Boolean = false,
    val deviceId: String? = null,
    val deviceName: String = "",
    val intervalMinutes: Int = 15,
    val nextRunAtMs: Long? = null,
    val statusMessage: String = "",
)

private data class RecordingArmState(
    val armEnabled: Boolean = false,
    val startArmed: Boolean = false,
)

private data class ControlTargetState(
    val scope: ControlScope,
    val selectedIds: Set<String>,
    val pendingIds: List<String>,
)

class WildViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val bleManager = (application as WildApplication).bleManager
    private val cloudFleetGateway = (application as WildApplication).cloudFleetGateway
    private val controlScope = MutableStateFlow(ControlScope.ActiveDevice)
    private val selectedSessionIds = MutableStateFlow<Set<String>>(emptySet())
    private val pendingConnectionIds = MutableStateFlow<List<String>>(emptyList())
    private val autoImpedanceState = MutableStateFlow(AutoImpedanceState())
    private val recordingArmState = MutableStateFlow(RecordingArmState())
    private var autoImpedanceJob: Job? = null
    private var autoImpedanceGeneration: Long = 0

    private val controlTargetState = combine(controlScope, selectedSessionIds, pendingConnectionIds) { scope, selectedIds, pendingIds ->
        ControlTargetState(
            scope = scope,
            selectedIds = selectedIds,
            pendingIds = pendingIds,
        )
    }

    private val fleetConnectState = combine(bleManager.fleetConnectActive, bleManager.fleetConnectPendingCount) { active, pendingCount ->
        active to pendingCount
    }

    private val shellState = combine(
        bleManager.activeSessionId,
        bleManager.isScanning,
        bleManager.statusBanner,
        fleetConnectState,
        cloudFleetGateway.state,
    ) { activeId, isScanning, banner, fleetConnectState, cloudFleet ->
        val (fleetConnectActive, fleetConnectPendingCount) = fleetConnectState
        ShellState(
            activeSessionId = activeId,
            isScanning = isScanning,
            statusBanner = banner,
            fleetConnectActive = fleetConnectActive,
            fleetConnectPendingCount = fleetConnectPendingCount,
            cloudFleet = cloudFleet,
        )
    }

    val uiState: StateFlow<WildUiState> = combine(
        bleManager.sessions,
        shellState,
        controlTargetState,
        autoImpedanceState,
        recordingArmState,
    ) { sessionsMap, shellState, controlTarget, autoImpedanceState, recordingArmState ->
        val sessions = sessionsMap.values
            .sortedWith(
                compareByDescending<DeviceSessionUiState> { it.isConnected }
                    .thenByDescending { it.verifiedTransport }
                    .thenByDescending { it.bulkConnectEligible }
                    .thenByDescending { it.isActive }
                    .thenBy { it.name.lowercase() }
            )
        val sanitizedSelectedIds = controlTarget.selectedIds.filterTo(linkedSetOf()) { id ->
            sessionsMap[id] != null
        }
        val sanitizedPendingIds = controlTarget.pendingIds.filter { id ->
            sessionsMap[id]?.isConnected == false
        }.distinct()

        WildUiState(
            sessions = sessions,
            activeSessionId = shellState.activeSessionId,
            isScanning = shellState.isScanning,
            statusBanner = shellState.statusBanner,
            cloudFleet = shellState.cloudFleet,
            fleetConnectActive = shellState.fleetConnectActive,
            fleetConnectPendingCount = shellState.fleetConnectPendingCount,
            controlScope = controlTarget.scope,
            selectedSessionIds = sanitizedSelectedIds,
            pendingConnectionIds = sanitizedPendingIds,
            autoImpedanceRunning = autoImpedanceState.running,
            autoImpedanceDeviceId = autoImpedanceState.deviceId,
            autoImpedanceDeviceName = autoImpedanceState.deviceName,
            autoImpedanceIntervalMinutes = autoImpedanceState.intervalMinutes,
            autoImpedanceNextRunAtMs = autoImpedanceState.nextRunAtMs,
            autoImpedanceStatusMessage = autoImpedanceState.statusMessage,
            recordArmEnabled = recordingArmState.armEnabled,
            recordStartArmed = recordingArmState.startArmed,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = WildUiState(),
    )

    fun clearBanner() {
        bleManager.clearBanner()
    }

    fun signInToCloudFleet(email: String, password: String) {
        cloudFleetGateway.signIn(email, password)
    }

    fun createCloudFleetAccount(email: String, password: String) {
        cloudFleetGateway.createSharedAccount(email, password)
    }

    fun signOutOfCloudFleet() {
        cloudFleetGateway.signOutOfSharedFleet()
    }

    fun setCloudFleetViewVisible(visible: Boolean) {
        cloudFleetGateway.setRemoteFleetViewVisible(visible)
    }

    fun setRecordArmEnabled(enabled: Boolean) {
        recordingArmState.value = if (enabled) {
            recordingArmState.value.copy(armEnabled = true)
        } else {
            RecordingArmState()
        }
    }

    fun cancelArmedRecordingStart() {
        recordingArmState.value = recordingArmState.value.copy(startArmed = false)
    }

    fun setControlScope(scope: ControlScope) {
        controlScope.value = scope
    }

    fun toggleSessionSelection(deviceId: String) {
        val session = uiState.value.sessions.firstOrNull { it.id == deviceId } ?: return
        val updatedSelection = linkedSetOf<String>().also { selected ->
            selected += selectedSessionIds.value
            if (deviceId in selected) {
                selected.remove(deviceId)
            } else if (session.isConnected) {
                selected.add(deviceId)
            }
        }
        if (updatedSelection == selectedSessionIds.value) {
            return
        }
        selectedSessionIds.value = updatedSelection
        controlScope.value = resolveScopeAfterSelectionChange(
            previousScope = controlScope.value,
            sessions = uiState.value.sessions,
            selectedIds = updatedSelection,
        )
    }

    fun clearSelectedSessions() {
        selectedSessionIds.value = emptySet()
        if (controlScope.value == ControlScope.SelectedDevices) {
            controlScope.value = ControlScope.ActiveDevice
        }
    }

    fun selectAllConnectedSessions() {
        val connectedIds = uiState.value.connectedSessions.mapTo(linkedSetOf()) { it.id }
        selectedSessionIds.value = connectedIds
        controlScope.value = ControlScope.SelectedDevices
    }

    fun startScan() {
        bleManager.startScan()
    }

    fun stopScan() {
        bleManager.stopScan()
    }

    fun clearRssiHistory() {
        bleManager.clearRssiHistory()
    }

    fun clearStaleDevices() {
        bleManager.clearStaleDiscoverySessions()
    }

    fun shutdownForAppExit() {
        recordingArmState.value = RecordingArmState()
        bleManager.shutdownForAppExit()
    }

    fun runDebugCommand(command: String) {
        if ((getApplication<Application>().applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0) {
            return
        }

        val normalized = command.trim().lowercase(Locale.US)
        Log.d("WildViewModel", "debug command requested: $normalized")
        viewModelScope.launch {
            when (normalized) {
                "connect_first_eligible" -> debugConnectFirstEligible()
                "connect_preview_first_eligible" -> debugConnectPreviewFirstEligible()
                "connect_preview_all_eligible" -> debugConnectPreviewAllEligible()
                "start_preview_first_connected" -> debugStartPreviewFirstConnected()
                "disconnect_all" -> disconnectAllConnected()
                "stop_preview" -> stopPreview()
                else -> Log.w("WildViewModel", "unknown debug command: $normalized")
            }
        }
    }

    fun connect(deviceId: String) {
        Log.d("WildViewModel", "connect requested for $deviceId")
        pendingConnectionIds.value = pendingConnectionIds.value.filterNot { it == deviceId }
        viewModelScope.launch {
            bleManager.connect(deviceId)
        }
    }

    fun connectVisibleSessions(deviceIds: List<String>) {
        val state = uiState.value
        val requestedIds = deviceIds.toHashSet()
        val connectable = state.sessions.filter { session ->
            session.id in requestedIds &&
                !session.isConnected &&
                session.bulkConnectEligible
        }
        if (connectable.isEmpty()) {
            return
        }
        val firstDisconnected = connectable.firstOrNull()?.id
        val connectableIds = connectable.mapTo(hashSetOf()) { it.id }
        selectedSessionIds.value = mergeSelectedIdsForConnectBatch(state, connectable.map { it.id })
        controlScope.value = ControlScope.SelectedDevices
        pendingConnectionIds.value = pendingConnectionIds.value.filterNot { it in connectableIds }
        viewModelScope.launch {
            bleManager.connectSeries(
                deviceIds = connectable.map { it.id },
                makeFirstActive = state.activeSession?.isConnected != true,
            )
        }
        if ((state.activeSession?.isConnected != true) && firstDisconnected != null) {
            bleManager.setActiveSession(firstDisconnected)
        }
    }

    fun togglePendingConnection(deviceId: String) {
        val queued = pendingConnectionIds.value
        pendingConnectionIds.value = if (deviceId in queued) {
            queued.filterNot { it == deviceId }
        } else {
            queued + deviceId
        }
    }

    fun clearPendingConnections() {
        pendingConnectionIds.value = emptyList()
    }

    fun queuePendingConnections(deviceIds: List<String>) {
        if (deviceIds.isEmpty()) {
            return
        }

        val sessionsById = uiState.value.sessions.associateBy { it.id }
        val mergedQueue = linkedSetOf<String>()
        pendingConnectionIds.value.forEach { queuedId ->
            val session = sessionsById[queuedId]
            if (session != null && !session.isConnected && !session.isLinkingLike) {
                mergedQueue += queuedId
            }
        }
        deviceIds.forEach { deviceId ->
            val session = sessionsById[deviceId]
            if (session != null && !session.isConnected && !session.isLinkingLike) {
                mergedQueue += deviceId
            }
        }
        pendingConnectionIds.value = mergedQueue.toList()
    }

    fun cancelPendingFleetConnect() {
        bleManager.cancelPendingFleetConnect()
    }

    private suspend fun debugConnectFirstEligible() {
        startScan()
        val session = waitForFirstEligibleSession(DebugScanWaitMs)
        if (session == null) {
            logDebugSessionRoster("debug connect")
            Log.w("WildViewModel", "debug connect: no eligible device found within ${DebugScanWaitMs}ms")
            return
        }

        Log.d("WildViewModel", "debug connect: targeting ${session.id} (${session.name})")
        bleManager.setActiveSession(session.id)
        bleManager.connect(session.id)
    }

    private suspend fun debugConnectPreviewFirstEligible() {
        debugConnectFirstEligible()
        val previewReady = waitForPreviewReadySession(DebugConnectWaitMs)
        if (previewReady == null) {
            Log.w("WildViewModel", "debug connect+preview: no preview-ready session within ${DebugConnectWaitMs}ms")
            return
        }

        if (previewReady.hostState == BleHostSessionState.Syncing || previewReady.hostState == BleHostSessionState.Synced) {
            Log.d("WildViewModel", "debug connect+preview: starting preview for ${previewReady.id} (${previewReady.name})")
            bleManager.startPreview(listOf(previewReady.id))
        } else {
            Log.d("WildViewModel", "debug connect+preview: session already live for ${previewReady.id} (${previewReady.name}) state=${previewReady.hostState}")
        }
    }

    private suspend fun debugStartPreviewFirstConnected() {
        val previewReady = waitForPreviewReadySession(DebugConnectWaitMs)
        if (previewReady == null) {
            Log.w("WildViewModel", "debug preview: no preview-ready session available within ${DebugConnectWaitMs}ms")
            return
        }

        if (previewReady.hostState == BleHostSessionState.Syncing || previewReady.hostState == BleHostSessionState.Synced) {
            Log.d("WildViewModel", "debug preview: starting preview for ${previewReady.id} (${previewReady.name})")
            bleManager.startPreview(listOf(previewReady.id))
        } else {
            Log.d("WildViewModel", "debug preview: session already live for ${previewReady.id} (${previewReady.name}) state=${previewReady.hostState}")
        }
    }

    private suspend fun debugConnectPreviewAllEligible() {
        startScan()
        val firstEligible = waitForFirstEligibleSession(DebugScanWaitMs)
        if (firstEligible == null) {
            logDebugSessionRoster("debug connect-all+preview")
            Log.w("WildViewModel", "debug connect-all+preview: no eligible devices found within ${DebugScanWaitMs}ms")
            return
        }

        val eligibleIds = uiState.value.sessions
            .filter { session ->
                !session.isConnected &&
                    !session.isLinkingLike &&
                    session.bulkConnectEligible
            }
            .map { it.id }
            .distinct()
        if (eligibleIds.isEmpty()) {
            Log.w("WildViewModel", "debug connect-all+preview: eligible device list became empty")
            return
        }

        Log.d("WildViewModel", "debug connect-all+preview: queueing ${eligibleIds.size} devices")
        bleManager.setActiveSession(eligibleIds.first())
        bleManager.connectSeries(eligibleIds, makeFirstActive = true)

        val previewReady = waitForPreviewReadySession(DebugConnectWaitMs)
        if (previewReady == null) {
            Log.w("WildViewModel", "debug connect-all+preview: no preview-ready session within ${DebugConnectWaitMs}ms")
            return
        }

        val previewStartIds = uiState.value.connectedSessions
            .filter { session ->
                session.hostState == BleHostSessionState.Syncing || session.hostState == BleHostSessionState.Synced
            }
            .map { it.id }
        if (previewStartIds.isNotEmpty()) {
            Log.d("WildViewModel", "debug connect-all+preview: starting preview for ${previewStartIds.size} preview-ready device(s)")
            bleManager.startPreview(previewStartIds)
        } else {
            Log.d("WildViewModel", "debug connect-all+preview: scoped session already live in state=${previewReady.hostState}")
        }
    }

    private fun logDebugSessionRoster(label: String) {
        val sessions = uiState.value.sessions
        if (sessions.isEmpty()) {
            Log.d("WildViewModel", "$label roster: no discovered sessions")
            return
        }

        sessions.forEachIndexed { index, session ->
            Log.d(
                "WildViewModel",
                String.format(
                    Locale.US,
                    "%s roster[%d]: name=%s state=%s rssi=%s prefix=%s adv=%s verified=%s eligible=%s",
                    label,
                    index,
                    session.name,
                    session.hostState,
                    session.rssi?.toString() ?: "--",
                    session.namePrefixMatch,
                    session.advertisedServiceMatch,
                    session.verifiedTransport,
                    session.bulkConnectEligible,
                ),
            )
        }
    }

    private suspend fun waitForFirstEligibleSession(timeoutMs: Long): DeviceSessionUiState? {
        val deadlineMs = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadlineMs) {
            val session = uiState.value.sessions.firstOrNull { candidate ->
                !candidate.isConnected &&
                    !candidate.isLinkingLike &&
                    candidate.bulkConnectEligible
            }
            if (session != null) {
                return session
            }
            delay(250)
        }
        return null
    }

    private suspend fun waitForConnectedSession(timeoutMs: Long): DeviceSessionUiState? {
        val deadlineMs = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadlineMs) {
            val state = uiState.value
            val activeConnected = state.activeSession?.takeIf { it.isConnected }
            if (activeConnected != null) {
                return activeConnected
            }

            val anyConnected = state.connectedSessions.firstOrNull()
            if (anyConnected != null) {
                return anyConnected
            }
            delay(250)
        }
        return null
    }

    private suspend fun waitForPreviewReadySession(timeoutMs: Long): DeviceSessionUiState? {
        val deadlineMs = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadlineMs) {
            val state = uiState.value
            val activeReady = state.activeSession?.takeIf(::canStartPreviewForDebug)
            if (activeReady != null) {
                return activeReady
            }

            val anyReady = state.connectedSessions.firstOrNull(::canStartPreviewForDebug)
            if (anyReady != null) {
                return anyReady
            }
            delay(250)
        }
        return null
    }

    private fun canStartPreviewForDebug(session: DeviceSessionUiState): Boolean {
        return session.hostState == BleHostSessionState.Syncing ||
            session.hostState == BleHostSessionState.Synced ||
            session.hostState == BleHostSessionState.Previewing ||
            session.hostState == BleHostSessionState.StartingRecording ||
            session.hostState == BleHostSessionState.Recording ||
            session.hostState == BleHostSessionState.StoppingRecording
    }

    fun connectQueuedCandidates() {
        val state = uiState.value
        val queuedIds = resolvePendingConnectionTargetIds(state)
        val firstQueued = queuedIds.firstOrNull() ?: return
        selectedSessionIds.value = queuedIds.toCollection(linkedSetOf())
        controlScope.value = ControlScope.SelectedDevices
        pendingConnectionIds.value = emptyList()
        viewModelScope.launch {
            bleManager.connectSeries(
                deviceIds = queuedIds,
                makeFirstActive = state.activeSession?.isConnected != true,
            )
        }
        if (state.activeSession?.isConnected != true) {
            bleManager.setActiveSession(firstQueued)
        }
    }

    fun disconnect(deviceId: String) {
        bleManager.disconnect(deviceId)
    }

    fun disconnectAllConnected() {
        pendingConnectionIds.value = emptyList()
        bleManager.disconnectAll()
    }

    fun setActiveSession(deviceId: String) {
        bleManager.setActiveSession(deviceId)
    }

    fun focusSession(deviceId: String) {
        controlScope.value = ControlScope.ActiveDevice
        bleManager.setActiveSession(deviceId)
    }

    fun startPreview() {
        startPreviewForTargets(resolveControlTargetIds(uiState.value))
    }

    fun startPreviewForTargets(deviceIds: List<String>) {
        viewModelScope.launch {
            bleManager.startPreview(deviceIds)
        }
    }

    fun startPreviewConnected() {
        startPreviewForTargets(uiState.value.connectedSessions.map { it.id })
    }

    fun stopPreview() {
        stopPreviewForTargets(resolveControlTargetIds(uiState.value))
    }

    fun stopPreviewForTargets(deviceIds: List<String>) {
        viewModelScope.launch {
            bleManager.stopPreview(deviceIds)
        }
    }

    fun stopPreviewConnected() {
        stopPreviewForTargets(uiState.value.connectedSessions.map { it.id })
    }

    fun startRecordingConnected() {
        startRecordingForTargets(uiState.value.connectedSessions.map { it.id })
    }

    fun stopRecordingConnected() {
        stopRecordingForTargets(uiState.value.connectedSessions.map { it.id })
    }

    fun startPreviewForDevice(deviceId: String) {
        viewModelScope.launch {
            bleManager.startPreview(listOf(deviceId))
        }
    }

    fun stopPreviewForDevice(deviceId: String) {
        viewModelScope.launch {
            bleManager.stopPreview(listOf(deviceId))
        }
    }

    fun startRecording() {
        startRecordingForTargets(resolveControlTargetIds(uiState.value))
    }

    fun startRecordingForTargets(deviceIds: List<String>) {
        if (recordingArmState.value.armEnabled) {
            if (deviceIds.isEmpty()) {
                viewModelScope.launch {
                    bleManager.startRecording(deviceIds)
                }
                return
            }

            if (recordingArmState.value.startArmed) {
                recordingArmState.value = recordingArmState.value.copy(startArmed = false)
                viewModelScope.launch {
                    bleManager.startRecording(deviceIds)
                }
            } else {
                recordingArmState.value = recordingArmState.value.copy(startArmed = true)
            }
            return
        }

        viewModelScope.launch {
            bleManager.startRecording(deviceIds)
        }
    }

    fun stopRecording() {
        stopRecordingForTargets(resolveControlTargetIds(uiState.value))
    }

    fun stopRecordingForTargets(deviceIds: List<String>) {
        viewModelScope.launch {
            bleManager.stopRecording(deviceIds)
        }
    }

    fun startRecordingForDevice(deviceId: String) {
        viewModelScope.launch {
            bleManager.startRecording(listOf(deviceId))
        }
    }

    fun stopRecordingForDevice(deviceId: String) {
        viewModelScope.launch {
            bleManager.stopRecording(listOf(deviceId))
        }
    }

    fun forceStopRecordingForDevice(deviceId: String) {
        viewModelScope.launch {
            bleManager.forceStopRecording(listOf(deviceId))
        }
    }

    fun setPreviewSelection(selection: PreviewSelection) {
        viewModelScope.launch {
            bleManager.setPreviewSelection(resolveControlTargetIds(), selection)
        }
    }

    fun setPreviewSelectionForDevice(deviceId: String, selection: PreviewSelection) {
        viewModelScope.launch {
            bleManager.setPreviewSelection(listOf(deviceId), selection)
        }
    }

    fun requestResync(suppressRtcWrite: Boolean = false) {
        viewModelScope.launch {
            bleManager.requestResync(resolveControlTargetIds(), suppressRtcWrite)
        }
    }

    fun requestResyncForDevice(deviceId: String, suppressRtcWrite: Boolean = false) {
        viewModelScope.launch {
            bleManager.requestResync(listOf(deviceId), suppressRtcWrite)
        }
    }

    fun requestSystemParams() {
        viewModelScope.launch {
            bleManager.requestSystemParams(resolveControlTargetIds())
        }
    }

    fun requestSystemParamsForDevice(deviceId: String) {
        viewModelScope.launch {
            bleManager.requestSystemParams(listOf(deviceId))
        }
    }

    fun requestDspParams() {
        viewModelScope.launch {
            bleManager.requestDspParams(resolveControlTargetIds())
        }
    }

    fun requestDspParamsForDevice(deviceId: String) {
        viewModelScope.launch {
            bleManager.requestDspParams(listOf(deviceId))
        }
    }

    fun requestAllParams() {
        viewModelScope.launch {
            bleManager.requestAllParams(resolveControlTargetIds())
        }
    }

    fun requestAllParamsForDevice(deviceId: String) {
        viewModelScope.launch {
            bleManager.requestAllParams(listOf(deviceId))
        }
    }

    fun setStreamRates(ephysRate: Int, cameraEnabled: Boolean, adcEnabled: Boolean) {
        viewModelScope.launch {
            bleManager.setStreamRates(
                deviceIds = resolveControlTargetIds(),
                ephysRate = ephysRate,
                cameraEnabled = cameraEnabled,
                adcEnabled = adcEnabled,
            )
        }
    }

    fun setStreamRatesForDevice(deviceId: String, ephysRate: Int, cameraEnabled: Boolean, adcEnabled: Boolean) {
        viewModelScope.launch {
            bleManager.setStreamRates(
                deviceIds = listOf(deviceId),
                ephysRate = ephysRate,
                cameraEnabled = cameraEnabled,
                adcEnabled = adcEnabled,
            )
        }
    }

    fun setQuickCustomFs(ephysRate: Int) {
        viewModelScope.launch {
            bleManager.setQuickCustomFs(
                deviceIds = resolveControlTargetIds(),
                ephysRate = ephysRate,
            )
        }
    }

    fun setQuickCustomFsForDevice(deviceId: String, ephysRate: Int) {
        viewModelScope.launch {
            bleManager.setQuickCustomFs(
                deviceIds = listOf(deviceId),
                ephysRate = ephysRate,
            )
        }
    }

    fun applyAcquisitionSystemProfile(
        vbattThresholdRaw: Int,
        audioRatio: Int,
        cameraRatio: Int,
    ) {
        val targetIds = resolveConnectedControlTargetIds(uiState.value)
        if (targetIds.isEmpty()) {
            return
        }
        viewModelScope.launch {
            bleManager.applyAcquisitionSystemProfile(
                deviceIds = targetIds,
                vbattThresholdRaw = vbattThresholdRaw,
                audioRatio = audioRatio,
                cameraRatio = cameraRatio,
            )
        }
    }

    fun applyAcquisitionSystemProfileForDevice(
        deviceId: String,
        vbattThresholdRaw: Int,
        audioRatio: Int,
        cameraRatio: Int,
    ) {
        viewModelScope.launch {
            bleManager.applyAcquisitionSystemProfile(
                deviceId = deviceId,
                vbattThresholdRaw = vbattThresholdRaw,
                audioRatio = audioRatio,
                cameraRatio = cameraRatio,
            )
        }
    }

    fun setLed(enabled: Boolean) {
        viewModelScope.launch {
            bleManager.setLed(resolveControlTargetIds(), enabled)
        }
    }

    fun setGpio0(mode: GpioMode) {
        viewModelScope.launch {
            bleManager.setGpio0(resolveControlTargetIds(), mode)
        }
    }

    fun setGpio1(mode: GpioMode) {
        viewModelScope.launch {
            bleManager.setGpio1(resolveControlTargetIds(), mode)
        }
    }

    fun setTriggerWaveform(enabled: Boolean) {
        viewModelScope.launch {
            bleManager.setTriggerWaveform(resolveControlTargetIds(), enabled)
        }
    }

    fun setTriggerWaveformForDevice(deviceId: String, enabled: Boolean) {
        viewModelScope.launch {
            bleManager.setTriggerWaveform(listOf(deviceId), enabled)
        }
    }

    fun requestSnapshot(preview: Boolean = false) {
        val activeId = uiState.value.activeSessionId ?: return
        viewModelScope.launch {
            bleManager.requestSnapshot(activeId, preview)
        }
    }

    fun setCameraPreviewStreaming(enabled: Boolean) {
        val activeId = uiState.value.activeSessionId ?: return
        viewModelScope.launch {
            bleManager.setCameraPreviewStreaming(activeId, enabled)
        }
    }

    fun setCameraPreviewStreamingForDevice(deviceId: String, enabled: Boolean) {
        viewModelScope.launch {
            bleManager.setCameraPreviewStreaming(deviceId, enabled)
        }
    }

    fun requestCameraParams() {
        val targetIds = resolveConnectedControlTargetIds(uiState.value)
        if (targetIds.isEmpty()) {
            return
        }
        viewModelScope.launch {
            bleManager.requestCameraParams(targetIds)
        }
    }

    fun requestCameraParamsForDevice(deviceId: String) {
        viewModelScope.launch {
            bleManager.requestCameraParams(deviceId)
        }
    }

    fun requestPreviewFrameForDevice(deviceId: String) {
        requestSnapshotForDevice(deviceId, preview = true)
    }

    fun requestSnapshotForDevice(deviceId: String, preview: Boolean = false) {
        viewModelScope.launch {
            bleManager.requestSnapshot(deviceId, preview)
        }
    }

    fun setCameraParams(reg0: Int, reg1: Int) {
        val targetIds = resolveConnectedControlTargetIds(uiState.value)
        if (targetIds.isEmpty()) {
            return
        }
        viewModelScope.launch {
            bleManager.setCameraParams(targetIds, reg0, reg1)
        }
    }

    fun setCameraParamsForDevice(deviceId: String, reg0: Int, reg1: Int) {
        viewModelScope.launch {
            bleManager.setCameraParams(deviceId, reg0, reg1)
        }
    }

    fun setStimEnabled(enabled: Boolean) {
        val targetIds = resolveConnectedControlTargetIds(uiState.value)
        if (targetIds.isEmpty()) {
            return
        }
        viewModelScope.launch {
            bleManager.setStimEnabled(targetIds, enabled)
        }
    }

    fun setTriggerGain(channelId: Int, gain: Float) {
        val targetIds = resolveConnectedControlTargetIds(uiState.value)
        if (targetIds.isEmpty()) {
            return
        }
        viewModelScope.launch {
            bleManager.setTriggerGain(targetIds, channelId, gain)
        }
    }

    fun setStimIntensity(channelId: Int, intensityPercent: Float) {
        val targetIds = resolveConnectedControlTargetIds(uiState.value)
        if (targetIds.isEmpty()) {
            return
        }
        viewModelScope.launch {
            bleManager.setStimIntensity(targetIds, channelId, intensityPercent)
        }
    }

    fun setTriggerThreshold(channelId: Int, threshold: Float) {
        val targetIds = resolveConnectedControlTargetIds(uiState.value)
        if (targetIds.isEmpty()) {
            return
        }
        viewModelScope.launch {
            bleManager.setTriggerThreshold(targetIds, channelId, threshold)
        }
    }

    fun forceTrigger(index: Int) {
        val targetIds = resolveConnectedControlTargetIds(uiState.value)
        if (targetIds.isEmpty()) {
            return
        }
        viewModelScope.launch {
            bleManager.forceTrigger(targetIds, index)
        }
    }

    fun setStimParams(
        channelId: Int,
        delayUnits: Float,
        randomDelayUnits: Float,
        durationUnits: Float,
        intervalUnits: Float,
        cycles: Int,
    ) {
        val targetIds = resolveConnectedControlTargetIds(uiState.value)
        if (targetIds.isEmpty()) {
            return
        }
        viewModelScope.launch {
            bleManager.setStimParams(
                deviceIds = targetIds,
                channelId = channelId,
                delayUnits = delayUnits,
                randomDelayUnits = randomDelayUnits,
                durationUnits = durationUnits,
                intervalUnits = intervalUnits,
                cycles = cycles,
            )
        }
    }

    fun setDspLiveParams(
        dspIndex: Int,
        maOrder: Int,
        filterType: Int,
        formula: Int,
        channels: List<Int>,
    ) {
        val targetIds = resolveConnectedControlTargetIds(uiState.value)
        if (targetIds.isEmpty()) {
            return
        }
        viewModelScope.launch {
            bleManager.setDspLiveParams(
                deviceIds = targetIds,
                dspIndex = dspIndex,
                maOrder = maOrder,
                filterType = filterType,
                formula = formula,
                channels = channels,
            )
        }
    }

    fun uploadSystemParams() {
        val targetIds = resolveConnectedControlTargetIds(uiState.value)
        if (targetIds.isEmpty()) {
            return
        }
        viewModelScope.launch {
            bleManager.uploadSystemParams(targetIds)
        }
    }

    fun uploadSystemParamsForDevice(deviceId: String) {
        viewModelScope.launch {
            bleManager.uploadSystemParams(listOf(deviceId))
        }
    }

    fun uploadDspParams(dspIndex: Int) {
        val targetIds = resolveConnectedControlTargetIds(uiState.value)
        if (targetIds.isEmpty()) {
            return
        }
        viewModelScope.launch {
            bleManager.uploadDspParams(targetIds, dspIndex)
        }
    }

    fun uploadDspParamsForDevice(deviceId: String, dspIndex: Int) {
        viewModelScope.launch {
            bleManager.uploadDspParams(listOf(deviceId), dspIndex)
        }
    }

    fun uploadAllParams() {
        val targetIds = resolveConnectedControlTargetIds(uiState.value)
        if (targetIds.isEmpty()) {
            return
        }
        viewModelScope.launch {
            bleManager.uploadAllParams(targetIds)
        }
    }

    fun uploadAllParamsForDevice(deviceId: String) {
        viewModelScope.launch {
            bleManager.uploadAllParams(deviceId)
        }
    }

    fun stageClosedLoopProfile(
        closedLoopMode: Int,
        triggerTrainStart: Int,
        triggerTrainDuration: Int,
        randomTriggerMin: Int,
        randomTriggerMax: Int,
        clParam1: List<Float>,
        clParam2: List<Float>,
        stimChannels: List<Int>,
    ) {
        val targetIds = resolveConnectedControlTargetIds(uiState.value)
        if (targetIds.isEmpty()) {
            return
        }
        viewModelScope.launch {
            bleManager.stageClosedLoopProfile(
                deviceIds = targetIds,
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
    }

    fun applyClosedLoopProfileToSystem(
        closedLoopMode: Int,
        triggerTrainStart: Int,
        triggerTrainDuration: Int,
        randomTriggerMin: Int,
        randomTriggerMax: Int,
        clParam1: List<Float>,
        clParam2: List<Float>,
        stimChannels: List<Int>,
    ) {
        val targetIds = resolveConnectedControlTargetIds(uiState.value)
        if (targetIds.isEmpty()) {
            return
        }
        viewModelScope.launch {
            bleManager.stageClosedLoopProfile(
                deviceIds = targetIds,
                closedLoopMode = closedLoopMode,
                triggerTrainStart = triggerTrainStart,
                triggerTrainDuration = triggerTrainDuration,
                randomTriggerMin = randomTriggerMin,
                randomTriggerMax = randomTriggerMax,
                clParam1 = clParam1,
                clParam2 = clParam2,
                stimChannels = stimChannels,
            )
            bleManager.uploadSystemParams(targetIds)
        }
    }

    fun applyClosedLoopProfileToAll(
        closedLoopMode: Int,
        triggerTrainStart: Int,
        triggerTrainDuration: Int,
        randomTriggerMin: Int,
        randomTriggerMax: Int,
        clParam1: List<Float>,
        clParam2: List<Float>,
        stimChannels: List<Int>,
    ) {
        val targetIds = resolveConnectedControlTargetIds(uiState.value)
        if (targetIds.isEmpty()) {
            return
        }
        viewModelScope.launch {
            bleManager.stageClosedLoopProfile(
                deviceIds = targetIds,
                closedLoopMode = closedLoopMode,
                triggerTrainStart = triggerTrainStart,
                triggerTrainDuration = triggerTrainDuration,
                randomTriggerMin = randomTriggerMin,
                randomTriggerMax = randomTriggerMax,
                clParam1 = clParam1,
                clParam2 = clParam2,
                stimChannels = stimChannels,
            )
            bleManager.uploadAllParams(targetIds)
        }
    }

    fun requestImpedance() {
        val targetIds = resolveConnectedControlTargetIds(uiState.value)
        if (targetIds.isEmpty()) {
            return
        }
        viewModelScope.launch {
            bleManager.requestImpedance(targetIds)
        }
    }

    fun requestImpedanceForDevice(deviceId: String) {
        viewModelScope.launch {
            bleManager.requestImpedance(deviceId)
        }
    }

    fun refreshSignalAnalysis() {
        val targetIds = resolveConnectedControlTargetIds(uiState.value)
        if (targetIds.isEmpty()) {
            return
        }
        viewModelScope.launch {
            bleManager.requestSpikeDetectorConfig(targetIds)
            bleManager.requestSpectrumConfig(targetIds)
            bleManager.requestSchedulerStatus(targetIds)
            bleManager.requestSchedulerConfig(targetIds)
        }
    }

    fun setSpikeDetectorConfig(config: com.wild.android.ble.SpikeDetectorConfigUiState) {
        val targetIds = resolveConnectedControlTargetIds(uiState.value)
        if (targetIds.isEmpty()) {
            return
        }
        viewModelScope.launch {
            bleManager.setSpikeDetectorConfig(targetIds, config)
        }
    }

    fun setSpectrumConfig(config: com.wild.android.ble.SpectrumConfigUiState) {
        val targetIds = resolveConnectedControlTargetIds(uiState.value)
        if (targetIds.isEmpty()) {
            return
        }
        viewModelScope.launch {
            bleManager.setSpectrumConfig(targetIds, config)
        }
    }

    fun setSchedulerEnabled(enabled: Boolean) {
        val targetIds = resolveConnectedControlTargetIds(uiState.value)
        if (targetIds.isEmpty()) {
            return
        }
        viewModelScope.launch {
            bleManager.setSchedulerEnabled(targetIds, enabled)
            bleManager.requestSchedulerStatus(targetIds)
            bleManager.requestSchedulerConfig(targetIds)
        }
    }

    fun setSchedulerRule(rule: com.wild.android.ble.SchedulerRuleUiState) {
        val targetIds = resolveConnectedControlTargetIds(uiState.value)
        if (targetIds.isEmpty()) {
            return
        }
        viewModelScope.launch {
            bleManager.setSchedulerRule(targetIds, rule)
            bleManager.requestSchedulerConfig(targetIds)
        }
    }

    fun setSchedulerRuleEnabled(ruleId: Int, enabled: Boolean) {
        val targetIds = resolveConnectedControlTargetIds(uiState.value)
        if (targetIds.isEmpty()) {
            return
        }
        viewModelScope.launch {
            bleManager.setSchedulerRuleEnabled(targetIds, ruleId, enabled)
            bleManager.requestSchedulerConfig(targetIds)
        }
    }

    fun clearSchedulerRule(ruleId: Int) {
        val targetIds = resolveConnectedControlTargetIds(uiState.value)
        if (targetIds.isEmpty()) {
            return
        }
        viewModelScope.launch {
            bleManager.clearSchedulerRule(targetIds, ruleId)
            bleManager.requestSchedulerConfig(targetIds)
        }
    }

    fun clearScheduler() {
        val targetIds = resolveConnectedControlTargetIds(uiState.value)
        if (targetIds.isEmpty()) {
            return
        }
        viewModelScope.launch {
            bleManager.clearScheduler(targetIds)
            bleManager.requestSchedulerStatus(targetIds)
            bleManager.requestSchedulerConfig(targetIds)
        }
    }

    fun startAutoImpedance(intervalMinutes: Int) {
        val activeSession = uiState.value.activeSession
        if (activeSession == null) {
            autoImpedanceState.value = autoImpedanceState.value.copy(
                running = false,
                deviceId = null,
                deviceName = "",
                nextRunAtMs = null,
                statusMessage = "Select an active BLE device before arming auto impedance.",
            )
            return
        }

        val safeIntervalMinutes = intervalMinutes.coerceIn(1, MaxAutoImpedanceIntervalMinutes)
        autoImpedanceGeneration += 1
        val generation = autoImpedanceGeneration
        autoImpedanceJob?.cancel()
        autoImpedanceState.value = AutoImpedanceState(
            running = true,
            deviceId = activeSession.id,
            deviceName = activeSession.name,
            intervalMinutes = safeIntervalMinutes,
            nextRunAtMs = null,
            statusMessage = "Auto impedance armed for ${activeSession.name}. First sweep starts now.",
        )
        autoImpedanceJob = viewModelScope.launch {
            try {
                runAutoImpedanceLoop(
                    generation = generation,
                    deviceId = activeSession.id,
                )
            } finally {
                if (autoImpedanceGeneration == generation && autoImpedanceState.value.running) {
                    autoImpedanceState.value = autoImpedanceState.value.copy(
                        running = false,
                        nextRunAtMs = null,
                        statusMessage = "Auto impedance stopped.",
                    )
                }
            }
        }
    }

    fun stopAutoImpedance() {
        autoImpedanceGeneration += 1
        autoImpedanceJob?.cancel()
        autoImpedanceJob = null
        autoImpedanceState.value = autoImpedanceState.value.copy(
            running = false,
            nextRunAtMs = null,
            statusMessage = "Auto impedance stopped.",
        )
    }

    fun requestSleep() {
        val activeId = uiState.value.activeSessionId ?: return
        viewModelScope.launch {
            bleManager.requestSleep(activeId)
        }
    }

    fun requestSoftwareReset() {
        val activeId = uiState.value.activeSessionId ?: return
        viewModelScope.launch {
            bleManager.requestSoftwareReset(activeId)
        }
    }

    fun requestBootloader() {
        val activeId = uiState.value.activeSessionId ?: return
        viewModelScope.launch {
            bleManager.requestBootloader(activeId)
        }
    }

    fun requestFirmwareUpdate() {
        val activeId = uiState.value.activeSessionId ?: return
        viewModelScope.launch {
            bleManager.requestFirmwareUpdate(activeId)
        }
    }

    /** Reads the picked fused Intel-HEX firmware into the bounded CE64 validator. */
    fun selectBleOtaPackage(uri: Uri) {
        val activeId = uiState.value.activeSessionId ?: return
        viewModelScope.launch {
            val application = getApplication<Application>()
            val packageName = displayNameFor(uri).ifBlank { "CE64 fused firmware.hex" }
            val bytes = runCatching {
                application.contentResolver.openInputStream(uri)?.use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count <= 0) {
                            break
                        }
                        if (output.size() + count > Ce64BleOtaPackage.MaxFusedHexBytes) {
                            throw IllegalArgumentException("The selected fused firmware file exceeds the supported size limit.")
                        }
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                } ?: throw IllegalArgumentException("Android could not open the selected firmware package.")
            }.getOrElse { error ->
                Log.w("WildViewModel", "Could not read fused CE64 firmware $packageName", error)
                // Passing an empty payload surfaces a normal validation error in
                // the selected device's maintenance card instead of failing silently.
                ByteArray(0)
            }
            bleManager.prepareBleOta(activeId, bytes, packageName)
        }
    }

    fun stageBleOta() {
        val activeId = uiState.value.activeSessionId ?: return
        viewModelScope.launch {
            bleManager.stageBleOta(activeId)
        }
    }

    fun installStagedBleOta() {
        val activeId = uiState.value.activeSessionId ?: return
        viewModelScope.launch {
            bleManager.installStagedBleOta(activeId)
        }
    }

    private fun displayNameFor(uri: Uri): String {
        val resolver = getApplication<Application>().contentResolver
        return resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index).orEmpty() else ""
        }.orEmpty()
    }

    fun requestAiModuleInstall(slot: Int) {
        val activeId = uiState.value.activeSessionId ?: return
        viewModelScope.launch {
            bleManager.requestAiModuleInstall(activeId, slot)
        }
    }

    fun selectAiModule(slot: Int?) {
        val activeId = uiState.value.activeSessionId ?: return
        viewModelScope.launch {
            bleManager.selectAiModule(activeId, slot)
        }
    }

    fun setAiRuntimeEnabled(enabled: Boolean) {
        val activeId = uiState.value.activeSessionId ?: return
        viewModelScope.launch {
            bleManager.setAiRuntimeEnabled(activeId, enabled)
        }
    }

    fun refreshAiRuntimeStatus() {
        val activeId = uiState.value.activeSessionId ?: return
        viewModelScope.launch {
            bleManager.requestAiRuntimeStatus(activeId)
            bleManager.requestAiResidentStatus(activeId)
        }
    }

    fun setRole(mode: Int, label: String) {
        val activeId = uiState.value.activeSessionId ?: return
        viewModelScope.launch {
            bleManager.setRole(activeId, mode, label)
        }
    }

    fun refreshRecordList() {
        val activeId = uiState.value.activeSessionId ?: return
        viewModelScope.launch {
            bleManager.refreshRecordList(activeId)
        }
    }

    fun refreshRecordListForDevice(deviceId: String) {
        viewModelScope.launch {
            bleManager.refreshRecordList(deviceId)
        }
    }

    fun refreshRecordListForScope() {
        val targetIds = resolveConnectedControlTargetIds(uiState.value)
        if (targetIds.isEmpty()) {
            return
        }
        viewModelScope.launch {
            targetIds.forEach { deviceId ->
                bleManager.refreshRecordList(deviceId)
            }
        }
    }

    fun prepareRecordsLaunchForCurrentScope() {
        val targetIds = resolveRecordLaunchRefreshTargetIds(uiState.value)
        if (targetIds.isEmpty()) {
            return
        }

        viewModelScope.launch {
            targetIds.forEach { deviceId ->
                bleManager.refreshRecordList(deviceId)
            }
        }
    }

    fun prepareControlLaunchForCurrentScope() {
        val targetIds = resolveControlLaunchReadTargetIds(uiState.value)
        if (targetIds.isEmpty()) {
            return
        }

        viewModelScope.launch {
            bleManager.requestAllParams(targetIds)
            bleManager.requestCameraParams(targetIds)
        }
    }

    fun prepareLiveLaunchForCurrentScope() {
        val targetIds = resolveLiveLaunchPrefetchTargetIds(uiState.value)
        if (targetIds.isEmpty()) {
            return
        }

        viewModelScope.launch {
            bleManager.requestAllParams(targetIds)
            bleManager.requestCameraParams(targetIds)
            targetIds.forEach { deviceId ->
                bleManager.refreshRecordList(deviceId)
            }
        }
    }

    fun prepareControlLaunchForDevice(deviceId: String) {
        viewModelScope.launch {
            val sessionBeforeLaunch = sessionForId(deviceId) ?: return@launch
            if (!sessionBeforeLaunch.isConnected && !sessionBeforeLaunch.isLinkingLike) {
                // Use the ordinary CE handshake here.  The device dashboard has
                // already made this session active; launching a parallel fast
                // connect can leave the control screen behind that selection.
                bleManager.connect(deviceId, makeActive = false)
            }

            val settledSession = waitForControlLaunchReady(deviceId, ConnectAndSyncTimeoutMs)
                ?: sessionForId(deviceId)
                ?: return@launch
            if (!settledSession.isConnected) {
                Log.w(
                    "WildViewModel",
                    "control launch preload skipped for $deviceId state=${settledSession.hostState}",
                )
                return@launch
            }

            // System and DSP payloads are loaded by the sequential connection
            // bootstrap.  DeviceParameterScreen requests the optional camera
            // payload only after that core sequence is complete.
        }
    }

    fun deleteLastRecord() {
        val activeId = uiState.value.activeSessionId ?: return
        viewModelScope.launch {
            bleManager.deleteLastRecord(activeId)
        }
    }

    fun deleteAllRecords() {
        val activeId = uiState.value.activeSessionId ?: return
        viewModelScope.launch {
            bleManager.deleteAllRecords(activeId)
        }
    }

    fun exportRecord(recordIndex: Int) {
        val activeId = uiState.value.activeSessionId ?: return
        viewModelScope.launch {
            bleManager.exportRecord(activeId, recordIndex)
        }
    }

    fun exportAllRecords() {
        val activeId = uiState.value.activeSessionId ?: return
        viewModelScope.launch {
            bleManager.exportAllRecords(activeId)
        }
    }

    fun exportAllRecordsForScope() {
        val state = uiState.value
        val targetIds = when (state.controlScope) {
            ControlScope.ActiveDevice ->
                listOfNotNull(state.activeSession).filter { it.records.isNotEmpty() }.map { it.id }
            ControlScope.SelectedDevices ->
                state.selectedSessions.filter { it.records.isNotEmpty() }.map { it.id }
            ControlScope.AllConnected ->
                state.connectedSessions.filter { it.records.isNotEmpty() }.map { it.id }
        }
        if (targetIds.isEmpty()) {
            return
        }
        viewModelScope.launch {
            targetIds.forEach { deviceId ->
                bleManager.exportAllRecords(deviceId)
            }
        }
    }

    fun exportAllRecordsForDevice(deviceId: String) {
        viewModelScope.launch {
            bleManager.exportAllRecords(deviceId)
        }
    }

    fun exportImpedanceSnapshot() {
        val activeId = uiState.value.activeSessionId ?: return
        viewModelScope.launch {
            bleManager.exportImpedanceSnapshot(activeId)
        }
    }

    fun showImpedanceExportPath() {
        val activeId = uiState.value.activeSessionId ?: return
        bleManager.showImpedanceExportPath(activeId)
    }

    fun showRecordExportPath() {
        val activeId = uiState.value.activeSessionId ?: return
        bleManager.showRecordExportPath(activeId)
    }

    fun showRecordExportPathForDevice(deviceId: String) {
        bleManager.showRecordExportPath(deviceId)
    }

    fun showSyncLogPath() {
        val activeId = uiState.value.activeSessionId ?: return
        bleManager.showSyncLogPath(activeId)
    }

    fun showSyncLogPathForDevice(deviceId: String) {
        bleManager.showSyncLogPath(deviceId)
    }

    fun showTriggerWaveformPath() {
        val activeId = uiState.value.activeSessionId ?: return
        bleManager.showTriggerWaveformPath(activeId)
    }

    fun showTriggerWaveformPathForDevice(deviceId: String) {
        bleManager.showTriggerWaveformPath(deviceId)
    }

    private fun resolveControlTargetIds(): List<String> {
        return resolveControlTargetIds(uiState.value)
    }

    private suspend fun runAutoImpedanceLoop(
        generation: Long,
        deviceId: String,
    ) {
        while (autoImpedanceGeneration == generation && autoImpedanceState.value.running) {
            val session = sessionForId(deviceId)
            if (session == null) {
                autoImpedanceState.value = autoImpedanceState.value.copy(
                    running = false,
                    deviceId = null,
                    deviceName = "",
                    nextRunAtMs = null,
                    statusMessage = "Auto impedance stopped because the target device is no longer available.",
                )
                return
            }

            autoImpedanceState.value = autoImpedanceState.value.copy(
                deviceName = session.name,
                nextRunAtMs = null,
                statusMessage = "Running scheduled impedance sweep on ${session.name}...",
            )

            val passStatus = runAutoImpedancePass(deviceId)
            if (autoImpedanceGeneration != generation || !autoImpedanceState.value.running) {
                return
            }

            val intervalMinutes = autoImpedanceState.value.intervalMinutes
            val nextRunAtMs = System.currentTimeMillis() + intervalMinutes * 60_000L
            autoImpedanceState.value = autoImpedanceState.value.copy(
                nextRunAtMs = nextRunAtMs,
                statusMessage = passStatus,
            )
            delay((nextRunAtMs - System.currentTimeMillis()).coerceAtLeast(0L))
        }
    }

    private suspend fun runAutoImpedancePass(deviceId: String): String {
        val session = sessionForId(deviceId)
            ?: return "Auto impedance stopped because the target device is no longer available."

        if (!session.isConnected) {
            bleManager.connect(deviceId, makeActive = false, fastBootstrap = true)
        }

        var ready = waitForAutoImpedanceReady(deviceId, ConnectAndSyncTimeoutMs)
        if (!ready && sessionForId(deviceId)?.isConnected == true) {
            bleManager.requestResync(listOf(deviceId), suppressRtcWrite = false)
            ready = waitForAutoImpedanceReady(deviceId, ResyncTimeoutMs)
        }
        if (!ready) {
            return "Timed out waiting for ${deviceNameFor(deviceId)} to reach a synced BLE state."
        }

        val baselineUpdatedAtMs = sessionForId(deviceId)?.impedanceSnapshot?.updatedAtMs ?: 0L
        bleManager.requestImpedance(deviceId)
        val captured = waitForAutoImpedanceSnapshot(
            deviceId = deviceId,
            baselineUpdatedAtMs = baselineUpdatedAtMs,
            timeoutMs = ImpedanceCaptureTimeoutMs,
            quietWindowMs = ImpedanceQuietWindowMs,
        )
        if (!captured) {
            return "No impedance bundle arrived from ${deviceNameFor(deviceId)}."
        }

        bleManager.exportImpedanceSnapshot(deviceId)
        bleManager.requestSoftwareReset(deviceId)
        val resetSettled = waitForAutoImpedanceDisconnect(
            deviceId = deviceId,
            timeoutMs = AutoImpedanceResetTimeoutMs,
        )
        return if (resetSettled) {
            "Saved scheduled impedance CSV bundle for ${deviceNameFor(deviceId)} and reset the device."
        } else {
            "Saved scheduled impedance CSV bundle for ${deviceNameFor(deviceId)} and requested software reset."
        }
    }

    private suspend fun waitForAutoImpedanceReady(
        deviceId: String,
        timeoutMs: Long,
    ): Boolean {
        val deadlineAtMs = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadlineAtMs && autoImpedanceState.value.running) {
            val session = sessionForId(deviceId) ?: return false
            if (session.hostState in AutoImpedanceReadyStates) {
                return true
            }
            if (session.hostState == BleHostSessionState.Error) {
                return false
            }
            delay(AutoImpedancePollMs)
        }
        return false
    }

    private suspend fun waitForControlLaunchReady(
        deviceId: String,
        timeoutMs: Long,
    ): DeviceSessionUiState? {
        val deadlineAtMs = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadlineAtMs) {
            val session = sessionForId(deviceId) ?: return null
            if (session.isConnected || session.hostState == BleHostSessionState.Error) {
                return session
            }
            delay(ControlLaunchPollMs)
        }
        return sessionForId(deviceId)
    }

    private suspend fun waitForAutoImpedanceSnapshot(
        deviceId: String,
        baselineUpdatedAtMs: Long,
        timeoutMs: Long,
        quietWindowMs: Long,
    ): Boolean {
        val deadlineAtMs = System.currentTimeMillis() + timeoutMs
        var sawUpdate = false
        var latestUpdateAtMs = baselineUpdatedAtMs
        var quietSinceAtMs = 0L

        while (System.currentTimeMillis() < deadlineAtMs && autoImpedanceState.value.running) {
            val session = sessionForId(deviceId) ?: return false
            if (!session.isConnected) {
                return false
            }

            val currentUpdatedAtMs = session.impedanceSnapshot.updatedAtMs
            if (currentUpdatedAtMs > latestUpdateAtMs) {
                latestUpdateAtMs = currentUpdatedAtMs
                quietSinceAtMs = System.currentTimeMillis()
                sawUpdate = true
            } else if (sawUpdate && System.currentTimeMillis() - quietSinceAtMs >= quietWindowMs) {
                return true
            }

            delay(AutoImpedancePollMs)
        }

        return sawUpdate
    }

    private suspend fun waitForAutoImpedanceDisconnect(
        deviceId: String,
        timeoutMs: Long,
    ): Boolean {
        val deadlineAtMs = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadlineAtMs && autoImpedanceState.value.running) {
            val session = sessionForId(deviceId)
            if (session == null || !session.isConnected) {
                return true
            }
            delay(AutoImpedancePollMs)
        }
        return sessionForId(deviceId)?.isConnected != true
    }

    private fun sessionForId(deviceId: String): DeviceSessionUiState? {
        return uiState.value.sessions.firstOrNull { it.id == deviceId }
    }

    private fun deviceNameFor(deviceId: String): String {
        return sessionForId(deviceId)?.name ?: deviceId
    }

    private companion object {
        val AutoImpedanceReadyStates = setOf(
            BleHostSessionState.Synced,
            BleHostSessionState.Previewing,
            BleHostSessionState.Recording,
        )
        const val DebugScanWaitMs = 12_000L
        const val DebugConnectWaitMs = 20_000L
        const val AutoImpedancePollMs = 150L
        const val ControlLaunchPollMs = 150L
        const val ConnectAndSyncTimeoutMs = 18_000L
        const val ResyncTimeoutMs = 8_000L
        const val ImpedanceCaptureTimeoutMs = 8_000L
        const val AutoImpedanceResetTimeoutMs = 6_000L
        const val ImpedanceQuietWindowMs = 750L
        const val MaxAutoImpedanceIntervalMinutes = 24 * 60
    }
}
