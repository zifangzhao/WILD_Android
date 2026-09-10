package com.wild.android.ble

import kotlin.test.*

class DeferredConfigurationTest {
    private val pending = DeviceSessionUiState("device", "CE128", "address", 0,
        configurationPendingApply = true, configurationReadbackRemaining = setOf(0x90, 0x91, 0x92))

    @Test fun needsAllThreeValidReadbacksBeforeClearingPendingSettings() {
        val system = configurationAfterReadback(pending, 0x90, valid = true, recorderProtected = false)
        assertTrue(system.configurationPendingApply)
        val dsp1 = configurationAfterReadback(system, 0x91, valid = true, recorderProtected = false)
        assertTrue(dsp1.configurationPendingApply)
        val dsp2 = configurationAfterReadback(dsp1, 0x92, valid = true, recorderProtected = false)
        assertFalse(dsp2.configurationPendingApply)
    }

    @Test fun malformedOrMidRecordingRepliesDoNotClearConfirmation() {
        assertEquals(pending, configurationAfterReadback(pending, 0x90, valid = false, recorderProtected = false))
        assertEquals(pending, configurationAfterReadback(pending, 0x90, valid = true, recorderProtected = true))
    }
}
