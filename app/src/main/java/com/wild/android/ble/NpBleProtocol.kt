package com.wild.android.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

/** WILDX NLD1/NLD2 controls; deliberately separate from CE64's B9 OTA command. */
data class NpDeviceStatus(
    val version: Int, val operation: Long, val requestId: Long, val error: Long,
    val state: Int, val fault: Long, val probeMask: Int, val sampleRate: Int,
    val chunks: Long, val startSector: Long, val endSector: Long, val capacitySectors: Long,
    val flags: Long, val lastMutationId: Long,
) {
    fun has(bits: Long) = flags and bits == bits
    val busy get() = flags and 12 != 0L
    val stopped get() = !busy && if (version == 2) state in listOf(0, 1, 5, 7, 255) else state in listOf(0, 3, 4) && flags and (131072L or 262144L) == 0L
    val recording get() = if (version == 2) state in 2..4 || state == 6 || has(4) else state in 1..2 || flags and (131072L or 262144L) != 0L || has(4)
    val canConfigure get() = !busy && if (version == 2) state in listOf(1, 5, 7) || state == 0 && has(16384) else stopped && has(1)
    val canStart get() = stopped && if (version == 2) {
        fault == 0L && (state == 1 || has(512) && state in listOf(5, 7)) && flags and (65536L or 131072L or 0x04000000L) == 0L &&
            NpBleProtocol.supportsProfile(this, probeMask, sampleRate)
    } else has(17) && !has(0x01000000) && startSector >= 8192 && capacitySectors > startSector && NpBleProtocol.supportsProfile(this, probeMask, sampleRate)
    val recordedSeconds get() = if (sampleRate <= 0 || probeMask == 0) 0L else if (version == 2) chunks * NpBleProtocol.profileTicks(probeMask) / (sampleRate.toLong() * 1600) else chunks * 61440 / (sampleRate.toLong() * 780 * Integer.bitCount(probeMask))
    val recordedMiB get() = if (version == 2) chunks * (46592.0 / 1048576) else chunks / 16.0
    val stateLabel get() = when {
        has(8) -> "Command queued — awaiting device confirmation"
        version == 2 -> when (state) {
            0 -> "SD catalog not mounted"; 1 -> "Ready"; 2 -> "Preparing probes"; 3 -> "Recording"
            4 -> "Saving recording — keep device powered"; 5 -> "Recording complete"; 6 -> "Waiting for scheduled start"
            7 -> "Cancelled before capture"; else -> if (stopped) "Stopped after recording error" else "Recording error — finalizing"
        }
        has(131072) -> "Waiting for scheduled start"
        has(262144) -> "Preparing probes"
        state == 1 -> "Recording"
        state == 2 -> "Saving recording — keep device powered"
        state == 4 || fault != 0L -> "Stopped after recording error"
        !has(16) -> "SD catalog not mounted"
        state == 3 -> "Recording complete"
        else -> "Ready"
    }
}

data class NpDeviceUiState(
    val status: NpDeviceStatus? = null, val reportedAtMs: Long = 0,
    val busy: Boolean = false, val message: String = "Connect to read WILDX capabilities.",
    val failure: String = "", val clockMessage: String = "Device clock not read",
    val availableProbeMask: Int = 0, val deviceUid: String = "",
)

enum class NpAction { ReadStatus, ApplyProfile, Start, Stop, SyncTime, Mount, UsbStreaming, Loader, Dfu }

