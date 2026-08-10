import Foundation

final class Ce32FrameParser {
    private enum ParseMode {
        case idle
        case framedCommandId
        case framedPayload
        case framedTerminator
        case rawCommandId
        case rawPayload
    }

    private var mode: ParseMode = .idle
    private var commandId: UInt8 = 0
    private var expectedPayloadLength = -1
    private var payload = Data()
    private var outOfFrameBytes = Data()

    private let onOutOfFrameBytes: (Data) -> Void
    private let onFrame: (UInt8, Data) -> Void

    init(
        onOutOfFrameBytes: @escaping (Data) -> Void = { _ in },
        onFrame: @escaping (UInt8, Data) -> Void
    ) {
        self.onOutOfFrameBytes = onOutOfFrameBytes
        self.onFrame = onFrame
        payload.reserveCapacity(512)
        outOfFrameBytes.reserveCapacity(64)
    }

    func push(_ chunk: Data) {
        for byte in chunk {
            consume(byte)
        }
        flushOutOfFrameBytes()
    }

    func reset() {
        resetParserState()
        outOfFrameBytes.removeAll(keepingCapacity: true)
    }

    private func resetParserState() {
        mode = .idle
        commandId = 0
        expectedPayloadLength = -1
        payload.removeAll(keepingCapacity: true)
    }

    private func flushOutOfFrameBytes() {
        guard !outOfFrameBytes.isEmpty else { return }
        onOutOfFrameBytes(outOfFrameBytes)
        outOfFrameBytes.removeAll(keepingCapacity: true)
    }

    private func consume(_ byte: UInt8) {
        switch mode {
        case .idle:
            switch byte {
            case 0x3C:
                flushOutOfFrameBytes()
                startFramedPacket()
            case 0xAD:
                flushOutOfFrameBytes()
                startRawPacket()
            default:
                outOfFrameBytes.append(byte)
            }

        case .framedCommandId:
            if !beginCommand(byte, payloadMode: .framedPayload, zeroPayloadMode: .framedTerminator) {
                restartFromPotentialLeadByte(byte)
            }

        case .framedPayload:
            payload.append(byte)
            if payload.count >= expectedPayloadLength {
                mode = .framedTerminator
            }

        case .framedTerminator:
            if byte == 0x3E {
                emitFrame()
            } else {
                restartFromPotentialLeadByte(byte)
            }

        case .rawCommandId:
            if !beginCommand(byte, payloadMode: .rawPayload, zeroPayloadMode: .rawPayload) {
                restartFromPotentialLeadByte(byte)
            } else if expectedPayloadLength == 0 {
                emitFrame()
            }

        case .rawPayload:
            payload.append(byte)
            if payload.count >= expectedPayloadLength {
                emitFrame()
            }
        }
    }

    private func startFramedPacket() {
        resetParserState()
        mode = .framedCommandId
    }

    private func startRawPacket() {
        resetParserState()
        mode = .rawCommandId
    }

    private func beginCommand(_ value: UInt8, payloadMode: ParseMode, zeroPayloadMode: ParseMode) -> Bool {
        commandId = value
        expectedPayloadLength = Ce32Protocol.payloadLength(for: value) ?? -1
        guard expectedPayloadLength >= 0 else {
            resetParserState()
            return false
        }

        mode = expectedPayloadLength == 0 ? zeroPayloadMode : payloadMode
        return true
    }

    private func emitFrame() {
        let emittedCommandId = commandId
        let emittedPayload = payload
        resetParserState()
        onFrame(emittedCommandId, emittedPayload)
    }

    private func restartFromPotentialLeadByte(_ value: UInt8) {
        resetParserState()
        switch value {
        case 0x3C:
            startFramedPacket()
        case 0xAD:
            startRawPacket()
        default:
            outOfFrameBytes.append(value)
        }
    }
}
