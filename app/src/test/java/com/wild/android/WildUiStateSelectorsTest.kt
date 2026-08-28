package com.wild.android

import com.wild.android.ble.BleHostSessionState
import com.wild.android.ble.ControlScope
import com.wild.android.ble.DeviceSessionUiState
import com.wild.android.ui.canStartLivePreview
import com.wild.android.ui.hasLivePreviewControl
import com.wild.android.ui.shouldReadCameraDuringControlLaunch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WildUiStateSelectorsTest {
    @Test
    fun activeScopeReturnsFocusedSessionIdEvenWhenOnlyOneIsConnected() {
        val connected = session("a", BleHostSessionState.Connected)
        val disconnected = session("b", BleHostSessionState.Disconnected)
        val state = WildUiState(
            sessions = listOf(connected, disconnected),
            activeSessionId = "a",
            controlScope = ControlScope.ActiveDevice,
        )

        assertEquals(listOf("a"), resolveControlTargetIds(state))
    }

    @Test
    fun selectedScopeDropsDisconnectedSelections() {
        val connectedA = session("a", BleHostSessionState.Previewing)
        val connectedB = session("b", BleHostSessionState.Recording)
        val disconnected = session("c", BleHostSessionState.Disconnected)
        val state = WildUiState(
            sessions = listOf(connectedA, connectedB, disconnected),
            activeSessionId = "a",
            controlScope = ControlScope.SelectedDevices,
            selectedSessionIds = setOf("a", "c"),
        )

        assertEquals(listOf("a"), state.selectedConnectedSessions.map { it.id })
        assertEquals(listOf("a"), resolveControlTargetIds(state))
    }

    @Test
    fun allConnectedScopeReturnsEveryConnectedSession() {
        val connectedA = session("a", BleHostSessionState.Connected)
        val connectedB = session("b", BleHostSessionState.StoppingRecording)
        val disconnected = session("c", BleHostSessionState.Error)
        val state = WildUiState(
            sessions = listOf(connectedA, connectedB, disconnected),
            activeSessionId = "a",
            controlScope = ControlScope.AllConnected,
            selectedSessionIds = setOf("b", "c"),
        )

        assertEquals(listOf("a", "b"), state.connectedSessions.map { it.id })
        assertEquals(listOf("a", "b"), resolveControlTargetIds(state))
    }

    @Test
    fun connectedControlTargetsDropDisconnectedActiveSession() {
        val disconnectedActive = session("a", BleHostSessionState.Disconnected)
        val connected = session("b", BleHostSessionState.Connected)
        val state = WildUiState(
            sessions = listOf(disconnectedActive, connected),
            activeSessionId = "a",
            controlScope = ControlScope.ActiveDevice,
        )

        assertEquals(listOf("a"), resolveControlTargetIds(state))
        assertEquals(emptyList(), resolveConnectedControlTargetIds(state))
    }

    @Test
    fun pendingConnectQueueDropsSessionsThatAreAlreadyConnected() {
        val queued = session("queued", BleHostSessionState.Disconnected)
        val connected = session("connected", BleHostSessionState.Connected)
        val retry = session("retry", BleHostSessionState.Error)
        val state = WildUiState(
            sessions = listOf(queued, connected, retry),
            pendingConnectionIds = listOf("queued", "connected", "retry"),
        )

        assertEquals(listOf("queued", "retry"), state.pendingConnectionSessions.map { it.id })
        assertEquals(listOf("queued", "retry"), resolvePendingConnectionTargetIds(state))
    }

    @Test
    fun pendingConnectQueuePreservesStagedQueueOrder() {
        val near = session("near", BleHostSessionState.Error)
        val verified = session("verified", BleHostSessionState.Disconnected)
        val ignored = session("ignored", BleHostSessionState.Connected)
        val state = WildUiState(
            sessions = listOf(near, ignored, verified),
            pendingConnectionIds = listOf("verified", "near", "ignored"),
        )

        assertEquals(listOf("verified", "near"), resolvePendingConnectionTargetIds(state))
    }

    @Test
    fun recordLaunchRefreshTargetsFollowCurrentSelectedScope() {
        val active = session("active", BleHostSessionState.Connected)
        val selected = session("selected", BleHostSessionState.Previewing)
        val disconnected = session("disconnected", BleHostSessionState.Disconnected)
        val state = WildUiState(
            sessions = listOf(active, selected, disconnected),
            activeSessionId = "active",
            controlScope = ControlScope.SelectedDevices,
            selectedSessionIds = linkedSetOf("selected", "disconnected"),
        )

        assertEquals(listOf("selected"), resolveRecordLaunchRefreshTargetIds(state))
    }

    @Test
    fun recordLaunchRefreshFallsBackToConnectedActiveWhenScopeHasNoConnectedTargets() {
        val active = session("active", BleHostSessionState.Connected)
        val disconnected = session("disconnected", BleHostSessionState.Disconnected)
        val state = WildUiState(
            sessions = listOf(active, disconnected),
            activeSessionId = "active",
            controlScope = ControlScope.SelectedDevices,
            selectedSessionIds = linkedSetOf("disconnected"),
        )

        assertEquals(listOf("active"), resolveRecordLaunchRefreshTargetIds(state))
    }

    @Test
    fun controlLaunchReadTargetsFollowCurrentAllConnectedScope() {
        val active = session("active", BleHostSessionState.Connected)
        val selected = session("selected", BleHostSessionState.Previewing)
        val disconnected = session("disconnected", BleHostSessionState.Disconnected)
        val state = WildUiState(
            sessions = listOf(active, selected, disconnected),
            activeSessionId = "active",
            controlScope = ControlScope.AllConnected,
            selectedSessionIds = linkedSetOf("selected"),
        )

        assertEquals(listOf("active", "selected"), resolveControlLaunchReadTargetIds(state))
    }

    @Test
    fun liveLaunchPrefetchTargetsFollowCurrentSelectedScope() {
        val active = session("active", BleHostSessionState.Connected)
        val selected = session("selected", BleHostSessionState.Previewing)
        val disconnected = session("disconnected", BleHostSessionState.Disconnected)
        val state = WildUiState(
            sessions = listOf(active, selected, disconnected),
            activeSessionId = "active",
            controlScope = ControlScope.SelectedDevices,
            selectedSessionIds = linkedSetOf("selected", "disconnected"),
        )

        assertEquals(listOf("selected"), resolveLiveLaunchPrefetchTargetIds(state))
    }

    @Test
    fun liveLaunchPrefetchFallsBackToConnectedActiveWhenScopeHasNoConnectedTargets() {
        val active = session("active", BleHostSessionState.Connected)
        val disconnected = session("disconnected", BleHostSessionState.Disconnected)
        val state = WildUiState(
            sessions = listOf(active, disconnected),
            activeSessionId = "active",
            controlScope = ControlScope.SelectedDevices,
            selectedSessionIds = linkedSetOf("disconnected"),
        )

        assertEquals(listOf("active"), resolveLiveLaunchPrefetchTargetIds(state))
    }

    @Test
    fun recorderBackedLiveSignalRemainsControllableAsLiveSignal() {
        val recorderBacked = session("fallback", BleHostSessionState.Recording).copy(
            recorderBackedLiveSignal = true,
        )
        val ordinaryRecording = recorderBacked.copy(recorderBackedLiveSignal = false)
        val directPreview = session("preview", BleHostSessionState.Previewing)

        assertTrue(hasLivePreviewControl(recorderBacked))
        assertFalse(hasLivePreviewControl(ordinaryRecording))
        assertTrue(hasLivePreviewControl(directPreview))
    }

    @Test
    fun explicitWaveformControlRemainsAvailableDuringRecording() {
        val recording = session("recording", BleHostSessionState.Recording)
        val waveformActive = recording.copy(waveformPreviewActive = true)

        assertTrue(canStartLivePreview(recording))
        assertFalse(hasLivePreviewControl(recording))
        assertFalse(canStartLivePreview(waveformActive))
        assertTrue(hasLivePreviewControl(waveformActive))
    }

    @Test
    fun previewRouteSessionsPreferActiveConnectedDeviceOverDisconnectedSelectedFallback() {
        val active = session("active", BleHostSessionState.Connected).copy(name = "Active")
        val disconnectedSelected = session("selected", BleHostSessionState.Disconnected).copy(name = "Selected")
        val state = WildUiState(
            sessions = listOf(active, disconnectedSelected),
            activeSessionId = "active",
            controlScope = ControlScope.SelectedDevices,
            selectedSessionIds = linkedSetOf("selected"),
        )

        assertEquals(listOf("active"), resolvePreviewRouteSessions(state).map { it.id })
        assertEquals(listOf("active"), resolvePreviewRouteTargetIds(state))
    }

    @Test
    fun previewRouteSessionsPreserveSelectedScopeOrderForMultiDevicePreview() {
        val bravo = session("bravo", BleHostSessionState.Connected).copy(name = "Bravo")
        val alpha = session("alpha", BleHostSessionState.Connected).copy(name = "Alpha")
        val state = WildUiState(
            sessions = listOf(alpha, bravo),
            activeSessionId = "bravo",
            controlScope = ControlScope.SelectedDevices,
            selectedSessionIds = linkedSetOf("bravo", "alpha"),
        )

        assertEquals(listOf("bravo", "alpha"), resolvePreviewRouteSessions(state).map { it.id })
        assertEquals(listOf("bravo", "alpha"), resolvePreviewRouteTargetIds(state))
    }

    @Test
    fun previewRouteSessionsKeepLaneOrderStableWhenActiveDeviceChanges() {
        val first = session("first", BleHostSessionState.Connected).copy(name = "Zulu")
        val second = session("second", BleHostSessionState.Connected).copy(name = "Alpha")
        val third = session("third", BleHostSessionState.Connected).copy(name = "Bravo")
        val state = WildUiState(
            sessions = listOf(first, second, third),
            activeSessionId = "third",
            controlScope = ControlScope.AllConnected,
        )

        assertEquals(listOf("first", "second", "third"), resolvePreviewRouteSessions(state).map { it.id })
        assertEquals(listOf("first", "second", "third"), resolvePreviewRouteTargetIds(state))
    }

    @Test
    fun previewRouteSessionsPreserveDisconnectedSelectionWhenNothingConnectedIsAvailable() {
        val disconnectedA = session("selected-a", BleHostSessionState.Disconnected).copy(name = "Bravo")
        val disconnectedB = session("selected-b", BleHostSessionState.Error).copy(name = "Alpha")
        val state = WildUiState(
            sessions = listOf(disconnectedA, disconnectedB),
            activeSessionId = "selected-a",
            controlScope = ControlScope.SelectedDevices,
            selectedSessionIds = linkedSetOf("selected-a", "selected-b"),
        )

        assertEquals(listOf("selected-a", "selected-b"), resolvePreviewRouteSessions(state).map { it.id })
        assertEquals(emptyList(), resolvePreviewRouteTargetIds(state))
    }

    @Test
    fun controlLaunchReadFallsBackToConnectedActiveWhenNoScopedTargetsRemain() {
        val active = session("active", BleHostSessionState.Connected)
        val disconnected = session("disconnected", BleHostSessionState.Disconnected)
        val state = WildUiState(
            sessions = listOf(active, disconnected),
            activeSessionId = "active",
            controlScope = ControlScope.SelectedDevices,
            selectedSessionIds = linkedSetOf("disconnected"),
        )

        assertEquals(listOf("active"), resolveControlLaunchReadTargetIds(state))
    }

    @Test
    fun selectionChangePreservesExplicitScopeAndFallsBackWhenSelectedScopeEmpties() {
        val connected = session("connected", BleHostSessionState.Previewing)
        val candidate = session("candidate", BleHostSessionState.Disconnected)

        assertEquals(
            ControlScope.ActiveDevice,
            resolveScopeAfterSelectionChange(
                previousScope = ControlScope.ActiveDevice,
                sessions = listOf(connected, candidate),
                selectedIds = setOf("connected"),
            ),
        )
        assertEquals(
            ControlScope.ActiveDevice,
            resolveScopeAfterSelectionChange(
                previousScope = ControlScope.SelectedDevices,
                sessions = listOf(connected, candidate),
                selectedIds = setOf("candidate"),
            ),
        )
    }

    @Test
    fun queueableMarkedIdsKeepOnlyUnconnectedNonLinkingMarks() {
        val candidate = session("candidate", BleHostSessionState.Disconnected)
        val connected = session("connected", BleHostSessionState.Connected)
        val linking = session("linking", BleHostSessionState.Connecting)
        val retry = session("retry", BleHostSessionState.Error)
        val state = WildUiState(
            sessions = listOf(candidate, connected, linking, retry),
            markedSessionIds = linkedSetOf("candidate", "connected", "linking", "retry"),
        )

        assertEquals(listOf("candidate", "retry"), resolveQueueableMarkedIds(state))
    }

    @Test
    fun queueableMarkedIdsPreservePhoneMarkOrderInsteadOfRosterSort() {
        val first = session("first", BleHostSessionState.Disconnected)
        val second = session("second", BleHostSessionState.Disconnected)
        val third = session("third", BleHostSessionState.Disconnected)
        val state = WildUiState(
            sessions = listOf(first, second, third),
            markedSessionIds = linkedSetOf("third", "first"),
        )

        assertEquals(listOf("third", "first"), resolveQueueableMarkedIds(state))
    }

    @Test
    fun connectBatchSelectionKeepsExistingConnectedSelectionAndAppendsNewBatchOrder() {
        val selected = session("selected", BleHostSessionState.Previewing)
        val stale = session("stale", BleHostSessionState.Disconnected)
        val batchA = session("batch-a", BleHostSessionState.Disconnected)
        val batchB = session("batch-b", BleHostSessionState.Disconnected)
        val state = WildUiState(
            sessions = listOf(selected, stale, batchA, batchB),
            selectedSessionIds = linkedSetOf("selected", "stale"),
        )

        assertEquals(
            listOf("selected", "stale", "batch-b", "batch-a"),
            mergeSelectedIdsForConnectBatch(state, listOf("batch-b", "batch-a")).toList(),
        )
    }

    @Test
    fun removeIdsPreservingOrderClearsConnectedMarksWithoutReorderingRemainingEntries() {
        assertEquals(
            listOf("first", "fourth"),
            removeIdsPreservingOrder(
                source = linkedSetOf("first", "second", "third", "fourth"),
                idsToRemove = listOf("third", "second", "missing"),
            ).toList(),
        )
    }

    @Test
    fun connectingAndReconnectingBucketsStaySeparateFromConnected() {
        val connecting = session("connecting", BleHostSessionState.Connecting)
        val reconnecting = session("reconnecting", BleHostSessionState.Reconnecting)
        val disconnecting = session("disconnecting", BleHostSessionState.Disconnecting)
        val connected = session("connected", BleHostSessionState.Connected)
        val state = WildUiState(
            sessions = listOf(connecting, reconnecting, disconnecting, connected),
        )

        assertEquals(listOf("connected"), state.connectedSessions.map { it.id })
        assertEquals(listOf("connecting"), state.connectingSessions.map { it.id })
        assertEquals(listOf("reconnecting"), state.reconnectingSessions.map { it.id })
        assertEquals(
            listOf("connecting", "reconnecting", "disconnecting"),
            state.sessions.filter { it.isLinkingLike }.map { it.id },
        )
    }

    @Test
    fun deviceRosterBucketsSeparatePhoneRosterLanes() {
        val connected = session("connected", BleHostSessionState.Connected)
        val linking = session("linking", BleHostSessionState.Connecting)
        val verified = session("verified", BleHostSessionState.Disconnected).copy(verifiedTransport = true)
        val nearby = session("nearby", BleHostSessionState.Disconnected)
        val state = WildUiState(
            sessions = listOf(connected, linking, verified, nearby),
        )

        val buckets = buildDeviceRosterBuckets(state)

        assertEquals(listOf("connected"), buckets.connected.map { it.id })
        assertEquals(listOf("linking"), buckets.linking.map { it.id })
        assertEquals(listOf("verified"), buckets.verified.map { it.id })
        assertEquals(listOf("nearby"), buckets.nearby.map { it.id })
    }

    @Test
    fun controlLaunchWaitsForCorePayloadBeforeRequestingOptionalCameraParams() {
        val partial = session("partial", BleHostSessionState.Synced).copy(
            systemParamHex = "01",
        )
        val coreReady = partial.copy(
            dsp1ParamHex = "02",
            dsp2ParamHex = "03",
        )

        assertFalse(shouldReadCameraDuringControlLaunch(partial))
        assertTrue(shouldReadCameraDuringControlLaunch(coreReady))
        assertFalse(shouldReadCameraDuringControlLaunch(coreReady.copy(cameraParamHex = "01")))
    }

    @Test
    fun normalizeDeviceRosterPaneFallsBackToFirstNonEmptyLane() {
        val verified = session("verified", BleHostSessionState.Disconnected).copy(namePrefixMatch = true)
        val nearby = session("nearby", BleHostSessionState.Disconnected)
        val state = WildUiState(
            sessions = listOf(verified, nearby),
        )

        val buckets = buildDeviceRosterBuckets(state)

        assertEquals(DeviceRosterPane.Verified, normalizeDeviceRosterPane(DeviceRosterPane.Connected, buckets))
        assertEquals(DeviceRosterPane.Verified, normalizeDeviceRosterPane(DeviceRosterPane.Linking, buckets))
        assertEquals(DeviceRosterPane.Verified, normalizeDeviceRosterPane(DeviceRosterPane.Verified, buckets))
        assertEquals(DeviceRosterPane.Nearby, normalizeDeviceRosterPane(DeviceRosterPane.Nearby, DeviceRosterBuckets(
            connected = emptyList(),
            linking = emptyList(),
            verified = emptyList(),
            nearby = buckets.nearby,
        )))
    }

    private fun session(
        id: String,
        state: BleHostSessionState,
    ): DeviceSessionUiState {
        return DeviceSessionUiState(
            id = id,
            name = "Device $id",
            address = "addr-$id",
            traceColorArgb = 0,
            hostState = state,
        )
    }
}
