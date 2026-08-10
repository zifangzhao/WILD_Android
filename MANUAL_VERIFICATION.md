# WILD Android Manual Verification

This checklist is for validating the Android CE32 / CE64 BLE client against the desktop reference in `C:\code\Project\Datalogger_allInOne\CE32_console\CE32_console\CE32_console`.

Use it on real Android hardware with one or more BLE-capable CE32 / CE64 / WILD devices nearby. The goal is to verify that the Android app covers the same practical BLE workflows as the Windows host, with phone-oriented UI and multi-device support.

## Preconditions

- Install the current debug APK from `app/build/outputs/apk/debug/app-debug.apk`.
- Use an Android phone with BLE support and Bluetooth available.
- Prefer a standard ADB phone such as a Pixel when possible; Huawei HDC/HDB-only visibility is not enough for `adb install`.
- If deploying from this repo, confirm the phone appears in `adb devices` or use `.\scripts\install-debug.ps1 -ListDevices` first.
- Charge the phone and target devices.
- If possible, prepare:
  - 1 device for single-device verification
  - 2 or more devices for multi-device verification

## 1. App Launch And Permissions

- Launch the app.
- Confirm the Bluetooth permission gate appears on first run.
- Grant permissions.
- If Bluetooth is off, confirm the Bluetooth enable gate appears and enable Bluetooth.
- Expected:
  - The app transitions into the main shell.
  - BLE scanning starts automatically after permissions are granted and Bluetooth is enabled.
  - The instrument header shows the current page, connected count, focus device, and scan state.

## 2. Device Discovery

- Open the `Devices` page.
- Verify candidate devices appear in:
  - `Pocket Fleet`
  - `Connected Sessions`
  - `Verified Candidates`
  - `Nearby BLE`
- Expected:
  - The top of the `Devices` page combines scan/connect/queue/scope/launch actions into one compact fleet hub instead of forcing a long setup preamble before the device list.
  - The pocket fleet hub's own `Link` / `Queue` / `Launch` switcher now renders as larger button tiles instead of a horizontal chip row, so the phone can move between bring-up pages without sideways scrolling.
  - Device cards stay compact by default and only reveal the deeper BLE identity / transport metadata after `Show Details`.
  - Device-card discovery, queue, role, and transport badges wrap instead of hiding in horizontal scroll strips.
  - Devices advertising `FFF0` or matching CE-style names surface as verified candidates.
  - Devices that only expose CE-style manufacturer telemetry still remain visible in the scanner list instead of disappearing just because the advertised name is blank or corrupted.
  - When advertisement battery bytes are present, the device card shows a pre-connect voltage reading before the BLE session is opened.
  - Odd/corrupted names still remain visible in `Nearby BLE`.
  - `Make Active`, `Connect`, `Verify & Connect`, and group-selection actions behave consistently.

## 3. Single-Device Connect

- Connect one verified device from the `Devices` page.
- Expected:
  - The device state progresses into connected/synced state.
  - The device can be made active.
  - The top shell shows `Connected = 1`.
  - The live shell and control pages become actionable.
  - If the target runs older legacy-wake firmware, a bare `0x80` wake acknowledgement or temporary `<CONFIG MODE BUSY>` response still settles into a usable connected/synced state instead of falling straight into reconnect timeout.
  - If the target emits Windows-known interface/diagnostic frames (`0xB0`, `0xB1`, `0xC0`, `0xF0`..`0xF4`), the Android session activity log reports them without breaking subsequent BLE framing.
  - The connected device card exposes direct `Live`, `Control`, and `Records` quick actions.
  - Those direct page shortcuts promote that device to active scope before opening the next page, so the phone does not stay armed for a stale multi-device target set.

## 4. Multi-Device Connect

- Connect 2 or more devices.
- Use:
  - mark a subset from `Verified Candidates` or `Nearby BLE`, then use `Queue Marked`
  - use `Queue Verified` or `Queue Nearby` when the whole visible lane should be staged
  - run `Connect Queue`
  - `Connect Verified`
  - manual connect per device
  - `Mark` / `Clear Mark`
  - `Select Connected`
  - target scope switching between `Active`, `Selected`, and `All`
  - the top fleet hub buttons to jump into `Live`, `Control`, or `Records` without rebuilding the session target set
