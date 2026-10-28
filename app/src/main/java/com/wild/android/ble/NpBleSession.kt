package com.wild.android.ble

import android.os.SystemClock
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One connection owns one correlated request at a time. Mutations are never replayed. */
internal class NpBleSession(
    parentScope: CoroutineScope,
    private val write: suspend (ByteArray) -> Boolean,
    private val publish: (NpDeviceUiState) -> Unit,
) {
    private val lifetime = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + lifetime)
    private val control = Mutex()
    @Volatile private var pending: Pair<(ByteArray) -> Boolean, CompletableDeferred<ByteArray>>? = null
    private var pendingCommand = 0xB9
    private var sequence = System.currentTimeMillis() and 0xffffffffL
    @Volatile private var closed = false
    private var ui = NpDeviceUiState()
    val parser = NpBleFrameParser { command, body -> if (command == pendingCommand) pending?.let { (matches, receipt) -> if (matches(body)) receipt.complete(body) } }

    private fun update(next: NpDeviceUiState) { if (!closed) { ui = next; publish(next) } }
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
                    readStatus()
                } catch (error: IOException) {
                    update(ui.copy(message = "Status read failed; last report retained.", failure = error.message.orEmpty()))
                } finally { control.unlock() }
            }
        }
    }
    private suspend fun exchange(frame: ByteArray, matches: (ByteArray) -> Boolean): ByteArray {
        if (closed) throw CancellationException("WILDX connection is closed.")
        val response = CompletableDeferred<ByteArray>()
        // The short 82 discovery request answers on B9, unlike normal controls.
        pendingCommand = if (frame[1] == 0x82.toByte()) 0xB9 else frame[1].toInt() and 255
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
        if (ui.status != null && status.version != ui.status!!.version) throw IOException("Device protocol changed; reconnect before controlling it.")
        update(ui.copy(status = status, reportedAtMs = System.currentTimeMillis(), message = status.stateLabel))
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
        update(ui.copy(busy = true, message = label, failure = ""))
        try { block() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { update(ui.copy(message = "Action not confirmed", failure = error.message ?: label)) }
        finally { update(ui.copy(busy = false)) }
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
            update(ui.copy(availableProbeMask = mask, deviceUid = uid))
        }
    }
    suspend fun selectProfile(mask: Int, rate: Int) = action("Applying probe selection and rate…") {
        val status = readStatus()
        require(status.canConfigure && NpBleProtocol.supportsProfile(status, mask, rate)) { "This profile is not available in the current device state." }
        val available = if (status.version == 1) 7 else ui.availableProbeMask.takeIf { it != 0 } ?: status.probeMask
        require(mask and available.inv() == 0) { "The device did not advertise all selected connectors." }
        val request = NpBleProtocol.request(status.version, 1, nextId(), mask.toLong(), rate.toLong())
        val ack = accept(exchange(request))
        if (!ack.has(8)) require(ack.probeMask == mask && ack.sampleRate == rate) { "Device did not confirm the requested recording profile." }
    }
    suspend fun start(seconds: Int = 0, acceptUnqualified: Boolean = false) = action("Requesting recording…") {
        val status = readStatus()
        accept(exchange(NpBleProtocol.start(status, nextId(), seconds, acceptUnqualified)))
        readStatus() // Queued/Preparing is displayed as such, never as confirmed capture.
    }
    suspend fun stop() = action("Requesting recording stop…") {
        val status = readStatus()
        if (!status.stopped) accept(exchange(NpBleProtocol.request(status.version, 3, nextId())))
        readStatus() // Poll continues until the device reports finalization complete.
    }
    suspend fun mount() = action("Mounting the SD catalog…") {
        val status = readStatus()
        require(status.stopped && status.has(16384)) { "Mount requires a stopped device with storage controls." }
        val request = NpBleProtocol.request(status.version, if (status.version == 2) 6 else 8, nextId())
        accept(exchange(request)); readStatus()
    }
    suspend fun mode(destination: Int) = action("Requesting mode change…") {
        val status = readStatus()
        require(status.stopped && status.fault == 0L && status.has(8192) && destination in 1..3) { "Stop recording before changing device mode." }
        // V1 NLD1 destinations: loader1, streaming2, DFU3; V2 uses the same
        // visible destinations but a different operation number.
        val request = NpBleProtocol.request(status.version, if (status.version == 2) 5 else 7, nextId(), destination.toLong())
        accept(exchange(request))
        update(ui.copy(message = "Mode request received; the BLE link may close. Check the destination using USB and the PC console."))
    }
    suspend fun syncClock() = action("Measuring Bluetooth clock delay…") {
        val status = readStatus()
        require(status.stopped && status.has(256)) { "Time synchronization requires a stopped device with clock support." }
        val wall = System.currentTimeMillis() * 1000
        val anchor = SystemClock.elapsedRealtimeNanos()
        fun now() = wall + (SystemClock.elapsedRealtimeNanos() - anchor) / 1000
        var bestError = Long.MAX_VALUE
        var bestUtc = 0L
        var bestTx = 0L
        var lastTx = 0L
        var generation: Long? = null
        repeat(16) {
            val request = NpBleProtocol.clockRequest(0, nextId())
            val start = now(); val response = exchange(request); val end = now()
            require(NpBleProtocol.u32(response, 16) == 0L && NpBleProtocol.u32(response, 52) == 1000L) { "Device clock observation rejected." }
            val currentGeneration = NpBleProtocol.u32(response, 28)
            if (generation == null) generation = currentGeneration
            require(currentGeneration == generation) { "Clock generation changed; repeat time sync." }
            val rx = NpBleProtocol.u32(response, 20); lastTx = NpBleProtocol.u32(response, 24)
            val processing = ((lastTx - rx) and 0xffffffffL) * 1000
            val elapsed = end - start
            require(elapsed in 0..5_000_000 && processing <= elapsed + 2000) { "Invalid Bluetooth clock timing." }
            val uncertainty = (elapsed - processing).coerceAtLeast(0) / 2 + 1000
            if (uncertainty < bestError) { bestError = uncertainty; bestUtc = start + (elapsed + processing) / 2; bestTx = lastTx }
        }
        require(bestError <= 500_000) { "Bluetooth delay is too uncertain. Try time sync again." }
        val utc = bestUtc + ((lastTx - bestTx) and 0xffffffffL) * 1000
        val response = exchange(NpBleProtocol.clockRequest(1, nextId(), lastTx, utc, bestError, generation!!))
        val expectedGeneration = ((generation!! + 1) and 0xffffffffL).let { if (it == 0L) 1 else it }
        require(NpBleProtocol.u32(response, 16) == 0L && NpBleProtocol.u32(response, 36) == 1L &&
            NpBleProtocol.u32(response, 28) == expectedGeneration && NpBleProtocol.u32(response, 32) == bestError) { "Device did not confirm the phone-time anchor." }
        update(ui.copy(clockMessage = "Phone time confirmed · Bluetooth uncertainty ±${"%.1f".format(bestError / 1000.0)} ms", message = "Device time synchronized"))
    }
    fun close() { closed = true; pending?.second?.cancel(); pending = null; lifetime.cancel(); parser.reset() }
}
