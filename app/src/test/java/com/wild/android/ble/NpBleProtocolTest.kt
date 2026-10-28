package com.wild.android.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import kotlin.test.*
import kotlinx.coroutines.*
class NpBleProtocolTest {
    private fun put(b: ByteArray, at: Int, value: Long) { ByteBuffer.wrap(b, at, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(value.toInt()) }
    private fun seal(b: ByteArray): ByteArray { put(b, b.size - 4, CRC32().apply { update(b, 0, b.size - 4) }.value); return b }
    private fun reply(version: Int = 2, op: Long = 0, id: Long = 9, state: Int = 1, mask: Int = 3, hz: Int = 20000, flags: Long = 17L or 128L or 256L or 512L or 8192L or 16384L or 1048576L, error: Long = 0): ByteArray {
        val b = ByteArray(64)
        listOf(if (version == 2) 0x32524c4eL else 0x31524c4eL, version.toLong(), op, id, error, state.toLong(), 0,
            mask.toLong(), hz.toLong(), 100, 8192, 0, 500_000_000, flags, 7)
            .forEachIndexed { i, v -> put(b, i * 4, v) }
        return seal(b)
    }
    private fun frame(body: ByteArray, raw: Boolean = true, command: Int = 0xB9) =
        byteArrayOf(if (raw) 0xAD.toByte() else 0x3C, command.toByte()) + body + if (raw) byteArrayOf(0xDA.toByte(), 0xDA.toByte()) else byteArrayOf(0x3E)

    @Test fun namesDoNotConfuseCe64WithNeuropixels() {
        for (name in listOf("WILD-X_A123", "WILDX-1", "XENP_01", "xe_np_2")) {
            assertTrue(NpBleProtocol.matchesName(name)); assertTrue(Ce32Protocol.matchesKnownNamePrefix(name))
        }
        assertFalse(NpBleProtocol.matchesName("CE64X_CCE02F232276"))
        assertFalse(NpBleProtocol.matchesName("WILD_CE128"))
    }
    @Test fun statusAndStartMatchTheConsoleWireLayout() {
        val status = assertNotNull(NpBleProtocol.status(reply()))
        assertEquals(2, status.version); assertEquals(20000, status.sampleRate)
        assertTrue(status.canStart)
        val start = NpBleProtocol.start(status, 10, 10, false)
        assertEquals(0x32444c4eL, NpBleProtocol.u32(start, 0))
        assertEquals(2L, NpBleProtocol.u32(start, 8))
        assertEquals(3L, NpBleProtocol.u32(start, 16))
        assertEquals(20000L, NpBleProtocol.u32(start, 20))
        assertEquals(5208L, NpBleProtocol.u32(start, 24))
        assertEquals(CRC32().apply { update(start, 0, 60) }.value, NpBleProtocol.u32(start, 60))
        assertContentEquals(byteArrayOf(0x3C, 0x82.toByte(), 0x81.toByte(), 0, 0, 0x3E), NpBleProtocol.discovery)
    }
    @Test fun rejectsBadCrcWrongFamilyAndOldTransaction() {
        assertNull(NpBleProtocol.status(reply().also { it[24] = 1 }))
        assertNull(NpBleProtocol.status(seal(reply().also { put(it, 0, 0x12345678) })))
        assertNull(NpBleProtocol.status(reply(mask = 5)))
        assertFalse(NpBleProtocol.matches(NpBleProtocol.request(2, 0, 10), reply(id = 9)))
        assertFalse(NpBleProtocol.matches(NpBleProtocol.request(1, 0, 9), reply()))
        assertTrue(NpBleProtocol.matches(NpBleProtocol.request(2, 0, 9), reply()))
    }
    @Test fun profilesAndRecordingAdmissionFollowDeviceCapabilities() {
        val modern = NpBleProtocol.status(reply())!!
        assertTrue(NpBleProtocol.supportsProfile(modern, 7, 7500))
        assertFalse(NpBleProtocol.supportsProfile(modern.copy(flags = 17L or 128L), 7, 7500))
        assertFalse(NpBleProtocol.supportsProfile(modern, 5, 20000))
        assertFailsWith<IllegalArgumentException> { NpBleProtocol.start(modern.copy(flags = modern.flags or 65536), 10, 10, false) }
        assertFailsWith<IllegalArgumentException> { NpBleProtocol.start(modern.copy(capacitySectors = 16384), 10, 10, false) }
        assertFailsWith<IllegalArgumentException> { NpBleProtocol.start(modern, 10, -1, false) }
        val v1 = NpBleProtocol.status(reply(version = 1, state = 0, flags = modern.flags and 2L.inv()))!!
        assertFailsWith<IllegalArgumentException> { NpBleProtocol.start(v1, 10, 10, false) }
        val start = NpBleProtocol.start(v1, 10, 10, true)
        assertEquals(8192L, NpBleProtocol.u32(start, 24))
        assertEquals(1L, NpBleProtocol.u32(start, 36))
        assertFalse(NpBleProtocol.supportsProfile(v1, 7, 30000))
    }
    @Test fun finalizationIsNotReportedAsStopped() {
        for ((version, saving, complete) in listOf(Triple(1, 2, 3), Triple(2, 4, 5))) {
            val s = NpBleProtocol.status(reply(version = version, state = saving))!!
            assertFalse(s.stopped); assertTrue(s.recording)
            assertTrue(NpBleProtocol.status(reply(version = version, state = complete))!!.stopped)
        }
        assertTrue(NpBleProtocol.status(reply(state = 2))!!.stateLabel.contains("Preparing"))
        assertFalse(NpBleProtocol.status(reply(flags = 17L or 128L or 8L, op = 1))!!.recording, "A queued profile change is not a recording")
    }
    @Test fun parserHandlesEveryFragmentBoundaryAndEmbeddedDelimiter() {
        val body = seal(reply().also { put(it, 36, 0xDA3C3EAD) })
        for (raw in listOf(false, true)) {
            val wire = frame(body, raw)
            for (split in 0..wire.size) {
                val received = mutableListOf<ByteArray>()
                val parser = NpBleFrameParser { _, p -> received.add(p) }
                parser.push(wire.copyOfRange(0, split)); parser.push(wire.copyOfRange(split, wire.size))
                assertEquals(1, received.size); assertContentEquals(body, received.single())
            }
        }
        val received = mutableListOf<ByteArray>()
        NpBleFrameParser { _, p -> received.add(p) }.push(frame(body).also { it[it.lastIndex] = 0 } + frame(body.also { it[0] = 0 }) + frame(reply()))
        assertEquals(1, received.size)
    }
    @Test fun simulatedConnectionConfirmsStateAndDoesNotReplayMutations() = runBlocking {
        var deviceState = 1
        var mask = 3; var hz = 20000
        var published = NpDeviceUiState()
        val mutations = mutableListOf<Long>()
        lateinit var session: NpBleSession
        session = NpBleSession(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), write = { bytes ->
            val command = bytes[1].toInt() and 255
            val body = if (command == 0x82) reply(id = 1) else if (command == 0xB8) {
                val request = bytes.copyOfRange(2, 66)
                val caps = ByteArray(64); put(caps, 0, 0x3252504e); caps[4] = 2; caps[5] = 5
                put(caps, 8, NpBleProtocol.u32(request, 8)); put(caps, 16, 876); put(caps, 24, 7); seal(caps)
            } else {
                val request = bytes.copyOfRange(2, 66)
                val op = NpBleProtocol.u32(request, 8); val id = NpBleProtocol.u32(request, 12)
                if (op != 0L) mutations.add(op)
                when (op) { 1L -> { mask = NpBleProtocol.u32(request, 16).toInt(); hz = NpBleProtocol.u32(request, 20).toInt() }; 2L -> deviceState = 2; 3L -> deviceState = 4 }
                // A delayed reply with the wrong ID must not finish this request.
                session.parser.push(frame(reply(op = op, id = 1, state = deviceState, mask = mask, hz = hz)))
                reply(op = op, id = id, state = deviceState, mask = mask, hz = hz)
            }
            session.parser.push(frame(body, command = if (command == 0xB8) 0xB8 else 0xB9)); true
        }, publish = { published = it })
        try {
            session.refresh(); assertEquals("", published.failure); assertEquals(2, published.status!!.version); assertEquals(7, published.availableProbeMask)
            session.selectProfile(1, 15000); assertEquals(1, published.status!!.probeMask)
            session.start(10); assertEquals("Preparing probes", published.status!!.stateLabel)
            session.stop(); assertFalse(published.status!!.stopped)
            deviceState = 5; session.refresh(); assertTrue(published.status!!.stopped)
            assertEquals(listOf(1L, 2L, 3L), mutations)
        } finally { session.close() }
    }

    @Test fun truncatedPreviewCannotHoldACompleteControlReceipt() {
        val received = mutableListOf<Int>()
        val parser = NpBleFrameParser { command, _ -> received.add(command) }
        parser.push(byteArrayOf(0xAD.toByte(), 0xBA.toByte()) + ByteArray(50))
        parser.push(frame(reply()))
        assertEquals(listOf(0xB9), received)
    }
}
