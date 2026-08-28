package com.wild.android.ble

import android.bluetooth.le.ScanRecord

data class Ce64AdvertisementStatus(
    val formatVersion: Int,
    val recording: Boolean,
    val previewing: Boolean,
    val bootModuleStatusPacked: Int,
    val lastEventCode: Int,
    val failedSubsystems: Int,
    val degradedSubsystems: Int,
    val batteryVoltage: Double,
    val storageUsedPercent: Int?,
    val recordingSeconds: Long,
    /** True only when this advertisement carried an elapsed-time value. */
    val hasRecordingElapsedTime: Boolean = true,
    /** True only when this advertisement carried the slow-ADC temperature page. */
    val hasTemperatureTelemetry: Boolean = false,
    val auxTemperatureCelsius: Double? = null,
    val mcuTemperatureCelsius: Double? = null,
)

object Ce32AdvertisementTelemetry {
    private const val ManufacturerSpecificDataType = 0xFF
    private const val MinBatteryVoltage = 2.0
    private const val MaxBatteryVoltage = 6.5
    private const val Ce64StatusBytes = 10
    private const val Ce64StatusHexChars = Ce64StatusBytes * 2

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

    fun parseCe64Status(record: ScanRecord?): Ce64AdvertisementStatus? {
        if (record == null) {
            return null
        }

        val manufacturerData = record.manufacturerSpecificData
        for (index in 0 until manufacturerData.size()) {
            val companyId = manufacturerData.keyAt(index)
            val payload = manufacturerData.valueAt(index) ?: continue
            parseCe64StatusPayload(payload)?.let { return it }

            val fullPayload = ByteArray(payload.size + 2)
            fullPayload[0] = (companyId and 0xFF).toByte()
            fullPayload[1] = ((companyId shr 8) and 0xFF).toByte()
            payload.copyInto(fullPayload, destinationOffset = 2)
            parseCe64StatusPayload(fullPayload)?.let { return it }
        }

        return extractManufacturerSections(record.bytes).firstNotNullOfOrNull(::parseCe64StatusPayload)
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

        return parseCe64StatusPayload(data)?.batteryVoltage
            ?: parseLegacyDecimalPayload(data)
            ?: parseRawVoltagePayload(data)
            ?: parseAsciiHexVoltagePayload(data, depth = 0)
    }

    internal fun parseCe64StatusPayload(data: ByteArray?): Ce64AdvertisementStatus? {
        if (data == null || data.isEmpty()) {
            return null
        }

        val text = data.toString(Charsets.US_ASCII)
        val hexText = when {
            text.length == Ce64StatusHexChars && text.all(::isAsciiHexChar) -> text
            text.length == Ce64StatusHexChars + 2 && text.startsWith("CE", ignoreCase = true) &&
                text.drop(2).all(::isAsciiHexChar) -> text.drop(2)
            else -> return null
        }
        val decoded = ByteArray(Ce64StatusBytes)
        for (index in decoded.indices) {
            val high = hexText[index * 2].digitToIntOrNull(16) ?: return null
            val low = hexText[index * 2 + 1].digitToIntOrNull(16) ?: return null
            decoded[index] = ((high shl 4) or low).toByte()
        }

        val flags = decoded[0].toInt() and 0xFF
        val formatVersion = flags ushr 4
        if (formatVersion !in setOf(2, 3, 4, 6, 7)) {
            return null
        }
        val v7TimePage = formatVersion == 7 && flags and 0x08 != 0
        val hasRecordingElapsedTime = formatVersion != 6 &&
            (formatVersion != 7 || v7TimePage)
        val recordingSeconds = when {
            v7TimePage -> (decoded[1].toLong() and 0xFF) or
                ((decoded[8].toLong() and 0xFF) shl 8) or
                ((decoded[9].toLong() and 0xFF) shl 16)
            hasRecordingElapsedTime -> (decoded[8].toLong() and 0xFF) or
                ((decoded[9].toLong() and 0xFF) shl 8)
            else -> 0L
        }
        val hasTemperatureTelemetry = formatVersion == 6 || (formatVersion == 7 && !v7TimePage)
        val auxTemperatureCelsius: Double?
        val mcuTemperatureCelsius: Double?
        if (hasTemperatureTelemetry) {
            val auxCode = (decoded[1].toInt() and 0xFF) or ((decoded[8].toInt() and 0x0F) shl 8)
            val mcuCode = ((decoded[8].toInt() ushr 4) and 0x0F) or ((decoded[9].toInt() and 0xFF) shl 4)
            auxTemperatureCelsius = decodeTemperatureDeciC(auxCode)
            mcuTemperatureCelsius = decodeTemperatureDeciC(mcuCode)
        } else {
            auxTemperatureCelsius = null
            mcuTemperatureCelsius = null
        }
        val storageValid = flags and 0x04 != 0
        val storageRaw = decoded[7].toInt() and 0xFF
        return Ce64AdvertisementStatus(
            formatVersion = formatVersion,
            recording = flags and 0x01 != 0,
            previewing = flags and 0x02 != 0,
            bootModuleStatusPacked = if (formatVersion == 4) decoded[1].toInt() and 0xFF else 0xFF,
            lastEventCode = ((decoded[2].toInt() and 0xFF) shl 8) or (decoded[3].toInt() and 0xFF),
            failedSubsystems = decoded[4].toInt() and 0xFF,
            degradedSubsystems = decoded[5].toInt() and 0xFF,
            batteryVoltage = (decoded[6].toInt() and 0xFF) * 0.02,
            storageUsedPercent = storageRaw.takeIf { storageValid && it <= 100 },
            recordingSeconds = recordingSeconds,
            hasRecordingElapsedTime = hasRecordingElapsedTime,
            hasTemperatureTelemetry = hasTemperatureTelemetry,
            auxTemperatureCelsius = auxTemperatureCelsius,
            mcuTemperatureCelsius = mcuTemperatureCelsius,
        )
    }

    /**
     * V7 alternates the temperature and elapsed-time extension pages. Keep the
     * last value from the other page, without altering complete legacy V2–V6
     * advertisements.
     */
    fun mergeStatusPages(
        previous: Ce64AdvertisementStatus?,
        incoming: Ce64AdvertisementStatus?,
    ): Ce64AdvertisementStatus? {
        if (incoming == null || previous == null) return incoming ?: previous
        val recordingJustStarted = !previous.recording && incoming.recording
        return incoming.copy(
            recordingSeconds = when {
                incoming.hasRecordingElapsedTime -> incoming.recordingSeconds
                recordingJustStarted -> 0L
                else -> previous.recordingSeconds
            },
            hasRecordingElapsedTime = incoming.hasRecordingElapsedTime ||
                (!recordingJustStarted && previous.hasRecordingElapsedTime),
            hasTemperatureTelemetry = incoming.hasTemperatureTelemetry || previous.hasTemperatureTelemetry,
            auxTemperatureCelsius = if (incoming.hasTemperatureTelemetry) {
                incoming.auxTemperatureCelsius
            } else {
                previous.auxTemperatureCelsius
            },
            mcuTemperatureCelsius = if (incoming.hasTemperatureTelemetry) {
                incoming.mcuTemperatureCelsius
            } else {
                previous.mcuTemperatureCelsius
            },
        )
    }

    private fun decodeTemperatureDeciC(code: Int): Double? =
        if (code == 0x0FFF) null else (code - 400) / 10.0

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

    private fun isAsciiHexChar(value: Char): Boolean = value.digitToIntOrNull(16) != null

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
