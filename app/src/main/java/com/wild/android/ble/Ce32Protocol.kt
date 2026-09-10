package com.wild.android.ble

import android.bluetooth.le.ScanRecord
import android.os.ParcelUuid
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.ZonedDateTime
import java.util.UUID

object Ce32Protocol {
    const val MaxChannelGroup = 8
    const val CameraStreamRateHz = 16
    const val AdcStreamRateHz = 160000

    val ServiceUuid: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
    val RxUuid: UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
    val TxUuid: UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")
    val LegacyTxUuid: UUID = UUID.fromString("0000fff3-0000-1000-8000-00805f9b34fb")
    val DeviceInfoServiceUuid: UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
    val SwVersionUuid: UUID = UUID.fromString("00002a28-0000-1000-8000-00805f9b34fb")
    val HwVersionUuid: UUID = UUID.fromString("00002a27-0000-1000-8000-00805f9b34fb")

    val ServiceParcelUuid: ParcelUuid = ParcelUuid(ServiceUuid)
    private val acceptedPrefixes = listOf("CE32", "CE64", "CE128", "WILD", "XENP")
    // Match the CE32 online-console menu and the 0x32 (quick FS) command.
    // FS = 0 is an intentional acquisition-off value, not an invalid rate.
    val CommonEphysRates = listOf(0, 1250, 2500, 5000, 10000, 20000)

    const val CameraSnapshotCommand = 0xAC
    const val CameraPreviewCommand = 0x9F
    const val CameraSnapshotPixels = 160
    const val CameraPreviewPixels = 40
    const val AiModuleEphysSlot = 0
    const val AiModuleImuSlot = 1
    const val AiModuleEphysStagingBlock = 0x1000
    const val AiModuleImuStagingBlock = 0x1200
    const val SpikeDetectorChannelCount = 64
    const val SpikeDetectorParamBytes = 512
    const val SpikeDetectorSupportedRateHz = 20_000
    const val SpectrumProtocolVersion = 4
    const val SpectrumDisplayBinCount = 16
    const val SpectrumSourceAdc = 0
    const val SpectrumSourceEphys = 1
    const val SchedulerRuleCount = 8
    const val SchedulerRuleBytes = 48
    const val SchedulerConfigBytes = 472
    const val SchedulerProfileChunkBytes = 32
    const val SchedulerProfileChunkCount = 16
    const val SchedulerCommandStatus = 0xD0
    const val SchedulerCommandList = 0xD1
    const val SchedulerCommandSetRule = 0xD2
    const val SchedulerCommandEnableRule = 0xD3
    const val SchedulerCommandClearRule = 0xD4
    const val SchedulerCommandClearAll = 0xD5
    const val SchedulerCommandGlobalEnable = 0xD6
    const val SchedulerEventDiagnostic = 0xD7
    const val SchedulerResponse = 0xD8
    const val SchedulerCommandProfileRead = 0xD9
    const val SchedulerCommandProfileWrite = 0xDB
    private const val PackedTime20Mask = 0x000FFFFF
    private const val PackedDelay12Max = 0x0FFF
    const val SectorData = 0x2000L
    const val BleLogEntriesPerBlock = 63
    const val BleLogBlockScanLimit = 1024
    private const val AdTypeIncomplete16BitUuid = 0x02
    private const val AdTypeComplete16BitUuid = 0x03
    private const val AdTypeIncomplete32BitUuid = 0x04
    private const val AdTypeComplete32BitUuid = 0x05
    private const val AdTypeIncomplete128BitUuid = 0x06
    private const val AdTypeComplete128BitUuid = 0x07

    fun hasExpectedService(record: ScanRecord?): Boolean {
        if (record == null) {
            return false
        }

        return hasExpectedService(record.serviceUuids ?: emptyList()) ||
            hasExpectedService(record.bytes)
    }

    fun hasExpectedService(advertisedServiceUuids: List<ParcelUuid>): Boolean {
        return advertisedServiceUuids.any { it.uuid == ServiceUuid }
    }

    internal fun hasExpectedService(scanRecordBytes: ByteArray?): Boolean {
        if (scanRecordBytes == null || scanRecordBytes.isEmpty()) {
            return false
        }

        var index = 0
        while (index < scanRecordBytes.size) {
            val sectionLength = scanRecordBytes[index].toInt() and 0xFF
            if (sectionLength == 0) {
                break
            }

            val typeIndex = index + 1
            val payloadStart = index + 2
            val payloadEnd = index + 1 + sectionLength
            if (typeIndex >= scanRecordBytes.size || payloadEnd > scanRecordBytes.size) {
                break
            }

            when (scanRecordBytes[typeIndex].toInt() and 0xFF) {
                AdTypeIncomplete16BitUuid, AdTypeComplete16BitUuid -> {
                    var offset = payloadStart
                    while (offset + 1 < payloadEnd) {
                        val candidate = ByteBuffer.wrap(scanRecordBytes, offset, 2)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .short.toInt() and 0xFFFF
                        if (candidate == 0xFFF0) {
                            return true
                        }
                        offset += 2
                    }
                }

                AdTypeIncomplete32BitUuid, AdTypeComplete32BitUuid -> {
                    var offset = payloadStart
                    while (offset + 3 < payloadEnd) {
                        val candidate = ByteBuffer.wrap(scanRecordBytes, offset, 4)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .int.toLong() and 0xFFFF_FFFFL
                        if (uuidFromShortForm(candidate) == ServiceUuid) {
                            return true
                        }
                        offset += 4
                    }
                }

                AdTypeIncomplete128BitUuid, AdTypeComplete128BitUuid -> {
                    var offset = payloadStart
                    while (offset + 15 < payloadEnd) {
                        if (uuidFromLittleEndian(scanRecordBytes, offset) == ServiceUuid) {
                            return true
                        }
                        offset += 16
                    }
                }
            }

            index = payloadEnd
        }

        return false
    }

    fun matchesKnownNamePrefix(name: String?): Boolean {
        val trimmed = name?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            return false
        }

