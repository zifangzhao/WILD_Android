package com.wild.android.ble

enum class BleHostSessionState {
    Disconnected,
    Connecting,
    Connected,
    Syncing,
    Synced,
    Previewing,
    StartingRecording,
    Recording,
    StoppingRecording,
    Reconnecting,
    Disconnecting,
    Error,
}

enum class ControlScope {
    ActiveDevice,
    SelectedDevices,
    AllConnected,
}

enum class GpioMode(
    val label: String,
    val commandByte: Byte,
) {
    Unknown("--", 0x00),
    Analog("Analog", 0x00),
    Input("Input", 0x01),
    Low("Low", 0x02),
    High("High", 0x0A.toByte());

    companion object {
        val SelectableModes = listOf(Analog, Input, Low, High)
    }
}

data class DigitalFlag(
    val name: String,
    val active: Boolean,
)

data class RecordSummary(
    val index: Int,
    val startSector: Long,
    val endSector: Long,
    val sizeSectors: Long,
) {
    val sizeMb: Double
        get() = sizeSectors * 512.0 / 1024.0 / 1024.0
}

data class PreviewSelection(
    val auxMode: Boolean,
    val index: Int,
) {
    fun toProtocolByte(): Byte {
        val safeIndex = index.coerceIn(0, 0x7F)
        return if (auxMode) {
            (safeIndex or 0x80).toByte()
        } else {
            safeIndex.toByte()
        }
    }

    fun optionCount(ephysChannelCount: Int? = null): Int {
        return if (auxMode) {
            if ((ephysChannelCount ?: 0) > 32) FullAuxLabels.size else BasicAuxLabels.size
        } else {
            (ephysChannelCount ?: 8).coerceAtLeast(1)
        }
    }

    fun normalizedForDevice(ephysChannelCount: Int? = null): PreviewSelection {
        val maxIndex = optionCount(ephysChannelCount).coerceAtLeast(1) - 1
        return copy(index = index.coerceIn(0, maxIndex))
    }

    fun label(ephysChannelCount: Int? = null): String {
        return if (auxMode) {
            val labels = if ((ephysChannelCount ?: 0) > 32) FullAuxLabels else BasicAuxLabels
            labels.getOrElse(index) { "Aux ${index + 1}" }
        } else {
            "E${index + 1}"
        }
    }

    companion object {
        val BasicAuxLabels = listOf(
            "Digital Signal",
            "Accelerometer X",
            "Accelerometer Y",
            "Accelerometer Z",
            "Vbat",
            "Test Signal",
            "DSP output A",
            "DSP output B",
        )

        val FullAuxLabels = listOf(
            "Digital Signal",
            "Accelerometer X",
            "Accelerometer Y",
            "Accelerometer Z",
            "Gyroscope X",
            "Gyroscope Y",
            "Gyroscope Z",
            "Magnetic X",
            "Magnetic Y",
            "Magnetic Z",
            "Vbat",
            "Test Signal",
            "DSP output A",
            "DSP output B",
        )

        val Default = PreviewSelection(auxMode = false, index = 0)
    }
}

data class PreviewPacket(
    val samples: List<Float>,
    val voltage: Double,
    val usedSpaceMb: Double,
    val digitalFlags: List<DigitalFlag>,
)

data class RecTimePacket(
    val recordingSeconds: Long,
    val voltage: Double?,
    val usedSpaceMb: Double?,
)

data class RecordStartEvent(
    val year: Int?,
    val month: Int?,
    val day: Int?,
    val hour: Int?,
    val minute: Int?,
    val second: Int?,
    val eventCode: Int,
)

data class SyncStatus(
    val mode: Int,
    val offsetSeconds: Float?,
    val accuracySeconds: Float?,
    val delaySeconds: Float?,
    val sampleCount: Int,
    val hostRxSeconds: Float? = null,
)

data class SyncMetricUiState(
    val mode: Int,
    val modeLabel: String,
    val offsetMs: Double?,
    val accuracyMs: Double?,
    val delayMs: Double?,
    val sampleCount: Int,
    val summary: String,
)

data class LiveSyncUiState(
    val deviceClockLabel: String,
    val rollingMeanMs: Double,
    val rollingStdMs: Double,
    val sampleCount: Int,
    val delayMs: Double,
    val lastOffsetMs: Int,
    val outlier: Boolean,
)

