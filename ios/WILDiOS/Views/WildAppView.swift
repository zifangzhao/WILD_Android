import Foundation
import SwiftUI

struct WildAppView: View {
    @EnvironmentObject private var bleManager: Ce32BleManager

    var body: some View {
        TabView {
            NavigationStack {
                DevicesView()
            }
            .tabItem { Label("Devices", systemImage: "antenna.radiowaves.left.and.right") }

            NavigationStack {
                LiveView()
            }
            .tabItem { Label("Live", systemImage: "waveform.path.ecg") }

            NavigationStack {
                ControlView()
            }
            .tabItem { Label("Control", systemImage: "slider.horizontal.3") }

            NavigationStack {
                RecordsView()
            }
            .tabItem { Label("Records", systemImage: "externaldrive") }
        }
        .overlay(alignment: .top) {
            if !bleManager.statusBanner.isEmpty {
                StatusBanner(message: bleManager.statusBanner) {
                    bleManager.clearBanner()
                }
                .padding(.horizontal)
                .padding(.top, 8)
            }
        }
    }
}

private struct DevicesView: View {
    @EnvironmentObject private var bleManager: Ce32BleManager

    var body: some View {
        List {
            Section {
                HStack {
                    Button {
                        bleManager.isScanning ? bleManager.stopScan() : bleManager.startScan()
                    } label: {
                        Label(bleManager.isScanning ? "Stop Scan" : "Scan", systemImage: bleManager.isScanning ? "stop.circle" : "dot.radiowaves.left.and.right")
                    }
                    .buttonStyle(.borderedProminent)

                    Spacer()

                    if !bleManager.connectedSessions.isEmpty {
                        Button {
                            bleManager.disconnectAll()
                        } label: {
                            Label("Disconnect All", systemImage: "xmark.circle")
                        }
                        .buttonStyle(.bordered)
                    }
                }
            }

            Section("Scope") {
                Picker("Target", selection: $bleManager.controlScope) {
                    ForEach(ControlScope.allCases) { scope in
                        Text(scope.rawValue).tag(scope)
                    }
                }
                .pickerStyle(.segmented)

                HStack {
                    Button("Select All Linked") {
                        bleManager.selectAllConnected()
                    }
                    Spacer()
                    Button("Clear Selected") {
                        bleManager.clearSelected()
                    }
                }
            }

            Section("Devices") {
                if bleManager.sessions.isEmpty {
                    EmptyStateView(
                        title: "No Devices",
                        systemImage: "antenna.radiowaves.left.and.right",
                        detail: "Start scanning near a CE32 / WILD BLE device."
                    )
                } else {
                    ForEach(bleManager.sessions) { session in
                        DeviceRow(session: session)
                    }
                }
            }
        }
        .navigationTitle("WILD")
    }
}

private struct DeviceRow: View {
    @EnvironmentObject private var bleManager: Ce32BleManager
    let session: DeviceSession

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .firstTextBaseline) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(session.name)
                        .font(.headline)
                    Text(session.address)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                        .truncationMode(.middle)
                }
                Spacer()
                StatePill(text: session.statusText, active: session.isConnected)
            }

            MetricGrid(session: session)

            HStack {
                Button {
                    if session.isConnected {
                        bleManager.disconnect(session.id)
                    } else {
                        bleManager.connect(session.id)
                    }
                } label: {
                    Label(session.isConnected ? "Disconnect" : "Connect", systemImage: session.isConnected ? "xmark" : "link")
                }
                .buttonStyle(.borderedProminent)

                Button {
                    bleManager.setActiveSession(session.id)
                } label: {
                    Label(session.isActive ? "Active" : "Use", systemImage: "scope")
                }
                .buttonStyle(.bordered)

                if session.isConnected {
                    Button {
                        bleManager.toggleSelected(session.id)
                    } label: {
                        Label(bleManager.selectedSessionIds.contains(session.id) ? "Selected" : "Select", systemImage: bleManager.selectedSessionIds.contains(session.id) ? "checkmark.circle.fill" : "circle")
                    }
                    .buttonStyle(.bordered)
                }
            }
            .labelStyle(.iconOnly)
        }
        .padding(.vertical, 4)
    }
}

private struct LiveView: View {
    @EnvironmentObject private var bleManager: Ce32BleManager

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                TargetHeader()

