package com.wild.android.cloud

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.wild.android.ble.AdvertisementStatusSampleUiState
import com.wild.android.ble.BleHostSessionState
import com.wild.android.ble.DeviceSessionUiState
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal const val ActiveCloudHeartbeatMs = 5 * 60_000L
internal const val PassiveAdvertisementCloudHeartbeatMs = 15 * 60_000L
internal const val CloudAdvertisementFreshMs = 10 * 60_000L
internal const val CloudReportRetentionMs = 60 * 60_000L
// The detailed dashboard history is sampled in memory at most once per
// minute, then uploaded only with the existing five- or fifteen-minute status
// heartbeat. This gives useful charts without a Firestore write per BLE scan.
internal const val CloudHistorySampleIntervalMs = 60_000L
internal const val CloudHistoryPendingWindowMs = 20 * 60_000L
internal const val CloudHistoryDayMs = 24 * 60 * 60_000L

/**
 * The cloud fleet is an operational view, not an archive of every device the
 * phone has ever discovered. Keep connected/control attempts, and only keep a
 * passive advertisement while it is genuinely being seen by this gateway.
 */
internal fun shouldPublishCloudSession(session: DeviceSessionUiState, nowMs: Long): Boolean {
    if (session.isConnected || session.isLinkingLike) {
        return true
    }
    return session.bulkConnectEligible &&
        session.lastSeenAtMs > 0L &&
        nowMs - session.lastSeenAtMs <= CloudAdvertisementFreshMs
}

internal fun cloudHeartbeatIntervalMs(session: DeviceSessionUiState): Long {
    return if (session.isConnected || session.isLinkingLike) {
        ActiveCloudHeartbeatMs
    } else {
        PassiveAdvertisementCloudHeartbeatMs
    }
}

/**
 * Publishes a compact, privacy-conscious view of the local BLE fleet to
 * Firestore. It deliberately excludes waveform samples, recording files,
 * BLE addresses, command payloads, contacts, and files. The configured Bluetooth
 * phone name is included solely as a human-readable gateway label so a shared
 * fleet can distinguish gateway phones.
 *
 * A signed-in Firebase user owns a fleet at `fleets/{uid}`. The same user can
 * sign in on another WILD phone to see its remote fleet view. Cloud publishing
 * starts only after a user explicitly signs in; this keeps ordinary local BLE
 * use private and avoids spending Firestore quota for anonymous gateways.
 */
