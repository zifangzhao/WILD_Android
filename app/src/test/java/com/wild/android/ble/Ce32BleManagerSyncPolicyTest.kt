package com.wild.android.ble

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Ce32BleManagerSyncPolicyTest {
    @Test
    fun systemParamFallbackDoesNotPromoteWhileBootstrapIsStillActive() {
        assertFalse(
            shouldPromoteSystemParamSyncFallback(
                initialSyncCompleted = false,
                initialSyncStarted = true,
                legacyReadyPromoted = false,
            ),
        )
    }

    @Test
    fun systemParamFallbackDoesNotPromoteWithoutLegacyReadySignal() {
        assertFalse(
            shouldPromoteSystemParamSyncFallback(
                initialSyncCompleted = false,
                initialSyncStarted = true,
                legacyReadyPromoted = false,
            ),
        )
    }

    @Test
    fun systemParamFallbackStillSupportsLegacyReadyPromotion() {
        assertTrue(
            shouldPromoteSystemParamSyncFallback(
                initialSyncCompleted = false,
                initialSyncStarted = true,
                legacyReadyPromoted = true,
            ),
        )
    }

    @Test
    fun systemParamFallbackDoesNotRePromoteCompletedSync() {
        assertFalse(
            shouldPromoteSystemParamSyncFallback(
                initialSyncCompleted = true,
                initialSyncStarted = true,
                legacyReadyPromoted = true,
            ),
        )
    }

    @Test
    fun handshakeReplyWithZeroSamplesCompletesInitialSync() {
        assertTrue(
            isTrustedInitialSyncCompletion(
                SyncStatus(
                    mode = 0x01,
                    offsetSeconds = 0f,
                    accuracySeconds = 0f,
                    delaySeconds = 0f,
                    sampleCount = 0,
                ),
            ),
        )
    }

    @Test
    fun trainingProgressReplyDoesNotCompleteInitialSync() {
        assertFalse(
            isTrustedInitialSyncCompletion(
                SyncStatus(
                    mode = 0x05,
                    offsetSeconds = null,
                    accuracySeconds = null,
                    delaySeconds = null,
                    sampleCount = 0,
                ),
            ),
        )
    }

    @Test
    fun recordingAttachReplyDoesNotStartTheNormalBootstrap() {
        val attach = SyncStatus(
            mode = 0x06,
            offsetSeconds = null,
            accuracySeconds = null,
            delaySeconds = null,
            sampleCount = 0,
        )

        assertFalse(isTrustedInitialSyncCompletion(attach))
        assertTrue(isRecordingAttachSyncMode(attach))
    }

    @Test
    fun configurationWritesAreDeferredForAnActiveOrAttachedRecorder() {
        assertTrue(shouldDeferConfigurationApply(isRecordingLike = true, isRecordingAttach = false))
        assertTrue(shouldDeferConfigurationApply(isRecordingLike = false, isRecordingAttach = true))
        assertFalse(shouldDeferConfigurationApply(isRecordingLike = false, isRecordingAttach = false))
    }

    @Test
    fun validDeviceTimeExchangeCompletesAHandshakeWithoutA82Reply() {
        assertTrue(
            shouldPromoteInitialSyncFrom8DProbe(
                initialSyncCompleted = false,
                hasDeviceTimestamp = true,
            ),
        )
        assertFalse(
            shouldPromoteInitialSyncFrom8DProbe(
                initialSyncCompleted = false,
                hasDeviceTimestamp = false,
            ),
        )
        assertFalse(
            shouldPromoteInitialSyncFrom8DProbe(
                initialSyncCompleted = true,
                hasDeviceTimestamp = true,
            ),
        )
    }

    @Test
    fun configurationModeBusyExplainsHowToRecover() {
        assertEquals(
            "Device is in BLE configuration mode. Finish that configuration session, then tap Resync device.",
            initialBootstrapFailureMessage(configModeBusyCount = 1),
        )
        assertEquals(
            "Device did not respond to the initial handshake",
            initialBootstrapFailureMessage(configModeBusyCount = 0),
        )
    }

    @Test
    fun failedActiveConnectionStaysFocusedWhenNoOtherDeviceIsLinked() {
        val failed = session("failed", BleHostSessionState.Disconnected)
        val nearby = session("nearby", BleHostSessionState.Disconnected)

        assertTrue(
            resolveActiveSessionAfterDisconnect(
                activeSessionId = failed.id,
                disconnectedDeviceId = failed.id,
                sessions = listOf(failed, nearby),
            ) == failed.id,
        )
    }

    @Test
    fun failedActiveConnectionMovesToAnotherLinkedDevice() {
        val failed = session("failed", BleHostSessionState.Disconnected)
        val linked = session("linked", BleHostSessionState.Synced)

        assertTrue(
            resolveActiveSessionAfterDisconnect(
                activeSessionId = failed.id,
                disconnectedDeviceId = failed.id,
                sessions = listOf(failed, linked),
            ) == linked.id,
        )
    }

    @Test
    fun parameterReadQueueAdvancesOnlyAfterItsMatchingPayloadReply() {
        val queue = ParameterReadQueue()
        val system = parameterRead(0x90, "system")
        val dsp1 = parameterRead(0x91, "dsp1")
        val dsp2 = parameterRead(0x92, "dsp2")

        assertTrue(queue.enqueue(listOf(system, dsp1, dsp2)))
        assertEquals(0x90, queue.firstOrNull()?.responseCommandId)
        assertFalse(queue.acknowledge(0x91))
        assertEquals(0x90, queue.firstOrNull()?.responseCommandId)
        assertTrue(queue.acknowledge(0x90))
        assertEquals(0x91, queue.firstOrNull()?.responseCommandId)
        assertFalse(queue.enqueue(listOf(dsp1, dsp2)))
        assertTrue(queue.acknowledge(0x91))
        assertEquals(0x92, queue.firstOrNull()?.responseCommandId)
        assertTrue(queue.acknowledge(0x92))
        assertEquals(null, queue.firstOrNull())
    }

    @Test
    fun parameterReadQueueClearsBeforeTheNextConnectionCanReuseIt() {
        val queue = ParameterReadQueue()
        val system = parameterRead(0x90, "system")
        val dsp1 = parameterRead(0x91, "dsp1")

        assertTrue(queue.enqueue(listOf(system, dsp1)))
        queue.clear()
        assertEquals(null, queue.firstOrNull())

        assertTrue(queue.enqueue(listOf(dsp1)))
        assertEquals(0x91, queue.firstOrNull()?.responseCommandId)
    }

    private fun session(id: String, hostState: BleHostSessionState): DeviceSessionUiState {
        return DeviceSessionUiState(
            id = id,
            name = id,
            address = id,
            traceColorArgb = 0,
            hostState = hostState,
        )
    }

    private fun parameterRead(responseCommandId: Int, label: String): ParameterReadRequest {
        return ParameterReadRequest(
            responseCommandId = responseCommandId,
            command = byteArrayOf(responseCommandId.toByte()),
            label = label,
            surfaceMessage = false,
            surfaceEvent = false,
        )
    }
}