                if let session = bleManager.activeSession {
                    WaveformPanel(session: session)
                    PreviewSourcePanel(session: session)
                    AcquisitionDeck(session: session)
                    DigitalFlagPanel(flags: session.digitalFlags)
                } else {
                    EmptyStateView(
                        title: "No Active Device",
                        systemImage: "waveform.path.ecg",
                        detail: "Connect or select a device on the Devices tab."
                    )
                        .frame(maxWidth: .infinity)
                }
            }
            .padding()
        }
        .navigationTitle("Live")
    }
}

private struct TargetHeader: View {
    @EnvironmentObject private var bleManager: Ce32BleManager

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Picker("Target", selection: $bleManager.controlScope) {
                ForEach(ControlScope.allCases) { scope in
                    Text(scope.rawValue).tag(scope)
                }
            }
            .pickerStyle(.segmented)

            HStack {
                Label(targetLabel, systemImage: "scope")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                Spacer()
                Text("\(bleManager.controlTargetIds.count) target(s)")
                    .font(.caption.monospacedDigit())
                    .foregroundStyle(.secondary)
            }
        }
    }

    private var targetLabel: String {
        switch bleManager.controlScope {
        case .activeDevice:
            return bleManager.activeSession?.name ?? "No active device"
        case .selectedDevices:
            return bleManager.selectedSessions.map(\.name).joined(separator: ", ").ifEmpty("No selected devices")
        case .allConnected:
            return "All connected devices"
        }
    }
}

private struct WaveformPanel: View {
    let session: DeviceSession

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Label(session.previewSelection.label(ephysChannelCount: session.parsedSystemParams?.ephysChannelCount), systemImage: "waveform")
                    .font(.headline)
                Spacer()
                Text("\(session.previewPacketCount) pkts")
                    .font(.caption.monospacedDigit())
                    .foregroundStyle(.secondary)
            }

            WaveformView(points: session.previewPoints)
                .frame(height: 220)
                .background(.black)
                .clipShape(RoundedRectangle(cornerRadius: 8))
        }
    }
}

private struct PreviewSourcePanel: View {
    @EnvironmentObject private var bleManager: Ce32BleManager
    let session: DeviceSession

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Preview Source")
                .font(.headline)

            Picker("Mode", selection: bindingForMode) {
                Text("Ephys").tag(false)
                Text("Aux").tag(true)
            }
            .pickerStyle(.segmented)

            Picker("Channel", selection: bindingForIndex) {
                ForEach(0..<session.previewSelection.optionCount(ephysChannelCount: session.parsedSystemParams?.ephysChannelCount), id: \.self) { index in
                    Text(PreviewSelection(auxMode: session.previewSelection.auxMode, index: index).label(ephysChannelCount: session.parsedSystemParams?.ephysChannelCount)).tag(index)
                }
            }
            .pickerStyle(.menu)
        }
    }

    private var bindingForMode: Binding<Bool> {
        Binding(
            get: { session.previewSelection.auxMode },
            set: { auxMode in
                bleManager.setPreviewSelection(PreviewSelection(auxMode: auxMode, index: 0), for: session.id)
            }
        )
    }

    private var bindingForIndex: Binding<Int> {
        Binding(
            get: { session.previewSelection.index },
            set: { index in
                bleManager.setPreviewSelection(PreviewSelection(auxMode: session.previewSelection.auxMode, index: index), for: session.id)
            }
        )
    }
}

private struct AcquisitionDeck: View {
    @EnvironmentObject private var bleManager: Ce32BleManager
    let session: DeviceSession

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Acquisition")
                .font(.headline)

            HStack {
                Button {
                    bleManager.startPreview()
                } label: {
                    Label("Preview", systemImage: "play.circle")
                }
                .buttonStyle(.borderedProminent)

                Button {
                    bleManager.stopPreview()
                } label: {
                    Label("Stop", systemImage: "stop.circle")
                }
                .buttonStyle(.bordered)
            }

            HStack {
                Button {
                    bleManager.startRecording()
                } label: {
                    Label("Record", systemImage: "record.circle")
                }
                .buttonStyle(.borderedProminent)

                Button {
                    bleManager.stopRecording()
                } label: {
                    Label("Stop Rec", systemImage: "stop.fill")
                }
                .buttonStyle(.bordered)
            }

            MetricGrid(session: session)
        }
    }
}

private struct ControlView: View {
    @EnvironmentObject private var bleManager: Ce32BleManager
    @State private var pendingSystemAction: SystemAction?

