package com.wild.android.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.*
import com.wild.android.ble.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NpProbeControls(session:DeviceSessionUiState,send:(NpCommand)->Unit,confirm:(String,NpCommand)->Unit) {
    val context=LocalContext.current;val scope=rememberCoroutineScope();val np=session.neuropixels;val s=np.status?:return
    val idle=session.isConnected && !np.busy
    var connector by rememberSaveable(session.id) { mutableIntStateOf(1) }
    var candidate by remember(session.id) { mutableStateOf<NpProbeMap?>(null) }
    var importing by remember(session.id) { mutableStateOf(false) }
    var error by remember(session.id) { mutableStateOf("") }
    var target by remember { mutableStateOf<Triple<String,Int,Int>?>(null) }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val bound=target
        if(uri!=null && bound!=null) {
            if(bound.first!=session.id || bound.third!=s.version) error="Device changed while selecting a file. Select it again for this device."
            else scope.launch {
                importing=true;error="";candidate=null
                try {
                    val selected=withContext(Dispatchers.IO) {
                        val resolver=context.contentResolver
                        val name=resolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { c -> if(c.moveToFirst()) c.getString(0) else null }?:"Probe configuration"
                        val bytes=resolver.openInputStream(uri)?.use { input ->
                            val output=ByteArrayOutputStream();val buffer=ByteArray(8192)
                            while(true) {val n=input.read(buffer);if(n<0) break;require(output.size()+n<=NpProbeConfiguration.MaxFileBytes) { "Configuration exceeds 1 MiB." };output.write(buffer,0,n)}
                            output.toByteArray()
                        }?:throw IllegalArgumentException("Could not open the selected document.")
                        val routing=context.assets.open("np2-routing.json").bufferedReader().use { it.readText() }
                        NpProbeConfiguration.importMap(bytes,name,bound.second,routing).also { require(it.version==bound.third) { "Selected file belongs to another device family." } }
                    }
                    candidate=selected
                } catch(cancelled:CancellationException) {throw cancelled}
                catch(e:Exception) {error=e.message?:"Invalid configuration."}
                finally {importing=false}
            }
        }
    }
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("Import an NP1 IMRO/native JSON, NRIC native JSON, or NP2013 four-shank IMRO/JSON. Connector selection is explicit; filenames do not choose a probe. This stages a map, not a recording.")
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            (1..3).forEach { p -> FilterChip(selected=connector==p,onClick={connector=p;candidate=null},enabled=idle && !importing && (s.version==1 || (np.availableProbeMask.takeIf {it!=0}?:s.probeMask) and (1 shl(p-1))!=0),label={Text("Connector P$p")},modifier=Modifier.heightIn(min=48.dp)) }
        }
        OutlinedButton(onClick={target=Triple(session.id,connector,s.version);picker.launch(arrayOf("*/*"))},enabled=idle && s.canConfigure && !importing,modifier=Modifier.heightIn(min=48.dp)) { Text("Import IMRO / JSON…") }
        if(importing) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
        candidate?.let { map ->
            Text("P${map.connector} · ${map.source} · ${if(map.model==4) "NP2013 four-shank" else if(map.model==2) "NP1" else "NRIC1384"}")
            Text("Request ID ${map.id.take(16)}… · CRC ${"%08X".format(map.crc)}. Import and routing checks passed locally; hardware is not yet programmed.")
            Button(onClick={confirm("Stage ${map.source} for ${session.name}, connector P${map.connector}? The device applies this map during the next probe preparation. Staged is not proof of an applied silicon map; check ID/CRC and verification after Start.",NpCommand(NpAction.StageProbeMap,probeMap=map))},enabled=idle && s.canConfigure,modifier=Modifier.heightIn(min=52.dp)) { Text("Stage this probe map") }
        }
        HorizontalDivider()
        np.probeMaps.forEach { map ->
            val receipt=np.probeReceipts.find {it.connector==map.connector}
            Text("P${map.connector}: ${map.source}",style=MaterialTheme.typography.titleMedium)
            Text(when {receipt==null->"No current receipt. Read status before relying on this request.";!receipt.matches(map)->"Not matched: device ID/CRC or error differs from this phone's request.";receipt.applied(map)->if(receipt.degraded)"DEGRADED applied receipt. Failed shank mask ${receipt.verification ushr 4}; full silicon mapping is NOT verified." else "Matching applied ID/CRC and firmware verification. Analog signal is not verified.";else->receipt.description})
        }
        OutlinedButton(onClick={send(NpCommand(NpAction.ReadProbeReceipts))},enabled=idle && !s.isMedia,modifier=Modifier.heightIn(min=48.dp)) { Text("Read device map receipts") }
        if(np.probeMaps.isEmpty()) np.probeReceipts.forEach { Text("P${it.connector}: ${it.description} · CRC ${"%08X".format(it.crc)}. No phone request to compare.") }
        Text("Retained requests are rechecked/restaged for selected connectors before Start. Only read-only receipt checks run during capture; a missing/mismatched receipt never stops an active recording. Transfers are bounded and COMMIT is never retried automatically.")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NpClosedLoopControls(session:DeviceSessionUiState,send:(NpCommand)->Unit,confirm:(String,NpCommand)->Unit) {
    val np=session.neuropixels;val s=np.status?:return;val idle=session.isConnected && !np.busy
    var count by rememberSaveable(session.id) {mutableIntStateOf(1)}
    var p1 by rememberSaveable(session.id) {mutableStateOf(if(s.version==2) "2" else "1")};var p2 by rememberSaveable(session.id) {mutableStateOf("2")}
    var c1 by rememberSaveable(session.id) {mutableStateOf("0")};var c2 by rememberSaveable(session.id) {mutableStateOf("1")}
    var g1 by rememberSaveable(session.id) {mutableStateOf("5")};var g2 by rememberSaveable(session.id) {mutableStateOf("5")}
    var rectify1 by rememberSaveable(session.id) {mutableStateOf(true)};var rectify2 by rememberSaveable(session.id) {mutableStateOf(true)}
    var trainingStart by rememberSaveable(session.id) {mutableStateOf("0")};var trainingDuration by rememberSaveable(session.id) {mutableStateOf("10000")}
    val settings=runCatching {
        NpClosedLoopSettings(listOf(NpDetector(p1.toInt(),c1.toInt(),g1.toFloat(),rectify1))+(if(count==2)listOf(NpDetector(p2.toInt(),c2.toInt(),g2.toFloat(),rectify2))else emptyList()),trainingStart.toLong(),trainingDuration.toLong())
    }.getOrNull()
    val validation=runCatching { NpClosedLoop.validate(s,requireNotNull(np.closedLoopCapabilities) {"Read device capabilities first."},requireNotNull(settings) {"Enter valid detector numbers."}) }
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("Optional on-device Ripple event detection, not CE64 stimulation. Qualified SD profiles: NP1 P1+P2 at 15 kHz; NP2 P1+P2+P3 at 20 kHz. Firmware must also admit the selected sources.")
        OutlinedButton(onClick={send(NpCommand(NpAction.ReadClosedLoop))},enabled=idle,modifier=Modifier.heightIn(min=48.dp)) { Text("Read capabilities & detector status") }
        np.closedLoop?.let { r -> Text("${if(!r.supported) "Unsupported / error ${r.result}" else if(r.active) "Active" else if(r.configured) "Configured; waits for recording" else "Disabled"} · max ${r.maxLanes} detectors · ${r.dropped} dropped events · last report ${java.time.Instant.ofEpochMilli(r.reportedAtMs)}") }
        if(np.closedLoopUncertain) Text("Setup outcome uncertain. Read status or explicitly Disable; never retry CONFIGURE blindly.",color=MaterialTheme.colorScheme.error)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { (1..2).forEach { n -> FilterChip(selected=count==n,onClick={count=n},label={Text("$n detector${if(n==2)"s" else ""}")},modifier=Modifier.heightIn(min=48.dp)) } }
        Text("Detector 1 · Ripple filter",style=MaterialTheme.typography.titleMedium)
        NpDetectorFields(p1,{p1=it},c1,{c1=it},g1,{g1=it},rectify1,{rectify1=it})
        if(count==2) {Text("Detector 2 · Ripple filter",style=MaterialTheme.typography.titleMedium);NpDetectorFields(p2,{p2=it},c2,{c2=it},g2,{g2=it},rectify2,{rectify2=it})}
        if(s.version==2 && count==2) Text("Two NP2 SD detectors require P2 channels 0 and 1.")
        OutlinedTextField(value=trainingStart,onValueChange={trainingStart=it},label={Text("Training begins after capture start · milliseconds")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth())
        OutlinedTextField(value=trainingDuration,onValueChange={trainingDuration=it},label={Text("Training duration · milliseconds (>0)")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth())
        Text("Gain is the threshold multiplier (0.1–1000), not neural amplifier gain. Configuration does not start recording. Preview and closed-loop share device resources; preview is retired before setup.")
        if(validation.isFailure) Text(validation.exceptionOrNull()?.message?:"Invalid detector settings.",color=MaterialTheme.colorScheme.error)
        Button(onClick={confirm("Configure $count Ripple detector(s) on ${session.name} using the applied recording profile? This changes on-device event detection, retires any preview lease, and takes effect during recording. No analog/performance qualification is implied.",NpCommand(NpAction.ConfigureClosedLoop,closedLoopSettings=settings))},enabled=idle && validation.isSuccess && !np.closedLoopUncertain,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {Text("Apply detector setup")}
        OutlinedButton(onClick={confirm("Disable closed-loop processing on ${session.name}? Neural recording remains a separate action. This sends one explicit disable and verifies readback.",NpCommand(NpAction.DisableClosedLoop))},enabled=idle && s.canConfigure,modifier=Modifier.heightIn(min=48.dp)) {Text("Disable closed-loop")}
    }
}

@Composable
private fun NpDetectorFields(probe:String,onProbe:(String)->Unit,channel:String,onChannel:(String)->Unit,gain:String,onGain:(String)->Unit,rectify:Boolean,onRectify:(Boolean)->Unit) {
    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(value=probe,onValueChange=onProbe,label={Text("Probe · 1–3")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.weight(1f))
        OutlinedTextField(value=channel,onValueChange=onChannel,label={Text("Channel · 0–383")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.weight(1f))
        OutlinedTextField(value=gain,onValueChange=onGain,label={Text("Threshold gain")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.weight(1f))
    }
    Row {Checkbox(checked=rectify,onCheckedChange=onRectify);Text("Rectify filtered signal before thresholding")}
}
