# WILD control panel 0.1.5

Version code: 6. Android 6.0 or later. Private tester APK using the existing
debug signing identity, so existing compatible test installs can update in place.

Published to Firebase App Distribution on 2026-09-10. Release: `5r1q7kb761mj8`.
[Install using an invited tester account](https://appdistribution.firebase.google.com/testerapps/1:125212755833:android:b2f096be06c92fe51111a8/releases/5r1q7kb761mj8).

## Changes

- Direct task navigation, larger controls, and clearer device information.
- Scheduler-v2 rule editing: daily, one-shot, repeating, and condition-triggered
  actions, with recording profiles and full profile readback verification.
- Serialized scheduler requests, echoed-reply framing, and bounded safe retries.
- Configuration readback status waits for System, DSP1, and DSP2 after stop.
- Per-station cloud health, acquisition, schedule, and AI summaries using the
  existing cloud heartbeat; improved sparse-page provenance and stale data handling.
- Clearer fused-firmware installation guidance for matching hardware variants.

## Validation and installation

136 Android unit tests pass and the debug APK builds successfully. Live CE64/CE128
hardware validation remains pending; this is a tester build, not a Play release.

The APK is distributed through Firebase App Distribution for the existing
`com.wild.android` app. Invited testers should use their invited Google account
in Firebase App Tester to download and install the build. WILD also checks at
app startup, limited to once every six hours. Installation still needs Android's
confirmation; a release upload does not silently install on phones.

SHA-256 of the APK:
`a578e0e1f2e3e094a319cff0e88ce187f7bef4069a34602287d8f7d31c10c1a9`

Detailed compatibility boundaries: [CE64/128 update notes](COMPATIBILITY_20260907.md).
