package com.wild.android.ble

/**
 * CE64 Bootloader V3 BLE-OTA package and wire helpers.
 *
 * The phone accepts the same single fused Intel-HEX firmware file as the PC
 * console. It extracts the V3 manifest at 0x08010000 and the application at
 * 0x08020000, validates both, and builds the internal 512-byte BLE blocks.
 */
internal data class Ce64BleOtaPackage(
    val manifest: ByteArray,
    val paddedImage: ByteArray,
    val generation: Long,
    val imageBytes: Int,
    val imageCrc32: Long,
) {
    val blockCount: Int
        get() = paddedImage.size / SectorBytes

    fun blockAt(index: Int): ByteArray {
        require(index in 0 until blockCount) { "OTA block index is outside the package." }
        val offset = index * SectorBytes
        return paddedImage.copyOfRange(offset, offset + SectorBytes)
    }

    companion object {
        const val ManifestBytes = 128
        const val SectorBytes = 512
        const val ApplicationBase = 0x0802_0000L
        const val ApplicationLimit = 0x0814_0000L
        const val ManifestAddress = 0x0801_0000L
        const val MaxFusedHexBytes = 8 * 1024 * 1024

        private const val ManifestMagic = 0x3346_4E4D_3436_4543L
        private const val ManifestFormat = 3L
        private const val ManifestStateValid = 0x444C_4156L
        private const val ManifestImageSystem = 1L

        /** Extracts the application OTA payload from a single fused CE64 `.hex` image. */
        fun fromFusedHex(hexBytes: ByteArray): Ce64BleOtaPackage {
            require(hexBytes.isNotEmpty() && hexBytes.size <= MaxFusedHexBytes) {
                "The fused CE64 firmware file is empty or exceeds the supported size limit."
            }
            val manifest = ByteArray(ManifestBytes)
            val manifestPresent = BooleanArray(ManifestBytes)
            val application = ByteArray((ApplicationLimit - ApplicationBase).toInt()) { 0xFF.toByte() }
            val text = hexBytes.decodeToString()
            var linearBase = 0L
            var segmentBase = 0L
            var sawData = false

            text.lineSequence().forEachIndexed { lineNumber, rawLine ->
                val line = rawLine.trim().removePrefix("\uFEFF")
                if (line.isEmpty()) {
                    return@forEachIndexed
                }
                require(line.startsWith(':')) { "Invalid Intel HEX record at line ${lineNumber + 1}." }
                val count = hexByte(line, 1, lineNumber)
                val offset = hexU16(line, 3, lineNumber)
                val type = hexByte(line, 7, lineNumber)
                val expectedLength = 11 + count * 2
                require(line.length == expectedLength) { "Invalid Intel HEX record length at line ${lineNumber + 1}." }
                val data = ByteArray(count) { index -> hexByte(line, 9 + index * 2, lineNumber).toByte() }
                val checksum = hexByte(line, 9 + count * 2, lineNumber)
                var sum = count + (offset ushr 8) + (offset and 0xFF) + type + checksum
                data.forEach { sum += it.toInt() and 0xFF }
                require(sum and 0xFF == 0) { "Invalid Intel HEX checksum at line ${lineNumber + 1}." }

                when (type) {
                    0x00 -> {
                        val base = if (linearBase != 0L) linearBase shl 16 else segmentBase shl 4
                        data.forEachIndexed { index, value ->
                            val address = base + offset + index
                            if (address in ManifestAddress until ManifestAddress + ManifestBytes) {
                                val manifestIndex = (address - ManifestAddress).toInt()
                                manifest[manifestIndex] = value
                                manifestPresent[manifestIndex] = true
                            }
                            if (address in ApplicationBase until ApplicationLimit) {
                                application[(address - ApplicationBase).toInt()] = value
                            }
                        }
                        sawData = true
                    }

                    0x01 -> Unit // EOF
                    0x02 -> {
                        require(data.size == 2) { "Invalid Intel HEX segment address at line ${lineNumber + 1}." }
                        segmentBase = ((data[0].toLong() and 0xFFL) shl 8) or (data[1].toLong() and 0xFFL)
                        linearBase = 0L
                    }

                    0x04 -> {
                        require(data.size == 2) { "Invalid Intel HEX linear address at line ${lineNumber + 1}." }
                        linearBase = ((data[0].toLong() and 0xFFL) shl 8) or (data[1].toLong() and 0xFFL)
                        segmentBase = 0L
                    }

                    0x03, 0x05 -> Unit // Start-address metadata is not payload.
                    else -> throw IllegalArgumentException("Unsupported Intel HEX record type 0x${type.toString(16).uppercase()} at line ${lineNumber + 1}.")
                }
            }

            require(sawData) { "The fused firmware file does not contain Intel HEX data records." }
            require(manifestPresent.all { it }) { "The fused firmware is missing bytes from the CE64 application manifest at 0x08010000." }

            val generation = u32(manifest, 24)
            val imageBytes = validateManifest(manifest)
            val imageCrc32 = u32(manifest, 36)
            val image = application.copyOfRange(0, imageBytes)
            require(Ce64BleOtaProtocol.stm32Crc32(image) == imageCrc32) {
                "The fused firmware application CRC does not match its manifest."
            }
            val paddedBytes = ((imageBytes + SectorBytes - 1) / SectorBytes) * SectorBytes
            val paddedImage = ByteArray(paddedBytes) { 0xFF.toByte() }
            image.copyInto(paddedImage)
            return Ce64BleOtaPackage(manifest, paddedImage, generation, imageBytes, imageCrc32)
        }

        private fun validateManifest(manifest: ByteArray): Int {
            require(manifest.size == ManifestBytes &&
                u64(manifest, 0) == ManifestMagic &&
                u32(manifest, 8) == ManifestFormat &&
                u32(manifest, 12) == ManifestBytes.toLong() &&
                u32(manifest, 16) == ManifestStateValid &&
                u32(manifest, 20) == ManifestImageSystem &&
                u32(manifest, 28) == ApplicationBase) {
                "The fused firmware does not contain a valid CE64 V3 system application manifest."
            }
            val generation = u32(manifest, 24)
            val imageBytes = u32(manifest, 32).toInt()
            val stack = u32(manifest, 40)
            val reset = u32(manifest, 44)
            require(generation != 0L && imageBytes >= 8 && imageBytes.toLong() <= ApplicationLimit - ApplicationBase &&
                vectorsAreValid(stack, reset)) {
                "The fused firmware manifest has invalid generation, image bounds, or application vectors."
            }
            require(Ce64BleOtaProtocol.stm32Crc32(manifest, 0, ManifestBytes - 4) == u32(manifest, 124)) {
                "The fused firmware manifest CRC is invalid."
            }
            return imageBytes
        }

        private fun vectorsAreValid(stack: Long, reset: Long): Boolean {
            val resetAddress = reset and 0xFFFF_FFFEL
            return stack in 0x2000_0000L..0x2008_0000L && (stack and 7L) == 0L &&
                (reset and 1L) != 0L && resetAddress in ApplicationBase until ApplicationLimit
        }

        private fun u32(data: ByteArray, offset: Int): Long {
            require(offset >= 0 && offset + 4 <= data.size)
            return (data[offset].toLong() and 0xFFL) or
                ((data[offset + 1].toLong() and 0xFFL) shl 8) or
                ((data[offset + 2].toLong() and 0xFFL) shl 16) or
                ((data[offset + 3].toLong() and 0xFFL) shl 24)
        }

        private fun u64(data: ByteArray, offset: Int): Long =
            u32(data, offset) or (u32(data, offset + 4) shl 32)

        private fun hexByte(line: String, index: Int, lineNumber: Int): Int {
            require(index >= 0 && index + 2 <= line.length) { "Truncated Intel HEX record at line ${lineNumber + 1}." }
            return line.substring(index, index + 2).toIntOrNull(16)
                ?: throw IllegalArgumentException("Invalid hexadecimal data at line ${lineNumber + 1}.")
        }

        private fun hexU16(line: String, index: Int, lineNumber: Int): Int {
            require(index >= 0 && index + 4 <= line.length) { "Truncated Intel HEX record at line ${lineNumber + 1}." }
            return line.substring(index, index + 4).toIntOrNull(16)
                ?: throw IllegalArgumentException("Invalid hexadecimal data at line ${lineNumber + 1}.")
        }
    }
}

