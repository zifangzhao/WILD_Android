package com.wild.android.ble

/** Rules shared by the editor and transport; matches CE64 scheduler v2. */
object Ce64Scheduler {
    fun validationError(rule: SchedulerRuleUiState): String? = with(rule) {
        when {
            id !in 0..7 -> "Choose rule 1–8."
            trigger !in 0..3 || action !in 0..3 || missedPolicy !in 0..1 -> "Unknown trigger, action, or missed-event policy."
            profileId != 255 && profileId !in 0..7 -> "Choose current settings or profile 1–8."
            action != 0 && profileId != 255 -> "Only recording-start rules can use a profile."
            conditionMask !in 0..15 || conditionLogic !in 0..1 || conditionInvertMask and conditionMask.inv() != 0 -> "Invalid condition selection."
            anchorDay !in 0..49_709L || timeOfDaySeconds !in 0..86_399L -> "Choose a valid device-local date and time."
            anchorDay * 86_400L + timeOfDaySeconds >= 0xFFFF_FFFFL -> "The date is outside the device clock range."
            trigger == 2 && periodSeconds !in 60..604_800L -> "Repeat interval must be 60 seconds to 7 days."
            action == 0 && durationSeconds !in 1..86_400L -> "Recording duration must be 1 second to 24 hours."
            trigger != 3 && signalSource != 0 -> "Signal sources require a condition-crossing trigger."
            trigger == 3 && (signalSource !in listOf(1, 2, 4, 8, 16) || missedPolicy != 0) -> "Choose a signal source and skip missed events."
            trigger == 3 && signalSource != 16 && conditionMask and requiredCondition(signalSource) == 0 -> "Enable the threshold for the selected signal source."
            conditionMask != 0 && (evaluationSeconds !in 10..3_600L || debounceCount !in 1..255 || maxDeferrals !in 1..24) -> "Conditions need a 10–3600 second interval, 1–255 passes, and 1–24 retries."
            conditionMask and 4 != 0 && evaluationSeconds < 60 -> "Motion conditions need at least 60 seconds between checks."
            action == 3 && (trigger != 2 || periodSeconds < 3_600 || missedPolicy != 0) -> "Restart rules must repeat at least hourly and skip missed events."
            priority !in 0..65_535 || batteryThresholdMv !in 0..65_535 || activityThresholdMg !in 0..65_535 ||
                hysteresis !in 0..65_535 || marker !in 0..65_535 || aiThresholdQ15 !in -32_768..32_767 ||
                storageThresholdBlocks !in 0..0xFFFF_FFFFL -> "A threshold or priority is outside the device's supported range."
            else -> null
        }
    }

    fun requiredCondition(signal: Int): Int = when (signal) { 1 -> 8; 2 -> 4; 4 -> 1; 8 -> 2; else -> 0 }

    fun resultLabel(result: Int): String = when (result) {
        0 -> "No action yet"
        1 -> "Completed"
        2 -> "Already in requested state"
        3 -> "Waiting for conditions"
        4 -> "Waiting for recording to finish"
        5 -> "Missed event skipped"
        6 -> "Higher-priority rule ran"
        7 -> "Conditions did not pass"
        8 -> "Storage error"
        9 -> "Recording profile unavailable"
        else -> "Unknown result ($result)"
    }

    fun errorLabel(code: Int): String = when (code) {
        1 -> "Invalid request"
        2 -> "Rule not supported; check its values"
        3 -> "Could not save to device storage"
        4 -> "Stored schedule failed its integrity check"
        5 -> "Recording profile is missing or incompatible"
        6 -> "Device is busy; stop recording before editing the schedule"
        else -> "Device rejected the request (error $code)"
    }

    fun retryable(command: Int): Boolean = command in listOf(0xD0, 0xD1, 0xD9, 0xDB)

    /** DB has no chunk echo. Its cumulative mask must match this exact chunk. */
    fun responseMatches(request: ByteArray, reply: ByteArray): Boolean {
        if (request.size < 3 || reply.isEmpty()) return false
        val command = request[1].toInt() and 0xFF
        if ((reply[0].toInt() and 0xFF) != command ||
            reply.size != Ce32Protocol.schedulerResponsePayloadLengthFor(command)) return false
        return when (command) {
            0xD9 -> request.size >= 5 && reply[1] == request[2] && reply[2] == request[3] && reply[3].toInt() == 16
            0xDB -> {
                if (request.size < 5) false else {
                    val chunk = request[3].toInt() and 0xFF
                    val mask = (reply[2].toInt() and 0xFF) or ((reply[3].toInt() and 0xFF) shl 8)
                    chunk in 0..15 && (reply[1].toInt() != 0 || mask == (1 shl (chunk + 1)) - 1)
                }
            }
            else -> true
        }
    }
}
