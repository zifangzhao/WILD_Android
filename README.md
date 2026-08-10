# WILD Android

Phone-first Android client for the CE32 / CE64 / WILD BLE devices, based on the desktop reference in `C:\code\Project\Datalogger_allInOne\CE32_console\CE32_console\CE32_console`.

## What is in this repo

- A new Kotlin + Jetpack Compose Android app scaffold.
- CE32 BLE transport wiring for:
  - scanning and multi-device connection
  - sync / resync handshakes
  - preview start / stop
  - recording start / stop
  - stimulation / closed-loop status monitoring
  - snapshot and camera preview requests
  - camera preview row and snapshot row rendering
  - system and camera parameter reads
  - camera register writes
  - GPIO / LED control
  - trigger waveform toggle
  - sleep / reset / bootloader / firmware update requests
  - BLE record list refresh and delete actions
- A phone-oriented UI with four working areas:
  - `Devices`
  - `Live`
  - `Control`
  - `Records`
- The `Devices` page now includes bulk `Connect All` / `Disconnect All` actions to make multi-device BLE sessions practical from a phone.
- The `Devices` page also supports marking candidate cards and then queueing that exact subset before bulk connect, so multi-device phone sessions do not have to mean `connect everything`.
- Candidate `Mark` state on the `Devices` page is now separate from the connected-device `Selected` group used by Live, Control, and Records, so pre-connect queue staging no longer collides with post-connect scoped operations.
- If you tap `Connect Now` on a marked candidate, that device now also carries forward into the selected-group state and arms `Selected` scope, so the phone does not drop the staged grouping intent just because you chose a direct one-device connect instead of the queue.
- When that subset is staged through `Queue Marked`, the queued connect order now follows the exact phone marking order instead of snapping back to the roster sort.
- When `Connect Queue` starts, that same queued subset is now carried forward into the connected `Selected` group in the same order, so grouped Live / Control / Records work can start from the exact staged fleet instead of rebuilding the subset again after link-up.
- The queue hub now also shows the current marked-candidate order as removable two-column tiles before queueing, so the phone can inspect and trim the staged subset without relying on a sideways chip strip.
- That same queue hub now also shows the current `Selected Group` order as removable two-column tiles, so grouped monitoring or control scope can be inspected and trimmed from the device-management page even while browsing verified or nearby candidates or waiting on a reconnecting member.
- The staged queue now preserves the exact device order chosen on the phone, instead of silently falling back to the roster sort order before connect starts.
- The queue page now shows that staged order as removable two-column tiles, so the queued subset can be trimmed directly from the phone bring-up hub without jumping back into the scan lists.
- Bulk `Connect All` and `Connect Queued` now run as staged fleet-connect sequences instead of firing concurrent `connectGatt` bursts, which is safer for preview-active multi-device sessions on Android.
- `Connect Verified` now also seeds `Selected` scope with that same verified batch before link-up, so grouped Live / Control / Records work can start from the devices the phone just connected instead of forcing a second selection pass.
- Any candidate `Mark` state for a device is now cleared as soon as that device enters a direct or staged connect flow, so queue marks do not quietly reappear after a later disconnect.
- Manual BLE connect now also pauses scanning until the staged or single-device connect sequence drains, matching the desktop host more closely and reducing scanner-vs-connect contention on Android.
- The `Devices` page now starts with a compact pocket-style fleet hub that combines scan, queue, scope, and `Live` / `Control` / `Records` launch actions instead of splitting setup across separate dashboard cards.
- That pocket fleet hub is now also split into dedicated `Link`, `Queue`, and `Launch` pages, so scan/connect, staged multi-device queue control, and page launch/scope selection no longer compete inside one tall phone card.
- That same pocket fleet hub now also moves its top `Link` / `Queue` / `Launch` switcher, the `Connected` / `Linking` / `Verified` / `Nearby` lane selector, the launch scope selector, and the queue-management shortcuts into larger button grids instead of horizontal chip rows, so the phone entry workflow no longer depends on sideways swipes for core fleet actions.
- The phone shell and `Devices` page now surface live linking state, so staged fleet-connect progress is visible during multi-device bring-up instead of looking idle while Android is still opening BLE links.
- The top shell now also keeps active-device telemetry such as state, preview source, battery, and record time in view across pages, so the app behaves more like a handheld instrument readout instead of a plain destination header.
- Shared telemetry widgets now use a denser instrument-style readout treatment, with uppercase labels, framed pills, and monospace values so the phone UI reads more like a handheld meter than a default dashboard.
- That top shell is now also collapsible, keeping a compact active-session summary visible by default while letting the user expand the full telemetry rows only when needed on a phone.
- A compact global target strip now sits under the shell on the non-device pages, so Live, Control, and Records can switch scope, focus device, and bulk-select the connected fleet without repeating the same selector cards inside every workflow.
- The global target strip is now also collapsible, so scope and focused-device context stay visible without permanently consuming vertical space before the working surface.
- That same global target strip now also exposes the current selected-device group as removable device tiles and adds a one-tap `Use Selected` shortcut, so multi-device scopes can be inspected, switched, and trimmed in place instead of bouncing back to a separate builder card.
- The expanded global target strip now also promotes the common scope pivots and subset pivots into larger phone buttons and quick tiles (`Focused`, `Live`, `Recording`, `All Linked`), so grouped BLE sessions can be retargeted without depending on another long horizontal chip row.
- That same global target strip now also stays visible when the connected list drops to zero but the active device or selected group is still preserved in reconnect state, so Live / Control / Records can keep focus context and clear the subset without backing out to the `Devices` page first.
- Selected-device state now also preserves the exact phone tap order end-to-end, so the selected scope, removable selected-device tiles, and any scoped multi-device fanout stay aligned with the subset order the user actually built instead of snapping back to roster sort order.
- The phone now also preserves selected-device membership across disconnects, so `Selected` monitoring pages can keep a reconnecting subset visible and clearable in place while preview, record, and control fanout still target only the currently connected members of that group.
- That reconnect-preserved `Selected` scope now also stays launchable from the phone hubs, and the Live / Control / Records launch cards explain when the selected group is still present but every member is reconnecting so grouped writes or record refreshes remain disabled until a device relinks.
- The live hubs now also keep their cross-page routes and focused-device tiles aligned with that preserved scope, so `Signal`, `Online`, `Camera`, `Status`, `Fleet`, and `Records` remain navigable during reconnect instead of dropping back to connected-only navigation.
- The `Camera`, `Status`, and `Fleet` hubs now also keep their `Online` route enabled whenever preserved scope exists, so reconnect recovery can reopen the online helper workflow without first rebuilding a connected-only active roster.
- Multi-device pages now surface the shared `Selected Devices` builder as soon as more than one device is connected, instead of waiting until the operator has already entered `Selected` scope or built a subset elsewhere first.
- That shared selection card now also includes one-tap `Active`, `Live`, `Rec`, and `Ready` quick picks, so the phone can rebuild a working subset from current session state without a long chip-by-chip tap pass.
- That shared selection card now also presents those subset picks as larger phone tiles and replaces the old all-chip picker with per-device `Add` / `Remove` rows, so grouped Live / Online / Control / Records work stays practical even when the connected list is longer than one screen width.
- That same shared selection card now also renders the current scoped subset as removable two-column device tiles instead of a horizontal chip row, so subset cleanup stays usable after several devices are already selected.
- The `Devices` page now keeps `Connecting` / `Reconnecting` sessions in a dedicated linking section instead of mixing them back into candidate lists, which makes staged fleet bring-up much clearer on a phone.
- That linking section now also lets the user cancel an in-flight connect or stop an automatic reconnect retry directly from the phone UI instead of waiting for the attempt to finish.
- While a staged bulk connect is still dispatching future devices, the `Devices` page now exposes `Stop Queue` so the remaining fleet-connect queue can be halted without waiting for every queued device to start.
- The `Devices` page now mirrors the Windows scanner's BLE advertisement battery parsing, so manufacturer-telemetry devices can stay visible and show pre-connect voltage before a full GATT session is opened.
- If the active device disconnects while other devices remain connected, the app now promotes another connected session so the phone UI stays actionable.
- Device cards on the `Devices` page now stay compact by default, keep the first-tap actions visible, and move deeper BLE identity / transport detail behind an explicit details reveal so the phone list stays denser.
- Those same device cards now also wrap their discovery, queue, role, and transport badges instead of hiding them in horizontal scroll strips, so phone-side scan triage stays readable when several identity markers are present.
- Connected device cards on the `Devices` page now include direct `Live`, `Control`, and `Records` routes that also force the target scope back to `Active`, so a phone user can jump straight into one device without accidentally leaving the app armed for a larger group action.
- The `Live` page keeps preview and recording together and also exposes remote camera monitoring widgets.
- The `Live` page now uses a tighter instrument-style command deck so preview/recording actions, live status, preview source, and key telemetry sit together like a handheld tool instead of a desktop form.
- The `Live -> Signal` pane now lands on a compact pocket-style hub and puts the waveform plus preview/record controls ahead of the deeper tuning cards, so phone use starts on the instrument view instead of a long dashboard preamble.
- That `Pocket Signal` hub now also carries direct `Camera`, `Closed-Loop`, `Records`, and `Devices` launch buttons, mirroring the desktop `Online` page's adjacent workflow buttons so a phone user can jump out of the live signal surface or go straight back to the link/queue hub without hunting through the bottom navigation first.
- That same `Pocket Signal` hub now also exposes a direct `Status` route, so health/activity diagnostics are one tap away from the preview surface instead of hiding behind the live-pane chip row.
- The `Pocket Signal` hub now also removes that duplicate page-jump clutter by keeping `Online`, `Camera`, and `Status` in the live-page chip row and compressing only the remaining cross-workflow jumps (`Closed-Loop`, `Records`, `Devices`) into one compact route strip.
- The `Live -> Signal` workflow is now split into dedicated `Monitor`, `Display`, and `Group` pages beneath that instrument surface, so the waveform and preview/record deck stay fixed while monitoring, display tuning, and selected-device building move onto smaller phone pages.
- The shared page selectors that drive `Signal`, `Online`, and the control subpanels now use larger two-column phone buttons instead of a single chip row, so page changes are easier to hit when moving between monitor, display, group, payload, and lifecycle-style lanes.
- The `Pocket Signal`, `Pocket Camera`, `Pocket Status`, and `Pocket Fleet` hubs now also move their main scope, pane, and route switches into button grids instead of sideways-scrolling chip rows, so the primary live workflow pivots stay reachable with one-hand taps on a phone.
- Those same live hubs now also switch their focused-device chooser from horizontal chips to two-column device tiles, so retargeting one session during multi-device monitoring no longer depends on sideways scrolling.
- The shared live target strip now also converts both focused-device switching and selected-subset trimming into two-column device tiles, so Online / Status / Camera / Fleet retargeting no longer drops back to horizontal chips when a multi-device scope is already active.
- The Windows-style online helper surface now lives on its own dedicated `Live -> Online` pane, so payload, records, camera, and quick status actions are no longer buried under the signal tuning stack on a phone.
- The `Live -> Online` workflow now lands on a `Pocket Online` hub before the deeper `Console`, `Payload`, `Camera`, and `Records` lanes, keeping `Resync`, `No RTC`, `Read Sys`, `Read DSP`, `Read All`, scoped `Push Sys`, scoped `Push All`, and adjacent workflow routing together on a phone-sized card.
- That same `Pocket Online` hub now promotes `Console`, `Payload`, `Camera`, and `Records` into larger lane tiles and keeps the surrounding status/maintenance routes in a single button grid, so the Windows online helpers are easier to hit one-handed than the earlier chip-heavy layout.
- That `Pocket Online` hub now also links directly into the `Status` live workflow, with separate `Health` and `Activity` routes so sync diagnostics, recent BLE events, and saved monitoring files stay adjacent to the online helpers instead of hiding behind extra navigation.
- That same `Pocket Online` hub now also jumps directly into `Control -> System -> Lifecycle`, so reset, bootloader, firmware-update, and role-override actions stay one hop away from the online monitoring surface instead of hiding behind the bottom navigation.
- That same `Pocket Online` hub now also mirrors the other live hubs more closely by exposing its own in-page scope button grid plus direct connected-device focus tiles, so multi-device online monitoring can retarget one session without backing out to `Devices` or relying only on the global strip.
- The `Pocket Online` hub now also folds `Signal`, `Health`, `Link`, `Activity`, `Lifecycle`, `Devices`, and `Closed-Loop` into one thumb-friendly route grid, so the phone helper surface no longer scatters adjacent workflow jumps across separate narrow rows.
- That same `Pocket Online` hub now also shows separate `System` and `Full` payload readiness pills, so the new `Push Sys` versus `Push All` choices are visible before the operator sends a scoped payload write from the phone.
- That same `Pocket Online` hub now also shows preserved-versus-linked scope counts and reconnect-specific helper notes when the active online device or selected online group is only partially linked, so phone users can tell whether disabled reads or pushes are caused by relinking members instead of a lost scope.
- That same `Pocket Online` hub now also keeps `Console`, `Payload`, `Camera`, and `Records` reachable whenever the phone still has preserved scope or cached lane data, so reconnecting sessions do not make those online pages look like they vanished.
- That same `Pocket Online` hub now also keeps a focused-device `Connect` / `Cancel Link` / `Stop Retry` / `Disconnect` action on the card itself, so the operator can recover or drop the active BLE session without jumping back to `Devices` just to touch the roster controls.
- That same `Pocket Online` hub now also keeps its focus-tile grid aligned with preserved scope during reconnect, so retargeting the online helper lanes does not fall back to the connected-only roster when a selected device is relinking.
- The shared `Online` and `Camera` route helpers now also promote the first preserved scoped device into focus when no active session is set, so those pages reopen into a usable phone workflow instead of landing on an empty state during reconnect recovery.
- The live workflow now also auto-restores focus to the first preserved scoped device when the active session is cleared, so `Signal`, `Online`, `Camera`, `Status`, and `Fleet` pages stay populated in place instead of dropping to an empty live screen during reconnect recovery.
- The `Live -> Online` pane no longer repeats a second lane-selector card underneath the hub, so the phone page keeps one compact `Pocket Online` control surface instead of stacking duplicate navigation.
- Opening the live-workflow `Payload` lane now also requests a fresh system-parameter read on entry, mirroring the Windows online `0x90` helper so the phone payload tools do not open on stale system values before the user taps `Read System`.
- That same active `Payload Tools` card now also carries `Read DSP`, `Push All`, and a separate `Full` readiness pill beside the existing system payload actions, so the main online payload workflow itself keeps the fuller CE32 maintenance set instead of deferring it back to the hub or deeper control pages.
- That same active `Payload Tools` card now also shows preserved-versus-linked scope counts and reconnect-specific helper notes when the active online device or selected online group is only partially linked, so scoped reads, waveform toggles, and payload pushes do not look broken when part of the group is still relinking.
- The scoped `Payload` wall for the non-focused devices now also exposes per-device `Read DSP` and `Push All` alongside `Read Sys`, `Read All`, and `Push Sys`, so multi-device online payload work does not lose the fuller CE32 maintenance set once the operator leaves the top hub.
- Those same online payload-wall cards now also preserve reconnecting `Active` / `Selected` members instead of hiding them when BLE drops, and cached system or DSP values remain visible with relink-aware copy while reads and pushes stay disabled.
- The live-workflow `Closed-Loop` route now mirrors the Windows `Online` button more closely by pre-reading system plus both DSP payloads before opening the phone control surface, so the closed-loop cards land with fresher data instead of waiting for a manual read first.
- The live-workflow `Records` routes now also mirror the Windows `Live Logs` button more closely by refreshing the active BLE record list as the route opens, so the phone record lane and full records page do not depend on a second manual refresh before showing current SD-log state.
- The live-workflow camera routes now also request a fresh preview frame as they open, so the dedicated camera page and the signal-side camera lane land with live imagery instead of a blank viewer while still staying lighter than forcing a full snapshot on every phone navigation.
- Those same live camera routes now also request a fresh camera-register read on entry, so the phone camera workflow opens with current `Reg0` / `Reg1` values instead of waiting for an immediate manual refresh.
- The `Live -> Camera` pane now also has its own `Pocket Camera` hub, replacing the generic fleet hub with scoped remote-monitoring summary, focused-device switching, and direct routes back into signal, online, diagnostics, records, closed-loop, and device pages.
- The `Live -> Status` pane now also has its own `Pocket Status` hub, replacing the generic fleet hub with direct `Health` / `Activity` switching, scoped diagnostics summary, focused-device switching, and adjacent workflow routes back into signal, online, camera, records, and device pages.
- The `Live -> Fleet` pane now also has its own `Pocket Fleet` hub, moving the fleet `Operate` / `Camera` / `Health` page switch into the top card and adding direct routes back into signal, online, diagnostics, records, and devices so multi-device monitoring stops feeling like a generic leftover page.
- Those `Pocket Camera`, `Pocket Status`, and `Pocket Fleet` hubs now also use the same compact route-chip treatment as `Signal` and `Online`, removing the heavier phone-width button rows so adjacent workflow jumps stay denser on smaller screens.
- The live command deck and per-device fleet cards now mirror the desktop host's preview/record arming rules, so phone-side buttons only enable when the scoped BLE sessions are in valid states and multi-device preview restarts follow the same grouped re-sync rules as Windows.
- That same live command deck now also shows preserved-versus-linked scope counts and reconnect-specific readiness notes when the active live device or selected live group is only partially linked, so phone users can tell whether disabled preview or record actions are caused by reconnecting members instead of a lost subset.
- The `Live` command deck now also carries a phone-side `Arm Start` mode, which ports the intent of the Windows `Wait for sync` workflow into a staged mobile recording start without relying on the desktop file-polling helper.
- That same live command deck now presents `Immediate Start` and `Arm Start` as paired pocket buttons instead of a single chip toggle, so preview/record staging reads more like a handheld instrument mode switch on the phone.
- The `Live -> Signal` page now mirrors the desktop monitor's display tuning with larger phone button grids for preview duration, display gain, and `Remove DC`, plus deeper preview history so the signal page can behave more like an actual handheld instrument instead of a fixed auto-scaled strip chart.
- The live `Command Deck` now also keeps traffic, voltage, storage, and record-readiness metrics behind a `Show Deck Detail` reveal, so the default phone view stays focused on targets, source, and preview/record actions instead of opening as a long telemetry stack.
- The live `Command Deck` now hides the full preview-source editor behind an explicit `Show Source` reveal, so preview and recording actions stay at the front of the phone workflow while source changes remain one tap away when needed.
- That same preview-source editor now uses larger `Ephys` / `Aux`, bank, and exact-channel button grids instead of sideways chip rows, so source changes stay reachable one-handed even when the channel list is wide.
- The live signal monitor now also renders both the multi-device trace legend and the digital-flag readout as stacked phone tiles instead of horizontal scroll rows, so the handheld monitor stays readable once more than one trace or flag is active.
- The `Live -> Online` workflow is now split into `Console`, `Payload`, `Camera`, and `Records` lanes, so the old desktop-style online controls stop competing inside one tall phone card and become easier to reach from the main live-page chips.
- The `Console` lane now carries the desktop-style `Signal Companion` counters, sync summary, link health, recent BLE activity, and both `Resync` plus `No RTC`, keeping those online readouts and recovery actions beside the signal deck without leaving them on the main monitor stack.
- That same `Signal Companion` card now also keeps the focused-device link action on the same page, so the live console can connect, cancel, retry-stop, or disconnect without throwing the operator back to the device roster.
- When the scope contains more than one device, that same `Console` lane now grows a compact scoped console wall for the remaining selected or connected devices, and those per-device cards also expose `No RTC` beside `Resync` so one drifting session can be recovered without leaving the signal workflow.
- Those same scoped console-wall cards now also keep direct `Connect` / `Cancel Link` / `Stop Retry` / `Disconnect` actions, so one dropped session in a multi-device online view can be recovered or removed without switching back to the roster first.
- Those same console monitoring walls now also keep the selected or focused session visible when it drops out of the connected set, so the phone can still reconnect it in place instead of hiding it as soon as the BLE link falls.
- The `Payload` lane keeps the scoped `Read System`, `Push Sys`, `Read All`, stream-rate updates, custom FS entry, camera and ADC stream toggles, and trigger-wave capture helpers on their own compact phone page.
- That same live `Payload` lane now also surfaces the parsed CE32 `Vbatt_threshold`, `Audio_ratio`, and `Camera_ratio` values and can send scoped `Apply Sys Fields` updates from the same online helper page, so the phone does not need a second jump into `Control -> Acquisition` just to inspect or push those payload fields.
- That same online `Payload` editor now also uses larger FS preset buttons plus dedicated Camera/ADC toggle buttons instead of horizontal chips, so the Windows-style online stream-rate workflow is easier to drive one-handed on a phone.
- That same active `Payload` editor now also keeps `Wave Path` plus the current waveform-file path visible inline, so trigger-wave capture does not depend on a separate console or status page just to find where Android is saving the file.
- When the scope contains more than one device, that same `Payload` lane now also grows a scoped payload wall for the remaining devices, keeping per-device payload readiness, `Read System`, `Read All`, and `Push Sys` actions on the same phone lane instead of forcing repeated active-device context switches.
- Those same scoped payload-wall cards now also expose a small per-device raw-field editor for `VBatt`, `Audio Ratio`, and `Cam Ratio`, so one device in a multi-device online session can be adjusted and pushed without retargeting the whole current scope first.
- Those same scoped payload-wall cards now also keep per-device FS entry, quick-FS, and Camera/ADC stream toggles behind `Edit Stream`, so one device's online stream settings can be changed in place without collapsing the current multi-device scope back to active-only.
- Those same scoped payload-wall cards now also keep per-device waveform arm/disarm and `Wave Path`, so trigger-wave capture can stay on the same multi-device online lane instead of bouncing back to the active card or a different status page.
- Those same scoped payload-wall cards now also keep the current waveform-file path visible inline, so saved capture destinations stay visible per device instead of dropping back to transient banner-only feedback.
- The `Camera` and `Records` lanes keep quick remote-monitoring actions and the active device's BLE record index one tap away without backing out of the preview workflow.
- That same `Records` lane now also keeps a direct `Save CSV` action on the active card, so the common desktop `Live Logs` export path is available without forcing an immediate jump into the full records vault on a phone.
- That same online `Records` lane now also keeps `Save CSV` available from the cached index after a disconnect, so a phone user can still export the loaded BLE record list while the link is reconnecting.
- Those same live-workflow `Records` routes now also land on the `Index` pane instead of the generic records vault, so a phone user who just refreshed BLE log metadata from the online helper lands on the raw on-device record list the way the Windows `Live Logs` path does.
- That same `Camera` lane now also keeps `Read Camera` and `Apply Camera` register controls on the same phone page as `Start Live`, `Single Frame`, and `Snapshot`, matching the Windows snapshot helper more closely instead of splitting preview and camera tuning across separate Android pages.
- When the scope contains more than one device, the signal-side `Online -> Camera` lane now also grows a scoped remote wall for the remaining devices, so multi-device camera monitoring can stay beside the signal workflow instead of forcing a jump to the dedicated camera page.
- Those same `Online -> Camera` wall cards now also keep per-device `Read Camera` and `Apply Camera` register controls behind a compact `Edit Camera Regs` reveal, so multi-device remote monitoring does not fall back to active-device-only camera tuning.
- Those same camera lanes now also preserve reconnecting `Active` / `Selected` members instead of hiding them as soon as BLE drops, and cached preview or snapshot frames remain visible with relink-aware copy while live camera actions stay disabled.
- The signal-side `Online -> Records` lane now does the same for record monitoring, adding a compact scoped record wall for the remaining devices so refresh and CSV export actions stay in the same workflow.
- Those same scoped online record-wall cards now also keep the current export folder visible inline, so per-device record CSV destinations stay visible without relying only on the banner after `Export Path`.
- Those same scoped online record-wall cards now also preserve reconnecting `Active` / `Selected` members instead of hiding them when BLE drops, and the card state now makes it clear whether the device is still linked, relinking, or only available through a cached record index.
- The slimmer `Live -> Signal -> Monitor` page now focuses on live flags and the active device's Windows-style online counters (`Cmd`, `Prev`, `Rec`) plus live voltage, used storage, and record runtime, so the phone monitor stays closer to a handheld instrument instead of a long desktop-form control stack.
- That same companion card now also mirrors the desktop BLE receive extras by surfacing `Notif` and legacy `Busy` counts from the real notification path, so transport-state noise on older firmware stays visible without leaving the signal monitor.
- The phone signal picker now also groups ephys preview sources into direct channel banks, so jumping to a specific display channel is faster than stepping one channel at a time while still staying compact enough for a phone screen.
- The fleet `Operate` cards now use that same banked preview-source workflow, so per-device source changes in a multi-device monitoring session do not fall back to `next source` stepping.
- That same fleet preview editor now also uses larger `Ephys` / `Aux`, bank, and exact-channel button grids instead of horizontal chip rows, so per-device source changes stay practical on a handset while several fleet cards are open.
- Fleet cards now also wrap their role/function/verified badges instead of putting them in a horizontal scroll row, so per-device monitoring status stays visible without sideways swipes.
- When the active preview source is `DSP output A` or `DSP output B`, the live signal view now preserves the desktop threshold-monitoring cue by painting the corresponding trigger threshold line on the Android trace.
- The `Signal` pane now keeps compact sync quality, BLE link health, and recent session activity beside the signal plot so remote monitoring does not require leaving the main preview workflow.
- Multi-device signal overlays now expose focusable per-trace legend tiles, so a phone user can tell traces apart and promote one device to the active session directly from the overlay.
- Multi-device live monitoring can now switch between `Overlay` and `Stacked` signal layouts, with stacked mini-scopes per device for more readable phone-side monitoring.
- The `Live` page now also surfaces per-device TX/RX counts and a recent BLE activity timeline so multi-device remote monitoring is not limited to a single status string.
- Fleet cards can now change each device's preview source directly with per-device bank toggle and next-source actions, so multi-device monitoring does not require bouncing back to the global command deck.
- The `Live` page now also mirrors the desktop BLE link-test monitor by decoding inbound `0xEE` packets into link RSSI, packet loss, sequence-error, and receive-rate diagnostics.
- The camera monitor on the `Live` page now supports a desktop-style continuous low-resolution preview loop instead of only one-shot frame requests.
- Fleet camera controls can now request full per-device snapshots as well as low-resolution preview frames, so multi-device remote monitoring no longer has to route every capture through the active-device camera pane.
- The `Live -> Camera` pane now keeps the active device in a large viewer and also shows a scoped camera wall for the other selected or connected devices, so multi-device remote monitoring can stay on one page.
- Those same `Live -> Camera` scoped wall cards now also keep per-device `Reg0` / `Reg1` read and apply controls behind the same compact `Edit Camera Regs` reveal, so one remote device can be camera-tuned in place without retargeting the whole page.
- The `Live -> Status` pane now keeps the active device in the detailed monitor and also shows a scoped status wall for the other selected or connected devices, so multi-device sync and BLE link health can stay on one phone page.
- The `Live -> Status` and `Live -> Fleet` panes now also expose per-device trigger-waveform capture paths, so saved waveform files stay reachable during multi-device remote monitoring instead of being hidden behind the active-session control page.
- The `Live -> Status` workflow is now split into dedicated `Health` and `Activity` pages, so sync diagnostics, BLE events, and saved monitoring files no longer compete for the same phone-sized card stack.
- The `Live -> Status` workflow now also includes a dedicated `Link` page, so BLE RSSI, packet loss, receive rate, and packet-gap telemetry collected from the Windows-style `0xEE` link-test packets no longer stay buried inside the broader health cards.
- The `Pocket Online` hub now links directly into that `Link` page alongside `Health` and `Activity`, so scoped online helpers can jump straight into transport-quality monitoring without an extra tap through the status page first.
- The dedicated `Health` and `Link` diagnostics pages now also expose `No RTC` alongside `Resync`, and the scoped status/link walls inherit that same per-device recovery path so sync troubleshooting can stay on the active diagnostics page.
- The dedicated `Link` diagnostics page and scoped link wall now also keep direct `Connect` / `Cancel Link` / `Stop Retry` / `Disconnect` actions beside the transport telemetry, so active-session recovery and per-device disconnects stay inside the link workflow during multi-device troubleshooting.
- The active `Health Monitor` card and the scoped health wall now also keep direct `Connect` / `Cancel Link` / `Stop Retry` / `Disconnect` actions beside the sync readout, so link recovery is available from the broader diagnostics page instead of only from the dedicated link surface.
- Those same status monitoring walls now also keep the selected or focused session visible when it is no longer connected, so the new recovery controls remain usable during multi-device dropouts instead of losing the device card immediately.
- The `Devices` queue hub plus the `Pocket Status` and `Pocket Fleet` hubs now also keep that selected-device subset visible even while a member is reconnecting, so the phone no longer silently drops the chosen monitoring group just because one BLE link falls out of the connected set.
- The `Pocket Status` and `Pocket Fleet` hubs now also count the broader monitoring scope instead of only connected sessions, so their phone summaries stay aligned with the deeper diagnostics and fleet-health pages while a selected or focused device is reconnecting.
- The `Activity` diagnostics page now also keeps direct `Connect` / `Cancel Link` / `Stop Retry` / `Disconnect` actions beside the recent BLE event list, so recovery can start on the same phone page where the failure was observed.
- The `Activity` diagnostics page and the `Fleet -> Health` monitor cards now also expose that same `No RTC` path, so recent-event review and multi-device health monitoring do not require backing out to another live surface before retrying sync without an RTC rewrite.
- The `Fleet -> Health` monitor now also keeps the selected or focused session visible when it drops out of the connected set, and those health cards expose the same direct `Connect` / `Cancel Link` / `Stop Retry` / `Disconnect` action, so multi-device recovery can stay on the fleet page instead of bouncing back to the roster.
- The `Fleet -> Health` cards now also keep `Focus` / `Show Detail` on the top row and move `Resync` / `No RTC` onto their own action row, which keeps those multi-device recovery controls more legible on a phone.
- The status activity timeline now also surfaces inbound sync-handshake frames such as `0x8B`, `0x8C`, and `0x8D`, so phone-side remote monitoring can confirm RTC-set requests and sync-probe traffic without opening the raw sync CSV first.
- Those same `Live -> Status` and `Live -> Fleet` surfaces now also expose per-device `Resync`, so one drifting BLE session can be recovered in place without leaving the multi-device monitoring workflow or changing the global scope.
- The `Live -> Fleet` monitor and the records fleet wall now follow the current `Active` / `Selected` / `All` scope instead of always expanding to every connected device, which keeps phone-sized multi-device pages aligned with the armed target set.
- The `Live -> Fleet` monitor is now also split into dedicated `Operate`, `Camera`, and `Health` pages so multi-device cards no longer stack every preview, record, camera, and log control at once on a phone-sized screen.
- Those per-device fleet cards now also keep only the quick monitor and action surface visible by default, with preview-source banks, camera narrative, and log/detail actions revealed explicitly per card so the multi-device phone view stays denser.
- The top live overview cards now count the current scoped target set instead of the full connected pool, so `Selected` and `Active` phone workflows no longer show misleading fleet-wide preview or recording totals.
- The `Control` page is split into phone-sized `Acquisition`, `Closed-Loop`, `I/O`, and `System` panels instead of one long desktop-style action list.
- The `Control Hub` now switches both target scope and the four main control panels with larger pocket-button grids and uses two-column focus-device tiles instead of horizontal chips, so scoped control retargeting stays practical on a handset.
- That same `Control Hub` now also explains when the active control device or part of the selected control group is still reconnecting, so phone users can distinguish a preserved-but-partially-linked scope from a lost group before trying scoped writes.
- The `Acquisition` panel now starts with a compact pocket acquisition deck that keeps sync, payload refresh, snapshot, preview-shot, and quick impedance/camera actions together before the deeper editors.
- The `Acquisition` panel now includes a single `Read All Payloads` action so Android can refresh system + both DSP payloads in one scoped step instead of making phone users trigger separate reads every time.
- The quick acquisition diagnostics now split clearly by intent: snapshot and preview-shot stay active-device-focused, while quick impedance runs plus camera-register reads/writes follow the current `Active` / `Selected` / `All` scope and show target/source context on the phone cards.
- The deeper stream-rate editor now also shows scope, target count, and active-device source context, so phone users can see that the displayed values come from the active device while `Apply Stream Rates` and `Quick FS Only` still fan out to the armed scope.
- That same deeper acquisition editor now also parses the Windows `Vbatt_threshold`, `Audio_ratio`, and `Camera_ratio` fields from the CE32 system payload, surfaces their current values on the phone, and lets the operator apply scoped raw-field updates with an immediate `Push Sys` from the same page.
- That same acquisition editor now also uses larger FS preset buttons plus dedicated Camera/ADC toggle buttons instead of horizontal chips, so the control-side stream-rate workflow stays consistent with the live online payload page on a phone.
- The `Impedance` diagnostics page now also exposes the active device's CSV folder directly, so phone-side impedance exports are discoverable without reading a transient banner first.
- Sync-log, record-export, and impedance-export actions now also persist their resolved Android destinations into each device session, so the relevant phone cards keep showing the current file or folder path after the button tap instead of dropping that information into a transient banner only.
- The `Closed-Loop` quick-controls card now keeps stim enable and force-trigger actions visible while moving the per-channel gain / threshold / intensity editors behind an explicit reveal, which keeps the phone control page denser by default.
- That same quick-controls card now also shows scope, target count, and active-device source context, and its fast stim / trigger / per-channel quick writes fan out to the armed scope.
- The advanced closed-loop editors and profile staging card now also show scope and active-device source context, and their stim-timing, DSP-live, and profile-stage actions fan out across the current target scope so multi-device tuning no longer has to be repeated one device at a time before a scoped push.
- The `Closed-Loop Profile` editor now also switches both profile mode and stim-routing paths with larger two-column button grids instead of chip rows, so the deeper routing page stays usable on a phone.
- The `Control` page now includes a dedicated impedance diagnostics card that keeps BLE `0x51` magnitude, `0x52` crosstalk columns, and `0x53` phase results together and exports the latest bundle to CSV files from the phone.
- The `Acquisition` panel now also includes a phone-sized `Auto Impedance` scheduler that repeatedly runs BLE impedance sweeps against one target device and auto-saves each CSV bundle without leaving the control workflow.
- The `System` panel now splits into dedicated `Push`, `Lifecycle`, and `Dump` lanes, so scoped payload sends no longer compete on the same phone page with sleep/reset/boot/update/role commands that belong to the active device only.
- The `I/O` panel now also switches GPIO modes with larger two-column button grids instead of chip rows, so LED/GPIO writes match the rest of the phone-first control language.
- The scoped system uploads now follow the current `Active` / `Selected` / `All` target set, and the system deck shows scope plus ready-target counts so phone users can see which in-scope devices already have payloads loaded before pushing params.
- The compact `Push` lane now keeps `Push Sys` and `Push All` visible by default and hides the less-frequent DSP-only sends behind a smaller reveal, which keeps the most common phone-side parameter actions closer to the top of the card.
- When `Auto Impedance` has to reconnect a dropped device, Android now mirrors the Windows `AutoZtest` helper more closely by using the fast-bootstrap `0x80` connect path before falling back to the normal synced-state wait.
- After a successful scheduled impedance capture, Android now also mirrors the desktop helper's cleanup path by requesting a software reset and letting the BLE link drop before the next scheduled pass.
- High-risk `System` actions now use confirmation prompts before reset, bootloader entry, or firmware-update request, which is safer on a phone-sized touchscreen.
- The dedicated `Lifecycle` lane now keeps sleep, reset, bootloader, firmware-update, and role-override actions together with the active device identity and current role, which matches the desktop separation more closely and reduces accidental cross-scope phone taps.
- The `Records` page now also confirms `Delete Last` and `Delete All`, and keeps those destructive actions disabled until the current BLE record index has been loaded for the active device.
- The `Records` page now mirrors the same `Active` / `Selected` / `All` scope model as the live and control pages, with one-step scope refresh and multi-device CSV export while destructive delete actions stay limited to the active device.
- Cached record-index CSV export now also works after disconnect for `Active` and `Selected` scope, so a phone user can still save already-loaded BLE record lists while refresh waits for relink.
- That same `Record Hub` now also uses larger scope buttons and two-column focused-device tiles instead of horizontal chip rows, so multi-device record export and retargeting stay practical on a phone when the connected list gets wide.
- That same `Record Hub` now also explains when the active record device or part of the selected record group is still reconnecting, so phone users can tell whether refresh and export are limited by relink state instead of a cleared scope.
- Jumping into `Records` from the device-launch or live-workflow routes now pre-refreshes the current armed scope when grouped targets are active, so selected/all record pages do not open with only the active device refreshed.
- The `Records` vault and fleet cards now expose each device's export folder directly, so phone-side CSV saves do not leave the user guessing where Android wrote the BLE index files.
- The `Records` fleet page now also keeps preserved `Active` / `Selected` record sessions visible while they reconnect, so per-device status, export folder, and cached CSV actions remain inspectable instead of disappearing with the BLE link.
- The `Live`, `Control`, and `Records` pages each include an in-page active-device switcher so multi-device sessions do not have to bounce back through the scanner list.
- Jumping into `Control` from the device-launch or grouped fleet routes now pre-reads system params for the current armed scope, so the acquisition/system cards do not open on stale payload state when the phone already knows which connected devices are being targeted.
- Unexpected BLE drops now move the affected session into `Reconnecting`, retry the link automatically, and request preview monitoring again when that session had an active live stream before the drop.
- Initial Android connect bootstrap now retries the desktop `0x82` handshake and system-parameter reads for a bounded window before escalating into the reconnect path.
- Legacy wake-prefix firmware now also mirrors the Windows host's raw notification handling by accepting a bare `0x80` wake acknowledgement as ready state and backing off when the device reports `<CONFIG MODE BUSY>`, which improves older-device bootstrap stability on Android.
- Windows-known interface and diagnostic packets (`0xB0`, `0xB1`, `0xC0`, `0xF0`..`0xF4`) now stay framed on Android and surface in session activity instead of collapsing into opaque unknown-command traffic.
- The BLE record workflow now mirrors the desktop host more closely by deferring record refresh/delete requests briefly while sync or recorder-stop state is still settling.
- The `Control` page now includes a desktop-matched acquisition card that reads system params and writes the CE32 `0x33` stream sampling-rate command.
- The `Acquisition` panel now also exposes the desktop no-RTC resync path and a quick legacy `0x32` FS-only write for cases where the full system payload has not been read yet.
- The `Live` page now also surfaces closed-loop / stimulation status from inbound `0x10` packets and counts triggered-waveform `0xAE` blocks.
- The `Control` page exposes the desktop camera register pair (`reg0`, `reg1`) through the same `0x15` BLE update command used by the Windows snapshot tool.
- The `Control` page now also exposes quick closed-loop writes for stim enable, manual force-trigger, trigger gain, trigger threshold, and stim intensity.
- The `Control` page also includes advanced live editors for the desktop `0x20` / `0x21` stimulation timing writes and `0x22` / `0x23` DSP live parameter writes.
- The `Control` page also stages desktop closed-loop profile fields such as `cl_mode`, train timing, random trigger bounds, and `stim_ch` routing before pushing the full system payload.
- The `Closed-Loop Profile` page can now also `Stage + Push Sys` or `Stage + Push All` directly from the same phone workflow, matching the desktop closed-loop window more closely instead of forcing the user back into the `System` panel to send staged edits.
- Those direct send actions reuse the current scope-ready counts, so `Stage + Push Sys` and `Stage + Push All` stay disabled until the armed devices have the required payload state instead of firing blind writes from the phone profile page.
- The closed-loop `Monitor` page now keeps live stimulation status, scoped payload refresh, and trigger-waveform capture helpers together, so the phone control surface behaves more like the desktop closed-loop window instead of splitting status and refresh across separate panels.
- The `System` control panel now also supports desktop-style full parameter uploads for `0x01` system payloads and `0x02` / `0x03` DSP payloads after the app has read the current 512-byte blocks.
- The `System` role override card now mirrors the Windows `AUTO / MASTER / SLAVE` wording, shows the current BLE role identity, and confirms the BLE-disrupting override before sending it from the phone.
- Trigger-waveform streaming now mirrors the Windows save-to-file workflow more closely: enabling waveform capture on Android creates a per-device `.dat` file, appends incoming `0xAE` blocks, and exposes the active capture path from the phone UI.

