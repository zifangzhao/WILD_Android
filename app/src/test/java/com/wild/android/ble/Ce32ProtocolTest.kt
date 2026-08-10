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
        assertContentEquals(byteArrayOf(0x3C, 0x94.toByte(), 0x34, 0x12, 0x3E), Ce32Protocol.buildLogBlockRequest(0x1234))
        assertContentEquals(byteArrayOf(0x3C, 0x95.toByte(), 0x07, 0x3E), Ce32Protocol.buildDeleteRecords(7))
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
        assertEquals(2, Ce32Protocol.payloadLengthFor(0xB0))
        assertEquals(2, Ce32Protocol.payloadLengthFor(0xB1))
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
    fun buildDspLiveUpdateMatchesWindowsCommandIdsForBothPipelines() {
        val dsp0 = Ce32Protocol.buildDspLiveUpdate(
            dspIndex = 0,
            maOrder = 11,
            filterType = 12,
            formula = 13,
            channels = listOf(21, 22, 23),
        )
        val dsp1 = Ce32Protocol.buildDspLiveUpdate(
            dspIndex = 1,
            maOrder = 11,
            filterType = 12,
            formula = 13,
            channels = listOf(21, 22, 23),
        )

        assertEquals(27, dsp0.size)
        assertEquals(0x22, dsp0[1].toInt() and 0xFF)
        assertEquals(11, littleEndianInt(dsp0, 2))
        assertEquals(12, littleEndianInt(dsp0, 6))
        assertEquals(13, littleEndianInt(dsp0, 10))
        assertEquals(21, littleEndianInt(dsp0, 14))
        assertEquals(22, littleEndianInt(dsp0, 18))
        assertEquals(23, littleEndianInt(dsp0, 22))
        assertEquals(0x3E, dsp0.last().toInt() and 0xFF)

        assertEquals(27, dsp1.size)
        assertEquals(0x23, dsp1[1].toInt() and 0xFF)
        assertEquals(11, littleEndianInt(dsp1, 2))
        assertEquals(12, littleEndianInt(dsp1, 6))
        assertEquals(13, littleEndianInt(dsp1, 10))
        assertEquals(21, littleEndianInt(dsp1, 14))
        assertEquals(22, littleEndianInt(dsp1, 18))
        assertEquals(23, littleEndianInt(dsp1, 22))
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
