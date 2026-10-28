# WILD control panel 0.1.6 — WILDX Neuropixels controls

Android 6.0 or later, package `com.wild.android`, version code **7**. Tester APK
signed with the existing debug identity; update a compatible installation in place.

WILDX NP1/V1 and NP2/V2 devices now open a dedicated Control page with:

- Explicit connection/disconnection and CRC-validated device identification.
- Device-supported probe and per-probe sample-rate selection.
- Finite or continuous SD recording, Stop, and device-reported finalization.
- Phone-time synchronization, SD-catalog mounting, and USB/loader/DFU selection.
- Per-station Neuropixels status, rate, probe, and storage summaries in the cloud.

Controls explain what is configured, queued, preparing, recording, or saving.
CE64 control/OTA commands are excluded from the WILDX transport. Mutations are
not automatically replayed after missing acknowledgements.

**How to use:** Devices → Connect a WILD-X/WILDX/XENP/XE_NP device → Control →
read status → apply a profile → Start. After Stop, wait until the device reports
completion. Disconnecting the phone does not stop the onboard recorder.

**Scope:** Probe IMRO/site programming, waveform previews, recording downloads,
camera/IMU views and firmware installation continue through WILDX PC.

**Verification:** 144 Android tests and 13 dashboard tests passed; the Android
APK and dashboard production build succeeded. Includes simulated NP2 discovery,
CAPS, profile, Start/Stop, stale replies, and truncated-frame recovery. Physical
WILDX validation remains pending.

See [the usage and protocol guide](Note/WILDX_ANDROID_SUPPORT_20261028.md).

APK SHA-256:
`2cf129392ecb3d0df66d9baf72c5c9d145d77a6d9df5e4e988b1aaa0717064cf`
