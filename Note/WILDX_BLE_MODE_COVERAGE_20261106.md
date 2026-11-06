# WILD-X Android BLE operating modes

The later [0.1.8 advanced BLE update](WILDX_ADVANCED_BLE_20261106.md) adds IMRO,
closed-loop configuration and catalog listing/deletion. This file preserves
the 0.1.7 scope and verification record below.

Android candidate **0.1.7 (8)** extends the 0.1.6 WILD-X implementation.
This is software-checked support, **not physical-device qualification**. No
phone or logger was connected, reset, recorded, formatted, or flashed during
development. Existing CE64/128 controls retain their separate protocol path.

## Where to find the controls

Devices → explicitly Connect the WILD-X device → Control. Select a device in
the header; commands are bound to that device ID, including dialog confirmation
and preview cleanup. Changing the selected device never starts a connection.

- **Recording:** neural connectors/rate, NP1 media profile, SD Start and timed
  Start. Applying a profile is separate from starting a recording.
- **Preview:** one selected neural channel from an already-running recording.
  Pause stops the preview lease, not acquisition.
- **Signals & time:** coherent IMU snapshot, read-only clock query, stopped-only
  phone-time synchronization and media counters.
- **Storage:** mount, recover, confirmed format and firmware-offered rewind.
- **Device modes:** explicit USB handoff and emergency-only reset.

Stop/cancel stays at the bottom across subpages. Devices and Disconnect remain
available while a command is pending. Disconnecting Bluetooth does **not** stop
the recorder. Wait for a firmware-confirmed terminal state after Stop; keep
power connected during finalization.

## Operating-mode matrix

“Available” below always means admitted by fresh firmware state/capabilities;
the phone does not create capabilities from a device name.

| Mode / operation | NP1 / NLD1 | NP2 / NLD2 |
| --- | --- | --- |
| Discovery, status, explicit connect/disconnect | Available | Available |
| Neural profiles | Supported P1/P2/P3 combinations; advertised 20 kHz only | Prefix masks P1, P1+P2, P1+P2+P3; connector CAPS checked |
| SD recording, finite reservation / continuous | Available; qualification warning acknowledged explicitly | Available; 7.5 kHz / continuous require capabilities |
| Scheduled UTC recording, cancel via Stop | Capability 1024 only; neural or verified media profile | Capability 1024 only; never substitutes pre-arm/first-sync commands |
| Camera-only / microphone-only / combined SD mode | MEDIA capability, native parameters independently read back | Not offered as an NLD2 neural operation |
| Neural display preview | AP or LFP; selected recorded probe/channel | AP on P1/P2 only, including P1/P2 in a triple recording |
| Sensor view | SNS1 snapshot capability required | Coherent SNS1 snapshot, rejected if unavailable |
| Clock read / stopped synchronization | Legacy NPT2 v1 and hardware RTC v2 | Legacy NPT2 v1 and hardware RTC v2 |
| Mount catalog | Stopped storage-capable recorder; can recover initially unknown capacity | Storage-capable idle/unmounted recorder |
| Recover interrupted catalog | Fault-free stopped recorder | Recovery/recoverable/quarantine and stored7.5 capability gates |
| Format | Explicit card-capacity confirmation; legacy / filesystem2 contracts | Explicit confirmation and NSP8 key |
| Overwrite from last known boundary | Not offered | Explicit firmware offer, confirmed capacity + boundary; never automatic |
| USB streaming / service handoff | Destination2 | Destination2 |
| Direct USB-download handoff | Destination4 | Not supported by firmware; enter USB service, then select download on PC |
| Firmware-loader / STM32 ROM DFU handoff | Destinations 1 / 3 | Destinations 1 / 3 |
| Rescue reset | Only advertised, quiescent failed state with confirmed fault | Explicit emergency confirmation; warns that recording is not drained |

The source uses NLD1/NLD2 **control versions**, not a hardware-board assertion.
An older/fixed media firmware may expose only its fixed settings and reject
other rates. Rejection or missing native readback is not reported as success.
No NP2 LFP, P3 BLE waveform, CE64 stimulation, or CE64 scheduler is invented.

## Important protocol and safety details

- Same FFF0/FFF1/FFF2 transport as CE64, incompatible command bodies. WILD-X B9
  is SD/clock/preview control, **not CE64 firmware install**.
- CRC-valid family/version/op/ID must correlate before a command is accepted.
  The NLD1 filesystem2 format request uses version2 but its normal NLR1 reply
  remains version1, as specified by the firmware contract.
- Native512-byte media settings have no transaction ID/CRC. The complete frame
  is serialized; uncertain delivery retires the link so a late native receipt
  cannot authorize a later operation. No media-setting command is replayed.