- Expected:
  - queued subset connect works without forcing every verified candidate to connect
  - The `Connected` / `Linking` / `Verified` / `Nearby` selector and the queue-management shortcuts (`Select Connected`, `Clear Group`, `Clear Marks`) render as larger button grids instead of only chip rows, so the phone fleet hub stays usable one-handed.
  - `Connect Verified` also seeds `Selected` scope with that same batch before the links finish opening, so grouped Live / Control / Records work can start immediately from the newly connected fleet
  - candidate marks stay separate from the connected-device `Selected` group used by Live / Control / Records, so pre-connect queue staging does not silently change the scoped control target set
  - once a direct or staged connect starts for a device, its candidate `Mark` state is cleared instead of coming back automatically after a later disconnect
  - if a marked candidate is connected directly with `Connect Now`, that device still carries forward into the selected-group state and arms `Selected` scope instead of dropping the staged grouping intent
  - when the staged subset was built from `Queue Marked`, the queued tiles and staged connect order follow the exact mark order from the phone instead of the roster order
  - when `Connect Queue` starts, that same queued subset becomes the pending `Selected` target set, and the connected members appear in grouped scope in the same staged order as they come online
  - the queue hub also shows the current `Selected Group` order as removable two-column tiles, and tapping one removes that device from grouped scope without leaving the device-management page even if one member is reconnecting
  - before queueing, the queue hub shows the current marked-candidate order as removable two-column tiles, and tapping one clears that mark without leaving the hub
  - queued subset connect preserves the same device order shown in the phone queue tiles when the staged connect starts
  - tapping a queued tile on the queue page removes that device from the staged batch without leaving the pocket fleet hub
  - Multiple sessions stay connected at once.
  - Bulk connect attempts are staged instead of all starting at the same instant, so adding more devices during phone-side preview monitoring does not collapse into a concurrent GATT burst.
  - During manual connect, BLE scanning pauses and then resumes after the connect sequence settles instead of competing with the link setup the whole time.
  - While staged fleet connect is running, the shell and `Devices` page show live linking state instead of appearing idle.
  - Devices that are currently `Connecting` or `Reconnecting` stay in a dedicated linking area instead of slipping back into `Verified Candidates` or `Nearby BLE`.
  - From that linking area, `Cancel Link` stops an in-flight connect and `Stop Retry` stops an automatic reconnect loop.
  - While a staged fleet connect still has queued devices remaining, `Stop Queue` prevents the rest of that queued connect batch from starting.
  - Any connected session card can jump straight into `Live`, `Control`, or `Records` for that one device, and those shortcuts reset the target scope to `Active`.
  - Entering `Control` from the grouped fleet launch path pre-reads system params for the current connected scope, and entering per-device `Control` does the same for that one active device before the phone control surface opens.
  - disconnected members stay visible in the selected-device group for monitoring and reconnect workflows, but scoped preview, record, sync, parameter, and control fanout still exclude them until BLE reconnects
  - `All` scope targets all currently connected devices.
- Outside the `Devices` page, the global target strip shows the current selected-device group as removable device tiles and offers `Use Selected`, so the phone can switch into or trim that subset without leaving the current workflow.
- When that target strip is expanded, the common scope pivots and subset pivots now appear as larger phone buttons and quick tiles (`Focused`, `Live`, `Recording`, `All Linked`) instead of only another horizontal chip row.
- If every connected session drops but the active device or selected subset is still preserved for reconnect, that same global target strip stays visible on `Live`, `Control`, and `Records`, and its device tiles still let the user inspect focus or clear the subset without returning to `Devices`.
- That selected-device subset now keeps the exact phone tap order through the target strip and scoped control fanout instead of silently falling back to the connected-roster sort order.
- That same selected-device subset now also stays visible across disconnects on the queue hub and the monitoring-focused status/fleet hubs, so reconnecting members can be recovered or cleared without rebuilding the group from scratch.
- If `Selected` scope still has preserved members but none are currently linked, the `Devices` hub still opens `Live`, `Control`, and `Records`, and those pages now explain that grouped writes or record refresh/export stay disabled until one device relinks.
- While that preserved scope is reconnecting, the `Signal`, `Online`, `Camera`, `Status`, `Fleet`, and `Records` live hubs still keep their route buttons and focused-device tiles usable instead of collapsing back to connected-only navigation.
- The `Camera`, `Status`, and `Fleet` hubs also keep their `Online` route buttons enabled during that reconnect state, so the operator can reopen the online helper workflow without first restoring a connected-only active focus.
- Once 2 or more devices are connected, the shared `Selected Devices` builder appears directly on the multi-device `Live`, `Control`, and `Records` workflows even before the phone is already armed for `Selected` scope.
- That shared selection card's `Active`, `Live`, `Rec`, and `Ready` quick picks each replace the current subset with the matching connected devices, so subset changes do not require a long row of manual chip taps.
- That same shared selection card now exposes those quick picks as larger phone tiles and lists each connected device as an `Add` / `Remove` row, so subset editing still works cleanly when the fleet is wider than one horizontal chip strip.
- That same shared selection card now also renders the current scoped subset as removable two-column device tiles instead of a chip row, so group cleanup still works once several devices are already selected.
  - The fleet cards on the `Live` page show independent status per device.

## 5. Live Preview Workflow

- Open the `Live` page.
- In `Signal` view, start preview from the command deck.
- From the `Pocket Signal` hub, use the direct `Camera`, `Closed-Loop`, `Records`, and `Devices` buttons.
- Change preview source selection and repeat.
- Exercise the signal tuning card:
  - switch `Window` between `1s`, `2s`, `5s`, and `10s`
  - switch `Gain` across at least a low-zoom and high-zoom preset
  - toggle `Remove DC`
  - switch preview source to `DSP output A` and `DSP output B` when available
- Expected:
  - On the `Signal` pane, the waveform and preview/record command deck appear before the deeper tuning/selection cards so the phone lands on the instrument workflow first.