class FirebaseFleetGateway(
    context: Context,
    private val appScope: CoroutineScope,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
    private val gatewayId = preferences.getString(GatewayIdPreference, null)
        ?: UUID.randomUUID().toString().also { generated ->
            preferences.edit().putString(GatewayIdPreference, generated).apply()
        }
    private val gatewayLabel = resolveGatewayLabel(context, gatewayId)

    private val _state = MutableStateFlow(
        CloudFleetGatewayState(
            gatewayId = gatewayId,
            message = "Cloud gateway is preparing.",
        ),
    )
    val state: StateFlow<CloudFleetGatewayState> = _state.asStateFlow()

    private var pendingSessions: List<DeviceSessionUiState> = emptyList()
    private var publishJob: Job? = null
    private var remoteFleetRegistration: ListenerRegistration? = null
    private var listeningUid: String? = null
    private var latestStatusFingerprint = ""
    private val latestDeviceFingerprints = mutableMapOf<String, String>()
    private val latestDevicePublishedAtMs = mutableMapOf<String, Long>()
    private val pendingHistorySamples = mutableMapOf<String, MutableMap<Long, CloudFleetHistorySample>>()
    private val latestCompleteAdvertisementSamples = mutableMapOf<String, CloudFleetHistorySample>()
    private var lastPublishedAtMs = 0L
    private var scheduledFingerprint: String? = null
    private var publishedOwnerId: String? = null
    private var remoteFleetViewVisible = false

    fun publishLocalFleet(sessions: Collection<DeviceSessionUiState>) {
        val now = nowMs()
        pendingSessions = sessions.filter { session ->
            shouldPublishCloudSession(session, now)
        }
        if (!hasSharedAccount()) {
            publishJob?.cancel()
            publishJob = null
            showCloudSharingDisabled()
            return
        }

        captureHistorySamples(pendingSessions, now)

        val fingerprint = cloudStatusFingerprint(pendingSessions)
        if (
            fingerprint == latestStatusFingerprint &&
            now - lastPublishedAtMs < PublishEvaluationIntervalMs
        ) {
            return
        }

        // Scanners can update their local state many times per second. When
        // the cloud-relevant state has not changed, retain the existing
        // scheduled publish instead of continually cancelling its debounce.
        if (publishJob?.isActive == true && scheduledFingerprint == fingerprint) {
            return
        }

        publishJob?.cancel()
        scheduledFingerprint = fingerprint
        publishJob = appScope.launch {
            delay(PublishDebounceMs)
            scheduledFingerprint = null
            publishLatestFleet()
        }
    }

    /**
     * Keeps cross-phone updates live only while the Cloud screen is visible.
     * A phone acting only as a gateway no longer consumes a Firestore listener
     * read for every update received by every other gateway.
     */
    fun setRemoteFleetViewVisible(visible: Boolean) {
        remoteFleetViewVisible = visible
        val user = sharedUser()
        if (visible && user != null) {
            onSignedIn(user)
        } else if (!visible) {
            stopRemoteFleetListener(clearDevices = false)
        } else {
            showCloudSharingDisabled()
        }
    }

    fun signIn(email: String, password: String) {
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank() || password.isBlank()) {
            _state.value = _state.value.copy(
                phase = CloudFleetPhase.NeedsSetup,
                message = "Enter both an email address and password to share this fleet.",
            )
            return
        }

        _state.value = _state.value.copy(
            phase = CloudFleetPhase.SigningIn,
            message = "Signing in to the shared fleet…",
        )
        auth.signInWithEmailAndPassword(cleanEmail, password)
            .addOnSuccessListener { result ->
                result.user?.let { user ->
                    onSignedIn(user)
                    publishLocalFleet(pendingSessions)
                } ?: reportAuthFailure(IllegalStateException("Firebase did not return the signed-in user."))
            }
            .addOnFailureListener(::reportAuthFailure)
    }

    fun createSharedAccount(email: String, password: String) {
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank() || password.length < MinimumPasswordLength) {
            _state.value = _state.value.copy(
                phase = CloudFleetPhase.NeedsSetup,
                message = "Use an email address and a password with at least $MinimumPasswordLength characters.",
            )
            return
        }

        _state.value = _state.value.copy(
            phase = CloudFleetPhase.SigningIn,
            message = "Creating the shared fleet account…",
        )
        auth.createUserWithEmailAndPassword(cleanEmail, password)
            .addOnSuccessListener { result ->
                result.user?.let { user ->
                    onSignedIn(user)
                    publishLocalFleet(pendingSessions)
                } ?: reportAuthFailure(IllegalStateException("Firebase did not return the new account."))
            }
            .addOnFailureListener(::reportAuthFailure)
    }

    fun signOutOfSharedFleet() {
        publishJob?.cancel()
        publishJob = null
        stopRemoteFleetListener(clearDevices = true)
        auth.signOut()
        resetPublishCache()
        _state.value = CloudFleetGatewayState(
            gatewayId = gatewayId,
            phase = CloudFleetPhase.Guest,
            message = "Cloud sharing is off. Sign in when you want to share device status.",
        )
    }

    private fun publishLatestFleet() {
        val publishedAt = nowMs()
        val reportableSessions = pendingSessions.filter { session ->
            shouldPublishCloudSession(session, publishedAt)
        }
        val fingerprint = cloudStatusFingerprint(reportableSessions)
        if (
            fingerprint == latestStatusFingerprint &&
            publishedAt - lastPublishedAtMs < PublishEvaluationIntervalMs
        ) {
            return
        }

        ensureSharedIdentity { user ->
            val userId = user.uid
            val deviceCollection = firestore.collection(FleetsCollection)
                .document(userId)
                .collection(DevicesCollection)
            val historyCollection = firestore.collection(FleetsCollection)
                .document(userId)
                .collection(HistoryCollection)
            val snapshots = reportableSessions.map { session ->
                cloudDeviceSnapshot(
                    session = session,
                    gatewayId = gatewayId,
                    gatewayLabel = gatewayLabel,
                    publishedAtMs = publishedAt,
                )
            }
            val snapshotsToPublish = snapshots.filter { snapshot ->
                latestDeviceFingerprints[snapshot.documentId] != cloudSnapshotFingerprint(snapshot) ||
                    publishedAt - (latestDevicePublishedAtMs[snapshot.documentId] ?: 0L) >=
                    snapshot.heartbeatIntervalMs
            }
            if (snapshotsToPublish.isEmpty()) {
                latestStatusFingerprint = fingerprint
                lastPublishedAtMs = publishedAt
                return@ensureSharedIdentity
            }

            val batch = firestore.batch()
            snapshotsToPublish.forEach { snapshot ->
                batch.set(
                    deviceCollection.document(snapshot.documentId),
                    snapshot.toFirestoreFields(),
                    SetOptions.merge(),
                )
            }
            val historySamplesToPublish = snapshotsToPublish.associate { snapshot ->
                snapshot.documentId to historySamplesFor(snapshot)
            }
            snapshotsToPublish.forEach { snapshot ->
                historySamplesToPublish[snapshot.documentId].orEmpty()
                    .groupBy { sample -> cloudHistoryDayStartMs(sample.timestampMs) }
                    .forEach { (dayStartMs, samples) ->
                        batch.set(
                            historyCollection.document(cloudHistoryDocumentId(snapshot.documentId, dayStartMs)),
                            snapshot.toHistoryFirestoreFields(dayStartMs, samples),
                            SetOptions.merge(),
                        )
                    }
            }
            batch.commit()
                .addOnSuccessListener {
                    if (sharedUser()?.uid != userId) {
                        return@addOnSuccessListener
                    }
                    latestStatusFingerprint = fingerprint
                    lastPublishedAtMs = publishedAt
                    snapshotsToPublish.forEach { snapshot ->
                        latestDeviceFingerprints[snapshot.documentId] = cloudSnapshotFingerprint(snapshot)
                        latestDevicePublishedAtMs[snapshot.documentId] = publishedAt
                    }
                    historySamplesToPublish.forEach { (documentId, samples) ->
                        val pendingSamples = pendingHistorySamples[documentId] ?: return@forEach
                        samples.forEach { sample ->
                            if (pendingSamples[sample.timestampMs] == sample) {
                                pendingSamples.remove(sample.timestampMs)
                            }
                        }
                        if (pendingSamples.isEmpty()) {
                            pendingHistorySamples.remove(documentId)
                        }
                    }
                    _state.value = _state.value.copy(
                        phase = CloudFleetPhase.Online,
                        fleetOwnerId = userId,
                        accountEmail = user.email,
                        sharedAcrossPhones = !user.isAnonymous,
                        lastPublishedAtMs = publishedAt,
                        message = cloudReadyMessage(user, pendingSessions.size),
                    )
                }
                .addOnFailureListener(::reportFirestoreFailure)
        }
    }

    private fun ensureSharedIdentity(onReady: (FirebaseUser) -> Unit) {
        sharedUser()?.let { existing ->
            onSignedIn(existing)
            onReady(existing)
            return
        }
        showCloudSharingDisabled()
    }

    private fun onSignedIn(user: FirebaseUser) {
        if (publishedOwnerId != user.uid) {
            resetPublishCache()
            publishedOwnerId = user.uid
        }
        _state.value = _state.value.copy(
            phase = CloudFleetPhase.Online,
            fleetOwnerId = user.uid,
            accountEmail = user.email,
            sharedAcrossPhones = true,
            message = cloudReadyMessage(user, _state.value.remoteDevices.size),
        )
        if (remoteFleetViewVisible) {
            startRemoteFleetListener(user)
        }
    }

    private fun startRemoteFleetListener(user: FirebaseUser) {
        if (listeningUid == user.uid && remoteFleetRegistration != null) return

        stopRemoteFleetListener(clearDevices = false)
        listeningUid = user.uid
        val reportQueryStartMs = nowMs() - CloudReportRetentionMs
        remoteFleetRegistration = firestore
            .collection(FleetsCollection)
            .document(user.uid)
            .collection(DevicesCollection)
            .whereGreaterThanOrEqualTo("lastPublishedAtMs", reportQueryStartMs)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    reportFirestoreFailure(error)
                    return@addSnapshotListener
                }
                val remoteDevices = snapshots?.documents
                    ?.mapNotNull(::remoteDeviceFromDocument)
                    ?.sortedWith(
                        compareByDescending<RemoteFleetDeviceUiState> { it.connected }
                            .thenByDescending { it.lastPublishedAtMs }
                            .thenBy { it.displayName.lowercase() },
                    )
                    .orEmpty()
                _state.value = _state.value.copy(
                    phase = CloudFleetPhase.Online,
                    fleetOwnerId = user.uid,
                    accountEmail = user.email,
                    sharedAcrossPhones = !user.isAnonymous,
                    remoteDevices = remoteDevices,
                    message = cloudReadyMessage(user, remoteDevices.size),
                )
            }
    }

    private fun stopRemoteFleetListener(clearDevices: Boolean) {
        remoteFleetRegistration?.remove()
        remoteFleetRegistration = null
        listeningUid = null
        if (clearDevices) {
            _state.value = _state.value.copy(remoteDevices = emptyList())
        }
    }

    private fun hasSharedAccount(): Boolean = sharedUser() != null

    private fun sharedUser(): FirebaseUser? = auth.currentUser?.takeUnless { it.isAnonymous }

    private fun showCloudSharingDisabled() {
        if (_state.value.phase == CloudFleetPhase.Guest &&
            _state.value.message.startsWith("Cloud sharing is off")
        ) {
            return
        }
        stopRemoteFleetListener(clearDevices = false)
        _state.value = _state.value.copy(
            phase = CloudFleetPhase.Guest,
            fleetOwnerId = null,
            accountEmail = null,
            sharedAcrossPhones = false,
            lastPublishedAtMs = null,
            message = "Cloud sharing is off. Sign in to share status across phones or the dashboard.",
        )
    }

    private fun resetPublishCache() {
        latestStatusFingerprint = ""
        latestDeviceFingerprints.clear()
        latestDevicePublishedAtMs.clear()
        pendingHistorySamples.clear()
        latestCompleteAdvertisementSamples.clear()
        lastPublishedAtMs = 0L
        scheduledFingerprint = null
        publishedOwnerId = null
    }

    private fun reportAuthFailure(error: Exception) {
        _state.value = _state.value.copy(
            phase = CloudFleetPhase.NeedsSetup,
            message = "Cloud sign-in needs Firebase Authentication: ${compactCloudError(error)}",
        )
    }

    private fun reportFirestoreFailure(error: Exception) {
        _state.value = _state.value.copy(
            phase = CloudFleetPhase.NeedsSetup,
            message = "Cloud status needs Firestore and its rules: ${compactCloudError(error)}",
        )
    }

    private fun cloudReadyMessage(user: FirebaseUser, deviceCount: Int): String {
        return "$deviceCount device status ${plural(deviceCount, "entry", "entries")} available to this Firebase account."
    }

    private fun captureHistorySamples(sessions: Collection<DeviceSessionUiState>, capturedAtMs: Long) {
        sessions.forEach { session ->
            val observedAtMs = if (session.isConnected || session.isLinkingLike) {
                capturedAtMs
            } else {
                session.lastSeenAtMs
            }
            if (observedAtMs <= 0L) return@forEach

            val documentId = "${gatewayId}_${cloudDeviceKey(session.id)}"
            latestCompleteAdvertisementHistorySample(session, observedAtMs)
                ?.let { sample -> latestCompleteAdvertisementSamples[documentId] = sample }
            val completeAdvertisement = latestCompleteAdvertisementSamples[documentId]
                ?.takeIf { sample -> observedAtMs - sample.sourceAdvertisementAtMs <= CloudAdvertisementFreshMs }
                ?: return@forEach
            // The time axis is the periodic cloud sample time. The attached
            // advertisement timestamp still tells us which full CE64 health
            // payload supplied the values. Sparse packets never overwrite it.
            val sample = completeAdvertisement.copy(
                timestampMs = cloudHistoryMinuteBucket(observedAtMs),
                connected = session.isConnected,
            )
            val samplesForDevice = pendingHistorySamples.getOrPut(documentId) { mutableMapOf() }
            samplesForDevice[sample.timestampMs] = sample
            val cutoffMs = capturedAtMs - CloudHistoryPendingWindowMs
            samplesForDevice.entries.removeAll { (timestampMs, _) -> timestampMs < cutoffMs }
        }
        latestCompleteAdvertisementSamples.entries.removeAll { (_, sample) ->
            capturedAtMs - sample.sourceAdvertisementAtMs > CloudAdvertisementFreshMs
        }
    }

    private fun historySamplesFor(snapshot: CloudFleetDeviceSnapshot): List<CloudFleetHistorySample> {
        val pendingSamples = pendingHistorySamples[snapshot.documentId]
            ?.values
            ?.filter { sample -> sample.timestampMs <= snapshot.lastPublishedAtMs }
            .orEmpty()
        return pendingSamples
    }

    private companion object {
        const val PreferencesName = "firebase_fleet_gateway"
        const val GatewayIdPreference = "gateway_id"
        const val FleetsCollection = "fleets"
        const val DevicesCollection = "devices"
        const val HistoryCollection = "history"
        const val PublishDebounceMs = 10_000L
        const val PublishEvaluationIntervalMs = 5 * 60_000L
        const val MinimumPasswordLength = 6
    }
}