## Windows UI baseline

The desktop reference currently boots into `Form1` by default from:

- `C:\code\Project\Datalogger_allInOne\CE32_console\CE32_console\CE32_console\Program.cs`

`Form1_Alt` is only used for alternate build flags. The Android information architecture in this repo follows the live `Form1` plus satellite-form split:

- `Form1 -> Online`:
  scanner, connect, preview on/off, record start/stop, sync arming, live display tuning, quick camera toggle, read/upload params, and status bits
- `Form1 -> Offline`:
  recording parameters, closed-loop configuration, stimulation parameters, SD-card setup, and file export
- `Form1 -> Advanced`:
  resync, host/follower role switch, sleep, reset, impedance, DFU/update, GPIO/LED, and auto-impedance helpers
- `Form_Download`:
  record delete and download workflow
- `Form_Snapshot`:
  camera preview, snapshot, and camera register read/write
- `Form_ClosedLoop`:
  online stimulation and DSP closed-loop controls and monitoring

Android screen mapping in this repo:

- `Devices`:
  Windows `Online` scanner and connect list, reorganized for staged multi-device BLE sessions
- `Live`:
  Windows `Online` preview/record monitor, dedicated online-helper pane, and `Form_Snapshot` remote camera monitoring, reorganized into phone-first live pages
