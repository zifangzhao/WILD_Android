package com.wild.android.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wild.android.ble.*
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

private val traceColors=listOf(Color(0xff168bce),Color(0xffc15e18),Color(0xff12876d),Color(0xffae499d))

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NpPreviewPanel(sessions: List<DeviceSessionUiState>,calibrations: MutableMap<Triple<String,Int,Int>,NpDisplayCalibration>,send: (NpCommand)->Unit) {
    var seconds by rememberSaveable { mutableFloatStateOf(2f) }
    var gain by rememberSaveable { mutableFloatStateOf(1f) }
    var cutoff by rememberSaveable { mutableStateOf(0.0) }
    var spectrum by rememberSaveable { mutableStateOf(false) }
    var displaySettings by rememberSaveable { mutableStateOf(false) }
    val devices=sessions.filter { it.isNeuropixels }.sortedBy { it.name.lowercase() }
    Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text("Live waveforms · WILD-X",style=MaterialTheme.typography.headlineSmall)
        Text("Show preview for each connected device below. This observes an existing SD recording; it never starts or stops recording. Devices share a relative time window, not a synchronized clock.")
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick={ devices.filter { it.isConnected }.forEach { send(NpCommand(NpAction.StopPreview,deviceId=it.id)) } },enabled=devices.any { it.neuropixels.preview.enabled || it.neuropixels.busy },modifier=Modifier.heightIn(min=48.dp)) { Text("Pause all previews · keep recording") }
            OutlinedButton(onClick={displaySettings=!displaySettings},modifier=Modifier.heightIn(min=48.dp)) {Text(if(displaySettings)"Hide display settings" else "Display settings / spectrum…")}
        }
        Text("${"%.2f".format(seconds)} s window · ×${"%.1f".format(gain)} display gain",style=MaterialTheme.typography.titleMedium)
        Text("On a waveform: horizontal pinch = time · vertical pinch = gain · vertical drag = offset. All changes are display-only.")
        if(displaySettings) {
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf(0.5f,2f,5f,10f).forEach { value -> FilterChip(selected=seconds==value,onClick={seconds=value},label={Text("$value s")},modifier=Modifier.heightIn(min=48.dp)) }
            OutlinedButton(onClick={seconds=2f;gain=1f},modifier=Modifier.heightIn(min=48.dp)) { Text("Reset time / gain") }
        }
        Text("Display gain (does not program the probe)")
        Slider(value=log10(gain),onValueChange={gain=10f.pow(it)},valueRange=-1f..2f,modifier=Modifier.semantics { contentDescription="Display gain" })
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf(0.0,0.7,5.0,30.0).forEach { value -> FilterChip(selected=cutoff==value,onClick={cutoff=value},label={Text(if(value==0.0) "High-pass off" else "$value Hz high-pass")},modifier=Modifier.heightIn(min=48.dp)) }
            FilterChip(selected=spectrum,onClick={spectrum=!spectrum},label={Text(if(spectrum)"Spectrum shown" else "Show spectrum")},modifier=Modifier.heightIn(min=48.dp))
        }
        }
        devices.forEachIndexed { deviceIndex,device -> key(device.id) {
            NpDevicePreview(device,deviceIndex,seconds,gain,cutoff,spectrum,calibrations,{seconds=it},{gain=it},send)
        } }
        Text("About the signal: NP1 shows up to four channels from one probe/band when compact preview is advertised. NP2 shows up to two AP channels on P1/P2 with filtered-preview firmware (useful band 0–200 Hz). Older firmware is limited to one subsampled trace; high-frequency aliasing is possible. Missing samples are never interpolated. No waveform samples are uploaded to Firebase.")
        Text("Switching devices or control tabs keeps previews running. Leaving WILD-X controls pauses all preview leases, not SD recording.")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NpDevicePreview(device: DeviceSessionUiState, index: Int, seconds: Float, gain: Float, cutoff: Double, spectrum: Boolean,
    profiles: MutableMap<Triple<String,Int,Int>,NpDisplayCalibration>,onSeconds:(Float)->Unit,onGain:(Float)->Unit,send:(NpCommand)->Unit) {
    val np=device.neuropixels; val status=np.status; val preview=np.preview
    var expanded by rememberSaveable(device.id) { mutableStateOf(device.isConnected) }
    LaunchedEffect(preview.enabled) { if(preview.enabled)expanded=false }
    var probe by rememberSaveable(device.id) { mutableIntStateOf(1) }
    var secondProbe by rememberSaveable(device.id) { mutableIntStateOf(1) }
    var channels by rememberSaveable(device.id) { mutableStateOf("0") }
    var band by rememberSaveable(device.id) { mutableIntStateOf(0) }
    var showCalibration by rememberSaveable(device.id) { mutableStateOf(false) }
    val version=status?.version?:2
    val parsed=channels.split(',').map { it.trim().toIntOrNull() }
    val max=if(version==1 && status?.has(0x08000000)==true)4 else if(version==1)1 else 2
    val valid=parsed.size in 1..max && parsed.all { it!=null && it in 0..383 }
    val selections=if(valid) parsed.mapIndexed { i,ch -> NpPreviewSelection(if(version==2 && i==1)secondProbe else probe,ch!!) } else emptyList()
    val selectionValid=valid && selections.distinct().size==selections.size && selections.all { status!=null && status.probeMask and (1 shl(it.probe-1))!=0 }
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text(device.name,style=MaterialTheme.typography.titleLarge)
        Text("${device.id.takeLast(8)} · ${if(device.isConnected)status?.stateLabel?:"Reading status" else "Disconnected · last preview retained"}")
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick={expanded=!expanded},modifier=Modifier.heightIn(min=48.dp)) { Text(if(expanded)"Hide channel setup" else "Choose preview channels") }
            OutlinedButton(onClick={send(NpCommand(NpAction.StopPreview,deviceId=device.id))},enabled=device.isConnected && (preview.enabled || np.busy),modifier=Modifier.heightIn(min=48.dp)) { Text("Pause this preview") }
        }
        if(expanded) {
            Text(if(version==1)"NP1 · one probe/band, 1–$max traces" else "NP2 · up to two AP traces; firmware support is checked before use")
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                (1..(if(version==1)3 else 2)).forEach { p -> FilterChip(selected=probe==p,onClick={probe=p},enabled=!preview.enabled,label={Text("Probe P$p")},modifier=Modifier.heightIn(min=48.dp)) }
                if(version==1) (0..1).forEach { b -> FilterChip(selected=band==b,onClick={band=b},enabled=!preview.enabled,label={Text(if(b==0)"AP" else "LFP")},modifier=Modifier.heightIn(min=48.dp)) }
            }
            OutlinedTextField(value=channels,onValueChange={channels=it},label={Text("Channels 0–383 · comma-separated (e.g. 0, 64)")},singleLine=true,enabled=!preview.enabled,modifier=Modifier.fillMaxWidth())
            if(version==2 && parsed.size>1) FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                (1..2).forEach { p -> FilterChip(selected=secondProbe==p,onClick={secondProbe=p},enabled=!preview.enabled,label={Text("Second trace · P$p")},modifier=Modifier.heightIn(min=48.dp)) }
            }
            if(!selectionValid) Text("Choose 1–$max distinct channels on active probes.",color=MaterialTheme.colorScheme.error)
            Button(onClick={send(NpCommand(NpAction.StartPreview,probe=probe,channel=selections.first().channel,band=if(version==1)band else 0,deviceId=device.id,previewChannels=selections))},
                enabled=device.isConnected && !np.busy && !preview.enabled && selectionValid && status?.has(128)==true && status.state==(if(version==1)1 else 3),modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Text(if(np.busy)"Preparing / device busy…" else "Show live preview · keep SD recording") }
            if(status?.recording!=true) Text("Start a neural SD recording under Recording first. A scheduled/warming-up device is not yet ready for preview.")
        }
        if(np.failure.isNotBlank()) Text(np.failure,color=MaterialTheme.colorScheme.error)
        Text(preview.message)
        if(preview.updatedAtMs>0) Text("Last sample ${java.time.Instant.ofEpochMilli(preview.updatedAtMs).atZone(java.time.ZoneId.systemDefault()).toLocalTime().withNano(0)} · ${"%.1f".format(preview.sampleHz)} Hz · ${preview.gaps} gap bursts${if(!preview.enabled)" · PAUSED / retained" else ""}")
        OutlinedButton(onClick={showCalibration=!showCalibration},modifier=Modifier.heightIn(min=48.dp)) { Text(if(showCalibration)"Hide calibration" else "Calibration / µV units…") }
        if(showCalibration) {
            val ports=(preview.traces.map { it.selection.probe }+selections.map { it.probe }+probe).distinct().sorted()
            val displayBand=if(preview.traces.isNotEmpty())preview.band else if(version==1)band else 0
            ports.forEach { p -> key(p,displayBand) { NpCalibrationEditor(device.id,version,p,displayBand) { profiles[Triple(device.id,p,displayBand)]=it } } }
        }
        preview.traces.forEachIndexed { lane,trace -> key(trace.selection.probe,trace.selection.channel,preview.band) {
            val calibration=profiles[Triple(device.id,trace.selection.probe,preview.band)]?.takeIf { it.factory==null || it.factory.version==version }
            val points=remember(trace.points,calibration,cutoff,preview.stride,preview.sampleHz) {
                val corrected=if(calibration==null || calibration.factory==null && calibration.scale==1.0)trace.points
                    else trace.points.map { p -> p.copy(value=calibration.correct(p.value,trace.selection.channel,preview.band,preview.filtered)) }
                if(preview.sampleHz>0 && preview.stride>0) NpPreviewAnalysis.highPass(corrected,preview.sampleHz,preview.stride,cutoff.coerceAtMost(preview.sampleHz/4)) else corrected
            }
            val units=calibration?.units?:"counts"
            val scale=calibration?.scale?:1.0
            NpTracePlot("${device.name} · P${trace.selection.probe} · Ch ${trace.selection.channel} · ${if(preview.band==0)"AP" else "LFP"}",points,preview,traceColors[(index+lane)%traceColors.size],units,scale,seconds,gain,onSeconds,onGain)
            if(spectrum && preview.sampleHz>0 && preview.stride>0) NpSpectrumPlot(points,preview,units,traceColors[(index+lane)%traceColors.size])
        } }
    } }
}

