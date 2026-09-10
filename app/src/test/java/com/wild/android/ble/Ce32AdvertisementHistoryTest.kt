package com.wild.android.ble

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Ce32AdvertisementHistoryTest {
    @Test
    fun samplesAdvertisementStateAtMostTwicePerSecondWhenUnchanged() {
        val status = status(recording = false, previewing = true)
        val first = appendAdvertisementHistorySample(emptyList(), status, null, 1_000L)
        val rateLimited = appendAdvertisementHistorySample(first, status, null, 1_400L)
        val second = appendAdvertisementHistorySample(rateLimited, status, null, 1_500L)

        assertEquals(1, rateLimited.size)
        assertEquals(2, second.size)
    }

    @Test
    fun retainsAnImmediateSampleWhenAdvertisementStateChanges() {
        val previewing = appendAdvertisementHistorySample(
            history = emptyList(),
            status = status(recording = false, previewing = true),
            voltage = null,
            timestampMs = 1_000L,
        )
        val recording = appendAdvertisementHistorySample(
            history = previewing,
            status = status(recording = true, previewing = true),
            voltage = null,
            timestampMs = 1_100L,
        )

        assertEquals(2, recording.size)
        assertTrue(recording.last().recording == true)
    }

    @Test
    fun storesLegacyAdvertisementVoltageWithoutInventingStateFlags() {
        val history = appendAdvertisementHistorySample(
            history = emptyList(),
            status = null,
            voltage = 4.12,
            timestampMs = 1_000L,
        )

        assertEquals(1, history.size)
        assertEquals(4.12, history.single().voltage)
        assertTrue(!history.single().hasStateTelemetry)
    }

    @Test
    fun changingAiResultCreatesAnImmediateHistoryPointButAgeAloneDoesNot() {
        val firstStatus = status(recording = true, previewing = false).copy(
            formatVersion = 8,
            batteryVoltage = null,
            advertisedSampleRateHz = 30_000,
            isAiAdvertisementPage = true,
            hasAiResult = true,
            aiModelId = 1,
            aiClassId = 3,
            aiConfidencePercentage = 91,
            aiEventSequence = 7,
            aiResultAgeSeconds = 1,
        )
        val first = appendAdvertisementHistorySample(emptyList(), firstStatus, null, 1_000L)
        val increasedAge = appendAdvertisementHistorySample(
            first,
            firstStatus.copy(aiResultAgeSeconds = 2),
            null,
            1_100L,
        )
        val newResult = appendAdvertisementHistorySample(
            increasedAge,
            firstStatus.copy(aiEventSequence = 8, aiResultAgeSeconds = 0, aiResultIsNew = true),
            null,
            1_200L,
        )

        assertEquals(1, increasedAge.size)
        assertEquals(2, newResult.size)
        assertEquals(8, newResult.last().aiEventSequence)
    }

    private fun status(recording: Boolean, previewing: Boolean): Ce64AdvertisementStatus {
        return Ce64AdvertisementStatus(
            formatVersion = 4,
            recording = recording,
            previewing = previewing,
            bootModuleStatusPacked = 0,
            lastEventCode = 0,
            failedSubsystems = 0,
            degradedSubsystems = 0,
            batteryVoltage = 4.96,
            storageUsedPercent = 25,
            recordingSeconds = 3_600L,
        )
    }
}
