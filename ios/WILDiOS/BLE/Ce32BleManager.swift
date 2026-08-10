import CoreBluetooth
import Foundation

final class Ce32BleManager: NSObject, ObservableObject {
    @Published private(set) var sessions: [DeviceSession] = []
    @Published private(set) var isScanning = false
    @Published var statusBanner = ""
    @Published var activeSessionId: String?
    @Published var controlScope: ControlScope = .activeDevice
    @Published var selectedSessionIds: Set<String> = []

    private var central: CBCentralManager!
    private var handles: [String: PeripheralHandle] = [:]
    private var sessionsById: [String: DeviceSession] = [:]

    override init() {
        super.init()
        central = CBCentralManager(delegate: self, queue: .main)
    }

    var activeSession: DeviceSession? {
        sessions.first { $0.id == activeSessionId }
    }

    var connectedSessions: [DeviceSession] {
        sessions.filter(\.isConnected)
    }

    var selectedSessions: [DeviceSession] {
        selectedSessionIds.compactMap { id in sessions.first { $0.id == id } }
    }

    var controlTargetIds: [String] {
        switch controlScope {
        case .activeDevice:
            return activeSessionId.map { [$0] } ?? []
        case .selectedDevices:
            return selectedSessions.filter(\.isConnected).map(\.id)
        case .allConnected:
            return connectedSessions.map(\.id)
        }
    }

    func startScan() {
        guard central.state == .poweredOn else {
            statusBanner = "Bluetooth is not powered on."
            return
        }
        central.scanForPeripherals(withServices: nil, options: [
            CBCentralManagerScanOptionAllowDuplicatesKey: false,
        ])
        isScanning = true
        statusBanner = "Scanning for CE32 / WILD BLE devices..."
    }

    func stopScan() {
        central.stopScan()
        isScanning = false
        statusBanner = "Scan stopped."
    }

    func clearBanner() {
        statusBanner = ""
    }

    func setActiveSession(_ id: String) {
        activeSessionId = id
        for sessionId in sessionsById.keys {
            sessionsById[sessionId]?.isActive = sessionId == id
        }
        publishSessions()
    }

    func toggleSelected(_ id: String) {
        if selectedSessionIds.contains(id) {
            selectedSessionIds.remove(id)
        } else if sessionsById[id]?.isConnected == true {
            selectedSessionIds.insert(id)
            controlScope = .selectedDevices
        }
    }

    func selectAllConnected() {
        selectedSessionIds = Set(connectedSessions.map(\.id))
        if !selectedSessionIds.isEmpty {
            controlScope = .selectedDevices
        }
    }

    func clearSelected() {
        selectedSessionIds.removeAll()
        if controlScope == .selectedDevices {
            controlScope = .activeDevice
        }
    }

    func connect(_ id: String) {
        guard let handle = handles[id] else { return }
        if isScanning {
            central.stopScan()
            isScanning = false
        }
        setActiveSession(id)
        updateSession(id) {
            $0.hostState = .connecting
            $0.statusText = "Connecting..."
            $0.lastFailure = ""
        }
        central.connect(handle.peripheral, options: nil)
    }

    func disconnect(_ id: String) {
        guard let handle = handles[id] else { return }
        updateSession(id) {
            $0.hostState = .disconnecting
            $0.statusText = "Disconnecting..."
        }
        central.cancelPeripheralConnection(handle.peripheral)
    }

    func disconnectAll() {
        connectedSessions.forEach { disconnect($0.id) }
    }

    func startPreview(targetIds: [String]? = nil) {
        for id in resolvedTargets(targetIds) {
            guard let selection = sessionsById[id]?.previewSelection else { continue }
            let ok = writeCommand(id, Ce32Protocol.buildPreviewStart(), label: "preview start") &&
                writeCommand(id, Ce32Protocol.buildPreviewSelect(selection), label: "preview selection") &&
                writeCommand(id, Ce32Protocol.buildPreviewStart(), label: "preview start")
            if ok {
                updateSession(id) {
                    $0.hostState = .previewing
                    $0.statusText = "Previewing"
                    $0.lastMessage = "Preview start requested"
                }
            }
        }
    }

    func stopPreview(targetIds: [String]? = nil) {
        for id in resolvedTargets(targetIds) {
            if writeCommand(id, Ce32Protocol.buildPreviewStop(), label: "preview stop") {
                updateSession(id) {
                    $0.hostState = .synced
                    $0.statusText = "Synced"
                    $0.lastMessage = "Preview stop requested"
                }
            }
        }
    }

