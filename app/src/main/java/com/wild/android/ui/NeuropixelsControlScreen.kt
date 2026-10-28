package com.wild.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
    session: DeviceSessionUiState,
    sessions: List<DeviceSessionUiState>,
    onBack: () -> Unit,
    onSelectDevice: (String) -> Unit,
    onLink: () -> Unit,
    onAction: (NpAction, Int, Int, Int, Boolean) -> Unit,
) {
    val np = session.neuropixels
    val status = np.status
    val connected = session.isConnected
    val idle = connected && !np.busy
    var pending by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var devicesExpanded by remember { mutableStateOf(false) }
    pending?.let { (message, action) ->
        AlertDialog(onDismissRequest = { pending = null }, title = { Text("${session.name} · confirm action") },
            text = { Text(message) },
            confirmButton = { Button(onClick = { pending = null; action() }) { Text("Confirm") } },
            dismissButton = { OutlinedButton(onClick = { pending = null }) { Text("Cancel") } })
    }
    fun act(action: NpAction) = onAction(action, 0, 0, 0, false)

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("Devices") }
            Box(Modifier.weight(1f)) {
                FilledTonalButton(onClick = { devicesExpanded = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("${session.name} ▾") }
                DropdownMenu(expanded = devicesExpanded, onDismissRequest = { devicesExpanded = false }) {
                    sessions.forEach { candidate -> DropdownMenuItem(text = { Text(candidate.name) }, onClick = { devicesExpanded = false; onSelectDevice(candidate.id) }) }
                }
            }
            OutlinedButton(onClick = onLink, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (connected || session.isLinkingLike) "Disconnect" else "Connect") }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
            item {
                NpCard("WILDX Neuropixels${status?.let { " · NP${it.version} / V${it.version}" }.orEmpty()}") {
                    Text(if (connected) status?.stateLabel ?: "Reading device capabilities…" else "Disconnected · showing last device report", style = MaterialTheme.typography.titleMedium)
                    Text("Controls on this page affect ${session.name} only. Probe numbers identify board connectors; they do not prove that a probe is physically attached.")
                    if (np.reportedAtMs > 0) Text("Last device report: ${DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(np.reportedAtMs))}")
                    if (status != null) {
                        Text("Selected probes: ${(1..3).filter { status.probeMask and (1 shl (it - 1)) != 0 }.joinToString { "P$it" }} · ${status.sampleRate} samples/s")
                        Text("Recording length: ${status.recordedSeconds} s · ${"%.1f".format(status.recordedMiB)} MiB recorded")
                        Text("SD capacity: ${"%.2f".format(status.capacitySectors * 512.0 / 1073741824)} GiB · next write sector: ${status.startSector}")
                        if (status.fault != 0L || status.error != 0L) Text("Device fault ${status.fault} · command error ${status.error}. Keep existing recordings; use the PC console for detailed diagnostics.", color = MaterialTheme.colorScheme.error)
                    }
                    Text(np.message)
                    if (np.failure.isNotBlank()) Text(np.failure, color = MaterialTheme.colorScheme.error)
                    if (np.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    OutlinedButton(onClick = { act(NpAction.ReadStatus) }, enabled = idle, modifier = Modifier.heightIn(min = 48.dp)) { Text("Read device status") }
                }
            }
            if (status != null) {
                item {
                    var mask by rememberSaveable(session.id) { mutableIntStateOf(status.probeMask) }
                    var rate by rememberSaveable(session.id) { mutableIntStateOf(status.sampleRate) }
                    NpCard("Recording profile") {
                        Text(if (status.version == 2) "NP2 records consecutive connectors: P1, P1+P2, or P1+P2+P3. Each probe has 384 channels; the rate is per probe." else "NP1 AP and LFP are recorded together. The LFP rate is the AP rate ÷ 12; choose the probe combination supported by this firmware.")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (1..7).filter { candidate ->
                                val available = if (status.version == 1) 7 else np.availableProbeMask.takeIf { it != 0 } ?: status.probeMask
                                candidate and available.inv() == 0 && NpBleProtocol.supportsProfile(status, candidate, rate)
                            }.forEach { candidate ->
                                FilterChip(selected = mask == candidate, onClick = { mask = candidate }, enabled = idle && status.canConfigure,
                                    label = { Text((1..3).filter { candidate and (1 shl (it - 1)) != 0 }.joinToString(" + ") { "P$it" }) }, modifier = Modifier.heightIn(min = 48.dp))
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NpBleProtocol.rates.forEach { candidate ->
                                FilterChip(selected = rate == candidate, onClick = { rate = candidate }, enabled = idle && status.canConfigure && NpBleProtocol.supportsProfile(status, mask, candidate), label = { Text("${candidate / 1000.0} kHz") }, modifier = Modifier.heightIn(min = 48.dp))
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { val chosenMask = mask; val chosenRate = rate; pending = "Apply probe mask $chosenMask at $chosenRate samples/s? This changes the next recording profile." to { onAction(NpAction.ApplyProfile, chosenMask, chosenRate, 0, false) } },
                                enabled = idle && status.canConfigure && NpBleProtocol.supportsProfile(status, mask, rate), modifier = Modifier.heightIn(min = 48.dp)) { Text("Apply recording profile") }
                            OutlinedButton(onClick = { mask = status.probeMask; rate = status.sampleRate }) { Text("Use device values") }
                        }
                        Text("Apply is confirmed by a device reply. Preparing or queued does not mean capture has started.")
                    }
                }
                item {
                    var duration by rememberSaveable(session.id) { mutableStateOf("0") }
                    var allow by rememberSaveable(session.id) { mutableStateOf(false) }
                    val seconds = duration.toIntOrNull()
                    val startCheck = runCatching { require(seconds != null); NpBleProtocol.start(status, 2, seconds, allow) }
                    NpCard("On-device SD recording") {
                        OutlinedTextField(value = duration, onValueChange = { duration = it }, label = { Text("Duration in seconds · 0 = continuous, up to 3600") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                        Text("Continuous recording runs until Stop or a device/storage limit. A duration reserves the firmware's storage blocks; reported length comes from the device.")
                        if (status.version == 1 && (!status.has(2) || status.probeMask == 3 && status.sampleRate == 30000)) {
                            Row { Checkbox(checked = allow, onCheckedChange = { allow = it }); Text("I accept this unqualified card/profile. Two-probe 30 kHz has shown sustained-recording failures.") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { val chosen = seconds!!; val accepted = allow; pending = "Start recording on ${session.name} using the device's applied probe profile, ${if (chosen == 0) "until Stop" else "for a $chosen-second allocation"}?" to { onAction(NpAction.Start, 0, 0, chosen, accepted) } }, enabled = idle && startCheck.isSuccess, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Start recording") }
                            FilledTonalButton(onClick = { act(NpAction.Stop) }, enabled = idle && !status.stopped, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Stop recording") }
                        }
                        if (startCheck.isFailure && status.stopped) Text(startCheck.exceptionOrNull()?.message ?: "Choose a valid duration and profile.", color = MaterialTheme.colorScheme.error)
                        if (status.state == 4 && status.version == 2 || status.state == 2 && status.version == 1) Text("Stop is still saving data. The status refreshes automatically; keep the device powered.")
                    }
                }
                item {
                    NpCard("Time & connection mode") {
                        Text(np.clockMessage)
                        Text("Sync measures Bluetooth round-trip delay and anchors the device to this phone's clock. The displayed uncertainty describes that exchange; the phone's own UTC accuracy also matters.")
                        OutlinedButton(onClick = { act(NpAction.SyncTime) }, enabled = idle && status.stopped && status.has(256), modifier = Modifier.heightIn(min = 48.dp)) { Text("Sync phone time") }
                        OutlinedButton(onClick = { act(NpAction.Mount) }, enabled = idle && status.stopped && status.has(16384), modifier = Modifier.heightIn(min = 48.dp)) { Text("Mount SD catalog") }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf(NpAction.UsbStreaming to "USB streaming", NpAction.Loader to "Firmware loader", NpAction.Dfu to "ROM DFU").forEach { (action, label) ->
                                OutlinedButton(onClick = { pending = "Switch ${session.name} to $label? The Bluetooth link may close. Continue using USB and the WILDX PC console." to { act(action) } }, enabled = idle && status.stopped && status.fault == 0L && status.has(8192), modifier = Modifier.heightIn(min = 48.dp)) { Text(label) }
                            }
                        }
                        Text("Probe IMRO/site programming, recording downloads, and firmware installation use the WILDX PC console. CE64 settings and CE64 firmware packages do not apply to this device.")
                    }
                }
            }
        }
    }
}

@Composable
private fun NpCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        content()
    } }
}
