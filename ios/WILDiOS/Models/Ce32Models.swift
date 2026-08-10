import Foundation

enum BleHostSessionState: String, CaseIterable, Identifiable {
    case disconnected = "Disconnected"
    case connecting = "Connecting"
    case connected = "Connected"
    case syncing = "Syncing"
    case synced = "Synced"
    case previewing = "Previewing"
    case startingRecording = "Starting Recording"
    case recording = "Recording"
    case stoppingRecording = "Stopping Recording"
    case reconnecting = "Reconnecting"
    case disconnecting = "Disconnecting"
    case error = "Error"

    var id: String { rawValue }
}

enum ControlScope: String, CaseIterable, Identifiable {
    case activeDevice = "Active"
    case selectedDevices = "Selected"
    case allConnected = "All"

    var id: String { rawValue }
}

enum GpioMode: String, CaseIterable, Identifiable {
    case unknown = "--"
    case analog = "Analog"
    case input = "Input"
    case low = "Low"
    case high = "High"

    var id: String { rawValue }

    var commandByte: UInt8 {
        switch self {
        case .unknown, .analog: return 0x00
        case .input: return 0x01
        case .low: return 0x02
        case .high: return 0x0A
        }
    }
}

struct DigitalFlag: Identifiable, Hashable {
    var name: String
    var active: Bool
    var id: String { name }
}

struct RecordSummary: Identifiable, Hashable {
    var index: Int
    var startSector: UInt32
    var endSector: UInt32
    var sizeSectors: UInt32

    var id: Int { index }
    var sizeMb: Double { Double(sizeSectors) * 512.0 / 1024.0 / 1024.0 }
}

struct PreviewSelection: Hashable {
    var auxMode: Bool
    var index: Int

    var protocolByte: UInt8 {
        let safeIndex = UInt8(max(0, min(index, 0x7F)))
        return auxMode ? (safeIndex | 0x80) : safeIndex
    }

    func optionCount(ephysChannelCount: Int? = nil) -> Int {
        if auxMode {
            return (ephysChannelCount ?? 0) > 32 ? Self.fullAuxLabels.count : Self.basicAuxLabels.count
        }
        return max(ephysChannelCount ?? 8, 1)
    }

    func normalized(ephysChannelCount: Int? = nil) -> PreviewSelection {
        let maxIndex = max(optionCount(ephysChannelCount: ephysChannelCount) - 1, 0)
        return PreviewSelection(auxMode: auxMode, index: min(max(index, 0), maxIndex))
    }

    func label(ephysChannelCount: Int? = nil) -> String {
        if auxMode {
            let labels = (ephysChannelCount ?? 0) > 32 ? Self.fullAuxLabels : Self.basicAuxLabels
            return labels.indices.contains(index) ? labels[index] : "Aux \(index + 1)"
        }
        return "E\(index + 1)"
    }

    static let `default` = PreviewSelection(auxMode: false, index: 0)

    static let basicAuxLabels = [
        "Digital Signal",
        "Accelerometer X",
        "Accelerometer Y",
        "Accelerometer Z",
        "Vbat",
        "Test Signal",
        "DSP output A",
        "DSP output B",
    ]

    static let fullAuxLabels = [
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
    ]
}

struct PreviewPacket {
    var samples: [Float]
    var voltage: Double
    var usedSpaceMb: Double
    var digitalFlags: [DigitalFlag]
}

struct RecTimePacket {
    var recordingSeconds: UInt32
    var voltage: Double?
    var usedSpaceMb: Double?
}

struct ParsedSystemParams {
    var fs: Int
    var channelCounts: [Int]
    var samplingRates: [Int]
    var stimMode: Int
    var closedLoopMode: Int
    var previewChannelBankRaw: Int
    var systemStatus: Int
    var firmwareVersion: Int
    var hardwareVersion: Int
    var baseFs: Int

    var ephysSamplingRate: Int { samplingRates.indices.contains(0) ? samplingRates[0] : 0 }
    var ephysChannelCount: Int { channelCounts.first(where: { $0 > 0 }) ?? 8 }

