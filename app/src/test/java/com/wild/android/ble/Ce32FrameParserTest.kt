package com.wild.android.ble

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Ce32FrameParserTest {
    @Test
    fun parserEmitsFrameAcrossMultipleChunks() {
        val frames = mutableListOf<Pair<Int, ByteArray>>()
        val parser = Ce32FrameParser { commandId, payload ->
            frames += commandId to payload
        }

        parser.push(byteArrayOf(0x3C, 0x42))
        parser.push(byteArrayOf(0x85.toByte()))
        parser.push(byteArrayOf(0x3E))

        assertEquals(1, frames.size)
        assertEquals(0x42, frames.single().first)
        assertContentEquals(byteArrayOf(0x85.toByte()), frames.single().second)
    }

    @Test
    fun parserEmitsBackToBackFramesFromSingleChunk() {
        val frames = mutableListOf<Pair<Int, ByteArray>>()
        val parser = Ce32FrameParser { commandId, payload ->
            frames += commandId to payload
        }

        parser.push(byteArrayOf(0x3C, 0x40, 0x3E, 0x3C, 0x41, 0x3E))

        assertEquals(2, frames.size)
        assertEquals(listOf(0x40, 0x41), frames.map { it.first })
        assertTrueAllPayloadsEmpty(frames)
    }

    @Test
    fun parserDropsUnknownCommandAndResyncsAtNextFrame() {
        val frames = mutableListOf<Pair<Int, ByteArray>>()
        val parser = Ce32FrameParser { commandId, payload ->
            frames += commandId to payload
        }

        parser.push(byteArrayOf(0x3C, 0xFF.toByte(), 0x3C, 0x41, 0x3E))

        assertEquals(1, frames.size)
        assertEquals(0x41, frames.single().first)
        assertContentEquals(byteArrayOf(), frames.single().second)
    }

    @Test
    fun parserDropsMalformedFrameAndRecoversOnNextStartByte() {
        val frames = mutableListOf<Pair<Int, ByteArray>>()
        val parser = Ce32FrameParser { commandId, payload ->
            frames += commandId to payload
        }

        parser.push(byteArrayOf(0x3C, 0x42, 0x01, 0x00, 0x3C, 0x40, 0x3E))

        assertEquals(1, frames.size)
        assertEquals(0x40, frames.single().first)
        assertContentEquals(byteArrayOf(), frames.single().second)
    }

    @Test
    fun resetClearsPartialFrameState() {
        val frames = mutableListOf<Pair<Int, ByteArray>>()
        val parser = Ce32FrameParser { commandId, payload ->
            frames += commandId to payload
        }

        parser.push(byteArrayOf(0x3C, 0x42))
        parser.reset()
        parser.push(byteArrayOf(0x01, 0x3E, 0x3C, 0x40, 0x3E))

        assertEquals(1, frames.size)
        assertEquals(0x40, frames.single().first)
        assertContentEquals(byteArrayOf(), frames.single().second)
    }

    @Test
    fun parserSurfacesLegacyRawLeadBytesBeforeFramedPayload() {
        val frames = mutableListOf<Pair<Int, ByteArray>>()
        val raw = mutableListOf<ByteArray>()
        val parser = Ce32FrameParser(
            onFrame = { commandId, payload -> frames += commandId to payload },
            onOutOfFrameBytes = { raw += it },
        )

        parser.push(byteArrayOf(0x80.toByte(), 0x3C, 0x40, 0x3E))

        assertEquals(1, raw.size)
        assertContentEquals(byteArrayOf(0x80.toByte()), raw.single())
        assertEquals(1, frames.size)
        assertEquals(0x40, frames.single().first)
        assertContentEquals(byteArrayOf(), frames.single().second)
    }

    @Test
    fun parserFlushesRawBytesEvenWhenNoFrameStarts() {
        val raw = mutableListOf<ByteArray>()
        val parser = Ce32FrameParser(
            onFrame = { _, _ -> },
            onOutOfFrameBytes = { raw += it },
        )
        val message = byteArrayOf(0x80.toByte(), 0x81.toByte(), 0x7F)

        parser.push(message)

        assertEquals(1, raw.size)
        assertContentEquals(message, raw.single())
    }

    @Test
    fun parserEmitsRawInboundPreviewPacketAcrossMultipleChunks() {
        val frames = mutableListOf<Pair<Int, ByteArray>>()
        val parser = Ce32FrameParser { commandId, payload ->
            frames += commandId to payload
        }
        val payload = ByteArray(136) { index -> index.toByte() }

        parser.push(byteArrayOf(0xAD.toByte()))
        parser.push(byteArrayOf(0xAD.toByte()))
        parser.push(payload.copyOfRange(0, 40))
        parser.push(payload.copyOfRange(40, payload.size))

        assertEquals(1, frames.size)
        assertEquals(0xAD, frames.single().first)
        assertContentEquals(payload, frames.single().second)
    }

    @Test
    fun parserEmitsRawInboundZeroPayloadAcknowledgement() {
        val frames = mutableListOf<Pair<Int, ByteArray>>()
        val parser = Ce32FrameParser { commandId, payload ->
            frames += commandId to payload
        }

        parser.push(byteArrayOf(0xAD.toByte(), 0x40))

        assertEquals(1, frames.size)
        assertEquals(0x40, frames.single().first)
        assertContentEquals(byteArrayOf(), frames.single().second)
    }

    @Test
    fun parserEmitsRawCe64AiRuntimeStatusAcrossChunks() {
        val frames = mutableListOf<Pair<Int, ByteArray>>()
        val parser = Ce32FrameParser { commandId, payload ->
            frames += commandId to payload
        }
        val payload = ByteArray(26) { index -> (index + 1).toByte() }

        parser.push(byteArrayOf(0xAD.toByte(), 0x99.toByte()))
        parser.push(payload.copyOfRange(0, 9))
        parser.push(payload.copyOfRange(9, payload.size))

        assertEquals(1, frames.size)
        assertEquals(0x99, frames.single().first)
        assertContentEquals(payload, frames.single().second)
    }

    @Test
    fun parserUsesSessionResolverForVariableLengthSchedulerReplies() {
        val frames = mutableListOf<Pair<Int, ByteArray>>()
        val payload = ByteArray(33) { it.toByte() }
        val parser = Ce32FrameParser(
            payloadLengthResolver = { commandId ->
                if (commandId == Ce32Protocol.SchedulerResponse) 33 else null
            },
            onFrame = { commandId, received -> frames += commandId to received },
        )

        parser.push(byteArrayOf(0xAD.toByte(), Ce32Protocol.SchedulerResponse.toByte()))
        parser.push(payload.copyOfRange(0, 7))
        parser.push(payload.copyOfRange(7, payload.size))

        assertEquals(1, frames.size)
        assertEquals(Ce32Protocol.SchedulerResponse, frames.single().first)
        assertContentEquals(payload, frames.single().second)
    }

    private fun assertTrueAllPayloadsEmpty(frames: List<Pair<Int, ByteArray>>) {
        frames.forEach { (_, payload) ->
            assertContentEquals(byteArrayOf(), payload)
        }
    }
}