- The `Pocket Signal` hub can jump directly into `Camera`, `Closed-Loop`, `Records`, and `Devices`, so the phone live page mirrors the desktop online-page reachability for adjacent workflows and can return to the link/queue hub without depending only on the bottom navigation.
- That same `Pocket Signal` hub can also jump directly into `Status`, so health and activity diagnostics stay one tap away from the preview surface.
- That same `Pocket Signal` hub now keeps `Online`, `Camera`, and `Status` in the live-page chip row and moves only the remaining cross-workflow jumps into one compact route strip, so the top card stops duplicating the same destinations in both chips and full-width buttons.
- The page selectors used for `Signal`, `Online`, and the control subpanels now render as larger two-column phone buttons instead of only a horizontal chip row, so switching between monitor/display/group and the deeper control lanes is easier on a handset.
- The `Pocket Signal`, `Pocket Camera`, `Pocket Status`, and `Pocket Fleet` hubs now also render their main scope, pane, and route switches as button grids instead of sideways-scrolling chip rows, so the primary live workflow pivots are easier to tap one-handed.
- Those same live hubs now also render their focused-device chooser as two-column device tiles instead of horizontal chips, so switching the active session during multi-device monitoring is easier on a handset.
- The shared live target strip now also renders both focused-device switching and selected-subset trimming as two-column device tiles, so Online / Status / Camera / Fleet retargeting no longer falls back to horizontal chips once several devices are connected.
  - Preview and recording controls are on the same live page.
  - The live command deck exposes `Immediate Start` and `Arm Start` as larger paired mode buttons instead of a single chip toggle.
  - If the active live device or selected live group is preserved while some or all members reconnect, the same deck shows the linked-versus-preserved target count and explains that preview or record commands only fan out to the linked members until relink finishes.
  - Signal plots update.
  - Signal tuning changes the live plot without leaving the preview workflow.
  - The `Display` page uses larger `Window`, `Gain`, and `DC Handling` button grids instead of sideways chip rows.
  - Longer windows use the buffered preview history instead of looking identical to the shortest view.
  - Gain changes visibly zoom the trace display like the desktop online monitor instead of leaving the plot fully auto-scaled.
  - `Remove DC` changes the baseline behavior of the displayed trace.
  - The `Show Source` preview editor uses larger `Ephys` / `Aux`, bank, and exact-channel button grids instead of long chip strips.
  - The multi-device trace legend and the digital-flag readout both render as stacked phone tiles instead of horizontal scroll rows.
  - When previewing `DSP output A` or `DSP output B`, the live signal page paints the matching trigger-threshold guide on the trace instead of leaving the DSP monitor unannotated.
  - Digital flags update.