internal object NpBleProtocol {
    val rates = listOf(7500, 15000, 20000, 30000)
    val discovery = byteArrayOf(0x3C, 0x82.toByte(), 0x81.toByte(), 0, 0, 0x3E)
    fun matchesName(name: String) = listOf("WILD-X", "WILDX", "XENP", "XE_NP").any { name.trim().startsWith(it, true) }
    fun u32(data: ByteArray, offset: Int) = ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffffffffL
    fun put(data: ByteArray, offset: Int, value: Long) { ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(value.toInt()) }
    fun crc(data: ByteArray, count: Int = data.size - 4) = CRC32().apply { update(data, 0, count) }.value
    fun checked(data: ByteArray) = data.size >= 4 && u32(data, data.size - 4) == crc(data)
    fun seal(data: ByteArray): ByteArray { put(data, data.size - 4, crc(data)); return data }
    fun frame(command: Int, body: ByteArray): ByteArray {
        require(body.size == 64)
        return byteArrayOf(0x3C, command.toByte()) + body + byteArrayOf(0x3E)
    }
    fun request(version: Int, operation: Int, id: Long, vararg fields: Long): ByteArray {
        require(version in 1..2 && operation in 0..12 && id in 1..0xffffffffL && fields.size <= 11)
        val b = ByteArray(64)
        listOf(if (version == 1) 0x31444c4eL else 0x32444c4eL, version.toLong(), operation.toLong(), id).plus(fields.toList())
            .forEachIndexed { index, word -> require(word in 0..0xffffffffL); put(b, index * 4, word) }
        return seal(b)
    }
    fun status(body: ByteArray): NpDeviceStatus? {
        if (body.size != 64 || !checked(body)) return null
        val version = when (u32(body, 0)) { 0x31524c4eL -> 1; 0x32524c4eL -> 2; else -> return null }
        if (u32(body, 4) != version.toLong() || u32(body, 12) == 0L) return null
        val s = NpDeviceStatus(version, u32(body, 8), u32(body, 12), u32(body, 16),
            u32(body, 20).toInt(), u32(body, 24), u32(body, 28).toInt(), u32(body, 32).toInt(),
            u32(body, 36), u32(body, 40), u32(body, 44), u32(body, 48), u32(body, 52), u32(body, 56))
        if (version == 2 && (profileTicks(s.probeMask) == 0L || s.sampleRate !in rates || s.state !in listOf(0,1,2,3,4,5,6,7,255) || !s.has(128))) return null
        if (version == 1 && (s.state !in 0..4 || s.probeMask !in 0..7)) return null
        return s
    }
    fun matches(request: ByteArray, reply: ByteArray): Boolean {
        if (request.size != 64 || reply.size != 64 || !checked(reply)) return false
        val replyMagic = when (u32(request, 0)) { 0x31444c4eL -> 0x31524c4eL; 0x32444c4eL -> 0x32524c4eL; 0x3254504eL -> 0x3252504eL; else -> return false }
        return u32(reply, 0) == replyMagic && (4..12 step 4).all { u32(reply, it) == u32(request, it) }
    }
    fun profileTicks(mask: Int): Long = when (mask) { 1 -> 122880; 3 -> 61440; 7 -> 40960; else -> 0 }
    fun supportsProfile(s: NpDeviceStatus, mask: Int, hz: Int): Boolean {
        if (hz !in rates || mask !in 1..7) return false
        if (s.version == 2) return profileTicks(mask) != 0L && (hz != 7500 || s.has(0x00100000))
        val count = Integer.bitCount(mask)
        return !(count == 3 && hz > 15000 || count == 2 && hz >= 20000 && mask != 3) &&
            (hz != 20000 || s.has(0x00100000) || s.has(0x00080000) && mask == 3)
    }
    fun start(s: NpDeviceStatus, id: Long, seconds: Int, acceptUnqualified: Boolean): ByteArray {
        require(s.canStart) { "Recorder is not ready. Read status and resolve SD or profile errors first." }
        require(seconds in 0..3600 && (seconds != 0 || s.has(512))) { "Choose 1–3600 seconds, or continuous on supported firmware." }
        val payload = ((s.startSector + 8192 + 16383) / 16384) * 16384
        if (s.version == 2) {
            val available = (((s.capacitySectors / 16384) * 16384 - payload).coerceAtLeast(0) / 364) * 4
            val blocks = if (seconds == 0) 0 else seconds.toLong() * s.sampleRate * 1600 / profileTicks(s.probeMask) / 4 * 4
            require(available >= 4 && (seconds == 0 || blocks in 4..available)) { "Not enough unreserved SD space for this duration." }
            return request(2, 2, id, s.probeMask.toLong(), s.sampleRate.toLong(), blocks)
        }
        require(s.has(2) || acceptUnqualified) { "Confirm use of a card/profile without reported validation." }
        require(!(s.probeMask == 3 && s.sampleRate == 30000) || acceptUnqualified) { "Two-probe 30 kHz needs explicit confirmation; sustained recording failures have been observed." }
        val bytes = seconds.toLong() * s.sampleRate * 832 * Integer.bitCount(s.probeMask)
        val end = if (seconds == 0) s.capacitySectors / 16384 * 16384 else payload + (bytes + 8388607) / 8388608 * 16384
        require(end > payload && end <= s.capacitySectors) { "Not enough SD space for this duration." }
        return request(1, 2, id, s.probeMask.toLong(), s.sampleRate.toLong(), s.startSector, end,
            (System.currentTimeMillis() / 1000).coerceAtLeast(1), if (s.has(2)) 0 else 1)
    }
    fun clockRequest(operation: Int, id: Long, reference: Long = 0, utcUs: Long = 0, uncertaintyUs: Long = 0, generation: Long = 0): ByteArray {
        require(operation in 0..1 && id in 1..0xffffffffL)
        val b = ByteArray(64)
        listOf(0x3254504eL, 1, operation.toLong(), id, reference, utcUs and 0xffffffffL, utcUs ushr 32, uncertaintyUs, generation)
            .forEachIndexed { i, v -> put(b, i * 4, v) }
        return seal(b)
    }
}