internal data class Ce64BleOtaReply(
    val commandId: Int,
    val result: Int,
    val state: Int,
    val generation: Long,
    val value: Long,
    val imageCrc32: Long,
    val detail: Int,
) {
    val isSuccess: Boolean
        get() = result == ResultOk

    companion object {
        const val ResultOk = 0
        const val ResultBusy = 2
        const val StateEmpty = 0
        const val StateReceiving = 1
        const val StateReady = 2
        const val StateInvalid = 3
    }
}

internal object Ce64BleOtaProtocol {
    const val CommandBegin = 0xB4
    const val CommandWrite = 0xB5
    const val CommandFinish = 0xB6
    const val CommandStatus = 0xB7
    const val CommandInstall = 0xB9
    const val ReplyPayloadBytes = 16

    fun buildBeginCommand(packageInfo: Ce64BleOtaPackage): ByteArray {
        val payload = ByteArray(3 + Ce64BleOtaPackage.ManifestBytes)
        payload[0] = 'O'.code.toByte()
        payload[1] = 'T'.code.toByte()
        payload[2] = 'A'.code.toByte()
        packageInfo.manifest.copyInto(payload, destinationOffset = 3)
        return frame(CommandBegin, payload)
    }

    fun buildWriteCommand(packageInfo: Ce64BleOtaPackage, blockIndex: Int): ByteArray {
        val block = packageInfo.blockAt(blockIndex)
        val payload = ByteArray(4 + 4 + Ce64BleOtaPackage.SectorBytes + 4)
        putU32(payload, 0, packageInfo.generation)
        putU32(payload, 4, blockIndex.toLong())
        block.copyInto(payload, destinationOffset = 8)
        putU32(payload, 8 + Ce64BleOtaPackage.SectorBytes, chunkCrc32(packageInfo.generation, blockIndex.toLong(), block))
        return frame(CommandWrite, payload)
    }