- The `Live` page now exposes `Online` as a first-class live-pane chip, so the operator can open the Windows-style helper surface without drilling through the signal-detail selector first.
- The first `Live -> Online` page is now a `Pocket Online` hub that keeps `Resync`, `No RTC`, `Read Sys`, `Read DSP`, `Read All`, scoped `Push Sys`, scoped `Push All`, and one-tap routes into `Console`, `Payload`, `Camera`, and `Records` together before the deeper per-lane cards.
- That same `Pocket Online` hub now promotes `Console`, `Payload`, `Camera`, and `Records` into larger lane tiles and keeps the surrounding status/maintenance routes in one button grid, so the online helper page is easier to scan and tap on a phone than the earlier chip-heavy layout.
- That same `Pocket Online` hub also includes direct `Health`, `Link`, and `Activity` routes into the `Status` workflow, so the operator can move from scoped online helpers into sync diagnostics, transport-quality monitoring, recent BLE events, and saved monitoring files without backing out to the live pane selector first.
- That same `Pocket Online` hub also includes a direct `Lifecycle` route into `Control -> System -> Lifecycle`, so reset, bootloader, firmware-update, and role-override actions stay adjacent to the online helper workflow on a phone.
- That same `Pocket Online` hub now also includes its own in-page scope button grid plus connected-device focus tiles, so multi-device online work can change scope or focus another connected session without leaving the page.
- That same `Pocket Online` hub now also keeps `Signal`, `Health`, `Link`, `Activity`, `Lifecycle`, `Devices`, and `Closed-Loop` in one route grid instead of split phone-width rows, so adjacent workflow jumps stay reachable one-handed.
- That same `Pocket Online` hub now also shows separate `System` and `Full` readiness pills, so the operator can see whether only the system payload or the full system-plus-DSP payload set is ready before sending a scoped push.
- If the active online device or part of the selected online group is reconnecting, that same hub now shows linked-versus-preserved target counts and explains that online helper reads or pushes only fan out to the currently linked members.
- That same `Pocket Online` hub still lets the operator reopen `Console`, `Payload`, `Camera`, and `Records` whenever preserved scope or cached lane data exists, so reconnecting sessions do not make those pages disappear from the online workflow.
- That same `Pocket Online` hub now also keeps a focused-device `Connect` / `Cancel Link` / `Stop Retry` / `Disconnect` action on the card itself, so link recovery no longer requires backing out to `Devices` before returning to the phone online workflow.
- That same `Pocket Online` hub now also keeps its focused-device tile grid aligned with preserved scope during reconnect, so retargeting the online helper lanes does not fall back to the connected-only roster when a selected device is relinking.
- If reconnect recovery leaves preserved scope available but clears the current active focus, opening `Online` or `Camera` now promotes the first preserved scoped device automatically instead of landing on an empty page.
- If the active live session drops while preserved scope still exists, the live workflow now also restores focus to the first preserved scoped device automatically so the current live page stays populated instead of collapsing to an empty state.
- That same `Pocket Online` hub now also serves as the only lane selector on the page, so the phone Online workflow does not stack a second duplicate selector card underneath it.
- Opening `Payload` from that live workflow now also requests a fresh system-parameter read first, so the payload tools and stream-rate editor do not start from stale system values even before the user taps `Read System` manually.
- That same active `Payload Tools` card now also keeps `Read DSP`, `Push All`, and a separate `Full` readiness pill beside the existing system payload actions, so the main online payload lane itself keeps the fuller CE32 maintenance set without bouncing back to the hub or control pages.
- If the active online device or part of the selected online group is reconnecting, that same `Payload Tools` card now shows linked-versus-preserved target counts and explains that scoped reads, waveform toggles, and payload pushes only fan out to the linked members.
- The scoped `Payload` wall for the non-focused devices now also keeps per-device `Read DSP` and `Push All` beside `Read Sys`, `Read All`, and `Push Sys`, so multi-device online payload work still has the fuller CE32 helper set after moving off the top hub.
- If an active or selected payload device disconnects after payloads were read, that same payload wall still keeps the device visible with cached-value or relink messaging instead of dropping it from the scoped list immediately.
- Launching `Closed-Loop` from that live workflow also pre-reads system and both DSP payloads, mirroring the desktop `Online` button so the phone control cards do not open on stale or empty parameter state when BLE is already connected.
- Opening `Records` from the same live workflow now also refreshes the active BLE record list first, mirroring the desktop `Live Logs` path so the phone record lane and full records page show current SD-log state without needing an immediate manual refresh.
- If the active record index is already cached, that same online `Records` lane still allows `Save CSV` after a disconnect, so export does not wait for BLE relink once the list is loaded.
- When the current scope is `Selected` or `All`, those same launch paths now pre-refresh the full connected target scope instead of only the active device, so grouped record monitoring does not open half-stale.
- Opening the live camera workflows now also requests a fresh preview frame on entry, so the dedicated camera page and the signal-side camera lane do not start on a blank viewer even before the user taps `Single Frame` or `Start Live`.
- Those same live camera workflows now also request a fresh camera-register read on entry, so the phone camera pages open with current `Reg0` / `Reg1` values instead of waiting for an immediate manual `Read Camera`.
- The `Live -> Status` page now lands on a `Pocket Status` hub that keeps `Health` / `Link` / `Activity` switching, scope summary, focused-device switching, and direct routes back into `Signal`, `Online`, `Camera`, `Records`, and `Devices` on one compact phone card.
- That same `Pocket Status` hub now also uses compact route chips instead of wider button rows, so diagnostics navigation stays denser on a phone.
- That same status hub now also includes a dedicated `Link` page so BLE RSSI, loss, receive rate, and packet-gap telemetry can be monitored on a focused phone page instead of hiding inside the broader sync-health cards.
- The dedicated `Health` and `Link` pages now also expose `No RTC` beside `Resync`, and the scoped status/link walls keep that same per-device recovery option without leaving the diagnostics workflow.
- The dedicated `Link` page and the scoped link wall now also keep direct `Connect` / `Cancel Link` / `Stop Retry` / `Disconnect` actions beside the transport telemetry, so active-session recovery and per-device disconnects can stay inside the link workflow during multi-device troubleshooting.
- The active `Health Monitor` card and the scoped health wall now also keep direct `Connect` / `Cancel Link` / `Stop Retry` / `Disconnect` actions beside the sync readout, so link recovery is available from the broader diagnostics page instead of only from the dedicated link surface.
- Those same status monitoring walls now also keep the selected or focused session visible when it is no longer connected, so the new recovery controls remain usable during multi-device dropouts instead of losing the device card immediately.
- The `Pocket Status` and `Pocket Fleet` hubs now also count the broader monitoring scope instead of only connected sessions, so their top-card summaries stay aligned with the deeper diagnostics and fleet-health pages while a selected or focused device is reconnecting.
- The `Activity` page now also keeps direct `Connect` / `Cancel Link` / `Stop Retry` / `Disconnect` actions beside the recent BLE event list, so recovery can start on the same phone page where the failure was observed.
- The `Activity` page and the `Fleet -> Health` cards now also keep `No RTC` beside `Resync`, so event review and multi-device health monitoring can retry sync without leaving the current live page.
- The `Fleet -> Health` page now also keeps the selected or focused session visible when it drops out of the connected set, and those health cards expose the same direct `Connect` / `Cancel Link` / `Stop Retry` / `Disconnect` action, so multi-device recovery can stay on the fleet page instead of bouncing back to the roster.
- The `Fleet -> Health` cards now split `Focus` / `Show Detail` from the recovery actions, leaving `Resync` / `No RTC` on their own row so the multi-device health controls stay easier to tap on a phone.
- `Live -> Online` is split into `Console`, `Payload`, `Camera`, and `Records` lanes, so the desktop online panel no longer lands as one long phone card.
- The `Console` lane carries the `Signal Companion` card with the active device's Windows-style `Cmd`, `Prev`, and `Rec` counters together with live voltage, used storage, record runtime, sync summary, recent BLE activity, and both `Resync` plus `No RTC`.
- That same `Signal Companion` card now also keeps the focused-device link action on the same page, so the live console can connect, cancel, retry-stop, or disconnect without leaving the phone monitoring workflow.
- After tapping `Sync Log` once, that same `Signal Companion` card keeps the resolved sync CSV path visible on the phone instead of relying only on the transient banner.
- When more than one scoped device is active, the same `Console` lane also shows a compact scoped console wall for the remaining devices so online counters, sync state, and quick `Inspect` / `Resync` / `No RTC` actions stay on the same phone page.
- Those same scoped console-wall cards now also keep direct `Connect` / `Cancel Link` / `Stop Retry` / `Disconnect` actions, so one dropped session in a multi-device online view can be recovered or removed without switching back to the roster first.
- Those same console monitoring walls now also keep the selected or focused session visible when it drops out of the connected set, so the phone can still reconnect it in place instead of hiding it as soon as the BLE link falls.
- When the session has received raw BLE notifications or legacy `<CONFIG MODE BUSY>` responses, the `Signal Companion` transport line surfaces `Notif` and `Busy` counts instead of hiding that transport noise from the phone console lane.
- The `Payload` lane supports `Read Sys`, `Read DSP`, scoped `Push Sys`, scoped `Push All`, `Read All`, a separate `Full` readiness state, and a custom FS entry in addition to the quick chips, and once system params are loaded it can also toggle camera and ADC stream rates from the same phone workflow.
- That same `Payload` lane now keeps its FS presets and Camera/ADC stream toggles in larger button grids instead of horizontal chip rows.
- That same `Payload` lane now also shows the parsed raw `VBatt`, `Audio Ratio`, and `Camera Ratio` system values and can send scoped `Apply Sys Fields` updates directly from the online helper page.
- That same active `Payload` lane now also keeps `Wave Path` and the current waveform-file path visible inline.
- When more than one scoped device is active, that same `Payload` lane also shows a scoped payload wall for the remaining devices so `Inspect`, `Read Sys`, `Read DSP`, `Read All`, `Push Sys`, and `Push All` remain available without leaving `Live -> Online`.
- Those same scoped payload-wall cards now also allow per-device raw `VBatt`, `Audio Ratio`, and `Cam Ratio` edits with direct `Apply Sys Fields`, so one device can be tuned without changing the entire current scope first.
- Those same scoped payload-wall cards now also allow per-device FS entry, `Quick FS`, `Apply Stream`, and Camera/ADC stream toggles behind `Edit Stream`, so one device's online stream settings can be changed without changing the active page target first.
- Those same scoped payload-wall cards now also allow per-device waveform arm/disarm plus `Wave Path`, so trigger-wave capture and saved-file access stay on the same multi-device online lane.
- Those same scoped payload-wall cards now also keep the waveform-file path visible inline per device instead of relying only on the banner.
- The `Camera` lane keeps quick remote-monitoring capture on the same live-signal workflow, and the `Records` lane keeps `Refresh Index`, `Save CSV`, `Open Records`, and `Export Path` next to the live signal deck like the Windows `Online` page's `Records` button without crowding the monitor page itself.
- Those same live-workflow `Records` routes now also land on the `Index` pane instead of the generic vault, so after a phone-side `Refresh Index` the operator lands on the raw on-device BLE record list the way the Windows `Live Logs` button does.
- Those same scoped online record-wall cards now also keep the current export folder visible inline, so `Export Path` does not depend only on the banner there either.
- That same `Camera` lane now also keeps `Read Camera` plus `Apply Camera` on the same page as `Start Live`, `Single Frame`, and `Snapshot`, matching the Windows `Form_Snapshot` workflow more closely on the phone.
- When more than one scoped device is active, that same `Camera` lane's remote wall now also allows per-device `Read Camera` and `Apply Camera` register updates behind `Edit Camera Regs`, so one device can be retuned without changing the active page target first.
- After tapping `Export Path` or saving a record CSV, the record cards keep the active device's export folder visible inline on the phone instead of only reporting it through the banner.
- When more than one scoped device is active, the same `Camera` lane also shows a scoped remote wall for the remaining devices so `Inspect`, `Start Live`, `Single Frame`, and `Snapshot` stay available without leaving `Live -> Online`.
- When more than one scoped device is active, the same `Records` lane also shows a scoped record wall for the remaining devices so `Inspect`, `Refresh`, `Save CSV`, and `Export Path` stay available without leaving `Live -> Online`.
  - The top live hub metrics follow the armed scope instead of always counting every connected device.
  - `Start Preview`, `Stop Preview`, `Start Recording`, and `Stop Recording` only enable when the scoped device states match the same arming rules used by the Windows host.
  - If a mixed multi-device scope has some sessions already previewing and others still synced, the preview start action switches into a grouped preview re-sync instead of blindly sending a second preview start to every device.
  - The main `Command Deck` keeps traffic, voltage, used-space, and record-readiness metrics behind `Show Deck Detail`, so the default live phone view stays shorter before the operator asks for more telemetry.
  - The main `Command Deck` keeps the preview-source editor hidden behind `Show Source` until requested, so the live phone page opens on preview/record controls first instead of immediately expanding the full source picker.
  - Compact sync and BLE link health remain visible on the same signal page without switching away to another monitor pane.
  - In multi-device scopes, every visible trace has a readable legend tile and tapping one can focus that device without leaving the signal view.
  - In multi-device scopes, `Overlay` and `Stacked` signal layouts both work, and stacked mode keeps each device readable on a phone-sized screen.
  - Preview source changes propagate correctly.
  - For multi-device scopes, `Start Preview` / `Stop Preview` fan out to the intended target set.