    func startRecording(targetIds: [String]? = nil) {
        for id in resolvedTargets(targetIds) {
            let selection = sessionsById[id]?.previewSelection ?? .default
            let ok = writeCommand(id, Ce32Protocol.buildRecordStart(), label: "record start") &&
                writeCommand(id, Ce32Protocol.buildPreviewStart(), label: "preview start during record") &&
                writeCommand(id, Ce32Protocol.buildPreviewSelect(selection), label: "preview selection") &&
                writeCommand(id, Ce32Protocol.buildPreviewStart(), label: "preview start")
            if ok {
                updateSession(id) {
                    $0.hostState = .startingRecording
                    $0.statusText = "Starting recording..."
                    $0.recordingSeconds = 0
                    $0.lastMessage = "Record start requested"
                }
            }
        }
    }

    func stopRecording(targetIds: [String]? = nil) {
        for id in resolvedTargets(targetIds) {
            if writeCommand(id, Ce32Protocol.buildRecordStopAndPreviewStop(), label: "record stop sequence") {
                updateSession(id) {
                    $0.hostState = .stoppingRecording
                    $0.statusText = "Stopping recording..."
                    $0.lastMessage = "Record stop requested"
                }
            }
        }
    }

    func setPreviewSelection(_ selection: PreviewSelection, for id: String) {
        let normalized = selection.normalized(ephysChannelCount: sessionsById[id]?.parsedSystemParams?.ephysChannelCount)
        updateSession(id) {
            $0.previewSelection = normalized
            if var params = $0.parsedSystemParams {
                params.previewChannelBankRaw = Int(normalized.protocolByte)
                $0.parsedSystemParams = params
            }
        }
        _ = writeCommand(id, Ce32Protocol.buildPreviewSelect(normalized), label: "preview source select")
    }

    func requestResync(targetIds: [String]? = nil) {
        for id in resolvedTargets(targetIds) {
            updateSession(id) {
                $0.hostState = .syncing
                $0.statusText = "Syncing..."
                $0.syncText = "Sync: starting"
            }
            _ = writeCommand(id, Ce32Protocol.buildSyncReset(), label: "sync reset")
            _ = writeCommand(id, Ce32Protocol.buildSyncStart(), label: "sync start")
        }
    }

    func requestSystemParams(targetIds: [String]? = nil) {
        for id in resolvedTargets(targetIds) {
            _ = writeCommand(id, Ce32Protocol.buildReadSystemParams(), label: "read system params")
        }
    }

    func requestAllParams(targetIds: [String]? = nil) {
        for id in resolvedTargets(targetIds) {
            _ = writeCommand(id, Ce32Protocol.buildReadSystemParams(), label: "read system params")
            _ = writeCommand(id, Ce32Protocol.buildReadDspParams(index: 0), label: "read dsp1")
            _ = writeCommand(id, Ce32Protocol.buildReadDspParams(index: 1), label: "read dsp2")
            _ = writeCommand(id, Ce32Protocol.buildReadCameraParams(), label: "read camera params")
        }
    }

    func requestImpedance(targetIds: [String]? = nil) {
        for id in resolvedTargets(targetIds) {
            _ = writeCommand(id, Ce32Protocol.buildImpedanceTest(), label: "impedance test")
        }
    }

    func requestSleep() {
        guard let id = activeSessionId else { return }
        if writeCommand(id, Ce32Protocol.buildEnterSleep(), label: "sleep") {
            disconnect(id)
        }
    }

    func requestSoftwareReset() {
        guard let id = activeSessionId else { return }
        if writeCommand(id, Ce32Protocol.buildSoftwareReset(), label: "software reset") {
            disconnect(id)
        }
    }

    func requestBootloader() {
        guard let id = activeSessionId else { return }
        if writeCommand(id, Ce32Protocol.buildSystemBootloader(), label: "system bootloader") {
            disconnect(id)
        }
    }

    func requestFirmwareUpdate() {
        guard let id = activeSessionId else { return }
        if writeCommand(id, Ce32Protocol.buildFirmwareImageUpdate(), label: "firmware update") {
            disconnect(id)
        }
    }

    func refreshRecordList() {
        guard let id = activeSessionId, let handle = handles[id], sessionsById[id]?.isConnected == true else {
            statusBanner = "Connect to a device before reading records."
            return
        }
        handle.recordScan = RecordScan(blockIndex: 0, previousSector: Ce32Protocol.sectorData, records: [])
        if writeCommand(id, Ce32Protocol.buildLogBlockRequest(blockIndex: 0), label: "read record block") {
            updateSession(id) {
                $0.lastMessage = "Record refresh requested"
            }
        }
    }

