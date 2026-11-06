# WILD-X advanced Android BLE controls — 0.1.8

This extends [0.1.7 operating modes](WILDX_BLE_MODE_COVERAGE_20261106.md) with
IMRO programming, closed-loop setup and recording catalog listing/deletion.
The former PC-only labels for those functions no longer apply in 0.1.8.
The APK is a debug-signed **tester prerelease**, not a hardware-qualified release.

## Probe maps: import, stage, then verify

1. Devices → explicitly Connect → Control → **Probe maps**. Pick the physical
   connector P1, P2 or P3; a filename does not select a connector or establish
   physical probe presence.
2. Import `.imro` or the console's `.npconfig.json`. NP1 accepts IMRO type 0 /
   NP1000/NP1001 and native NP1 JSON. NRIC1384 accepts native JSON with its fixed
   wiring. NP2 programming accepts NP2013 four-shank, gain100, all384 channels
   active; legacy gain80 and single-shank models are rejected rather than
   silently converted.
3. Review the connector/model, request identity and CRC, then **Stage this probe
   map**. Validation checks channel IDs, physical routing, shared references,
   reserved sites, gains, standby and file-size limits before any device write.
4. Start the recording separately. The phone rechecks/restages retained maps
   after final profile selection. Read-only receipts are checked after probe
   preparation, or use **Read device map receipts** yourself.

“Staged” means resident on the MCU for the next preparation, not programmed on
silicon. “Applied” requires matching ID/CRC and firmware verification. NP2
degraded receipts explicitly retain their failed-shank mask and unverified
mapping warning. No receipt establishes analog signal correctness. A delayed
receipt never sends Stop to an active recorder.

The selected file is bounded to 1 MiB (IMRO 128 KiB). NP2's four 1280-entry routing
tables are extracted from the existing console's vendor catalog identity
`85c1236e512be518f6706256c24a214fab7ff238e993149e242fd5ad69ba167f`.
The Android 32-byte request ID hashes the canonical physical payload with the
ID bytes zeroed, including the selected connector. This differs from the PC
editor's normalized-JSON ID algorithm; it is an opaque on-wire identity, not a
claim that the original JSON identity is shared. Exact source-file SHA-256 is
retained separately. Three IMRO fixtures match the PC compiler's physical
payload bytes outside those 32 opaque ID bytes.

NPC1/NPC2 operations share the existing serialized B8 connection, with family,
operation, transaction and CRC checks. Upload chunks require exact received
offsets. COMMIT is delivered once. A failed partial transfer is left for the
firmware's bounded lease to expire; no automatic ABORT/COMMIT is injected into
an uncertain byte stream. Read-only status can establish a matching resident
map without uploading/replaying it. Requests survive reconnect on that device;
old application receipts do not become fresh proof on the new connection.

## Closed-loop: optional detector setup

Control → **Closed-loop** → Read capabilities & detector status. Set one/two
detectors, source connector/channel, threshold multiplier, rectification and
training start/duration in milliseconds relative to capture start. Only the
Ripple filter is exposed. This configures event detection, not CE64 stimulation
or neural amplifier gain, and does not start recording.

Admission follows `NpClosedLoopSettings` in the current console:

| BLE-controlled SD profile | Detector constraints |
| --- | --- |
| NP1 P1+P2 at 15 kHz | One/two distinct sources admitted by firmware capability masks |
| NP2 P1+P2+P3 at 20 kHz | One admitted source; two detectors must use P2 channels 0 and 1 |

Firmware must also advertise the detector count, probe mask, channel group and
Ripple filter. Other profiles remain disabled. These are the PC's scoped
qualification gates; they are **not a new Android hardware qualification**.
The current reference is console
`Note/RECORDING_VALIDATION.md#closed-loop-qualification` and its2026-11-04
evidence for the specific APP/USB artifacts and recording modes.

Apply is stopped-only, explicitly confirmed, retires the expendable preview
lease and makes one CONFIGURE followed by read-only verification of the exact
request CRC/profile. Disable is a distinct explicit zero-detector CONFIGURE.
A missing receipt leaves setup uncertain and blocks Start/new configure until
readback resolves it or the user explicitly disables it. Reconnect retains
that uncertainty rather than treating it as Off. A configured external setup
must be configured/disabled explicitly before this phone starts a recording.
Preview remains unavailable while closed-loop is configured/active or unknown.

