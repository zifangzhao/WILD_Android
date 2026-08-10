package com.wild.android.ble

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Ce32AdvertisementTelemetryTest {
    @Test
    fun parseVoltagePayloadHandlesRawDigitsManufacturerPrefixAndAsciiHex() {
        val rawDigits = assertNotNull(Ce32AdvertisementTelemetry.parseVoltagePayload(byteArrayOf(4, 1, 2)))
        val manufacturerPrefixed = assertNotNull(Ce32AdvertisementTelemetry.parseVoltagePayload(byteArrayOf(0x30, 0x00, 4, 1, 2)))
        val asciiHex = assertNotNull(Ce32AdvertisementTelemetry.parseVoltagePayload("343132".encodeToByteArray()))

        assertEquals(4.12, rawDigits, 1e-9)
        assertEquals(4.12, manufacturerPrefixed, 1e-9)
        assertEquals(4.12, asciiHex, 1e-9)
        assertNull(Ce32AdvertisementTelemetry.parseVoltagePayload(byteArrayOf(9, 9, 9)))
    }

    @Test
    fun parseVoltageFromScanBytesFindsManufacturerSectionPayload() {
        val scanBytes = byteArrayOf(
            0x06,
            0xFF.toByte(),
            0x30,
            0x00,
            '4'.code.toByte(),
            '1'.code.toByte(),
            '2'.code.toByte(),
        )

        val voltage = Ce32AdvertisementTelemetry.parseVoltageFromScanBytes(scanBytes)

        assertNotNull(voltage)
        assertEquals(4.12, voltage, 1e-9)
        assertTrue(Ce32AdvertisementTelemetry.hasManufacturerPayload(scanBytes))
    }

    @Test
    fun hasManufacturerPayloadIgnoresScanRecordsWithoutMfgSections() {
        val scanBytes = byteArrayOf(
            0x02,
            0x01,
            0x06,
            0x03,
            0x03,
            0xF0.toByte(),
            0xFF.toByte(),
        )

        assertFalse(Ce32AdvertisementTelemetry.hasManufacturerPayload(scanBytes))
        assertNull(Ce32AdvertisementTelemetry.parseVoltageFromScanBytes(scanBytes))
    }
}
