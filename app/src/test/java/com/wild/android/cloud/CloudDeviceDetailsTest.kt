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
    @Test fun probePayloadsReceiptsAndCatalogUiDoNotCreateCloudWritesOrUploadRawConfiguration() {
        val original=session()
        val map=com.wild.android.ble.NpProbeMap(2,1,4,"local.imro","source-hash",ByteArray(876))
        val changed=original.copy(neuropixels=original.neuropixels.copy(probeMaps=listOf(map),
            catalog=com.wild.android.ble.NpCatalogSnapshot(2,1000000,8192,emptyList(),"snapshot",false)))
        assertEquals(cloudStatusFingerprint(listOf(original)),cloudStatusFingerprint(listOf(changed)))
        assertFalse(cloudDeviceDetails(changed).toString().contains("source-hash"))
        assertFalse(cloudDeviceDetails(changed).toString().contains("snapshot"))
    }
}