@Composable
private fun NpTracePlot(label: String,points: List<NpPreviewPoint>,preview: NpPreviewUi,color: Color,units: String,scale: Double,
    seconds: Float,gain: Float,onSeconds:(Float)->Unit,onGain:(Float)->Unit) {
    var offset by rememberSaveable(label) { mutableFloatStateOf(0f) }
    val currentSeconds by rememberUpdatedState(seconds); val currentGain by rememberUpdatedState(gain)
    val grid=MaterialTheme.colorScheme.outlineVariant; val background=MaterialTheme.colorScheme.surface
    val range=256*scale/gain
    Text(label,style=MaterialTheme.typography.titleMedium,color=color)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
        Text("±${"%.2f".format(range)} $units · ×${"%.1f".format(gain)}")
        TextButton(onClick={offset=0f},modifier=Modifier.heightIn(min=48.dp)) { Text("Center trace") }
    }
    Canvas(Modifier.fillMaxWidth().height(200.dp).background(background).semantics { contentDescription="$label waveform. Horizontal pinch adjusts time, vertical pinch adjusts gain, vertical drag adjusts offset." }
        .pointerInput(label) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed=true)
                var pinched=false
                do {
                    val event=awaitPointerEvent(); val active=event.changes.filter { it.pressed }
                    if(active.size>=2) {
                        pinched=true
                        val a=active[0];val b=active[1]
                        if(a.previousPressed && b.previousPressed) {
                            val view=NpPreviewAnalysis.pinch(NpPreviewAnalysis.View(currentSeconds,currentGain,offset),abs(b.position.x-a.position.x),abs(b.position.y-a.position.y),abs(b.previousPosition.x-a.previousPosition.x),abs(b.previousPosition.y-a.previousPosition.y))
                            onSeconds(view.seconds);onGain(view.gain);active.forEach { it.consume() }
                        }
                    } else if(active.size==1 && !pinched && size.height>0) {
                        val change=active.single()
                        if(change.previousPressed) { offset=(offset+(change.position.y-change.previousPosition.y)/size.height).coerceIn(-1.5f,1.5f);change.consume() }
                    }
                } while(event.changes.any { it.pressed })
            }
        }) {
        repeat(5) { i -> drawLine(grid,Offset(0f,size.height*i/4),Offset(size.width,size.height*i/4)) }
        repeat(6) { i -> drawLine(grid,Offset(size.width*i/5,0f),Offset(size.width*i/5,size.height)) }
        val last=points.lastOrNull()?.tick?:return@Canvas
        if(preview.stride<=0 || preview.sampleHz<=0) return@Canvas
        val span=seconds*preview.sampleHz*preview.stride
        val path=Path();var pen=false;var previous: Long?=null
        points.forEach { p ->
            val x=((1-(last-p.tick)/span)*size.width).toFloat()
            if(!p.value.isFinite() || x<0) {pen=false;previous=null}
            else {
                val y=(size.height*(0.5+offset)-p.value/range*size.height/2).toFloat()
                if(pen && previous?.let { p.tick-it==preview.stride }==true)path.lineTo(x,y) else path.moveTo(x,y)
                pen=true;previous=p.tick
            }
        }
        clipRect {drawPath(path,color,style=Stroke(width=2f))}
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Text("−${"%.2f".format(seconds)} s");Text("0 · latest received")}
    Text("Offset ${"%.0f".format(offset*100)}% · displayed ${if(units=="counts")"nominal ADC counts" else units}",style=MaterialTheme.typography.bodyMedium)
}