    var body: some View {
        List {
            Section {
                TargetHeader()
            }

            Section("Online") {
                Button {
                    bleManager.requestResync()
                } label: {
                    Label("Resync", systemImage: "clock.arrow.circlepath")
                }

                Button {
                    bleManager.requestAllParams()
                } label: {
                    Label("Read All Payloads", systemImage: "square.and.arrow.down")
                }

                Button {
                    bleManager.requestImpedance()
                } label: {
                    Label("Impedance", systemImage: "bolt.horizontal")
                }
            }

            if let session = bleManager.activeSession {
                Section("Active Device") {
                    LabeledContent("State", value: session.statusText)
                    LabeledContent("System", value: session.parsedSystemParams.map { "\($0.ephysSamplingRate) Hz / \($0.ephysChannelCount) ch" } ?? "--")
                    LabeledContent("Camera", value: session.parsedCameraParams.map { "Reg0 \($0.reg0), Reg1 \($0.reg1)" } ?? "--")
                    LabeledContent("Firmware", value: session.swVersion ?? "--")
                    LabeledContent("Hardware", value: session.hwVersion ?? "--")
                }
            }

            Section("Lifecycle") {
                ForEach(SystemAction.allCases) { action in
                    Button(role: action.role) {
                        pendingSystemAction = action
                    } label: {
                        Label(action.title, systemImage: action.systemImage)
                    }
                }
            }
        }
        .navigationTitle("Control")
        .confirmationDialog("Send system command?", item: $pendingSystemAction) { action in
            Button(action.title, role: action.role) {
                switch action {
                case .sleep: bleManager.requestSleep()
                case .reset: bleManager.requestSoftwareReset()
                case .bootloader: bleManager.requestBootloader()
                case .firmwareUpdate: bleManager.requestFirmwareUpdate()
                }
            }
        } message: { action in
            Text(action.message)
        }
    }
}

private struct RecordsView: View {
    @EnvironmentObject private var bleManager: Ce32BleManager

    var body: some View {
        List {
            Section {
                Button {
                    bleManager.refreshRecordList()
                } label: {
                    Label("Refresh BLE Records", systemImage: "arrow.clockwise")
                }

                Button(role: .destructive) {
                    bleManager.deleteLastRecord()
                } label: {
                    Label("Delete Last", systemImage: "trash")
                }

                Button(role: .destructive) {
                    bleManager.deleteAllRecords()
                } label: {
                    Label("Delete All", systemImage: "trash.fill")
                }
            }

            if let session = bleManager.activeSession {
                Section(session.name) {
                    if session.records.isEmpty {
                        EmptyStateView(
                            title: "No Record Index",
                            systemImage: "externaldrive",
                            detail: "Refresh records after connecting to load BLE log entries."
                        )
                    } else {
                        ForEach(session.records) { record in
                            VStack(alignment: .leading, spacing: 4) {
                                Text("Record \(record.index + 1)")
                                    .font(.headline)
                                Text("Start \(record.startSector)  End \(record.endSector)")
                                    .font(.caption.monospacedDigit())
                                    .foregroundStyle(.secondary)
                                Text(String(format: "%.2f MB", record.sizeMb))
                                    .font(.caption.monospacedDigit())
                            }
                        }
                    }
                }
            }
        }
        .navigationTitle("Records")
    }
}

private struct WaveformView: View {
    let points: [Float]

    var body: some View {
        Canvas { context, size in
            let gridColor = Color.white.opacity(0.16)
            for fraction in stride(from: 0.25, through: 0.75, by: 0.25) {
                var grid = Path()
                let y = size.height * fraction
                grid.move(to: CGPoint(x: 0, y: y))
                grid.addLine(to: CGPoint(x: size.width, y: y))
                context.stroke(grid, with: .color(gridColor), lineWidth: 1)
            }

            guard points.count > 1 else {
                let message = Text("Waiting for preview").font(.caption).foregroundColor(.white.opacity(0.5))
                context.draw(message, at: CGPoint(x: size.width / 2, y: size.height / 2))
                return
            }

            let maxMagnitude = max(points.map { abs($0) }.max() ?? 1, 1)
            var path = Path()
            for (index, point) in points.enumerated() {
                let x = CGFloat(index) / CGFloat(points.count - 1) * size.width
                let normalized = CGFloat(point / maxMagnitude)
                let y = size.height * 0.5 - normalized * size.height * 0.42
                if index == 0 {
                    path.move(to: CGPoint(x: x, y: y))
                } else {
                    path.addLine(to: CGPoint(x: x, y: y))
                }
            }
            context.stroke(path, with: .color(.green), lineWidth: 1.5)
        }
    }
}

