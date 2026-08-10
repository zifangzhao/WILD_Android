import SwiftUI

@main
struct WILDApp: App {
    @StateObject private var bleManager = Ce32BleManager()

    var body: some Scene {
        WindowGroup {
            WildAppView()
                .environmentObject(bleManager)
        }
    }
}