enum class CloudFleetPhase {
    Guest,
    SigningIn,
    Online,
    NeedsSetup,
}

data class CloudFleetGatewayState(
    val gatewayId: String = "",
    val phase: CloudFleetPhase = CloudFleetPhase.Guest,
    val fleetOwnerId: String? = null,
    val accountEmail: String? = null,
    val sharedAcrossPhones: Boolean = false,
    val lastPublishedAtMs: Long? = null,
    val remoteDevices: List<RemoteFleetDeviceUiState> = emptyList(),
    val message: String = "Cloud gateway is unavailable.",
)

data class RemoteFleetDeviceUiState(
    val documentId: String,
    val gatewayId: String,
    val gatewayLabel: String,
    val displayName: String,
    val hostState: String,
    val connected: Boolean,
    val recording: Boolean,
    val previewing: Boolean,
    val rssiDbm: Int?,
    val batteryVolts: Double?,
    val storageUsedMb: Double?,
    val storageUsedPercent: Int?,
    val auxTemperatureCelsius: Double?,
    val mcuTemperatureCelsius: Double?,
    val recordingSeconds: Long,
    val advertisedSampleRateHz: Int?,
    val aiModelId: Int?,
    val aiClassId: Int?,
    val aiConfidencePercentage: Int?,
    val aiEventSequence: Int?,
    val aiResultAgeSeconds: Int?,
    val aiResultIsNew: Boolean,
    val aiResultAdvertisedAtMs: Long?,
    val firmwareVersion: String?,
    val hardwareVersion: String?,
    val lastSeenAtMs: Long,
    val lastPublishedAtMs: Long,
)