data class ParsedSystemParams(
    val fs: Int,
    val auxMode: Int,
    val channelCounts: List<Int>,
    val samplingRates: List<Int>,
    val stimMode: Int,
    val closedLoopMode: Int,
    val stimIntervals: List<Int>,
    val pulseWidths: List<Int>,
    val pulseCounts: List<Int>,
    val stimDelays: List<Int>,
    val stimRandomDelays: List<Int>,
    val triggerTrainStart: Int,
    val triggerTrainDuration: Int,
    val triggerGains: List<Float>,
    val previewChannelBankRaw: Int,
    val systemStatus: Int,
    val stimIntensities: List<Int>,
    val stimChannels: List<Int>,
    val miscRatio: Int,
    val previewRatio: Int,
    val miscInterval: Int,
    val errorCode: Long,
    val firmwareVersion: Int,
    val hardwareVersion: Int,
    val randomTriggerMin: Int,
    val randomTriggerMax: Int,
    val clParam1: List<Float>,
    val clParam2: List<Float>,
    val vbattThresholdRaw: Int,
    val audioRatio: Int,
    val cameraRatio: Int,
    val baseFs: Int,
) {
    val ephysSamplingRate: Int
        get() = samplingRates.getOrElse(0) { 0 }

    val ephysChannelCount: Int
        get() = channelCounts.firstOrNull { it > 0 } ?: 8

    val cameraSamplingRate: Int
        get() = samplingRates.getOrElse(1) { 0 }

    val adcSamplingRate: Int
        get() = samplingRates.getOrElse(2) { 0 }

    val cameraEnabled: Boolean
        get() = cameraSamplingRate > 0

    val adcEnabled: Boolean
        get() = adcSamplingRate > 0

    val stimEnabled: Boolean
        get() = stimMode != 0

    val previewSelection: PreviewSelection
        get() {
            val raw = previewChannelBankRaw and 0xFF
            return PreviewSelection(
                auxMode = raw and 0x80 != 0,
                index = raw and 0x7F,
            ).normalizedForDevice(ephysChannelCount)
        }

    fun stimIntensityPercent(index: Int): Float {
        val raw = stimIntensities.getOrElse(index) { 0 }.coerceIn(0, 65535)
        return raw / 65535f * 100f
    }

    fun timingUnits(index: Int, values: List<Int>): Float {
        return values.getOrElse(index) { 0 } / 10f
    }
}

data class ParsedCameraParams(
    val reg0: Int,
    val reg1: Int,
)

data class ParsedDspParams(
    val formula: Int,
    val filterType: Int,
    val func2: Int,
    val maOrder: Int,
    val channels: List<Int>,
)

data class StimControlStatus(
    val id: Int,
    val triggerDelayThis: Int,
    val triggerDelay: Int,
    val triggerRandomDelay: Int,
    val triggerDuration: Int,
    val triggerInterval: Int,
    val triggerLevel: Float,
    val triggerGain: Float,
    val triggerMean: Float,
    val trainSps: Int,
    val trainStartSps: Int,
    val triggerState: Int,
    val triggerElapsed: Int,
    val stimCount: Int,
    val count: Int,
) {
    val stateLabel: String
        get() = when (triggerState) {
            0 -> "Idle"
            1 -> "Armed"
            2 -> "Triggered"
            3 -> "Delivering"
            else -> "State $triggerState"
        }
}

data class SessionEventUiState(
    val timestampMs: Long,
    val summary: String,
)

data class ImpedanceSnapshotUiState(
    val magnitudeValues: List<Int> = emptyList(),
    val phaseValues: List<Int> = emptyList(),
    val crosstalkByDriveChannel: Map<Int, List<Int>> = emptyMap(),
    val updatedAtMs: Long = 0L,
)

/** The persistent 512-byte configuration used by CE64's live transient viewer. */
data class SpikeDetectorConfigUiState(
    val enabled: Boolean = false,
    val channelEnableMask: Long = 0L,
    val positivePolarityMask: Long = 0L,
    val thresholds: List<Int> = List(64) { 120 },
    val refractorySamples: Int = 0,
    val hpfShift: Int = 4,
    val confirmationTag: Int = 0,
) {
    fun channelEnabled(channel: Int): Boolean =
        channel in 0..63 && (channelEnableMask and (1L shl channel)) != 0L

    fun positivePolarity(channel: Int): Boolean =
        channel in 0..63 && (positivePolarityMask and (1L shl channel)) != 0L
}