    var previewSelection: PreviewSelection {
        PreviewSelection(
            auxMode: (previewChannelBankRaw & 0x80) != 0,
            index: previewChannelBankRaw & 0x7F
        ).normalized(ephysChannelCount: ephysChannelCount)
    }
}

struct ParsedCameraParams {
    var reg0: Int
    var reg1: Int
}

struct ParsedDspParams {
    var formula: Int
    var filterType: Int
    var func2: Int
    var maOrder: Int
    var channels: [Int]
}

struct StimControlStatus {
    var id: Int
    var triggerState: Int
    var stimCount: Int
    var count: Int

    var stateLabel: String {
        switch triggerState {
        case 0: return "Idle"
        case 1: return "Armed"
        case 2: return "Triggered"
        case 3: return "Delivering"
        default: return "State \(triggerState)"
        }
    }
}

struct DeviceSession: Identifiable {
    var id: String
    var name: String
    var address: String
    var advertisedServiceMatch: Bool = false
    var namePrefixMatch: Bool = false
    var verifiedTransport: Bool = false
    var hostState: BleHostSessionState = .disconnected
    var statusText: String = "Disconnected"
    var syncText: String = ""
    var lastMessage: String = ""
    var lastFailure: String = ""
    var rssi: Int?
    var advertisedVoltage: Double?
    var lastSeenAt: Date = Date()
    var previewPacketCount: Int = 0
    var recTimePacketCount: Int = 0
    var commandTxCount: Int = 0
    var commandRxCount: Int = 0
    var notificationRxCount: Int = 0
    var voltage: Double?
    var usedSpaceMb: Double?
    var recordingSeconds: UInt32 = 0
    var previewSelection: PreviewSelection = .default
    var previewPoints: [Float] = []
    var digitalFlags: [DigitalFlag] = []
    var swVersion: String?
    var hwVersion: String?
    var parsedSystemParams: ParsedSystemParams?
    var parsedCameraParams: ParsedCameraParams?
    var parsedDsp1Params: ParsedDspParams?
    var parsedDsp2Params: ParsedDspParams?
    var stimControlStatus: StimControlStatus?
    var systemParamHex: String = ""
    var dsp1ParamHex: String = ""
    var dsp2ParamHex: String = ""
    var cameraParamHex: String = ""
    var records: [RecordSummary] = []
    var ledOn: Bool = false
    var gpio0Mode: GpioMode = .unknown
    var gpio1Mode: GpioMode = .unknown
    var triggerWaveformEnabled: Bool = false
    var triggeredWaveformBlockCount: Int = 0
    var isActive: Bool = false

    var bulkConnectEligible: Bool {
        verifiedTransport || advertisedServiceMatch || namePrefixMatch
    }

    var isConnected: Bool {
        switch hostState {
        case .connected, .syncing, .synced, .previewing, .startingRecording, .recording, .stoppingRecording:
            return true
        default:
            return false
        }
    }

    var isRecordingLike: Bool {
        hostState == .startingRecording || hostState == .recording || hostState == .stoppingRecording
    }

    var isLinkingLike: Bool {
        hostState == .connecting || hostState == .reconnecting || hostState == .disconnecting
    }
}

final class PreviewBuffer {
    private var values: [Float]
    private var writeIndex = 0
    private var size = 0

    init(capacity: Int) {
        values = Array(repeating: 0, count: max(capacity, 1))
    }

    func append(_ samples: [Float]) {
        for sample in samples {
            values[writeIndex] = sample
            writeIndex = (writeIndex + 1) % values.count
            if size < values.count {
                size += 1
            }
        }
    }

    func snapshot(maxPoints: Int? = nil) -> [Float] {
        guard size > 0 else { return [] }
        let points = min(size, maxPoints ?? values.count)
        let start = (writeIndex - points + values.count) % values.count
        return (0..<points).map { values[(start + $0) % values.count] }
    }

    func clear() {
        writeIndex = 0
        size = 0
    }
}
