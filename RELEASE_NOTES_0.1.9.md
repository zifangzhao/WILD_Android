# WILD control panel 0.1.9 — WILD-X multi-device live preview

Tester prerelease, Android versionCode 10, package `com.wild.android`.

- Labelled stacked waveforms across multiple explicitly connected WILD-X devices.
  NP1 compact firmware: up to 4 channels from one probe/band. NP2 filtered-preview
  firmware: up to 2 AP channels on P1/P2. Older firmware retains one-channel preview.
- Horizontal pinch for time, vertical pinch for display gain, vertical drag for
  per-trace offset; center/reset and large labelled controls. Channel setup
  collapses when preview starts; display/filter/spectrum controls are expandable.
- Factory gain/optional NP1 ADC CSV correction with explicit serial/gain binding,
  private saved tables and provenance. Optional **operator-supplied µV/count**
  conversion, separately bound to AP/LFP. No guessed voltage/hardware trim claim.
- Display high-pass and a labelled Hann-windowed FFT using contiguous original
  preview samples. Correct NP2 signed Q3 scaling, per-channel timestamps, checked
  replay and gaps; no interpolation or full-rate AP spectrum claim.
- Per-device and all-device preview pause, retained last-sample time, bounded
  buffers and control priority. Preview never starts/stops SD capture. No waveform
  cloud traffic; existing Firebase throttling remains unchanged.

Use **Live preview**, or **Control → Preview**, after starting neural SD recording.
See [preview operation, limits and calibration](Note/WILDX_ANDROID_PREVIEW_20261106.md).

Validation: 184 Android tests passed (0 failures/errors/skips); APK build and v1/v2
signature verification passed. The certificate matches 0.1.8. Android 6.0/API 23
minimum, target API 35. APK size 28,544,937 bytes.

SHA-256: `64285ded07ed2391e0d3c2b344d4a5a89335c83029eb04b7c4a6c8c36e2b512a`.

Physical multi-device BLE, analog calibration and touch/orientation qualification
remain pending. This is not a full 384-channel stream or a web waveform release.
No attached device was connected, recorded, reset, formatted, flashed or installed
during this change.
