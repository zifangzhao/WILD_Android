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
    fun parsesCurrentCe64V4HealthAndStorageAdvertisement() {
        val status = assertNotNull(
            Ce32AdvertisementTelemetry.parseCe64StatusPayload(
                "CE450700000000F819100E".encodeToByteArray(),
            ),
        )

        assertEquals(4, status.formatVersion)
        assertTrue(status.recording)
        assertFalse(status.previewing)
        assertEquals(0x07, status.bootModuleStatusPacked)
        assertEquals(0, status.lastEventCode)
        assertEquals(4.96, status.batteryVoltage, 1e-9)
        assertEquals(25, status.storageUsedPercent)
        assertEquals(3600L, status.recordingSeconds)
        assertEquals(
            4.96,
            assertNotNull(
                Ce32AdvertisementTelemetry.parseVoltagePayload(
                    "450700000000F819100E".encodeToByteArray(),
                ),
            ),
            1e-9,
        )
    }

    @Test
    fun preservesLegacyV2AndV3ElapsedTimeLayouts() {
        val v2 = assertNotNull(
            Ce32AdvertisementTelemetry.parseCe64StatusPayload(
                "CE257E12340000F8643412".encodeToByteArray(),
            ),
        )
        val v3 = assertNotNull(
            Ce32AdvertisementTelemetry.parseCe64StatusPayload(
                "CE39AF00000000F8643412".encodeToByteArray(),
            ),
        )

        assertEquals(2, v2.formatVersion)
        assertTrue(v2.recording)
        assertEquals(0x1234, v2.lastEventCode)
        assertEquals(0x1234L, v2.recordingSeconds)
        assertTrue(v2.hasRecordingElapsedTime)
        assertFalse(v2.hasTemperatureTelemetry)

        assertEquals(3, v3.formatVersion)
        assertTrue(v3.recording)
        assertEquals(0x1234L, v3.recordingSeconds)
        assertTrue(v3.hasRecordingElapsedTime)
        assertFalse(v3.hasTemperatureTelemetry)
    }

    @Test
    fun parsesV6TemperatureAndV7AlternatingExtensionPagesWithoutChangingV4() {
        val v6Temperature = assertNotNull(
            Ce32AdvertisementTelemetry.parseCe64StatusPayload(
                "CE610200000000F819732B".encodeToByteArray(),
            ),
        )
        val v7ElapsedTime = assertNotNull(
            Ce32AdvertisementTelemetry.parseCe64StatusPayload(
                "CE7D4500000000F8192301".encodeToByteArray(),
            ),
        )
        val v7Temperature = assertNotNull(
            Ce32AdvertisementTelemetry.parseCe64StatusPayload(
                "CE750200000000F819732B".encodeToByteArray(),
            ),
        )

        assertEquals(6, v6Temperature.formatVersion)
        assertTrue(v6Temperature.hasTemperatureTelemetry)
        assertFalse(v6Temperature.hasRecordingElapsedTime)
        assertEquals(37.0, assertNotNull(v6Temperature.auxTemperatureCelsius), 1e-9)
        assertEquals(29.5, assertNotNull(v6Temperature.mcuTemperatureCelsius), 1e-9)

        assertEquals(7, v7ElapsedTime.formatVersion)
        assertTrue(v7ElapsedTime.hasRecordingElapsedTime)
        assertFalse(v7ElapsedTime.hasTemperatureTelemetry)
        assertEquals(0x012345L, v7ElapsedTime.recordingSeconds)

        val merged = assertNotNull(Ce32AdvertisementTelemetry.mergeStatusPages(v7ElapsedTime, v7Temperature))
        assertTrue(merged.hasRecordingElapsedTime)
        assertTrue(merged.hasTemperatureTelemetry)
        assertEquals(0x012345L, merged.recordingSeconds)
        assertEquals(37.0, assertNotNull(merged.auxTemperatureCelsius), 1e-9)
        assertEquals(29.5, assertNotNull(merged.mcuTemperatureCelsius), 1e-9)
    }

    @Test
    fun rejectsUnknownCe64AdvertisementVersion() {
        assertNull(Ce32AdvertisementTelemetry.parseCe64StatusPayload("CE150700000000F819100E".encodeToByteArray()))
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