    func deleteLastRecord() {
        guard let id = activeSessionId else { return }
        _ = writeCommand(id, Ce32Protocol.buildDeleteRecords(deleteCount: 1), label: "delete last record")
    }

    func deleteAllRecords() {
        guard let id = activeSessionId else { return }
        _ = writeCommand(id, Ce32Protocol.buildDeleteRecords(deleteCount: 255), label: "delete records")
    }

    private func resolvedTargets(_ targetIds: [String]?) -> [String] {
        let ids = targetIds ?? controlTargetIds
        return ids.filter { sessionsById[$0]?.isConnected == true }
    }

    @discardableResult
    private func writeCommand(_ id: String, _ data: Data, label: String) -> Bool {
        guard let handle = handles[id], let characteristic = handle.txCharacteristic ?? handle.legacyTxCharacteristic else {
            updateSession(id) {
                $0.lastFailure = "Missing TX characteristic for \(label)"
            }
            return false
        }

        let writeType: CBCharacteristicWriteType = characteristic.properties.contains(.writeWithoutResponse) ? .withoutResponse : .withResponse
        handle.peripheral.writeValue(data, for: characteristic, type: writeType)
        updateSession(id) {
            $0.commandTxCount += 1
            $0.lastMessage = "Sent \(label)"
        }
        return true
    }

    private func upsertDiscoveredSession(
        peripheral: CBPeripheral,
        advertisementData: [String: Any],
        rssi: NSNumber
    ) {
        let id = peripheral.identifier.uuidString
        let advertisedName = advertisementData[CBAdvertisementDataLocalNameKey] as? String
        let name = peripheral.name ?? advertisedName ?? "Unknown WILD"
        let advertisedServices = advertisementData[CBAdvertisementDataServiceUUIDsKey] as? [CBUUID] ?? []
        let serviceMatch = advertisedServices.contains { uuid in
            uuid == Ce32Protocol.serviceUuid || uuid.uuidString.uppercased() == "FFF0"
        }
        let nameMatch = Ce32Protocol.matchesKnownNamePrefix(name)
        guard serviceMatch || nameMatch else { return }

        if handles[id] == nil {
            handles[id] = PeripheralHandle(
                id: id,
                peripheral: peripheral,
                onOutOfFrameBytes: { [weak self] raw in self?.handleOutOfFrameBytes(deviceId: id, rawBytes: raw) },
                onFrame: { [weak self] commandId, payload in self?.handleFrame(deviceId: id, commandId: commandId, payload: payload) }
            )
        }

        var session = sessionsById[id] ?? DeviceSession(id: id, name: name, address: id)
        session.name = name
        session.address = id
        session.advertisedServiceMatch = session.advertisedServiceMatch || serviceMatch
        session.namePrefixMatch = session.namePrefixMatch || nameMatch
        session.rssi = rssi.intValue
        session.lastSeenAt = Date()
        sessionsById[id] = session
        publishSessions()
    }

    private func updateSession(_ id: String, mutate: (inout DeviceSession) -> Void) {
        guard var session = sessionsById[id] else { return }
        mutate(&session)
        sessionsById[id] = session
        publishSessions()
    }

    private func publishSessions() {
        for id in sessionsById.keys {
            sessionsById[id]?.isActive = id == activeSessionId
        }
        sessions = sessionsById.values.sorted { lhs, rhs in
            if lhs.isConnected != rhs.isConnected { return lhs.isConnected && !rhs.isConnected }
            if lhs.isLinkingLike != rhs.isLinkingLike { return lhs.isLinkingLike && !rhs.isLinkingLike }
            if lhs.bulkConnectEligible != rhs.bulkConnectEligible { return lhs.bulkConnectEligible && !rhs.bulkConnectEligible }
            if lhs.isActive != rhs.isActive { return lhs.isActive && !rhs.isActive }
            return lhs.name.localizedCaseInsensitiveCompare(rhs.name) == .orderedAscending
        }
    }

    private func serviceReady(_ id: String) {
        updateSession(id) {
            $0.verifiedTransport = true
            $0.hostState = .syncing
            $0.statusText = "Syncing..."
            $0.lastFailure = ""
            $0.lastMessage = "BLE service ready"
        }
        _ = writeCommand(id, Ce32Protocol.buildFastHandshake(), label: "fast handshake")
        requestResync(targetIds: [id])
        requestAllParams(targetIds: [id])
    }