/** One best-effort, 32-sample waveform produced by the CE64 live spike viewer. */
data class LiveSpikeEventUiState(
    val channel: Int,
    val positivePolarity: Boolean,
    val sequence: Long,
    val sampleIndex: Long,
    val droppedTotal: Long,
    val samples: List<Int>,
    val receivedAtMs: Long,
)

data class SpectrumConfigUiState(
    val enabled: Boolean = false,
    val dwtProfileEnabled: Boolean = false,
    val source: Int = 0,
    val channel: Int = 0,
    val firstBin: Int = 16,
    val lastBin: Int = 63,
    val periodMs: Int = 200,
    val fftReady: Boolean = false,
    val skippedUpdates: Int = 0,
) {
    val sourceLabel: String
        get() = if (source == 1) "Ephys ch $channel" else "ADC"
}

/** A completed 16-band selected-signal spectrum snapshot. */
data class SpectrumSnapshotUiState(
    val sequence: Long = 0L,
    val source: Int = 0,
    val channel: Int = 0,
    val windowStartSample: Long = 0L,
    val peakBin: Int = 0,
    val peakLevel: Int = 0,
    val noiseLevel: Int = 0,
    val confidence: Int = 0,
    val cycleK: Int = 0,
    val dwtCycleValid: Boolean = false,
    val levels: List<Int> = emptyList(),
    val receivedBandsMask: Int = 0,
    val isComplete: Boolean = false,
    val updatedAtMs: Long = 0L,
)

data class CpuLoadUiState(
    val busyPercent: Int,
    val windowSeconds: Int,
    val updatedAtMs: Long,
)

/** Device-local CE64 low-power scheduler status. All timestamps are CE64 RTC seconds. */
data class SchedulerStatusUiState(
    val version: Int,
    val enabled: Boolean,
    val clockValid: Boolean,
    val activeIdle: Boolean,
    val generation: Long,
    val nextWakeSeconds: Long?,
    val lastRuleId: Int,
    val lastAction: Int,
    val lastResult: Int,
    val activeRuleId: Int,
    val lastTimeSeconds: Long,
    val deferredCount: Long,
    val missedCount: Long,
    val conflictCount: Long,
)

/** Exactly mirrors one 48-byte CE64 scheduler rule (protocol version 2). */
data class SchedulerRuleUiState(
    val id: Int,
    val enabled: Boolean = false,
    val trigger: Int = 0,
    val action: Int = 0,
    val missedPolicy: Int = 0,
    val profileId: Int = 0xFF,
    val conditionMask: Int = 0,
    val conditionLogic: Int = 0,
    val priority: Int = 0,
    val anchorDay: Long = 0L,
    val timeOfDaySeconds: Long = 0L,
    val periodSeconds: Long = 0L,
    val durationSeconds: Long = 0L,
    val evaluationSeconds: Long = 60L,
    val batteryThresholdMv: Int = 0,
    val storageThresholdBlocks: Long = 0L,
    val activityThresholdMg: Int = 0,
    val aiThresholdQ15: Int = 0,
    val hysteresis: Int = 0,
    val debounceCount: Int = 1,
    val maxDeferrals: Int = 1,
    val marker: Int = 0,
    val signalSource: Int = 0,
    val conditionInvertMask: Int = 0,
) {
    val triggerLabel: String
        get() = when (trigger) {
            0 -> "Daily"
            1 -> "Once"
            2 -> "Repeat"
            3 -> "Signal"
            else -> "Unknown"
        }

    val actionLabel: String
        get() = when (action) {
            0 -> "Start recording"
            1 -> "Stop recording"
            2 -> "Marker"
            3 -> "Restart"
            else -> "Unknown"
        }
}

data class SchedulerConfigUiState(
    val version: Int,
    val generation: Long,
    val enabled: Boolean,
    val rules: List<SchedulerRuleUiState>,
    val profileCrc32: List<Long>,
    val profileGenerations: List<Long>,
)

data class AiRuntimeStatusUiState(
    val layoutReady: Boolean,
    val hasSelectedSlot: Boolean,
    val requestedEnabled: Boolean,
    val running: Boolean,
    val loadableBackend: Boolean,
    val activeSlot: Int?,
    val statusCode: Int,
    val runtimeWindowAddress: Long,
    val runtimeWindowBytes: Long,
    val inputBufferBytes: Long,
    val outputBufferBytes: Long,
    val executionTime: Long,
)

