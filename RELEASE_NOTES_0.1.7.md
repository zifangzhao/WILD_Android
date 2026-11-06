# WILD control panel 0.1.7 — WILD-X BLE modes

Local Android candidate, versionCode 8. Extends NP1/NLD1 and NP2/NLD2 control
without changing CE64/128 transport or cloud quotas.

- Separate Recording, Preview, Signals & time, Storage and Device modes pages;
  explicit device targeting, readable labels, 48–52 dp action targets and persistent
  Stop/cancel across subpages.
- Verified NP1 media profile readback and finite/continuous SD allocation,
  capability-gated timed Start, native motion-sensor views and both legacy and
  hardware-RTC time synchronization.
- Single-channel NP1 AP/LFP and NP2 P1/P2 AP display preview with validated
  source framing/session/timestamps. Native ADC counts, not calibrated µV.
  Bounded buffers, gaps preserved and control-priority lease renewal.
- Guarded catalog mount/recovery, confirmed family-specific format and NP2
  firmware-offered rewind. No automatic destructive operation or mutation replay.
- NP1 direct USB-download handoff; family-correct USB service/loader/ROM DFU;
  acknowledged handoffs disconnect BLE. Emergency reset is explicit and warns
  about incomplete recordings.

No physical-device qualification, phone installation or public publication is
implied. No firmware/card/hardware was modified. USB transfers, IMRO programming,
closed-loop configuration and recording catalog listing/deletion remain PC
workflows. See [mode matrix and verification](Note/WILDX_BLE_MODE_COVERAGE_20261106.md)
for exact scope and remaining hardware acceptance.

Validation: 158 Android unit tests passed, APK compiled and signature verified.
Local APK SHA-256:
`c118c9a06db58e3a5e271377b0fa6467a51c296428854f1a6e3d87fc0315583e`.