@Composable
private fun NpSpectrumPlot(points: List<NpPreviewPoint>,preview: NpPreviewUi,units: String,color: Color) {
    val bins=remember(points,preview.sampleHz,preview.stride) { NpPreviewAnalysis.spectrum(points,preview.sampleHz,preview.stride) }
    val grid=MaterialTheme.colorScheme.outlineVariant
    Text("Spectrum · $units peak amplitude · ${if(preview.filtered)"filtered preview, useful 0–200 Hz" else "subsampled preview, aliasing possible"}",style=MaterialTheme.typography.titleMedium)
    if(bins.isEmpty()) { Text("Waiting for at least 64 contiguous samples. Gaps reset the spectrum.");return }
    val max=bins.maxOf { it.amplitude }.coerceAtLeast(1e-9);val peak=bins.drop(1).maxByOrNull { it.amplitude }
    Text("Peak ${"%.1f".format(peak?.hz?:0.0)} Hz · ${"%.2f".format(peak?.amplitude?:0.0)} $units · resolution ${"%.2f".format(bins[1].hz)} Hz · scale 0–${"%.2f".format(max)} $units")
    Canvas(Modifier.fillMaxWidth().height(150.dp).semantics { contentDescription="One-sided Hann-windowed amplitude spectrum, 0 to ${preview.sampleHz/2} Hz" }) {
        repeat(4) { i -> drawLine(grid,Offset(0f,size.height*i/3),Offset(size.width,size.height*i/3)) }
        val path=Path();bins.forEachIndexed { i,b -> val x=(b.hz/(preview.sampleHz/2)*size.width).toFloat();val y=(size.height*(1-b.amplitude/max)).toFloat();if(i==0)path.moveTo(x,y)else path.lineTo(x,y) };drawPath(path,color,style=Stroke(width=2f))
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text("0 Hz");Text("${"%.0f".format(preview.sampleHz/4)} Hz");Text("${"%.0f".format(preview.sampleHz/2)} Hz · Nyquist") }
}

