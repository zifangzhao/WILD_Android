package com.wild.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wild.android.ble.Ce64Scheduler
import com.wild.android.ble.DeviceSessionUiState
import com.wild.android.ble.SchedulerRuleUiState
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

internal fun schedulerDraftValues(rule: SchedulerRuleUiState): Map<String, String> = mapOf(
    "enabled" to if (rule.enabled) "1" else "0",
    "trigger" to rule.trigger.toString(), "action" to rule.action.toString(),
    "profile" to rule.profileId.toString(), "missed" to rule.missedPolicy.toString(),
    "date" to LocalDate.of(2000, 1, 1).plusDays(rule.anchorDay).toString(),
    "time" to LocalTime.ofSecondOfDay(rule.timeOfDaySeconds.coerceIn(0, 86399)).toString(),
    "period" to rule.periodSeconds.toString(), "duration" to rule.durationSeconds.toString(),
    "conditions" to rule.conditionMask.toString(), "inverted" to rule.conditionInvertMask.toString(),
    "logic" to rule.conditionLogic.toString(), "signal" to rule.signalSource.toString(),
    "battery" to rule.batteryThresholdMv.toString(), "storage" to rule.storageThresholdBlocks.toString(),
    "activity" to rule.activityThresholdMg.toString(), "ai" to rule.aiThresholdQ15.toString(),
    "evaluation" to rule.evaluationSeconds.toString(), "passes" to rule.debounceCount.toString(),
    "retries" to rule.maxDeferrals.toString(), "hysteresis" to rule.hysteresis.toString(),
    "priority" to rule.priority.toString(), "marker" to rule.marker.toString(),
)

/** Parse without clamping: an invalid typed value must never become a valid but different command. */
internal fun schedulerRuleFromDraft(id: Int, fields: Map<String, String>): SchedulerRuleUiState {
    fun n(key: String): Long = fields[key]?.toLongOrNull() ?: error("Enter a whole number for $key.")
    fun i(key: String): Int = n(key).also { require(it in Int.MIN_VALUE..Int.MAX_VALUE) { "$key is outside the supported range." } }.toInt()
    val trigger = i("trigger")
    val action = i("action")
    val conditions = i("conditions")
    val date = if (trigger == 1 || trigger == 2) {
        runCatching { LocalDate.parse(fields["date"]) }.getOrElse { error("Enter the date as YYYY-MM-DD.") }
    } else LocalDate.of(2000, 1, 1)
    val time = if (trigger != 3) {
        runCatching { LocalTime.parse(fields["time"]) }.getOrElse { error("Enter the time as HH:MM or HH:MM:SS (24-hour clock).") }
    } else LocalTime.MIDNIGHT
    val rule = SchedulerRuleUiState(
        id = id, enabled = i("enabled") == 1, trigger = trigger, action = action,
        profileId = if (action == 0) i("profile") else 255,
        missedPolicy = if (trigger == 3) 0 else i("missed"),
        anchorDay = ChronoUnit.DAYS.between(LocalDate.of(2000, 1, 1), date),
        timeOfDaySeconds = time.toSecondOfDay().toLong(),
        periodSeconds = if (trigger == 2) n("period") else 0,
        durationSeconds = if (action == 0) n("duration") else 0,
        conditionMask = conditions, conditionInvertMask = i("inverted") and conditions,
        conditionLogic = i("logic"), signalSource = if (trigger == 3) i("signal") else 0,
        batteryThresholdMv = i("battery"), storageThresholdBlocks = n("storage"),
        activityThresholdMg = i("activity"), aiThresholdQ15 = i("ai"),
        evaluationSeconds = n("evaluation"), debounceCount = i("passes"), maxDeferrals = i("retries"),
        hysteresis = i("hysteresis"), priority = i("priority"), marker = i("marker"),
    )
    Ce64Scheduler.validationError(rule)?.let { error(it) }
    return rule
}

