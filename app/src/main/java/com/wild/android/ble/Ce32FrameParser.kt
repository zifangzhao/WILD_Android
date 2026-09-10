package com.wild.android.ble

class Ce32FrameParser(
    private val onOutOfFrameBytes: (ByteArray) -> Unit = {},
    /** Resolves dynamic-length CE64 replies (currently scheduler response 0xD8). */
    private val payloadLengthResolver: (commandId: Int) -> Int? = { null },
    private val onFrame: (commandId: Int, payload: ByteArray) -> Unit,
) {
    private enum class ParseMode {
        Idle,
        FramedCommandId,
        FramedPayload,
        FramedTerminator,
        RawCommandId,
        RawPayload,
    }

    private var mode = ParseMode.Idle
    private var commandId = -1
    private var expectedPayloadLength = -1
    private val payload = ArrayList<Byte>(512)
    private val outOfFrameBytes = ArrayList<Byte>(64)

    fun push(chunk: ByteArray) {
        for (byte in chunk) {
            consume(byte)
        }
        flushOutOfFrameBytes()
    }

    fun reset() {
        resetParserState()
        outOfFrameBytes.clear()
    }

    private fun resetParserState() {
        mode = ParseMode.Idle
        commandId = -1
        expectedPayloadLength = -1
        payload.clear()
    }

    private fun flushOutOfFrameBytes() {
        if (outOfFrameBytes.isEmpty()) {
            return
        }
        onOutOfFrameBytes(outOfFrameBytes.toByteArray())
        outOfFrameBytes.clear()
    }

    private fun consume(byte: Byte) {
        val value = byte.toInt() and 0xFF

        when (mode) {
            ParseMode.Idle -> {
                when (value) {
                    0x3C -> {
                        flushOutOfFrameBytes()
                        startFramedPacket()
                    }

                    // Desktop CE32 transport uses 0xAD as a raw inbound lead byte,
                    // followed by the real command id and a fixed payload without 0x3E.
                    0xAD -> {
                        flushOutOfFrameBytes()
                        startRawPacket()
                    }

                    else -> {
                        outOfFrameBytes += byte
                    }
                }
            }

            ParseMode.FramedCommandId -> {
                if (!beginCommand(value, ParseMode.FramedPayload, ParseMode.FramedTerminator)) {
                    restartFromPotentialLeadByte(value)
                }
            }

            ParseMode.FramedPayload -> {
                if (!appendPayload(byte)) return
                if (payload.size >= expectedPayloadLength) {
                    mode = ParseMode.FramedTerminator
                }
            }

            ParseMode.FramedTerminator -> {
                if (value == 0x3E) {
                    emitFrame()
                } else {
                    restartFromPotentialLeadByte(value)
                }
            }

            ParseMode.RawCommandId -> {
                if (!beginCommand(value, ParseMode.RawPayload, ParseMode.RawPayload)) {
                    restartFromPotentialLeadByte(value)
                } else if (expectedPayloadLength == 0) {
                    emitFrame()
                }
            }

            ParseMode.RawPayload -> {
                if (!appendPayload(byte)) return
                if (payload.size >= expectedPayloadLength) {
                    emitFrame()
                }
            }
        }
    }

    private fun startFramedPacket() {
        resetParserState()
        mode = ParseMode.FramedCommandId
    }

    private fun startRawPacket() {
        resetParserState()
        mode = ParseMode.RawCommandId
    }

    private fun beginCommand(
        value: Int,
        payloadMode: ParseMode,
        zeroPayloadMode: ParseMode,
    ): Boolean {
        commandId = value
        // D8 is self-describing: the first byte echoes D0/D1/.../DB. Never
        // derive its size from the request currently waiting in the host.
        // Late replies can arrive after a timeout or during a different read.
        expectedPayloadLength = if (value == Ce32Protocol.SchedulerResponse) {
            Int.MAX_VALUE
        } else {
            payloadLengthResolver(value) ?: Ce32Protocol.payloadLengthFor(value) ?: -1
        }
        if (expectedPayloadLength < 0) {
            resetParserState()
            return false
        }

        mode = if (expectedPayloadLength == 0) {
            zeroPayloadMode
        } else {
            payloadMode
        }
        return true
    }

    private fun appendPayload(byte: Byte): Boolean {
        if (commandId == Ce32Protocol.SchedulerResponse && payload.isEmpty()) {
            expectedPayloadLength = Ce32Protocol.schedulerResponsePayloadLengthFor(byte.toInt() and 0xFF)
                ?: run { resetParserState(); return false }
        }
        payload += byte
        return true
    }

    private fun emitFrame() {
        val emittedCommandId = commandId
        val emittedPayload = payload.toByteArray()
        resetParserState()
        onFrame(emittedCommandId, emittedPayload)
    }

    private fun restartFromPotentialLeadByte(value: Int) {
        resetParserState()
        when (value) {
            0x3C -> startFramedPacket()
            0xAD -> startRawPacket()
            else -> outOfFrameBytes += value.toByte()
        }
    }
}