- `Control`:
  Windows `Offline` + `Advanced` + `Form_ClosedLoop` control surface, split into phone-sized acquisition, closed-loop, I/O, and system panels
- `Records`:
  Windows `File` / `Form_Download` record list, export, and delete flows

## CE32 reference mapping

- BLE service: `FFF0`
- RX notify characteristic: `FFF1`
- TX write characteristic: `FFF2`
- Legacy TX wake characteristic: `FFF3`
- Optional device info service: `180A`

Key command mappings in the current mobile port:

- Preview start: `< 40 >`
- Preview stop: `< 41 >`
- Preview source select: `< 42 selector >`
- Record start: `< 30 >`
- Record stop: `< 31 >`
- Resync start: `< 82 81 00 00 >`
- Sync reset: `< 89 >`
- Read system params: `< 90 >`
- Read DSP params: `< 91 >`, `< 92 >`
- Snapshot request: `< 9E 00 >`
- Camera preview snapshot: `< 9E 01 >`
- Sleep: `< A0 0A >`
- Role switch: `< A1 mode >`
- Software reset: `< AB BA >`
- System bootloader: `< AC CA >`
- Firmware image update: `< AE >`
- Record block: `< 94 lo hi >`
- Delete record(s): `< 95 count >`

## Build and run

The repo now contains a complete Android Gradle project, including the wrapper JAR.

