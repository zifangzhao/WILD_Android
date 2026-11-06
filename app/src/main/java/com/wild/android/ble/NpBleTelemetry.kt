package com.wild.android.ble

import java.io.IOException
import kotlinx.coroutines.delay

data class NpClockReply(val version: Int, val rx: Long, val tx: Long, val generation: Long,
    val uncertaintyUs: Long, val valid: Boolean, val utcUs: Long, val state: Int, val flags: Long) {
    val hardware get() = version == 2 && flags and 17L == 17L
    val calibrated get() = valid && if (version == 2) flags and 4L != 0L else true
}

data class NpImuSnapshot(val reportedAtMs: Long, val valid: Long, val sequence: Long,
    val acceleration: List<Double>, val angularVelocity: List<Double>, val magneticField: List<Double>,
    val imuError: Long, val environmentError: Long)

/** Both NPT2 versions use the same magic but have different clock semantics. */
internal object NpBleTelemetry {
    fun clock(b: ByteArray, request: ByteArray): NpClockReply {
        require(NpBleProtocol.matches(request, b)) { "Clock receipt identity/CRC mismatch." }
        fun u(at: Int) = NpBleProtocol.u32(b, at)
        require(u(16) == 0L) { "Clock operation rejected: ${u(16)}." }
        val version = u(4).toInt(); val flags = u(56)
        val utc = u(40) or (u(44) shl 32); val valid = u(36) == 1L
        if (version == 2) {
            require(flags and 31L.inv() == 0L && u(36) in 0..1 && valid == (flags and 2L != 0L))
            require(u(52) == if (flags and 1L != 0L) 100L else 0L)
            require(if (valid) utc in 946684800000000L..4102444799999999L else utc == 0L)
            require(if (flags and 4L != 0L) flags and 19L == 19L && u(28) != 0L && u(32) <= 500000 else u(32) == 0L)
        } else require(version == 1 && u(52) == 1000L && u(36) in 0..1)
        return NpClockReply(version, u(20), u(24), u(28), u(32), valid, utc, u(48).toInt(), flags)
    }
    fun timing(start: Long, end: Long, r: NpClockReply): Pair<Long, Long> {
        val processing = ((r.tx - r.rx) and 0xffffffffL) * 1000
        val elapsed = end - start
        require(elapsed in 0..5_000_000 && processing <= elapsed + 2000) { "Invalid radio clock timing." }
        return start + (elapsed + processing) / 2 to (elapsed - processing).coerceAtLeast(0) / 2 + 1000
    }
    /** A single COMMIT; only read-only observations are repeated afterwards. */
    suspend fun synchronize(version: Int, family: Int, nextId: () -> Long, now: () -> Long,
        exchange: suspend (ByteArray) -> ByteArray, beforeCommit: () -> Unit): Pair<NpClockReply, Long> {
        fun admit(r: NpClockReply) {
            val stopped = if (version == 2 && family == 1) r.state in listOf(1, 5) else r.state in listOf(0, 1, 5, 7)
            require(stopped && (version == 1 || r.hardware && r.flags and 8L == 0L)) { "Clock sync requires a stopped, unfrozen clock provider." }
        }
        var bestError = Long.MAX_VALUE; var bestUtc = 0L; var bestTx = 0L; var lastTx = 0L
        var generation: Long? = null; var firstTx = 0L
        var lower = Long.MIN_VALUE; var upper = Long.MAX_VALUE
        repeat(if (version == 2) 8 else 16) { index ->
            val q = NpBleProtocol.clockRequest(0, nextId(), version = version)
            val start = now(); val r = clock(exchange(q), q); val end = now(); admit(r)
            if (generation == null) { generation = r.generation; firstTx = r.tx }
            require(r.generation == generation && ((r.tx - lastTx) and 0xffffffffL).let { index == 0 || it < 0x80000000L }) { "Clock lifetime/generation changed." }
            val (utc, error) = timing(start, end, r)
            if (version == 2) {
                val elapsed = (r.tx - firstTx) and 0xffffffffL
                require(elapsed < 0x80000000L)
                val atFirst = utc - elapsed * 1000
                lower = maxOf(lower, atFirst - error); upper = minOf(upper, atFirst + error)
                require(lower <= upper) { "Phone and device clocks are not continuous." }
            }
            if (error < bestError) { bestError = error; bestUtc = utc; bestTx = r.tx }
            lastTx = r.tx
        }
        require(bestError <= 500000) { "Radio delay is too uncertain; no clock commit sent." }
        val delta = (lastTx - bestTx) and 0xffffffffL; require(delta < 0x80000000L)
        val q = NpBleProtocol.clockRequest(1, nextId(), lastTx, bestUtc + delta * 1000, bestError, generation!!, version)
        beforeCommit()
        val begin = now(); val ack = clock(exchange(q), q); val end = now(); admit(ack)
        require(ack.generation != 0L && ack.generation != generation) { "Clock generation did not advance." }
        if (version == 1) {
            val expected=((generation!!+1) and 0xffffffffL).let { if (it==0L) 1 else it }
            val (estimated, error) = timing(begin, end, ack)
            require(ack.calibrated && ack.generation==expected && ack.uncertaintyUs == bestError && kotlin.math.abs(estimated - ack.utcUs) <= bestError + error + 5000) { "Clock anchor failed its readback offset check." }
            return ack to bestError
        }
        repeat(13) { index ->
            if (index > 0) delay(200)
            val read = NpBleProtocol.clockRequest(0, nextId(), version = 2)
            val start = now(); val r = clock(exchange(read), read); val finish = now(); admit(r)
            require(r.generation == ack.generation) { "RTC generation changed during phase completion." }
            if (r.calibrated) {
                val (estimated, error) = timing(start, finish, r)
                val processing = ((r.tx - r.rx) and 0xffffffffL) * 1000
                require(r.uncertaintyUs == bestError && estimated - processing - error - bestError - 100 <= r.utcUs &&
                    estimated + error + bestError + 100 >= r.utcUs) { "Hardware RTC phase was not confirmed." }
                return r to bestError
            }
        }
        throw IOException("RTC phase completion is unconfirmed; COMMIT was not replayed.")
    }
    fun imuRequest(id: Long, token: Long, offset: Int): ByteArray {
        require(id in 1..0xffffffffL && offset in 0 until 140 && offset % 28 == 0 && (offset == 0) == (token == 0L))
        val b = ByteArray(64)
        listOf(0x5153504eL, 1L, if (offset == 0) 0L else 1L, id, token, offset.toLong()).forEachIndexed { i, v -> NpBleProtocol.put(b, i * 4, v) }
        return NpBleProtocol.seal(b)
    }
    fun imuMatches(q: ByteArray, b: ByteArray) = b.size == 64 && NpBleProtocol.checked(b) &&
        NpBleProtocol.u32(b,0) == 0x5253504eL && (4..12 step 4).all { NpBleProtocol.u32(b,it) == NpBleProtocol.u32(q,it) } && NpBleProtocol.u32(b,24) == NpBleProtocol.u32(q,20)
    suspend fun imu(nextId: () -> Long, exchange: suspend (ByteArray) -> ByteArray): NpImuSnapshot {
        val snapshot = ByteArray(140); var token = 0L
        for (offset in 0 until 140 step 28) {
            val q = imuRequest(nextId(), token, offset); val r = exchange(q)
            require(imuMatches(q,r) && NpBleProtocol.u32(r,16) == 0L) { "IMU view unavailable; no complete coherent snapshot." }
            val actual = NpBleProtocol.u32(r,20)
            require(actual != 0L && (token == 0L || token == actual) && NpBleProtocol.u32(r,28) == 140L) { "IMU snapshot identity changed." }
            token = actual; r.copyInto(snapshot, offset, 32, 60)
        }
        fun u(at: Int) = NpBleProtocol.u32(snapshot, at)
        require(u(0) == 0x31534e53L && u(4) == 1L)
        var valid = u(12); val observed = u(16)
        if (u(20) == 0L || ((observed-u(32)) and 0xffffffffL) > 100) valid = valid and 1L.inv()
        if (u(24) == 0L || ((observed-u(36)) and 0xffffffffL) > 200) valid = valid and 2L.inv()
        if (u(28) == 0L || ((observed-u(40)) and 0xffffffffL) > 3000) valid = valid and 4L.inv()
        fun axes(at: Int, scale: Double) = (0..2).map { u(at + it * 4).toInt() / scale }
        return NpImuSnapshot(System.currentTimeMillis(), valid, u(20), axes(48,1e6), axes(60,1e3), axes(72,1e3), u(104), u(108))
    }
}
