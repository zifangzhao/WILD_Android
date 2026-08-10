import CoreBluetooth
import Foundation

enum Ce32Protocol {
    static let maxChannelGroup = 8
    static let serviceUuid = CBUUID(string: "0000FFF0-0000-1000-8000-00805F9B34FB")
    static let rxUuid = CBUUID(string: "0000FFF1-0000-1000-8000-00805F9B34FB")
    static let txUuid = CBUUID(string: "0000FFF2-0000-1000-8000-00805F9B34FB")
    static let legacyTxUuid = CBUUID(string: "0000FFF3-0000-1000-8000-00805F9B34FB")
    static let deviceInfoServiceUuid = CBUUID(string: "0000180A-0000-1000-8000-00805F9B34FB")
    static let swVersionUuid = CBUUID(string: "00002A28-0000-1000-8000-00805F9B34FB")
    static let hwVersionUuid = CBUUID(string: "00002A27-0000-1000-8000-00805F9B34FB")

    static let sectorData: UInt32 = 0x2000
    static let bleLogEntriesPerBlock = 63
    static let bleLogBlockScanLimit = 1024

    private static let acceptedPrefixes = ["CE32", "CE64", "CE128", "WILD", "XENP"]

    static func matchesKnownNamePrefix(_ name: String?) -> Bool {
        let trimmed = (name ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return false }
        return acceptedPrefixes.contains { trimmed.range(of: $0, options: [.caseInsensitive, .anchored]) != nil }
    }

    static func payloadLength(for commandId: UInt8) -> Int? {
        switch commandId {
        case 0x10: return 60
        case 0x30, 0x31, 0x40, 0x41, 0x8B: return 0
        case 0x42: return 1
        case 0x51: return 128
        case 0x52: return 130
        case 0x53: return 128
        case 0x82: return 15
        case 0x85: return 32
        case 0x8C: return 16
        case 0x8D: return 24
        case 0x8E: return 25
        case 0x8F: return 4
        case 0x90, 0x91, 0x92: return 512
        case 0x94: return 512
        case 0x95: return 4
        case 0x9D: return 4
        case 0x9F: return 41
        case 0xAC: return 161
        case 0xAD: return 136
        case 0xAE: return 512
        case 0xAF: return 10
        case 0xB0, 0xB1: return 2
        case 0xC0: return 1
        case 0xF0, 0xF1, 0xF2, 0xF3, 0xF4: return 512
        case 0xEE: return 3
        default: return nil
        }
    }

    static func buildPreviewStart() -> Data {
        Data([0x3C, 0x40, 0x3E])
    }

    static func buildPreviewStop() -> Data {
        Data([0x3C, 0x41, 0x3E])
    }

    static func buildPreviewSelect(_ selection: PreviewSelection) -> Data {
        Data([0x3C, 0x42, selection.protocolByte, 0x3E])
    }

    static func buildRecordStart() -> Data {
        Data([0x3C, 0x30, 0x3E])
    }

    static func buildRecordStop() -> Data {
        Data([0x3C, 0x31, 0x3E])
    }

    static func buildRecordStopAndPreviewStop() -> Data {
        Data([0x3C, 0x31, 0x3E, 0x3C, 0x41, 0x3E])
    }

    static func buildSyncReset() -> Data {
        Data([0x3C, 0x89, 0x3E])
    }

    static func buildSyncStart() -> Data {
        Data([0x3C, 0x82, 0x81, 0x00, 0x00, 0x3E])
    }

    static func buildFastHandshake() -> Data {
        Data([0x3C, 0x80, 0x3E])
    }

    static func buildReadSystemParams() -> Data {
        Data([0x3C, 0x90, 0x3E])
    }

    static func buildReadDspParams(index: Int) -> Data {
        Data([0x3C, UInt8(0x91 + max(0, min(index, 1))), 0x3E])
    }

    static func buildSnapshotRequest(preview: Bool = false) -> Data {
        Data([0x3C, 0x9E, preview ? 0x01 : 0x00, 0x3E])
    }

    static func buildReadCameraParams() -> Data {
        Data([0x3C, 0x9D, 0x3E])
    }

    static func buildImpedanceTest() -> Data {
        Data([0x3C, 0x51, 0x3E])
    }

    static func buildEnterSleep() -> Data {
        Data([0x3C, 0xA0, 0x0A, 0x3E])
    }

    static func buildSoftwareReset() -> Data {
        Data([0x3C, 0xAB, 0xBA, 0x3E])
    }

    static func buildSystemBootloader() -> Data {
        Data([0x3C, 0xAC, 0xCA, 0x3E])
    }