data class AiResidentSlotStatusUiState(
    val slot: Int,
    val present: Boolean,
    val statusCode: Int,
    val storageStartAddress: Long,
    val storageCapacityBytes: Long,
    val imageSizeBytes: Long,
    val imageFlags: Long,
    val runtimeRamAddress: Long,
    val requiredArenaBytes: Long,
    val inputBytes: Long,
)

data class BleLinkStatsUiState(
    val packetCount: Long,
    val missingPacketCount: Long,
    val sequenceErrorCount: Int,
    val lastRssiRaw: Int,
    val rxRateHz: Double,
) {
    val lossPercent: Double
        get() {
            val expectedPackets = packetCount + missingPacketCount
            return if (expectedPackets <= 0L) {
                0.0
            } else {
                missingPacketCount * 100.0 / expectedPackets.toDouble()
            }
    }
}

data class RssiSampleUiState(
    val timestampMs: Long,
    val valueDbm: Int,
)

/** A time-stamped snapshot of CE64 manufacturer advertisement telemetry. */
data class AdvertisementStatusSampleUiState(
    val timestampMs: Long,
    val voltage: Double?,
    val recording: Boolean?,
    val previewing: Boolean?,
    val failedSubsystems: Int?,
    val degradedSubsystems: Int?,
    val storageUsedPercent: Int?,
    val recordingSeconds: Long?,
    val lastEventCode: Int?,
) {
    val hasStateTelemetry: Boolean
        get() = recording != null || previewing != null ||
            failedSubsystems != null || degradedSubsystems != null
}

enum class BleOtaPhase {
    Idle,
    PackageReady,
    Staging,
    Verifying,
    ReadyToInstall,
    InstallRequested,
    Failed,
}

data class BleOtaUiState(
    val phase: BleOtaPhase = BleOtaPhase.Idle,
    val packageName: String = "",
    val generation: Long = 0L,
    val imageBytes: Int = 0,
    val imageCrc32: Long = 0L,
    val completedBlocks: Int = 0,
    val totalBlocks: Int = 0,
    val statusMessage: String = "No fused firmware file selected",
    val failureMessage: String = "",
) {
    val progressFraction: Float
        get() = if (totalBlocks > 0) completedBlocks.coerceIn(0, totalBlocks).toFloat() / totalBlocks else 0f

    val hasPreparedPackage: Boolean
        get() = phase != BleOtaPhase.Idle
}

