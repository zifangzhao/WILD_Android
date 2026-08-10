package com.wild.android.ble

import kotlin.test.Test
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
}
