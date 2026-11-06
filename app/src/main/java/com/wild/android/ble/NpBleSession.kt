package com.wild.android.ble

import java.io.IOException
import java.security.SecureRandom
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One connection owns one correlated request at a time. Mutations are never replayed. */
internal class NpBleSession(
    parentScope: CoroutineScope,
    private val write: suspend (ByteArray) -> Boolean,
    private val publish: (NpDeviceUiState) -> Unit,
    private val retire: (String) -> Unit = {},
    retainedMaps: List<NpProbeMap> = emptyList(),
    retainedClosedLoop: NpClosedLoopStatus? = null,
    retainedClosedLoopUncertain: Boolean = false,
) {
    private val lifetime = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + lifetime)
    private val control = Mutex()
    @Volatile private var pending: Pair<(ByteArray) -> Boolean, CompletableDeferred<ByteArray>>? = null
    private var pendingCommand = 0xB9
    private var sequence = System.currentTimeMillis() and 0xffffffffL
    @Volatile private var closed = false
    @Volatile private var ui = NpDeviceUiState(probeMaps=retainedMaps,closedLoop=retainedClosedLoop,
        closedLoopUncertain=retainedClosedLoopUncertain || retainedClosedLoop?.configured==true)
    private val uiGate = Any()
    private var clockVersion = 0
    private var clockCommitUncertain = false
    private var clockMappingMatchesPhone = false
    private var previewJob: Job? = null
    private var previewRequest: ByteArray? = null
    @Volatile private var previewInput: Channel<ByteArray>? = null
    private val awaitingProbeReceipts = java.util.ArrayDeque<Int>()
    private var expectedClosedLoop: ByteArray? = null
    private var closedLoopSettings: NpClosedLoopSettings? = null
    private val catalog = NpBleCatalog(::readStatus,::nextId,
        { command,q,matches -> exchange(NpBleProtocol.frame(command,q),matches=matches) },
        { page -> exchangeNative(byteArrayOf(0x3c,0x94.toByte(),page.toByte(),(page ushr 8).toByte(),0x3e)) },
        { nativeDeleteLast() }, { accept(exchange(it)) }, { message -> update { it.copy(message=message) } })
    val parser = NpBleFrameParser { command, body ->
        if (command == 0xBA) previewInput?.trySend(body)
        if (command == pendingCommand || pendingCommand == -1 && command in listOf(0x90,0x95)) pending?.let { (matches, receipt) ->
            try { if (matches(body)) receipt.complete(body) } catch (error: Exception) { receipt.completeExceptionally(error) }
        }
    }

    private fun update(change: (NpDeviceUiState) -> NpDeviceUiState) = synchronized(uiGate) {
        if (!closed) { ui = change(ui); publish(ui) }
    }
    private fun nextId(): Long {
        sequence = (maxOf(sequence, ui.status?.lastMutationId ?: 0L) + 1) and 0xffffffffL
        if (sequence == 0L || sequence == 1L) sequence = 2
        return sequence
    }
    fun connect() {
        scope.launch { refresh() }
        scope.launch {
            while (isActive) {
                delay(5_000)
                // Background reads yield to the user's command, rather than
                // building a queue in front of Stop.
                if (ui.status != null && control.tryLock()) try {
                    if (readStatus().isMedia) readMedia()
                    else if (awaitingProbeReceipts.isNotEmpty() && ui.status?.let { if(it.version==1)it.state in 1..4 else it.state in listOf(3,4,5,6,7,255) }==true) {
                        val port=awaitingProbeReceipts.first
                        readProbeReceipt(ui.status!!.version,port);awaitingProbeReceipts.removeFirst()
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) {
                    update { it.copy(message = "Status read failed; last report retained.", failure = error.message.orEmpty()) }
                } finally { control.unlock() }
            }
        }
    }
    private suspend fun exchange(frame: ByteArray, responseCommand: Int? = null, matches: (ByteArray) -> Boolean): ByteArray {
        if (closed) throw CancellationException("WILDX connection is closed.")
        val response = CompletableDeferred<ByteArray>()
        // The short 82 discovery request answers on B9, unlike normal controls.
        pendingCommand = responseCommand ?: when (frame[1].toInt() and 255) { 0x82 -> 0xB9; 0x33 -> 0x90; else -> frame[1].toInt() and 255 }
        pending = matches to response
        try {
            if (!write(frame)) throw IOException("WILDX command was not fully sent. Read status before another action.")
            return withTimeoutOrNull(5_000) { response.await() }
                ?: throw IOException("No matching WILDX reply. Outcome unknown; read status before retrying. No command was replayed.")
        } finally { if (pending?.second === response) pending = null }
    }
    private suspend fun exchange(request: ByteArray) = exchange(NpBleProtocol.frame(0xB9, request)) { NpBleProtocol.matches(request, it) }
    private fun accept(body: ByteArray): NpDeviceStatus {
        val status = NpBleProtocol.status(body) ?: throw IOException("Invalid WILDX status or unsupported firmware capabilities.")
        val previous=ui.status
        if (ui.status != null && status.version != ui.status!!.version) throw IOException("Device protocol changed; reconnect before controlling it.")
        update { it.copy(status = status, reportedAtMs = System.currentTimeMillis(), message = status.stateLabel,
            media = if (status.isMedia) it.media else null, mediaProfile = if (status.isMedia) it.mediaProfile else null) }
        if (ui.preview.enabled && (ui.preview.traces.any { !canPreview(status,it.selection.probe,ui.preview.band) } || status.sampleRate!=ui.preview.sourceHz ||
                previous?.let { it.startSector!=status.startSector || it.lastMutationId!=status.lastMutationId }==true)) endPreview("Recording/profile changed — preview stopped. Re-enable after checking status.")
        if (status.error != 0L) throw IOException("Device rejected operation ${status.operation}: error ${status.error}, fault ${status.fault}. Read status before trying again.")
        return status
    }
    private suspend fun readStatus(): NpDeviceStatus {
        val version = ui.status?.version ?: run {
            val discovered = NpBleProtocol.status(exchange(NpBleProtocol.discovery) {
                NpBleProtocol.status(it)?.let { s -> s.operation == 0L && s.requestId == 1L } == true
            }) ?: throw IOException("No supported Neuropixels device replied.")
            discovered.version
        }
        // The initial handshake only identifies the family. Always obtain a
        // second, fresh STATUS before enabling any controls.
        return accept(exchange(NpBleProtocol.request(version, 0, nextId())))
    }
    private suspend fun action(label: String, block: suspend () -> Unit) = control.withLock {
        update { it.copy(busy = true, message = label, failure = "") }
        try { block() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { update { it.copy(message = "Action not confirmed", failure = error.message ?: label) } }
        finally { update { it.copy(busy = false) } }
    }
    suspend fun refresh() = action("Reading Neuropixels status…") {
        val status = readStatus()
        if (status.version == 2 && ui.availableProbeMask == 0) {
            val id = nextId()
            val request = ByteArray(64)
            NpBleProtocol.put(request, 0, 0x3243504e)
            request[4] = 2; request[5] = 5
            NpBleProtocol.put(request, 8, id); NpBleProtocol.seal(request)
            val caps = exchange(NpBleProtocol.frame(0xB8, request)) {
                NpBleProtocol.checked(it) && NpBleProtocol.u32(it, 0) == 0x3252504eL && it[4] == 2.toByte() && it[5] == 5.toByte() &&
                    NpBleProtocol.u32(it, 8) == id && NpBleProtocol.u32(it, 16) == 876L
            }
            val mask = NpBleProtocol.u32(caps, 24).toInt()
            require(caps[6] == 0.toByte() && mask in listOf(3, 7)) { "Unsupported NP2 connector capabilities; only the current device profile is available." }
            val uid = (32..40 step 4).joinToString("") { "%08X".format(NpBleProtocol.u32(caps, it)) }
            update { it.copy(availableProbeMask = mask, deviceUid = uid) }
        }
    }
    suspend fun execute(command: NpCommand) {
        when (command.action) {
            NpAction.ReadStatus -> refresh()
            NpAction.ApplyProfile -> selectProfile(command.mask, command.rate)
            NpAction.ApplyMedia -> applyMedia(NpMediaProfile(command.cameraHz, command.microphone))
            NpAction.ReadMedia -> action("Reading media counters…") { require(readStatus().isMedia); readMediaProfile(); readMedia() }
            NpAction.Start -> start(command.seconds, command.acceptUnqualified)
            NpAction.Schedule -> schedule(command)
            NpAction.Stop -> stop()
            NpAction.ReadClock -> action("Reading clock without changing time…") { require(readStatus().has(256)); observeClock() }
            NpAction.SyncTime -> syncClock()
            NpAction.ReadImu -> readImu()
            NpAction.Mount, NpAction.Recover, NpAction.Format, NpAction.Overwrite -> storage(command)
            NpAction.UsbStreaming -> mode(2)
            NpAction.UsbDownload -> mode(4)
            NpAction.Loader -> mode(1)
            NpAction.Dfu -> mode(3)
            NpAction.RescueReset -> rescue(command.expectedFault)
            NpAction.StartPreview -> startPreview(command)
            NpAction.StopPreview -> action("Pausing display preview…") { stopPreviewCore() }
            NpAction.StageProbeMap -> action("Staging the requested probe map…") { stageProbeMap(requireNotNull(command.probeMap)) }
            NpAction.ReadProbeReceipts -> action("Reading probe configuration receipts…") {
                val s=readStatus();val ports=if(ui.probeMaps.isNotEmpty())ui.probeMaps.map { it.connector-1 } else (0..2).filter { s.probeMask and (1 shl it)!=0 }
                ports.forEach { readProbeReceipt(s.version,it) }
            }
            NpAction.ReadClosedLoop -> action("Reading closed-loop capabilities and status…") { readStatus();readClosedLoop(0);readClosedLoop(2) }
            NpAction.ConfigureClosedLoop -> action("Applying closed-loop setup…") { configureClosedLoop(requireNotNull(command.closedLoopSettings)) }
            NpAction.DisableClosedLoop -> action("Disabling closed-loop processing…") { configureClosedLoop(null) }
            NpAction.ReadCatalog -> action("Reading the recording catalog…") { stopPreviewCore();update { it.copy(catalog=null) };val result=catalog.refresh();update { it.copy(catalog=result) } }
            NpAction.DeleteLast -> action("Deleting the confirmed last recording…") {
                stopPreviewCore();update { it.copy(catalog=it.catalog?.copy(canDelete=false)) }
                val result=catalog.deleteLast(command.catalogFingerprint);update { it.copy(catalog=result,message="Last recording deleted; unchanged earlier entries verified.") }
            }
        }
    }
    suspend fun selectProfile(mask: Int, rate: Int) = action("Applying probe selection and rate…") {
        stopPreviewCore()
        val status = readStatus()
        require(status.canConfigure && NpBleProtocol.supportsProfile(status, mask, rate)) { "This profile is not available in the current device state." }
        val available = if (status.version == 1) 7 else ui.availableProbeMask.takeIf { it != 0 } ?: status.probeMask
        require(mask and available.inv() == 0) { "The device did not advertise all selected connectors." }
        val request = NpBleProtocol.request(status.version, 1, nextId(), mask.toLong(), rate.toLong())
        val ack = accept(exchange(request))
        if (!ack.has(8)) require(ack.probeMask == mask && ack.sampleRate == rate) { "Device did not confirm the requested recording profile." }
        if (mask != 0) update { it.copy(mediaProfile = null, media = null) }
    }
    suspend fun start(seconds: Int = 0, acceptUnqualified: Boolean = false) = action("Requesting recording…") {
        stopPreviewCore()
        val status = readStatus()
        prepareStart(status)
        if (status.isMedia) readMediaProfile()
        accept(exchange(NpBleProtocol.start(status, nextId(), seconds, acceptUnqualified, ui.mediaProfile)))
        readStatus() // Queued/Preparing is displayed as such, never as confirmed capture.
    }
    suspend fun stop() = action("Requesting recording stop…") {
        stopPreviewCore()
        val status = readStatus()
        if (!status.stopped) accept(exchange(NpBleProtocol.request(status.version, 3, nextId())))
        readStatus() // Poll continues until the device reports finalization complete.
    }
    suspend fun mode(destination: Int) = action("Requesting mode change…") {
        stopPreviewCore()
        val status = readStatus()
        val request = NpBleProtocol.mode(status, nextId(), destination)
        val ack = accept(exchange(request))
        require(ack.lastMutationId == NpBleProtocol.u32(request,12)) { "Mode handoff was not acknowledged; check status, no command replayed." }
        update { it.copy(message = "Mode handoff acknowledged. Refresh USB devices in the PC console; destination arrival has not been verified.") }
        retire("WILDX mode handoff acknowledged — continue using USB")
    }
    private suspend fun exchangeNative(frame: ByteArray): ByteArray = try {
        exchange(frame) { it.size == 512 }.also { require(it.size == 512) }
    } catch (error: IOException) {
        // 90 replies have no IDs/CRC. Never let a late native receipt satisfy
        // another action on this connection, including after a partial send.
        update { it.copy(failure = error.message.orEmpty(), message = "Native reply uncertain; reconnect before retrying.") }
        retire("Uncorrelated WILDX reply uncertain — reconnect required")
        throw error
    }
    private suspend fun nativeDeleteLast():Long {
        var deleted:Long?=null;var parameters=false
        try {
            exchange(byteArrayOf(0x3c,0x95.toByte(),1,0x3e),-1) { b ->
                if(b.size==4) deleted=NpBleProtocol.u32(b,0)
                if(b.size==512) parameters=true
                deleted!=null && parameters
            }
            return requireNotNull(deleted)
        } catch(error:Exception) {
            if(error is CancellationException) throw error
            retire("Native delete outcome uncertain — reconnect and refresh, no replay")
            throw error
        }
    }
    private suspend fun config(q:ByteArray):NpProbeReceipt {
        val r=exchange(NpBleProtocol.frame(0xB8,q)) { NpConfigWire.matches(q,it) }
        val receipt=NpConfigWire.parse(q,r)
        update { it.copy(probeReceipts=(it.probeReceipts.filterNot { old -> old.connector==receipt.connector }+receipt).sortedBy { r -> r.connector }) }
        require(receipt.error==0) { "Probe configuration rejected (${receipt.error}); no applied-state claim or command replay." }
        return receipt
    }
    private suspend fun readProbeReceipt(version:Int,port:Int)=config(NpConfigWire.request(version,0,port,nextId()))
    private suspend fun stageProbeMap(map:NpProbeMap) {
        stopPreviewCore()
        val s=readStatus()
        require(s.canConfigure && !s.isMedia && map.version==s.version && map.connector in 1..3 && map.payload.size==if(s.version==1)1576 else 876) { "Stop recording and use a matching-family probe map." }
        val port=map.connector-1
        require(map.payload[5].toInt()==port && map.payload[4].toInt()==map.model && map.payload.copyOfRange(0,4).contentEquals((if(s.version==1)"NPB1" else "N2B1").toByteArray())) { "Probe payload connector/family differs from the confirmed request." }
        if(s.version==2) {
            val q=NpConfigWire.request(2,5,port,nextId())
            val caps=exchange(NpBleProtocol.frame(0xB8,q)) { NpConfigWire.matches(q,it) }
            require(caps[6]==0.toByte() && NpBleProtocol.u32(caps,28)==4L && NpBleProtocol.u32(caps,24) and (1L shl port)!=0L) { "Firmware does not admit NP2013 programming on this connector." }
        }
        update { it.copy(probeMaps=(it.probeMaps.filterNot { old -> old.connector==map.connector }+map).sortedBy { m -> m.connector }) }
        val resident=readProbeReceipt(s.version,port)
        if(resident.matches(map) && resident.transferState in listOf(0,2) && resident.received==0L && resident.portState in listOf(2,3)) return
        val id=nextId()
        config(NpConfigWire.request(s.version,1,port,id,length=map.payload.size,crc=map.crc))
        for(offset in map.payload.indices step 32) {
            val data=map.payload.copyOfRange(offset,minOf(offset+32,map.payload.size))
            val ack=config(NpConfigWire.request(s.version,2,port,id,offset,data.size,if(s.version==2)map.crc else 0,data))
            require(ack.received==offset+data.size.toLong()) { "Probe upload offset mismatch; wait for transfer expiry and read status, no replay." }
            update { it.copy(message="P${map.connector}: uploaded ${offset+data.size}/${map.payload.size} bytes…") }
        }
        // COMMIT is delivered once. No automatic ABORT/COMMIT follows an
        // uncertain/partial write; read-only STATUS can recover resident proof.
        val receipt=config(NpConfigWire.request(s.version,3,port,id,crc=map.crc))
        require(receipt.matches(map) && receipt.portState==2) { "Map staging was not confirmed by matching ID/CRC." }
        update { it.copy(message="P${map.connector} map staged. Not applied until probe preparation; check receipts after Start.") }
    }
    private suspend fun readClosedLoop(op:Int):NpClosedLoopStatus {
        val q=NpClosedLoop.request(op,nextId());val raw=exchange(NpBleProtocol.frame(0xB9,q)) { NpClosedLoop.matches(q,it) }
        val reply=NpClosedLoop.parse(q,raw)
        // CAPS can describe capabilities without current configured flags.
        // It must never overwrite the last actual STATUS with an invented Off.
        update { if(op==0) it.copy(closedLoopCapabilities=reply) else it.copy(closedLoop=reply) }
        if(op==2 && reply.result==0L) {
            val expected=expectedClosedLoop
            if(!reply.configured || expected!=null && reply.configCrc==NpBleProtocol.u32(expected,60)) update { it.copy(closedLoopUncertain=false) }
        }
        return reply
    }
    private suspend fun configureClosedLoop(settings:NpClosedLoopSettings?) {
        stopPreviewCore();delay(2100) // Shared device workspace; age any old lease.
        val s=readStatus();require(s.canConfigure && s.fault==0L) { "Stop and wait for finalization before changing closed-loop setup." }
        require(settings==null || !ui.closedLoopUncertain) { "Previous setup is uncertain. Read status or explicitly Disable first; CONFIGURE is not replayed." }
        val caps=readClosedLoop(0);require(caps.supported) { "Closed-loop is unavailable on this firmware." }
        if(settings!=null) NpClosedLoop.validate(s,caps,settings)
        val q=NpClosedLoop.request(1,nextId(),if(settings==null)0 else s.sampleRate,settings)
        expectedClosedLoop=q;closedLoopSettings=settings
        update { it.copy(closedLoopUncertain=true) }
        val r=NpClosedLoop.parse(q,exchange(NpBleProtocol.frame(0xB9,q)) { NpClosedLoop.matches(q,it) })
        require(r.result==0L && r.configured==(settings!=null) && (settings==null || r.configCrc==NpBleProtocol.u32(q,60))) { "Closed-loop configure not confirmed; read status, no replay." }
        val fresh=readClosedLoop(2)
        require(fresh.result==0L && fresh.configured==(settings!=null) && (settings==null || fresh.configCrc==NpBleProtocol.u32(q,60) && fresh.hz==s.sampleRate)) { "Closed-loop readback differs; setup remains unconfirmed." }
        update { it.copy(closedLoopUncertain=false,message=if(settings==null)"Closed-loop disabled and read back." else "Closed-loop setup confirmed. Training/processing begins with the supported recording; analog performance is not verified.") }
    }
    private suspend fun prepareStart(s:NpDeviceStatus) {
        require(!ui.closedLoopUncertain) { "Resolve uncertain closed-loop setup before Start; read status or explicitly Disable." }
        if(ui.closedLoop?.configured==true || closedLoopSettings!=null) {
            val settings=closedLoopSettings?:error("Closed-loop was configured outside this phone's setup. Configure or Disable explicitly before Start.")
            val caps=readClosedLoop(0);NpClosedLoop.validate(s,caps,settings)
            val current=readClosedLoop(2)
            require(current.configured && current.configCrc==expectedClosedLoop?.let { NpBleProtocol.u32(it,60) }) { "Closed-loop setup changed; explicitly reapply before Start." }
        }
        if(!s.isMedia) ui.probeMaps.filter { s.probeMask and (1 shl (it.connector-1))!=0 }.forEach { stageProbeMap(it) }
        awaitingProbeReceipts.clear()
        if(!s.isMedia) ui.probeMaps.filter { s.probeMask and (1 shl (it.connector-1))!=0 }.forEach { awaitingProbeReceipts.add(it.connector-1) }
        catalog.invalidate();update { it.copy(catalog=it.catalog?.copy(canDelete=false,warning="Recording setup changed; refresh this last catalog report before deletion.")) }
    }
    private suspend fun readMediaProfile(): NpMediaProfile {
        val frame = byteArrayOf(0x3c,0x90.toByte(),0x3e)
        val profile = NpBleProtocol.mediaProfile(exchangeNative(frame))
        update { it.copy(mediaProfile = profile) }; return profile
    }
    private suspend fun readMedia() {
        if (ui.mediaProfile == null) readMediaProfile()
        val request = NpBleProtocol.mediaRequest()
        val body = exchange(NpBleProtocol.frame(0xB9, request)) { it.size == 64 && it.copyOfRange(0,8).contentEquals(request.copyOfRange(0,8)) }
        update { it.copy(media = NpBleProtocol.mediaStatus(body)) }
    }
    private suspend fun applyMedia(profile: NpMediaProfile) = action("Applying camera/microphone recording mode…") {
        stopPreviewCore()
        val status = readStatus()
        require(status.version == 1 && status.stopped && status.fault == 0L && status.has(0x200000)) { "Stopped NP1 with media support required." }
        val frame = NpBleProtocol.mediaCommand(profile)
        exchangeNative(frame) // The immediate native reply can precede reset.
        // A successful native reply is not proof that the mode transition is
        // finished. Poll fresh STATUS, then independently reread parameters.
        val deadline = System.nanoTime()+60_000_000_000
        var confirmed = false
        while (!confirmed && System.nanoTime()<deadline) {
            val current=readStatus()
            if (current.isMedia && current.stopped) confirmed=readMediaProfile()==profile
            if (!confirmed) delay(250)
        }
        require(confirmed) { "Media mode was not confirmed; recording not started." }
        update { it.copy(media = null, message = "Camera/microphone mode confirmed. Start records on the device SD card, not to the phone.") }
    }
    private suspend fun storage(command: NpCommand) = action("Running ${command.action.name.lowercase()} storage action…") {
        stopPreviewCore()
        catalog.invalidate();update { it.copy(catalog=it.catalog?.copy(canDelete=false)) }
        val before = readStatus()
        val request = NpBleProtocol.storage(before, nextId(), command.action, command.expectedCapacity, command.expectedStart, SecureRandom().nextLong().let { if (it == 0L) 1 else it })
        val id = NpBleProtocol.u32(request,12)
        var status = accept(exchange(request))
        require(status.lastMutationId == id) { "Storage mutation was not acknowledged. No command replayed." }
        repeat(40) { if (status.has(8)) { delay(250); status = readStatus(); require(status.lastMutationId == id) { "Another storage mutation occurred." } } }
        status = readStatus()
        require(status.lastMutationId == id && !status.busy && status.stopped && status.error == 0L && status.has(16) &&
            (before.capacitySectors == 0L && command.action == NpAction.Mount && status.capacitySectors > 8192 || status.capacitySectors == before.capacitySectors)) { "Storage completion not confirmed. Read status; no command replayed." }
        if (before.version == 2) require(status.state == 1 && status.fault == 0L)
        if (command.action == NpAction.Format) {
            require(status.startSector == 8192L) { "Formatted catalog frontier is not empty." }
            if (before.version == 1 && before.has(0x02000000)) require(status.has(0x04000000)) { "Requested filesystem2 layout was not confirmed." }
        }
        if (command.action == NpAction.Overwrite) require(status.startSector == command.expectedStart && !status.has(131072) && !status.has(0x04000000)) { "Overwrite boundary not confirmed." }
        update { it.copy(message = "${command.action} completed and verified by fresh device status. Recording was not started.") }
    }
    private suspend fun rescue(expectedFault: Long) = action("Sending explicitly confirmed rescue reset…") {
        val s = readStatus()
        require(s.version == 2 || s.version == 1 && s.stopped && s.state == 4 && s.fault != 0L && s.fault == expectedFault && s.has(4096)) { "NP1 rescue is only available for the confirmed quiescent failure." }
        val sent = write(byteArrayOf(0x3c,0xab.toByte(),0xba.toByte(),0x3e))
        update { it.copy(message = if (sent) "Rescue reset sent without a firmware acknowledgement. Reconnect and read status; recovery is not yet verified." else "Rescue delivery uncertain. Reconnect and read status; no retry sent.") }
        retire("WILDX rescue request — reconnect and check device status")
    }
    private fun clockNow(): () -> Long {
        val wall = System.currentTimeMillis() * 1000; val monotonic = System.nanoTime()
        return { wall + (System.nanoTime() - monotonic) / 1000 }
    }
    private suspend fun observeClock(): NpClockReply {
        val now=clockNow()
        fun acceptClock(q: ByteArray, raw: ByteArray, start: Long): NpClockReply {
            val finish=now(); val r=NpBleTelemetry.clock(raw,q)
            val (estimated,error)=NpBleTelemetry.timing(start,finish,r)
            val processing=((r.tx-r.rx) and 0xffffffffL)*1000
            clockMappingMatchesPhone=r.calibrated && kotlin.math.abs(estimated-r.utcUs)<=error+r.uncertaintyUs+processing+(if (r.hardware) 100 else 5000)
            return publishClock(r)
        }
        if (clockVersion == 0) {
            val probe = NpBleProtocol.clockRequest(0, nextId(), version = 2)
            // Only read-only capability probing falls back. Never fall back
            // after attempting a hardware RTC COMMIT.
            val start=now()
            val raw = try { exchange(probe) } catch (_: IOException) { null }
            if (raw!=null && NpBleProtocol.u32(raw,56) and 17L==17L) {
                clockVersion=2
                // Recognized hardware-provider rejections must surface, not
                // silently trigger a legacy clock mutation instead.
                return acceptClock(probe,raw,start)
            }
            clockVersion=1
        }
        val q = NpBleProtocol.clockRequest(0, nextId(), version = clockVersion)
        val start=now(); val raw=exchange(q)
        return acceptClock(q,raw,start)
    }
    private fun publishClock(r: NpClockReply): NpClockReply {
        update { it.copy(clock = r, clockMessage = "${if (r.hardware) "Hardware RTC" else "Device time mapping"}: ${if (r.calibrated) "calibrated" else "not calibrated"} · generation ${r.generation} · reported uncertainty ±${"%.1f".format(r.uncertaintyUs/1000.0)} ms") }
        return r
    }
    private suspend fun syncClockCore() {
        val status = readStatus()
        require(status.stopped && status.has(256)) { "Time synchronization requires a stopped device with clock support." }
        require(!clockCommitUncertain) { "Previous clock COMMIT is uncertain. Read the clock and investigate before another explicit synchronization; no commit replayed." }
        observeClock()
        val (reply, error) = NpBleTelemetry.synchronize(clockVersion, status.version, ::nextId, clockNow(), { exchange(it) }, { clockCommitUncertain = true })
        clockCommitUncertain = false; publishClock(reply)
        update { it.copy(clockMessage = "${if (reply.hardware) "Hardware RTC" else "Phone time"} confirmed · Bluetooth uncertainty ±${"%.1f".format(error/1000.0)} ms", message = "Device time synchronized; phone UTC accuracy is separate.") }
    }
    suspend fun syncClock() = action("Measuring Bluetooth clock delay…") { syncClockCore() }
    private suspend fun schedule(command: NpCommand) = action("Arming a scheduled recording…") {
        var status = readStatus()
        if (status.isMedia) readMediaProfile()
        // Validate the intent before doing even an optional clock write.
        NpBleProtocol.scheduled(status, nextId(), command.seconds, command.acceptUnqualified, command.utcMs, System.currentTimeMillis(),ui.mediaProfile)
        val clock = observeClock()
        require(clock.calibrated && clock.uncertaintyUs <= 500000 && clockMappingMatchesPhone && (clock.version==1 || clock.flags and 8L==0L)) { "Device time is unconfirmed, frozen or differs from this phone. Synchronize while stopped before arming a timed recording." }
        status = readStatus()
        prepareStart(status)
        val request = NpBleProtocol.scheduled(status, nextId(), command.seconds, command.acceptUnqualified, command.utcMs, System.currentTimeMillis(),ui.mediaProfile)
        accept(exchange(request)); readStatus()
    }
    private suspend fun readImu() = action("Reading a coherent sensor snapshot…") {
        val status = readStatus()
        require(status.version == 2 || status.has(0x10000000)) { "This firmware does not advertise BLE IMU snapshots." }
        val sample = NpBleTelemetry.imu(::nextId) { q -> exchange(NpBleProtocol.frame(0xB9,q)) { NpBleTelemetry.imuMatches(q,it) } }
        update { it.copy(imu = sample, message = "Sensor snapshot received. This is a sampled view, not the full-rate IMU recording.") }
    }
    private fun canPreview(s: NpDeviceStatus, probe: Int, band: Int) = s.has(128) && !s.has(8) && !s.isMedia &&
        s.state == (if (s.version == 1) 1 else 3) && probe in 1..(if (s.version == 1) 3 else 2) &&
        s.probeMask and (1 shl (probe-1)) != 0 && band in 0..(if (s.version == 1) 1 else 0)
    private fun endPreview(message: String) {
        previewJob?.cancel(); previewJob = null; previewInput?.close(); previewInput = null
        update { it.copy(preview = it.preview.copy(enabled = false,message = message)) }
    }
    private suspend fun stopPreviewCore() {
        val request = previewRequest; previewRequest = null
        if (previewJob == null && request == null) return
        endPreview("Preview paused. Recording is independent.")
        if (request != null) {
            val stop = if(NpBleProtocol.u32(request,8)==6L) NpMultiPreview.feedback(NpBleProtocol.u32(request,12),NpBleProtocol.u32(request,24),0,stop=true)
                else request.copyOf().also { NpBleProtocol.put(it,8,3); NpBleProtocol.seal(it) }
            // Expendable preview only: no acknowledgement; the two-second
            // firmware lease also expires when renewal stops.
            write(NpBleProtocol.frame(0xB9,stop))
        }
    }
    private suspend fun startPreview(command: NpCommand) = action("Validating probe framing for preview…") {
        stopPreviewCore()
        delay(2100) // Previous unacknowledged preview leases must expire.
        val status = readStatus()
        val selections=command.previewChannels.ifEmpty { listOf(NpPreviewSelection(command.probe,command.channel)) }.toList()
        require(selections.size in 1..(if(status.version==1)4 else 2) && selections.distinct().size==selections.size && selections.all { canPreview(status,it.probe,command.band) && it.channel in 0..383 }) { "Select distinct supported channels in an active neural SD recording. NP2 carries P1/P2 AP only; preview never starts acquisition." }
        require(status.version!=1 || selections.map { it.probe }.distinct().size==1) { "NP1 compact preview uses one probe and one band at a time." }
        val selection=selections.first()
        // CL and preview share the device workspace. An unavailable/unknown
        // query must not be interpreted as 'closed-loop off'.
        val cl = readClosedLoop(2)
        require(cl.result==0L && !cl.configured) { "Preview unavailable: closed-loop is configured or its state was not confirmed. Recording remains independent." }
        var capabilitySession=0L
        val multi=if(status.version==2) {
            val capsId=nextId(); val caps=exchangePreview(NpMultiPreview.capabilities(capsId)) { NpMultiPreview.matches(it,capsId) }
            NpMultiPreview.supported(caps,capsId).also { if(it) { capabilitySession=NpBleProtocol.u32(caps,8);require(NpBleProtocol.u32(caps,12)==status.sampleRate.toLong()) { "Preview source rate changed." } } }
        } else false
        val compact=status.version==1 && status.has(0x08000000)
        require(multi || compact || selections.size==1) { "This firmware supports one preview channel only. Select one trace or update the device firmware." }
        val originId = nextId(); val origin = NpBlePreview.Burst(status.version,originId)
        val originRequest = NpBlePreview.request(status.version,originId,0,selection.probe,selection.channel,band=command.band)
        // Preview replies arrive on BA, controls stay on B9. Assemble a whole
        // origin before phase search; never guess phase from a partial burst.
        exchangePreview(originRequest) { origin.accept(it) }
        require(origin.kind==0 && origin.probe==selection.probe && origin.channel==selection.channel && origin.band==command.band && origin.hz==status.sampleRate)
        require(!multi || origin.session==capabilitySession) { "Recording session changed during preview capability check." }
        val phases=selections.map { origin.phase(it.probe) }; val phase=phases.first()
        val fresh = readStatus()
        require(selections.all { canPreview(fresh,it.probe,command.band) } && fresh.probeMask==status.probeMask && fresh.sampleRate==status.sampleRate && fresh.lastMutationId==status.lastMutationId && fresh.startSector==status.startSector) { "Recording changed during preview calibration." }
        delay(550)
        val id = nextId()
        val input = Channel<ByteArray>(32,onBufferOverflow=BufferOverflow.DROP_OLDEST)
        val receiver=if(multi) NpMultiPreview.Receiver(id,origin.session,status.sampleRate,selections,phases) else null
        val request = when {
            multi -> NpMultiPreview.configure(id,origin.session,selections,phases)
            compact -> NpMultiPreview.compactRequest(id,5,selection.probe,selections.map { it.channel },phase,origin.session,command.band)
            else -> NpBlePreview.request(status.version,id,2,selection.probe,selection.channel,phase,origin.session,command.band)
        }
        previewInput=input; previewRequest=request
        update { it.copy(preview=NpPreviewUi(true,if(multi)"Filtered 500 Hz preview · useful band 0–200 Hz" else "Subsampled preview · aliasing possible",bits=if (status.version==1) 10 else 12,
            probe=selection.probe,channel=selection.channel,band=command.band,traces=selections.map { s -> NpPreviewTrace(s) },sourceHz=status.sampleRate,filtered=multi)) }
        if (!write(NpBleProtocol.frame(0xB9,request))) { previewRequest=null; endPreview("Preview send failed. Recording is independent."); return@action }
        previewJob=scope.launch {
            var previousEnd=0L; var lastData=System.nanoTime(); var renewed=System.nanoTime()
            try {
                while (isActive && ui.preview.enabled) {
                    val b=withTimeoutOrNull(150) { input.receive() }
                    val frames=if(b==null || NpBleProtocol.u32(b,4)!=id) emptyList() else when {
                        receiver!=null -> receiver.accept(b)
                        compact -> listOfNotNull(NpMultiPreview.compact(b,id,origin.session,status.sampleRate,selection.probe,selections.map { it.channel },command.band,phase))
                        else -> {
                            val burst=NpBlePreview.Burst(status.version,id)
                            if(!burst.accept(b)) emptyList() else {
                                require(burst.kind==2 && burst.session==origin.session && burst.hz==status.sampleRate && burst.probe==selection.probe && burst.channel==selection.channel && burst.band==command.band) { "Preview session/rate/selection changed." }
                                val midpoint=if(status.version==1)512.0 else 2048.0
                                listOf(NpMultiPreview.Frame(0,burst.first,burst.stride,0,listOf(burst.decode(phase).map { p -> p.copy(value=midpoint-p.value) })))
                            }
                        }
                    }
                    frames.forEach { frame ->
                        if(previousEnd==0L || frame.first>=previousEnd) {
                            val gap=previousEnd!=0L && frame.first!=previousEnd
                            update { current ->
                                val traces=current.preview.traces.mapIndexed { lane,trace ->
                                    val points=frame.traces[lane]
                                    trace.copy(points=(trace.points+(if(gap)listOf(NpPreviewPoint(points.first().tick,Double.NaN)) else emptyList())+points).takeLast(5000))
                                }
                                current.copy(preview=current.preview.copy(traces=traces,points=traces.first().points,stride=frame.stride,
                                    sampleHz=status.sampleRate*(if(status.version==1)780.0 else 1600.0)/frame.stride,
                                    updatedAtMs=System.currentTimeMillis(),gaps=current.preview.gaps+(if(gap || frame.traces.any { t -> t.any { it.value.isNaN() } })1 else 0)))
                            }
                            previousEnd=frame.first+frame.traces.first().size*frame.stride; lastData=System.nanoTime()
                        }
                    }
                    val now=System.nanoTime()
                    if (now-lastData>5_000_000_000) throw IOException("Preview data stopped. Read status and re-enable preview; recording is independent.")
                    if (now-renewed>(if(multi)150_000_000 else 750_000_000) && control.tryLock()) try {
                        val renew=receiver?.feedback(stalled=now-lastData>350_000_000) ?: request.copyOf().also { NpBleProtocol.put(it,8,4); NpBleProtocol.seal(it) }
                        if (!write(NpBleProtocol.frame(0xB9,renew))) throw IOException("Preview renewal failed; no control replayed.")
                        renewed=System.nanoTime()
                    } finally { control.unlock() }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { endPreview(error.message ?: "Preview unavailable; recording is independent.") }
        }
    }
    private suspend fun exchangePreview(request: ByteArray, matches: (ByteArray) -> Boolean): ByteArray {
        return exchange(NpBleProtocol.frame(0xB9,request),0xBA,matches)
    }
    fun close() { closed = true; pending?.second?.cancel(); pending = null; previewInput?.close(); previewInput=null; lifetime.cancel(); parser.reset() }
}