Android floor:

- `minSdk = 21` in `app/build.gradle.kts`
- That means the APK can install on Android 5.0+ hardware, but BLE test work is still better on a modern phone with stable ADB and Bluetooth stacks.

Recommended phone for bring-up:

- A Google Pixel 5 is a good choice.
- It uses standard ADB over USB or wireless debugging on Windows, which avoids the Huawei HDC/HDB driver path.
- Huawei phones are still usable only if they also show up in `adb devices`; HDC alone is not enough for Gradle/ADB deploy.

Recommended first phone setup on Windows:

1. On the phone, enable `Developer options`.
2. Turn on `USB debugging`.
3. If needed, also turn on `Wireless debugging`.
4. Connect once by USB and accept the RSA prompt, or pair wirelessly with:

```powershell
adb pair <phone-ip>:<pair-port>
adb connect <phone-ip>:<debug-port>
```

5. Confirm the phone is visible with:

```powershell
adb devices
```

If the device shows as `unauthorized`, unlock the phone and accept the USB debugging RSA prompt before trying to install the APK.

Recommended first run:

1. Open the repo in Android Studio.
2. Let Android Studio sync the Gradle project and install any missing SDK components.
3. Run on a BLE-capable Android phone with Bluetooth enabled and the required BLE permissions granted.

Command-line debug build:

```powershell
.\gradlew.bat :app:assembleDebug
```

Debug APK output:

- `app/build/outputs/apk/debug/app-debug.apk`

ADB install helper:

```powershell
.\scripts\install-debug.ps1 -Launch
```

If more than one ADB device is connected:

```powershell
.\scripts\install-debug.ps1 -Serial <device-serial> -Launch
```

List currently visible ADB devices:

```powershell
.\scripts\install-debug.ps1 -ListDevices
```

Manual verification checklist:

- [`MANUAL_VERIFICATION.md`](./MANUAL_VERIFICATION.md)