internal fun schedulerClockLabel(seconds: Long?): String = seconds?.let {
    runCatching { LocalDateTime.of(2000, 1, 1, 0, 0).plusSeconds(it)
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) }.getOrNull()
} ?: "No wake scheduled"

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SchedulerRuleEditor(
    session: DeviceSessionUiState,
    targetCount: Int,
    canControl: Boolean,
    onRefresh: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onSetRule: (SchedulerRuleUiState) -> Unit,
    onSetRuleEnabled: (Int, Boolean) -> Unit,
    onClearRule: (Int) -> Unit,
    onClearAll: () -> Unit,
    onSaveProfile: (Int) -> Unit,
) {
    val status = session.schedulerStatus
    val config = session.schedulerConfig
    val canRead = canControl && !session.schedulerBusy
    val canWrite = canRead && !session.isRecordingLike && !session.configurationPendingApply && config != null
    var selectedId by rememberSaveable(session.id) { mutableIntStateOf(0) }
    var reload by rememberSaveable(session.id) { mutableIntStateOf(0) }
    var showAdvanced by rememberSaveable(session.id) { mutableStateOf(false) }
    var showProfiles by rememberSaveable(session.id) { mutableStateOf(false) }
    var profileSlot by rememberSaveable(session.id) { mutableIntStateOf(0) }
    var confirmation by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }

    confirmation?.let { (message, action) ->
        AlertDialog(onDismissRequest = { confirmation = null }, title = { Text("Confirm device change") },
            text = { Text(message) },
            confirmButton = { Button(onClick = { confirmation = null; action() }) { Text("Confirm") } },
            dismissButton = { OutlinedButton(onClick = { confirmation = null }) { Text("Cancel") } })
    }

    Text("Runs on the device, including when the phone disconnects. All dates and times use the device's local clock; timezone and daylight-saving changes are not automatic.", style = MaterialTheme.typography.bodyMedium)
    Text("${session.name} · ${if (targetCount == 1) "1 command target" else "$targetCount command targets"}", style = MaterialTheme.typography.titleSmall)
    Text("Schedule: ${when (status?.enabled) { true -> "Enabled"; false -> "Disabled"; null -> "Not read" }} · Clock: ${when (status?.clockValid) { true -> "Ready"; false -> "Set device time"; null -> "Not read" }}")
    status?.let {
        Text("Next wake: ${schedulerClockLabel(it.nextWakeSeconds)}", style = MaterialTheme.typography.bodyMedium)
        Text("${Ce64Scheduler.resultLabel(it.lastResult)} · ${it.deferredCount} deferred · ${it.missedCount} missed · ${it.conflictCount} conflicts", style = MaterialTheme.typography.bodyMedium)
    }
    Text(session.schedulerMessage, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
    if (session.schedulerBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (session.isRecordingLike) Text("Recording is active. Read the schedule now; stop recording before editing it.", style = MaterialTheme.typography.bodyMedium)
    if (session.configurationPendingApply) Text("Confirm pending acquisition settings before saving a recording profile.")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = onRefresh, enabled = canRead, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Read schedule") }
        FilledTonalButton(
            onClick = { confirmation = "${if (status?.enabled == true) "Disable" else "Enable"} scheduling on $targetCount device(s)?" to { onSetEnabled(status?.enabled != true) } },
            enabled = canWrite && status != null && (status.enabled || status.clockValid),
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
        ) { Text(if (status?.enabled == true) "Disable schedule" else "Enable schedule") }
    }
    if (config == null) {
        Text("Read the schedule before editing. A successful response confirms scheduler v2 support on this device; the device name alone does not.")
        return
    }
    val saved = config.rules.getOrElse(selectedId) { SchedulerRuleUiState(selectedId) }
    var values by rememberSaveable(session.id, selectedId, reload, stateSaver = mapSaver<Map<String, String>>(
        save = { it }, restore = { it.mapValues { entry -> entry.value as String } },
    )) { mutableStateOf(schedulerDraftValues(saved)) }
    fun set(key: String, value: String) { values = values + (key to value) }
    fun number(key: String) = values[key]?.toIntOrNull() ?: 0
    val draft = runCatching { schedulerRuleFromDraft(selectedId, values) }
    val error = draft.exceptionOrNull()?.message
    val targetSuffix = if (targetCount > 1) " to $targetCount devices" else ""

    HorizontalDivider()
    ScheduleChoice("Rule", selectedId, (0..7).map { it to "Rule ${it + 1}${if (config.rules.getOrNull(it)?.enabled == true) " · enabled" else " · disabled"}" }) { selectedId = it }
    Text("Your draft stays here if the connection drops. Read device values, then use Reload saved rule to replace the draft.", style = MaterialTheme.typography.bodyMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = { confirmation = "Replace this draft with the last-read rule ${selectedId + 1}?" to { reload++ } }) { Text("Reload saved rule") }
        OutlinedButton(onClick = {
            values = schedulerDraftValues(SchedulerRuleUiState(selectedId, enabled = true, durationSeconds = 1800,
                anchorDay = ChronoUnit.DAYS.between(LocalDate.of(2000, 1, 1), LocalDate.now())))
        }) { Text("New daily rule") }
    }
    ScheduleChoice("When", number("trigger"), listOf(0 to "Every day", 1 to "Once on a date", 2 to "Repeat at an interval", 3 to "When a condition crosses")) { set("trigger", it.toString()) }
    ScheduleChoice("Action", number("action"), listOf(0 to "Start recording", 1 to "Stop recording", 2 to "Write event marker", 3 to "Restart device")) { set("action", it.toString()) }
    ScheduleChoice("Rule state after saving", number("enabled"), listOf(1 to "Enabled", 0 to "Disabled (save only)")) { set("enabled", it.toString()) }
    if (number("trigger") in 1..2) ScheduleField("Date · YYYY-MM-DD", values.getValue("date"), numeric = false) { set("date", it) }
    if (number("trigger") != 3) ScheduleField("Device-local time · HH:MM:SS", values.getValue("time"), numeric = false) { set("time", it) }
    if (number("trigger") == 2) ScheduleField("Repeat every · seconds (60–604800)", values.getValue("period")) { set("period", it) }
    if (number("action") == 0) {
        ScheduleField("Record for · seconds (1–86400)", values.getValue("duration")) { set("duration", it) }
        ScheduleChoice("Recording settings", number("profile"), listOf(255 to "Current settings on device SD") + (0..7).map {
            it to "Profile ${it + 1}${if (config.profileGenerations.getOrElse(it) { 0 } > 0) " · saved" else " · not saved"}"
        }) { set("profile", it.toString()) }
    }
    if (number("trigger") == 3) {
        ScheduleChoice("Signal source", number("signal"), listOf(4 to "Battery voltage", 8 to "Free storage", 2 to "Motion activity", 1 to "AI output", 16 to "External event (reserved)")) {
            set("signal", it.toString())
            set("conditions", (number("conditions") or Ce64Scheduler.requiredCondition(it)).toString())
        }
        Text("A crossing triggers once, then re-arms after the release threshold is crossed. AI needs the running pipeline; external events need a firmware producer.", style = MaterialTheme.typography.bodyMedium)
    }
    Text("Conditions", style = MaterialTheme.typography.titleMedium)
    Text("Leave all off for a time-only rule. Storage thresholds refer to free space.", style = MaterialTheme.typography.bodyMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(1 to "Battery", 2 to "Free storage", 4 to "Motion", 8 to "AI output").forEach { (bit, title) ->
            FilterChip(selected = number("conditions") and bit != 0,
                onClick = { set("conditions", (number("conditions") xor bit).toString()) },
                label = { Text(title) }, modifier = Modifier.heightIn(min = 48.dp))
        }
    }
    if (number("conditions") != 0) {
        ScheduleChoice("Require", number("logic"), listOf(0 to "All conditions", 1 to "Any condition")) { set("logic", it.toString()) }
        listOf(Triple(1, "battery", "Battery · mV"), Triple(2, "storage", "Free storage · 512-byte blocks"), Triple(4, "activity", "Motion · mg"), Triple(8, "ai", "AI · signed Q15 (1.0 = 32767)")).forEach { (bit, key, title) ->
            if (number("conditions") and bit != 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1.2f)) { ScheduleField(title, values.getValue(key)) { set(key, it) } }
                    Box(Modifier.weight(1f)) {
                        ScheduleChoice("Threshold direction", if (number("inverted") and bit != 0) 1 else 0,
                            listOf(0 to "At or above ≥", 1 to "At or below ≤")) {
                            set("inverted", (if (it == 1) number("inverted") or bit else number("inverted") and bit.inv()).toString())
                        }
                    }
                }
            }
        }
        Text("2048 blocks = 1 MiB. Motion checks must be at least 60 seconds apart.", style = MaterialTheme.typography.bodyMedium)
    }
    TextButton(onClick = { showAdvanced = !showAdvanced }) { Text(if (showAdvanced) "Hide timing & priority" else "Timing, retries & priority") }
    if (showAdvanced) {
        if (number("trigger") != 3) ScheduleChoice("If an event is late", number("missed"), listOf(0 to "Skip missed events", 1 to "Catch up once within 5 minutes")) { set("missed", it.toString()) }
        listOf("evaluation" to "Condition check interval · seconds", "passes" to "Consecutive passing checks · 1–255", "retries" to "Maximum deferrals · 1–24", "hysteresis" to "Release hysteresis · threshold units", "priority" to "Priority · 0–65535 (higher wins)", "marker" to "Event marker · 0–65535").forEach { (key, label) ->
            ScheduleField(label, values.getValue(key)) { set(key, it) }
        }
    }
    val profileMissing = draft.getOrNull()?.let { it.action == 0 && it.profileId != 255 && config.profileGenerations.getOrElse(it.profileId) { 0 } == 0L } == true
    if (error != null || profileMissing) Text(error ?: "Save the selected recording profile first.", color = MaterialTheme.colorScheme.error)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = {
            val rule = draft.getOrThrow()
            confirmation = "Save rule ${selectedId + 1}$targetSuffix: ${rule.triggerLabel} · ${rule.actionLabel}${if (rule.action == 0) " for ${rule.durationSeconds} seconds" else ""}. ${if (rule.enabled) "It will be enabled when the scheduler is enabled." else "It will remain disabled."}" to { onSetRule(rule) }
        }, enabled = canWrite && draft.isSuccess && !profileMissing, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Save rule$targetSuffix") }
        OutlinedButton(onClick = { confirmation = "${if (saved.enabled) "Disable" else "Enable"} saved rule ${selectedId + 1}$targetSuffix? Unsaved edits are not included." to { onSetRuleEnabled(selectedId, !saved.enabled) } },
            enabled = canWrite, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text(if (saved.enabled) "Disable saved rule" else "Enable saved rule") }
    }
    TextButton(onClick = { showProfiles = !showProfiles }) { Text(if (showProfiles) "Hide recording profiles" else "Manage recording profiles (1–8)") }
    if (showProfiles) {
        Text("Save a copy of ${session.name}'s last-read 512-byte acquisition settings. This affects only this device. Existing rules that use the profile will use the replacement on their next start.")
        ScheduleChoice("Profile slot", profileSlot, (0..7).map { it to "Profile ${it + 1} · generation ${config.profileGenerations.getOrElse(it) { 0 }}" }) { profileSlot = it }
        Button(onClick = { val slot = profileSlot; confirmation = "Replace profile ${slot + 1} on ${session.name} with its last-read acquisition settings? The app will read back and compare all 512 bytes." to { onSaveProfile(slot) } },
            enabled = canWrite && session.parsedSystemParams != null && session.isConnected) { Text("Save settings to profile ${profileSlot + 1}") }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = { confirmation = "Delete rule ${selectedId + 1}$targetSuffix?" to { onClearRule(selectedId) } }, enabled = canWrite) { Text("Delete this rule") }
        TextButton(onClick = { confirmation = "Delete all rules and disable scheduling$targetSuffix?" to onClearAll }, enabled = canWrite) { Text("Delete all rules") }
    }
}

@Composable
private fun ScheduleChoice(label: String, selected: Int, options: List<Pair<Int, String>>, onChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(options.firstOrNull { it.first == selected }?.second ?: "Choose $label", modifier = Modifier.weight(1f))
                Text("▾")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (value, title) -> DropdownMenuItem(text = { Text(title) }, onClick = { expanded = false; onChange(value) }) }
            }
        }
    }
}

@Composable
private fun ScheduleField(label: String, value: String, numeric: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text),
        modifier = Modifier.fillMaxWidth())
}