internal data class CloudFleetDeviceSnapshot(
    val documentId: String,
    val gatewayId: String,
    val gatewayLabel: String,
    val displayName: String,
    val hostState: String,
    val connected: Boolean,
    val recording: Boolean,
    val previewing: Boolean,
    val rssiDbm: Int?,
    val batteryVolts: Double?,
    val storageUsedMb: Double?,
    val storageUsedPercent: Int?,
    val auxTemperatureCelsius: Double?,
    val mcuTemperatureCelsius: Double?,
    val hasTemperatureTelemetry: Boolean,
    val recordingSeconds: Long,
    val advertisedSampleRateHz: Int?,
    val aiModelId: Int?,
    val aiClassId: Int?,
    val aiConfidencePercentage: Int?,
    val aiEventSequence: Int?,
    val aiResultAgeSeconds: Int?,
    val aiResultIsNew: Boolean,
    val aiResultAdvertisedAtMs: Long?,
    val firmwareVersion: String?,
    val hardwareVersion: String?,
    val lastSeenAtMs: Long,
    val lastPublishedAtMs: Long,
    val heartbeatIntervalMs: Long,
    val deviceDetails: Map<String, Any> = emptyMap(),
    val aiHasResult: Boolean? = null,
    val aiStatusReportedAtMs: Long? = null,
    val statusAdvertisedAtMs: Long? = null,
) {
    fun toFirestoreFields(): Map<String, Any> = buildMap {
        put("gatewayId", gatewayId)
        put("gatewayLabel", gatewayLabel)
        put("displayName", displayName)
        put("hostState", hostState)
        put("connected", connected)
        put("recording", recording)
        put("previewing", previewing)
        put("recordingSeconds", recordingSeconds)
        put("lastSeenAtMs", lastSeenAtMs)
        put("lastPublishedAtMs", lastPublishedAtMs)
        put("updatedAt", FieldValue.serverTimestamp())
        if (deviceDetails.isNotEmpty()) put("deviceDetails", deviceDetails)
        aiHasResult?.let { put("aiHasResult", it) }
        aiStatusReportedAtMs?.let { put("aiStatusReportedAtMs", it) }
        statusAdvertisedAtMs?.let { put("statusAdvertisedAtMs", it) }
        rssiDbm?.let { put("rssiDbm", it) }
        batteryVolts?.let { put("batteryVolts", it) }
        storageUsedMb?.let { put("storageUsedMb", it) }
        storageUsedPercent?.let { put("storageUsedPercent", it) }
        advertisedSampleRateHz?.let { put("advertisedSampleRateHz", it) }
        aiModelId?.let { put("aiModelId", it) }
        aiClassId?.let { put("aiClassId", it) }
        aiConfidencePercentage?.let { put("aiConfidencePercentage", it) }
        aiEventSequence?.let { put("aiEventSequence", it) }
        aiResultAgeSeconds?.let { put("aiResultAgeSeconds", it) }
        if (aiModelId != null) {
            put("aiResultIsNew", aiResultIsNew)
            aiResultAdvertisedAtMs?.let { put("aiResultAdvertisedAtMs", it) }
        }
        if (hasTemperatureTelemetry) {
            put("auxTemperatureCelsius", auxTemperatureCelsius ?: FieldValue.delete())
            put("mcuTemperatureCelsius", mcuTemperatureCelsius ?: FieldValue.delete())
        }
        firmwareVersion?.takeIf { it.isNotBlank() }?.let { put("firmwareVersion", it) }
        hardwareVersion?.takeIf { it.isNotBlank() }?.let { put("hardwareVersion", it) }
    }

    fun toHistoryFirestoreFields(
        dayStartMs: Long,
        samples: List<CloudFleetHistorySample>,
    ): Map<String, Any> = buildMap {
        put("deviceDocumentId", documentId)
        put("gatewayId", gatewayId)
        put("gatewayLabel", gatewayLabel)
        put("displayName", displayName)
        put("dayStartMs", dayStartMs)
        put("lastSampleAtMs", samples.maxOf { sample -> sample.timestampMs })
        put("updatedAt", FieldValue.serverTimestamp())
        // Firestore treats this as an atomic append. A daily document keeps
        // seven-day chart reads small, while minute-bucket timestamps make the
        // dashboard immune to a retry of the same heartbeat.
        put("samples", FieldValue.arrayUnion(*samples.map { it.toFirestoreFields() }.toTypedArray()))
    }
}

