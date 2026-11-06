package com.wild.android.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wild.android.ble.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NeuropixelsControlScreen(
    session: DeviceSessionUiState, sessions: List<DeviceSessionUiState>,
    onBack: () -> Unit, onSelectDevice: (String) -> Unit, onLink: () -> Unit,
    onAction: (NpCommand) -> Unit,
    initialPreview: Boolean = false,
) {
    val np = session.neuropixels; val status = np.status
    val idle = session.isConnected && !np.busy
    fun send(command: NpCommand) = onAction(command.copy(deviceId=session.id))
    var page by rememberSaveable { mutableStateOf(if(initialPreview) "Preview" else "Recording") }
    var pending by remember(session.id) { mutableStateOf<Pair<String, NpCommand>?>(null) }
    var devicesExpanded by remember { mutableStateOf(false) }
    val scroll = rememberLazyListState()
    val previewState=rememberSaveableStateHolder()
    val previewCalibrations=remember { mutableStateMapOf<Triple<String,Int,Int>,NpDisplayCalibration>() }
    LaunchedEffect(page) { scroll.scrollToItem(0) }
    fun act(action: NpAction) = send(NpCommand(action))
    val currentSessions by rememberUpdatedState(sessions)
    val currentAction by rememberUpdatedState(onAction)
    DisposableEffect(Unit) {
        // Include preparation in flight: Stop queues behind its validation and
        // retires the lease even if the user leaves before the first sample.
        onDispose { currentSessions.filter { it.isNeuropixels && it.isConnected }.forEach { currentAction(NpCommand(NpAction.StopPreview,deviceId=it.id)) } }
    }
    pending?.let { (message, command) ->
        AlertDialog(onDismissRequest = { pending = null }, title = { Text("${session.name} · confirm") }, text = { Text(message) },
            confirmButton = { Button(onClick = { pending = null; send(command) }, enabled = idle) { Text("Confirm") } },
            dismissButton = { OutlinedButton(onClick = { pending = null }) { Text("Cancel") } })
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("Devices") }
            Box(Modifier.weight(1f)) {
                FilledTonalButton(onClick = { devicesExpanded = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("${session.name} ▾") }
                DropdownMenu(expanded = devicesExpanded, onDismissRequest = { devicesExpanded = false }) {
                    sessions.forEach { candidate -> DropdownMenuItem(text = { Text(candidate.name) }, onClick = { devicesExpanded = false; onSelectDevice(candidate.id) }) }
                }
            }
            OutlinedButton(onClick = onLink, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (session.isConnected || session.isLinkingLike) "Disconnect" else "Connect") }
        }
        Text(if (session.isConnected) "NP${status?.version ?: "?"} · ${status?.stateLabel ?: "Reading capabilities…"}" else "Disconnected · last report retained", style = MaterialTheme.typography.titleMedium)
        if (np.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (np.failure.isNotBlank()) Text(np.failure, color = MaterialTheme.colorScheme.error)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Recording", "Probe maps", "Closed-loop", "Preview", "Signals & time", "Storage", "Device modes").forEach { name ->
                FilterChip(selected = page == name, onClick = { page = name }, label = { Text(name) }, modifier = Modifier.heightIn(min = 48.dp))
            }
        }
        LazyColumn(state = scroll, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
            if(status!=null && page=="Probe maps") item { NpCard("Probe IMRO / site maps") { NpProbeControls(session,::send) { text,command -> pending=text to command } } }
            if(status!=null && page=="Closed-loop") item { NpCard("Closed-loop event detection") { NpClosedLoopControls(session,::send) { text,command -> pending=text to command } } }
            if (page == "Preview") item { previewState.SaveableStateProvider("preview") { NpPreviewPanel(sessions,previewCalibrations,onAction) } }
            item { NpCard("${session.name} · ${if (status?.isMedia == true) "Camera / microphone" else "Neuropixels"}") {
                if (np.reportedAtMs > 0) Text("Last report ${time(np.reportedAtMs)} · ${np.message}")
                if (status != null) {
                    Text(if (status.isMedia) "Camera ${np.mediaProfile?.cameraHz ?: "?"} fps · microphone ${if (np.mediaProfile?.microphone == true) "200 kHz" else if (np.mediaProfile != null) "off" else "not read"}" else "${probes(status.probeMask)} · ${status.sampleRate} samples/s per probe")
                    Text("${np.media?.seconds ?: status.recordedSeconds} s · ${"%.1f".format(status.recordedMiB)} MiB recorded · SD ${"%.2f".format(status.capacitySectors*512.0/1073741824)} GiB")
                    if (status.fault != 0L || status.error != 0L) Text("Device fault ${status.fault} · command error ${status.error}. Download important recordings before maintenance.", color = MaterialTheme.colorScheme.error)
                }
                OutlinedButton(onClick = { act(NpAction.ReadStatus) }, enabled = idle, modifier = Modifier.heightIn(min = 48.dp)) { Text("Refresh status") }
            } }
            if (status != null && page == "Recording") {
                item {
                    // Media status has mask/rate0; use a usable neural draft,
                    // without silently changing the applied device profile.
                    var mask by rememberSaveable(session.id) { mutableIntStateOf(status.probeMask.takeIf { it > 0 } ?: 1) }
                    var rate by rememberSaveable(session.id) { mutableIntStateOf(status.sampleRate.takeIf { it > 0 } ?: 15000) }
                    NpCard("Neural recording profile") {
                        Text(if (status.version == 2) "Choose consecutive connectors. Each probe has 384 channels; available connectors do not prove that a probe is attached." else "AP and LFP are recorded together; LFP rate = AP ÷ 12. Select a supported connector combination.")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val available = if (status.version == 1) 7 else np.availableProbeMask.takeIf { it != 0 } ?: status.probeMask
                            (1..7).filter { it and available.inv() == 0 && NpBleProtocol.supportsProfile(status,it,rate) }.forEach { candidate ->
                                FilterChip(selected = mask == candidate, onClick = { mask = candidate }, enabled = idle && status.canConfigure, label = { Text(probes(candidate)) }, modifier = Modifier.heightIn(min = 48.dp))
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NpBleProtocol.rates.forEach { candidate -> FilterChip(selected = rate == candidate, onClick = { rate = candidate }, enabled = idle && status.canConfigure && NpBleProtocol.supportsProfile(status,mask,candidate), label = { Text("${candidate/1000.0} kHz") }, modifier = Modifier.heightIn(min = 48.dp)) }
                        }
                        Button(onClick = { pending = "Apply ${probes(mask)} at $rate samples/s? This selects neural mode; it does not start capture." to NpCommand(NpAction.ApplyProfile,mask,rate) }, enabled = idle && status.canConfigure && NpBleProtocol.supportsProfile(status,mask,rate), modifier = Modifier.heightIn(min = 48.dp)) { Text("Apply neural profile") }
                    }
                }
                if (status.version == 1 && status.has(0x200000)) item {
                    var camera by rememberSaveable(session.id) { mutableStateOf(true) }; var mic by rememberSaveable(session.id) { mutableStateOf(false) }
                    var fps by rememberSaveable(session.id) { mutableStateOf("40") }; val hz = if (camera) fps.toIntOrNull() else 0
                    NpCard("Camera / microphone mode · NP1") {
                        Text("Records to the device SD card instead of neural capture. It does not stream video/audio to this phone.")
                        Row { Checkbox(checked = camera, onCheckedChange = { camera = it }, enabled = idle); Text("Camera") }
                        OutlinedTextField(value = fps, onValueChange = { fps = it }, enabled = camera && idle, label = { Text("Camera rate · 1–50 frames/s") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                        Row { Checkbox(checked = mic, onCheckedChange = { mic = it }, enabled = idle); Text("Microphone · 200 kHz") }
                        Button(onClick = { pending = "Select camera ${hz} fps, microphone ${if (mic) "on" else "off"}? Neural capture will be disabled. Recording starts separately." to NpCommand(NpAction.ApplyMedia,cameraHz=hz!!,microphone=mic) }, enabled = idle && status.stopped && status.fault == 0L && hz != null && hz in 0..50 && (hz > 0 || mic) && (!camera || hz > 0), modifier = Modifier.heightIn(min = 48.dp)) { Text("Apply media mode") }
                        if (status.isMedia) OutlinedButton(onClick = { act(NpAction.ReadMedia) }, enabled = idle) { Text("Read media profile & counters") }
                    }
                }
                item {
                    var duration by rememberSaveable(session.id) { mutableStateOf("0") }; var allow by rememberSaveable(session.id) { mutableStateOf(false) }
                    var scheduled by rememberSaveable(session.id) { mutableStateOf(false) }; var minutes by rememberSaveable(session.id) { mutableStateOf("2") }
                    val seconds = duration.toIntOrNull(); val delayMinutes = minutes.toIntOrNull()
                    val check = runCatching { require(seconds != null); NpBleProtocol.start(status,2,seconds,allow,np.mediaProfile) }
                    NpCard("Start / stop SD recording") {
                        OutlinedTextField(value = duration, onValueChange = { duration = it }, label = { Text("Duration · seconds (0 = continuous, max 3600)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                        Text("Finite duration reserves storage, not an exact phone timer. Disconnecting Bluetooth does not stop onboard recording.")
                        if (status.isMedia && np.mediaProfile == null) OutlinedButton(onClick = { act(NpAction.ReadMedia) }, enabled = idle) { Text("Read media values before Start") }
                        if (status.version == 1 && (!status.has(2) || status.probeMask == 3 && status.sampleRate == 30000)) Row { Checkbox(checked = allow, onCheckedChange = { allow = it }); Text("Accept unqualified card/profile. Two-probe 30 kHz has shown sustained-recording failures.") }
                        Row { Checkbox(checked = scheduled, onCheckedChange = { scheduled = it }, enabled = status.canSchedule && idle); Text("Schedule start · requires firmware support") }
                        if (scheduled) OutlinedTextField(value = minutes, onValueChange = { minutes = it }, label = { Text("Start in minutes · 1–1440") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                        Button(onClick = {
                            val utc = if (scheduled) System.currentTimeMillis() + delayMinutes!! * 60000L else 0
                            pending = "${if (scheduled) "Arm recording for ${time(utc)}" else "Start recording now"} on ${session.name}, ${if (seconds == 0) "until Stop" else "with a $seconds-second allocation"}? Uses the applied device profile." to NpCommand(if (scheduled) NpAction.Schedule else NpAction.Start,seconds=seconds!!,acceptUnqualified=allow,utcMs=utc)
                        }, enabled = idle && check.isSuccess && (!scheduled || status.canSchedule && delayMinutes != null && delayMinutes in 1..1440), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(if (scheduled) "Arm timed recording" else "Start recording") }
                        if (check.isFailure && status.stopped) Text(check.exceptionOrNull()?.message ?: "Choose a valid profile.", color = MaterialTheme.colorScheme.error)
                        if (scheduled) Text("Sync time under Signals & time first. Confirmation keeps a fixed start time; delivery never silently moves it forward.")
                    }
                }
            }
            if (status != null && page == "Signals & time") {
                item { NpCard("Device clock") {
                    Text(np.clockMessage); np.clock?.let { if (it.valid) Text("Last device UTC: ${Instant.ofEpochMilli(it.utcUs/1000)}") }
                    Text("Read is non-mutating, including during recording. Sync changes the stopped clock once and checks readback. Radio uncertainty does not include the phone's UTC error.")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { act(NpAction.ReadClock) }, enabled = idle && status.has(256), modifier = Modifier.heightIn(min = 48.dp)) { Text("Read clock") }
                        Button(onClick = { act(NpAction.SyncTime) }, enabled = idle && status.stopped && status.has(256), modifier = Modifier.heightIn(min = 48.dp)) { Text("Sync phone time") }
                    }
                } }
                item { NpCard("Motion sensors · snapshot") {
                    Text("One coherent view, not the full-rate IMU recording. Unavailable/stale axes are not displayed as zeros.")
                    np.imu?.let { imu ->
                        fun axes(values: List<Double>) = values.joinToString(" / ") { "%.3f".format(it) }
                        Text("Last report ${time(imu.reportedAtMs)} · sequence ${imu.sequence}")
                        Text("Acceleration (g): ${if (imu.valid and 1L != 0L) axes(imu.acceleration) else "unavailable"}")
                        Text("Angular velocity (°/s): ${if (imu.valid and 1L != 0L) axes(imu.angularVelocity) else "unavailable"}")
                        Text("Magnetic field (µT): ${if (imu.valid and 2L != 0L) axes(imu.magneticField) else "unavailable"}")
                        Text("Errors: IMU ${imu.imuError}, environment ${imu.environmentError}")
                    }
                    OutlinedButton(onClick = { act(NpAction.ReadImu) }, enabled = idle && (status.version == 2 || status.has(0x10000000)), modifier = Modifier.heightIn(min = 48.dp)) { Text("Read motion sensors") }
                } }
                np.media?.let { m -> item { NpCard("Camera / microphone counters") {
                    Text("${m.frames} camera frames · ${m.missed} missed · ${m.samples} audio samples · ${m.seconds} s")
                    Text("Faults ${m.cameraFault}/${m.microphoneFault} · ${m.overruns} overruns")
                    OutlinedButton(onClick = { act(NpAction.ReadMedia) }, enabled = idle && status.isMedia) { Text("Refresh counters") }
                } } }
            }
            if (status != null && page == "Storage") item { NpCard("SD catalog & recovery") {
                Text("Next allocation sector ${status.startSector}. Mount does not format. Recovery preserves data but may retain the entire interrupted allocation.")
                if (status.version == 2 && status.stopped && status.state in listOf(5,7)) Text("This firmware requires Ready for storage maintenance. Apply a neural profile under Recording first; existing recordings are retained.")
                if (status.version == 2 && status.has(65536)) Text("Card full. Recovery does not necessarily release reserved space.")
                if (status.version == 2 && status.has(131072)) Text("Interrupted catalog needs recovery. Download important recordings first.", color = MaterialTheme.colorScheme.error)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { send(NpCommand(NpAction.Mount,expectedCapacity=status.capacitySectors)) }, enabled = idle && status.canMount, modifier = Modifier.heightIn(min = 48.dp)) { Text("Mount catalog") }
                    OutlinedButton(onClick = { pending = "Recover ${session.name}'s interrupted catalog? Existing payload is preserved; the interrupted reservation may remain allocated." to NpCommand(NpAction.Recover,expectedCapacity=status.capacitySectors) }, enabled = idle && status.canRecover, modifier = Modifier.heightIn(min = 48.dp)) { Text("Recover catalog") }
                }
                if (status.canOverwrite) OutlinedButton(onClick = { pending = "Remove catalog entries at/after sector ${status.startSector} on ${session.name}? Future recordings can overwrite ALL data from this position, including damaged/unindexed data. Raw sectors are not erased. Recording will not start automatically." to NpCommand(NpAction.Overwrite,expectedCapacity=status.capacitySectors,expectedStart=status.startSector) }, enabled = idle, modifier = Modifier.heightIn(min = 48.dp)) { Text("Overwrite from last known position…") }
                HorizontalDivider(); Text("Destructive maintenance", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { pending = "Format the ${"%.2f".format(status.capacitySectors*512.0/1073741824)} GiB card in ${session.name}? ALL recording catalog entries will be removed and future recordings can overwrite their data. Download first. Capacity will be rechecked before delivery." to NpCommand(NpAction.Format,expectedCapacity=status.capacitySectors) }, enabled = idle && status.canFormat, modifier = Modifier.heightIn(min = 48.dp)) { Text("Format SD card…") }
                Text("Recording downloads/exports use USB or a card reader. Catalog listing/deletion below changes SD metadata; it does not transfer recorded signals to the phone.")
            } }
            if(status!=null && page=="Storage") {
                item { NpCard("On-device recordings") {
                    OutlinedButton(onClick={act(NpAction.ReadCatalog)},enabled=idle && status.stopped && (status.version==1 || status.has(0x400000)),modifier=Modifier.heightIn(min=48.dp)) { Text("Refresh recording list") }
                    if(status.version==2 && !status.has(0x400000)) Text("This NP2 firmware does not advertise BLE catalog listing/deletion. Mount/recovery still use their separate capability gates.")
                    np.catalog?.let { c ->
                        Text("${c.records.size} recordings · ${"%.2f".format(c.capacity*512.0/1073741824)} GiB card · last catalog ${time(c.reportedAtMs)}")
                        if(c.warning.isNotBlank()) Text(c.warning)
                        val last=c.records.lastOrNull()
                        OutlinedButton(onClick={pending="Delete LAST recording ${last!!.id} on ${session.name}, sectors ${last.start}–${last.end} (${"%.2f".format(last.mib)} MiB allocated)? This cannot be undone; later recordings can overwrite the data. The entire catalog/card snapshot is rechecked before sending. If it changed, nothing is deleted." to NpCommand(NpAction.DeleteLast,catalogFingerprint=c.fingerprint)},enabled=idle && status.stopped && c.canDelete && last!=null,modifier=Modifier.heightIn(min=52.dp)) { Text("Delete last recording…") }
                        Text("Only the last eligible recording can be deleted. Allocated size is not proof of valid recorded sample length. Refresh after reconnect or storage changes.")
                    }
                } }
                np.catalog?.let { catalog -> items(catalog.records,key={"record-${it.id}"}) { r ->
                    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text("Recording ${r.id} · ${"%.2f".format(r.mib)} MiB allocated",style=MaterialTheme.typography.titleMedium)
                        Text("Sectors ${r.start}–${r.end} · ${if(catalog.version==1)"Extent only; metadata not read" else r.label}")
                        if(r.hz>0) Text("${probes(r.mask.toInt())} · ${r.hz} samples/s · session ${r.session}")
                    } }
                } }
            }
            if (status != null && page == "Device modes") item { NpCard("Leave BLE control for a USB mode") {
                Text("Stop and wait for finalization first. An acknowledged handoff releases Bluetooth; check the destination on the PC. NP2 download starts from USB service, not an unsupported BLE destination4.")
                val modes = listOf(NpAction.UsbStreaming to "USB streaming / service") + (if (status.version == 1) listOf(NpAction.UsbDownload to "USB recording download") else emptyList()) + listOf(NpAction.Loader to "Firmware loader", NpAction.Dfu to "STM32 ROM DFU")
                modes.forEach { (action,label) -> OutlinedButton(onClick = { pending = "Switch ${session.name} to $label? Bluetooth disconnects after acknowledgement. This does not install firmware or verify USB arrival." to NpCommand(action) }, enabled = idle && status.canSwitchMode, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(label) } }
                HorizontalDivider(); Text("Emergency only · reset can lose buffered data", color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = { pending = "Emergency reset ${session.name} WITHOUT draining recording buffers? Active recordings can be incomplete. Use Stop normally. There is no firmware acknowledgement; reconnect and check afterwards." to NpCommand(NpAction.RescueReset,expectedFault=status.fault) }, enabled = idle && (status.version == 2 || status.stopped && status.state == 4 && status.fault != 0L && status.has(4096)), modifier = Modifier.heightIn(min = 48.dp)) { Text("Rescue reset…") }
                Text("Use Probe maps for IMRO programming and Closed-loop for qualified Ripple setup. CE64 images, stimulation and scheduler commands do not apply. Firmware installation remains a WILD-X USB/PC workflow.")
            } }
        }
        // Stop stays in the same place across subpages. Navigation and
        // Disconnect remain available during all device actions.
        if (status != null) FilledTonalButton(onClick = { act(NpAction.Stop) }, enabled = idle && !status.stopped, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(if (status.version == 2 && status.state == 4 || status.version == 1 && status.state == 2) "Saving recording · keep power connected" else "Stop / cancel recording · ${session.name}")
        }
    }
}

private fun probes(mask: Int) = (1..3).filter { mask and (1 shl (it-1)) != 0 }.joinToString(" + ") { "P$it" }
private fun time(ms: Long) = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(ms))
@Composable
private fun NpCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(title,style=MaterialTheme.typography.titleLarge); content() } }
}