    private func handleIncomingNotification(deviceId: String, value: Data) {
        guard let handle = handles[deviceId], !value.isEmpty else { return }
        handle.notificationRxCount += 1
        updateSession(deviceId) {
            $0.notificationRxCount = handle.notificationRxCount
        }
        handle.parser.push(value)
    }

    private func handleOutOfFrameBytes(deviceId: String, rawBytes: Data) {
        guard rawBytes.contains(0x80) else { return }
        updateSession(deviceId) {
            $0.hostState = .syncing
            $0.statusText = "Syncing..."
            $0.syncText = "Sync: legacy wake acknowledged"
            $0.lastMessage = "Legacy BLE handshake acknowledged"
        }
    }

    private func handleFrame(deviceId: String, commandId: UInt8, payload: Data) {
        updateSession(deviceId) {
            $0.commandRxCount += 1
            $0.lastMessage = "RX 0x\(String(format: "%02X", commandId))"
        }

        switch commandId {
        case 0x10:
            guard let stim = Ce32Protocol.parseStimControl(payload) else { return }
            updateSession(deviceId) {
                $0.stimControlStatus = stim
                $0.lastMessage = "Stim \(stim.stateLabel)"
            }

        case 0x82:
            updateSession(deviceId) {
                $0.hostState = $0.isRecordingLike ? $0.hostState : .synced
                $0.statusText = $0.isRecordingLike ? $0.statusText : "Synced"
                $0.syncText = "Sync complete"
                $0.lastFailure = ""
                $0.lastMessage = "Sync complete"
            }
            requestSystemParams(targetIds: [deviceId])

        case 0x8B:
            _ = writeCommand(deviceId, Ce32Protocol.buildRtcSetCommand(), label: "rtc set")

        case 0x8E:
            updateSession(deviceId) {
                $0.hostState = .recording
                $0.statusText = "Recording"
                $0.lastMessage = "Record start acknowledged"
            }

        case 0x90:
            guard let params = Ce32Protocol.parseSystemParams(payload) else { return }
            updateSession(deviceId) {
                $0.parsedSystemParams = params
                $0.previewSelection = params.previewSelection
                $0.systemParamHex = Ce32Protocol.bytesToHex(Data(payload.prefix(32)))
                if !$0.isRecordingLike && $0.hostState != .previewing {
                    $0.hostState = .synced
                    $0.statusText = "Synced"
                }
                $0.lastMessage = "System params received"
            }

        case 0x91:
            updateSession(deviceId) {
                $0.parsedDsp1Params = Ce32Protocol.parseDspParams(payload)
                $0.dsp1ParamHex = Ce32Protocol.bytesToHex(Data(payload.prefix(32)))
                $0.lastMessage = "DSP1 params received"
            }

        case 0x92:
            updateSession(deviceId) {
                $0.parsedDsp2Params = Ce32Protocol.parseDspParams(payload)
                $0.dsp2ParamHex = Ce32Protocol.bytesToHex(Data(payload.prefix(32)))
                $0.lastMessage = "DSP2 params received"
            }

        case 0x94:
            handleRecordBlock(deviceId: deviceId, payload: payload)

        case 0x95:
            let remaining = payload.u32LE(at: 0).map { Int($0) } ?? 0
            updateSession(deviceId) {
                $0.lastMessage = "Delete acknowledged, \(remaining) remaining"
            }

        case 0x9D:
            guard let params = Ce32Protocol.parseCameraParams(payload) else { return }
            updateSession(deviceId) {
                $0.parsedCameraParams = params
                $0.cameraParamHex = Ce32Protocol.bytesToHex(payload)
                $0.lastMessage = "Camera params received"
            }

        case 0xAD:
            guard let packet = Ce32Protocol.parsePreviewPacket(payload), let handle = handles[deviceId] else { return }
            handle.previewBuffer.append(packet.samples)
            updateSession(deviceId) {
                $0.previewPacketCount += 1
                $0.previewPoints = handle.previewBuffer.snapshot(maxPoints: 900)
                $0.voltage = packet.voltage
                $0.usedSpaceMb = packet.usedSpaceMb
                $0.digitalFlags = packet.digitalFlags
                if !$0.isRecordingLike {
                    $0.hostState = .previewing
                    $0.statusText = "Previewing"
                }
                $0.lastMessage = "Preview packet \($0.previewPacketCount)"
            }

        case 0xAF:
            guard let packet = Ce32Protocol.parseRecTimePacket(payload) else { return }
            updateSession(deviceId) {
                $0.recTimePacketCount += 1
                $0.recordingSeconds = packet.recordingSeconds
                $0.voltage = packet.voltage ?? $0.voltage
                $0.usedSpaceMb = packet.usedSpaceMb ?? $0.usedSpaceMb
                $0.hostState = .recording
                $0.statusText = "Recording"
                $0.lastMessage = "Recording time \(packet.recordingSeconds)s"
            }

        case 0x51, 0x52, 0x53:
            updateSession(deviceId) {
                $0.lastMessage = "Impedance payload 0x\(String(format: "%02X", commandId)) received"
            }

        case 0xAE:
            updateSession(deviceId) {
                $0.triggeredWaveformBlockCount += 1
                $0.lastMessage = "Trigger waveform block \($0.triggeredWaveformBlockCount)"
            }

        default:
            let ascii = Ce32Protocol.parseAsciiMessage(payload)
            updateSession(deviceId) {
                $0.lastMessage = ascii.isEmpty ? "RX 0x\(String(format: "%02X", commandId)) \(payload.count) bytes" : ascii
            }
        }
    }

