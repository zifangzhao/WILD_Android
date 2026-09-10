package com.wild.android.cloud

import com.wild.android.ble.Ce64Scheduler
import com.wild.android.ble.DeviceSessionUiState

/** Small last-read summaries ride the existing throttled device document.
 * No waveform samples, parameter images, extra listener, or polling is added. */
internal fun cloudDeviceDetails(session: DeviceSessionUiState): Map<String, Any> = buildMap {
    session.parsedSystemParams?.let { params ->
        put("acquisition", mapOf(
            "reportedAtMs" to session.configurationReportedAtMs,
            "channelCount" to params.ephysChannelCount,
            "sampleRateHz" to params.ephysSamplingRate,
            "cameraEnabled" to params.cameraEnabled,
            "adcEnabled" to params.adcEnabled,
            "stimulationEnabled" to params.stimEnabled,
            "pendingApply" to session.configurationPendingApply,
        ))
    }
    session.advertisedHealthStatus?.takeIf { !it.isAiAdvertisementPage || session.lastHealthAdvertisementAtMs > 0 }?.let { status ->
        put("health", mapOf(
            "reportedAtMs" to session.lastHealthAdvertisementAtMs,
            "failedSubsystems" to status.failedSubsystems,
            "degradedSubsystems" to status.degradedSubsystems,
            "lastEventCode" to status.lastEventCode,
        ))
    }
    session.schedulerStatus?.let { status ->
        put("scheduler", buildMap<String, Any> {
            put("reportedAtMs", session.schedulerReportedAtMs)
            put("version", status.version)
            put("enabled", status.enabled)
            put("clockValid", status.clockValid)
            put("activeIdle", status.activeIdle)
            // -1 explicitly clears a previously reported wake, rather than
            // retaining an old wake when Firestore merges a sparse update.
            put("nextWakeSeconds", status.nextWakeSeconds ?: -1L)
            put("activeRuleId", status.activeRuleId)
            put("lastResult", status.lastResult)
            put("lastResultLabel", Ce64Scheduler.resultLabel(status.lastResult))
            put("deferredCount", status.deferredCount)
            put("missedCount", status.missedCount)
            put("conflictCount", status.conflictCount)
            session.schedulerConfig?.let { config ->
                put("enabledRuleCount", config.rules.count { it.enabled })
                put("savedProfileCount", config.profileGenerations.count { it > 0 })
            }
        })
    }
    session.aiRuntimeStatus?.let { status ->
        put("aiRuntime", mapOf(
            "running" to status.running, "requestedEnabled" to status.requestedEnabled,
            "activeSlot" to (status.activeSlot ?: -1), "statusCode" to status.statusCode,
            "layoutReady" to status.layoutReady,
        ))
    }
    if (session.spikeDetectorConfig != null || session.spectrumConfig != null) {
        put("analysis", buildMap<String, Any> {
            session.spikeDetectorConfig?.let { put("spikeEnabled", it.enabled) }
            session.spectrumConfig?.let { put("spectrumEnabled", it.enabled) }
        })
    }
}

/** Observation time changing alone must not consume an additional cloud write. */
internal fun cloudDetailsFingerprint(details: Map<String, Any>): String = details.toSortedMap().entries.joinToString("|") { (name, fields) ->
    val stable = (fields as? Map<*, *>)?.entries?.filterNot { it.key == "reportedAtMs" }
        ?.sortedBy { it.key.toString() }?.joinToString(",") { "${it.key}=${it.value}" }.orEmpty()
    "$name:$stable"
}
