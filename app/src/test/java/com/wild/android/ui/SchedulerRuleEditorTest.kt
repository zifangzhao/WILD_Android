package com.wild.android.ui

import com.wild.android.ble.SchedulerRuleUiState
import kotlin.test.*

class SchedulerRuleEditorTest {
    @Test fun editingAdvancedRulePreservesItsTriggerGuardsAndProfile() {
        val rule = SchedulerRuleUiState(4, enabled = true, trigger = 2, action = 0, profileId = 3,
            anchorDay = 9746, timeOfDaySeconds = 48620, periodSeconds = 3600, durationSeconds = 333,
            conditionMask = 15, conditionInvertMask = 3, conditionLogic = 1, priority = 12,
            batteryThresholdMv = 3500, storageThresholdBlocks = 2048000, activityThresholdMg = 12,
            aiThresholdQ15 = -42, hysteresis = 5, debounceCount = 3, maxDeferrals = 8, marker = 65)
        assertEquals(rule, schedulerRuleFromDraft(4, schedulerDraftValues(rule)))
    }

    @Test fun invalidDatesAndNumbersDoNotSilentlySendMidnightOrClampedValues() {
        val values = schedulerDraftValues(SchedulerRuleUiState(0, trigger = 1, durationSeconds = 60))
        for ((key, value) in listOf("time" to "25:00", "date" to "2026-02-30", "duration" to "", "duration" to "86401", "battery" to "999999999999")) {
            assertTrue(runCatching { schedulerRuleFromDraft(0, values + (key to value)) }.isFailure, key)
        }
    }

    @Test fun batteryStopUsesInvertedSignalGuardNotDailyTime() {
        val rule = SchedulerRuleUiState(2, enabled = true, trigger = 3, action = 1, signalSource = 4,
            conditionMask = 1, conditionInvertMask = 1, batteryThresholdMv = 3400)
        assertEquals(rule, schedulerRuleFromDraft(2, schedulerDraftValues(rule)))
    }
}
