package com.wild.android.ble

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class Ce32BleManagerPreviewPrimePolicyTest {
    @Test
    fun previewPrimeDelayMatchesWindowsParityCommandMap() {
        assertEquals(175L, previewPrimeDelayForInboundCommand(0x82))
        assertEquals(225L, previewPrimeDelayForInboundCommand(0x90))
        assertEquals(275L, previewPrimeDelayForInboundCommand(0x91))
        assertEquals(275L, previewPrimeDelayForInboundCommand(0x92))
        assertNull(previewPrimeDelayForInboundCommand(0x8B))
    }
}