    private func handleRecordBlock(deviceId: String, payload: Data) {
        guard let handle = handles[deviceId], var scan = handle.recordScan else {
            updateSession(deviceId) {
                $0.lastMessage = "Record block received"
            }
            return
        }

        let countRaw = min(Int(payload.u32LE(at: 0) ?? 0), Ce32Protocol.bleLogEntriesPerBlock)
        if countRaw == 0 {
            finishRecordScan(deviceId: deviceId, scan: scan)
            return
        }

        for index in 0..<countRaw {
            guard let endSector = payload.u32LE(at: 4 + index * 4), endSector > 0 else { continue }
            let startSector = Ce32Protocol.alignUpAu(scan.previousSector)
            let sizeSectors = endSector > startSector ? endSector - startSector : 0
            scan.records.append(RecordSummary(index: scan.records.count, startSector: startSector, endSector: endSector, sizeSectors: sizeSectors))
            scan.previousSector = endSector
        }

        if countRaw == Ce32Protocol.bleLogEntriesPerBlock && scan.blockIndex + 1 < Ce32Protocol.bleLogBlockScanLimit {
            scan.blockIndex += 1
            handle.recordScan = scan
            _ = writeCommand(deviceId, Ce32Protocol.buildLogBlockRequest(blockIndex: scan.blockIndex), label: "read record block")
        } else {
            finishRecordScan(deviceId: deviceId, scan: scan)
        }
    }

    private func finishRecordScan(deviceId: String, scan: RecordScan) {
        handles[deviceId]?.recordScan = nil
        updateSession(deviceId) {
            $0.records = scan.records
            $0.lastMessage = "Loaded \(scan.records.count) BLE record(s)"
        }
    }
}

extension Ce32BleManager: CBCentralManagerDelegate {
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        switch central.state {
        case .poweredOn:
            statusBanner = "Bluetooth ready."
        case .poweredOff:
            isScanning = false
            statusBanner = "Bluetooth is powered off."
        case .unauthorized:
            isScanning = false
            statusBanner = "Bluetooth permission is not authorized."
        case .unsupported:
            isScanning = false
            statusBanner = "This device does not support Bluetooth LE."
        default:
            isScanning = false
            statusBanner = "Bluetooth state: \(central.state.rawValue)"
        }
    }

    func centralManager(
        _ central: CBCentralManager,
        didDiscover peripheral: CBPeripheral,
        advertisementData: [String: Any],
        rssi RSSI: NSNumber
    ) {
        upsertDiscoveredSession(peripheral: peripheral, advertisementData: advertisementData, rssi: RSSI)
    }

    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        let id = peripheral.identifier.uuidString
        peripheral.delegate = self
        updateSession(id) {
            $0.hostState = .connected
            $0.statusText = "Discovering services..."
            $0.lastFailure = ""
        }
        peripheral.discoverServices([Ce32Protocol.serviceUuid, Ce32Protocol.deviceInfoServiceUuid])
    }

    func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        let id = peripheral.identifier.uuidString
        updateSession(id) {
            $0.hostState = .error
            $0.statusText = "Connect failed"
            $0.lastFailure = error?.localizedDescription ?? "Connect failed"
        }
    }

    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        let id = peripheral.identifier.uuidString
        handles[id]?.parser.reset()
        handles[id]?.previewBuffer.clear()
        updateSession(id) {
            $0.hostState = .disconnected
            $0.statusText = "Disconnected"
            $0.lastFailure = error?.localizedDescription ?? ""
            $0.lastMessage = error == nil ? "Disconnected" : "Disconnected unexpectedly"
        }
    }
}