    static func buildFirmwareImageUpdate() -> Data {
        Data([0x3C, 0xAE, 0x3E])
    }

    static func buildLogBlockRequest(blockIndex: Int) -> Data {
        Data([0x3C, 0x94, UInt8(blockIndex & 0xFF), UInt8((blockIndex >> 8) & 0xFF), 0x3E])
    }

    static func buildDeleteRecords(deleteCount: Int) -> Data {
        Data([0x3C, 0x95, UInt8(max(0, min(deleteCount, 255))), 0x3E])
    }

    static func buildLedCommand(enabled: Bool) -> Data {
        let state = enabled ? 1 : 0
        return Data([0x3C, 0x61, 0x00, UInt8(0x02 | (state << 3)), 0x3E])
    }

    static func buildGpio0Command(mode: GpioMode) -> Data {
        Data([0x3C, 0x61, 0x02, mode.commandByte, 0x3E])
    }

    static func buildGpio1Command(mode: GpioMode) -> Data {
        Data([0x3C, 0x61, 0x03, mode.commandByte, 0x3E])
    }

    static func buildTriggerWaveform(enabled: Bool) -> Data {
        Data([0x3C, 0x43, enabled ? 0x01 : 0x00, 0x3E])
    }

    static func buildRtcSetCommand(now: Date = Date()) -> Data {
        let calendar = Calendar.current
        let components = calendar.dateComponents([.weekday, .month, .day, .year, .hour, .minute, .second, .nanosecond], from: now)
        let weekday = components.weekday ?? 1
        let wildWeekday = weekday == 1 ? 7 : weekday - 1
        var payload = Data([
            UInt8(max(0, min(wildWeekday, 255))),
            UInt8(components.month ?? 1),
            UInt8(components.day ?? 1),
            UInt8(max(0, min((components.year ?? 2000) - 2000, 255))),
            UInt8(components.hour ?? 0),
            UInt8(components.minute ?? 0),
            UInt8(components.second ?? 0),
            0x00,
        ])
        let ticksWithinSecond = UInt32((components.nanosecond ?? 0) / 10_000)
        payload.appendUInt32LE(ticksWithinSecond)
        payload.appendUInt32LE(9_999)
        payload.appendUInt32LE(0)
        payload.appendUInt32LE(0)
        return frame(0x8A, payload: payload)
    }

    static func frame(_ commandId: UInt8, payload: Data = Data()) -> Data {
        var data = Data([0x3C, commandId])
        data.append(payload)
        data.append(0x3E)
        return data
    }

    static func parsePreviewPacket(_ payload: Data) -> PreviewPacket? {
        guard payload.count >= 136, let voltageRaw = payload.u16LE(at: 0), let usedBlocks = payload.u32LE(at: 2) else {
            return nil
        }
        let digitalFlags = parseDigitalFlags(payload[6], payload[7])
        var samples: [Float] = []
        samples.reserveCapacity(64)
        var offset = 8
        for _ in 0..<64 {
            guard let sample = payload.i16LE(at: offset) else { return nil }
            samples.append(Float(sample))
            offset += 2
        }
        return PreviewPacket(
            samples: samples,
            voltage: Double(voltageRaw) / 65535.0 * 3.3 * 4.0,
            usedSpaceMb: Double(usedBlocks) * 512.0 / 1024.0 / 1024.0,
            digitalFlags: digitalFlags
        )
    }

    static func parseRecTimePacket(_ payload: Data) -> RecTimePacket? {
        guard let seconds = payload.u32LE(at: 0) else { return nil }
        let voltage = payload.u16LE(at: 4).map { Double($0) / 65535.0 * 3.3 * 4.0 }
        let used = payload.u32LE(at: 6).map { Double($0) * 512.0 / 1024.0 / 1024.0 }
        return RecTimePacket(recordingSeconds: seconds, voltage: voltage, usedSpaceMb: used)
    }

    static func parseSystemParams(_ payload: Data) -> ParsedSystemParams? {
        guard payload.count >= 440 else { return nil }
        return ParsedSystemParams(
            fs: Int(payload.u32LE(at: 0) ?? 0),
            channelCounts: (0..<maxChannelGroup).map { Int(payload.u16LE(at: 8 + $0 * 2) ?? 0) },
            samplingRates: (0..<maxChannelGroup).map { Int(payload.u32LE(at: 40 + $0 * 4) ?? 0) },
            stimMode: Int(payload.u32LE(at: 160) ?? 0),
            closedLoopMode: Int(payload.u32LE(at: 164) ?? 0),
            previewChannelBankRaw: Int(payload.u32LE(at: 280) ?? 0),
            systemStatus: Int(payload.u32LE(at: 284) ?? 0),
            firmwareVersion: Int(payload.u16LE(at: 328) ?? 0),
            hardwareVersion: Int(payload.u16LE(at: 330) ?? 0),
            baseFs: Int(payload.u32LE(at: 436) ?? 0)
        )
    }

