package com.wild.android.ble

import android.bluetooth.le.ScanRecord

object Ce32AdvertisementTelemetry {
    private const val ManufacturerSpecificDataType = 0xFF
    private const val MinBatteryVoltage = 2.0
    private const val MaxBatteryVoltage = 6.5

    fun hasManufacturerPayload(record: ScanRecord?): Boolean {
        if (record == null) {
            return false
        }

        if (record.manufacturerSpecificData.size() > 0) {
            return true
        }

        return hasManufacturerPayload(record.bytes)
    }

    fun parseVoltage(record: ScanRecord?): Double? {
        if (record == null) {
            return null
        }

        val manufacturerData = record.manufacturerSpecificData
        for (index in 0 until manufacturerData.size()) {
            val companyId = manufacturerData.keyAt(index)
            val payload = manufacturerData.valueAt(index) ?: continue
            parseVoltagePayload(payload)?.let { return it }

            val fullPayload = ByteArray(payload.size + 2)
            fullPayload[0] = (companyId and 0xFF).toByte()
            fullPayload[1] = ((companyId shr 8) and 0xFF).toByte()
            payload.copyInto(fullPayload, destinationOffset = 2)
            parseVoltagePayload(fullPayload)?.let { return it }
        }

        return parseVoltageFromScanBytes(record.bytes)
    }

    internal fun hasManufacturerPayload(scanRecordBytes: ByteArray?): Boolean {
        return extractManufacturerSections(scanRecordBytes).isNotEmpty()
    }

    internal fun parseVoltageFromScanBytes(scanRecordBytes: ByteArray?): Double? {
        for (payload in extractManufacturerSections(scanRecordBytes)) {
            if (payload.size > 2) {
                parseVoltagePayload(payload.copyOfRange(2, payload.size))?.let { return it }
            }
            parseVoltagePayload(payload)?.let { return it }
        }

        return null
    }

    internal fun parseVoltagePayload(data: ByteArray?): Double? {
        if (data == null || data.size < 3) {
            return null
        }

        return parseLegacyDecimalPayload(data)
            ?: parseRawVoltagePayload(data)
            ?: parseAsciiHexVoltagePayload(data, depth = 0)
    }

    private fun isValidVoltage(voltage: Double): Boolean {
        return !voltage.isNaN() &&
            !voltage.isInfinite() &&
            voltage >= MinBatteryVoltage &&
            voltage <= MaxBatteryVoltage
    }

    private fun parseRawVoltagePayload(data: ByteArray): Double? {
        return when {
            data.size == 3 -> parseThreeDigitVoltage(data, offset = 0)
            data.size == 4 && data[0] == 0.toByte() -> parseThreeDigitVoltage(data, offset = 1)
            data.size == 5 && hasManufacturerCompanyIdPrefix(data) -> parseThreeDigitVoltage(data, offset = 2)
            else -> null
        }
    }

    private fun parseLegacyDecimalPayload(data: ByteArray): Double? {
        var candidate = 0.0
        var scale = 0.01
        for (index in data.lastIndex downTo 0) {
            val digit = decodeDigit(data[index]) ?: return null
            candidate += digit * scale
            scale *= 10.0
        }

        return candidate.takeIf(::isValidVoltage)
    }

    private fun parseAsciiHexVoltagePayload(data: ByteArray, depth: Int): Double? {
        if (depth > 1 || !isAsciiHex(data)) {
            return null
        }

        val decoded = decodeAsciiHex(data) ?: return null
        return parseRawVoltagePayload(decoded)
            ?: if (isAsciiHex(decoded)) parseAsciiHexVoltagePayload(decoded, depth + 1) else null
    }

    private fun parseThreeDigitVoltage(data: ByteArray, offset: Int): Double? {
        if (offset < 0 || data.size - offset < 3) {
            return null
        }

        val ones = decodeDigit(data[offset]) ?: return null
        val tenths = decodeDigit(data[offset + 1]) ?: return null
        val hundredths = decodeDigit(data[offset + 2]) ?: return null
        val candidate = ones + (tenths * 0.1) + (hundredths * 0.01)
        return candidate.takeIf(::isValidVoltage)
    }

    private fun hasManufacturerCompanyIdPrefix(data: ByteArray): Boolean {
        if (data.size < 2) {
            return false
        }

        val low = data[0].toInt() and 0xFF
        val high = data[1].toInt() and 0xFF
        return (low == 0x30 && high == 0x00) ||
            (low == 0x00 && high == 0x30) ||
            (low == 0x33 && high == 0x30) ||
            (low == 0x30 && high == 0x33)
    }

    private fun decodeDigit(value: Byte): Int? {
        val raw = value.toInt() and 0xFF
        return when {
            raw <= 9 -> raw
            raw in '0'.code..'9'.code -> raw - '0'.code
            else -> null
        }
    }

    private fun isAsciiHex(data: ByteArray): Boolean {
        if (data.isEmpty()) {
            return false
        }

        return data.all { decodeHexNibble(it) != null }
    }

    private fun decodeAsciiHex(data: ByteArray): ByteArray? {
        if (data.isEmpty()) {
            return null
        }

        val normalized = if ((data.size and 1) == 0) data else byteArrayOf('0'.code.toByte()) + data
        val decoded = ByteArray(normalized.size / 2)
        for (index in decoded.indices) {
            val high = decodeHexNibble(normalized[index * 2]) ?: return null
            val low = decodeHexNibble(normalized[index * 2 + 1]) ?: return null
            decoded[index] = ((high shl 4) or low).toByte()
        }
        return decoded
    }

    private fun decodeHexNibble(value: Byte): Int? {
        val raw = value.toInt() and 0xFF
        return when (raw) {
            in '0'.code..'9'.code -> raw - '0'.code
            in 'A'.code..'F'.code -> raw - 'A'.code + 10
            in 'a'.code..'f'.code -> raw - 'a'.code + 10
            else -> null
        }
    }

    private fun extractManufacturerSections(scanRecordBytes: ByteArray?): List<ByteArray> {
        if (scanRecordBytes == null || scanRecordBytes.isEmpty()) {
            return emptyList()
        }

        val sections = mutableListOf<ByteArray>()
        var index = 0
        while (index < scanRecordBytes.size) {
            val length = scanRecordBytes[index].toInt() and 0xFF
            if (length == 0) {
                break
            }

            val typeIndex = index + 1
            val endExclusive = index + 1 + length
            if (typeIndex >= scanRecordBytes.size || endExclusive > scanRecordBytes.size) {
                break
            }

            val type = scanRecordBytes[typeIndex].toInt() and 0xFF
            if (type == ManufacturerSpecificDataType) {
                sections += scanRecordBytes.copyOfRange(typeIndex + 1, endExclusive)
            }

            index = endExclusive
        }

        return sections
    }
}
