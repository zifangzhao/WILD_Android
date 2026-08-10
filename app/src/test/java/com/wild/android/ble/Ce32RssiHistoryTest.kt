package com.wild.android.ble

import kotlin.test.Test
import kotlin.test.assertEquals

class Ce32RssiHistoryTest {
    @Test
    fun samplesAreRateLimitedAndIgnoreInvalidRssi() {
        val first = appendRssiHistorySample(emptyList(), -64, 1_000L)
        val rateLimited = appendRssiHistorySample(first, -63, 1_400L)
        val second = appendRssiHistorySample(rateLimited, -62, 1_500L)
        val invalid = appendRssiHistorySample(second, 127, 2_000L)

        assertEquals(listOf(-64), first.map { it.valueDbm })
        assertEquals(first, rateLimited)
        assertEquals(listOf(-64, -62), second.map { it.valueDbm })
        assertEquals(second, invalid)
    }

    @Test
    fun samplesExpireAfterFiveMinutesAndAreBounded() {
        var history = emptyList<RssiSampleUiState>()
        repeat(601) { index ->
            history = appendRssiHistorySample(history, -70, index * 500L)
        }

        assertEquals(600, history.size)
        assertEquals(500L, history.first().timestampMs)

        val pruned = appendRssiHistorySample(history, -71, 600_001L)
        assertEquals(listOf(-71), pruned.map { it.valueDbm })
    }
}
