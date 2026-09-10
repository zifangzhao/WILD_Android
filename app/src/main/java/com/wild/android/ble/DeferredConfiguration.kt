package com.wild.android.ble

internal fun configurationAfterReadback(
    session: DeviceSessionUiState,
    command: Int,
    valid: Boolean,
    recorderProtected: Boolean,
): DeviceSessionUiState {
    if (!valid || recorderProtected || !session.configurationPendingApply || command !in setOf(0x90, 0x91, 0x92)) return session
    val remaining = session.configurationReadbackRemaining.ifEmpty { setOf(0x90, 0x91, 0x92) } - command
    return session.copy(
        configurationReadbackRemaining = remaining,
        configurationPendingApply = remaining.isNotEmpty(),
        lastMessage = if (remaining.isEmpty()) "System, DSP1 and DSP2 re-read after stop. Review the returned settings before the next recording." else session.lastMessage,
    )
}
