package com.wild.android.cloud

import com.wild.android.ble.BleHostSessionState
import com.wild.android.ble.Ce64AdvertisementStatus
import com.wild.android.ble.DeviceSessionUiState
import com.wild.android.ble.AdvertisementStatusSampleUiState
import com.wild.android.ble.RssiSampleUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class FirebaseFleetGatewayTest {
    @Test
    fun deviceKeyIsStableButDoesNotExposeTheLocalBleIdentifier() {
        val localId = "AA:BB:CC:DD:EE:FF"

        val first = cloudDeviceKey(localId)
        val second = cloudDeviceKey(localId)

        assertEquals(first, second)
        assertEquals(24, first.length)
        assertFalse(first.contains(localId))
        assertNotEquals(first, cloudDeviceKey("11:22:33:44:55:66"))
    }

    @Test
    fun snapshotPublishesStatusButNotTheRawBleAddress() {
        val session = DeviceSessionUiState(
            id = "AA:BB:CC:DD:EE:FF",
            name = "CE64X_CCE02F232276",
            address = "AA:BB:CC:DD:EE:FF",
            traceColorArgb = 0xFF1687F2.toInt(),
            hostState = BleHostSessionState.Recording,
            rssi = -58,
            voltage = 3.74,
            usedSpaceMb = 123.5,
            recordingSeconds = 45,
            waveformPreviewActive = true,
            lastSeenAtMs = 120_000L,
            swVersion = "1.2.3",
        )

        val snapshot = cloudDeviceSnapshot(
            session = session,
            gatewayId = "gateway-1",
            gatewayLabel = "Field Phone · 0001",
            publishedAtMs = 130_000L,
        )
        val fields = snapshot.toFirestoreFields()

        assertEquals("gateway-1", snapshot.gatewayId)
        assertEquals("Field Phone · 0001", snapshot.gatewayLabel)
        assertEquals("CE64X_CCE02F232276", snapshot.displayName)
        assertTrue(snapshot.connected)
        assertTrue(snapshot.recording)
        assertTrue(snapshot.previewing)
        assertEquals(-58, snapshot.rssiDbm)
        assertFalse(snapshot.documentId.contains(session.address))
        assertFalse(fields.values.any { it == session.address })
        assertFalse(fields.containsKey("address"))
        assertEquals("Field Phone · 0001", fields["gatewayLabel"])
    }

    @Test
    fun statusFingerprintChangesWhenADeviceConnectionStateChanges() {
        val disconnected = DeviceSessionUiState(
            id = "device-a",
            name = "Device A",
            address = "AA:BB:CC:DD:EE:FF",
            traceColorArgb = 0xFF1687F2.toInt(),
            hostState = BleHostSessionState.Disconnected,
        )
        val connected = disconnected.copy(hostState = BleHostSessionState.Connected)

        assertNotEquals(
            cloudStatusFingerprint(listOf(disconnected)),
            cloudStatusFingerprint(listOf(connected)),
        )
    }

    @Test
    fun statusFingerprintIgnoresLiveScanNoiseUntilTheHealthHeartbeat() {
        val original = DeviceSessionUiState(
            id = "device-a",
            name = "Device A",
            address = "AA:BB:CC:DD:EE:FF",
            traceColorArgb = 0xFF1687F2.toInt(),
            hostState = BleHostSessionState.Recording,
            rssi = -55,
            voltage = 3.74,
            usedSpaceMb = 121.0,
            recordingSeconds = 41,
            lastSeenAtMs = 100_000L,
        )
        val scanUpdate = original.copy(
            rssi = -83,
            voltage = 3.741,
            usedSpaceMb = 122.0,
            recordingSeconds = 293,
            lastSeenAtMs = 190_000L,
        )

        assertEquals(
            cloudStatusFingerprint(listOf(original)),
            cloudStatusFingerprint(listOf(scanUpdate)),
        )
    }

    @Test
    fun snapshotFingerprintIgnoresRssiAndLastSeenNoise() {
        val original = cloudDeviceSnapshot(
            session = DeviceSessionUiState(
                id = "device-a",
                name = "Device A",
                address = "AA:BB:CC:DD:EE:FF",
                traceColorArgb = 0xFF1687F2.toInt(),
                hostState = BleHostSessionState.Connected,
                rssi = -55,
                lastSeenAtMs = 100_000L,
            ),
            gatewayId = "gateway-1",
            gatewayLabel = "Field Phone · 0001",
            publishedAtMs = 110_000L,
        )

        assertEquals(
            cloudSnapshotFingerprint(original),
            cloudSnapshotFingerprint(original.copy(rssiDbm = -90, lastSeenAtMs = 190_000L)),
        )
    }

    @Test
    fun snapshotPublishesAiAdvertisementFieldsWithoutLettingAgeCauseExtraWrites() {
        val aiAdvertisement = Ce64AdvertisementStatus(
            formatVersion = 8,
            recording = true,
            previewing = false,
            bootModuleStatusPacked = 0xFF,
            lastEventCode = 0,
            failedSubsystems = 0,
            degradedSubsystems = 0,
            batteryVoltage = null,
            storageUsedPercent = null,
            recordingSeconds = 0L,
            isAiAdvertisementPage = true,
            advertisedSampleRateHz = 30_000,
            hasAiResult = true,
            aiResultIsNew = true,
            aiModelId = 4,
            aiClassId = 9,
            aiConfidencePercentage = 94,
            aiEventSequence = 17,
            aiResultAgeSeconds = 1,
        )
        val session = DeviceSessionUiState(
            id = "device-ai",
            name = "Device AI",
            address = "AA:BB:CC:DD:EE:FF",
            traceColorArgb = 0xFF1687F2.toInt(),
            advertisedHealthStatus = aiAdvertisement,
            lastSeenAtMs = 100_000L,
            lastAiAdvertisementAtMs = 100_000L,
            lastAiResultAtMs = 100_000L,
        )

        val snapshot = cloudDeviceSnapshot(session, "gateway-1", "Field Phone", 110_000L)
        val fields = snapshot.toFirestoreFields()

        assertEquals(30_000, fields["advertisedSampleRateHz"])
        assertEquals(4, fields["aiModelId"])
        assertEquals(94, fields["aiConfidencePercentage"])
        assertEquals(100_000L, fields["aiResultAdvertisedAtMs"])
        assertEquals(
            cloudSnapshotFingerprint(snapshot),
            cloudSnapshotFingerprint(snapshot.copy(aiResultAgeSeconds = 2)),
        )
        assertEquals(cloudSnapshotFingerprint(snapshot), cloudSnapshotFingerprint(snapshot.copy(
            aiClassId = 2, aiConfidencePercentage = 80, aiEventSequence = 18, aiResultIsNew = false)))
        val heartbeat = cloudDeviceSnapshot(session.copy(lastSeenAtMs = 200_000L), "gateway-1", "Field Phone", 210_000L)
        assertEquals(100_000L, heartbeat.aiResultAdvertisedAtMs, "A later name-only advertisement is not a new AI observation")
        val noResult = cloudDeviceSnapshot(session.copy(
            advertisedHealthStatus = aiAdvertisement.copy(hasAiResult = false, aiModelId = null),
            lastAiAdvertisementAtMs = 200_000L), "gateway-1", "Field Phone", 210_000L)
        assertEquals(false, noResult.toFirestoreFields()["aiHasResult"])
    }

    @Test
    fun cloudPublishingKeepsActiveDevicesButDropsStaleAdvertisements() {
        val passiveAdvertisement = DeviceSessionUiState(
            id = "device-a",
            name = "Device A",
            address = "AA:BB:CC:DD:EE:FF",
            traceColorArgb = 0xFF1687F2.toInt(),
            namePrefixMatch = true,
            lastSeenAtMs = 100_000L,
        )
        val connectedWithoutAdvertisement = passiveAdvertisement.copy(
            advertisedServiceMatch = false,
            hostState = BleHostSessionState.Connected,
            lastSeenAtMs = 0L,
        )

        assertTrue(shouldPublishCloudSession(passiveAdvertisement, 105_000L))
        assertFalse(shouldPublishCloudSession(passiveAdvertisement, 100_000L + CloudAdvertisementFreshMs + 1L))
        assertTrue(shouldPublishCloudSession(connectedWithoutAdvertisement, 0L))
    }

    @Test
    fun cloudHeartbeatsFavorConnectedDeviceFreshness() {
        val passiveAdvertisement = DeviceSessionUiState(
            id = "device-a",
            name = "Device A",
            address = "AA:BB:CC:DD:EE:FF",
            traceColorArgb = 0xFF1687F2.toInt(),
            namePrefixMatch = true,
            lastSeenAtMs = 100_000L,
        )
        val connected = passiveAdvertisement.copy(hostState = BleHostSessionState.Connected)

        assertEquals(PassiveAdvertisementCloudHeartbeatMs, cloudHeartbeatIntervalMs(passiveAdvertisement))
        assertEquals(ActiveCloudHeartbeatMs, cloudHeartbeatIntervalMs(connected))
    }

    @Test
    fun historySamplesUseCompleteAdvertisementsAndDoNotExposeBleAddresses() {
        val advertisement = AdvertisementStatusSampleUiState(
            timestampMs = 125_400L,
            voltage = 3.74,
            recording = true,
            previewing = false,
            failedSubsystems = 0,
            degradedSubsystems = 0,
            storageUsedPercent = 42,
            recordingSeconds = 72L,
            lastEventCode = 0,
        )
        val session = DeviceSessionUiState(
            id = "AA:BB:CC:DD:EE:FF",
            name = "CE64X_CCE02F232276",
            address = "AA:BB:CC:DD:EE:FF",
            traceColorArgb = 0xFF1687F2.toInt(),
            hostState = BleHostSessionState.Recording,
            rssi = -61,
            voltage = 3.74,
            recordingSeconds = 72,
            rssiHistory = listOf(RssiSampleUiState(timestampMs = 125_300L, valueDbm = -63)),
            advertisementHistory = listOf(advertisement),
        )

        val sample = latestCompleteAdvertisementHistorySample(session, observedAtMs = 125_678L)
            ?: error("Expected a complete advertisement history sample")
        val fields = sample.toFirestoreFields()

        assertEquals(120_000L, sample.timestampMs)
        assertEquals(125_400L, sample.sourceAdvertisementAtMs)
        assertEquals(-63, fields["r"])
        assertEquals(374, fields["v"])
        assertEquals(42, fields["p"])
        assertFalse(fields.values.any { it == session.address })
        assertFalse(fields.containsKey("address"))
    }

    @Test
    fun incompleteAdvertisementsAreNotUsedForCloudHistory() {
        val incomplete = AdvertisementStatusSampleUiState(
            timestampMs = 125_400L,
            voltage = 3.74,
            recording = true,
            previewing = false,
            failedSubsystems = 0,
            degradedSubsystems = 0,
            storageUsedPercent = 42,
            recordingSeconds = null,
            lastEventCode = 0,
        )
        val session = DeviceSessionUiState(
            id = "device-a",
            name = "CE64X_CCE02F232276",
            address = "AA:BB:CC:DD:EE:FF",
            traceColorArgb = 0xFF1687F2.toInt(),
            advertisementHistory = listOf(incomplete),
        )

        assertFalse(isCompleteCloudHistoryAdvertisement(incomplete))
        assertEquals(null, latestCompleteAdvertisementHistorySample(session, observedAtMs = 125_678L))
    }

    @Test
    fun historyDocumentsGroupSamplesByStableUtcDay() {
        val dayOneSample = cloudHistoryMinuteBucket(CloudHistoryDayMs - 1L)
        val dayTwoSample = cloudHistoryMinuteBucket(CloudHistoryDayMs + 1L)

        assertEquals(0L, cloudHistoryDayStartMs(dayOneSample))
        assertEquals(CloudHistoryDayMs, cloudHistoryDayStartMs(dayTwoSample))
        assertEquals(
            "gateway_device_${CloudHistoryDayMs}",
            cloudHistoryDocumentId("gateway_device", CloudHistoryDayMs),
        )
    }
}