/** A compact, minute-bucketed telemetry point for the cloud dashboard. */
internal data class CloudFleetHistorySample(
    val timestampMs: Long,
    val sourceAdvertisementAtMs: Long,
    val rssiDbm: Int?,
    val batteryCentivolts: Int?,
    val storageUsedPercent: Int?,
    val recordingSeconds: Long,
    val connected: Boolean,
    val recording: Boolean,
) {
    fun toFirestoreFields(): Map<String, Any> = buildMap {
        put("t", timestampMs)
        put("a", sourceAdvertisementAtMs)
        rssiDbm?.let { put("r", it) }
        batteryCentivolts?.let { put("v", it) }
        storageUsedPercent?.let { put("p", it) }
        put("d", recordingSeconds)
        put("c", connected)
        put("g", recording)
    }
}

internal fun cloudDeviceSnapshot(
    session: DeviceSessionUiState,
    gatewayId: String,
    gatewayLabel: String,
    publishedAtMs: Long,
): CloudFleetDeviceSnapshot {
    val advertisedStatus = session.advertisedHealthStatus
    return CloudFleetDeviceSnapshot(
        documentId = "${gatewayId}_${cloudDeviceKey(session.id)}",
        gatewayId = gatewayId,
        gatewayLabel = gatewayLabel.take(MaxGatewayLabelLength),
        displayName = session.name.ifBlank { "WILD device" }.take(MaxDisplayNameLength),
        hostState = session.hostState.name,
        connected = session.isConnected,
        recording = session.isRecordingLike || advertisedStatus?.recording == true,
        previewing = session.hostState == BleHostSessionState.Previewing ||
            session.waveformPreviewActive ||
            advertisedStatus?.previewing == true,
        rssiDbm = session.rssi,
        batteryVolts = session.voltage ?: session.advertisedVoltage,
        storageUsedMb = session.usedSpaceMb,
        storageUsedPercent = advertisedStatus?.storageUsedPercent,
        auxTemperatureCelsius = advertisedStatus?.auxTemperatureCelsius,
        mcuTemperatureCelsius = advertisedStatus?.mcuTemperatureCelsius,
        hasTemperatureTelemetry = advertisedStatus?.hasTemperatureTelemetry == true,
        recordingSeconds = maxOf(session.recordingSeconds, advertisedStatus?.recordingSeconds ?: 0L),
        advertisedSampleRateHz = advertisedStatus?.advertisedSampleRateHz,
        aiModelId = advertisedStatus?.aiModelId,
        aiClassId = advertisedStatus?.aiClassId,
        aiConfidencePercentage = advertisedStatus?.aiConfidencePercentage,
        aiEventSequence = advertisedStatus?.aiEventSequence,
        aiResultAgeSeconds = advertisedStatus?.aiResultAgeSeconds,
        aiResultIsNew = advertisedStatus?.aiResultIsNew == true,
        aiResultAdvertisedAtMs = session.lastAiResultAtMs.takeIf { it > 0L },
        firmwareVersion = session.swVersion,
        hardwareVersion = session.hwVersion,
        lastSeenAtMs = session.lastSeenAtMs.takeIf { it > 0L } ?: publishedAtMs,
        lastPublishedAtMs = publishedAtMs,
        heartbeatIntervalMs = cloudHeartbeatIntervalMs(session),
        deviceDetails = cloudDeviceDetails(session),
        aiHasResult = advertisedStatus?.hasAiResult?.takeIf { session.lastAiAdvertisementAtMs > 0L },
        aiStatusReportedAtMs = session.lastAiAdvertisementAtMs.takeIf { it > 0L },
        // Explicit zero distinguishes a new gateway that has never seen a
        // status packet from legacy gateways that don't report provenance.
        statusAdvertisedAtMs = session.lastStatusAdvertisementAtMs,
    )
}

