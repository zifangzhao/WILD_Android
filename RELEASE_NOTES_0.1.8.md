# WILD control panel 0.1.8 — advanced WILD-X BLE controls

Tester prerelease, Android versionCode 9, package `com.wild.android`.
Includes the previously local 0.1.7 operating-mode expansion and adds:

- **Probe maps:** import NP1 IMRO/native JSON, NRIC1384 native JSON, or NP2013
  four-shank IMRO/JSON for an explicitly selected P1/P2/P3 connector. Validate
  vendor routing, gains, references and channel uniqueness before staging with
  NPC1/NPC2. Retain requests per device; reuse matching resident receipts or
  restage after final profile selection before Start. Staged/applied/degraded
  receipts are clearly distinguished; read-only checks never stop a recording.
- **Closed-loop:** read capabilities/status; configure one/two Ripple detectors,
  sources, threshold gains, rectification and training interval; explicitly
  disable with verified readback. Admission matches the console's qualified SD
  profiles and advertised source/filter capabilities. Preview leases retire
  before setup. Unknown outcomes block Start/configure until resolved; no
  mutating request is automatically replayed.
- **Recording catalog:** list legacy V1, V1 filesystem2 and capable V2 catalogs.
  Delete only the confirmed eligible last recording, with fresh snapshot checks,
  transaction/CID/UID guards where supported, single delivery and post-delete
  verification that earlier entries are unchanged. Native V1 replies are
  serialized and retire an uncertain connection. Missing/degraded descriptors
  and unsupported V2 capabilities keep deletion disabled.

Use Devices → Connect → Control → Probe maps / Closed-loop / Storage.
The persistent Stop/cancel and explicit Disconnect remain available across
subpages. File import, configuration and catalog work add no cloud listeners or
raw-configuration uploads and retain existing Firestore throttling.

This is software-validated, **not physically qualified on Android WILD-X**.
No device was connected, recorded, reset, formatted, deleted or flashed during
development. Firmware capabilities remain authoritative; USB downloads,
full-rate acquisition, firmware installation and live camera/audio transfers
remain USB/PC workflows. Experimental pre-arm/first-sync is not enabled.

See [advanced BLE usage, safety and verification](Note/WILDX_ADVANCED_BLE_20261106.md).

Validation: 170 Android tests passed (0 failed/errors/skips); APK build and
v1/v2 signature verification passed. Signing identity matches the published
0.1.6 app. Minimum Android 6.0/API23; target API35.

APK: `WILD-control-panel-0.1.8.apk` (28,459,201 bytes).
SHA-256: `b17fcdf51db3e5568479e52ec581c2115e942669300c023c6fbde8e0427f799a`.