internal data class NpDisplayCalibration(val factory: NpPreviewCalibration?,val scale: Double,val units: String) {
    fun correct(value: Double,channel: Int,band: Int,filtered: Boolean)=(factory?.correct(value,channel,band,filtered)?:value)*scale
}

/** Small validated tables live in private app storage, never Firestore or hardware settings. */
private fun calibrationKey(device: String,probe: Int)=MessageDigest.getInstance("SHA-256").digest("$device/$probe".toByteArray()).joinToString("") { "%02x".format(it) }
private fun saveCalibration(context: Context,key: String,profile: NpPreviewCalibration) {
    val json=JSONObject().put("version",profile.version).put("serial",profile.serial).put("apGain",profile.apGain).put("lfpGain",profile.lfpGain)
        .put("ap",JSONArray(profile.apFactors)).put("lfp",JSONArray(profile.lfpFactors)).put("thresholds",JSONArray(profile.thresholds)).put("offsets",JSONArray(profile.offsets)).put("gainHash",profile.gainSha256).put("adcHash",profile.adcSha256)
    context.getSharedPreferences("np_preview_calibration",Context.MODE_PRIVATE).edit().putString(key,json.toString()).apply()
}
private fun loadCalibration(context: Context,key: String,version: Int): NpPreviewCalibration? = runCatching {
    val text=context.getSharedPreferences("np_preview_calibration",Context.MODE_PRIVATE).getString(key,null)?:return null
    require(text.length<65536)
    val j=JSONObject(text)
    fun doubles(name: String)=j.getJSONArray(name).let { a -> List(a.length()) { a.getDouble(it) } }
    fun ints(name: String)=j.getJSONArray(name).let { a -> List(a.length()) { a.getInt(it) } }
    NpPreviewCalibration(j.getInt("version"),j.getString("serial"),j.getInt("apGain"),j.getInt("lfpGain"),doubles("ap"),doubles("lfp"),ints("thresholds"),ints("offsets"),j.getString("gainHash"),j.getString("adcHash")).also {require(it.version==version);it.validate()}
}.getOrNull()

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NpCalibrationEditor(device: String,version: Int,probe: Int,band: Int,onChange:(NpDisplayCalibration)->Unit) {
    val context=LocalContext.current;val scope=rememberCoroutineScope();val key=remember(device,probe) {calibrationKey(device,probe)}
    var profile by remember(key,version) { mutableStateOf(loadCalibration(context,key,version)) }
    var serial by rememberSaveable(key,version) {mutableStateOf(profile?.serial?:"")}
    var apGain by rememberSaveable(key,version) {mutableStateOf((profile?.apGain?.takeIf {it>0}?:1000).toString())}
    var lfpGain by rememberSaveable(key,version) {mutableStateOf((profile?.lfpGain?.takeIf {it>0}?:50).toString())}
    var scale by rememberSaveable(key,version,band) {mutableStateOf("")}
    var useFactory by rememberSaveable(key,version) {mutableStateOf(false)}
    var useUv by rememberSaveable(key,version,band) {mutableStateOf(false)}
    var gainBytes by remember(key,version) {mutableStateOf<ByteArray?>(null)}
    var adcBytes by remember(key,version) {mutableStateOf<ByteArray?>(null)}
    var adcTarget by remember {mutableStateOf(false)}
    var error by remember(key,version) {mutableStateOf("")}
    var busy by remember {mutableStateOf(false)}
    val currentChange by rememberUpdatedState(onChange)
    val factor=scale.toDoubleOrNull()?.takeIf {it.isFinite() && it>0 && it<=100000}
    val matches=profile?.let {it.serial==serial.trim() && (version==2 || it.apGain==apGain.toIntOrNull() && it.lfpGain==lfpGain.toIntOrNull())}==true
    LaunchedEffect(profile,useFactory,useUv,factor,matches,serial) {
        currentChange(NpDisplayCalibration(if(useFactory && matches)profile else null,if(useUv && factor!=null && serial.toULongOrNull()?.let {it>0uL}==true)factor else 1.0,
            if(useUv && factor!=null && serial.toULongOrNull()?.let {it>0uL}==true)"µV (operator scale)" else if(useFactory && matches)"calibrated counts" else "counts"))
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) scope.launch {
            busy=true;error=""
            try {
                val bytes=withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { input ->
                    val out=ByteArrayOutputStream();val buffer=ByteArray(8192)
                    while(true) { val n=input.read(buffer);if(n<0)break;require(out.size()+n<=NpPreviewCalibration.maxBytes){"CSV exceeds 2 MiB."};out.write(buffer,0,n) };out.toByteArray()
                }?:error("Cannot open calibration CSV.") }
                if(adcTarget)adcBytes=bytes else gainBytes=bytes
            }catch(cancelled:CancellationException) {throw cancelled}
            catch(e:Exception) {error=e.message?:"Import failed."} finally {busy=false}
        }
    }
    HorizontalDivider();Text("P$probe · ${if(band==0)"AP" else "LFP"} display calibration",style=MaterialTheme.typography.titleMedium)
    Text("Bind to the physical probe serial and actual acquisition gains. This does not read probe identity or apply hardware trims. Saved factory tables require explicit confirmation; recheck after moving probes.")
    OutlinedTextField(value=serial,onValueChange={serial=it;useFactory=false;useUv=false},label={Text("Physical probe serial")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth())
    if(version==1) Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value=apGain,onValueChange={apGain=it;useFactory=false},label={Text("Actual AP gain")},singleLine=true,modifier=Modifier.weight(1f),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
        OutlinedTextField(value=lfpGain,onValueChange={lfpGain=it;useFactory=false},label={Text("Actual LFP gain")},singleLine=true,modifier=Modifier.weight(1f),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
    }
    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick={adcTarget=false;picker.launch(arrayOf("*/*"))},enabled=!busy,modifier=Modifier.heightIn(min=48.dp)) {Text(if(gainBytes==null)"Import factory gain CSV…" else "Gain CSV selected ✓")}
        if(version==1) OutlinedButton(onClick={adcTarget=true;picker.launch(arrayOf("*/*"))},enabled=!busy,modifier=Modifier.heightIn(min=48.dp)) {Text(if(adcBytes==null)"Optional ADC CSV…" else "ADC CSV selected ✓")}
        if(adcBytes!=null) TextButton(onClick={adcBytes=null}) {Text("Remove ADC file")}
        Button(onClick={
            try { val p=NpPreviewCalibration.parse(requireNotNull(gainBytes),adcBytes,version,serial,apGain.toIntOrNull()?:0,lfpGain.toIntOrNull()?:0);saveCalibration(context,key,p);profile=p;useFactory=true;error="" }
            catch(e:Exception) {error=e.message?:"Invalid calibration."}
        },enabled=!busy && gainBytes!=null,modifier=Modifier.heightIn(min=48.dp)) {Text("Validate and save factory correction")}
    }
    if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
    profile?.let { p -> Text("Saved NP${p.version} serial ${p.serial} · gain SHA ${p.gainSha256.take(12)}…${if(p.thresholds.isNotEmpty())" · digital ADC correction" else " · gain correction only"}") }
    Row { Checkbox(checked=useFactory && matches,onCheckedChange={useFactory=it},enabled=matches);Text("Confirm saved serial/gains and use factory correction") }
    if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
    OutlinedTextField(value=scale,onValueChange={scale=it;useUv=false},label={Text("µV per corrected ${if(band==0)"AP" else "LFP"} count")},supportingText={Text("Supply the scale for this probe/model, band and gain. No automatic voltage assumption.")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth())
    Row { Checkbox(checked=useUv,onCheckedChange={useUv=it},enabled=factor!=null && serial.toULongOrNull()?.let {it>0uL}==true);Text("Use my µV/count scale · display only") }
}
