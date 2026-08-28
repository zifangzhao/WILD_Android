package com.wild.android.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Ce32ProtocolTest {
    @Test
    fun buildStreamFsCommandPadsMissingChannels() {
        val command = Ce32Protocol.buildStreamFsCommand(listOf(625, 1250, 2500))

        assertEquals(35, command.size)
        assertEquals(0x3C, command.first().toInt() and 0xFF)
        assertEquals(0x33, command[1].toInt() and 0xFF)
        assertEquals(0x3E, command.last().toInt() and 0xFF)
        assertEquals(625, littleEndianInt(command, 2))
        assertEquals(1250, littleEndianInt(command, 6))
        assertEquals(2500, littleEndianInt(command, 10))
        assertEquals(0, littleEndianInt(command, 14))
        assertEquals(0, littleEndianInt(command, 30))
    }

    @Test
    fun buildQuickFsCommandUsesLittleEndianRate() {
        val command = Ce32Protocol.buildQuickFsCommand(20_000)

        assertContentEquals(byteArrayOf(0x3C, 0x32, 0x20, 0x4E, 0x3E), command)
    }

    @Test
    fun buildFastHandshakeUsesLegacyBootstrapCommand() {
        val command = Ce32Protocol.buildFastHandshake()

        assertContentEquals(byteArrayOf(0x3C, 0x80.toByte(), 0x3E), command)
    }

    @Test
    fun lifecyclePreviewAndRecordCommandsMatchWindowsReferenceBytes() {
        assertContentEquals(byteArrayOf(0x3C, 0x89.toByte(), 0x3E), Ce32Protocol.buildSyncReset())
        assertContentEquals(byteArrayOf(0x3C, 0x82.toByte(), 0x81.toByte(), 0x00, 0x00, 0x3E), Ce32Protocol.buildSyncStart())
        assertContentEquals(byteArrayOf(0x3C, 0x40, 0x3E), Ce32Protocol.buildPreviewStart())
        assertContentEquals(byteArrayOf(0x3C, 0x41, 0x3E), Ce32Protocol.buildPreviewStop())
        assertContentEquals(
            byteArrayOf(0x3C, 0x42, 0x03, 0x3E),
            Ce32Protocol.buildPreviewSelect(PreviewSelection(auxMode = false, index = 3)),
        )
        assertContentEquals(
            byteArrayOf(0x3C, 0x42, 0x85.toByte(), 0x3E),
            Ce32Protocol.buildPreviewSelect(PreviewSelection(auxMode = true, index = 5)),
        )
        assertContentEquals(byteArrayOf(0x3C, 0x30, 0x3E), Ce32Protocol.buildRecordStart())
        assertContentEquals(byteArrayOf(0x3C, 0x31, 0x3E), Ce32Protocol.buildRecordStop())
        assertContentEquals(
            byteArrayOf(0x3C, 0x31, 0x3E, 0x3C, 0x41, 0x3E),
            Ce32Protocol.buildRecordStopAndPreviewStop(),
        )
        assertContentEquals(byteArrayOf(0x3C, 0xA1.toByte(), 0x02, 0x3E), Ce32Protocol.buildRoleSwitch(2))
        assertContentEquals(byteArrayOf(0x3C, 0xA0.toByte(), 0x0A, 0x3E), Ce32Protocol.buildEnterSleep())
        assertContentEquals(byteArrayOf(0x3C, 0xAB.toByte(), 0xBA.toByte(), 0x3E), Ce32Protocol.buildSoftwareReset())
        assertContentEquals(byteArrayOf(0x3C, 0xAC.toByte(), 0xCA.toByte(), 0x3E), Ce32Protocol.buildSystemBootloader())
        assertContentEquals(byteArrayOf(0x3C, 0xAE.toByte(), 0x3E), Ce32Protocol.buildFirmwareImageUpdate())
        assertContentEquals(
            byteArrayOf(0x3C, 0x96.toByte(), 0x00, 0x00, 0x10, 0x00, 0x00, 0x3E),
            Ce32Protocol.buildAiModuleInstall(Ce32Protocol.AiModuleEphysSlot),
        )
        assertContentEquals(
            byteArrayOf(0x3C, 0x96.toByte(), 0x01, 0x00, 0x12, 0x00, 0x00, 0x3E),
            Ce32Protocol.buildAiModuleInstall(Ce32Protocol.AiModuleImuSlot),
        )
        assertContentEquals(byteArrayOf(0x3C, 0x97.toByte(), 0x00, 0x3E), Ce32Protocol.buildAiModuleSelect(0))
        assertContentEquals(byteArrayOf(0x3C, 0x97.toByte(), 0xFF.toByte(), 0x3E), Ce32Protocol.buildAiModuleSelect(null))
        assertContentEquals(byteArrayOf(0x3C, 0x98.toByte(), 0x01, 0x3E), Ce32Protocol.buildAiRuntimeEnable(true))
        assertContentEquals(byteArrayOf(0x3C, 0x98.toByte(), 0x00, 0x3E), Ce32Protocol.buildAiRuntimeEnable(false))
        assertContentEquals(byteArrayOf(0x3C, 0x99.toByte(), 0x3E), Ce32Protocol.buildAiRuntimeStatusRequest())
        assertContentEquals(byteArrayOf(0x3C, 0x9B.toByte(), 0xFF.toByte(), 0x3E), Ce32Protocol.buildAiResidentStatusRequest())
        assertContentEquals(byteArrayOf(0x3C, 0x9B.toByte(), 0x01, 0x3E), Ce32Protocol.buildAiResidentStatusRequest(1))
        assertContentEquals(byteArrayOf(0x3C, 0x94.toByte(), 0x34, 0x12, 0x3E), Ce32Protocol.buildLogBlockRequest(0x1234))
        assertContentEquals(byteArrayOf(0x3C, 0x95.toByte(), 0x07, 0x3E), Ce32Protocol.buildDeleteRecords(7))
    }

    @Test
    fun peripheralAndTriggerControlCommandsKeepTheirWireFormat() {
        assertContentEquals(
            byteArrayOf(0x3C, 0x15, 0x34, 0x12, 0xCD.toByte(), 0xAB.toByte(), 0x3E),
            Ce32Protocol.buildCameraParamUpdate(reg0 = 0x1234, reg1 = 0xABCD),
        )
        assertContentEquals(
            byteArrayOf(0x3C, 0x15, 0x02, 0x00, 0x00, 0xA0.toByte(), 0x3F, 0x3E),
            Ce32Protocol.buildTriggerGainUpdate(channelId = 2, gain = 1.25f),
        )
        assertContentEquals(byteArrayOf(0x3C, 0x9E.toByte(), 0x00, 0x3E), Ce32Protocol.buildSnapshotRequest())
        assertContentEquals(byteArrayOf(0x3C, 0x9E.toByte(), 0x01, 0x3E), Ce32Protocol.buildSnapshotRequest(preview = true))
        assertContentEquals(byteArrayOf(0x3C, 0x9D.toByte(), 0x3E), Ce32Protocol.buildReadCameraParams())
        assertContentEquals(byteArrayOf(0x3C, 0x11, 0x01, 0x3E), Ce32Protocol.buildStimEnable(true))
        assertContentEquals(byteArrayOf(0x3C, 0x11, 0x00, 0x3E), Ce32Protocol.buildStimEnable(false))
        assertContentEquals(
            byteArrayOf(0x3C, 0x13, 0xFF.toByte(), 0x7F, 0x01, 0x3E),
            Ce32Protocol.buildStimIntensity(channelId = 1, intensityPercent = 50f),
        )
        assertContentEquals(
            byteArrayOf(0x3C, 0x14, 0x02, 0x00, 0x00, 0x20, 0x40, 0x3E),
            Ce32Protocol.buildTriggerThreshold(channelId = 2, threshold = 2.5f),
        )
        assertContentEquals(byteArrayOf(0x3C, 0x60, 0x08, 0x3E), Ce32Protocol.buildForceTrigger(3))
        assertContentEquals(byteArrayOf(0x3C, 0x61, 0x00, 0x0A, 0x3E), Ce32Protocol.buildLedCommand(true))
        assertContentEquals(byteArrayOf(0x3C, 0x61, 0x02, 0x0A, 0x3E), Ce32Protocol.buildGpio0Command(GpioMode.High))
        assertContentEquals(byteArrayOf(0x3C, 0x61, 0x03, 0x01, 0x3E), Ce32Protocol.buildGpio1Command(GpioMode.Input))
        assertContentEquals(byteArrayOf(0x3C, 0x51, 0x3E), Ce32Protocol.buildImpedanceTest())
        assertContentEquals(byteArrayOf(0x3C, 0x43, 0x83.toByte(), 0x3E), Ce32Protocol.buildTriggerWaveform(true))
        assertContentEquals(byteArrayOf(0x3C, 0x43, 0x00, 0x3E), Ce32Protocol.buildTriggerWaveform(false))
    }

    @Test
    fun parsesCe64ResidentAiStatusPackets() {
        val runtimePayload = ByteBuffer.allocate(26).order(ByteOrder.LITTLE_ENDIAN)
            .put(0x1F.toByte())
            .put(Ce32Protocol.AiModuleImuSlot.toByte())
            .putInt(-7)
            .putInt(0x2000B480)
            .putInt(0x0003C000)
            .putInt(256)
            .putInt(64)
            .putInt(1234)
            .array()
        val runtime = assertNotNull(Ce32Protocol.parseAiRuntimeStatus(runtimePayload))
        assertTrue(runtime.layoutReady)
        assertTrue(runtime.running)
        assertEquals(Ce32Protocol.AiModuleImuSlot, runtime.activeSlot)
        assertEquals(-7, runtime.statusCode)
        assertEquals(0x2000B480L, runtime.runtimeWindowAddress)
        assertEquals(1234L, runtime.executionTime)

        val slotPayload = ByteBuffer.allocate(34).order(ByteOrder.LITTLE_ENDIAN)
            .put(Ce32Protocol.AiModuleEphysSlot.toByte())
            .put(1)
            .putInt(0)
            .putInt(0x08100000)
            .putInt(0x00080000)
            .putInt(4096)
            .putInt(0x100)
            .putInt(0x2000B480)
            .putInt(1024)
            .putInt(256)
            .array()
        val slot = assertNotNull(Ce32Protocol.parseAiResidentSlotStatus(slotPayload))
        assertTrue(slot.present)
        assertEquals(Ce32Protocol.AiModuleEphysSlot, slot.slot)
        assertEquals(4096L, slot.imageSizeBytes)
        assertEquals(1024L, slot.requiredArenaBytes)
    }

    @Test
    fun build8CReplyMirrorsDeviceStampAndAddsHostStamp() {
        val requestPayload = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val hostTime = fixedTime(minute = 12, second = 34, millis = 567)

        val command = Ce32Protocol.build8CReply(requestPayload, hostTime)

        assertEquals(19, command.size)
        assertEquals(0x8C, command[1].toInt() and 0xFF)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8), command.slice(2..9).map { it.toInt() and 0xFF })
        assertEquals(12 * 60 + 34, littleEndianInt(command, 10))
        assertEquals(5670, littleEndianInt(command, 14))
        assertEquals(0x3E, command.last().toInt() and 0xFF)
    }

    @Test
    fun build8DReplyAndPatch8DReplyTxStampUseRequestedTimes() {
        val requestPayload = byteArrayOf(9, 8, 7, 6, 5, 4, 3, 2)
        val hostRx = fixedTime(minute = 1, second = 2, millis = 300)
        val hostTx = fixedTime(minute = 1, second = 3, millis = 450)
        val patchedTx = fixedTime(minute = 1, second = 4, millis = 990)

        val command = Ce32Protocol.build8DReply(requestPayload, hostRx = hostRx, hostTx = hostTx)

        assertEquals(27, command.size)
        assertEquals(0x8D, command[1].toInt() and 0xFF)
        assertEquals(listOf(9, 8, 7, 6, 5, 4, 3, 2), command.slice(2..9).map { it.toInt() and 0xFF })
        assertEquals(62, littleEndianInt(command, 10))
        assertEquals(3000, littleEndianInt(command, 14))
        assertEquals(63, littleEndianInt(command, 18))
        assertEquals(4500, littleEndianInt(command, 22))

        Ce32Protocol.patch8DReplyTxStamp(command, patchedTx)
        assertEquals(64, littleEndianInt(command, 18))
        assertEquals(9900, littleEndianInt(command, 22))
    }

    @Test
    fun parsePreviewPacketDecodesSamplesVoltageSpaceAndFlags() {
        val payload = ByteArray(136)
        putU16(payload, 0, 0x8000)
        putU32(payload, 2, 2048)
        payload[6] = 0x03
        payload[7] = 0xA0.toByte()
        repeat(64) { index ->
            putI16(payload, 8 + index * 2, index - 32)
        }

        val packet = Ce32Protocol.parsePreviewPacket(payload)

        assertNotNull(packet)
        assertEquals(64, packet.samples.size)
        assertEquals(-32f, packet.samples.first())
        assertEquals(31f, packet.samples.last())
        assertTrue(packet.voltage > 6.5)
        assertEquals(1.0, packet.usedSpaceMb, 1e-9)
        assertTrue(packet.digitalFlags.first { it.name == "DET1" }.active)
        assertTrue(packet.digitalFlags.first { it.name == "STIM1" }.active)
        assertTrue(packet.digitalFlags.first { it.name == "DSP_READY" }.active)
        assertFalse(packet.digitalFlags.first { it.name == "DET2" }.active)
    }

    @Test
    fun parseRecordingTelemetryAndStartEventsRejectMalformedDates() {
        val telemetryPayload = ByteArray(10)
        putU32(telemetryPayload, 0, 3_600)
        putU16(telemetryPayload, 4, 0x8000)
        putU32(telemetryPayload, 6, 2_048)

        val telemetry = Ce32Protocol.parseRecTimePacket(telemetryPayload)
        assertNotNull(telemetry)
        assertEquals(3_600L, telemetry.recordingSeconds)
        assertTrue(assertNotNull(telemetry.voltage) > 6.5)
        assertEquals(1.0, assertNotNull(telemetry.usedSpaceMb), 1e-9)

        val startPayload = ByteArray(25).apply {
            this[1] = 7
            this[2] = 14
            this[3] = 26
            this[4] = 12
            this[5] = 34
            this[6] = 56
            this[24] = 3
        }
        val start = Ce32Protocol.parseRecordStartEvent(startPayload)
        assertNotNull(start)
        assertEquals(2026, start.year)
        assertEquals(7, start.month)
        assertEquals(14, start.day)
        assertEquals(12, start.hour)
        assertEquals(34, start.minute)
        assertEquals(56, start.second)
        assertEquals(3, start.eventCode)

        startPayload[1] = 0
        val malformed = Ce32Protocol.parseRecordStartEvent(startPayload)
        assertNotNull(malformed)
        assertNull(malformed.year)
        assertNull(malformed.month)
        assertEquals(3, malformed.eventCode)
    }

    @Test
    fun parseSyncStatusHandlesFullSyncMetricPacket() {
        val payload = ByteBuffer.allocate(15).order(ByteOrder.LITTLE_ENDIAN)
            .put(0x01)
            .putFloat(1.25f)
            .putFloat(0.5f)
            .putFloat(0.125f)
            .putShort(7.toShort())
            .array()

        val sync = Ce32Protocol.parseSyncStatus(payload)

        assertNotNull(sync)
        assertEquals(0x01, sync.mode)
        assertEquals(1.25f, sync.offsetSeconds)
        assertEquals(0.5f, sync.accuracySeconds)
        assertEquals(0.125f, sync.delaySeconds)
        assertEquals(7, sync.sampleCount)
        assertNull(sync.hostRxSeconds)
    }

    @Test
    fun packedLiveSyncTimePreservesDeviceClockAndRequestedDelay() {
        val hostTime = fixedTime(minute = 1, second = 2, millis = 345)
        val outbound = Ce32Protocol.buildPackedTimeMeasurement(delayMs = 250, now = hostTime)
        assertEquals(0x8F, outbound[1].toInt() and 0xFF)
        assertEquals(7, outbound.size)

        val packed = ByteBuffer.wrap(outbound, 2, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val expectedClockMs = (hostTime.toLocalTime().toNanoOfDay() / 1_000_000L).toInt() and 0xFFFFF
        assertEquals(expectedClockMs, packed and 0xFFFFF)
        assertEquals(250, packed ushr 20)

        val inbound = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(123_456 or (375 shl 20))
            .array()
        val sync = Ce32Protocol.parseSyncStatus(inbound)
        assertNotNull(sync)
        assertEquals(0x03, sync.mode)
        assertEquals(123.456f, sync.offsetSeconds)
        assertEquals(0.375f, sync.delaySeconds)
        assertEquals(0, sync.sampleCount)
        assertNotNull(sync.hostRxSeconds)
    }

    @Test
    fun parseCameraRowRejectsOutOfRangeRowsAndDecodesPreviewRows() {
        val goodPayload = byteArrayOf(3) + ByteArray(Ce32Protocol.CameraPreviewPixels) { index -> index.toByte() }
        val goodRow = Ce32Protocol.parseCameraRow(Ce32Protocol.CameraPreviewCommand, goodPayload)

        assertNotNull(goodRow)
        assertTrue(goodRow.isPreview)
        assertEquals(3, goodRow.row)
        assertEquals(Ce32Protocol.CameraPreviewPixels, goodRow.bytes.size)
        assertEquals(0, goodRow.bytes.first().toInt() and 0xFF)
        assertEquals(Ce32Protocol.CameraPreviewPixels - 1, goodRow.bytes.last().toInt() and 0xFF)

        val badPayload = byteArrayOf(Ce32Protocol.CameraPreviewPixels.toByte()) +
            ByteArray(Ce32Protocol.CameraPreviewPixels)
        assertNull(Ce32Protocol.parseCameraRow(Ce32Protocol.CameraPreviewCommand, badPayload))
    }

    @Test
    fun parseCameraStimAndDeleteReadbacksKeepTheirFieldsSeparated() {
        val camera = Ce32Protocol.parseCameraParams(
            byteArrayOf(0x34, 0x12, 0xCD.toByte(), 0xAB.toByte()),
        )
        assertNotNull(camera)
        assertEquals(0x1234, camera.reg0)
        assertEquals(0xABCD, camera.reg1)

        val stimPayload = ByteArray(60)
        putU32(stimPayload, 0, 101)
        putU32(stimPayload, 4, 102)
        putU32(stimPayload, 8, 103)
        putU32(stimPayload, 12, 104)
        putU32(stimPayload, 16, 105)
        ByteBuffer.wrap(stimPayload, 20, 4).order(ByteOrder.LITTLE_ENDIAN).putFloat(1.25f)
        ByteBuffer.wrap(stimPayload, 24, 4).order(ByteOrder.LITTLE_ENDIAN).putFloat(2.5f)
        ByteBuffer.wrap(stimPayload, 28, 4).order(ByteOrder.LITTLE_ENDIAN).putFloat(3.75f)
        putU32(stimPayload, 32, 106)
        putU32(stimPayload, 36, 107)
        putU32(stimPayload, 40, 108)
        putU32(stimPayload, 44, 109)
        putU32(stimPayload, 48, 110)
        putU32(stimPayload, 52, 111)
        putU32(stimPayload, 56, 7)

        val stim = Ce32Protocol.parseStimControl(stimPayload)
        assertNotNull(stim)
        assertEquals(7, stim.id)
        assertEquals(101, stim.triggerDelayThis)
        assertEquals(2.5f, stim.triggerGain)
        assertEquals(3.75f, stim.triggerMean)
        assertEquals(108, stim.triggerState)
        assertEquals(110, stim.stimCount)
        assertEquals(111, stim.count)

        assertEquals(0x12345678L, Ce32Protocol.parseDeleteAck(byteArrayOf(0x78, 0x56, 0x34, 0x12)))
    }

    @Test
    fun parseShortListsKeepUnsignedMagnitudeAndSignedPhaseSeparate() {
        val magnitudePayload = byteArrayOf(0x34, 0x12, 0xFF.toByte(), 0x7F)
        val phasePayload = byteArrayOf(0x34, 0x12, 0x00, 0x80.toByte(), 0xFF.toByte(), 0xFF.toByte())

        val magnitude = Ce32Protocol.parseUnsignedShortList(magnitudePayload)
        val phase = Ce32Protocol.parseSignedShortList(phasePayload)

        assertEquals(listOf(0x1234, 0x7FFF), magnitude)
        assertEquals(listOf(0x1234, -32768, -1), phase)
    }

    @Test
    fun payloadLengthAndVersionHelpersMatchCurrentAssumptions() {
        assertEquals(24, Ce32Protocol.payloadLengthFor(0x8D))
        assertEquals(16, Ce32Protocol.payloadLengthFor(0xB0))
        assertEquals(15, Ce32Protocol.payloadLengthFor(0xB1))
        assertEquals(84, Ce32Protocol.payloadLengthFor(0xB2))
        assertEquals(3, Ce32Protocol.payloadLengthFor(0xB3))
        assertEquals(16, Ce32Protocol.payloadLengthFor(Ce64BleOtaProtocol.CommandBegin))
        assertEquals(16, Ce32Protocol.payloadLengthFor(Ce64BleOtaProtocol.CommandWrite))
        assertEquals(16, Ce32Protocol.payloadLengthFor(Ce64BleOtaProtocol.CommandFinish))
        assertEquals(16, Ce32Protocol.payloadLengthFor(Ce64BleOtaProtocol.CommandStatus))
        assertEquals(16, Ce32Protocol.payloadLengthFor(Ce64BleOtaProtocol.CommandInstall))
        assertEquals(512, Ce32Protocol.payloadLengthFor(0x9C))
        assertEquals(14, Ce32Protocol.payloadLengthFor(0x9A))
        assertEquals(32, Ce32Protocol.payloadLengthFor(Ce32Protocol.SchedulerEventDiagnostic))
        assertEquals(1, Ce32Protocol.payloadLengthFor(0xC0))
        assertEquals(0, Ce32Protocol.payloadLengthFor(0x40))
        assertEquals(0, Ce32Protocol.payloadLengthFor(0x41))
        assertEquals(1, Ce32Protocol.payloadLengthFor(0x42))
        assertEquals(512, Ce32Protocol.payloadLengthFor(0xF0))
        assertEquals(512, Ce32Protocol.payloadLengthFor(0xF4))
        assertEquals(1 + Ce32Protocol.CameraPreviewPixels, Ce32Protocol.payloadLengthFor(Ce32Protocol.CameraPreviewCommand))
        assertNull(Ce32Protocol.payloadLengthFor(0xFF))
        assertTrue(Ce32Protocol.isLegacyWakeFirmware("3.1.0"))
        assertFalse(Ce32Protocol.isLegacyWakeFirmware("3.2.0"))
        assertTrue(Ce32Protocol.matchesKnownNamePrefix("CE32_host"))
        assertTrue(Ce32Protocol.matchesKnownNamePrefix("CE64_alpha"))
        assertTrue(Ce32Protocol.matchesKnownNamePrefix("CE128_beta"))
        assertTrue(Ce32Protocol.matchesKnownNamePrefix("WILD_node"))
        assertTrue(Ce32Protocol.matchesKnownNamePrefix("XENP_probe"))
        assertFalse(Ce32Protocol.matchesKnownNamePrefix("mystery-ble"))
    }

    @Test
    fun spikeAndSpectrumBuildersMatchCe64WireLayouts() {
        val spike = SpikeDetectorConfigUiState(
            enabled = true,
            channelEnableMask = 0x11L,
            positivePolarityMask = 0x10L,
            thresholds = List(64) { if (it == 4) 321 else 120 },
            confirmationTag = 7,
        )
        val spikeCommand = Ce32Protocol.buildSpikeDetectorConfig(spike)

        assertEquals(515, spikeCommand.size)
        assertEquals(0x24, spikeCommand[1].toInt() and 0xFF)
        assertEquals(0x3E, spikeCommand.last().toInt() and 0xFF)
        val decoded = assertNotNull(Ce32Protocol.parseSpikeDetectorConfig(spikeCommand.copyOfRange(2, 514)))
        assertTrue(decoded.enabled)
        assertTrue(decoded.channelEnabled(0))
        assertTrue(decoded.channelEnabled(4))
        assertTrue(decoded.positivePolarity(4))
        assertEquals(321, decoded.thresholds[4])
        assertEquals(7, decoded.confirmationTag)

        val spectrum = Ce32Protocol.buildSpectrumConfig(
            SpectrumConfigUiState(
                enabled = true,
                dwtProfileEnabled = true,
                source = Ce32Protocol.SpectrumSourceEphys,
                channel = 31,
                firstBin = 1,
                lastBin = 63,
                periodMs = 200,
            ),
        )
        assertContentEquals(
            byteArrayOf(0x3C, 0x25, 0x04, 0x03, 0x10, 0x01, 0x1F, 0x01, 0x00, 0x3F, 0x00, 0xC8.toByte(), 0x00, 0x3E),
            spectrum,
        )
    }

    @Test
    fun schedulerRuleBuilderAndStatusParserMatchVersionTwoLayout() {
        val rule = SchedulerRuleUiState(
            id = 2,
            enabled = true,
            trigger = 0,
            action = 0,
            profileId = 0xFF,
            timeOfDaySeconds = 36_000,
            durationSeconds = 1_800,
            evaluationSeconds = 60,
            debounceCount = 1,
            maxDeferrals = 1,
        )
        val command = Ce32Protocol.buildSchedulerRuleUpdate(rule)
        assertEquals(51, command.size)
        assertEquals(Ce32Protocol.SchedulerCommandSetRule, command[1].toInt() and 0xFF)
        assertEquals(2, command[2].toInt() and 0xFF)
        assertEquals(1, command[3].toInt() and 0xFF)
        assertEquals(0xA0, command[16].toInt() and 0xFF) // 36,000 LE at rule offset 14
        assertEquals(0x8C, command[17].toInt() and 0xFF)

        val status = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
            .put(2)
            .put(1)
            .put(1)
            .put(0)
            .putInt(9)
            .putInt(1234)
            .put(3)
            .put(0)
            .put(1)
            .put(0xFF.toByte())
            .putInt(100)
            .putInt(2)
            .putInt(3)
            .putInt(4)
            .array()
        val decoded = assertNotNull(Ce32Protocol.parseSchedulerStatus(status))
        assertTrue(decoded.enabled)
        assertTrue(decoded.clockValid)
        assertEquals(9, decoded.generation)
        assertEquals(1234, decoded.nextWakeSeconds)
        assertEquals(3, decoded.lastRuleId)
        assertEquals(4, decoded.conflictCount)
    }

    @Test
    fun sharedServiceWithoutAWildIdentityIsNotEligibleForConnection() {
        val sharedServiceOnly = DeviceSessionUiState(
            id = "test-device",
            name = "sps",
            address = "AA:BB:CC:DD:EE:FF",
            traceColorArgb = 0xFF1687F2.toInt(),
            advertisedServiceMatch = true,
        )

        assertFalse(sharedServiceOnly.bulkConnectEligible)
        assertTrue(sharedServiceOnly.copy(namePrefixMatch = true).bulkConnectEligible)
    }

    @Test
    fun buildSystemParamUploadRoundTripsKeyControlFields() {
        val params = sampleSystemParams()
        val command = Ce32Protocol.buildSystemParamUpload(ByteArray(512), params)

        assertEquals(515, command.size)
        assertEquals(0x3C, command.first().toInt() and 0xFF)
        assertEquals(0x01, command[1].toInt() and 0xFF)
        assertEquals(0x3E, command.last().toInt() and 0xFF)

        val parsed = Ce32Protocol.parseSystemParams(command.copyOfRange(2, command.lastIndex))

        assertNotNull(parsed)
        assertEquals(params.fs, parsed.fs)
        assertEquals(params.auxMode, parsed.auxMode)
        assertEquals(params.channelCounts, parsed.channelCounts)
        assertEquals(params.samplingRates, parsed.samplingRates)
        assertEquals(params.stimMode, parsed.stimMode)
        assertEquals(params.closedLoopMode, parsed.closedLoopMode)
        assertEquals(params.triggerTrainStart, parsed.triggerTrainStart)
        assertEquals(params.triggerTrainDuration, parsed.triggerTrainDuration)
        assertEquals(params.previewChannelBankRaw, parsed.previewChannelBankRaw)
        assertEquals(params.miscRatio, parsed.miscRatio)
        assertEquals(params.previewRatio, parsed.previewRatio)
        assertEquals(params.miscInterval, parsed.miscInterval)
        assertEquals(params.randomTriggerMin, parsed.randomTriggerMin)
        assertEquals(params.randomTriggerMax, parsed.randomTriggerMax)
        assertEquals(params.baseFs, parsed.baseFs)
        assertEquals(params.stimIntensities, parsed.stimIntensities)
        assertEquals(params.stimChannels, parsed.stimChannels)
        assertEquals(params.triggerGains, parsed.triggerGains)
        assertEquals(params.clParam1, parsed.clParam1)
        assertEquals(params.clParam2, parsed.clParam2)
    }

    @Test
    fun buildDspParamUploadRoundTripsChannelsAndCoefficients() {
        val params = ParsedDspParams(
            formula = 12,
            filterType = 3,
            func2 = 9,
            maOrder = 21,
            channels = listOf(7, 5, 3, 1),
        )

        val command = Ce32Protocol.buildDspParamUpload(dspIndex = 1, basePayload = ByteArray(512), params = params)

        assertEquals(515, command.size)
        assertEquals(0x3C, command.first().toInt() and 0xFF)
        assertEquals(0x03, command[1].toInt() and 0xFF)
        assertEquals(0x3E, command.last().toInt() and 0xFF)

        val parsed = Ce32Protocol.parseDspParams(command.copyOfRange(2, command.lastIndex))

        assertNotNull(parsed)
        assertEquals(params.formula, parsed.formula)
        assertEquals(params.filterType, parsed.filterType)
        assertEquals(params.func2, parsed.func2)
        assertEquals(params.maOrder, parsed.maOrder)
        assertEquals(params.channels, parsed.channels)
    }

    @Test
    fun buildStimParamUpdateMatchesWindowsCommandIdsForBothChannels() {
        val left = Ce32Protocol.buildStimParamUpdate(
            channelId = 0,
            delayUnits = 1.2f,
            randomDelayUnits = 3.4f,
            durationUnits = 5.6f,
            intervalUnits = 7.8f,
            cycles = 9,
        )
        val right = Ce32Protocol.buildStimParamUpdate(
            channelId = 1,
            delayUnits = 1.2f,
            randomDelayUnits = 3.4f,
            durationUnits = 5.6f,
            intervalUnits = 7.8f,
            cycles = 9,
        )

        assertEquals(23, left.size)
        assertEquals(0x20, left[1].toInt() and 0xFF)
        assertEquals(12, littleEndianInt(left, 2))
        assertEquals(34, littleEndianInt(left, 6))
        assertEquals(56, littleEndianInt(left, 10))
        assertEquals(78, littleEndianInt(left, 14))
        assertEquals(9, littleEndianInt(left, 18))
        assertEquals(0x3E, left.last().toInt() and 0xFF)

        assertEquals(24, right.size)
        assertEquals(0x21, right[1].toInt() and 0xFF)
        assertEquals(12, littleEndianInt(right, 2))
        assertEquals(34, littleEndianInt(right, 6))
        assertEquals(56, littleEndianInt(right, 10))
        assertEquals(78, littleEndianInt(right, 14))
        assertEquals(9, littleEndianInt(right, 18))
        assertEquals(0x00, right[right.lastIndex - 1].toInt() and 0xFF)
        assertEquals(0x3E, right.last().toInt() and 0xFF)
    }

    @Test
    fun buildDspLiveUpdateCarriesAllFourChannelsForBothPipelines() {
        val dsp0 = Ce32Protocol.buildDspLiveUpdate(
            dspIndex = 0,
            maOrder = 11,
            filterType = 12,
            formula = 13,
            channels = listOf(21, 22, 23, 24),
        )
        val dsp1 = Ce32Protocol.buildDspLiveUpdate(
            dspIndex = 1,
            maOrder = 11,
            filterType = 12,
            formula = 13,
            channels = listOf(21, 22, 23, 24),
        )

        assertEquals(31, dsp0.size)
        assertEquals(0x22, dsp0[1].toInt() and 0xFF)
        assertEquals(11, littleEndianInt(dsp0, 2))
        assertEquals(12, littleEndianInt(dsp0, 6))
        assertEquals(13, littleEndianInt(dsp0, 10))
        assertEquals(21, littleEndianInt(dsp0, 14))
        assertEquals(22, littleEndianInt(dsp0, 18))
        assertEquals(23, littleEndianInt(dsp0, 22))
        assertEquals(24, littleEndianInt(dsp0, 26))
        assertEquals(0x3E, dsp0.last().toInt() and 0xFF)

        assertEquals(31, dsp1.size)
        assertEquals(0x23, dsp1[1].toInt() and 0xFF)
        assertEquals(11, littleEndianInt(dsp1, 2))
        assertEquals(12, littleEndianInt(dsp1, 6))
        assertEquals(13, littleEndianInt(dsp1, 10))
        assertEquals(21, littleEndianInt(dsp1, 14))
        assertEquals(22, littleEndianInt(dsp1, 18))
        assertEquals(23, littleEndianInt(dsp1, 22))
        assertEquals(24, littleEndianInt(dsp1, 26))
        assertEquals(0x3E, dsp1.last().toInt() and 0xFF)
    }

    @Test
    fun previewSelectionUsesRealAuxLabelsAndChannelCountNormalization() {
        val compact = PreviewSelection(auxMode = true, index = 4)
        val extended = PreviewSelection(auxMode = true, index = 9)
        val ephys = PreviewSelection(auxMode = false, index = 63)

        assertEquals("Vbat", compact.label(ephysChannelCount = 32))
        assertEquals("Magnetic Z", extended.label(ephysChannelCount = 64))
        assertEquals(63, ephys.normalizedForDevice(64).index)
        assertEquals(31, PreviewSelection(auxMode = false, index = 63).normalizedForDevice(32).index)
    }

    private fun fixedTime(minute: Int, second: Int, millis: Int): ZonedDateTime {
        return ZonedDateTime.of(2026, 6, 22, 10, minute, second, millis * 1_000_000, ZoneId.of("UTC"))
    }

    private fun sampleSystemParams(): ParsedSystemParams {
        return ParsedSystemParams(
            fs = 20_000,
            auxMode = 2,
            channelCounts = listOf(64, 0, 0, 0, 0, 0, 0, 0),
            samplingRates = listOf(20_000, 16, 160_000, 0, 0, 0, 0, 0),
            stimMode = 1,
            closedLoopMode = 4,
            stimIntervals = listOf(10, 20, 30, 40),
            pulseWidths = listOf(11, 21, 31, 41),
            pulseCounts = listOf(12, 22, 32, 42),
            stimDelays = listOf(13, 23, 33, 43),
            stimRandomDelays = listOf(14, 24, 34, 44),
            triggerTrainStart = 120,
            triggerTrainDuration = 240,
            triggerGains = listOf(1.5f, 2.5f, 3.5f, 4.5f),
            previewChannelBankRaw = 0x81,
            systemStatus = 0,
            stimIntensities = listOf(111, 222, 333, 444),
            stimChannels = listOf(1, 2, 3, 0),
            miscRatio = 5,
            previewRatio = 6,
            miscInterval = 250,
            errorCode = 0,
            firmwareVersion = 0,
            hardwareVersion = 0,
            randomTriggerMin = 345,
            randomTriggerMax = 678,
            clParam1 = listOf(0.1f, 0.2f, 0.3f, 0.4f),
            clParam2 = listOf(1.1f, 1.2f, 1.3f, 1.4f),
            baseFs = 625,
            vbattThresholdRaw = 2750,
            audioRatio = 17,
            cameraRatio = 19,
        )
    }

    private fun littleEndianInt(bytes: ByteArray, offset: Int): Int {
        return ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int
    }

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        ByteBuffer.wrap(bytes, offset, 2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort())
    }

    private fun putI16(bytes: ByteArray, offset: Int, value: Int) {
        ByteBuffer.wrap(bytes, offset, 2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort())
    }

    private fun putU32(bytes: ByteArray, offset: Int, value: Int) {
        ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(value)
    }
}
