# WILD-X Android live preview — 0.1.9

This is a software-validated **tester build**, not a new physical WILD-X BLE qualification.
It extends the [0.1.8 controls](WILDX_ADVANCED_BLE_20261106.md) without changing
recording, IMRO, closed-loop, catalog, cloud or firmware-installation protocols.

## Quick start

1. Connect explicitly to each intended WILD-X device. Apply its recording profile
   and start neural SD recording under **Recording**. Preview cannot start capture.
2. Open **Live preview**, or **Control → Preview**. All discovered WILD-X devices
   are listed alphabetically; the page does not connect them automatically.
3. Under each device, choose a probe and comma-separated channels, for example
   `0, 64`. On NP2, select the second trace's probe separately. Press **Show live
   preview** for that device. Channel setup collapses once preview starts.
4. Enable another device to see its own labelled traces below. Each trace names
   the device, probe, channel and AP/LFP band. The windows are relative to each
   device's latest sample: **they do not establish cross-device synchronization**.
5. **Pause this preview** affects only that device's display. **Pause all previews**
   retires all preview leases. Neither operation stops SD recording. Use the
   separately labelled recording Stop/cancel button to stop capture.

Switching the active device or WILD-X control tab keeps subscriptions running.
Leaving WILD-X controls queues preview-only Stop, including preparation in flight.
Disconnect/lease expiry also retires preview. Paused/disconnected traces retain
their last samples, marked with the last received time; they are not live reports.

## Available channels depend on firmware

| Family | Concurrent traces per device | Signal and checks |
| --- | --- | --- |
| NP1 compact capability `0x08000000` | 1–4 distinct channels on one P1/P2/P3 probe and one band | AP or LFP, raw 10-bit ADC, per-lane valid masks and source offsets, CRC |
| New NP2 multi-preview extension | 1–2 distinct probe/channel pairs on P1/P2 | 500 Hz filtered AP preview, useful band 0–200 Hz; signed Q3 counts divided by 8 exactly once |
| Older NP1/NP2 | One trace | Existing subsampled raw preview; aliasing possible |

NP2 recording on P3 does not imply BLE P3 preview or an LFP stream. The app cannot
show all384 channels over BLE. Configured or unconfirmed closed-loop processing
blocks preview because the firmware shares its workspace; it never disables CL
automatically to make a plot work.

NP2 support is queried first. Only the old firmware's explicit typed unsupported
reply enables legacy fallback. A timeout/corrupt capability response is not treated
as old firmware. If two channels were requested on legacy firmware, select one
rather than silently substituting or duplicating a trace.

## Time, gain and offset

- Horizontal two-finger pinch changes the shared time window, 0.1–10 seconds.
- Vertical two-finger pinch changes shared **display** gain, ×0.1–100.
- One-finger vertical drag changes that trace's offset; **Center trace** resets it.
- **Display settings / spectrum** offers time presets, a gain slider and reset.

These gestures only affect plotting. They do not program amplifier gains,
sampling rates, IMRO, closed-loop thresholds or recording duration. Touch the
waveform to adjust offset; drag outside it to scroll the page.

## Calibration and units

Default traces use signed nominal ADC counts, with `midpoint − raw` polarity
exactly once: NP1 midpoint512, NP2 midpoint2048. Filtered NP2 uses its already-signed
firmware values instead. **Counts are not automatically microvolts.**

For each device/probe/band, open **Calibration / µV units**:

1. Enter the physical probe's numeric serial. On NP1 enter the actual AP and LFP
   acquisition gains, not display gain. Supported factory gains are 50,125,250,
   500,1000,1500,2000,3000. This is operator-supplied identity, not a probe readback.
2. Import the factory gain CSV. NP1 has17 columns, NP2 has2. Complete ordered
   channel rows, positive finite factors, family and serial are checked. NP1
   optional ADC CSV needs32 ordered rows, matching serial and valid trim ranges.
   Files are bounded to2 MiB each. Nonuniform960-electrode factors are refused
   without a confirmed channel-to-site mapping; routing is never guessed.
3. Validate/save, then confirm the serial/gain binding. Tables are saved privately
   on this phone, with SHA-256 provenance, bound to device ID and connector. They
   are not uploaded to Firebase or applied as hardware comparator/bias trims.
4. The factory output is **calibrated counts**. To display µV, explicitly supply
   **µV per corrected AP/LFP count** for the actual model, band and gain, then enable
   the operator-scale checkbox. The app deliberately does not assume a generic
   input range or derive volts from a guessed gain. AP/LFP scales are independent.

For raw NP1, threshold/offset correction precedes factory gain. For filtered NP2,
only its linear factory gain factor is applied after decoding; nonlinear NP1 ADC
trims cannot be applied to an already filtered NP2 waveform. Display calculations
retain floating-point precision rather than quantizing a recording/export file.

For a nominal sample `x`, factory factor `g` and explicit scale `s`, the simple
gain-only path is `display_uV = x × g × s`. Display gain changes only the visible
range, not this value. Recheck serial/gains whenever a probe is moved or recording
configuration changes. Reopening WILD-X controls requires calibration confirmation;
saved files alone are not evidence that the same physical probe is attached.

## Display filter and spectrum

Display high-pass options: off,0.7,5 or30 Hz (one-pole). State resets at NaNs or
noncontiguous source timestamps; no line or filter transient bridges a gap.
Raw samples remain unchanged in memory and on SD. NP2 firmware's anti-alias FIR
is separate from this host display filter. A host filter cannot undo aliasing
already introduced by a legacy subsampled stream.

The spectrum uses the latest64–512 **contiguous** samples, a power-of-two FFT,
mean removal, Hann window and one-sided peak-amplitude normalization. Frequency
is based on the actual preview rate, not the full recording rate. The page labels
frequency resolution, amplitude units, peak and Nyquist. A gap resets the usable
window. This is not a full-rate AP spike spectrum, PSD, or diagnostic measurement.

## Bounded transport, memory and cloud usage

At most5000 points per selected trace are retained (~10 seconds at500 Hz).
The receive channel is32 packets; NP2 out-of-order recovery is bounded to64
sequences. CRC failures/missing packets request checked original data using the
firmware's cumulative ACK/replay protocol. Only contiguous sequences with exact
source timestamps are published; samples/timestamps are never interpolated.
Replay exhaustion, changed settings or5 seconds without forward data pauses
preview and preserves recording. Legacy missing data leaves explicit gaps.

Control traffic has priority over expendable preview feedback/renewal. The
firmware's two-second preview lease remains the fallback on loss/partial delivery.
Recording/configuration commands are not retried as preview recovery.

Waveforms, filters, spectrum, calibration and gestures are **local to the phone**.
They add no Firestore listeners, sample uploads, or waveform-driven status writes.
Existing cloud throttling is unchanged; this is not a web waveform implementation.

## Validation and remaining physical checks

Automated checks cover compact NP1 masks/offsets, NP2 Q3 scale, CRC/reordering/
replay, discontinuity rejection, typed legacy fallback, two independent simulated
device subscriptions, preview-only pause, calibration identity/ranges, filter
reset, known-tone FFT frequency/amplitude, directional pinch and cloud exclusion.

Before calling this hardware-qualified, test actual NP1/NP2 firmware, one/four
NP1 and one/two NP2 traces, probe changes, long-running SD recording integrity,
phone orientation/gestures, multiple BLE links under contention, loss/reconnect,
and input amplitude/polarity with an independently known signal. No attached
hardware was controlled or installed during this implementation.