/**
 * A chart point must come from a full CE64 health advertisement, rather than
 * composing fields across sparse advertisements or a stale connection packet.
 * Storage remains optional because older firmware can mark that byte invalid.
 */
internal fun isCompleteCloudHistoryAdvertisement(sample: AdvertisementStatusSampleUiState): Boolean {
    return sample.voltage != null && sample.recording != null && sample.recordingSeconds != null
}

internal fun latestCompleteAdvertisementHistorySample(
    session: DeviceSessionUiState,
    observedAtMs: Long,
): CloudFleetHistorySample? {
    val advertisement = session.advertisementHistory
        .asReversed()
        .firstOrNull(::isCompleteCloudHistoryAdvertisement)
        ?.takeIf { sample -> observedAtMs - sample.timestampMs <= CloudAdvertisementFreshMs }
        ?: return null
    val rssi = session.rssiHistory
        .asReversed()
        .firstOrNull { sample -> sample.timestampMs <= advertisement.timestampMs }
        ?.valueDbm
        ?: session.rssi
    return CloudFleetHistorySample(
        timestampMs = cloudHistoryMinuteBucket(observedAtMs),
        sourceAdvertisementAtMs = advertisement.timestampMs,
        rssiDbm = rssi,
        batteryCentivolts = advertisement.voltage
            ?.takeIf { value -> value.isFinite() }
            ?.let { value -> kotlin.math.round(value * 100.0).toInt() },
        storageUsedPercent = advertisement.storageUsedPercent,
        recordingSeconds = advertisement.recordingSeconds ?: 0L,
        connected = session.isConnected,
        recording = advertisement.recording == true,
    )
}