## 6. Recording Workflow

- From the same `Live` page, start recording.
- Turn `Arm Start` on, press `Arm Recording`, then confirm the primary action changes to `Trigger Start`.
- While armed, verify `Cancel Arm` clears the staged start without sending a BLE record command.
- Re-arm and trigger the scoped recording start.
- Confirm the recording timer updates.
- Stop recording.
- Expected:
  - Recording begins without leaving the signal workflow.
  - `Arm Start` lets the phone stage the next scoped recording start in the same command deck instead of forcing the user back into a desktop-style helper workflow.
  - When armed, `Trigger Start` only enables once the scoped device set is in a valid synced-or-previewing state.
  - Record start/stop transitions complete without hanging UI state.
  - Group recording start stays blocked until every scoped device is already synced or previewing, matching the desktop host.
  - Multi-device record actions follow the current target scope.
  - After stopping, sessions recover into a synced/preview-ready state.

## 7. Fleet Monitoring

- Switch the `Live` page to `Fleet`.
- Switch between `Active`, `Selected`, and `All` scope.
- For each connected device:
  - focus the card
  - toggle preview
  - start/stop recording
  - run `Resync`
  - change preview bank / source from the card
  - request a camera frame
  - request a full camera snapshot
  - toggle camera live preview
  - open `Sync Log`
  - if waveform capture is enabled for that device, open `Wave Path`
