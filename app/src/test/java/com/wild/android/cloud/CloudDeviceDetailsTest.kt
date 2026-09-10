package com.wild.android.cloud

import com.wild.android.ble.DeviceSessionUiState
import com.wild.android.ble.SchedulerStatusUiState
import kotlin.test.*

class CloudDeviceDetailsTest {
    private fun session() = DeviceSessionUiState("device", "CE128", "address", 0,
        schedulerStatus = SchedulerStatusUiState(2, true, false, false, 0, null, 255, 0, 0, 255, 0, 0, 0, 0),
        schedulerReportedAtMs = 1000L)

    @Test fun scheduleObservationTimeDoesNotCauseExtraCloudWrites() {
        val first = session()
        val ping = first.copy(schedulerReportedAtMs = 2000L)
        assertEquals(cloudStatusFingerprint(listOf(first)), cloudStatusFingerprint(listOf(ping)))
        assertNotEquals(cloudStatusFingerprint(listOf(first)), cloudStatusFingerprint(listOf(first.copy(
            schedulerStatus = first.schedulerStatus!!.copy(clockValid = true)))))
    }

    @Test fun noWakeIsExplicitAndRawFirmwareDataIsNotUploaded() {
        val details = cloudDeviceDetails(session())
        assertEquals(-1L, (details["scheduler"] as Map<*, *>)["nextWakeSeconds"])
        assertFalse(details.containsKey("systemParamHex"))
        assertFalse(details.containsKey("address"))
    }
}
