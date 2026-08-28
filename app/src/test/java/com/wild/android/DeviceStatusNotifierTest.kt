package com.wild.android

import com.wild.android.ble.BleHostSessionState
import com.wild.android.ble.DeviceSessionUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeviceStatusNotifierTest {
    @Test
    fun emitsConnectedOnlyAfterTheLinkBecomesUsable() {
        val previous = snapshot(BleHostSessionState.Disconnected)
        val current = session(BleHostSessionState.Synced)

        assertEquals(
            DeviceStatusAlertKind.Connected,
            deviceStatusAlertForTransition(previous, current)?.kind,
        )
    }

    @Test
    fun emitsOneRecordingStartAcrossStartingAndRecordingStates() {
        val connected = snapshot(BleHostSessionState.Synced)
        val starting = session(BleHostSessionState.StartingRecording)

        assertEquals(
            DeviceStatusAlertKind.RecordingStarted,
            deviceStatusAlertForTransition(connected, starting)?.kind,
        )
        assertNull(
            deviceStatusAlertForTransition(
                DeviceStatusSnapshot.from(starting),
                session(BleHostSessionState.Recording),
            ),
        )
    }

    @Test
    fun recordsAStopOnlyWhenTheDeviceRemainsLinked() {
        val recording = snapshot(BleHostSessionState.Recording)

        assertEquals(
            DeviceStatusAlertKind.RecordingStopped,
            deviceStatusAlertForTransition(recording, session(BleHostSessionState.Synced))?.kind,
        )
        assertNull(
            deviceStatusAlertForTransition(recording, session(BleHostSessionState.Disconnected)),
        )
    }

    @Test
    fun reportsUnexpectedLinkLossButNotAnIntentionalDisconnect() {
        val linked = snapshot(BleHostSessionState.Synced)

        assertEquals(
            DeviceStatusAlertKind.ConnectionLost,
            deviceStatusAlertForTransition(linked, session(BleHostSessionState.Reconnecting))?.kind,
        )
        assertNull(
            deviceStatusAlertForTransition(linked, session(BleHostSessionState.Disconnected)),
        )
    }

    @Test
    fun givesNewFailurePriorityOverTheRelatedStateChange() {
        val linked = snapshot(BleHostSessionState.Synced)
        val error = session(BleHostSessionState.Error, failure = "GATT status 133")

        val alert = deviceStatusAlertForTransition(linked, error)

        assertEquals(DeviceStatusAlertKind.Problem, alert?.kind)
        assertEquals("GATT status 133", alert?.detail)
    }

    @Test
    fun alertsOnceWhenACompatibleDeviceFirstAppearsInRange() {
        val nearby = session(BleHostSessionState.Disconnected).copy(
            namePrefixMatch = true,
        )

        assertEquals(
            DeviceStatusAlertKind.DeviceInRange,
            deviceInRangeAlertFor(nearby)?.kind,
        )
        assertNull(deviceInRangeAlertFor(nearby.copy(namePrefixMatch = false)))
    }

    @Test
    fun alertsWhenAnAlreadySeenDeviceBecomesRecognizedAsCompatible() {
        val unseenCompatible = snapshot(BleHostSessionState.Disconnected)
        val compatible = session(BleHostSessionState.Disconnected).copy(
            namePrefixMatch = true,
        )

        assertEquals(
            DeviceStatusAlertKind.DeviceInRange,
            if (!unseenCompatible.eligibleForRangeAlert) deviceInRangeAlertFor(compatible)?.kind else null,
        )
    }

    private fun snapshot(state: BleHostSessionState): DeviceStatusSnapshot {
        return DeviceStatusSnapshot.from(session(state))
    }

    private fun session(
        state: BleHostSessionState,
        failure: String = "",
    ): DeviceSessionUiState {
        return DeviceSessionUiState(
            id = "test-device",
            name = "CE64X_CCE02F232276",
            address = "76:22:23:2F:E0:CC",
            traceColorArgb = 0xFF2A86E8.toInt(),
            hostState = state,
            lastFailure = failure,
        )
    }
}