- Expected:
  - The fleet cards follow the armed scope instead of always showing every connected device.
- The `Live -> Fleet` page now lands on a `Pocket Fleet` hub that keeps the `Operate` / `Camera` / `Health` fleet-page switch, scope summary, focused-device switching, and direct routes into `Signal`, `Online`, `Status`, `Records`, and `Devices` on one compact phone card.
- That same `Pocket Fleet` hub now also uses compact route chips instead of wider button rows, so multi-device workflow jumps stay denser on a phone.
  - `Active` scope only shows the focused device, `Selected` only shows the selected subset, and `All` shows the full connected fleet.
  - Fleet cards remain independently actionable.
  - `Resync` can be issued for one device from the fleet page without changing the global control scope.
  - Preview source changes can be issued per device without leaving the fleet monitor.
  - The expanded fleet preview editor uses larger `Ephys` / `Aux`, bank, and exact-channel button grids instead of chip rows.
  - Fleet-card role, function, and verified-transport badges wrap instead of hiding in a horizontal scroll row.
  - Camera preview tiles update per device.
  - A full snapshot can be requested per device from the fleet view without first changing the active session.
  - The `Sync Log` action surfaces a device-specific path.
  - `Wave Path` surfaces the device-specific waveform capture file without forcing the user back to the active-session control page.
  - Link status, sync summary, and recent activity remain visible per device.

## 8. Camera Monitoring

- Switch the `Live` page to `Camera`.
- For the active device:
  - tap `Start Live`
  - tap `Single Frame`
  - tap `Snapshot`
- When multiple devices are selected or connected:
  - keep one active device in the large viewer
  - use the scoped camera wall for the other devices
  - request `Single Frame`, `Snapshot`, and `Start Live` from those per-device cards
  - open `Edit Camera Regs` on one non-active device card
  - confirm `Reg0` / `Reg1` show the cached values for that device
  - tap `Read Camera`, change one register value, and tap `Apply Camera`
  - tap `Inspect` to promote one wall device into the large viewer without leaving the camera page
- Expected:
  - The `Live -> Camera` page now lands on a `Pocket Camera` hub that keeps scope summary, focused-device switching, and routes back into `Signal`, `Online`, `Status`, `Records`, `Closed-Loop`, and `Devices` on one compact phone card.
  - That same `Pocket Camera` hub now uses compact route chips instead of wider button rows, so remote-monitoring navigation stays denser on a phone.
  - Low-resolution preview frames appear and continue when live mode is enabled.
  - Snapshot and preview panes update separately.
  - The camera page can monitor more than one connected device without forcing every capture through a single active-device workflow.
  - If an active or selected camera device disconnects after frames were captured, the `Pocket Camera` page and the signal-side `Online -> Camera` wall still keep that device visible with relink or cached-frame messaging instead of dropping it from scope immediately.
  - The scoped camera wall can read and apply one device's `Reg0` / `Reg1` values without changing the active-device focus or the current global scope first.
  - Camera monitoring remains on the live page rather than being hidden in a secondary workflow.

## 9. Control Page Coverage

- Open the `Control` page and verify all four panels:
  - `Acquisition`
  - `Closed-Loop`
  - `I/O`
  - `System`
- Expected:
  - Each panel is reachable and usable on a phone-sized layout.
  - The top `Control Hub` uses larger scope/page button grids plus two-column focused-device tiles instead of horizontal chip rows.
  - If the active control device or part of the selected control group is reconnecting, the same hub explains that scoped writes only unlock for the linked members instead of looking like the scope disappeared.
  - Scope selection is visible and respected.