- Storage actions re-read status after confirmation, recheck the card capacity
  (and offered rewind boundary), and verify the mutation ID and completion.
  Formatting removes the catalog; later recordings can overwrite all old data.
  Rewind removes entries at/after the offered boundary; it is not data recovery.
- Timed Start has a fixed confirmed UTC, 5 seconds–24 hours ahead. It needs a
  fresh calibrated clock observation consistent with this phone and an unfrozen
  provider. It never silently moves the requested time forward or sends a
  CE64 scheduler/pre-arm command. Current firmware without capability1024 keeps
  timed Start disabled.
- Hardware RTC detection is read-only. Sync makes8 hardware or16 legacy timing
  observations, sends one COMMIT, and verifies generation, uncertainty and
  offset/phase readback. Unknown COMMIT outcomes block another commit in that
  session; no automatic fallback/replay follows a mutating attempt. Radio
  uncertainty is not a guarantee of the phone's own UTC accuracy.
- Preview first obtains a complete calibration snapshot, validates NP1 origin
  or NP2 SuperSync, then matches session, rate, probe, channel, band and source
  timestamp phase. Closed-loop must be confirmed unconfigured before preview
  uses the shared firmware workspace. A refusal leaves acquisition independent.
- Display values are **native unsigned ADC counts**, not calibrated microvolts:
  NP1 is10-bit, NP2 is12-bit. Missing rows/gaps are not interpolated. The display
  is subsampled and can alias; it is not the full-rate recording.
- Preview has an8-packet bounded queue and1500-point plot. Lease renewals yield
  to controls; pausing/leaving the device page cancels renewal, with a2-second
  firmware expiry as fallback. Stop does not wait for a preview data stream.
- No waveform samples, IMU polling or additional listeners are uploaded to
  Firestore. Existing cloud throttling/heartbeat rules are unchanged.

## Deliberate remaining PC workflows — not mobile parity claims

These are **not implemented by this update**: IMRO/site-map programming and
configuration receipt management; optional on-device closed-loop configuration;
probe pre-arm/first-sync protocols; recording catalog listing/Delete Last;
recording downloads/exports; factory ADC calibration; camera exposure/live
video/audio views; firmware installation and USB acquisition.

The phone can hand off to the USB loader/download/service modes; it does not
perform those USB workflows. Full PC-console BLE feature parity therefore
remains incomplete, even though the operating-mode transitions above are
exposed. Pre-arm is disabled in the current published console and must not be
promoted from a source-only capability to a qualified recording mode.

## Source comparison and software checks

Compared with the local WILD-X console contracts under
`D:/Code/WILDX_console/WILDX_console/USB512_interface/USB512_interface/Ble`
(`NpV1BleProtocol`, `NpV2SdControl`, `NpBleWire`, `NpHardwareRtcClock`,
`NpV2OnlineClock`, `NpBleImu`, and NP1/NP2 preview wire/decoder), plus NLD2
firmware control under `D:/Code/XE_NP_V2/Core`. The console README identifies
the current published application as1.0.0.246. Read-only comparison only; no
console/firmware repository was changed.

Runnable checks:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain '-Pkotlin.incremental=false'
```

The added fixtures cover family-specific destinations, scheduled UTC offsets,
SD admission and destructive keys, changed-capacity/rewind refusal, filesystem2
reply compatibility, media parameter layouts and allocation, every native
fragment boundary, single-delivery format/handoff, native partial-send retirement,
coherent IMU tokens, hardware/legacy clock synchronization and lost COMMIT,
NP1 AP/LFP and NP2 SuperSync/ADC decoding with missing samples, and simulated
BA preview delivery/pause without issuing SD Start/Stop.

Final result: **158 tests, 0 failures/errors/skips**; `assembleDebug` passed.
The APK signature verifies under v1 and v2; package `com.wild.android`,
versionName `0.1.7`, versionCode `8`, minimum Android API 23. The packaged
binary is a local debug-signed tester candidate, not a production-qualified
release. Existing SDK XML/deprecation and v1 META-INF warnings remain; v2
signature verification succeeds.

Local artifact:
`build/releases/0.1.7/WILD-control-panel-0.1.7.apk` (27,120,181 bytes).
SHA-256:
`c118c9a06db58e3a5e271377b0fa6467a51c296428854f1a6e3d87fc0315583e`.
Copy checksum matches the checked Gradle output. No GitHub/Firebase publication
or phone installation was performed for this candidate.

Physical acceptance is still needed for every relevant family/firmware/card:
connect/reconnect; profile transitions; SD Start/Stop/restart/drain; recorded
sample and storage integrity; preview coexistence; scheduled timing; hardware
RTC phase; and every handoff's destination arrival. Destructive storage tests
need an explicitly expendable card and separate user authorization. A passed
JVM test cannot establish any of those hardware properties.