## Recording catalog and Delete Last

Control → **Storage** → Refresh recording list. The list shows each recording's
ID, allocation size and sector range; NP2 includes firmware metadata summaries.
Allocation is not a precise valid sample count. Complete metadata is not a
payload-integrity guarantee; downloads verify that separately.

| Firmware catalog | Listing | Deletion |
| --- | --- | --- |
| Legacy V1 | Native94 pages, duplicate extent checks, bounded read-only prefixes | Last only, unchanged full list/status; requires both native95 and90 receipts |
| V1 filesystem2 | S2PG page/CRC, epoch/generation, explicit ranges; missing descriptors labeled | Advertised NDU1 inspection/commit with exact snapshot, UID, CID, epoch, capacity and nonce checks |
| NP2/NLD2 | Capability22, correlated INFO/ENTRY, stable token/count, metadata and tail checks | Firmware delete eligibility, fresh full snapshot/CID, exact token/count/tail cookie and explicit key |

Confirm the device, last ID, allocation and sector range. The client re-reads
the list before deleting and refuses a changed catalog/card. It invalidates
the confirmation before sending, delivers one mutation and verifies the new
count and unchanged earlier entries. Unknown acknowledgement/completion never
reuses the prior consent or triggers a retry. Refresh and inspect the result.
V1 filesystem2 without transactional support, missing descriptors, read-only
prefixes and ineligible V2 metadata never authorize deletion. On legacy V1,
firmware exposes no CID/UID in native catalog/delete replies: protection is
limited to the serialized connection, identical pages, capacity and frontier;
the UI does not pretend those replies provide transactional identity.

Delete removes catalog metadata; later recordings may overwrite that extent.
It cannot be undone by the app. Format/rewind are separate destructive actions
and are never used as deletion fallbacks. An uncertain native reply retires
the connection so a late frame cannot satisfy another operation. No physical
storage mutation was performed while developing this update.

## What is not a BLE phone workflow

The firmware's full-rate USB acquisition, recording download/export, USB
firmware installation and live USB camera/audio transfers still require the PC
or a card reader. The phone offers their BLE mode handoffs, not those USB
transfers. Experimental pre-arm/first-sync is disabled in the published console
and is not promoted by this APK. No WILD-X command uses CE64's OTA/stimulation
wire definitions.

## Verification and release

The Android build runs:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain '-Pkotlin.incremental=false'
```

Regressions cover three PC-compiled IMRO routing fixtures; invalid/duplicate
routes and fractional JSON; family/transaction/upload/commit identity; lost
probe COMMIT recovered with read-only receipts; qualified closed-loop source
gates, float packing and lost CONFIGURE without replay; legacy/filesystem2/V2
catalog/deletion; changed catalog/CID refusal; lost delete consent invalidation;
fragmented NDU1 and truncated-preview recovery; both native deletion receipt
orders; and unchanged cloud fingerprint/no raw map/catalog uploads.

Physical acceptance remains pending: actual NP1/NP2 probe application, detector
training and effects on recording/IMU integrity, BLE reconnect/pressure, and
catalog mutations on an explicitly expendable card. No phone was installed or
device connected/recorded/deleted/formatted/reset/flashed for these checks.

Final result: **170 tests, zero failures/errors/skips**, and APK build passed.
Capability queries are kept separate from actual closed-loop status so a CAPS
reply cannot overwrite an active/configured state with a false Off indication.
Existing SDK XML/deprecation/META-INF v1 warnings remain; v1/v2 signature
verification succeeds with the same signing identity as published 0.1.6.

Release asset: [WILD control panel 0.1.8](https://github.com/zifangzhao/WILD_Android/releases/tag/v0.1.8),
package `com.wild.android`, versionCode `9`, minimum API `23`, target API `35`.
APK size 28,459,201 bytes; SHA-256
`b17fcdf51db3e5568479e52ec581c2115e942669300c023c6fbde8e0427f799a`.
The release APK is byte-identical to the checked Gradle output. No Firebase
deployment or phone installation is part of this publication.