### Acquisition

- Run:
  - the compact `Pocket Acquisition` deck appears before the deeper acquisition editors
  - `Resync`
  - `No RTC`
  - `Read Sys`
  - `Read DSP`
  - `Read All Payloads`
  - stream-rate updates
  - quick FS write
  - raw `VBatt`, `Audio Ratio`, and `Camera Ratio` system-field edits plus scoped `Apply Sys Fields`
  - camera param reads/writes
  - impedance request
  - impedance CSV export after data is received
  - auto impedance start/stop with a short interval
- Expected:
  - The most common sync/read/capture actions are grouped into one compact top card instead of being split across separate quick-action sections.
  - the one-step payload refresh reads system + both DSP blocks for the current scope
  - the deeper acquisition editor shows the current parsed `VBatt`, `Audio Ratio`, and `Camera Ratio` values from the system payload and can send a scoped `Push Sys` directly after editing them
  - `Impedance` and `Read Cam` from the acquisition deck follow the current `Active`, `Selected`, or `All` scope, while `Snapshot` and `Preview Shot` stay tied to the active device.
  - The camera-register and impedance cards show scope, target count, and active-device source context so phone users can tell which values are being displayed and which devices will receive the next scoped action.
  - `CSV Path` on the impedance card surfaces the exact Android folder used for the active device's impedance exports.
  - After tapping `CSV Path` or saving an impedance bundle, that same card keeps the current export folder visible inline so the destination stays visible during phone use.
  - the stream-rate editor shows the armed scope and target count, and makes it clear that current values come from the active device while writes fan out to the scoped targets
  - that same stream-rate editor keeps its FS presets and Camera/ADC toggles in larger button grids instead of horizontal chip rows
  - Status and raw payload panels update after reads.
  - The impedance diagnostics card separates magnitude, crosstalk, and phase instead of collapsing every packet type into one list.
  - `Run Test` can fan out to the in-scope devices, while `Save CSV` still writes the latest impedance bundle from the active device into the app files area.
  - `Auto Impedance` immediately runs one BLE sweep, then keeps repeating at the configured minute interval until stopped.
  - If the target device is disconnected when a scheduled pass starts, the scheduler attempts the Windows-style fast-bootstrap connect path first and still reaches a usable synced/preview-ready state before the sweep.
  - After a successful scheduled sweep, the app requests a software reset and the BLE link drops before the next scheduled pass, matching the desktop automation cleanup more closely.
  - While the scheduler is active, the card shows the current target device, interval, next run time, and rolling status text.
  - Control writes do not leave the UI in stale state.

### Closed-Loop

- Verify:
  - the quick-controls card stays compact by default and only opens the channel editors after `Show Channel Editors`
  - stim enable/disable
  - trigger gain
  - trigger threshold
  - stim intensity
  - manual trigger
  - waveform toggle
  - waveform capture path
  - advanced stim timing and DSP live writes
  - closed-loop profile staging
  - direct `Stage + Push Sys` and `Stage + Push All`
- Expected:
  - Quick controls work from a phone layout.
  - The first screen keeps stim toggle / force-trigger actions visible without forcing both channel editors open at all times.
  - The quick-controls card shows the current scope, target count, and active-device source context so phone users can tell that displayed values come from one device while quick writes fan out to the armed scope.
  - Stim enable/disable, force-trigger, and the channel-editor quick apply actions follow the current `Active`, `Selected`, or `All` scope.
  - The profile-stage card and advanced stim / DSP live editors also show scope and active-device source context, and their apply actions now follow the current `Active`, `Selected`, or `All` scope.
  - The `Closed-Loop Profile` page uses larger two-column button grids for both mode selection and stim routing instead of chip rows.
  - The `Closed-Loop Profile` page can now send the staged profile directly with `Stage + Push Sys` or `Stage + Push All` instead of forcing a detour through the `System` panel.
  - Those direct profile-send actions only enable when the scoped devices already have the required payload blocks loaded, matching the app's existing ready-count safety gates.
  - Enabling trigger waveform creates a per-device capture file and `Wave Path` surfaces that location from Android.
  - As `0xAE` packets arrive, waveform block counts and saved-byte totals increase on the monitor cards instead of only flipping a boolean.
  - Advanced editors remain usable without desktop-only interaction patterns.

### I/O

- Toggle:
  - LED
  - GPIO0
  - GPIO1
- Repeat with `Active`, `Selected`, and `All` scope.
- Expected:
  - Aggregate state is visible for group scopes.
  - GPIO mode selection uses larger two-column button grids instead of chip rows.
  - Commands apply to the intended device set.

### System

- Verify:
  - the top system card stays compact by default and only reveals DSP push / boot / role-override actions after `Show System Actions`
  - upload system params
  - upload DSP params
  - upload all params
  - switch between `Push`, `Lifecycle`, and `Dump`
  - sleep
  - reset
  - bootloader
  - firmware update request
  - role override (`AUTO`, `MASTER`, `SLAVE`)