    static func parseCameraParams(_ payload: Data) -> ParsedCameraParams? {
        guard let reg0 = payload.u16LE(at: 0), let reg1 = payload.u16LE(at: 2) else { return nil }
        return ParsedCameraParams(reg0: Int(reg0), reg1: Int(reg1))
    }

    static func parseDspParams(_ payload: Data) -> ParsedDspParams? {
        guard payload.count >= 144 else { return nil }
        return ParsedDspParams(
            formula: Int(payload.u32LE(at: 128) ?? 0),
            filterType: Int(payload.u32LE(at: 132) ?? 0),
            func2: Int(payload.u32LE(at: 136) ?? 0),
            maOrder: Int(payload.u32LE(at: 140) ?? 0),
            channels: (0..<4).map { Int(payload[$0]) }
        )
    }

    static func parseStimControl(_ payload: Data) -> StimControlStatus? {
        guard payload.count >= 60 else { return nil }
        return StimControlStatus(
            id: Int(payload.i32LE(at: 56) ?? 0),
            triggerState: Int(payload.i32LE(at: 40) ?? 0),
            stimCount: Int(payload.i32LE(at: 48) ?? 0),
            count: Int(payload.i32LE(at: 52) ?? 0)
        )
    }

    static func parseAsciiMessage(_ payload: Data) -> String {
        String(data: payload, encoding: .utf8)?
            .trimmingCharacters(in: CharacterSet(charactersIn: "\0").union(.whitespacesAndNewlines)) ?? ""
    }

    static func bytesToHex(_ payload: Data) -> String {
        payload.map { String(format: "%02X", $0) }.joined(separator: " ")
    }

    static func alignUpAu(_ sector: UInt32) -> UInt32 {
        let auSectors: UInt32 = 0x2000
        let remainder = sector % auSectors
        return remainder == 0 ? sector : sector + auSectors - remainder
    }

    private static func parseDigitalFlags(_ first: UInt8, _ second: UInt8) -> [DigitalFlag] {
        [
            DigitalFlag(name: "DET1", active: first & 0x01 != 0),
            DigitalFlag(name: "STIM1", active: first & 0x02 != 0),
            DigitalFlag(name: "DET2", active: first & 0x04 != 0),
            DigitalFlag(name: "STIM2", active: first & 0x08 != 0),
            DigitalFlag(name: "AUX1", active: first & 0x10 != 0),
            DigitalFlag(name: "AUX2", active: first & 0x20 != 0),
            DigitalFlag(name: "AUX3", active: first & 0x40 != 0),
            DigitalFlag(name: "EXT1", active: first & 0x80 != 0),
            DigitalFlag(name: "EXT2", active: second & 0x01 != 0),
            DigitalFlag(name: "EXT3", active: second & 0x02 != 0),
            DigitalFlag(name: "UDV", active: second & 0x04 != 0),
            DigitalFlag(name: "DSP_WAIT", active: second & 0x08 != 0),
            DigitalFlag(name: "DSP_CAL", active: second & 0x10 != 0),
            DigitalFlag(name: "DSP_READY", active: second & 0x20 != 0),
            DigitalFlag(name: "LD1", active: second & 0x40 != 0),
            DigitalFlag(name: "LD2", active: second & 0x80 != 0),
        ]
    }
}

extension Data {
    func u16LE(at offset: Int) -> UInt16? {
        guard offset >= 0, count >= offset + 2 else { return nil }
        return UInt16(self[offset]) | (UInt16(self[offset + 1]) << 8)
    }

    func i16LE(at offset: Int) -> Int16? {
        u16LE(at: offset).map { Int16(bitPattern: $0) }
    }

    func u32LE(at offset: Int) -> UInt32? {
        guard offset >= 0, count >= offset + 4 else { return nil }
        return UInt32(self[offset]) |
            (UInt32(self[offset + 1]) << 8) |
            (UInt32(self[offset + 2]) << 16) |
            (UInt32(self[offset + 3]) << 24)
    }

    func i32LE(at offset: Int) -> Int32? {
        u32LE(at: offset).map { Int32(bitPattern: $0) }
    }

    mutating func appendUInt32LE(_ value: UInt32) {
        append(UInt8(value & 0xFF))
        append(UInt8((value >> 8) & 0xFF))
        append(UInt8((value >> 16) & 0xFF))
        append(UInt8((value >> 24) & 0xFF))
    }
}
