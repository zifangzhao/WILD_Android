# WILDX Neuropixels Android controls

## Using the phone

1. Find a device named `WILD-X…`, `WILDX…`, `XENP…`, or `XE_NP…` on Devices.
   Connect explicitly, then open Control. The device replies determine whether
   it is NP1/V1 or NP2/V2; the name is only a discovery hint.
2. Read the current state and profile. Choose connectors and rate, then use
   **Apply recording profile**. NP2 supports consecutive connectors P1, P1+P2,
   and, when advertised, P1+P2+P3. Each probe has 384 channels. Available board
   connectors do not prove physical probe presence or validate a site map.
3. Set a duration of 1–3600 seconds, or 0 for continuous recording on supported
   firmware. **Start recording** uses the applied device profile. A queued
   acknowledgement or Preparing state is not evidence of active capture.
4. Use **Stop recording** and wait for the device's terminal status. Saving/
   finalization can outlast a command acknowledgement; keep the device powered.
5. **Sync phone time** measures Bluetooth delay and commits one clock anchor
   while stopped. Its uncertainty describes the phone/device exchange, not an
   independent guarantee of the phone's UTC accuracy.
6. **Mount SD catalog** mounts supported storage without formatting. USB
   streaming, firmware-loader, and ROM-DFU buttons require confirmation and may
   close the BLE connection. Continue the destination workflow in the PC console.

The device page remains available on disconnect, showing the last report.
Disconnect cancels the phone's pending transactions; it does not stop an onboard
recording. Reconnect and read status before issuing further commands.

## What differs from CE64/128

| Item | WILDX phone implementation |
| --- | --- |
| BLE service | Same FFF0/FFF1/FFF2 transport; different command bodies |
| Initial identification | Short `82` request, validated `B9` NLR1/NLR2 reply, then a fresh STATUS |
| Recording/status | `B9`, 64-byte CRC32-protected NLD1 or NLD2 transactions |
| NP2 connector capability | Read-only `B8` NPC2 CAPS; unsupported connectors stay unavailable |
| Time | NPT2/NPR2, 16 measured observations and one non-replayed anchor commit |
| SD state | Device-reported queued/preparing/recording/saving/complete/error states |
| Recording size/time | Native V1/V2 chunk geometry; no CE64 ADC or voltage synthesis |
| Cloud | Per-station Neuropixels fields use the existing throttled device report |

NP1 20 kHz requires the firmware's advertised rate capability. NP1 two-probe
30 kHz and unqualified card/profile recording require explicit acknowledgement.
NP2 7.5 kHz requires the advertised live-rate capability. Firmware remains the
authority for SD and profile admission.

CE64 parameter uploads, stimulation, scheduler commands, and firmware staging
are excluded from the WILDX transport. In particular, WILDX's B9 is not CE64's
B9 firmware-install command. Request IDs, family, version, CRC and frame tails
must match before a reply completes a transaction. A lost mutation receipt is
reported as an unknown outcome; the phone never automatically replays it.

## Current scope

This adds BLE discovery/connection, NP1/NP2 status, probe/rate selection, finite
and continuous SD recording, Stop/finalization monitoring, phone time sync,
catalog mount, mode changes, and cloud summaries. Probe IMRO/site programming,
waveform decoding/preview, recording downloads, camera/IMU views, and firmware
installation still use WILDX PC. No CE64 fused image is accepted as a WILDX image.

The Android implementation follows the current WILDX console's `NpSdControl`,
`NpV2SdControl`, `NpV1BleProtocol`, `NpBleWire`, `Np2ConfigurationClient`, and
`NpV2OnlineClock` contracts under
`D:/Code/WILDX_console/WILDX_console/USB512_interface/USB512_interface`.

## Verification

Android 0.1.6 (7) builds successfully; all 144 Android tests and 13 dashboard
tests pass. Dashboard production output also builds. No phone or WILDX hardware
was connected or modified for these checks.

The software regression checks fragmentation at every byte boundary, embedded
frame delimiters, CRC/family/transaction rejection, capability-limited profiles,
storage bounds, finalization state, and a simulated discovery → CAPS → profile
→ Start → Stop workflow. The simulation also injects stale receipts and verifies
that each mutation is sent only once. It does not connect to physical hardware.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain '-Pkotlin.incremental=false'
```

Use `-Pkotlin.incremental=false` if the old Kotlin cache reports internal
visibility errors in unchanged OTA/scheduler files. No visibility changes or
changes to the separate Quest/clock-sync work are needed.