- Expected:
  - The `System` panel separates `Push`, `Lifecycle`, and `Dump`, so scoped payload sends no longer sit on the same phone page as reset / boot / role actions.
  - The compact `Push` lane keeps `Push Sys` and `Push All` visible first, while DSP-only sends stay behind a smaller reveal.
  - `Push Sys`, `Push DSP1`, `Push DSP2`, and `Push All` follow the current `Active`, `Selected`, or `All` scope instead of always writing only to the active device.
  - The system deck shows the current scope and ready counts so phone users can see how many in-scope devices already have the required payloads loaded.
  - The `Lifecycle` lane shows the active device identity and current BLE role before sending sleep, reset, bootloader, firmware-update, or role-override commands.
  - reset, bootloader, and firmware-update requests require explicit confirmation before the BLE-disrupting action is sent
  - High-risk actions stay scoped to the active device when appropriate.
  - the system deck shows the current BLE role identity before sending a desktop-style override
  - Role/boot actions drop the BLE link in a controlled way.

## 10. Records Workflow

- Open the `Records` page.
- For one device:
  - refresh record list
  - export a single record index row
  - export the full index CSV
  - delete last
  - delete all
- For multi-device sessions:
  - use the `Record Hub` scope buttons to switch between `Active`, `Selected`, and `All`
  - use `Refresh Scope` and `Save Scope CSVs`
  - if using `Selected`, build or trim that subset from the in-page selected-device card
  - use the fleet record monitor cards to refresh/export per device
  - switch focused device from the two-column device tiles and confirm destructive actions remain active-device scoped
- Expected:
  - Record lists load over BLE.
  - Exports write into the app files area.
  - Entering `Records` from `Devices` or `Live` pre-refreshes the current connected scope when grouped targets are armed, instead of only refreshing the active device.
  - Scope refresh fans out to the intended device set.
- Scope CSV export only runs for in-scope devices that already have a loaded record index.
- For `Active` and `Selected` scope, those same cached record indexes can still be exported after disconnect while refresh waits for relink.
  - If a selected or active record device disconnects after its index is loaded, the `Live -> Online -> Records` wall and the `Records -> Fleet` page still keep that device visible with relink or cached-export messaging instead of dropping it from the scoped list.
  - The `Record Hub` keeps its scope selector and focused-device switcher in larger button/tile grids instead of horizontal chip strips.
  - If the active record device or part of the selected record group is reconnecting, the same hub explains whether refresh and export are waiting on relink or only targeting the currently linked members.
  - The fleet record monitor follows the armed scope instead of always showing every connected device.
  - Fleet record monitoring works without repeatedly leaving the page.
  - `Export Path` on the vault and fleet cards surfaces the exact Android folder used for that device's BLE record CSVs.
  - `Delete Last` and `Delete All` require explicit confirmation before the command is sent.
  - Delete actions stay disabled until at least one record is present in the loaded index.
  - Delete actions do not accidentally apply to every device.

## 11. Reconnect Recovery

- While previewing or recording, power-cycle a device or move it out of range briefly.
- Restore it.
- Expected:
  - The session enters reconnect/error state visibly.
  - The app retries connection.
  - Monitoring is restored when possible.
  - If the focused device is lost while others remain connected, another session can still be focused and controlled.

## 12. Sync Logging

- Trigger:
  - initial sync
  - packed-time sync updates
  - `Sync Log` actions from fleet/status UI
- Expected:
  - A per-device `sync_results.csv` path is surfaced.
  - Rows accumulate for the Android-supported sync record types:
    - `sync_metric_0x82`
    - `sync_meas_0x8C`
    - `sync_meas_0x8D`
    - `sync_live_0x8F`

## 13. Status Monitoring Wall

- Switch the `Live` page to `Status`.
- Verify `Health`, `Link`, and `Activity` page chips all switch correctly.
- With multiple selected or connected devices:
  - keep one device as the active detailed monitor
  - confirm the other scoped devices appear below in the status wall
  - use `Inspect` to promote another device without leaving the page
  - run `Resync` from one wall card and from the active status monitor
  - open `Sync Log` from one of the wall cards
  - if waveform capture is enabled, open `Wave Path` from both the active monitor and one wall card
- Expected:
  - The status page is not limited to a single active device when multiple sessions are in scope.
  - Each wall card shows current state, sync summary, BLE link summary, and latest activity for that device.
  - The `Link` page shows BLE RSSI, loss, receive rate, and packet-gap / sequence-error telemetry for both the active device and the scoped wall below it when the transport is active.
  - The activity feed also shows inbound sync-handshake milestones such as `0x8B`, `0x8C`, and `0x8D`, so phone-side monitoring can confirm that RTC-set requests and sync probes are flowing without opening the raw sync CSV first.
  - `Resync` on the active monitor or a wall card only targets that device and does not force the whole scoped set to restart.
  - The active status monitor and wall cards both surface the current waveform file when waveform capture is running or was recently armed.
  - `Inspect` changes the active detailed monitor without leaving the status workflow.

## Pass Criteria

The Android port should be treated as functionally verified only when:

- single-device BLE control works end-to-end
- multi-device connect works end-to-end
- live preview and recording work from the same live workflow
- fleet monitoring works per device
- major control-page actions behave correctly
- record refresh/export/delete actions behave correctly
- reconnect recovery is acceptable
- sync logs are produced and reachable from the UI

If any scenario fails, capture:

- page and scope used
- connected device count
- active/focused device
- last visible banner
- relevant recent events from the session card
- whether the failure was single-device or multi-device only