internal fun cloudHistoryMinuteBucket(timestampMs: Long): Long {
    return Math.floorDiv(timestampMs, CloudHistorySampleIntervalMs) * CloudHistorySampleIntervalMs
}

internal fun cloudHistoryDayStartMs(timestampMs: Long): Long {
    return Math.floorDiv(timestampMs, CloudHistoryDayMs) * CloudHistoryDayMs
}

internal fun cloudHistoryDocumentId(deviceDocumentId: String, dayStartMs: Long): String {
    return "${deviceDocumentId}_$dayStartMs"
}

internal fun cloudDeviceKey(localDeviceId: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(localDeviceId.toByteArray(Charsets.UTF_8))
    return digest.take(DeviceKeyBytes).joinToString(separator = "") { byte ->
        "%02x".format(byte)
    }
}

internal fun cloudStatusFingerprint(sessions: Collection<DeviceSessionUiState>): String {
    return sessions
        .sortedBy { it.id }
        .joinToString(separator = "|") { session ->
            val advertisedStatus = session.advertisedHealthStatus
            listOf(
                session.id,
                session.name,
                session.hostState.name,
                session.isConnected.toString(),
                (session.isRecordingLike || advertisedStatus?.recording == true).toString(),
                (session.waveformPreviewActive || advertisedStatus?.previewing == true).toString(),
                cloudMetricBucket(session.voltage ?: session.advertisedVoltage, VoltageBucketVolts).toString(),
                cloudMetricBucket(session.usedSpaceMb, StorageBucketMb).toString(),
                advertisedStatus?.storageUsedPercent?.toString().orEmpty(),
                cloudMetricBucket(advertisedStatus?.auxTemperatureCelsius, 0.1).toString(),
                cloudMetricBucket(advertisedStatus?.mcuTemperatureCelsius, 0.1).toString(),
                (maxOf(session.recordingSeconds, advertisedStatus?.recordingSeconds ?: 0L) /
                    RecordingSecondsBucket).toString(),
                advertisedStatus?.advertisedSampleRateHz?.toString().orEmpty(),
                // AI observations can change on every advertisement. They ride
                // the heartbeat, not a standalone write for every inference.
                cloudDetailsFingerprint(cloudDeviceDetails(session)),
                session.swVersion.orEmpty(),
                session.hwVersion.orEmpty(),
            ).joinToString(separator = ",")
        }
}

/**
 * Fields that justify a standalone device update. RSSI and last-seen time are
 * intentionally omitted: they are refreshed by the five-minute health
 * heartbeat instead of generating a Firestore write for every BLE scan.
 */
