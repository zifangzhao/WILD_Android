package com.wild.android.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class WildPreviewSelectorHelpersTest {
    @Test
    fun previewBankRangesChunkChannelsIntoPhoneSizedBanks() {
        assertEquals(
            listOf(0..7, 8..15, 16..23, 24..31),
            previewBankRanges(optionCount = 32),
        )
        assertEquals(
            listOf(0..7, 8..9),
            previewBankRanges(optionCount = 10),
        )
    }

    @Test
    fun previewBankForIndexReturnsContainingBank() {
        assertEquals(0..7, previewBankForIndex(index = 0, optionCount = 32))
        assertEquals(8..15, previewBankForIndex(index = 13, optionCount = 32))
        assertEquals(24..31, previewBankForIndex(index = 31, optionCount = 32))
    }

    @Test
    fun previewBankLabelUsesEphysRangeNotation() {
        assertEquals("E1-8", previewBankLabel(0..7))
        assertEquals("E9-16", previewBankLabel(8..15))
        assertEquals("E1", previewBankLabel(0..0))
    }
}