extension Ce32BleManager: CBPeripheralDelegate {
    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        let id = peripheral.identifier.uuidString
        if let error {
            updateSession(id) {
                $0.hostState = .error
                $0.statusText = "Service discovery failed"
                $0.lastFailure = error.localizedDescription
            }
            return
        }

        guard let services = peripheral.services else { return }
        for service in services {
            if service.uuid == Ce32Protocol.serviceUuid {
                peripheral.discoverCharacteristics([Ce32Protocol.rxUuid, Ce32Protocol.txUuid, Ce32Protocol.legacyTxUuid], for: service)
            } else if service.uuid == Ce32Protocol.deviceInfoServiceUuid {
                peripheral.discoverCharacteristics([Ce32Protocol.swVersionUuid, Ce32Protocol.hwVersionUuid], for: service)
            }
        }
    }

    func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        let id = peripheral.identifier.uuidString
        if let error {
            updateSession(id) {
                $0.hostState = .error
                $0.statusText = "Characteristic discovery failed"
                $0.lastFailure = error.localizedDescription
            }
            return
        }

        guard let handle = handles[id], let characteristics = service.characteristics else { return }
        for characteristic in characteristics {
            switch characteristic.uuid {
            case Ce32Protocol.rxUuid:
                handle.rxCharacteristic = characteristic
                peripheral.setNotifyValue(true, for: characteristic)
            case Ce32Protocol.txUuid:
                handle.txCharacteristic = characteristic
            case Ce32Protocol.legacyTxUuid:
                handle.legacyTxCharacteristic = characteristic
            case Ce32Protocol.swVersionUuid, Ce32Protocol.hwVersionUuid:
                peripheral.readValue(for: characteristic)
            default:
                break
            }
        }

        if service.uuid == Ce32Protocol.serviceUuid &&
            !handle.ready &&
            handle.rxCharacteristic != nil &&
            (handle.txCharacteristic != nil || handle.legacyTxCharacteristic != nil) {
            handle.ready = true
            serviceReady(id)
        }
    }

    func peripheral(_ peripheral: CBPeripheral, didUpdateNotificationStateFor characteristic: CBCharacteristic, error: Error?) {
        guard characteristic.uuid == Ce32Protocol.rxUuid else { return }
        let id = peripheral.identifier.uuidString
        if let error {
            updateSession(id) {
                $0.lastFailure = error.localizedDescription
            }
        } else if characteristic.isNotifying {
            updateSession(id) {
                $0.lastMessage = "Notifications enabled"
            }
        }
    }

    func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        let id = peripheral.identifier.uuidString
        if let error {
            updateSession(id) {
                $0.lastFailure = error.localizedDescription
            }
            return
        }
        guard let value = characteristic.value else { return }
        switch characteristic.uuid {
        case Ce32Protocol.rxUuid:
            handleIncomingNotification(deviceId: id, value: value)
        case Ce32Protocol.swVersionUuid:
            updateSession(id) { $0.swVersion = Ce32Protocol.parseAsciiMessage(value) }
        case Ce32Protocol.hwVersionUuid:
            updateSession(id) { $0.hwVersion = Ce32Protocol.parseAsciiMessage(value) }
        default:
            break
        }
    }
}

private final class PeripheralHandle {
    let id: String
    let peripheral: CBPeripheral
    var rxCharacteristic: CBCharacteristic?
    var txCharacteristic: CBCharacteristic?
    var legacyTxCharacteristic: CBCharacteristic?
    let parser: Ce32FrameParser
    let previewBuffer = PreviewBuffer(capacity: 4096)
    var notificationRxCount = 0
    var ready = false
    var recordScan: RecordScan?

    init(
        id: String,
        peripheral: CBPeripheral,
        onOutOfFrameBytes: @escaping (Data) -> Void,
        onFrame: @escaping (UInt8, Data) -> Void
    ) {
        self.id = id
        self.peripheral = peripheral
        self.parser = Ce32FrameParser(onOutOfFrameBytes: onOutOfFrameBytes, onFrame: onFrame)
    }
}

private struct RecordScan {
    var blockIndex: Int
    var previousSector: UInt32
    var records: [RecordSummary]
}
