package com.wild.android.ble

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Ce64BleOtaTest {
    @Test
    fun fusedHexValidationAndWireCommandsMatchCe64V3Protocol() {
        val fusedHex = validFusedHex(generation = 73)
        val packageInfo = Ce64BleOtaPackage.fromFusedHex(fusedHex)

        assertEquals(73L, packageInfo.generation)
        assertEquals(12, packageInfo.imageBytes)
        assertEquals(1, packageInfo.blockCount)
        assertEquals(0x2000_2000L, u32(packageInfo.blockAt(0), 0))
        assertEquals(0x0802_0001L, u32(packageInfo.blockAt(0), 4))

        val begin = Ce64BleOtaProtocol.buildBeginCommand(packageInfo)
        assertEquals(134, begin.size)
        assertContentEquals(byteArrayOf(0x3C, 0xB4.toByte(), 'O'.code.toByte(), 'T'.code.toByte(), 'A'.code.toByte()), begin.take(5).toByteArray())
        assertEquals(0x3E, begin.last().toInt() and 0xFF)

        val write = Ce64BleOtaProtocol.buildWriteCommand(packageInfo, 0)
        assertEquals(527, write.size)
        assertEquals(0xB5, write[1].toInt() and 0xFF)
        assertEquals(73L, u32(write, 2))
        assertEquals(0L, u32(write, 6))
        assertContentEquals(packageInfo.blockAt(0), write.copyOfRange(10, 522))
        assertEquals(0x3E, write.last().toInt() and 0xFF)

        assertContentEquals(byteArrayOf(0x3C, 0xB6.toByte(), 73, 0, 0, 0, 0x3E), Ce64BleOtaProtocol.buildFinishCommand(73))
        assertContentEquals(byteArrayOf(0x3C, 0xB7.toByte(), 0x3E), Ce64BleOtaProtocol.buildStatusCommand())
        assertContentEquals(byteArrayOf(0x3C, 0xB9.toByte(), 'G'.code.toByte(), 'O'.code.toByte(), 0x7F, 0x3E), Ce64BleOtaProtocol.buildInstallCommand(0x7F))
    }

    @Test
    fun replyParserUsesTheSixteenByteAndroidPayload() {
        val payload = ByteArray(16)
        payload[0] = Ce64BleOtaReply.ResultOk.toByte()
        payload[1] = Ce64BleOtaReply.StateReady.toByte()
        putU32(payload, 2, 73)
        putU32(payload, 6, 8)
        putU32(payload, 10, 0x1234_5678L)
        payload[14] = 0xCD.toByte()
        payload[15] = 0xAB.toByte()

        val reply = assertNotNull(Ce64BleOtaProtocol.parseReply(0xB7, payload))
        assertTrue(reply.isSuccess)
        assertEquals(Ce64BleOtaReply.StateReady, reply.state)
        assertEquals(73L, reply.generation)
        assertEquals(8L, reply.value)
        assertEquals(0x1234_5678L, reply.imageCrc32)
        assertEquals(0xABCD, reply.detail)
    }

    @Test
    fun fusedHexValidationRejectsCorruptApplication() {
        assertFailsWith<IllegalArgumentException> {
            Ce64BleOtaPackage.fromFusedHex(validFusedHex(corruptApplication = true))
        }
    }

    private fun validFusedHex(generation: Long = 1, corruptApplication: Boolean = false): ByteArray {
        val image = ByteArray(12) { 0xFF.toByte() }
        putU32(image, 0, 0x2000_2000L)
        putU32(image, 4, 0x0802_0001L)
        putU32(image, 8, 0x1234_5678L)
        val manifest = ByteArray(Ce64BleOtaPackage.ManifestBytes)
        putU32(manifest, 0, 0x3436_4543L)
        putU32(manifest, 4, 0x3346_4E4DL)
        putU32(manifest, 8, 3)
        putU32(manifest, 12, Ce64BleOtaPackage.ManifestBytes.toLong())
        putU32(manifest, 16, 0x444C_4156L)
        putU32(manifest, 20, 1)
        putU32(manifest, 24, generation)
        putU32(manifest, 28, Ce64BleOtaPackage.ApplicationBase)
        putU32(manifest, 32, image.size.toLong())
        putU32(manifest, 36, Ce64BleOtaProtocol.stm32Crc32(image))
        putU32(manifest, 40, u32(image, 0))
        putU32(manifest, 44, u32(image, 4))
        putU32(manifest, 124, Ce64BleOtaProtocol.stm32Crc32(manifest, 0, 124))

        if (corruptApplication) {
            image[8] = 0x79
        }
        return buildString {
            append(intelHexRecord(0x04, 0, byteArrayOf(0x08, 0x01)))
            manifest.asList().chunked(16).forEachIndexed { index, block ->
                append(intelHexRecord(0x00, index * 16, block.toByteArray()))
            }
            append(intelHexRecord(0x04, 0, byteArrayOf(0x08, 0x02)))
            append(intelHexRecord(0x00, 0, image))
            append(intelHexRecord(0x01, 0, byteArrayOf()))
        }.encodeToByteArray()
    }

    private fun intelHexRecord(type: Int, offset: Int, data: ByteArray): String {
        var sum = data.size + (offset ushr 8) + (offset and 0xFF) + type
        data.forEach { sum += it.toInt() and 0xFF }
        val checksum = (-sum) and 0xFF
        return buildString {
            append(":%02X%04X%02X".format(data.size, offset, type))
            data.forEach { append("%02X".format(it.toInt() and 0xFF)) }
            append("%02X\n".format(checksum))
        }
    }

    private fun putU32(data: ByteArray, offset: Int, value: Long) {
        data[offset] = value.toByte()
        data[offset + 1] = (value shr 8).toByte()
        data[offset + 2] = (value shr 16).toByte()
        data[offset + 3] = (value shr 24).toByte()
    }

    private fun u32(data: ByteArray, offset: Int): Long =
        (data[offset].toLong() and 0xFFL) or
            ((data[offset + 1].toLong() and 0xFFL) shl 8) or
            ((data[offset + 2].toLong() and 0xFFL) shl 16) or
            ((data[offset + 3].toLong() and 0xFFL) shl 24)
}
