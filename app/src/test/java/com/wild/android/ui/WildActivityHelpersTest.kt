package com.wild.android.ui

import com.wild.android.ble.DeviceSessionUiState
import com.wild.android.ble.SessionEventUiState
import kotlin.test.Test
import kotlin.test.assertEquals

class WildActivityHelpersTest {
    @Test
    fun sessionLatestActivityLinePrefersNewestEvent() {
        val session = baseSession().copy(
            lastMessage = "Host idle",
            lastFailure = "Old failure",
            recentEvents = listOf(
                SessionEventUiState(timestampMs = 10L, summary = "Sync settled"),
                SessionEventUiState(timestampMs = 20L, summary = "Preview resumed"),
            ),
        )

        assertEquals("Preview resumed", sessionLatestActivityLine(session))
    }

    @Test
    fun sessionLatestActivityLineFallsBackToFailureThenMessageThenDefault() {
        assertEquals(
            "Link timeout",
            sessionLatestActivityLine(baseSession().copy(lastFailure = "Link timeout")),
        )
        assertEquals(
            "Preview armed",
            sessionLatestActivityLine(baseSession().copy(lastMessage = "Preview armed")),
        )
        assertEquals(
            "No recent BLE activity.",
            sessionLatestActivityLine(baseSession()),
        )
    }

    @Test
    fun formatByteCountCompactUsesReadableUnits() {
        assertEquals("0 B", formatByteCountCompact(0))
        assertEquals("1.0 KB", formatByteCountCompact(1024))
        assertEquals("1.5 KB", formatByteCountCompact(1536))
        assertEquals("1.0 MB", formatByteCountCompact(1024 * 1024))
    }

    @Test
    fun formatUsedSpaceLabelUsesDesktopStyleMbFormatting() {
        assertEquals("--", formatUsedSpaceLabel(null))
        assertEquals("0.00 MB", formatUsedSpaceLabel(0.0))
        assertEquals("12.35 MB", formatUsedSpaceLabel(12.3456))
    }

    @Test
    fun formatOnlineCounterExtrasOmitsEmptyCountersAndFormatsDesktopStyleExtras() {
        assertEquals(null, formatOnlineCounterExtras(baseSession()))
        assertEquals(
            "Notif 12",
            formatOnlineCounterExtras(baseSession().copy(notificationRxCount = 12)),
        )
        assertEquals(
            "Notif 12  |  Busy 3",
            formatOnlineCounterExtras(
                baseSession().copy(
                    notificationRxCount = 12,
                    legacyConfigBusyCount = 3,
                ),
            ),
        )
    }

    private fun baseSession(): DeviceSessionUiState {
        return DeviceSessionUiState(
            id = "device-a",
            name = "Device A",
            address = "addr-a",
            traceColorArgb = 0,
        )
    }
}