private struct EmptyStateView: View {
    let title: String
    let systemImage: String
    let detail: String

    var body: some View {
        VStack(spacing: 10) {
            Image(systemName: systemImage)
                .font(.largeTitle)
                .foregroundStyle(.secondary)
            Text(title)
                .font(.headline)
            Text(detail)
                .font(.subheadline)
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 24)
    }
}

private struct MetricGrid: View {
    let session: DeviceSession

    var body: some View {
        Grid(alignment: .leading, horizontalSpacing: 14, verticalSpacing: 6) {
            GridRow {
                MetricLabel("RSSI", value: session.rssi.map { "\($0) dBm" } ?? "--")
                MetricLabel("VBAT", value: session.voltage.map { String(format: "%.2f V", $0) } ?? session.advertisedVoltage.map { String(format: "%.2f V", $0) } ?? "--")
            }
            GridRow {
                MetricLabel("USED", value: session.usedSpaceMb.map { String(format: "%.1f MB", $0) } ?? "--")
                MetricLabel("REC", value: "\(session.recordingSeconds)s")
            }
        }
    }
}

private struct MetricLabel: View {
    let title: String
    let value: String

    init(_ title: String, value: String) {
        self.title = title
        self.value = value
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
                .font(.caption2)
                .foregroundStyle(.secondary)
            Text(value)
                .font(.caption.monospacedDigit())
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct DigitalFlagPanel: View {
    let flags: [DigitalFlag]

    var body: some View {
        if !flags.isEmpty {
            VStack(alignment: .leading, spacing: 8) {
                Text("Digital Flags")
                    .font(.headline)
                FlowLayout(items: flags) { flag in
                    StatePill(text: flag.name, active: flag.active)
                }
            }
        }
    }
}

private struct StatusBanner: View {
    let message: String
    let onDismiss: () -> Void

    var body: some View {
        HStack {
            Text(message)
                .font(.subheadline)
                .lineLimit(2)
            Spacer()
            Button(action: onDismiss) {
                Image(systemName: "xmark")
            }
            .buttonStyle(.plain)
        }
        .padding(12)
        .background(.thinMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .shadow(radius: 3)
    }
}

private struct StatePill: View {
    let text: String
    let active: Bool

    var body: some View {
        Text(text)
            .font(.caption.monospacedDigit())
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(active ? Color.green.opacity(0.18) : Color.gray.opacity(0.18))
            .foregroundStyle(active ? .green : .secondary)
            .clipShape(Capsule())
    }
}

private struct FlowLayout<Item: Identifiable, Content: View>: View {
    let items: [Item]
    let content: (Item) -> Content

    var body: some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 84), spacing: 8)], alignment: .leading, spacing: 8) {
            ForEach(items) { item in
                content(item)
            }
        }
    }
}

private enum SystemAction: String, CaseIterable, Identifiable {
    case sleep
    case reset
    case bootloader
    case firmwareUpdate

    var id: String { rawValue }

    var title: String {
        switch self {
        case .sleep: return "Sleep"
        case .reset: return "Software Reset"
        case .bootloader: return "Bootloader"
        case .firmwareUpdate: return "Firmware Update"
        }
    }

    var systemImage: String {
        switch self {
        case .sleep: return "moon"
        case .reset: return "arrow.counterclockwise"
        case .bootloader: return "terminal"
        case .firmwareUpdate: return "square.and.arrow.up"
        }
    }

    var message: String {
        switch self {
        case .sleep: return "This will ask the active device to sleep and disconnect the BLE link."
        case .reset: return "This will ask the active device to reset and disconnect the BLE link."
        case .bootloader: return "This will move the active device toward bootloader mode."
        case .firmwareUpdate: return "This sends the firmware image update entry command to the active device."
        }
    }

    var role: ButtonRole? {
        switch self {
        case .sleep: return nil
        case .reset, .bootloader, .firmwareUpdate: return .destructive
        }
    }
}

private extension String {
    func ifEmpty(_ fallback: String) -> String {
        isEmpty ? fallback : self
    }
}