/** Fixed-length frames with validated tails and CRC. Handles arbitrary GATT fragmentation. */
internal class NpBleFrameParser(private val onFrame: (Int, ByteArray) -> Unit) {
    private val pending = ArrayList<Byte>(256)
    fun reset() = pending.clear()
    fun push(data: ByteArray) {
        for (byte in data) {
            pending.add(byte)
            while (pending.size >= 2) {
                val lead = pending[0].toInt() and 255
                val command = pending[1].toInt() and 255
                val length = when (command) { 0xB8, 0xB9 -> 64; 0xBA -> 224; 0xBC -> 192; else -> 0 }
                if (lead !in listOf(0xAD, 0x3C) || length == 0) { pending.removeAt(0); continue }
                val size = length + if (lead == 0xAD) 4 else 3
                if (pending.size < size) {
                    // A dropped preview fragment must not hold a later Stop/
                    // status receipt. Only a complete CRC-valid control can
                    // supersede the incomplete disposable preview.
                    val controlAt = if (command == 0xBA) (2 until pending.size - 66).firstOrNull { at ->
                        val start = pending[at].toInt() and 255
                        val cmd = pending[at + 1].toInt() and 255
                        val end = at + if (start == 0xAD) 68 else 67
                        start in listOf(0xAD, 0x3C) && cmd in listOf(0xB8, 0xB9) && end <= pending.size &&
                            (if (start == 0xAD) pending[end - 2] == 0xDA.toByte() && pending[end - 1] == 0xDA.toByte() else pending[end - 1] == 0x3E.toByte()) &&
                            NpBleProtocol.checked(pending.subList(at + 2, at + 66).toByteArray())
                    } else null
                    if (controlAt != null) { pending.subList(0, controlAt).clear(); continue }
                    break
                }
                val tail = if (lead == 0xAD) pending[size - 2] == 0xDA.toByte() && pending[size - 1] == 0xDA.toByte() else pending[size - 1] == 0x3E.toByte()
                val body = pending.subList(2, length + 2).toByteArray()
                if (!tail || command != 0xBA && !NpBleProtocol.checked(body)) { pending.removeAt(0); continue }
                pending.subList(0, size).clear()
                onFrame(command, body)
            }
        }
    }
}
