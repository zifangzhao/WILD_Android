# CE64/128 app update — 2026-09-07

## Source baseline and limits

Compared against CE64 `9c9d244` (scheduler reliability checkpoint after FM65),
`docs/LOW_POWER_SCHEDULER.md`, the packed declarations in
`Core/Inc/CE32_scheduler.h`, and the WILD Console sources. The checkpoint is
**not a new firmware release**. Android version: **0.1.5 (6)**.

Device names are not capability negotiation. Read responses establish which
features are available on a particular firmware/hardware combination. Existing
CE32 and older PC cloud reports remain readable; missing extended telemetry is
shown as **Not reported**, not as disabled or unsupported.

## Changes

### Android

- A direct, sticky Task menu replaces two nested selectors in focused device
  control. Labels describe operations rather than “Push”, “Dump”, or “Quick”.
- Device status includes channel count, firmware, hardware, battery, storage,
  sample rate, and the last-read scheduler state. No settings are read just by
  opening this summary.
- Scheduler v2 editor exposes daily, dated one-shot, periodic, and signal
  rules; start/stop, marker, and bounded restart actions; current or saved
  profiles; battery/storage/motion/AI guards; threshold direction, ALL/ANY,
  hysteresis, debounce, deferrals, priority, and missed-event policy.
- Editor drafts survive connection changes. Reloading device values does not
  silently discard a draft. Saves, deletes, profile replacement, and global
  enabling use explicit confirmations naming their scope.
- “Save settings to profile” copies the focused device's last-read 512-byte
  settings, writes all 16 chunks, reads all chunks back, and compares every
  byte. Other scheduler transactions cannot interrupt this sequence.
- D8 reply length comes from its echoed request, not the current request.
  Transactions are serialized; wrong command/chunk replies cannot acknowledge
  another request. Only reads and identical DB chunks have bounded retries.
  Rule changes with a lost ACK are reported as an unknown outcome and are not
  automatically replayed.
- Configuration replies require version, exact size, magic, commit marker and
  CRC; the exact firmware pristine RAM configuration is accepted separately.
  Empty rule slots retain their own slot identities. Invalid typed values and
  out-of-range slot/chunk IDs are rejected, not clamped to another operation.
- Schedule/profile edits are blocked while recording. Read-only operations
  remain available. Busy/rejected/unknown outcomes have explanatory messages.
- Deferred configuration state is cleared only after valid System, DSP1 and
  DSP2 readbacks after stop. Readback is not described as proof that arbitrary
  proposed edits match or that the next recording has started using them.
- Fused HEX guidance explicitly distinguishes application installation from
  bootloader/resident-service replacement. CRC does not establish board identity.

### Firebase dashboard

- A Device details dialog shows separate acquisition, identity, scheduler,
  health, AI, and viewer summaries for each reporting station.
- Compact overview, larger regular labels/buttons, keyboard focus indicators,
  responsive detail layout, and summary cards that open the corresponding filter.
- Scheduler dates display the device's civil clock, without converting to the
  browser's timezone. Fault bit labels match the firmware's subsystem bitmap.
- Sparse reports retain previous values. A section's older observation cannot
  replace its newer observation; false/zero/no-wake explicitly update values.
- AI result age uses its own observation time, not the latest station ping.
  Invalid AI results are explicitly marked without reviving older valid status.
- The illustrative one-cell 3.7 V Li-ion percentage curve uses 3.4 V as the
  usable-empty endpoint. It is a voltage-based estimate, not a calibrated gauge.

## Cloud cost and provenance

New summaries are fields in the existing device document; they do not add
listeners or a faster polling loop. AI event/class/confidence changes and
observation timestamps alone do not trigger extra writes. They ride the
existing 5-minute connected / 15-minute passive heartbeat. Meaningful device
state/configuration changes retain the gateway's existing throttled path.

History samples now use the actual received page, not a synthetic combination
of new AI flags and old health measurements. A name-only advertisement cannot
refresh the source timestamp of a cached status or AI result.

The maintained Firebase web app is in `web-dashboard/firebase-hosting`, not the
older Sites prototype in `web-dashboard/app`. These changes do not migrate the
public deployment or change Authentication or Firestore rules.

## Boundaries still requiring device-specific validation

- No claim of live CE64/CE128 HIL validation is made by unit tests or APK builds.
- External scheduler signals require a firmware producer; AI and motion guards
  require valid measurements. Motion sampling is limited to once per minute.
- The live spike protocol carries a 64-channel mask; this is not proof of
  128-channel spike-viewer support. Preview selection and acquisition counts
  continue to use the device's reported channel configuration.
- BLE installation cannot replace the bootloader/resident recovery service.
  Use a matching fused image and an installed firmware with BLE staging support.
- This Firebase dashboard remains status-only: it does not issue remote BLE
  commands, stream raw waveforms, or upload recording files.

## Validation commands

Verified in this update: Android debug build succeeded (version 0.1.5, code 6),
136 Android unit tests passed, 12 Firebase-dashboard tests passed, and the
Firebase Vite production build succeeded. The local preview returned HTTP 200.
No browser interaction test, live BLE/HIL test, or phone installation is claimed.

On 2026-09-10, with operator approval, the Firebase dashboard was deployed to
https://wild-984fd.web.app. Hosting version: `2081fc59197010f3`. All 12 dashboard
tests and the production build passed again before deployment. The public page
returned HTTP 200 and its JavaScript SHA-256 matched the tested local build:
`294250911fd3dfb1ee057e75d887d66872f62d427266c6aa33d826d9283dfb1b`.
Only Hosting was deployed; Firestore rules, functions, and APK distribution were
unchanged by that Hosting deployment. Android 0.1.5 was still local at that point;
see `RELEASE_NOTES_0.1.5.md` for the subsequent APK release.

Later on 2026-09-10, the identical Android 0.1.5 (6) APK was uploaded and
distributed to the existing tester through Firebase App Distribution. Release:
`5r1q7kb761mj8`. The final build/test check passed again before upload. This does
not constitute a new live BLE validation or an automatic installation on phones.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain
```

From `web-dashboard`:

```powershell
node --test firebase-hosting/tests/device-details.test.mjs
node node_modules/vite/bin/vite.js build firebase-hosting --config firebase-hosting/vite.config.js
```

Before live use, verify scheduler read/edit/readback, a short timed recording,
profile save/verify, reconnect with an unsaved draft, CE64 and CE128 channel
selection, and cloud observation age with one modern Android and one legacy PC
reporter. Do not treat build success as that live-device validation.