internal fun cloudSnapshotFingerprint(snapshot: CloudFleetDeviceSnapshot): String {
    return listOf(
        snapshot.gatewayId,
        snapshot.gatewayLabel,
        snapshot.displayName,
        snapshot.hostState,
        snapshot.connected.toString(),
        snapshot.recording.toString(),
        snapshot.previewing.toString(),
        cloudMetricBucket(snapshot.batteryVolts, VoltageBucketVolts).toString(),
        cloudMetricBucket(snapshot.storageUsedMb, StorageBucketMb).toString(),
        snapshot.storageUsedPercent?.toString().orEmpty(),
        cloudMetricBucket(snapshot.auxTemperatureCelsius, 0.1).toString(),
        cloudMetricBucket(snapshot.mcuTemperatureCelsius, 0.1).toString(),
        (snapshot.recordingSeconds / RecordingSecondsBucket).toString(),
        snapshot.advertisedSampleRateHz?.toString().orEmpty(),
        cloudDetailsFingerprint(snapshot.deviceDetails),
        snapshot.firmwareVersion.orEmpty(),
        snapshot.hardwareVersion.orEmpty(),
    ).joinToString(separator = ",")
}

private fun cloudMetricBucket(value: Double?, step: Double): Long? {
    return value
        ?.takeIf { it.isFinite() }
        ?.let { kotlin.math.round(it / step).toLong() }
}

private fun remoteDeviceFromDocument(document: DocumentSnapshot): RemoteFleetDeviceUiState? {
    val data = document.data ?: return null
    val displayName = data["displayName"] as? String ?: return null
    return RemoteFleetDeviceUiState(
        documentId = document.id,
        gatewayId = data["gatewayId"] as? String ?: "",
        gatewayLabel = data["gatewayLabel"] as? String ?: "WILD Android gateway",
        displayName = displayName,
        hostState = data["hostState"] as? String ?: "Unknown",
        connected = data["connected"] as? Boolean ?: false,
        recording = data["recording"] as? Boolean ?: false,
        previewing = data["previewing"] as? Boolean ?: false,
        rssiDbm = (data["rssiDbm"] as? Number)?.toInt(),
        batteryVolts = (data["batteryVolts"] as? Number)?.toDouble(),
        storageUsedMb = (data["storageUsedMb"] as? Number)?.toDouble(),
        storageUsedPercent = (data["storageUsedPercent"] as? Number)?.toInt(),
        auxTemperatureCelsius = (data["auxTemperatureCelsius"] as? Number)?.toDouble(),
        mcuTemperatureCelsius = (data["mcuTemperatureCelsius"] as? Number)?.toDouble(),
        recordingSeconds = (data["recordingSeconds"] as? Number)?.toLong() ?: 0L,
        advertisedSampleRateHz = (data["advertisedSampleRateHz"] as? Number)?.toInt(),
        aiModelId = (data["aiModelId"] as? Number)?.toInt(),
        aiClassId = (data["aiClassId"] as? Number)?.toInt(),
        aiConfidencePercentage = (data["aiConfidencePercentage"] as? Number)?.toInt(),
        aiEventSequence = (data["aiEventSequence"] as? Number)?.toInt(),
        aiResultAgeSeconds = (data["aiResultAgeSeconds"] as? Number)?.toInt(),
        aiResultIsNew = data["aiResultIsNew"] as? Boolean ?: false,
        aiResultAdvertisedAtMs = (data["aiResultAdvertisedAtMs"] as? Number)?.toLong(),
        firmwareVersion = data["firmwareVersion"] as? String,
        hardwareVersion = data["hardwareVersion"] as? String,
        lastSeenAtMs = (data["lastSeenAtMs"] as? Number)?.toLong() ?: 0L,
        lastPublishedAtMs = (data["lastPublishedAtMs"] as? Number)?.toLong() ?: 0L,
    )
}

private fun resolveGatewayLabel(context: Context, gatewayId: String): String {
    val bluetoothName = if (
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    ) {
        runCatching {
            context.getSystemService(BluetoothManager::class.java)?.adapter?.name
        }.getOrNull()
    } else {
        null
    }
    val fallback = listOf(Build.MANUFACTURER, Build.MODEL)
        .joinToString(separator = " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .ifBlank { "WILD Android gateway" }
    val phoneName = bluetoothName
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: fallback
    return "$phoneName · ${gatewayId.takeLast(GatewayLabelIdCharacters).uppercase()}"
        .take(MaxGatewayLabelLength)
}

private fun compactCloudError(error: Exception): String {
    val message = error.message?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
    return message.takeIf { it.isNotBlank() }?.take(MaxErrorLength) ?: "connection was rejected"
}

private fun plural(count: Int, singular: String, plural: String): String {
    return if (count == 1) singular else plural
}

private const val DeviceKeyBytes = 12
private const val MaxDisplayNameLength = 80
private const val MaxGatewayLabelLength = 80
private const val MaxErrorLength = 120
private const val GatewayLabelIdCharacters = 4
private const val VoltageBucketVolts = 0.02
private const val StorageBucketMb = 5.0
private const val RecordingSecondsBucket = 5 * 60L