        return acceptedPrefixes.any { prefix ->
            trimmed.startsWith(prefix, ignoreCase = true)
        }
    }

    fun payloadLengthFor(commandId: Int): Int? {
        return when (commandId) {
            0x10 -> 60
            0x30, 0x31, 0x40, 0x41, 0x8B -> 0
            0x42 -> 1
            0x51 -> 128
            0x52 -> 130
            0x53 -> 128
            0x82 -> 15
            0x85 -> 32
            0x8C -> 16
            0x8D -> 24
            0x8E -> 25
            0x8F -> 4
            0x90, 0x91, 0x92, 0x9C -> 512
            0x94 -> 512
            0x95 -> 4
            0x9A -> 14
            0x9D -> 4
            CameraSnapshotCommand -> 1 + CameraSnapshotPixels
            CameraPreviewCommand -> 1 + CameraPreviewPixels
            0xAD -> 136
            0xA2 -> 512
            0xAE -> 512
            0xAF -> 10
            0x99 -> 26
            0x9B -> 34
            0xB0 -> 16
            0xB1 -> 15
            0xB2 -> 84
            0xB3 -> 3
            Ce64BleOtaProtocol.CommandBegin,
            Ce64BleOtaProtocol.CommandWrite,
            Ce64BleOtaProtocol.CommandFinish,
            Ce64BleOtaProtocol.CommandStatus,
            Ce64BleOtaProtocol.CommandInstall -> Ce64BleOtaProtocol.ReplyPayloadBytes
            SchedulerEventDiagnostic -> 32
            0xC0 -> 1
            0xF0, 0xF1, 0xF2, 0xF3, 0xF4 -> 512
            0xEE -> 3
            else -> null
        }
    }

    fun buildPreviewStart(): ByteArray = byteArrayOf(0x3C.toByte(), 0x40.toByte(), 0x3E.toByte())

    fun buildPreviewStop(): ByteArray = byteArrayOf(0x3C.toByte(), 0x41.toByte(), 0x3E.toByte())

    fun buildPreviewSelect(selection: PreviewSelection): ByteArray =
        byteArrayOf(0x3C.toByte(), 0x42.toByte(), selection.toProtocolByte(), 0x3E.toByte())

    fun buildRecordStart(): ByteArray = byteArrayOf(0x3C.toByte(), 0x30.toByte(), 0x3E.toByte())

    fun buildRecordStop(): ByteArray = byteArrayOf(0x3C.toByte(), 0x31.toByte(), 0x3E.toByte())

    fun buildRecordStopAndPreviewStop(): ByteArray =
        byteArrayOf(0x3C.toByte(), 0x31.toByte(), 0x3E.toByte(), 0x3C.toByte(), 0x41.toByte(), 0x3E.toByte())

    fun buildSyncReset(): ByteArray = byteArrayOf(0x3C.toByte(), 0x89.toByte(), 0x3E.toByte())

    fun buildSyncStart(): ByteArray = byteArrayOf(0x3C.toByte(), 0x82.toByte(), 0x81.toByte(), 0x00.toByte(), 0x00.toByte(), 0x3E.toByte())

    fun buildFastHandshake(): ByteArray = byteArrayOf(0x3C.toByte(), 0x80.toByte(), 0x3E.toByte())

    fun buildReadSystemParams(): ByteArray = byteArrayOf(0x3C.toByte(), 0x90.toByte(), 0x3E.toByte())

    fun buildReadDspParams(index: Int): ByteArray = byteArrayOf(0x3C.toByte(), (0x91 + index).toByte(), 0x3E.toByte())

    fun buildStreamFsCommand(samplingRates: List<Int>): ByteArray {
        val safeRates = IntArray(MaxChannelGroup) { index ->
            samplingRates.getOrElse(index) { 0 }.coerceAtLeast(0)
        }
        val buffer = ByteBuffer.allocate(3 + 4 * MaxChannelGroup).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(0x3C.toByte())
        buffer.put(0x33.toByte())
        safeRates.forEach { buffer.putInt(it) }
        buffer.put(0x3E.toByte())
        return buffer.array()
    }

    fun buildQuickFsCommand(ephysRate: Int): ByteArray {
        val safeRate = ephysRate.coerceIn(0, 0xFFFF)
        return byteArrayOf(
            0x3C.toByte(),
            0x32.toByte(),
            (safeRate and 0xFF).toByte(),
            ((safeRate shr 8) and 0xFF).toByte(),
            0x3E.toByte(),
        )
    }

    fun buildSystemParamUpload(
        basePayload: ByteArray,
        params: ParsedSystemParams,
    ): ByteArray {
        val payload = basePayload.copyOf(512)
        putU32(payload, 0, params.fs)
        putU32(payload, 4, params.auxMode)
        repeat(MaxChannelGroup) { index ->
            putU16(payload, 8 + index * 2, params.channelCounts.getOrElse(index) { 0 })
        }
        repeat(MaxChannelGroup) { index ->
            putU32(payload, 40 + index * 4, params.samplingRates.getOrElse(index) { 0 })
        }
        putU32(payload, 160, params.stimMode)
        putU32(payload, 164, params.closedLoopMode)
        repeat(4) { index ->
            putU32(payload, 168 + index * 4, params.stimIntervals.getOrElse(index) { 0 })
            putU32(payload, 184 + index * 4, params.pulseWidths.getOrElse(index) { 0 })
            putU32(payload, 200 + index * 4, params.pulseCounts.getOrElse(index) { 0 })
            putU32(payload, 216 + index * 4, params.stimDelays.getOrElse(index) { 0 })
            putU32(payload, 232 + index * 4, params.stimRandomDelays.getOrElse(index) { 0 })
            putF32(payload, 256 + index * 4, params.triggerGains.getOrElse(index) { 0f })
            putU32(payload, 288 + index * 4, params.stimIntensities.getOrElse(index) { 0 })
            putU32(payload, 304 + index * 4, params.stimChannels.getOrElse(index) { 0 })
            putF32(payload, 372 + index * 4, params.clParam1.getOrElse(index) { 0f })
            putF32(payload, 388 + index * 4, params.clParam2.getOrElse(index) { 0f })
        }
        putU32(payload, 248, params.triggerTrainStart)
        putU32(payload, 252, params.triggerTrainDuration)
        putU32(payload, 280, params.previewChannelBankRaw)
        payload[320] = params.miscRatio.coerceIn(0, 255).toByte()
        payload[321] = params.previewRatio.coerceIn(0, 255).toByte()
        putU16(payload, 322, params.miscInterval)
        putU32(payload, 364, params.randomTriggerMin)
        putU32(payload, 368, params.randomTriggerMax)
        putU16(payload, 404, params.vbattThresholdRaw)
        payload[406] = params.audioRatio.coerceIn(0, 255).toByte()
        payload[407] = params.cameraRatio.coerceIn(0, 255).toByte()
        putU32(payload, 436, params.baseFs)
        return frame(0x01, payload)
    }

    fun buildCameraParamUpdate(reg0: Int, reg1: Int): ByteArray {
        val buffer = ByteBuffer.allocate(7).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(0x3C.toByte())
        buffer.put(0x15.toByte())
        buffer.putShort(reg0.coerceIn(0, 0xFFFF).toShort())
        buffer.putShort(reg1.coerceIn(0, 0xFFFF).toShort())
        buffer.put(0x3E.toByte())
        return buffer.array()
    }

    fun buildTriggerGainUpdate(channelId: Int, gain: Float): ByteArray {
        val buffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(0x3C.toByte())
        buffer.put(0x15.toByte())
        buffer.put(channelId.coerceIn(0, 255).toByte())
        buffer.putFloat(gain)
        buffer.put(0x3E.toByte())
        return buffer.array()
    }

    fun buildSnapshotRequest(preview: Boolean = false): ByteArray =
        byteArrayOf(0x3C.toByte(), 0x9E.toByte(), if (preview) 0x01.toByte() else 0x00.toByte(), 0x3E.toByte())

    fun buildReadCameraParams(): ByteArray = byteArrayOf(0x3C.toByte(), 0x9D.toByte(), 0x3E.toByte())

    fun buildStimEnable(enabled: Boolean): ByteArray =
        byteArrayOf(0x3C.toByte(), 0x11.toByte(), if (enabled) 0x01.toByte() else 0x00.toByte(), 0x3E.toByte())

    fun buildStimIntensity(channelId: Int, intensityPercent: Float): ByteArray {
        val scaled = ((intensityPercent.coerceIn(0f, 100f) / 100f) * 65535f).toInt().coerceIn(0, 0xFFFF)
        val buffer = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(0x3C.toByte())
        buffer.put(0x13.toByte())
        buffer.putShort(scaled.toShort())
        buffer.put(channelId.coerceIn(0, 255).toByte())
        buffer.put(0x3E.toByte())
        return buffer.array()
    }

    fun buildTriggerThreshold(channelId: Int, threshold: Float): ByteArray {
        val buffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(0x3C.toByte())
        buffer.put(0x14.toByte())
        buffer.put(channelId.coerceIn(0, 255).toByte())
        buffer.putFloat(threshold)
        buffer.put(0x3E.toByte())
        return buffer.array()
    }

    fun buildStimParamUpdate(
        channelId: Int,
        delayUnits: Float,
        randomDelayUnits: Float,
        durationUnits: Float,
        intervalUnits: Float,
        cycles: Int,
    ): ByteArray {
        val commandId = if (channelId.coerceIn(0, 1) == 0) 0x20 else 0x21
        val totalSize = if (commandId == 0x20) 23 else 24
        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(0x3C.toByte())
        buffer.put(commandId.toByte())
        buffer.putInt((delayUnits.coerceAtLeast(0f) * 10f).toInt())
        buffer.putInt((randomDelayUnits.coerceAtLeast(0f) * 10f).toInt())
        buffer.putInt((durationUnits.coerceAtLeast(0f) * 10f).toInt())
        buffer.putInt((intervalUnits.coerceAtLeast(0f) * 10f).toInt())
        buffer.putInt(cycles.coerceAtLeast(0))
        if (commandId == 0x21) {
            buffer.put(0x00)
        }
        buffer.put(0x3E.toByte())
        return buffer.array()
    }

    fun buildDspLiveUpdate(
        dspIndex: Int,
        maOrder: Int,
        filterType: Int,
        formula: Int,
        channels: List<Int>,
    ): ByteArray {
        val commandId = if (dspIndex.coerceIn(0, 1) == 0) 0x22 else 0x23
        // CE64 accepts four 0-based source channels (A through D) in the
        // current 0x22 / 0x23 live-DSP command.  Older Android builds sent
        // only A-C, silently leaving D unchanged on the device.
        val safeChannels = IntArray(4) { index -> channels.getOrElse(index) { 0 }.coerceIn(0, 63) }
        val buffer = ByteBuffer.allocate(31).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(0x3C.toByte())
        buffer.put(commandId.toByte())
        buffer.putInt(maOrder.coerceAtLeast(0))
        buffer.putInt(filterType.coerceAtLeast(0))
        buffer.putInt(formula.coerceAtLeast(0))
        safeChannels.forEach { buffer.putInt(it) }
        buffer.put(0x3E.toByte())
        return buffer.array()
    }

    fun buildDspParamUpload(
        dspIndex: Int,
        basePayload: ByteArray,
        params: ParsedDspParams,
    ): ByteArray {
        val commandId = if (dspIndex.coerceIn(0, 1) == 0) 0x02 else 0x03
        val payload = basePayload.copyOf(512)
        params.channels.forEachIndexed { index, channel ->
            if (index < 128) {
                payload[index] = channel.coerceIn(0, 255).toByte()
            }
        }
        putU32(payload, 128, params.formula)
        putU32(payload, 132, params.filterType)
        putU32(payload, 136, params.func2)
        putU32(payload, 140, params.maOrder)
        return frame(commandId, payload)
    }

    fun buildSpikeDetectorConfig(config: SpikeDetectorConfigUiState, confirmationTag: Int = config.confirmationTag): ByteArray {
        val payload = ByteArray(SpikeDetectorParamBytes)
        putU32(payload, 0, 0x314B5053)
        putU16(payload, 4, 2)
        putU16(payload, 6, if (config.enabled) 0x01 else 0x00)
        putU64(payload, 8, config.channelEnableMask)
        putU64(payload, 16, config.positivePolarityMask)
        repeat(SpikeDetectorChannelCount) { channel ->
            putI16(payload, 24 + channel * 2, config.thresholds.getOrElse(channel) { 120 }.coerceIn(1, Short.MAX_VALUE.toInt()))
        }
        putU16(payload, 152, config.refractorySamples.coerceIn(0, 0xFFFF))
        payload[154] = config.hpfShift.coerceIn(1, 8).toByte()
        payload[155] = confirmationTag.coerceIn(0, 0xFF).toByte()
        return frame(0x24, payload)
    }

    fun buildReadSpikeDetectorConfig(): ByteArray = frame(0x9C)

    fun buildSpectrumConfig(config: SpectrumConfigUiState): ByteArray {
        val flags = (if (config.enabled) 0x01 else 0x00) or
            (if (config.dwtProfileEnabled) 0x02 else 0x00)
        val payload = ByteBuffer.allocate(11).order(ByteOrder.LITTLE_ENDIAN)
            .put(SpectrumProtocolVersion.toByte())
            .put(flags.toByte())
            .put(SpectrumDisplayBinCount.toByte())
            .put(config.source.coerceIn(SpectrumSourceAdc, SpectrumSourceEphys).toByte())
            .put(config.channel.coerceIn(0, 63).toByte())
            .putShort(config.firstBin.coerceIn(1, 63).toShort())
            .putShort(config.lastBin.coerceIn(config.firstBin.coerceIn(1, 63), 63).toShort())
            .putShort(config.periodMs.coerceIn(100, 1_000).toShort())
            .array()
        return frame(0x25, payload)
    }

    fun buildReadSpectrumConfig(): ByteArray = frame(0x9A)

    fun buildSchedulerStatusRequest(): ByteArray = frame(SchedulerCommandStatus)

    fun buildSchedulerListRequest(): ByteArray = frame(SchedulerCommandList)

    fun buildSchedulerRuleUpdate(rule: SchedulerRuleUiState): ByteArray {
        require(Ce64Scheduler.validationError(rule) == null) { Ce64Scheduler.validationError(rule).orEmpty() }
        return frame(SchedulerCommandSetRule, schedulerRuleBytes(rule))
    }

    fun buildSchedulerRuleEnable(ruleId: Int, enabled: Boolean): ByteArray =
        frame(SchedulerCommandEnableRule, byteArrayOf(schedulerIndex(ruleId, SchedulerRuleCount), if (enabled) 1 else 0))

    fun buildSchedulerRuleClear(ruleId: Int): ByteArray =
        frame(SchedulerCommandClearRule, byteArrayOf(schedulerIndex(ruleId, SchedulerRuleCount)))

    fun buildSchedulerClearAll(): ByteArray = frame(SchedulerCommandClearAll, byteArrayOf(0x5A))

    fun buildSchedulerGlobalEnable(enabled: Boolean): ByteArray =
        frame(SchedulerCommandGlobalEnable, byteArrayOf(if (enabled) 1 else 0))

    fun buildSchedulerProfileRead(profileId: Int, chunkIndex: Int): ByteArray =
        frame(
            SchedulerCommandProfileRead,
            byteArrayOf(
                schedulerIndex(profileId, SchedulerRuleCount),
                schedulerIndex(chunkIndex, SchedulerProfileChunkCount),
            ),
        )

    fun buildSchedulerProfileWrite(profileId: Int, chunkIndex: Int, data: ByteArray): ByteArray {
        require(data.size == SchedulerProfileChunkBytes) { "A recording profile chunk must contain exactly 32 bytes." }
        return frame(
            SchedulerCommandProfileWrite,
            byteArrayOf(
                schedulerIndex(profileId, SchedulerRuleCount),
                schedulerIndex(chunkIndex, SchedulerProfileChunkCount),
            ) + data,
        )
    }

    private fun schedulerIndex(value: Int, count: Int): Byte {
        require(value in 0 until count) { "Scheduler slot/chunk is outside the supported range." }
        return value.toByte()
    }

    fun schedulerResponsePayloadLengthFor(requestCommand: Int): Int? = when (requestCommand) {
        SchedulerCommandStatus -> 33
        SchedulerCommandList -> 1 + SchedulerConfigBytes
        SchedulerCommandSetRule,
        SchedulerCommandEnableRule,
        SchedulerCommandClearRule,
        SchedulerCommandClearAll,
        SchedulerCommandGlobalEnable -> 2
        SchedulerCommandProfileRead -> 1 + 3 + SchedulerProfileChunkBytes
        SchedulerCommandProfileWrite -> 4
        else -> null
    }

    fun buildForceTrigger(index: Int): ByteArray =
        byteArrayOf(0x3C.toByte(), 0x60.toByte(), (0x01 shl index.coerceIn(0, 7)).toByte(), 0x3E.toByte())

    fun buildRoleSwitch(mode: Int): ByteArray = byteArrayOf(0x3C.toByte(), 0xA1.toByte(), mode.toByte(), 0x3E.toByte())

    fun buildEnterSleep(): ByteArray = byteArrayOf(0x3C.toByte(), 0xA0.toByte(), 0x0A.toByte(), 0x3E.toByte())

    fun buildSoftwareReset(): ByteArray = byteArrayOf(0x3C.toByte(), 0xAB.toByte(), 0xBA.toByte(), 0x3E.toByte())

    fun buildSystemBootloader(): ByteArray = byteArrayOf(0x3C.toByte(), 0xAC.toByte(), 0xCA.toByte(), 0x3E.toByte())

    // Windows uses outbound 0xAE for firmware-update entry even though inbound 0xAE is waveform payload data.
    fun buildFirmwareImageUpdate(): ByteArray = byteArrayOf(0x3C.toByte(), 0xAE.toByte(), 0x3E.toByte())

    /**
     * Requests installation of an AI module that has already been staged to the
     * device SD card by the desktop workflow. The CE32_console uses the same
     * fixed slots and staging blocks before sending this BLE request.
     */
    fun buildAiModuleInstall(slot: Int): ByteArray {
        val safeSlot = slot.coerceIn(AiModuleEphysSlot, AiModuleImuSlot)
        val stagingBlock = if (safeSlot == AiModuleImuSlot) {
            AiModuleImuStagingBlock
        } else {
            AiModuleEphysStagingBlock
        }
        return byteArrayOf(
            0x3C.toByte(),
            0x96.toByte(),
            safeSlot.toByte(),
            (stagingBlock and 0xFF).toByte(),
            ((stagingBlock shr 8) and 0xFF).toByte(),
            ((stagingBlock shr 16) and 0xFF).toByte(),
            ((stagingBlock shr 24) and 0xFF).toByte(),
            0x3E.toByte(),
        )
    }

    fun buildAiModuleSelect(slot: Int?): ByteArray =
        frame(0x97, byteArrayOf((slot ?: 0xFF).coerceIn(0, 0xFF).toByte()))

    fun buildAiRuntimeEnable(enabled: Boolean): ByteArray =
        frame(0x98, byteArrayOf(if (enabled) 0x01 else 0x00))

    fun buildAiRuntimeStatusRequest(): ByteArray = frame(0x99)

    fun buildAiResidentStatusRequest(slot: Int? = null): ByteArray =
        frame(0x9B, byteArrayOf((slot ?: 0xFF).coerceIn(0, 0xFF).toByte()))

    fun buildPackedTimeMeasurement(delayMs: Int = 0, now: ZonedDateTime = ZonedDateTime.now()): ByteArray {
        val msSinceMidnight = (now.toLocalTime().toNanoOfDay() / 1_000_000L).toInt()
        val packed = (msSinceMidnight and PackedTime20Mask) or ((delayMs.coerceIn(0, PackedDelay12Max) and PackedDelay12Max) shl 20)
        return frame(0x8F, ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(packed).array())
    }

    fun buildLogBlockRequest(blockIndex: Int): ByteArray =
        byteArrayOf(0x3C.toByte(), 0x94.toByte(), (blockIndex and 0xFF).toByte(), ((blockIndex shr 8) and 0xFF).toByte(), 0x3E.toByte())

    fun buildDeleteRecords(deleteCount: Int): ByteArray =
        byteArrayOf(0x3C.toByte(), 0x95.toByte(), deleteCount.coerceIn(0, 255).toByte(), 0x3E.toByte())

    fun buildLedCommand(enabled: Boolean): ByteArray {
        val state = if (enabled) 1 else 0
        return byteArrayOf(0x3C.toByte(), 0x61.toByte(), 0x00.toByte(), (0x02 or (state shl 3)).toByte(), 0x3E.toByte())
    }

    fun buildGpio0Command(mode: GpioMode): ByteArray =
        byteArrayOf(0x3C.toByte(), 0x61.toByte(), 0x02.toByte(), mode.commandByte, 0x3E.toByte())

    fun buildGpio1Command(mode: GpioMode): ByteArray =
        byteArrayOf(0x3C.toByte(), 0x61.toByte(), 0x03.toByte(), mode.commandByte, 0x3E.toByte())

    fun buildImpedanceTest(): ByteArray = byteArrayOf(0x3C.toByte(), 0x51.toByte(), 0x3E.toByte())

    /**
     * CE64's current closed-loop viewer uses bits 0/1 for CL1/CL2 and bit 7
     * for continuous best-effort delivery.  Earlier Android builds sent 0x01,
     * which only requested a legacy one-shot CL1 capture.
     */
    fun buildTriggerWaveform(enabled: Boolean): ByteArray =
        byteArrayOf(0x3C.toByte(), 0x43.toByte(), if (enabled) 0x83.toByte() else 0x00.toByte(), 0x3E.toByte())

    fun parseAiRuntimeStatus(payload: ByteArray): AiRuntimeStatusUiState? {
        if (payload.size < 26) {
            return null
        }

        val flags = payload[0].toInt() and 0xFF
        val rawSlot = payload[1].toInt() and 0xFF
        return AiRuntimeStatusUiState(
            layoutReady = flags and 0x01 != 0,
            hasSelectedSlot = flags and 0x02 != 0,
            requestedEnabled = flags and 0x04 != 0,
            running = flags and 0x08 != 0,
            loadableBackend = flags and 0x10 != 0,
            activeSlot = rawSlot.takeIf { it in AiModuleEphysSlot..AiModuleImuSlot },
            statusCode = i32(payload, 2),
            runtimeWindowAddress = u32(payload, 6),
            runtimeWindowBytes = u32(payload, 10),
            inputBufferBytes = u32(payload, 14),
            outputBufferBytes = u32(payload, 18),
            executionTime = u32(payload, 22),
        )
    }

    fun parseAiResidentSlotStatus(payload: ByteArray): AiResidentSlotStatusUiState? {
        if (payload.size < 34) {
            return null
        }

        val slot = payload[0].toInt() and 0xFF
        if (slot !in AiModuleEphysSlot..AiModuleImuSlot) {
            return null
        }

        return AiResidentSlotStatusUiState(
            slot = slot,
            present = payload[1].toInt() and 0xFF != 0,
            statusCode = i32(payload, 2),
            storageStartAddress = u32(payload, 6),
            storageCapacityBytes = u32(payload, 10),
            imageSizeBytes = u32(payload, 14),
            imageFlags = u32(payload, 18),
            runtimeRamAddress = u32(payload, 22),
            requiredArenaBytes = u32(payload, 26),
            inputBytes = u32(payload, 30),
        )
    }

    fun parseSpikeDetectorConfig(payload: ByteArray): SpikeDetectorConfigUiState? {
        if (payload.size < SpikeDetectorParamBytes || u32(payload, 0) != 0x314B5053L || u16(payload, 4) != 2) {
            return null
        }
        return SpikeDetectorConfigUiState(
            enabled = u16(payload, 6) and 0x01 != 0,
            channelEnableMask = i64(payload, 8),
            positivePolarityMask = i64(payload, 16),
            thresholds = List(SpikeDetectorChannelCount) { channel -> i16(payload, 24 + channel * 2) },
            refractorySamples = u16(payload, 152),
            hpfShift = payload[154].toInt() and 0xFF,
            confirmationTag = payload[155].toInt() and 0xFF,
        )
    }

    fun parseLiveSpikeEvent(payload: ByteArray, receivedAtMs: Long = System.currentTimeMillis()): LiveSpikeEventUiState? {
        if (payload.size < 84 || (payload[0].toInt() and 0xFF) != 1) {
            return null
        }
        val channel = payload[1].toInt() and 0xFF
        if (channel !in 0 until SpikeDetectorChannelCount) {
            return null
        }
        return LiveSpikeEventUiState(
            channel = channel,
            positivePolarity = payload[2].toInt() and 0x01 != 0,
            sequence = u32(payload, 4),
            sampleIndex = i64(payload, 8),
            droppedTotal = u32(payload, 16),
            samples = List(32) { sample -> i16(payload, 20 + sample * 2) },
            receivedAtMs = receivedAtMs,
        )
    }

    fun parseCpuLoad(payload: ByteArray, updatedAtMs: Long = System.currentTimeMillis()): CpuLoadUiState? {
        if (payload.size < 3 || (payload[0].toInt() and 0xFF) != 1) {
            return null
        }
        return CpuLoadUiState(
            busyPercent = (payload[1].toInt() and 0xFF).coerceIn(0, 100),
            windowSeconds = payload[2].toInt() and 0xFF,
            updatedAtMs = updatedAtMs,
        )
    }

    fun parseSpectrumConfig(payload: ByteArray): SpectrumConfigUiState? {
        if (payload.size < 14 || (payload[0].toInt() and 0xFF) != SpectrumProtocolVersion) {
            return null
        }
        val source = payload[3].toInt() and 0xFF
        val channel = payload[4].toInt() and 0xFF
        if (source !in SpectrumSourceAdc..SpectrumSourceEphys || (source == SpectrumSourceAdc && channel != 0) ||
            (source == SpectrumSourceEphys && channel !in 0..63)
        ) {
            return null
        }
        val flags = payload[1].toInt() and 0xFF
        val fftStatus = payload[11].toInt() and 0xFF
        return SpectrumConfigUiState(
            enabled = flags and 0x01 != 0,
            dwtProfileEnabled = flags and 0x02 != 0,
            source = source,
            channel = channel,
            firstBin = u16(payload, 5),
            lastBin = u16(payload, 7),
            periodMs = u16(payload, 9),
            fftReady = fftStatus and 0x80 != 0 && (fftStatus and 0x7F) == 7,
            skippedUpdates = u16(payload, 12),
        )
    }

    fun parseSpectrumMetadata(payload: ByteArray, updatedAtMs: Long = System.currentTimeMillis()): SpectrumSnapshotUiState? {
        if (payload.size < 16 || (payload[0].toInt() and 0xFF) != SpectrumProtocolVersion) {
            return null
        }
        val flags = payload[1].toInt() and 0xFF
        val sourceTag = flags ushr 1
        val source = if (sourceTag == 0) SpectrumSourceAdc else SpectrumSourceEphys
        val channel = if (sourceTag == 0) 0 else sourceTag - 1
        if (channel !in 0..63) {
            return null
        }
        return SpectrumSnapshotUiState(
            sequence = u32(payload, 2),
            source = source,
            channel = channel,
            windowStartSample = u32(payload, 6),
            peakBin = u16(payload, 10),
            peakLevel = payload[12].toInt() and 0xFF,
            noiseLevel = payload[13].toInt() and 0xFF,
            confidence = payload[14].toInt() and 0xFF,
            cycleK = payload[15].toInt() and 0xFF,
            dwtCycleValid = flags and 0x01 != 0,
            updatedAtMs = updatedAtMs,
        )
    }

    data class SpectrumBinsPacket(
        val sequence: Long,
        val firstIndex: Int,
        val levels: List<Int>,
    )

    fun parseSpectrumBins(payload: ByteArray): SpectrumBinsPacket? {
        if (payload.size < 7 || (payload[0].toInt() and 0xFF) != SpectrumProtocolVersion) {
            return null
        }
        val count = payload[6].toInt() and 0xFF
        val firstIndex = payload[5].toInt() and 0xFF
        if (count <= 0 || count > SpectrumDisplayBinCount || firstIndex + count > SpectrumDisplayBinCount || payload.size < 7 + count) {
            return null
        }
        return SpectrumBinsPacket(
            sequence = u32(payload, 1),
            firstIndex = firstIndex,
            levels = List(count) { index -> payload[7 + index].toInt() and 0xFF },
        )
    }

    fun parseSchedulerStatus(payload: ByteArray): SchedulerStatusUiState? {
        if (payload.size < 32 || (payload[0].toInt() and 0xFF) != 2) {
            return null
        }
        val nextWake = u32(payload, 8).takeUnless { it == 0xFFFF_FFFFL }
        return SchedulerStatusUiState(
            version = payload[0].toInt() and 0xFF,
            enabled = payload[1].toInt() and 0x01 != 0,
            clockValid = payload[2].toInt() and 0x01 != 0,
            activeIdle = payload[3].toInt() and 0x01 != 0,
            generation = u32(payload, 4),
            nextWakeSeconds = nextWake,
            lastRuleId = payload[12].toInt() and 0xFF,
            lastAction = payload[13].toInt() and 0xFF,
            lastResult = payload[14].toInt() and 0xFF,
            activeRuleId = payload[15].toInt() and 0xFF,
            lastTimeSeconds = u32(payload, 16),
            deferredCount = u32(payload, 20),
            missedCount = u32(payload, 24),
            conflictCount = u32(payload, 28),
        )
    }

    fun parseSchedulerConfig(payload: ByteArray): SchedulerConfigUiState? {
        if (payload.size != SchedulerConfigBytes || u32(payload, 0) != 0x53434844L ||
            u16(payload, 4) != 2 || u16(payload, 6) != SchedulerConfigBytes || u32(payload, 12) !in 0L..1L) {
            return null
        }
        // Firmware returns this precise RAM-only image before the first save.
        val pristine = u32(payload, 8) == 0L && u32(payload, 12) == 1L &&
            payload.drop(16).all { it == 0.toByte() }
        val crc = java.util.zip.CRC32().apply { update(payload, 0, 464) }.value
        if (!pristine && (u32(payload, 468) != 0x434D4954L || u32(payload, 464) != crc)) return null
        val rules = List(SchedulerRuleCount) { index ->
            val offset = 16 + index * SchedulerRuleBytes
            if (payload.copyOfRange(offset, offset + SchedulerRuleBytes).all { it == 0.toByte() }) {
                SchedulerRuleUiState(id = index)
            } else {
                val rule = parseSchedulerRule(payload, offset)
                if (rule.id != index || (payload[offset + 1].toInt() and 0xFE) != 0 ||
                    (rule.enabled && Ce64Scheduler.validationError(rule) != null)) return null
                rule
            }
        }
        return SchedulerConfigUiState(
            version = u16(payload, 4),
            generation = u32(payload, 8),
            enabled = u32(payload, 12) != 0L,
            // Empty slots are all zeros on the wire, including their ID. Keep
            // slot identity so selecting rule 2–8 cannot silently edit rule 1.
            rules = rules,
            profileCrc32 = List(SchedulerRuleCount) { index -> u32(payload, 400 + index * 4) },
            profileGenerations = List(SchedulerRuleCount) { index -> u32(payload, 432 + index * 4) },
        )
    }

    fun build8CReply(requestPayload: ByteArray, now: ZonedDateTime = ZonedDateTime.now()): ByteArray {
        val sync = toSyncStamp(now)
        val payload = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
            .put(requestPayload.copyOfRange(0, 8))
            .putInt(sync.seconds)
            .putInt(sync.subSeconds)
            .array()
        return frame(0x8C, payload)
    }

    fun build8DReply(requestPayload: ByteArray, hostRx: ZonedDateTime = ZonedDateTime.now(), hostTx: ZonedDateTime = ZonedDateTime.now()): ByteArray {
        val syncRx = toSyncStamp(hostRx)
        val syncTx = toSyncStamp(hostTx)
        val payload = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            .put(requestPayload.copyOfRange(0, 8))
            .putInt(syncRx.seconds)
            .putInt(syncRx.subSeconds)
            .putInt(syncTx.seconds)
            .putInt(syncTx.subSeconds)
            .array()
        return frame(0x8D, payload)
    }

    fun patch8DReplyTxStamp(command: ByteArray, hostTx: ZonedDateTime = ZonedDateTime.now()) {
        if (command.size < 27 || ((command.getOrNull(1)?.toInt() ?: -1) and 0xFF) != 0x8D) {
            return
        }

        val syncTx = toSyncStamp(hostTx)
        putU32(command, 18, syncTx.seconds)
        putU32(command, 22, syncTx.subSeconds)
    }

    fun buildRtcSetCommand(now: ZonedDateTime = ZonedDateTime.now()): ByteArray {
        val date = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            .put(((now.dayOfWeek.value % 7).takeIf { it != 0 } ?: 7).toByte())
            .put(now.monthValue.toByte())
            .put(now.dayOfMonth.toByte())
            .put((now.year - 2000).coerceIn(0, 255).toByte())
            .array()

        val ticksWithinSecond = (now.nano / 100).coerceIn(0, 9_999_999) / 100
        val time = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN)
            .put(now.hour.toByte())
            .put(now.minute.toByte())
            .put(now.second.toByte())
            .put(0x00)
            .putInt(ticksWithinSecond)
            .putInt(9_999)
            .putInt(0)
            .putInt(0)
            .array()

        val payload = ByteBuffer.allocate(date.size + time.size).order(ByteOrder.LITTLE_ENDIAN)
            .put(date)
            .put(time)
            .array()
        return frame(0x8A, payload)
    }

    fun parsePreviewPacket(payload: ByteArray): PreviewPacket? {
        if (payload.size < 136) {
            return null
        }

        val voltageRaw = u16(payload, 0)
        val usedBlocks = u32(payload, 2)
        val digitalFlags = parseDigitalFlags(payload[6], payload[7])
        val sampleCount = 64
        val samples = ArrayList<Float>(sampleCount)
        var offset = 8
        repeat(sampleCount) {
            samples += i16(payload, offset).toFloat()
            offset += 2
        }

        return PreviewPacket(
            samples = samples,
            voltage = voltageRaw / 65535.0 * 3.3 * 4.0,
            usedSpaceMb = usedBlocks * (512.0 / 1024.0 / 1024.0),
            digitalFlags = digitalFlags,
        )
    }

    fun parseRecTimePacket(payload: ByteArray): RecTimePacket? {
        if (payload.size < 4) {
            return null
        }

        val seconds = u32(payload, 0).toLong()
        val voltage = if (payload.size >= 6) u16(payload, 4) / 65535.0 * 3.3 * 4.0 else null
        val usedSpaceMb = if (payload.size >= 10) u32(payload, 6) * (512.0 / 1024.0 / 1024.0) else null
        return RecTimePacket(seconds, voltage, usedSpaceMb)
    }

    fun parseRecordStartEvent(payload: ByteArray): RecordStartEvent? {
        if (payload.size < 25) {
            return null
        }

        val month = payload[1].toInt() and 0xFF
        val day = payload[2].toInt() and 0xFF
        val year = 2000 + (payload[3].toInt() and 0xFF)
        val hour = payload[4].toInt() and 0xFF
        val minute = payload[5].toInt() and 0xFF
        val second = payload[6].toInt() and 0xFF
        val eventCode = payload[24].toInt() and 0xFF

        return RecordStartEvent(
            year = year.takeIf { month in 1..12 && day in 1..31 },
            month = month.takeIf { it in 1..12 },
            day = day.takeIf { it in 1..31 },
            hour = hour.takeIf { it in 0..23 },
            minute = minute.takeIf { it in 0..59 },
            second = second.takeIf { it in 0..59 },
            eventCode = eventCode,
        )
    }

    fun parseSyncStatus(payload: ByteArray): SyncStatus? {
        if (payload.isEmpty()) {
            return null
        }

        return when {
            payload.size >= 15 -> {
                val mode = payload[0].toInt() and 0xFF
                val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
                buffer.position(1)
                SyncStatus(
                    mode = mode,
                    offsetSeconds = buffer.float,
                    accuracySeconds = buffer.float,
                    delaySeconds = buffer.float,
                    sampleCount = buffer.short.toInt() and 0xFFFF,
                )
            }

            payload.size >= 4 -> {
                val packedDev = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN).int
                val hostRxSeconds = currentHostSeconds()
                val devMs = packedDev and PackedTime20Mask
                SyncStatus(
                    mode = 0x03,
                    offsetSeconds = devMs / 1000f,
                    accuracySeconds = null,
                    delaySeconds = ((packedDev ushr 20) and PackedDelay12Max) / 1000f,
                    sampleCount = 0,
                    hostRxSeconds = hostRxSeconds,
                )
            }

            else -> null
        }
    }

    fun parseSystemParams(payload: ByteArray): ParsedSystemParams? {
        if (payload.size < 440) {
            return null
        }

        return ParsedSystemParams(
            fs = u32(payload, 0).toInt(),
            auxMode = u32(payload, 4).toInt(),
            channelCounts = List(MaxChannelGroup) { index ->
                u16(payload, 8 + index * 2)
            },
            samplingRates = List(MaxChannelGroup) { index ->
                u32(payload, 40 + index * 4).toInt()
            },
            stimMode = u32(payload, 160).toInt(),
            closedLoopMode = u32(payload, 164).toInt(),
            stimIntervals = List(4) { index -> u32(payload, 168 + index * 4).toInt() },
            pulseWidths = List(4) { index -> u32(payload, 184 + index * 4).toInt() },
            pulseCounts = List(4) { index -> u32(payload, 200 + index * 4).toInt() },
            stimDelays = List(4) { index -> u32(payload, 216 + index * 4).toInt() },
            stimRandomDelays = List(4) { index -> u32(payload, 232 + index * 4).toInt() },
            triggerTrainStart = u32(payload, 248).toInt(),
            triggerTrainDuration = u32(payload, 252).toInt(),
            triggerGains = List(4) { index -> f32(payload, 256 + index * 4) },
            previewChannelBankRaw = u32(payload, 280).toInt(),
            systemStatus = u32(payload, 284).toInt(),
            stimIntensities = List(4) { index -> u32(payload, 288 + index * 4).toInt() },
            stimChannels = List(4) { index -> u32(payload, 304 + index * 4).toInt() },
            miscRatio = payload[320].toInt() and 0xFF,
            previewRatio = payload[321].toInt() and 0xFF,
            miscInterval = u16(payload, 322),
            errorCode = u32(payload, 324),
            firmwareVersion = u16(payload, 328),
            hardwareVersion = u16(payload, 330),
            randomTriggerMin = u32(payload, 364).toInt(),
            randomTriggerMax = u32(payload, 368).toInt(),
            clParam1 = List(4) { index -> f32(payload, 372 + index * 4) },
            clParam2 = List(4) { index -> f32(payload, 388 + index * 4) },
            vbattThresholdRaw = u16(payload, 404),
            audioRatio = payload[406].toInt() and 0xFF,
            cameraRatio = payload[407].toInt() and 0xFF,
            baseFs = u32(payload, 436).toInt(),
        )
    }

    fun parseCameraParams(payload: ByteArray): ParsedCameraParams? {
        if (payload.size < 4) {
            return null
        }
        return ParsedCameraParams(
            reg0 = u16(payload, 0),
            reg1 = u16(payload, 2),
        )
    }

    fun parseStimControl(payload: ByteArray): StimControlStatus? {
        if (payload.size < 60) {
            return null
        }

        return StimControlStatus(
            id = i32(payload, 56),
            triggerDelayThis = i32(payload, 0),
            triggerDelay = i32(payload, 4),
            triggerRandomDelay = i32(payload, 8),
            triggerDuration = i32(payload, 12),
            triggerInterval = i32(payload, 16),
            triggerLevel = f32(payload, 20),
            triggerGain = f32(payload, 24),
            triggerMean = f32(payload, 28),
            trainSps = i32(payload, 32),
            trainStartSps = i32(payload, 36),
            triggerState = i32(payload, 40),
            triggerElapsed = i32(payload, 44),
            stimCount = i32(payload, 48),
            count = i32(payload, 52),
        )
    }

    fun parseDspParams(payload: ByteArray): ParsedDspParams? {
        if (payload.size < 144) {
            return null
        }
        return ParsedDspParams(
            formula = u32(payload, 128).toInt(),
            filterType = u32(payload, 132).toInt(),
            func2 = u32(payload, 136).toInt(),
            maOrder = u32(payload, 140).toInt(),
            channels = List(4) { index -> payload[index].toInt() and 0xFF },
        )
    }

    fun parseAsciiMessage(payload: ByteArray): String {
        return payload.toString(Charsets.UTF_8).trim('\u0000').trim()
    }

    fun parseUnsignedShortList(payload: ByteArray): List<Int> {
        val values = mutableListOf<Int>()
        var index = 0
        while (index + 1 < payload.size) {
            values += u16(payload, index)
            index += 2
        }
        return values
    }

    fun parseSignedShortList(payload: ByteArray): List<Int> {
        val values = mutableListOf<Int>()
        var index = 0
        while (index + 1 < payload.size) {
            values += i16(payload, index)
            index += 2
        }
        return values
    }

    fun parseCameraRow(commandId: Int, payload: ByteArray): CameraRow? {
        val pixels = when (commandId) {
            CameraSnapshotCommand -> CameraSnapshotPixels
            CameraPreviewCommand -> CameraPreviewPixels
            else -> return null
        }
        if (payload.size < pixels + 1) {
            return null
        }
        val row = payload[0].toInt() and 0xFF
        if (row >= pixels) {
            return null
        }
        return CameraRow(
            row = row,
            pixels = pixels,
            bytes = payload.copyOfRange(1, 1 + pixels),
            isPreview = commandId == CameraPreviewCommand,
        )
    }

    fun parseDeleteAck(payload: ByteArray): Long? {
        if (payload.size < 4) {
            return null
        }
        return u32(payload, 0)
    }

    fun bytesToHex(payload: ByteArray): String = payload.joinToString(" ") { "%02X".format(it) }

    private fun parseDigitalFlags(first: Byte, second: Byte): List<DigitalFlag> {
        return listOf(
            DigitalFlag("DET1", first.toInt() and 0x01 != 0),
            DigitalFlag("STIM1", first.toInt() and 0x02 != 0),
            DigitalFlag("DET2", first.toInt() and 0x04 != 0),
            DigitalFlag("STIM2", first.toInt() and 0x08 != 0),
            DigitalFlag("AUX1", first.toInt() and 0x10 != 0),
            DigitalFlag("AUX2", first.toInt() and 0x20 != 0),
            DigitalFlag("AUX3", first.toInt() and 0x40 != 0),
            DigitalFlag("EXT1", first.toInt() and 0x80 != 0),
            DigitalFlag("EXT2", second.toInt() and 0x01 != 0),
            DigitalFlag("EXT3", second.toInt() and 0x02 != 0),
            DigitalFlag("UDV", second.toInt() and 0x04 != 0),
            DigitalFlag("DSP_WAIT", second.toInt() and 0x08 != 0),
            DigitalFlag("DSP_CAL", second.toInt() and 0x10 != 0),
            DigitalFlag("DSP_READY", second.toInt() and 0x20 != 0),
            DigitalFlag("LD1", second.toInt() and 0x40 != 0),
            DigitalFlag("LD2", second.toInt() and 0x80 != 0),
        )
    }

    fun isLegacyWakeFirmware(version: String?): Boolean {
        val trimmed = version?.trim()?.trim('\u0000').orEmpty()
        if (trimmed.isEmpty()) {
            return false
        }

        val normalized = buildString {
            trimmed.forEach { ch ->
                if (ch.isDigit() || ch == '.') {
                    append(ch)
                }
            }
        }.ifEmpty { trimmed }

        val parsedParts = normalized.split('.')
        val major = parsedParts.getOrNull(0)?.toIntOrNull()
        val minor = parsedParts.getOrNull(1)?.toIntOrNull() ?: 0
        return major != null && (major < 3 || (major == 3 && minor <= 1))
    }

    fun alignUpAu(sector: Long): Long {
        val auSectors = 0x2000L
        val rem = sector % auSectors
        return if (rem == 0L) sector else sector + auSectors - rem
    }

    private fun toSyncStamp(time: ZonedDateTime): SyncStamp {
        val seconds = time.minute * 60 + time.second
        val subSeconds = (time.nano / 100_000).coerceIn(0, 9_999)
        return SyncStamp(seconds = seconds, subSeconds = subSeconds)
    }

    private fun currentHostSeconds(): Float {
        val now = ZonedDateTime.now()
        return now.minute * 60f + now.second + now.nano / 1_000_000_000f
    }

    private fun frame(commandId: Int, payload: ByteArray = byteArrayOf()): ByteArray {
        return ByteBuffer.allocate(payload.size + 3)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put(0x3C.toByte())
            .put(commandId.toByte())
            .put(payload)
            .put(0x3E.toByte())
            .array()
    }

    private fun schedulerRuleBytes(rule: SchedulerRuleUiState): ByteArray {
        val payload = ByteArray(SchedulerRuleBytes)
        payload[0] = rule.id.coerceIn(0, SchedulerRuleCount - 1).toByte()
        payload[1] = if (rule.enabled) 0x01 else 0x00
        payload[2] = rule.trigger.coerceIn(0, 3).toByte()
        payload[3] = rule.action.coerceIn(0, 3).toByte()
        payload[4] = rule.missedPolicy.coerceIn(0, 1).toByte()
        payload[5] = rule.profileId.coerceIn(0, 0xFF).toByte()
        payload[6] = rule.conditionMask.coerceIn(0, 0x0F).toByte()
        payload[7] = rule.conditionLogic.coerceIn(0, 1).toByte()
        putU16(payload, 8, rule.priority)
        putU32(payload, 10, rule.anchorDay.coerceIn(0L, 0xFFFF_FFFFL).toInt())
        putU32(payload, 14, rule.timeOfDaySeconds.coerceIn(0L, 86_399L).toInt())
        putU32(payload, 18, rule.periodSeconds.coerceIn(0L, 604_800L).toInt())
        putU32(payload, 22, rule.durationSeconds.coerceIn(0L, 86_400L).toInt())
        putU32(payload, 26, rule.evaluationSeconds.coerceIn(0L, 3_600L).toInt())
        putU16(payload, 30, rule.batteryThresholdMv)
        putU32(payload, 32, rule.storageThresholdBlocks.coerceIn(0L, 0xFFFF_FFFFL).toInt())
        putU16(payload, 36, rule.activityThresholdMg)
        putI16(payload, 38, rule.aiThresholdQ15.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()))
        putU16(payload, 40, rule.hysteresis)
        payload[42] = rule.debounceCount.coerceIn(1, 0xFF).toByte()
        payload[43] = rule.maxDeferrals.coerceIn(1, 24).toByte()
        putU16(payload, 44, rule.marker)
        payload[46] = rule.signalSource.coerceIn(0, 0x1F).toByte()
        payload[47] = rule.conditionInvertMask.coerceIn(0, 0x0F).toByte()
        return payload
    }

    private fun parseSchedulerRule(payload: ByteArray, offset: Int): SchedulerRuleUiState {
        return SchedulerRuleUiState(
            id = payload[offset].toInt() and 0xFF,
            enabled = payload[offset + 1].toInt() and 0x01 != 0,
            trigger = payload[offset + 2].toInt() and 0xFF,
            action = payload[offset + 3].toInt() and 0xFF,
            missedPolicy = payload[offset + 4].toInt() and 0xFF,
            profileId = payload[offset + 5].toInt() and 0xFF,
            conditionMask = payload[offset + 6].toInt() and 0xFF,
            conditionLogic = payload[offset + 7].toInt() and 0xFF,
            priority = u16(payload, offset + 8),
            anchorDay = u32(payload, offset + 10),
            timeOfDaySeconds = u32(payload, offset + 14),
            periodSeconds = u32(payload, offset + 18),
            durationSeconds = u32(payload, offset + 22),
            evaluationSeconds = u32(payload, offset + 26),
            batteryThresholdMv = u16(payload, offset + 30),
            storageThresholdBlocks = u32(payload, offset + 32),
            activityThresholdMg = u16(payload, offset + 36),
            aiThresholdQ15 = i16(payload, offset + 38),
            hysteresis = u16(payload, offset + 40),
            debounceCount = payload[offset + 42].toInt() and 0xFF,
            maxDeferrals = payload[offset + 43].toInt() and 0xFF,
            marker = u16(payload, offset + 44),
            signalSource = payload[offset + 46].toInt() and 0xFF,
            conditionInvertMask = payload[offset + 47].toInt() and 0xFF,
        )
    }

    private fun uuidFromShortForm(shortForm: Long): UUID {
        return UUID.fromString(String.format("%08x-0000-1000-8000-00805f9b34fb", shortForm))
    }

    private fun uuidFromLittleEndian(payload: ByteArray, offset: Int): UUID? {
        if (offset < 0 || payload.size < offset + 16) {
            return null
        }

        val reversed = payload.copyOfRange(offset, offset + 16).reversedArray()
        val buffer = ByteBuffer.wrap(reversed).order(ByteOrder.BIG_ENDIAN)
        return UUID(buffer.long, buffer.long)
    }

    private fun u16(payload: ByteArray, offset: Int): Int =
        ByteBuffer.wrap(payload, offset, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF

    private fun i16(payload: ByteArray, offset: Int): Int =
        ByteBuffer.wrap(payload, offset, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()

    private fun u32(payload: ByteArray, offset: Int): Long =
        ByteBuffer.wrap(payload, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFF_FFFFL

    private fun i64(payload: ByteArray, offset: Int): Long =
        ByteBuffer.wrap(payload, offset, 8).order(ByteOrder.LITTLE_ENDIAN).long

    private data class SyncStamp(
        val seconds: Int,
        val subSeconds: Int,
    )

    data class CameraRow(
        val row: Int,
        val pixels: Int,
        val bytes: ByteArray,
        val isPreview: Boolean,
    )

    private fun putU16(payload: ByteArray, offset: Int, value: Int) {
        ByteBuffer.wrap(payload, offset, 2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.coerceIn(0, 0xFFFF).toShort())
    }

    private fun putI16(payload: ByteArray, offset: Int, value: Int) {
        ByteBuffer.wrap(payload, offset, 2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort())
    }

    private fun putU32(payload: ByteArray, offset: Int, value: Int) {
        ByteBuffer.wrap(payload, offset, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(value)
    }

    private fun putU64(payload: ByteArray, offset: Int, value: Long) {
        ByteBuffer.wrap(payload, offset, 8).order(ByteOrder.LITTLE_ENDIAN).putLong(value)
    }

    private fun putF32(payload: ByteArray, offset: Int, value: Float) {
        ByteBuffer.wrap(payload, offset, 4).order(ByteOrder.LITTLE_ENDIAN).putFloat(value)
    }

    private fun i32(payload: ByteArray, offset: Int): Int =
        ByteBuffer.wrap(payload, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int

    private fun f32(payload: ByteArray, offset: Int): Float =
        ByteBuffer.wrap(payload, offset, 4).order(ByteOrder.LITTLE_ENDIAN).float
}