data class DeviceSessionUiState(
    val id: String,
    val name: String,
    val address: String,
    val traceColorArgb: Int,
    val advertisedServiceMatch: Boolean = false,
    val namePrefixMatch: Boolean = false,
    val verifiedTransport: Boolean = false,
    val hostState: BleHostSessionState = BleHostSessionState.Disconnected,
    val statusText: String = "Disconnected",
    val syncText: String = "",
    val roleTag: String = "",
    val functionTag: String = "",
    val lastMessage: String = "",
    val lastFailure: String = "",
    val rssi: Int? = null,
    val rssiHistory: List<RssiSampleUiState> = emptyList(),
    val advertisementHistory: List<AdvertisementStatusSampleUiState> = emptyList(),
    val advertisedVoltage: Double? = null,
    val advertisedHealthStatus: Ce64AdvertisementStatus? = null,
    val hasAdvertisementTelemetry: Boolean = false,
    val lastSeenAtMs: Long = 0L,
    val previewPacketCount: Int = 0,
    val recTimePacketCount: Int = 0,
    val commandTxCount: Int = 0,
    val commandRxCount: Int = 0,
    val notificationRxCount: Int = 0,
    val legacyConfigBusyCount: Int = 0,
    val bleLinkStats: BleLinkStatsUiState? = null,
    val voltage: Double? = null,
    val usedSpaceMb: Double? = null,
    val recordingSeconds: Long = 0L,
    val previewSelection: PreviewSelection = PreviewSelection.Default,
    val previewPoints: List<Float> = emptyList(),
    val threshold: Float? = null,
    val digitalFlags: List<DigitalFlag> = emptyList(),
    val lastSyncMetric: SyncMetricUiState? = null,
    val liveSync: LiveSyncUiState? = null,
    val awaitingLiveSync: Boolean = false,
    val swVersion: String? = null,
    val hwVersion: String? = null,
    val parsedSystemParams: ParsedSystemParams? = null,
    val parsedCameraParams: ParsedCameraParams? = null,
    val parsedDsp1Params: ParsedDspParams? = null,
    val parsedDsp2Params: ParsedDspParams? = null,
    val stimControlStatus: StimControlStatus? = null,
    val stimControlStatuses: Map<Int, StimControlStatus> = emptyMap(),
    val systemParamHex: String = "",
    val dsp1ParamHex: String = "",
    val dsp2ParamHex: String = "",
    val cameraParamHex: String = "",
    val cameraPreviewPixels: Int = 0,
    val cameraPreviewImage: ByteArray = byteArrayOf(),
    val cameraPreviewFrameId: Int = 0,
    val cameraPreviewStreaming: Boolean = false,
    val cameraSnapshotPixels: Int = 0,
    val cameraSnapshotImage: ByteArray = byteArrayOf(),
    val cameraSnapshotFrameId: Int = 0,
    val impedanceSnapshot: ImpedanceSnapshotUiState = ImpedanceSnapshotUiState(),
    val spikeDetectorConfig: SpikeDetectorConfigUiState? = null,
    val recentSpikeEvents: List<LiveSpikeEventUiState> = emptyList(),
    val spikeViewerDropCount: Long = 0L,
    val spectrumConfig: SpectrumConfigUiState? = null,
    val spectrumSnapshot: SpectrumSnapshotUiState? = null,
    val cpuLoad: CpuLoadUiState? = null,
    val schedulerStatus: SchedulerStatusUiState? = null,
    val schedulerConfig: SchedulerConfigUiState? = null,
    val schedulerProfileReceiveMask: Int = 0,
    val schedulerProfileWriteMask: Int = 0,
    val records: List<RecordSummary> = emptyList(),
    val ledOn: Boolean = false,
    val gpio0Mode: GpioMode = GpioMode.Unknown,
    val gpio1Mode: GpioMode = GpioMode.Unknown,
    val syncLogPath: String = "",
    val recordExportPath: String = "",
    val impedanceExportPath: String = "",
    val triggerWaveformEnabled: Boolean = false,
    val triggeredWaveformBlockCount: Int = 0,
    val triggeredWaveformBlocksByLane: Map<Int, Int> = emptyMap(),
    val latestTriggeredWaveforms: Map<Int, List<Int>> = emptyMap(),
    val lastTriggeredWaveformBytes: Int = 0,
    val triggerWaveformCaptureBytes: Long = 0L,
    val triggerWaveformCapturePath: String = "",
    val triggerWaveformCaptureActive: Boolean = false,
    val aiRuntimeStatus: AiRuntimeStatusUiState? = null,
    val aiResidentSlotStatuses: Map<Int, AiResidentSlotStatusUiState> = emptyMap(),
    val bleOta: BleOtaUiState = BleOtaUiState(),
    val recentEvents: List<SessionEventUiState> = emptyList(),
    val isLegacyWakeFirmware: Boolean = false,
    val isActive: Boolean = false,
    val recorderBackedLiveSignal: Boolean = false,
    /** True after the host has explicitly sent the live-waveform start command (0x40). */
    val waveformPreviewActive: Boolean = false,
) {
    /**
     * A shared 0xFFF0 service alone is not a device identity: unrelated BLE
     * peripherals can advertise it. A device is eligible only after a known
     * WILD/CE name, an authenticated CE64 status advertisement, or a completed
     * protocol handshake has confirmed it.
     */
    val bulkConnectEligible: Boolean
        get() = verifiedTransport || namePrefixMatch || advertisedHealthStatus != null

    val gpio0High: Boolean
        get() = gpio0Mode == GpioMode.High

    val gpio1High: Boolean
        get() = gpio1Mode == GpioMode.High

    val isConnected: Boolean
        get() = hostState in setOf(
            BleHostSessionState.Connected,
            BleHostSessionState.Syncing,
            BleHostSessionState.Synced,
            BleHostSessionState.Previewing,
            BleHostSessionState.StartingRecording,
            BleHostSessionState.Recording,
            BleHostSessionState.StoppingRecording,
        )

    val isRecordingLike: Boolean
        get() = hostState in setOf(
            BleHostSessionState.StartingRecording,
            BleHostSessionState.Recording,
            BleHostSessionState.StoppingRecording,
        )

    val isLinkingLike: Boolean
        get() = hostState in setOf(
            BleHostSessionState.Connecting,
            BleHostSessionState.Reconnecting,
            BleHostSessionState.Disconnecting,
        )
}
