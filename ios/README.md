# WILD iOS

Native SwiftUI/CoreBluetooth starter port of the Android WILD client.

This folder is intentionally self-contained so it can be opened on a Mac with Xcode even though this Windows workspace cannot build or run iOS code locally.

## Current port surface

- `Devices`: CoreBluetooth scan, connect, disconnect, active-device selection, and selected-device scope.
- `Live`: CE32 preview source selection, waveform rendering, preview start/stop, and recording start/stop.
- `Control`: resync, parameter reads, impedance request, sleep/reset/bootloader/firmware-entry commands.
- `Records`: BLE record-index refresh/delete command path with an initial record-block parser scaffold.
- `BLE`: CE32 UUIDs, command framing, notification frame parser, preview/record/system/camera/DSP parsing helpers.

The command sequencing matches the Android port for the core acquisition path:

- Preview start: `< 40 >`, `< 42 selector >`, `< 40 >`
- Preview stop: `< 41 >`
- Record start: `< 30 >`, `< 40 >`, `< 42 selector >`, `< 40 >`
- Record stop: `< 31 >`, `< 41 >`

## Open in Xcode

1. Copy or open this repo on a Mac with Xcode installed.
2. Open `ios/WILDiOS.xcodeproj`.
3. Set a development team and bundle identifier if needed.
4. Build to a BLE-capable iPhone or iPad.
5. Grant Bluetooth permission on first launch.

The app uses iOS 16.0 as the deployment target because the UI uses `NavigationStack`.

## Verification status

No iOS compile or simulator run was performed in this workspace because the active machine does not have an iOS development environment. The source was created to mirror the Android architecture and protocol behavior, but it still needs first build/run fixes on macOS/Xcode.