    fun buildFinishCommand(generation: Long): ByteArray =
        frame(CommandFinish, ByteArray(4).also { putU32(it, 0, generation) })

    fun buildStatusCommand(): ByteArray = frame(CommandStatus, byteArrayOf())

    fun buildInstallCommand(nonce: Int): ByteArray =
        frame(CommandInstall, byteArrayOf('G'.code.toByte(), 'O'.code.toByte(), nonce.toByte()))

    fun parseReply(commandId: Int, payload: ByteArray): Ce64BleOtaReply? {
        if (payload.size != ReplyPayloadBytes) {
            return null
        }
        return Ce64BleOtaReply(
            commandId = commandId,
            result = payload[0].toInt() and 0xFF,
            state = payload[1].toInt() and 0xFF,
            generation = u32(payload, 2),
            value = u32(payload, 6),
            imageCrc32 = u32(payload, 10),
            detail = u16(payload, 14),
        )
    }

    /** STM32F4 CRC peripheral semantics, not reflected ZIP/Ethernet CRC32. */
    fun stm32Crc32(data: ByteArray, offset: Int = 0, count: Int = data.size - offset): Long {
        require(offset >= 0 && count >= 0 && offset <= data.size - count)
        var cursor = offset
        val end = offset + count
        var crc = 0xFFFF_FFFFL
        while (cursor < end) {
            var word = 0xFFFF_FFFFL
            val bytes = minOf(4, end - cursor)
            repeat(bytes) { index ->
                word = (word and (0xFFL shl (index * 8)).inv()) or
                    ((data[cursor + index].toLong() and 0xFFL) shl (index * 8))
            }
            crc = feedWord(crc, word)
            cursor += bytes
        }
        return crc and 0xFFFF_FFFFL
    }

    private fun chunkCrc32(generation: Long, blockIndex: Long, block: ByteArray): Long {
        val crcInput = ByteArray(8 + Ce64BleOtaPackage.SectorBytes)
        putU32(crcInput, 0, generation)
        putU32(crcInput, 4, blockIndex)
        block.copyInto(crcInput, destinationOffset = 8)
        return stm32Crc32(crcInput)
    }

    private fun frame(commandId: Int, payload: ByteArray): ByteArray =
        ByteArray(payload.size + 3).also { frame ->
            frame[0] = 0x3C
            frame[1] = commandId.toByte()
            payload.copyInto(frame, destinationOffset = 2)
            frame[frame.lastIndex] = 0x3E
        }

    private fun feedWord(initialCrc: Long, initialWord: Long): Long {
        var crc = initialCrc and 0xFFFF_FFFFL
        var word = initialWord and 0xFFFF_FFFFL
        repeat(32) {
            val xor = ((crc xor word) and 0x8000_0000L) != 0L
            crc = (crc shl 1) and 0xFFFF_FFFFL
            if (xor) {
                crc = crc xor 0x04C1_1DB7L
            }
            word = (word shl 1) and 0xFFFF_FFFFL
        }
        return crc
    }

    private fun u16(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)

    private fun u32(data: ByteArray, offset: Int): Long =
        (data[offset].toLong() and 0xFFL) or
            ((data[offset + 1].toLong() and 0xFFL) shl 8) or
            ((data[offset + 2].toLong() and 0xFFL) shl 16) or
            ((data[offset + 3].toLong() and 0xFFL) shl 24)

    private fun putU32(data: ByteArray, offset: Int, value: Long) {
        data[offset] = value.toByte()
        data[offset + 1] = (value shr 8).toByte()
        data[offset + 2] = (value shr 16).toByte()
        data[offset + 3] = (value shr 24).toByte()
    }
}
