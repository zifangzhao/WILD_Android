package com.wild.android.ui

import android.content.res.Configuration
import android.graphics.Bitmap
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.SettingsInputAntenna
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold

import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wild.android.WildUiState
import com.wild.android.WildViewModel
import com.wild.android.R
import com.wild.android.ble.BleHostSessionState
import com.wild.android.ble.BleOtaPhase
import com.wild.android.ble.BleLinkStatsUiState
import com.wild.android.ble.AdvertisementStatusSampleUiState
import com.wild.android.ble.Ce64AdvertisementStatus
import com.wild.android.ble.Ce32Protocol
import com.wild.android.ble.ControlScope
import com.wild.android.ble.DeviceSessionUiState
import com.wild.android.ble.GpioMode
import com.wild.android.ble.ImpedanceSnapshotUiState
import com.wild.android.ble.LiveSyncUiState
import com.wild.android.ble.PreviewSelection
import com.wild.android.ble.SchedulerRuleUiState
import com.wild.android.ble.SessionEventUiState
import com.wild.android.ble.SpectrumConfigUiState
import com.wild.android.ble.SpectrumSnapshotUiState
import com.wild.android.ble.SpikeDetectorConfigUiState
import com.wild.android.ble.SyncMetricUiState
import com.wild.android.ble.staleDiscoveryDeviceIds
import com.wild.android.cloud.CloudFleetGatewayState
import com.wild.android.cloud.CloudFleetPhase
import com.wild.android.cloud.RemoteFleetDeviceUiState
import com.wild.android.resolvePreviewRouteSessions
import com.wild.android.resolvePreviewRouteTargetIds
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

private const val SHOW_UI_DESCRIPTIONS = false
private const val PreviewHighPassCutoffHz = 0.7f
private const val PreviewDefaultSampleRateHz = 1250
private const val PreviewPacketSampleCount = 64
private const val PreviewMaxDisplaySampleRateHz = 5_000

private fun compactUiCopy(detailed: String, compact: String): String {
    return if (SHOW_UI_DESCRIPTIONS) detailed else compact
}

private enum class AppDestination(
    val title: String,
    val icon: ImageVector,
    val showInDock: Boolean = true,
) {
    Devices("Devices", Icons.Outlined.Memory),
    Remote("Cloud fleet", Icons.Outlined.Cloud, showInDock = false),
    Rssi("RSSI", Icons.Outlined.SettingsInputAntenna, showInDock = false),
    Preview("Preview", Icons.AutoMirrored.Outlined.ShowChart, showInDock = false),
    Live("Live", Icons.Outlined.SettingsInputAntenna, showInDock = false),
    Control("Control", Icons.Outlined.Tune, showInDock = false),
    Records("Records", Icons.Outlined.FolderOpen, showInDock = false),
}

private enum class ControlSection(
    val label: String,
) {
    Acquisition("Acquisition"),
    ClosedLoop("Closed-Loop"),
    Analysis("Signal tools"),
    Io("I/O"),
    System("System"),
}

private enum class AcquisitionPane(
    val label: String,
) {
    Quick("Device status"),
    Rates("Sampling rates & channels"),
    Camera("Camera"),
    Impedance("Impedance"),
}

private enum class ClosedLoopPane(
    val label: String,
) {
    Quick("Stimulation controls"),
    Profile("Module settings"),
    Advanced("AI & advanced settings"),
    Monitor("Event waveforms"),
}

private enum class AnalysisPane(
    val label: String,
) {
    Spike("Spike detection"),
    Spectrum("Frequency spectrum"),
    Schedule("Recording schedule & profiles"),
}

private enum class SystemPane(
    val label: String,
) {
    Push("Save device settings"),
    Lifecycle("Firmware update & power"),
    Dump("Raw parameter data"),
}

private enum class RecordsPane(
    val label: String,
) {
    Fleet("Devices"),
    Vault("Files"),
    Index("Device"),
}

private enum class LivePane(
    val label: String,
) {
    Online("Online"),
    Camera("Camera"),
    Status("Status"),
    Fleet("Devices"),
}

private enum class OnlineToolsPane(
    val label: String,
) {
    Hub("Tools"),
    Console("Sync"),
    Payload("Payload"),
    Camera("Camera"),
    Records("Records"),
}

private enum class StatusPane(
    val label: String,
) {
    Health("Health"),
    Link("Link"),
    Activity("Activity"),
}

private fun compactScopeLabel(scope: ControlScope): String = when (scope) {
    ControlScope.ActiveDevice -> "Active only"
    ControlScope.SelectedDevices -> "Selected devices"
    ControlScope.AllConnected -> "All connected"
}

private fun compactScopeSelectorLabel(scope: ControlScope): String = when (scope) {
    ControlScope.ActiveDevice -> "Active"
    ControlScope.SelectedDevices -> "Selected"
    ControlScope.AllConnected -> "All"
}

private fun compactOnlineToolsLabel(pane: OnlineToolsPane): String = when (pane) {
    OnlineToolsPane.Hub -> "Tools"
    OnlineToolsPane.Console -> "Sync"
    OnlineToolsPane.Payload -> "Params"
    OnlineToolsPane.Camera -> "Cam"
    OnlineToolsPane.Records -> "Files"
}

private fun buildCompactScopeOptions(
    scope: ControlScope,
    selectedCount: Int,
    connectedCount: Int,
): List<Triple<ControlScope, String, Boolean>> {
    val showSelectedScope = selectedCount > 0 || scope == ControlScope.SelectedDevices
    val showAllScope = connectedCount > 1 || scope == ControlScope.AllConnected
    return buildList {
        add(Triple(ControlScope.ActiveDevice, compactScopeSelectorLabel(ControlScope.ActiveDevice), true))
        if (showSelectedScope) {
            add(
                Triple(
                    ControlScope.SelectedDevices,
                    compactScopeSelectorLabel(ControlScope.SelectedDevices),
                    selectedCount > 0,
                )
            )
        }
        if (showAllScope) {
            add(
                Triple(
                    ControlScope.AllConnected,
                    compactScopeSelectorLabel(ControlScope.AllConnected),
                    connectedCount > 1,
                )
            )
        }
    }
}

private enum class FleetPane(
    val label: String,
) {
    Operate("Control"),
    Camera("Camera"),
    Health("Health"),
}

private enum class SignalViewMode(
    val label: String,
) {
    Overlay("Overlay"),
    Stacked("Stacked"),
}

private enum class SignalWindowPreset(
    val seconds: Int,
    val label: String,
) {
    OneSecond(1, "1s"),
    TwoSeconds(2, "2s"),
    FiveSeconds(5, "5s"),
    TenSeconds(10, "10s"),
}

private fun SignalWindowPreset.step(delta: Int): SignalWindowPreset {
    val entries = SignalWindowPreset.entries
    val nextIndex = (entries.indexOf(this) + delta).coerceIn(0, entries.lastIndex)
    return entries[nextIndex]
}

private enum class SignalGainPreset(
    val label: String,
    val normalizedPerCount: Float,
) {
    FiveMilliVolts("5mV", 0.195f / 5000f),
    TwoMilliVolts("2mV", 0.195f / 2000f),
    OneMilliVolt("1mV", 0.195f / 1000f),
    FiveHundredMicroVolts("500uV", 0.195f / 500f),
    TwoHundredMicroVolts("200uV", 0.1956f / 200f),
    OneHundredMicroVolts("100uV", 0.1956f / 100f),
    FiftyMicroVolts("50uV", 0.195f / 50f),
    TwentyMicroVolts("20uV", 0.195f / 20f),
    TenMicroVolts("10uV", 0.195f / 10f),
}

private fun SignalGainPreset.step(delta: Int): SignalGainPreset {
    val entries = SignalGainPreset.entries
    val nextIndex = (entries.indexOf(this) + delta).coerceIn(0, entries.lastIndex)
    return entries[nextIndex]
}

private enum class RssiWindowPreset(
    val milliseconds: Long,
    val label: String,
) {
    ThirtySeconds(30_000L, "30s"),
    OneMinute(60_000L, "1m"),
    TwoMinutes(120_000L, "2m"),
    FiveMinutes(300_000L, "5m"),
}

private enum class RssiMonitorMode(
    val label: String,
) {
    Locate("RSSI overview"),
    Advertisement("Advertisement state"),
}

private data class AdvertisementStatePlotRow(
    val label: String,
    val color: Color,
    val isActive: (AdvertisementStatusSampleUiState) -> Boolean,
)

private data class SignalDisplayConfig(
    val window: SignalWindowPreset,
    val gain: SignalGainPreset,
    val removeDc: Boolean,
)

private data class PreviewRenderColumn(
    val xFraction: Float,
    val minValue: Float,
    val maxValue: Float,
)

private data class PreparedPreviewTrace(
    val columns: List<PreviewRenderColumn>,
    val threshold: Float? = null,
    val thresholdLabel: String? = null,
    val filledFraction: Float = 0f,
)

private data class ClosedLoopModeOption(
    val value: Int,
    val label: String,
)

private data class ScopeStatusSummary(
    val targetCount: Int,
    val previewingCount: Int,
    val recordingCount: Int,
    val ledOnCount: Int,
    val gpio0ModeLabel: String,
    val gpio1ModeLabel: String,
    val gpio0ConsensusMode: GpioMode? = null,
    val gpio1ConsensusMode: GpioMode? = null,
    val triggerWaveformCount: Int,
    val totalUsedSpaceMb: Double,
    val totalTrafficTx: Int,
    val totalTrafficRx: Int,
    val previewSourceLabel: String,
)

private data class LiveActionSummary(
    val previewStartCount: Int,
    val previewStopCount: Int,
    val recordingStartCount: Int,
    val recordingActiveCount: Int,
    val recordingStopPendingCount: Int,
    val recordingStopCount: Int,
    val canRunPreviewGroupResync: Boolean,
    val previewStartBlockedByScope: Boolean,
    val recordingStartBlockedByScope: Boolean,
) {
    val canStartPreview: Boolean
        get() = previewStartCount > 0 || canRunPreviewGroupResync

    val canStopPreview: Boolean
        get() = previewStopCount > 0

    val canStartRecording: Boolean
        get() = recordingStartCount > 0

    val canStopRecording: Boolean
        get() = recordingStopCount > 0

    val hasPendingRecordingStop: Boolean
        get() = recordingStopPendingCount > 0
}

private data class PendingConfirmAction(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val onConfirm: () -> Unit,
)

private val closedLoopModeOptions = listOf(
    ClosedLoopModeOption(0, "Disabled"),
    ClosedLoopModeOption(1, "Single A"),
    ClosedLoopModeOption(6, "Single A Hilbert"),
    ClosedLoopModeOption(2, "Double"),
    ClosedLoopModeOption(7, "Double Hilbert"),
    ClosedLoopModeOption(3, "Cascade A->B"),
    ClosedLoopModeOption(4, "Gated A & B"),
    ClosedLoopModeOption(5, "Random"),
)

private val eventTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

@Composable
fun WildApp(
    viewModel: WildViewModel,
    debugDestinationOverride: String? = null,
    debugDestinationToken: Int = 0,
    onDebugDestinationConsumed: () -> Unit = {},
    onExitApp: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val bleOtaPackagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let(viewModel::selectBleOtaPackage)
    }
    var destination by rememberSaveable { mutableStateOf(AppDestination.Devices) }
    var controlReturnDestinationName by rememberSaveable { mutableStateOf(AppDestination.Devices.name) }
    var previewReturnDestinationName by rememberSaveable { mutableStateOf(AppDestination.Control.name) }
    var liveReturnDestinationName by rememberSaveable { mutableStateOf(AppDestination.Control.name) }
    var recordsReturnDestinationName by rememberSaveable { mutableStateOf(AppDestination.Live.name) }
    var controlLaunchSectionName by rememberSaveable { mutableStateOf(ControlSection.Acquisition.name) }
    var controlLaunchClosedLoopPaneName by rememberSaveable { mutableStateOf(ClosedLoopPane.Quick.name) }
    var controlLaunchSystemPaneName by rememberSaveable { mutableStateOf(SystemPane.Push.name) }
    var controlLaunchRequestToken by rememberSaveable { mutableStateOf(0) }
    var recordsLaunchPaneName by rememberSaveable { mutableStateOf(RecordsPane.Fleet.name) }
    var recordsLaunchRequestToken by rememberSaveable { mutableStateOf(0) }
    val controlReturnDestination = AppDestination.valueOf(controlReturnDestinationName)
    val previewReturnDestination = AppDestination.valueOf(previewReturnDestinationName)
    val liveReturnDestination = AppDestination.valueOf(liveReturnDestinationName)
    val recordsReturnDestination = AppDestination.valueOf(recordsReturnDestinationName)
    val showModeDock = AppDestination.entries.count { it.showInDock } > 1 && destination.showInDock
    val immersivePreview = destination == AppDestination.Preview
    val compactRouteChrome = destination == AppDestination.Preview || destination == AppDestination.Live
    val ultraCompactPreviewChrome = destination == AppDestination.Preview
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    LaunchedEffect(debugDestinationToken, debugDestinationOverride) {
        val target = debugDestinationOverride
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return@LaunchedEffect
        val destinationOverride = AppDestination.entries.firstOrNull { entry ->
            entry.name.equals(target, ignoreCase = true)
        } ?: return@LaunchedEffect
        when (destinationOverride) {
            AppDestination.Remote -> Unit
            AppDestination.Rssi -> Unit
            AppDestination.Preview -> previewReturnDestinationName = AppDestination.Devices.name
            AppDestination.Live -> liveReturnDestinationName = AppDestination.Devices.name
            AppDestination.Control -> controlReturnDestinationName = AppDestination.Devices.name
            AppDestination.Records -> recordsReturnDestinationName = AppDestination.Devices.name
            AppDestination.Devices -> Unit
        }
        destination = destinationOverride
        onDebugDestinationConsumed()
    }

    BackHandler(enabled = destination != AppDestination.Devices) {
        destination = when (destination) {
            AppDestination.Remote -> AppDestination.Devices
            AppDestination.Rssi -> AppDestination.Devices
            AppDestination.Live -> liveReturnDestination
            AppDestination.Records -> recordsReturnDestination
            AppDestination.Preview -> previewReturnDestination
            AppDestination.Control -> controlReturnDestination
            AppDestination.Devices -> AppDestination.Devices
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                if (showModeDock) {
                    ModeDock(
                        destination = destination,
                        onSelect = { destination = it },
                    )
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (isLandscape) Modifier else Modifier.statusBarsPadding())
                    .padding(padding)
                    .padding(
                        horizontal = when {
                            ultraCompactPreviewChrome -> 4.dp
                            compactRouteChrome -> 8.dp
                            else -> 16.dp
                        },
                        vertical = when {
                            ultraCompactPreviewChrome -> 2.dp
                            compactRouteChrome -> 4.dp
                            else -> 6.dp
                        },
                    ),
                verticalArrangement = Arrangement.spacedBy(
                    when {
                        ultraCompactPreviewChrome -> 4.dp
                        compactRouteChrome -> 8.dp
                        else -> 12.dp
                    }
                ),
            ) {
                if (!immersivePreview && uiState.statusBanner.isNotBlank()) {
                    StatusBanner(
                        message = uiState.statusBanner,
                        onDismiss = viewModel::clearBanner,
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (destination) {
                        AppDestination.Devices -> DevicesScreen(
                            modifier = Modifier.fillMaxSize(),
                            uiState = uiState,
                            onScanToggle = {
                                if (uiState.isScanning) viewModel.stopScan() else viewModel.startScan()
                            },
                            onConnectVisible = viewModel::connectVisibleSessions,
                            onConnectQueued = viewModel::connectQueuedCandidates,
                            onCancelPendingFleetConnect = viewModel::cancelPendingFleetConnect,
                            onDisconnectAll = viewModel::disconnectAllConnected,
                            onDisconnectSession = viewModel::disconnect,
                            onStopAllRecording = { sessionIds ->
                                viewModel.stopRecordingForTargets(sessionIds)
                            },
                            onConnectSession = viewModel::connect,
                            onFocusSession = viewModel::focusSession,
                            onOpenControlPage = { deviceId ->
                                viewModel.setActiveSession(deviceId)
                                viewModel.setControlScope(ControlScope.ActiveDevice)
                                controlReturnDestinationName = AppDestination.Devices.name
                                controlLaunchSectionName = ControlSection.Acquisition.name
                                controlLaunchClosedLoopPaneName = ClosedLoopPane.Quick.name
                                controlLaunchSystemPaneName = SystemPane.Push.name
                                controlLaunchRequestToken += 1
                                destination = AppDestination.Control
                            },
                            onOpenScopePreview = {
                                previewReturnDestinationName = AppDestination.Devices.name
                                destination = AppDestination.Preview
                            },
                            onOpenRssiMonitor = {
                                destination = AppDestination.Rssi
                            },
                            onOpenCloudFleet = {
                                destination = AppDestination.Remote
                            },
                            onClearStaleDevices = viewModel::clearStaleDevices,
                            onExitApp = onExitApp,
                        )

                        AppDestination.Remote -> CloudFleetScreen(
                            modifier = Modifier.fillMaxSize(),
                            cloudFleet = uiState.cloudFleet,
                            onBack = { destination = AppDestination.Devices },
                            onSignIn = viewModel::signInToCloudFleet,
                            onCreateAccount = viewModel::createCloudFleetAccount,
                            onSignOut = viewModel::signOutOfCloudFleet,
                            onCloudFleetViewVisible = viewModel::setCloudFleetViewVisible,
                        )

                        AppDestination.Rssi -> RssiMonitorScreen(
                            modifier = Modifier.fillMaxSize(),
                            sessions = uiState.sessions,
                            isScanning = uiState.isScanning,
                            onClearHistory = viewModel::clearRssiHistory,
                            onBack = { destination = AppDestination.Devices },
                        )

                        AppDestination.Preview -> PreviewScreen(
                            modifier = Modifier.fillMaxSize(),
                            uiState = uiState,
                            onActivateSession = viewModel::setActiveSession,
                            onOpenDevices = { destination = AppDestination.Devices },
                            onOpenOperate = {
                                controlReturnDestinationName = AppDestination.Preview.name
                                controlLaunchSectionName = ControlSection.Acquisition.name
                                controlLaunchClosedLoopPaneName = ClosedLoopPane.Quick.name
                                controlLaunchSystemPaneName = SystemPane.Push.name
                                controlLaunchRequestToken += 1
                                destination = AppDestination.Control
                            },
                            onStartPreview = viewModel::startPreviewForTargets,
                            onStopPreview = viewModel::stopPreviewForTargets,
                            onStartRecording = viewModel::startRecordingForTargets,
                            onStopRecording = viewModel::stopRecordingForTargets,
                            onSetPreviewSelectionForDevice = viewModel::setPreviewSelectionForDevice,
                        )

                        AppDestination.Live -> LiveScreen(
                            modifier = Modifier.fillMaxSize(),
                            uiState = uiState,
                            onPageTitleChange = {},
                            onActivateSession = viewModel::setActiveSession,
                            onFocusSession = viewModel::focusSession,
                            onScopeChange = viewModel::setControlScope,
                            onToggleSessionSelection = viewModel::toggleSessionSelection,
                            onSelectAllConnectedSessions = viewModel::selectAllConnectedSessions,
                            onClearSelectedSessions = viewModel::clearSelectedSessions,
                            onStartPreviewForDevice = viewModel::startPreviewForDevice,
                            onStopPreviewForDevice = viewModel::stopPreviewForDevice,
                            onSetPreviewSelectionForDevice = viewModel::setPreviewSelectionForDevice,
                            onStartRecordingForDevice = viewModel::startRecordingForDevice,
                            onStopRecordingForDevice = viewModel::stopRecordingForDevice,
                            onForceStopRecordingForDevice = viewModel::forceStopRecordingForDevice,
                            onRequestPreviewFrameForDevice = viewModel::requestPreviewFrameForDevice,
                            onRequestSnapshotForDevice = { deviceId -> viewModel.requestSnapshotForDevice(deviceId) },
                            onSetCameraPreviewStreamingForDevice = viewModel::setCameraPreviewStreamingForDevice,
                            onReadCameraParamsForDevice = viewModel::requestCameraParamsForDevice,
                            onSetCameraParamsForDevice = viewModel::setCameraParamsForDevice,
                            onResyncForDevice = { deviceId -> viewModel.requestResyncForDevice(deviceId) },
                            onResyncNoRtcForDevice = { deviceId -> viewModel.requestResyncForDevice(deviceId, suppressRtcWrite = true) },
                            onRequestImpedanceForDevice = viewModel::requestImpedanceForDevice,
                            onShowSyncLogPathForDevice = viewModel::showSyncLogPathForDevice,
                            onShowTriggerWaveformPathForDevice = viewModel::showTriggerWaveformPathForDevice,
                            onReadSystemParamsForDevice = viewModel::requestSystemParamsForDevice,
                            onReadDspParamsForDevice = viewModel::requestDspParamsForDevice,
                            onReadAllParamsForDevice = viewModel::requestAllParamsForDevice,
                            onRefreshRecordsForDevice = viewModel::refreshRecordListForDevice,
                            onExportAllRecordsForDevice = viewModel::exportAllRecordsForDevice,
                            onShowRecordExportPathForDevice = viewModel::showRecordExportPathForDevice,
                            onUploadSystemParamsForDevice = viewModel::uploadSystemParamsForDevice,
                            onUploadDspParamsForDevice = viewModel::uploadDspParamsForDevice,
                            onUploadAllParamsForDevice = viewModel::uploadAllParamsForDevice,
                            onApplyStreamRatesForDevice = viewModel::setStreamRatesForDevice,
                            onQuickSetFsForDevice = viewModel::setQuickCustomFsForDevice,
                            onApplyAcquisitionSystemProfileForDevice = viewModel::applyAcquisitionSystemProfileForDevice,
                            onSetTriggerWaveformForDevice = viewModel::setTriggerWaveformForDevice,
                            onApplyStreamRates = viewModel::setStreamRates,
                            onQuickSetFs = viewModel::setQuickCustomFs,
                            onApplyAcquisitionSystemProfileToSystem = viewModel::applyAcquisitionSystemProfile,
                            onPreviewSnapshot = { viewModel.requestSnapshot(preview = true) },
                            onReadCameraParams = viewModel::requestCameraParams,
                            onSetTriggerWaveform = viewModel::setTriggerWaveform,
                            onShowTriggerWaveformPath = viewModel::showTriggerWaveformPath,
                            onConnectSession = viewModel::connect,
                            onDisconnectSession = viewModel::disconnect,
                            onOpenSignalOnline = viewModel::prepareLiveLaunchForCurrentScope,
                            onOpenStatus = {},
                            onOpenRecords = {
                                recordsReturnDestinationName = AppDestination.Live.name
                                viewModel.prepareRecordsLaunchForCurrentScope()
                                recordsLaunchPaneName = RecordsPane.Fleet.name
                                recordsLaunchRequestToken += 1
                                destination = AppDestination.Records
                            },
                        )

                        AppDestination.Control -> DeviceParameterScreen(
                            modifier = Modifier.fillMaxSize(),
                            uiState = uiState,
                            launchSectionName = controlLaunchSectionName,
                            launchClosedLoopPaneName = controlLaunchClosedLoopPaneName,
                            launchSystemPaneName = controlLaunchSystemPaneName,
                            launchRequestToken = controlLaunchRequestToken,
                            onBackToDevices = { destination = controlReturnDestination },
                            onOpenPreview = {
                                previewReturnDestinationName = AppDestination.Control.name
                                destination = AppDestination.Preview
                            },
                            onActivateSession = viewModel::setActiveSession,
                            onScopeChange = viewModel::setControlScope,
                            onToggleSessionSelection = viewModel::toggleSessionSelection,
                            onSelectAllConnectedSessions = viewModel::selectAllConnectedSessions,
                            onClearSelectedSessions = viewModel::clearSelectedSessions,
                            onLinkAction = {
                                uiState.activeSession?.let { session ->
                                    if (session.isConnected || session.isLinkingLike) {
                                        viewModel.disconnect(session.id)
                                    } else {
                                        viewModel.connect(session.id)
                                    }
                                }
                            },
                            onResync = { viewModel.requestResync() },
                            onReadParams = viewModel::requestSystemParams,
                            onReadDsp = viewModel::requestDspParams,
                            onReadAllParams = viewModel::requestAllParams,
                            onApplyStreamRates = viewModel::setStreamRates,
                            onQuickSetFs = viewModel::setQuickCustomFs,
                            onApplyAcquisitionSystemProfileToSystem = viewModel::applyAcquisitionSystemProfile,
                            onSnapshot = viewModel::requestSnapshot,
                            onPreviewSnapshot = { viewModel.requestSnapshot(preview = true) },
                            onReadCameraParams = viewModel::requestCameraParams,
                            onSetCameraParams = viewModel::setCameraParams,
                            onSetStimEnabled = viewModel::setStimEnabled,
                            onSetTriggerGain = viewModel::setTriggerGain,
                            onSetStimIntensity = viewModel::setStimIntensity,
                            onSetTriggerThreshold = viewModel::setTriggerThreshold,
                            onForceTrigger = viewModel::forceTrigger,
                            onSetStimParams = viewModel::setStimParams,
                            onSetDspLiveParams = viewModel::setDspLiveParams,
                            onApplyClosedLoopProfileToSystem = viewModel::applyClosedLoopProfileToSystem,
                            onApplyClosedLoopProfileToAll = viewModel::applyClosedLoopProfileToAll,
                            onImpedance = viewModel::requestImpedance,
                            onExportImpedance = viewModel::exportImpedanceSnapshot,
                            onShowImpedanceExportPath = viewModel::showImpedanceExportPath,
                            onStartAutoImpedance = viewModel::startAutoImpedance,
                            onStopAutoImpedance = viewModel::stopAutoImpedance,
                            onLed = viewModel::setLed,
                            onGpio0 = viewModel::setGpio0,
                            onGpio1 = viewModel::setGpio1,
                            onTriggerWaveform = viewModel::setTriggerWaveform,
                            onShowTriggerWaveformPath = viewModel::showTriggerWaveformPath,
                            onSleep = viewModel::requestSleep,
                            onReset = viewModel::requestSoftwareReset,
                            onBootloader = viewModel::requestBootloader,
                            onFirmwareUpdate = viewModel::requestFirmwareUpdate,
                            onPickBleOtaPackage = {
                                bleOtaPackagePicker.launch(arrayOf("text/plain", "application/octet-stream", "application/*"))
                            },
                            onStageBleOta = viewModel::stageBleOta,
                            onInstallStagedBleOta = viewModel::installStagedBleOta,
                            onRequestAiModuleInstall = viewModel::requestAiModuleInstall,
                            onRefreshAiStatus = viewModel::refreshAiRuntimeStatus,
                            onSelectAiModule = viewModel::selectAiModule,
                            onSetAiRuntimeEnabled = viewModel::setAiRuntimeEnabled,
                            onUploadSystemParams = viewModel::uploadSystemParams,
                            onUploadDspParams = viewModel::uploadDspParams,
                            onUploadAllParams = viewModel::uploadAllParams,
                            onRole = viewModel::setRole,
                            onRefreshSignalAnalysis = viewModel::refreshSignalAnalysis,
                            onSetSpikeDetectorConfig = viewModel::setSpikeDetectorConfig,
                            onSetSpectrumConfig = viewModel::setSpectrumConfig,
                            onSetSchedulerEnabled = viewModel::setSchedulerEnabled,
                            onSetSchedulerRule = viewModel::setSchedulerRule,
                            onSetSchedulerRuleEnabled = viewModel::setSchedulerRuleEnabled,
                            onClearSchedulerRule = viewModel::clearSchedulerRule,
                            onClearScheduler = viewModel::clearScheduler,
                            onReadScheduler = viewModel::refreshScheduler,
                            onSaveSchedulerProfile = viewModel::saveSchedulerProfile,
                        )

                        AppDestination.Records -> RecordsScreen(
                            modifier = Modifier.fillMaxSize(),
                            uiState = uiState,
                            launchPaneName = recordsLaunchPaneName,
                            launchRequestToken = recordsLaunchRequestToken,
                            onActivateSession = viewModel::setActiveSession,
                            onScopeChange = viewModel::setControlScope,
                            onToggleSessionSelection = viewModel::toggleSessionSelection,
                            onSelectAllConnectedSessions = viewModel::selectAllConnectedSessions,
                            onClearSelectedSessions = viewModel::clearSelectedSessions,
                            onRefreshScope = viewModel::refreshRecordListForScope,
                            onRefresh = viewModel::refreshRecordList,
                            onRefreshForDevice = viewModel::refreshRecordListForDevice,
                            onDeleteLast = viewModel::deleteLastRecord,
                            onDeleteAll = viewModel::deleteAllRecords,
                            onExportRecord = viewModel::exportRecord,
                            onExportAllScope = viewModel::exportAllRecordsForScope,
                            onExportAll = viewModel::exportAllRecords,
                            onExportAllForDevice = viewModel::exportAllRecordsForDevice,
                            onShowRecordExportPath = viewModel::showRecordExportPath,
                            onShowRecordExportPathForDevice = viewModel::showRecordExportPathForDevice,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeDock(
    destination: AppDestination,
    onSelect: (AppDestination) -> Unit,
) {
    val dockDestinations = AppDestination.entries.filter { it.showInDock }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            dockDestinations.forEach { item ->
                ModeDockItem(
                    item = item,
                    selected = item == destination,
                    onSelect = { onSelect(item) },
                )
            }
        }
    }
}

@Composable
private fun RowScope.ModeDockItem(
    item: AppDestination,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.background.copy(alpha = 0.72f)
        },
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onSelect),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                item.icon,
                contentDescription = item.title,
                modifier = Modifier.size(18.dp),
                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(width = 18.dp, height = 3.dp)
                        .background(MaterialTheme.colorScheme.onPrimaryContainer, RoundedCornerShape(999.dp))
                )
            }
        }
    }
}

@Composable
private fun StatusBanner(
    message: String,
    onDismiss: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    }
}

@Composable
private fun DevicesScreen(
    modifier: Modifier = Modifier,
    uiState: WildUiState,
    onScanToggle: () -> Unit,
    onConnectVisible: (List<String>) -> Unit,
    onConnectQueued: () -> Unit,
    onCancelPendingFleetConnect: () -> Unit,
    onDisconnectAll: () -> Unit,
    onDisconnectSession: (String) -> Unit,
    onStopAllRecording: (List<String>) -> Unit,
    onConnectSession: (String) -> Unit,
    onFocusSession: (String) -> Unit,
    onOpenControlPage: (String) -> Unit,
    onOpenScopePreview: () -> Unit,
    onOpenRssiMonitor: () -> Unit,
    onOpenCloudFleet: () -> Unit,
    onClearStaleDevices: () -> Unit,
    onExitApp: () -> Unit,
) {
    val discoveredSessions = uiState.sessions
        .sortedWith(
            compareByDescending<DeviceSessionUiState> { it.isActive }
                .thenByDescending { it.isConnected }
                .thenByDescending { it.isLinkingLike }
                .thenByDescending { it.id in uiState.pendingConnectionIds }
                .thenBy { it.name.lowercase(Locale.US) },
    )
    val connectableSessions = discoveredSessions.filter { !it.isConnected && !it.isLinkingLike && it.bulkConnectEligible }
    val connectedCount = uiState.connectedSessions.size
    val linkingCount = discoveredSessions.count { it.isLinkingLike }
    val disconnectableCount = connectedCount + linkingCount
    val recordingSessions = uiState.connectedSessions.filter { it.isRecordingLike }
    val staleDeviceCount = staleDiscoveryDeviceIds(discoveredSessions).size
    val attentionCount = discoveredSessions.count { session ->
        session.lastFailure.isNotBlank() ||
            session.hostState == BleHostSessionState.Reconnecting
    }
    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                DeviceDashboardHeader(
                    isScanning = uiState.isScanning,
                    onScanToggle = onScanToggle,
                    onOpenRssiMonitor = onOpenRssiMonitor,
                    onOpenCloudFleet = onOpenCloudFleet,
                )
            }
            item {
                DeviceDashboardSummaryCard(
                    nearbyCount = discoveredSessions.size,
                    connectedCount = connectedCount,
                    recordingCount = recordingSessions.size,
                    attentionCount = attentionCount,
                    isScanning = uiState.isScanning,
                )
            }
            item {
                AdvertisementFleetOverview(
                    sessions = discoveredSessions,
                    isScanning = uiState.isScanning,
                    onConnectSession = onConnectSession,
                    onDisconnectSession = onDisconnectSession,
                    onFocusSession = onFocusSession,
                    onOpenControlPage = onOpenControlPage,
                )
            }
            item {
                DeviceListMaintenanceCard(
                    staleDeviceCount = staleDeviceCount,
                    onClearStaleDevices = onClearStaleDevices,
                    onExitApp = onExitApp,
                )
            }
        }

        DeviceDashboardActionBar(
            connectableCount = connectableSessions.size,
            queuedCount = uiState.pendingConnectionIds.size,
            fleetConnectActive = uiState.fleetConnectActive,
            connectedCount = connectedCount,
            disconnectableCount = disconnectableCount,
            recordingSessionIds = recordingSessions.map { it.id },
            onConnectAll = { onConnectVisible(connectableSessions.map { it.id }) },
            onConnectQueued = onConnectQueued,
            onCancelPendingFleetConnect = onCancelPendingFleetConnect,
            onDisconnectAll = onDisconnectAll,
            onOpenPreview = onOpenScopePreview,
            onStopAllRecording = onStopAllRecording,
            onExitApp = onExitApp,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceListMaintenanceCard(
    staleDeviceCount: Int,
    onClearStaleDevices: () -> Unit,
    onExitApp: () -> Unit,
) {
    var showExitConfirmation by rememberSaveable { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Device list", style = MaterialTheme.typography.titleSmall)
            Text(
                "Clear removes only disconnected devices not heard for 30 minutes. Connected or linking devices stay protected.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onClearStaleDevices,
                    enabled = staleDeviceCount > 0,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(if (staleDeviceCount > 0) "Clear stale devices ($staleDeviceCount)" else "No stale devices")
                }
                TextButton(
                    onClick = { showExitConfirmation = true },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("Exit app")
                }
            }
        }
    }

    if (showExitConfirmation) {
        AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            title = { Text("Exit WILD Control Panel?") },
            text = {
                Text(
                    "This stops Bluetooth scanning, closes all WILD links, and removes the background BLE notification. Device recordings continue unless you stop them first.",
                )
            },
            confirmButton = {
                Button(onClick = onExitApp) { Text("Exit app") }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmation = false }) { Text("Keep running") }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CloudFleetScreen(
    modifier: Modifier = Modifier,
    cloudFleet: CloudFleetGatewayState,
    onBack: () -> Unit,
    onSignIn: (String, String) -> Unit,
    onCreateAccount: (String, String) -> Unit,
    onSignOut: () -> Unit,
    onCloudFleetViewVisible: (Boolean) -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val signingIn = cloudFleet.phase == CloudFleetPhase.SigningIn
    val remoteDevices = cloudFleet.remoteDevices
    val connectedCount = remoteDevices.count { it.connected }
    val recordingCount = remoteDevices.count { it.recording }

    DisposableEffect(Unit) {
        onCloudFleetViewVisible(true)
        onDispose { onCloudFleetViewVisible(false) }
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedIconButton(
                onClick = onBack,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to devices")
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Cloud fleet", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "View status from another WILD phone. Remote BLE commands are disabled.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f),
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = when (cloudFleet.phase) {
                CloudFleetPhase.Online -> Color(0xFF2BA66B).copy(alpha = 0.12f)
                CloudFleetPhase.SigningIn -> MaterialTheme.colorScheme.primaryContainer
                CloudFleetPhase.NeedsSetup -> MaterialTheme.colorScheme.errorContainer
                CloudFleetPhase.Guest -> MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Cloud,
                        contentDescription = null,
                        tint = cloudFleetStatusColor(cloudFleet.phase),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        cloudFleetStatusTitle(cloudFleet),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Text(
                    cloudFleet.message,
                    style = MaterialTheme.typography.bodyMedium,
                )
                cloudFleet.lastPublishedAtMs?.let { publishedAtMs ->
                    Text(
                        "Last phone update ${formatCloudAge(publishedAtMs)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    )
                }
            }
        }

        if (cloudFleet.sharedAcrossPhones) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Shared account", style = MaterialTheme.typography.titleSmall)
                        Text(
                            cloudFleet.accountEmail ?: "Firebase account",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f),
                        )
                    }
                    TextButton(onClick = onSignOut) {
                        Text("Sign out")
                    }
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Share this fleet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Use the same Firebase email/password on the gateway and viewing phones. " +
                            "Guest status remains private to one phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f),
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        singleLine = true,
                        enabled = !signingIn,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        enabled = !signingIn,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { onSignIn(email, password) },
                            enabled = !signingIn,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text(if (signingIn) "Connecting…" else "Sign in")
                        }
                        OutlinedButton(
                            onClick = { onCreateAccount(email, password) },
                            enabled = !signingIn,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text("Create shared account")
                        }
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Remote device status", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Latest status published by gateway phones on this account.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f),
                        )
                    }
                    DeviceDashboardMetricChip(
                        text = "$connectedCount linked",
                        color = if (connectedCount > 0) Color(0xFF2BA66B) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (recordingCount > 0) {
                        Spacer(Modifier.width(6.dp))
                        DeviceDashboardMetricChip(
                            text = "$recordingCount rec",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                if (remoteDevices.isEmpty()) {
                    Text(
                        "No cloud device status yet. Keep the gateway phone online and connected to a WILD device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f),
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        remoteDevices.forEach { device ->
                            RemoteFleetDeviceCard(device)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RemoteFleetDeviceCard(device: RemoteFleetDeviceUiState) {
    val statusColor = when {
        device.recording -> MaterialTheme.colorScheme.error
        device.previewing -> Color(0xFF1687F2)
        device.connected -> Color(0xFF2BA66B)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.54f),
        modifier = Modifier.widthIn(min = 220.dp, max = 340.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                device.displayName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                remoteFleetDeviceStateLabel(device),
                style = MaterialTheme.typography.labelMedium,
                color = statusColor,
            )
            Text(
                "Gateway: ${device.gatewayLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                device.rssiDbm?.let { rssi ->
                    DeviceDashboardMetricChip("$rssi dBm", statusColor)
                }
                device.batteryVolts?.let { volts ->
                    DeviceDashboardMetricChip("Battery ${formatVoltageLabel(volts)}")
                }
                device.storageUsedPercent?.let { percent ->
                    DeviceDashboardMetricChip("Storage $percent%")
                } ?: device.storageUsedMb?.let { usedMb ->
                    DeviceDashboardMetricChip("Storage ${formatUsedSpaceLabel(usedMb)}")
                }
                if (device.recording && device.recordingSeconds > 0L) {
                    DeviceDashboardMetricChip("Rec ${formatSeconds(device.recordingSeconds)}", MaterialTheme.colorScheme.error)
                }
                device.advertisedSampleRateHz?.takeIf { rate -> rate > 0 }?.let { rate ->
                    DeviceDashboardMetricChip("Rate $rate Hz")
                }
                device.aiModelId?.let { modelId ->
                    val aiLabel = buildString {
                        append("AI M$modelId")
                        device.aiClassId?.let { classId -> append(" C$classId") }
                        device.aiConfidencePercentage?.let { confidence -> append(" $confidence%") }
                        if (device.aiResultIsNew) append(" new")
                    }
                    DeviceDashboardMetricChip(
                        aiLabel,
                        if (device.aiResultIsNew) MaterialTheme.colorScheme.primary else statusColor,
                    )
                }
            }
            Text(
                "Gateway update ${formatCloudAge(device.lastPublishedAtMs)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f),
            )
        }
    }
}

private fun cloudFleetStatusTitle(cloudFleet: CloudFleetGatewayState): String {
    return when (cloudFleet.phase) {
        CloudFleetPhase.Online -> "Cloud fleet online"
        CloudFleetPhase.Guest -> "Private cloud gateway"
        CloudFleetPhase.SigningIn -> "Connecting cloud fleet"
        CloudFleetPhase.NeedsSetup -> "Cloud setup needed"
    }
}

@Composable
private fun cloudFleetStatusColor(phase: CloudFleetPhase): Color {
    return when (phase) {
        CloudFleetPhase.Online -> Color(0xFF2BA66B)
        CloudFleetPhase.SigningIn -> MaterialTheme.colorScheme.primary
        CloudFleetPhase.NeedsSetup -> MaterialTheme.colorScheme.error
        CloudFleetPhase.Guest -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

private fun remoteFleetDeviceStateLabel(device: RemoteFleetDeviceUiState): String {
    return when {
        device.recording -> "Recording"
        device.previewing -> "Live signal"
        device.connected -> "Connected"
        else -> "Last state: ${device.hostState.lowercase().replaceFirstChar { it.uppercase() }}"
    }
}

private fun formatCloudAge(timestampMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    if (timestampMs <= 0L) {
        return "not received"
    }
    val seconds = ((nowMs - timestampMs).coerceAtLeast(0L) / 1_000L)
    return when {
        seconds < 5L -> "now"
        seconds < 60L -> "${seconds}s ago"
        seconds < 3_600L -> "${seconds / 60L}m ago"
        else -> "${seconds / 3_600L}h ago"
    }
}

@Composable
private fun DeviceDashboardHeader(
    isScanning: Boolean,
    onScanToggle: () -> Unit,
    onOpenRssiMonitor: () -> Unit,
    onOpenCloudFleet: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.wild_logo_mark_v2),
            contentDescription = "WILD logo",
            modifier = Modifier.size(42.dp),
            contentScale = ContentScale.Fit,
        )
        Spacer(Modifier.width(10.dp))
        Text("WILD Control Panel", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.weight(1f))
        OutlinedButton(
            onClick = onOpenCloudFleet,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Cloud,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(6.dp))
            Text("Cloud")
        }
        Spacer(Modifier.width(6.dp))
        OutlinedButton(
            onClick = onOpenRssiMonitor,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ShowChart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(6.dp))
            Text("RSSI")
        }
        Spacer(Modifier.width(6.dp))
        Button(
            onClick = onScanToggle,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Sync,
                contentDescription = null,
            )
            Spacer(Modifier.width(6.dp))
            Text(if (isScanning) "Stop scan" else "Scan")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdvertisementFleetOverview(
    sessions: List<DeviceSessionUiState>,
    isScanning: Boolean,
    onConnectSession: (String) -> Unit,
    onDisconnectSession: (String) -> Unit,
    onFocusSession: (String) -> Unit,
    onOpenControlPage: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Advertisement overview", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (isScanning) {
                            "Connect a device, then open its controls when linked"
                        } else {
                            "Scan is paused — showing the last advertisements heard"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    )
                }
                DeviceDashboardMetricChip(
                    text = "${sessions.size} heard",
                    color = if (isScanning) Color(0xFF2BA66B) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (sessions.isEmpty()) {
                Text(
                    text = "No WILD devices heard yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f),
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    sessions.forEach { session ->
                        AdvertisementStatusTile(
                            session = session,
                            onConnectSession = { onConnectSession(session.id) },
                            onDisconnectSession = { onDisconnectSession(session.id) },
                            onFocusSession = { onFocusSession(session.id) },
                            onOpenControlPage = { onOpenControlPage(session.id) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdvertisementStatusTile(
    session: DeviceSessionUiState,
    onConnectSession: () -> Unit,
    onDisconnectSession: () -> Unit,
    onFocusSession: () -> Unit,
    onOpenControlPage: () -> Unit,
) {
    val stateColor = deviceDashboardStatusColor(session)
    val roleIdentity = formatBleRoleIdentity(session.roleTag, session.functionTag)
    val batteryLabel = formatConnectedBatteryLabel(session)
    val advertisedStatus = session.advertisedHealthStatus
    val liveStorage = formatUsedSpaceLabel(session.usedSpaceMb)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.54f),
        modifier = Modifier
            .widthIn(min = 220.dp, max = 340.dp)
            .border(
                width = 1.dp,
                color = Color(session.traceColorArgb).copy(alpha = 0.38f),
                shape = RoundedCornerShape(16.dp),
            ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(Color(session.traceColorArgb), CircleShape),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = compactDeviceUiLabel(session.name, session.address),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = advertisementStateLabel(session),
                        style = MaterialTheme.typography.labelMedium,
                        color = stateColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DeviceDashboardMetricChip(
                    text = session.rssi?.let { "$it dBm ${rssiQualityLabel(it)}" } ?: "RSSI --",
                    color = stateColor,
                )
                if (batteryLabel != "Not reported") {
                    DeviceDashboardMetricChip(text = "Battery $batteryLabel")
                }
                advertisedStatus?.storageUsedPercent?.let { percent ->
                    DeviceDashboardMetricChip(text = "Storage $percent% ad")
                }
                if (advertisedStatus?.recording == true && advertisedStatus.recordingSeconds > 0L) {
                    DeviceDashboardMetricChip(text = "Rec ${formatSeconds(advertisedStatus.recordingSeconds)} ad")
                }
                advertisedStatus?.advertisedSampleRateHz?.takeIf { rate -> rate > 0 }?.let { rate ->
                    DeviceDashboardMetricChip(text = "Rate $rate Hz ad")
                }
                advertisedStatus?.takeIf { status -> status.hasAiResult }?.let { status ->
                    DeviceDashboardMetricChip(
                        text = advertisementAiLabel(status),
                        color = if (status.aiResultIsNew) MaterialTheme.colorScheme.primary else stateColor,
                    )
                }
                advertisedStatus?.takeIf { it.failedSubsystems != 0 || it.degradedSubsystems != 0 }?.let { status ->
                    DeviceDashboardMetricChip(
                        text = advertisementHealthLabel(status),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (roleIdentity.isNotBlank()) {
                    DeviceDashboardMetricChip(text = roleIdentity)
                }
                if (session.isConnected) {
                    DeviceDashboardMetricChip(
                        text = "Storage $liveStorage live",
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = advertisementSourceLabel(session),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f),
            )
            when {
                session.isConnected -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(
                            onClick = onFocusSession,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) {
                            Text(if (session.isActive) "Focused" else "Focus")
                        }
                    }
                    OutlinedButton(
                        onClick = onOpenControlPage,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text("Open device controls")
                    }
                }
                session.isLinkingLike -> {
                    OutlinedButton(
                        onClick = onDisconnectSession,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text(
                            if (session.hostState == BleHostSessionState.Reconnecting) {
                                "Stop reconnecting"
                            } else {
                                "Cancel connection"
                            },
                        )
                    }
                }
                session.bulkConnectEligible -> {
                    Button(
                        onClick = onConnectSession,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text("Connect this device")
                    }
                }
                else -> {
                    Text(
                        text = "Advertisement only — not eligible for a WILD connection.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceDashboardSummaryCard(
    nearbyCount: Int,
    connectedCount: Int,
    recordingCount: Int,
    attentionCount: Int,
    isScanning: Boolean,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        FlowRow(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DeviceDashboardSummaryItem(
                icon = Icons.Outlined.SettingsInputAntenna,
                value = nearbyCount.toString(),
                label = "Nearby",
            )
            DeviceDashboardSummaryItem(
                icon = Icons.Outlined.Sync,
                value = connectedCount.toString(),
                // A GATT connection can exist before the CE handshake has
                // completed, so this must not imply that the device is ready
                // for an acquisition command.
                label = "Connected",
                color = if (connectedCount > 0) Color(0xFF2BA66B) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DeviceDashboardSummaryItem(
                icon = Icons.Outlined.FiberManualRecord,
                value = recordingCount.toString(),
                label = "Recording",
                color = if (recordingCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (attentionCount > 0) {
                Text(
                    text = "$attentionCount need attention",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                Text(
                    text = if (isScanning) "Systems normal" else "Scan paused",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isScanning) Color(0xFF2BA66B) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DeviceDashboardSummaryItem(
    icon: ImageVector,
    value: String,
    label: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = color)
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = color,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceDashboardActionBar(
    connectableCount: Int,
    queuedCount: Int,
    fleetConnectActive: Boolean,
    connectedCount: Int,
    disconnectableCount: Int,
    recordingSessionIds: List<String>,
    onConnectAll: () -> Unit,
    onConnectQueued: () -> Unit,
    onCancelPendingFleetConnect: () -> Unit,
    onDisconnectAll: () -> Unit,
    onOpenPreview: () -> Unit,
    onStopAllRecording: (List<String>) -> Unit,
    onExitApp: () -> Unit,
) {
    val connectionActionLabel = when {
        fleetConnectActive -> "Stop queue"
        queuedCount > 0 -> "Connect queue ($queuedCount)"
        connectableCount > 0 -> "Connect verified ($connectableCount)"
        else -> null
    }
    val onConnectionAction: (() -> Unit)? = when {
        fleetConnectActive -> onCancelPendingFleetConnect
        queuedCount > 0 -> onConnectQueued
        connectableCount > 0 -> onConnectAll
        else -> null
    }
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var showExitConfirmation by rememberSaveable { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
    ) {
        if (isLandscape) {
            Row(
                modifier = Modifier.padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                connectionActionLabel?.let { label ->
                    Button(
                        onClick = onConnectionAction ?: {},
                        modifier = Modifier.weight(1.25f).heightIn(min = 50.dp),
                    ) {
                        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                FilledTonalButton(
                    onClick = onOpenPreview,
                    modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                    enabled = connectedCount > 0,
                ) {
                    Text("Live signals", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = onDisconnectAll,
                    modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                    enabled = disconnectableCount > 0,
                ) {
                    Text(
                        if (disconnectableCount > 0) "Cancel / disconnect ($disconnectableCount)" else "Cancel / disconnect",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (recordingSessionIds.isNotEmpty()) {
                    Button(
                        onClick = { onStopAllRecording(recordingSessionIds) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                        modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                    ) {
                        Text("Stop rec ${recordingSessionIds.size}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                TextButton(
                    onClick = { showExitConfirmation = true },
                    modifier = Modifier.weight(0.72f).heightIn(min = 50.dp),
                ) {
                    Text("Exit app", maxLines = 1)
                }
            }
        } else {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
            Text("Fleet actions", style = MaterialTheme.typography.titleSmall)
            connectionActionLabel?.let { label ->
                Button(
                    onClick = onConnectionAction ?: {},
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text(label) }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = onOpenPreview,
                    modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                    enabled = connectedCount > 0,
                ) {
                    Text("Live\nsignals", textAlign = TextAlign.Center)
                }
                OutlinedButton(
                    onClick = onDisconnectAll,
                    modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                    enabled = disconnectableCount > 0,
                ) {
                    Text(
                        if (disconnectableCount > 0) "Cancel / disconnect\nall ($disconnectableCount)" else "Cancel / disconnect\nall",
                        textAlign = TextAlign.Center,
                    )
                }
            }
            if (recordingSessionIds.isNotEmpty()) {
                Button(
                    onClick = { onStopAllRecording(recordingSessionIds) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) {
                    Text("Stop recording on ${recordingSessionIds.size} device${if (recordingSessionIds.size == 1) "" else "s"}")
                }
            }
            TextButton(
                onClick = { showExitConfirmation = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text("Exit WILD app — stop Bluetooth")
            }
            }
        }
    }

    if (showExitConfirmation) {
        AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            title = { Text("Exit WILD Control Panel?") },
            text = {
                Text(
                    "This stops Bluetooth scanning, cancels all connection attempts, closes all WILD links, and removes the background BLE notification. Device recordings continue unless you stop them first.",
                )
            },
            confirmButton = {
                Button(onClick = onExitApp) { Text("Exit app") }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmation = false }) { Text("Keep running") }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RssiMonitorScreen(
    modifier: Modifier = Modifier,
    sessions: List<DeviceSessionUiState>,
    isScanning: Boolean,
    onClearHistory: () -> Unit,
    onBack: () -> Unit,
) {
    var window by rememberSaveable { mutableStateOf(RssiWindowPreset.OneMinute) }
    var modeName by rememberSaveable { mutableStateOf(RssiMonitorMode.Locate.name) }
    var selectedDeviceId by rememberSaveable { mutableStateOf<String?>(null) }
    var isPaused by rememberSaveable { mutableStateOf(false) }
    var nowMs by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    var pausedAtMs by rememberSaveable { mutableStateOf(0L) }
    val displayedNowMs = if (isPaused) pausedAtMs else nowMs
    val orderedSessions = sessions.sortedWith(
        compareByDescending<DeviceSessionUiState> { it.isActive }
            .thenByDescending { it.isConnected }
            .thenBy { it.name.lowercase(Locale.US) },
    )
    val mode = RssiMonitorMode.valueOf(modeName)
    val selectedSession = orderedSessions.firstOrNull { it.id == selectedDeviceId }
        ?: orderedSessions.firstOrNull { it.isActive }
        ?: orderedSessions.firstOrNull()

    LaunchedEffect(orderedSessions.map { it.id }, selectedSession?.id) {
        if (selectedDeviceId != selectedSession?.id) {
            selectedDeviceId = selectedSession?.id
        }
    }

    LaunchedEffect(isPaused) {
        while (!isPaused) {
            nowMs = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedIconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.width(8.dp))
            Text("RSSI Monitor", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Text(
                text = if (isScanning) "Scanning" else "Paused",
                style = MaterialTheme.typography.labelMedium,
                color = if (isScanning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedIconButton(
                onClick = {
                    if (isPaused) {
                        nowMs = System.currentTimeMillis()
                        isPaused = false
                    } else {
                        pausedAtMs = nowMs
                        isPaused = true
                    }
                },
            ) {
                Icon(
                    imageVector = if (isPaused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                    contentDescription = if (isPaused) "Resume chart" else "Pause chart",
                )
            }
            Spacer(Modifier.width(6.dp))
            OutlinedIconButton(onClick = onClearHistory) {
                Icon(Icons.Outlined.Remove, contentDescription = "Clear RSSI and advertisement history")
            }
        }

        if (orderedSessions.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (isScanning) "Looking for WILD devices..." else "No WILD devices",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f),
                )
            }
            return@Column
        }

        selectedSession?.let { session ->
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(min = 240.dp, max = 300.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (mode == RssiMonitorMode.Locate) {
                        RssiDeviceLegend(
                            sessions = orderedSessions,
                            modifier = Modifier.height(220.dp),
                        )
                    } else {
                        RssiDeviceSelector(
                            sessions = orderedSessions,
                            selectedDeviceId = session.id,
                            onSelect = { selectedDeviceId = it },
                        )
                    }
                    Text("Display", style = MaterialTheme.typography.labelLarge)
                    RssiMonitorMode.entries.forEach { option ->
                        val isSelected = option == mode
                        if (isSelected) {
                            Button(
                                onClick = { modeName = option.name },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            ) {
                                Text(option.label, textAlign = TextAlign.Center)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { modeName = option.name },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            ) {
                                Text(option.label, textAlign = TextAlign.Center)
                            }
                        }
                    }
                    Text("History window", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        RssiWindowPreset.entries.forEach { option ->
                            FilterChip(
                                selected = option == window,
                                onClick = { window = option },
                                label = { Text(option.label) },
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxHeight().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    when (mode) {
                        RssiMonitorMode.Locate -> {
                            RssiFleetSummary(orderedSessions)
                            RssiTimelineChart(
                                sessions = orderedSessions,
                                nowMs = displayedNowMs,
                                windowMs = window.milliseconds,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        RssiMonitorMode.Advertisement -> {
                            AdvertisementStatusSummary(session)
                            AdvertisementStatusTimelineChart(
                                session = session,
                                nowMs = displayedNowMs,
                                windowMs = window.milliseconds,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RssiDeviceLegend(
    sessions: List<DeviceSessionUiState>,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("RSSI devices", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "Each color matches its trace",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            )
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(sessions, key = { it.id }) { device ->
                    val traceColor = Color(device.traceColorArgb)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(traceColor, CircleShape),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = compactDeviceUiLabel(device.name, device.address),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = device.rssi?.let { "$it dBm" } ?: "--",
                            style = MaterialTheme.typography.labelLarge,
                            color = traceColor,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RssiDeviceSelector(
    sessions: List<DeviceSessionUiState>,
    selectedDeviceId: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedSession = sessions.firstOrNull { it.id == selectedDeviceId } ?: return
    val selectedColor = Color(selectedSession.traceColorArgb)

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(selectedColor, CircleShape),
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Target device", style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = compactDeviceUiLabel(selectedSession.name, selectedSession.address),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = sessionSelectorTelemetryLabel(selectedSession),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(Icons.Outlined.ArrowDropDown, contentDescription = "Choose device")
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            sessions.forEach { session ->
                val traceColor = Color(session.traceColorArgb)
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(traceColor, CircleShape),
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(compactDeviceUiLabel(session.name, session.address))
                                Text(
                                    text = sessionSelectorTelemetryLabel(session),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                                )
                            }
                        }
                    },
                    onClick = {
                        onSelect(session.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun RssiFleetSummary(sessions: List<DeviceSessionUiState>) {
    val strongest = sessions.maxByOrNull { it.rssi ?: Int.MIN_VALUE }
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.46f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Compare ${sessions.size} nearby device${if (sessions.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "Move with the phone: stronger, less-negative RSSI means closer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f),
                )
            }
            strongest?.let { device ->
                val traceColor = Color(device.traceColorArgb)
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Strongest now",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f),
                    )
                    Text(
                        text = device.rssi?.let { "$it dBm" } ?: "--",
                        style = MaterialTheme.typography.titleMedium,
                        color = traceColor,
                    )
                    Text(
                        text = compactDeviceUiLabel(device.name, device.address),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun RssiTimelineChart(
    sessions: List<DeviceSessionUiState>,
    nowMs: Long,
    windowMs: Long,
    modifier: Modifier = Modifier,
) {
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
    val labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f)
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val rangeMin = -100f
    val rangeMax = -30f

    Box(
        modifier = Modifier
            .then(modifier)
            .fillMaxWidth()
            .heightIn(min = 170.dp)
            .background(surfaceColor, RoundedCornerShape(14.dp))
            .border(1.dp, gridColor, RoundedCornerShape(14.dp)),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 42.dp, top = 16.dp, end = 12.dp, bottom = 24.dp),
        ) {
            repeat(5) { index ->
                val fraction = index / 4f
                val y = size.height * fraction
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            repeat(5) { index ->
                val fraction = index / 4f
                val x = size.width * fraction
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
            }

            val cutoffMs = nowMs - windowMs
            sessions.forEach { session ->
                val visibleSamples = session.rssiHistory.filter { sample ->
                    sample.timestampMs in cutoffMs..nowMs
                }
                if (visibleSamples.isEmpty()) {
                    return@forEach
                }

                val path = Path()
                visibleSamples.forEachIndexed { index, sample ->
                    val ageFraction = ((nowMs - sample.timestampMs).toFloat() / windowMs)
                        .coerceIn(0f, 1f)
                    val x = size.width * (1f - ageFraction)
                    val clamped = sample.valueDbm.toFloat().coerceIn(rangeMin, rangeMax)
                    val y = size.height * ((rangeMax - clamped) / (rangeMax - rangeMin))
                    if (index == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }
                }
                drawPath(
                    path = path,
                    color = Color(session.traceColorArgb).copy(alpha = 0.94f),
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(start = 4.dp, top = 10.dp, bottom = 18.dp)
                .width(34.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("dBm", style = MaterialTheme.typography.labelSmall, color = labelColor)
            listOf("-30", "-48", "-65", "-82", "-100").forEach { value ->
                Text(value, style = MaterialTheme.typography.labelSmall, color = labelColor)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(start = 44.dp, end = 14.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("-${formatRssiWindow(windowMs)}", style = MaterialTheme.typography.labelSmall, color = labelColor)
            Text("now", style = MaterialTheme.typography.labelSmall, color = labelColor)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdvertisementStatusSummary(session: DeviceSessionUiState) {
    val latest = session.advertisementHistory.lastOrNull()
    val stateLabel = advertisementTimelineStateLabel(latest)
    val stateColor = advertisementTimelineStateColor(latest)
    Surface(
        color = stateColor.copy(alpha = 0.10f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(stateColor, CircleShape),
                )
                Spacer(Modifier.width(8.dp))
                Text("Latest advertisement: $stateLabel", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatRecentSeenLabel(session),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                latest?.voltage?.let { voltage ->
                    DeviceDashboardMetricChip("Battery ${formatVoltageLabel(voltage)}", stateColor)
                }
                latest?.storageUsedPercent?.let { percent ->
                    DeviceDashboardMetricChip("Storage $percent%")
                }
                latest?.recordingSeconds?.takeIf { seconds -> seconds > 0L }?.let { seconds ->
                    DeviceDashboardMetricChip("Rec ${formatSeconds(seconds)}")
                }
                latest?.advertisedSampleRateHz?.takeIf { rate -> rate > 0 }?.let { rate ->
                    DeviceDashboardMetricChip("Rate $rate Hz")
                }
                latest?.aiModelId?.let { modelId ->
                    val aiLabel = buildString {
                        append("AI M$modelId")
                        latest.aiClassId?.let { classId -> append(" C$classId") }
                        latest.aiConfidencePercentage?.let { confidence -> append(" $confidence%") }
                        if (latest.aiResultIsNew) append(" new")
                    }
                    DeviceDashboardMetricChip(
                        aiLabel,
                        if (latest.aiResultIsNew) MaterialTheme.colorScheme.primary else stateColor,
                    )
                }
                latest?.lastEventCode?.takeIf { eventCode -> eventCode != 0 }?.let { eventCode ->
                    DeviceDashboardMetricChip("Event 0x${eventCode.toString(16).uppercase(Locale.US)}")
                }
            }
            Text(
                text = if (latest?.hasStateTelemetry == true) {
                    "Broadcast state only; this is independent of the phone's BLE connection state."
                } else {
                    "No CE64 state payload yet. This device currently advertises RSSI${if (latest?.voltage != null) " and battery" else ""} only."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
            )
        }
    }
}

@Composable
private fun AdvertisementStatusTimelineChart(
    session: DeviceSessionUiState,
    nowMs: Long,
    windowMs: Long,
    modifier: Modifier = Modifier,
) {
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
    val labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f)
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val rows = remember {
        listOf(
            AdvertisementStatePlotRow(
                label = "Recording",
                color = Color(0xFFD94343),
                isActive = { sample -> sample.recording == true },
            ),
            AdvertisementStatePlotRow(
                label = "Live signal",
                color = Color(0xFF1687F2),
                isActive = { sample -> sample.previewing == true },
            ),
            AdvertisementStatePlotRow(
                label = "Attention",
                color = Color(0xFFF08A18),
                isActive = { sample ->
                    (sample.failedSubsystems ?: 0) != 0 || (sample.degradedSubsystems ?: 0) != 0
                },
            ),
            AdvertisementStatePlotRow(
                label = "CE64 status",
                color = Color(0xFF2BA66B),
                isActive = AdvertisementStatusSampleUiState::hasStateTelemetry,
            ),
        )
    }
    val visibleSamples = session.advertisementHistory.filter { sample ->
        sample.timestampMs in (nowMs - windowMs)..nowMs && sample.hasStateTelemetry
    }

    Box(
        modifier = Modifier
            .then(modifier)
            .fillMaxWidth()
            .heightIn(min = 170.dp)
            .background(surfaceColor, RoundedCornerShape(14.dp))
            .border(1.dp, gridColor, RoundedCornerShape(14.dp)),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 88.dp, top = 16.dp, end = 12.dp, bottom = 24.dp),
        ) {
            rows.indices.forEach { index ->
                val y = size.height * (index + 0.5f) / rows.size
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            repeat(5) { index ->
                val fraction = index / 4f
                val x = size.width * fraction
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
            }

            rows.forEachIndexed { rowIndex, row ->
                val rowTop = size.height * rowIndex / rows.size + 5.dp.toPx()
                val rowHeight = size.height / rows.size - 10.dp.toPx()
                visibleSamples.forEachIndexed sampleLoop@ { index, sample ->
                    if (!row.isActive(sample)) {
                        return@sampleLoop
                    }
                    val startFraction = ((sample.timestampMs - (nowMs - windowMs)).toFloat() / windowMs)
                        .coerceIn(0f, 1f)
                    val nextTimestampMs = visibleSamples.getOrNull(index + 1)?.timestampMs
                        ?: (sample.timestampMs + 1_000L).coerceAtMost(nowMs)
                    val endFraction = ((nextTimestampMs - (nowMs - windowMs)).toFloat() / windowMs)
                        .coerceIn(startFraction, 1f)
                    val left = size.width * startFraction
                    val right = maxOf(left + 3.dp.toPx(), size.width * endFraction).coerceAtMost(size.width)
                    drawRect(
                        color = row.color.copy(alpha = 0.90f),
                        topLeft = Offset(left, rowTop),
                        size = Size((right - left).coerceAtLeast(1f), rowHeight.coerceAtLeast(1f)),
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(start = 8.dp, top = 14.dp, bottom = 20.dp)
                .width(76.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            rows.forEach { row ->
                Text(row.label, style = MaterialTheme.typography.labelSmall, color = labelColor)
            }
        }
        if (visibleSamples.isEmpty()) {
            Text(
                text = "No CE64 state fields in recent advertisements.",
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.bodyMedium,
                color = labelColor,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(start = 90.dp, end = 14.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("-${formatRssiWindow(windowMs)}", style = MaterialTheme.typography.labelSmall, color = labelColor)
            Text("now", style = MaterialTheme.typography.labelSmall, color = labelColor)
        }
    }
}

private fun advertisementTimelineStateLabel(sample: AdvertisementStatusSampleUiState?): String {
    return when {
        sample == null -> "No state payload"
        !sample.hasStateTelemetry -> "Telemetry without state"
        (sample.failedSubsystems ?: 0) != 0 -> "Fault reported"
        (sample.degradedSubsystems ?: 0) != 0 -> "Degraded reported"
        sample.recording == true -> "Recording"
        sample.previewing == true -> "Live signal"
        else -> "Idle / ready"
    }
}

private fun advertisementTimelineStateColor(sample: AdvertisementStatusSampleUiState?): Color {
    return when {
        sample == null -> Color(0xFF7A8494)
        (sample.failedSubsystems ?: 0) != 0 -> Color(0xFFD94343)
        (sample.degradedSubsystems ?: 0) != 0 -> Color(0xFFF08A18)
        sample.recording == true -> Color(0xFFD94343)
        sample.previewing == true -> Color(0xFF1687F2)
        sample.hasStateTelemetry -> Color(0xFF2BA66B)
        else -> Color(0xFF7A8494)
    }
}

@Composable
private fun RssiMonitorDeviceRow(
    session: DeviceSessionUiState,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color(session.traceColorArgb).copy(alpha = 0.38f),
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onSelect),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .background(Color(session.traceColorArgb), CircleShape),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = compactDeviceUiLabel(session.name),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(rssiQualityLabel(session.rssi))
                        val state = advertisementTimelineStateLabel(session.advertisementHistory.lastOrNull())
                        if (state != "Waiting for advertisement" && state != "Telemetry without state") {
                            append(" · ")
                            append(state)
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = session.rssi?.let { "$it dBm" } ?: "--",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "${session.rssiHistory.size} samples",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f),
                )
            }
        }
    }
}

private fun formatRssiWindow(windowMs: Long): String {
    return if (windowMs % 60_000L == 0L) {
        "${windowMs / 60_000L}m"
    } else {
        "${windowMs / 1_000L}s"
    }
}

private fun rssiQualityLabel(rssi: Int?): String = when {
    rssi == null -> "--"
    rssi >= -55 -> "Excellent"
    rssi >= -67 -> "Good"
    rssi >= -75 -> "Fair"
    else -> "Weak"
}

@Composable
private fun DevicesScopeActionCard(
    scope: ControlScope,
    connectedCount: Int,
    selectedConnectedCount: Int,
    pendingCount: Int,
    fleetConnectActive: Boolean,
    canLaunchScope: Boolean,
    onConnectQueued: () -> Unit,
    onCancelFleetConnect: () -> Unit,
    onClearPendingConnections: () -> Unit,
    onDisconnectAll: () -> Unit,
    onScopeChange: (ControlScope) -> Unit,
    onOpenPreview: () -> Unit,
    onOpenControl: () -> Unit,
    onOpenRecords: () -> Unit,
) {
    val showSelectedScope = selectedConnectedCount > 0 || scope == ControlScope.SelectedDevices
    val showAllScope = connectedCount > 0 || scope == ControlScope.AllConnected
    ControlCard(
        title = "",
        subtitle = "",
    ) {
        CompactHubMetricBand(
            metrics = buildList {
                add("Linked" to connectedCount.toString())
                if (showSelectedScope) {
                    add("Selected" to selectedConnectedCount.toString())
                }
                if (pendingCount > 0 || fleetConnectActive) {
                    add("Queued" to pendingCount.toString())
                }
                add(
                    "Use" to when (scope) {
                        ControlScope.ActiveDevice -> "One"
                        ControlScope.SelectedDevices -> "Selected"
                        ControlScope.AllConnected -> "All"
                    },
                )
            },
        )

        val connectActions = buildList {
            if (pendingCount > 0 || fleetConnectActive) {
                add(
                    PocketButtonSpec(
                        label = if (fleetConnectActive) {
                            "Stop Queue"
                        } else {
                            "Connect Queue $pendingCount"
                        },
                        onClick = if (fleetConnectActive) onCancelFleetConnect else onConnectQueued,
                        enabled = fleetConnectActive || pendingCount > 0,
                    ),
                )
                add(
                    PocketButtonSpec(
                        label = "Clear Queue",
                        onClick = onClearPendingConnections,
                        enabled = pendingCount > 0 || fleetConnectActive,
                    ),
                )
            }
            if (connectedCount > 0) {
                add(
                    PocketButtonSpec(
                        label = "Disconnect All",
                        onClick = onDisconnectAll,
                        enabled = connectedCount > 0,
                    ),
                )
            }
        }
        if (connectActions.isNotEmpty()) {
            PocketButtonGrid(
                options = connectActions,
                columns = minOf(3, connectActions.size.coerceAtLeast(1)),
            )
        }

        val scopeButtons = buildList {
            add(
                PocketButtonSpec(
                    label = "One",
                    onClick = { onScopeChange(ControlScope.ActiveDevice) },
                    selected = scope == ControlScope.ActiveDevice,
                ),
            )
            if (showSelectedScope) {
                add(
                    PocketButtonSpec(
                        label = if (selectedConnectedCount > 0) "Selected $selectedConnectedCount" else "Selected",
                        onClick = { onScopeChange(ControlScope.SelectedDevices) },
                        enabled = selectedConnectedCount > 0,
                        selected = scope == ControlScope.SelectedDevices,
                    ),
                )
            }
            if (showAllScope) {
                add(
                    PocketButtonSpec(
                        label = if (connectedCount > 0) "All $connectedCount" else "All",
                        onClick = { onScopeChange(ControlScope.AllConnected) },
                        enabled = connectedCount > 0,
                        selected = scope == ControlScope.AllConnected,
                    ),
                )
            }
        }
        PocketButtonGrid(
            options = scopeButtons,
            columns = minOf(3, scopeButtons.size.coerceAtLeast(1)),
        )

        PocketButtonGrid(
            options = listOf(
                PocketButtonSpec(
                    label = "Preview",
                    onClick = onOpenPreview,
                    enabled = canLaunchScope,
                ),
                PocketButtonSpec(
                    label = "Control",
                    onClick = onOpenControl,
                    enabled = canLaunchScope,
                ),
                PocketButtonSpec(
                    label = "Records",
                    onClick = onOpenRecords,
                    enabled = canLaunchScope,
                ),
            ),
        )
    }
}

@Composable
private fun PreviewScreen(
    modifier: Modifier = Modifier,
    uiState: WildUiState,
    onActivateSession: (String) -> Unit,
    onOpenDevices: () -> Unit,
    onOpenOperate: () -> Unit,
    onStartPreview: (List<String>) -> Unit,
    onStopPreview: (List<String>) -> Unit,
    onStartRecording: (List<String>) -> Unit,
    onStopRecording: (List<String>) -> Unit,
    onSetPreviewSelectionForDevice: (String, PreviewSelection) -> Unit,
) {
    val previewSessions = remember(
        uiState.controlScope,
        uiState.sessions,
        uiState.connectedSessions,
        uiState.selectedSessions,
        uiState.selectedConnectedSessions,
        uiState.activeSessionId,
        uiState.activeSession,
    ) {
        resolvePreviewRouteSessions(uiState)
    }
    val previewControlTargetIds = remember(
        uiState.controlScope,
        uiState.sessions,
        uiState.connectedSessions,
        uiState.selectedSessions,
        uiState.selectedConnectedSessions,
        uiState.activeSessionId,
        uiState.activeSession,
    ) {
        resolvePreviewRouteTargetIds(uiState)
    }
    val allConnectedTargetIds = remember(uiState.connectedSessions) {
        uiState.connectedSessions.map { it.id }
    }
    val commandScopeLabel = when (uiState.controlScope) {
        ControlScope.ActiveDevice -> "This device"
        ControlScope.SelectedDevices -> {
            "Selected ${previewControlTargetIds.size} device${if (previewControlTargetIds.size == 1) "" else "s"}"
        }
        ControlScope.AllConnected -> {
            "All ${previewControlTargetIds.size} connected"
        }
    }
    val activeSession = previewSessions.firstOrNull { it.id == uiState.activeSessionId } ?: previewSessions.firstOrNull()
    var signalWindowPresetName by rememberSaveable { mutableStateOf(SignalWindowPreset.TenSeconds.name) }
    var signalGainPresetName by rememberSaveable { mutableStateOf(SignalGainPreset.OneMilliVolt.name) }
    var signalViewModeName by rememberSaveable { mutableStateOf(SignalViewMode.Stacked.name) }
    var removeSignalDc by rememberSaveable { mutableStateOf(true) }
    val signalViewMode = SignalViewMode.valueOf(signalViewModeName)
    val displayConfig = SignalDisplayConfig(
        window = SignalWindowPreset.valueOf(signalWindowPresetName),
        gain = SignalGainPreset.valueOf(signalGainPresetName),
        removeDc = removeSignalDc,
    )

    LaunchedEffect(activeSession?.id) {
        val session = activeSession ?: return@LaunchedEffect
        if (uiState.activeSessionId != session.id) {
            onActivateSession(session.id)
        }
    }

    Box(modifier = modifier) {
        if (previewSessions.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                EmptyStateCard(compactUiCopy("Select a device on Devices before opening Preview.", "Select a device to preview."))
                FilledTonalButton(
                    onClick = onOpenDevices,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Open Devices")
                }
                Spacer(modifier = Modifier.weight(1f))
            }
            return@Box
        }

        SignalPlotCard(
            modifier = Modifier.fillMaxSize(),
            sessions = previewSessions,
            allConnectedSessions = uiState.connectedSessions,
            commandScopeLabel = commandScopeLabel,
            activeSessionId = activeSession?.id,
            onActivateSession = onActivateSession,
            onOpenDevices = onOpenDevices,
            onOpenOperate = onOpenOperate,
            onSetPreviewSelection = onSetPreviewSelectionForDevice,
            displayConfig = displayConfig,
            onWindowChange = { signalWindowPresetName = it.name },
            onGainChange = { signalGainPresetName = it.name },
            onRemoveDcChange = { removeSignalDc = it },
            signalViewMode = signalViewMode,
            onStartPreview = { onStartPreview(previewControlTargetIds) },
            onStopPreview = { onStopPreview(previewControlTargetIds) },
            onStartRecording = { onStartRecording(previewControlTargetIds) },
            onStopRecording = { onStopRecording(previewControlTargetIds) },
            onStartAllPreview = { onStartPreview(allConnectedTargetIds) },
            onStopAllPreview = { onStopPreview(allConnectedTargetIds) },
        )
    }
}

@Composable
private fun DeviceDashboardMetricChip(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(
        color = color.copy(alpha = 0.10f),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

private fun deviceDashboardStatusColor(session: DeviceSessionUiState): Color {
    return when {
        session.isRecordingLike -> Color(0xFFD94343)
        session.hostState == BleHostSessionState.Previewing -> Color(0xFF1687F2)
        session.isConnected -> Color(0xFF2BA66B)
        session.isLinkingLike -> Color(0xFFF08A18)
        session.lastFailure.isNotBlank() -> Color(0xFFD94343)
        else -> Color(0xFF7A8494)
    }
}

private fun formatDeviceDashboardDuration(seconds: Long): String {
    val safeSeconds = seconds.coerceAtLeast(0L)
    val hours = safeSeconds / 3_600L
    val minutes = (safeSeconds % 3_600L) / 60L
    val remainderSeconds = safeSeconds % 60L
    return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, remainderSeconds)
}

/*
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PocketFleetHubCard(
    uiState: WildUiState,
    rosterPane: DeviceRosterPane,
    verifiedCount: Int,
    nearbyCount: Int,
    connectedCount: Int,
    linkingCount: Int,
    selectedGroupCount: Int,
    markedCount: Int,
    markedQueueableCount: Int,
    pendingCount: Int,
    selectedGroupSessions: List<DeviceSessionUiState>,
    markedSessions: List<DeviceSessionUiState>,
    pendingSessions: List<DeviceSessionUiState>,
    canLaunchScope: Boolean,
    queueVisibleCount: Int,
    queueVisibleIds: List<String>,
    queueVisibleLabel: String?,
    onConnectVisible: (List<String>) -> Unit,
    onConnectQueued: () -> Unit,
    onCancelFleetConnect: () -> Unit,
    onClearPendingConnections: () -> Unit,
    onSelectAllConnectedSessions: () -> Unit,
    onClearSelectedSessions: () -> Unit,
    onClearMarkedSessions: () -> Unit,
    onDisconnectAll: () -> Unit,
    onScopeChange: (ControlScope) -> Unit,
    onRosterPaneChange: (DeviceRosterPane) -> Unit,
    onQueueVisible: () -> Unit,
    onQueueMarked: () -> Unit,
    onRemoveSelected: (String) -> Unit,
    onRemoveMarked: (String) -> Unit,
    onRemoveQueued: (String) -> Unit,
    onOpenLive: () -> Unit,
    onOpenControl: () -> Unit,
    onOpenRecords: () -> Unit,
) {
    var paneName by rememberSaveable { mutableStateOf(FleetHubPane.Link.name) }
    val pane = FleetHubPane.valueOf(paneName)
    ControlCard(
        title = "",
        subtitle = "",
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InfoPill("Ready", verifiedCount.toString())
            InfoPill("Conn", connectedCount.toString())
            InfoPill(
                "Scope",
                when (uiState.controlScope) {
                    ControlScope.ActiveDevice -> "One"
                    ControlScope.SelectedDevices -> "Sel"
                    ControlScope.AllConnected -> "All"
                },
            )
            if (nearbyCount > 0 || linkingCount > 0) {
                InfoPill(
                    if (linkingCount > 0) "Linking" else "Nearby",
                    if (linkingCount > 0) linkingCount.toString() else nearbyCount.toString(),
                )
            }
            if (selectedGroupCount > 0) {
                InfoPill("Scope", selectedGroupCount.toString())
            }
            if (markedCount > 0) {
                InfoPill("Pick", markedCount.toString())
            }
            if (pendingCount > 0) {
                InfoPill("Pending", pendingCount.toString())
            }
        }

        PocketButtonGrid(
            options = FleetHubPane.entries.map { entry ->
                PocketButtonSpec(
                    label = entry.label,
                    onClick = { paneName = entry.name },
                    selected = pane == entry,
                )
            },
        )

        when (pane) {
            FleetHubPane.Link -> {
                val laneActionLabel: String
                val laneActionEnabled: Boolean
                val laneActionClick: () -> Unit
                when (rosterPane) {
                    DeviceRosterPane.Connected -> {
                        laneActionLabel = "Scope Linked"
                        laneActionEnabled = connectedCount > 0
                        laneActionClick = onSelectAllConnectedSessions
                    }
                    DeviceRosterPane.Linking -> {
                        if (uiState.fleetConnectActive) {
                            laneActionLabel = "Stop Connect (${uiState.fleetConnectPendingCount})"
                            laneActionEnabled = true
                            laneActionClick = onCancelFleetConnect
                        } else {
                            laneActionLabel = "Linking"
                            laneActionEnabled = false
                            laneActionClick = {}
                        }
                    }
                    DeviceRosterPane.Verified -> {
                        laneActionLabel = "Connect Listed"
                        laneActionEnabled = queueVisibleIds.isNotEmpty() && !uiState.fleetConnectActive
                        laneActionClick = { onConnectVisible(queueVisibleIds) }
                    }
                    DeviceRosterPane.Nearby -> {
                        laneActionLabel = "Pick Listed"
                        laneActionEnabled = queueVisibleIds.isNotEmpty() && !uiState.fleetConnectActive
                        laneActionClick = onQueueVisible
                    }
                }

                PocketButtonGrid(
                    options = DeviceRosterPane.entries.map { entry ->
                        val count = when (entry) {
                            DeviceRosterPane.Connected -> connectedCount
                            DeviceRosterPane.Linking -> linkingCount
                            DeviceRosterPane.Verified -> verifiedCount
                            DeviceRosterPane.Nearby -> nearbyCount
                        }
                        PocketButtonSpec(
                            label = "${entry.label} $count",
                            onClick = { onRosterPaneChange(entry) },
                            selected = rosterPane == entry,
                        )
                    },
                    columns = 2,
                )

                OutlinedButton(
                    onClick = laneActionClick,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = laneActionEnabled,
                ) {
                    Text(laneActionLabel)
                }

                if (uiState.fleetConnectActive) {
                    Text(
                        "Connecting ${uiState.fleetConnectPendingCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                    )
                }
            }

            FleetHubPane.Queue -> {
                if (queueVisibleLabel != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = onQueueVisible,
                            modifier = Modifier.weight(1f),
                            enabled = queueVisibleCount > 0 && !uiState.fleetConnectActive,
                        ) {
                            Text("$queueVisibleLabel ($queueVisibleCount)")
                        }
                        OutlinedButton(
                            onClick = onQueueMarked,
                            modifier = Modifier.weight(1f),
                            enabled = markedQueueableCount > 0 && !uiState.fleetConnectActive,
                        ) {
                            Text("Add Picked ($markedQueueableCount)")
                        }
                    }
                } else if (markedQueueableCount > 0) {
                    OutlinedButton(
                        onClick = onQueueMarked,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.fleetConnectActive,
                    ) {
                        Text("Add Picked ($markedQueueableCount)")
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(
                        onClick = onConnectQueued,
                        modifier = Modifier.weight(1f),
                        enabled = pendingCount > 0 && !uiState.fleetConnectActive,
                    ) {
                        Text("Connect Pending ($pendingCount)")
                    }
                    OutlinedButton(
                        onClick = {
                            if (uiState.fleetConnectActive) {
                                onCancelFleetConnect()
                            } else {
                                onClearPendingConnections()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = if (uiState.fleetConnectActive) true else uiState.pendingConnectionIds.isNotEmpty(),
                    ) {
                        Text(
                            if (uiState.fleetConnectActive) {
                                "Stop Connect (${uiState.fleetConnectPendingCount})"
                            } else {
                                "Clear Pending"
                            }
                        )
                    }
                }

                PocketButtonGrid(
                    options = listOf(
                        PocketButtonSpec(
                            label = "Scope Linked $connectedCount",
                            onClick = onSelectAllConnectedSessions,
                            enabled = connectedCount > 0,
                        ),
                        PocketButtonSpec(
                            label = "Clear Scope",
                            onClick = onClearSelectedSessions,
                            enabled = selectedGroupCount > 0,
                        ),
                        PocketButtonSpec(
                            label = "Clear Picked",
                            onClick = onClearMarkedSessions,
                            enabled = markedCount > 0,
                        ),
                    ),
                )

                SessionOrderGrid(
                    title = "Scope",
                    sessions = selectedGroupSessions,
                    activeSessionId = uiState.activeSessionId,
                    helperText = "",
                    onSessionClick = onRemoveSelected,
                )

                SessionOrderGrid(
                    title = "Picked",
                    sessions = markedSessions,
                    activeSessionId = uiState.activeSessionId,
                    helperText = "",
                    onSessionClick = onRemoveMarked,
                )

                SessionOrderGrid(
                    title = "Pending",
                    sessions = pendingSessions,
                    activeSessionId = uiState.activeSessionId,
                    helperText = "",
                    onSessionClick = onRemoveQueued,
                )
            }

        }

        PocketButtonGrid(
            options = listOf(
                PocketButtonSpec(
                    label = "One",
                    onClick = { onScopeChange(ControlScope.ActiveDevice) },
                    enabled = uiState.activeSession != null,
                    selected = uiState.controlScope == ControlScope.ActiveDevice,
                ),
                PocketButtonSpec(
                    label = "Scope ${uiState.selectedSessions.size}",
                    onClick = { onScopeChange(ControlScope.SelectedDevices) },
                    enabled = uiState.selectedSessions.isNotEmpty(),
                    selected = uiState.controlScope == ControlScope.SelectedDevices,
                ),
                PocketButtonSpec(
                    label = "All $connectedCount",
                    onClick = { onScopeChange(ControlScope.AllConnected) },
                    enabled = connectedCount > 0,
                    selected = uiState.controlScope == ControlScope.AllConnected,
                ),
            ),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(
                onClick = onOpenLive,
                modifier = Modifier.weight(1f),
                enabled = canLaunchScope,
            ) {
                Text("Live")
            }
            OutlinedButton(
                onClick = onOpenControl,
                modifier = Modifier.weight(1f),
                enabled = canLaunchScope,
            ) {
                Text("Control")
            }
            OutlinedButton(
                onClick = onOpenRecords,
                modifier = Modifier.weight(1f),
                enabled = canLaunchScope,
            ) {
                Text("Records")
            }
        }

        OutlinedButton(
            onClick = onDisconnectAll,
            modifier = Modifier.fillMaxWidth(),
            enabled = connectedCount > 0,
        ) {
            Text("Disconnect All")
        }
    }
}

*/
@Composable
private fun DiscoveryBadge(
    label: String,
    emphasized: Boolean,
) {
    AssistChip(
        onClick = {},
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (emphasized) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            labelColor = if (emphasized) {
                MaterialTheme.colorScheme.onBackground
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
            },
        ),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WrappedDiscoveryBadges(
    badges: List<Pair<String, Boolean>>,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        badges.forEach { (label, emphasized) ->
            DiscoveryBadge(label = label, emphasized = emphasized)
        }
    }
}

@Composable
private fun PocketFleetHubCard(
    connectedSessions: List<DeviceSessionUiState>,
    scopeSessions: List<DeviceSessionUiState>,
    activeSession: DeviceSessionUiState?,
    activeSessionId: String?,
    canOpenOnline: Boolean,
    canOpenCamera: Boolean,
    canOpenStatus: Boolean,
    scope: ControlScope,
    selectedCount: Int,
    onScopeChange: (ControlScope) -> Unit,
    fleetPane: FleetPane,
    onFleetPaneChange: (FleetPane) -> Unit,
    onActivate: (String) -> Unit,
    onOpenOnline: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenStatus: () -> Unit,
) {
    val previewCount = scopeSessions.count(::isPreviewingSession)
    val recordingCount = scopeSessions.count { it.isRecordingLike }
    val attentionCount = scopeSessions.count {
        it.hostState == BleHostSessionState.Error || it.hostState == BleHostSessionState.Reconnecting
    }
    val focusSessions = monitorFocusSessions(activeSession, scopeSessions, connectedSessions)
    val scopeOptions = buildCompactScopeOptions(
        scope = scope,
        selectedCount = selectedCount,
        connectedCount = connectedSessions.size,
    )
    val headlineName = when {
        scopeSessions.isEmpty() -> "No devices"
        scope == ControlScope.ActiveDevice -> activeSession?.name ?: scopeSessions.first().name
        else -> "${scopeSessions.size} devices"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(30.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f),
                        )
                    )
                )
                .padding(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Devices", style = MaterialTheme.typography.titleMedium)
                Text(
                    compactDeviceUiLabel(headlineName),
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (SHOW_UI_DESCRIPTIONS) {
                    Text(
                        when (scope) {
                            ControlScope.ActiveDevice ->
                                "Focused scope is armed. Use the fleet page to operate, camera-check, or inspect health on one device without losing the rest of the linked sessions."
                            ControlScope.SelectedDevices ->
                                if (selectedCount > 0) {
                                    "Selection scope is armed for $selectedCount device(s). Use the fleet pages below to operate only that subset."
                                } else {
                                    "Selection scope is armed, but no connected devices are selected yet."
                                }
                            ControlScope.AllConnected ->
                                "Fleet scope is armed. The cards below will show every connected device on the selected fleet page."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    )
                }

                CompactHubMetricBand(
                    metrics = listOf(
                        "Devices" to scopeSessions.size.toString(),
                        "Preview" to previewCount.toString(),
                        "Rec" to recordingCount.toString(),
                        "Alert" to attentionCount.toString(),
                        "Linked" to connectedSessions.size.toString(),
                        "Mode" to fleetPane.label,
                    ),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (scopeOptions.size > 1) {
                        CompactDropdownSelector(
                            currentLabel = scopeOptions.firstOrNull { it.first == scope }?.second ?: compactScopeSelectorLabel(scope),
                            options = scopeOptions.map { (targetScope, label, _) -> targetScope.name to label },
                            selectedOptionName = scope.name,
                            onSelectOption = { onScopeChange(ControlScope.valueOf(it)) },
                            optionEnabled = { optionName ->
                                scopeOptions.firstOrNull { it.first.name == optionName }?.third == true
                            },
                            compact = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    CompactDropdownSelector(
                        currentLabel = fleetPane.label,
                        options = FleetPane.entries.map { it.name to it.label },
                        selectedOptionName = fleetPane.name,
                        onSelectOption = { onFleetPaneChange(FleetPane.valueOf(it)) },
                        compact = true,
                        modifier = Modifier.weight(if (scopeOptions.size > 1) 1f else 1.35f),
                    )
                    if (canOpenOnline) {
                        PreviewCompactToggleChip(
                            label = "Online",
                            selected = false,
                            onClick = onOpenOnline,
                            compact = true,
                        )
                    }
                    if (canOpenCamera) {
                        PreviewCompactToggleChip(
                            label = "Cam",
                            selected = false,
                            onClick = onOpenCamera,
                            compact = true,
                        )
                    }
                    if (canOpenStatus) {
                        PreviewCompactToggleChip(
                            label = "Status",
                            selected = false,
                            onClick = onOpenStatus,
                            compact = true,
                        )
                    }
                }

                if (focusSessions.isEmpty()) {
                    Text(
                        "Connect a device to continue.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    )
                } else {
                    CompactFocusSessionSelector(
                        sessions = focusSessions,
                        activeSessionId = activeSessionId,
                        onActivate = onActivate,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (SHOW_UI_DESCRIPTIONS) {
                    Text(
                        "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveScreen(
    modifier: Modifier = Modifier,
    uiState: WildUiState,
    onPageTitleChange: (String) -> Unit,
    onActivateSession: (String) -> Unit,
    onFocusSession: (String) -> Unit,
    onScopeChange: (ControlScope) -> Unit,
    onToggleSessionSelection: (String) -> Unit,
    onSelectAllConnectedSessions: () -> Unit,
    onClearSelectedSessions: () -> Unit,
    onStartPreviewForDevice: (String) -> Unit,
    onStopPreviewForDevice: (String) -> Unit,
    onSetPreviewSelectionForDevice: (String, PreviewSelection) -> Unit,
    onStartRecordingForDevice: (String) -> Unit,
    onStopRecordingForDevice: (String) -> Unit,
    onForceStopRecordingForDevice: (String) -> Unit,
    onRequestPreviewFrameForDevice: (String) -> Unit,
    onRequestSnapshotForDevice: (String) -> Unit,
    onSetCameraPreviewStreamingForDevice: (String, Boolean) -> Unit,
    onReadCameraParamsForDevice: (String) -> Unit,
    onSetCameraParamsForDevice: (String, Int, Int) -> Unit,
    onResyncForDevice: (String) -> Unit,
    onResyncNoRtcForDevice: (String) -> Unit,
    onRequestImpedanceForDevice: (String) -> Unit,
    onShowSyncLogPathForDevice: (String) -> Unit,
    onShowTriggerWaveformPathForDevice: (String) -> Unit,
    onReadSystemParamsForDevice: (String) -> Unit,
    onReadDspParamsForDevice: (String) -> Unit,
    onReadAllParamsForDevice: (String) -> Unit,
    onRefreshRecordsForDevice: (String) -> Unit,
    onExportAllRecordsForDevice: (String) -> Unit,
    onShowRecordExportPathForDevice: (String) -> Unit,
    onUploadSystemParamsForDevice: (String) -> Unit,
    onUploadDspParamsForDevice: (String, Int) -> Unit,
    onUploadAllParamsForDevice: (String) -> Unit,
    onApplyStreamRatesForDevice: (String, Int, Boolean, Boolean) -> Unit,
    onQuickSetFsForDevice: (String, Int) -> Unit,
    onApplyAcquisitionSystemProfileForDevice: (String, Int, Int, Int) -> Unit,
    onSetTriggerWaveformForDevice: (String, Boolean) -> Unit,
    onApplyStreamRates: (Int, Boolean, Boolean) -> Unit,
    onQuickSetFs: (Int) -> Unit,
    onApplyAcquisitionSystemProfileToSystem: (Int, Int, Int) -> Unit,
    onPreviewSnapshot: () -> Unit,
    onReadCameraParams: () -> Unit,
    onSetTriggerWaveform: (Boolean) -> Unit,
    onShowTriggerWaveformPath: () -> Unit,
    onConnectSession: (String) -> Unit,
    onDisconnectSession: (String) -> Unit,
    onOpenSignalOnline: () -> Unit,
    onOpenStatus: () -> Unit,
    onOpenRecords: () -> Unit,
) {
    val activeSession = uiState.activeSession
    val preservedScopeSessions = preservedScopedSessions(uiState)
    var paneName by rememberSaveable { mutableStateOf(LivePane.Online.name) }
    var onlineToolsPaneName by rememberSaveable { mutableStateOf(OnlineToolsPane.Console.name) }
    var statusPaneName by rememberSaveable { mutableStateOf(StatusPane.Health.name) }
    var fleetPaneName by rememberSaveable { mutableStateOf(FleetPane.Operate.name) }
    val pane = runCatching { LivePane.valueOf(paneName) }.getOrDefault(LivePane.Online)
    val requestedOnlineToolsPane = OnlineToolsPane.valueOf(onlineToolsPaneName)
    val onlineToolsPane = if (requestedOnlineToolsPane == OnlineToolsPane.Hub) {
        OnlineToolsPane.Console
    } else {
        requestedOnlineToolsPane
    }
    val statusPane = StatusPane.valueOf(statusPaneName)
    val fleetPane = FleetPane.valueOf(fleetPaneName)
    val livePageTitle = when (pane) {
        LivePane.Online -> when (onlineToolsPane) {
            OnlineToolsPane.Hub -> "Online"
            OnlineToolsPane.Console -> "Sync"
            OnlineToolsPane.Payload -> "Parameters"
            OnlineToolsPane.Camera -> "Camera"
            OnlineToolsPane.Records -> "Records"
        }
        LivePane.Camera -> "Camera"
        LivePane.Status -> "Status"
        LivePane.Fleet -> "Devices"
    }
    val plotSessions = scopedSessions(uiState)
    val monitorSessions = scopedMonitorSessions(uiState)
    val preservedFocusSessions = monitorFocusSessions(activeSession, preservedScopeSessions, uiState.connectedSessions)
    val focusedSession = activeSession ?: preservedFocusSessions.firstOrNull()
    val focusSessions = monitorFocusSessions(focusedSession, preservedScopeSessions, uiState.connectedSessions)
    val cameraScopeSessions = cameraDisplayScopeSessions(uiState)
    val payloadScopeSessions = payloadDisplayScopeSessions(uiState)
    val recordScopeSessions = recordDisplayScopeSessions(uiState)
    val hasConnectedFocus = focusedSession?.isConnected == true
    val hasPayloadCache = payloadScopeSessions.any(::sessionHasPayloadCache)
    val hasCameraCache = cameraScopeSessions.any(::sessionHasCameraCache)
    val hasRecordCache = recordScopeSessions.any { it.records.isNotEmpty() }
    val hasStatusCache = monitorSessions.any { session ->
        session.liveSync != null ||
            session.lastSyncMetric != null ||
            session.bleLinkStats != null ||
            session.commandRxCount > 0 ||
            session.commandTxCount > 0
    }
    val canOpenOnlinePage = hasConnectedFocus || hasPayloadCache || hasCameraCache || hasRecordCache
    val canOpenCameraPage = hasConnectedFocus || hasCameraCache
    val canOpenStatusPage = uiState.connectedSessions.isNotEmpty() || hasStatusCache
    val targetCount = plotSessions.count()
    val canControlScope = targetCount > 0
    val onFocusedLinkAction: () -> Unit = {
        focusedSession?.let { session ->
            if (session.isConnected || session.isLinkingLike) {
                onDisconnectSession(session.id)
            } else {
                onConnectSession(session.id)
            }
        }
    }
    val onFocusedResync: () -> Unit = {
        focusedSession?.let { session ->
            onResyncForDevice(session.id)
        }
    }
    val onFocusedResyncNoRtc: () -> Unit = {
        focusedSession?.let { session ->
            onResyncNoRtcForDevice(session.id)
        }
    }
    val onFocusedShowSyncLogPath: () -> Unit = {
        focusedSession?.let { session ->
            onShowSyncLogPathForDevice(session.id)
        }
    }
    val onFocusedShowTriggerWaveformPath: () -> Unit = {
        focusedSession?.let { session ->
            onShowTriggerWaveformPathForDevice(session.id)
        }
    }
    val onFocusedReadSystemParams: () -> Unit = {
        focusedSession?.let { session ->
            onReadSystemParamsForDevice(session.id)
        }
    }
    val onFocusedReadDspParams: () -> Unit = {
        focusedSession?.let { session ->
            onReadDspParamsForDevice(session.id)
        }
    }
    val onFocusedReadAllParams: () -> Unit = {
        focusedSession?.let { session ->
            onReadAllParamsForDevice(session.id)
        }
    }
    val onFocusedUploadSystemParams: () -> Unit = {
        focusedSession?.let { session ->
            onUploadSystemParamsForDevice(session.id)
        }
    }
    val onFocusedUploadDspParams: (Int) -> Unit = { dspIndex ->
        focusedSession?.let { session ->
            onUploadDspParamsForDevice(session.id, dspIndex)
        }
    }
    val onFocusedUploadAllParams: () -> Unit = {
        focusedSession?.let { session ->
            onUploadAllParamsForDevice(session.id)
        }
    }
    val onFocusedPreviewSnapshot: () -> Unit = {
        focusedSession?.let { session ->
            onRequestPreviewFrameForDevice(session.id)
        }
    }
    val onFocusedSnapshot: () -> Unit = {
        focusedSession?.let { session ->
            onRequestSnapshotForDevice(session.id)
        }
    }
    val onFocusedSetCameraPreviewStreaming: (Boolean) -> Unit = { enabled ->
        focusedSession?.let { session ->
            onSetCameraPreviewStreamingForDevice(session.id, enabled)
        }
    }
    val onFocusedReadCameraParams: () -> Unit = {
        focusedSession?.let { session ->
            onReadCameraParamsForDevice(session.id)
        }
    }
    val onFocusedSetCameraParams: (Int, Int) -> Unit = { reg0, reg1 ->
        focusedSession?.let { session ->
            onSetCameraParamsForDevice(session.id, reg0, reg1)
        }
    }
    val onFocusedRefreshRecords: () -> Unit = {
        focusedSession?.let { session ->
            onRefreshRecordsForDevice(session.id)
        }
    }
    val onFocusedExportAllRecords: () -> Unit = {
        focusedSession?.let { session ->
            onExportAllRecordsForDevice(session.id)
        }
    }
    val onFocusedShowRecordExportPath: () -> Unit = {
        focusedSession?.let { session ->
            onShowRecordExportPathForDevice(session.id)
        }
    }
    val openOnlinePane = {
        if (activeSession == null) {
            preservedFocusSessions.firstOrNull()?.let { fallback ->
                onActivateSession(fallback.id)
            }
        }
        paneName = LivePane.Online.name
        onlineToolsPaneName = OnlineToolsPane.Console.name
        onOpenSignalOnline()
    }
    val openCameraPane = {
        if (activeSession == null) {
            preservedFocusSessions.firstOrNull()?.let { fallback ->
                onActivateSession(fallback.id)
                onRequestPreviewFrameForDevice(fallback.id)
                onReadCameraParamsForDevice(fallback.id)
            }
        } else {
            onPreviewSnapshot()
            onReadCameraParams()
        }
        paneName = LivePane.Camera.name
    }
    val openStatusPane: (StatusPane) -> Unit = { target ->
        paneName = LivePane.Status.name
        statusPaneName = target.name
        onOpenStatus()
    }

    LaunchedEffect(activeSession?.id, preservedFocusSessions.firstOrNull()?.id) {
        if (activeSession == null) {
            preservedFocusSessions.firstOrNull()?.let { fallback ->
                onActivateSession(fallback.id)
            }
        }
    }
    LaunchedEffect(livePageTitle) {
        onPageTitleChange(livePageTitle)
    }

    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            when (pane) {
                LivePane.Online -> {
                    if (focusedSession != null) {
                        OnlineLaneHeaderCard(
                            connectedSessions = uiState.connectedSessions,
                            focusSessions = focusSessions,
                            session = focusedSession,
                            activeSessionId = uiState.activeSessionId,
                            canOpenStatus = canOpenStatusPage,
                            canOpenConnected = uiState.connectedSessions.isNotEmpty(),
                            activeToolsPane = onlineToolsPane,
                            scope = uiState.controlScope,
                            selectedCount = uiState.selectedSessions.size,
                            payloadCachedCount = payloadScopeSessions.count(::sessionHasPayloadCache),
                            cameraCachedCount = cameraScopeSessions.count(::sessionHasCameraCache),
                            recordCachedCount = recordScopeSessions.count { it.records.isNotEmpty() },
                            onScopeChange = onScopeChange,
                            onActivate = onActivateSession,
                            onLinkAction = onFocusedLinkAction,
                            onResync = onFocusedResync,
                            onOpenStatus = { openStatusPane(StatusPane.Health) },
                            onOpenConnected = { paneName = LivePane.Fleet.name },
                            onOpenConsole = { onlineToolsPaneName = OnlineToolsPane.Console.name },
                            onOpenPayload = {
                                onFocusedReadSystemParams()
                                onlineToolsPaneName = OnlineToolsPane.Payload.name
                            },
                            onOpenCamera = {
                                onFocusedPreviewSnapshot()
                                onFocusedReadCameraParams()
                                onlineToolsPaneName = OnlineToolsPane.Camera.name
                            },
                            onOpenRecords = {
                                onFocusedRefreshRecords()
                                onlineToolsPaneName = OnlineToolsPane.Records.name
                            },
                        )
                    } else {
                        EmptyStateCard(compactUiCopy("Connect and focus a device to open the online tools and record index actions.", "Focus a connected device."))
                    }
                }

                LivePane.Status -> {
                    PocketStatusHubCard(
                        connectedSessions = uiState.connectedSessions,
                        scopeSessions = monitorSessions,
                        activeSession = activeSession,
                        activeSessionId = uiState.activeSessionId,
                        canOpenOnline = canOpenOnlinePage,
                        canOpenCamera = canOpenCameraPage,
                        canOpenConnected = uiState.connectedSessions.isNotEmpty(),
                        scope = uiState.controlScope,
                        selectedCount = uiState.selectedSessions.size,
                        statusPane = statusPane,
                        onScopeChange = onScopeChange,
                        onStatusPaneChange = { statusPaneName = it.name },
                        onActivate = onActivateSession,
                        onOpenOnline = openOnlinePane,
                        onOpenCamera = openCameraPane,
                        onOpenConnected = { paneName = LivePane.Fleet.name },
                    )
                }

                LivePane.Camera -> {
                    PocketCameraHubCard(
                        connectedSessions = uiState.connectedSessions,
                        scopeSessions = cameraScopeSessions,
                        linkedScopeCount = plotSessions.size,
                        activeSession = activeSession,
                        activeSessionId = uiState.activeSessionId,
                        canOpenOnline = canOpenOnlinePage,
                        canOpenStatus = canOpenStatusPage,
                        canOpenConnected = uiState.connectedSessions.isNotEmpty(),
                        scope = uiState.controlScope,
                        selectedCount = uiState.selectedSessions.size,
                        onScopeChange = onScopeChange,
                        onActivate = onActivateSession,
                        onOpenOnline = openOnlinePane,
                        onOpenStatus = { openStatusPane(StatusPane.Health) },
                        onOpenConnected = { paneName = LivePane.Fleet.name },
                    )
                }

                LivePane.Fleet -> {
                    PocketFleetHubCard(
                        connectedSessions = uiState.connectedSessions,
                        scopeSessions = monitorSessions,
                        activeSession = activeSession,
                        activeSessionId = uiState.activeSessionId,
                        canOpenOnline = canOpenOnlinePage,
                        canOpenCamera = canOpenCameraPage,
                        canOpenStatus = canOpenStatusPage,
                        scope = uiState.controlScope,
                        selectedCount = uiState.selectedSessions.size,
                        onScopeChange = onScopeChange,
                        fleetPane = fleetPane,
                        onFleetPaneChange = { fleetPaneName = it.name },
                        onActivate = onActivateSession,
                        onOpenOnline = openOnlinePane,
                        onOpenCamera = openCameraPane,
                        onOpenStatus = { openStatusPane(StatusPane.Health) },
                    )
                }
            }
        }

        when (pane) {
            LivePane.Online -> {
                if (focusedSession != null) {
                    val consoleWallSessions = monitorSessions.filter { it.id != focusedSession.id }
                    val payloadWallSessions = payloadScopeSessions.filter { it.id != focusedSession.id }
                    val cameraWallSessions = cameraScopeSessions.filter { it.id != focusedSession.id }
                    val recordsWallSessions = recordScopeSessions.filter { it.id != focusedSession.id }

                    when (onlineToolsPane) {
                        OnlineToolsPane.Console,
                        OnlineToolsPane.Hub -> {
                            item {
                                SignalMonitorCompanionCard(
                                    session = focusedSession,
                                    subtitle = "",
                                    onLinkAction = onFocusedLinkAction,
                                    onResync = onFocusedResync,
                                    onResyncNoRtc = onFocusedResyncNoRtc,
                                    onShowSyncLogPath = onFocusedShowSyncLogPath,
                                    onShowTriggerWaveformPath = onFocusedShowTriggerWaveformPath,
                                )
                            }
                            if (consoleWallSessions.isNotEmpty()) {
                                item {
                                    FocusSessionGrid(
                                        sessions = consoleWallSessions,
                                        activeSessionId = null,
                                        helperText = null,
                                        subtitleForSession = { session ->
                                            when {
                                                session.hostState == BleHostSessionState.Error -> "ERR"
                                                session.isRecordingLike -> "REC"
                                                session.awaitingLiveSync && session.isConnected -> "SYNC"
                                                isPreviewingSession(session) -> "LIVE"
                                                else -> compactSessionStateLabel(session)
                                            }
                                        },
                                        onActivate = { deviceId ->
                                            onActivateSession(deviceId)
                                        },
                                    )
                                }
                            }
                        }

                        OnlineToolsPane.Payload -> {
                            item {
                                SignalOnlineToolkitCard(
                                    session = focusedSession,
                                    scope = uiState.controlScope,
                                    preservedScopeCount = preservedScopeSessions.size,
                                    targetCount = plotSessions.count(),
                                    systemReadyCount = plotSessions.count { it.parsedSystemParams != null && it.systemParamHex.isNotBlank() },
                                    dsp1ReadyCount = plotSessions.count {
                                        it.parsedDsp1Params != null && it.dsp1ParamHex.isNotBlank()
                                    },
                                    dsp2ReadyCount = plotSessions.count {
                                        it.parsedDsp2Params != null && it.dsp2ParamHex.isNotBlank()
                                    },
                                    fullPayloadReadyCount = plotSessions.count {
                                        it.parsedSystemParams != null &&
                                            it.systemParamHex.isNotBlank() &&
                                            it.parsedDsp1Params != null &&
                                            it.dsp1ParamHex.isNotBlank() &&
                                            it.parsedDsp2Params != null &&
                                            it.dsp2ParamHex.isNotBlank()
                                    },
                                    subtitle = "",
                                    onReadSystemParams = onFocusedReadSystemParams,
                                    onReadDspParams = onFocusedReadDspParams,
                                    onReadAllParams = onFocusedReadAllParams,
                                    onUploadSystemParams = onFocusedUploadSystemParams,
                                    onUploadDspParams = onFocusedUploadDspParams,
                                    onUploadAllParams = onFocusedUploadAllParams,
                                    onApplyStreamRates = onApplyStreamRates,
                                    onQuickSetFs = onQuickSetFs,
                                    onApplySystemProfileToSystem = onApplyAcquisitionSystemProfileToSystem,
                                    onSetTriggerWaveform = onSetTriggerWaveform,
                                    onShowTriggerWaveformPath = onShowTriggerWaveformPath,
                                )
                            }
                            items(payloadWallSessions, key = { it.id }) { session ->
                                CompactPayloadFleetCard(
                                    session = session,
                                    isActive = session.id == uiState.activeSessionId,
                                    onActivate = {
                                        onActivateSession(session.id)
                                        onReadSystemParamsForDevice(session.id)
                                    },
                                    onReadSystem = { onReadSystemParamsForDevice(session.id) },
                                    onReadDsp = { onReadDspParamsForDevice(session.id) },
                                    onReadAll = { onReadAllParamsForDevice(session.id) },
                                    onUploadSystem = { onUploadSystemParamsForDevice(session.id) },
                                    onUploadDsp = { dspIndex ->
                                        onUploadDspParamsForDevice(session.id, dspIndex)
                                    },
                                    onUploadAll = { onUploadAllParamsForDevice(session.id) },
                                    onApplyStreamRates = { ephysRate, cameraEnabled, adcEnabled ->
                                        onApplyStreamRatesForDevice(session.id, ephysRate, cameraEnabled, adcEnabled)
                                    },
                                    onQuickSetFs = { ephysRate ->
                                        onQuickSetFsForDevice(session.id, ephysRate)
                                    },
                                    onApplySystemProfile = { vbattThresholdRaw, audioRatio, cameraRatio ->
                                        onApplyAcquisitionSystemProfileForDevice(
                                            session.id,
                                            vbattThresholdRaw,
                                            audioRatio,
                                            cameraRatio,
                                        )
                                    },
                                    onSetTriggerWaveform = { enabled ->
                                        onSetTriggerWaveformForDevice(session.id, enabled)
                                    },
                                    onShowTriggerWaveformPath = { onShowTriggerWaveformPathForDevice(session.id) },
                                )
                            }
                        }

                        OnlineToolsPane.Camera -> {
                            item {
                                CameraMonitorCard(
                                    session = focusedSession,
                                    onPreviewSnapshot = onFocusedPreviewSnapshot,
                                    onSnapshot = onFocusedSnapshot,
                                    onSetCameraPreviewStreaming = onFocusedSetCameraPreviewStreaming,
                                )
                            }
                            if (cameraWallSessions.isNotEmpty()) {
                                item {
                                    FocusSessionGrid(
                                        sessions = cameraWallSessions,
                                        activeSessionId = null,
                                        helperText = null,
                                        subtitleForSession = { session ->
                                            when {
                                                session.cameraPreviewStreaming -> "LIVE"
                                                session.cameraPreviewPixels > 0 || session.cameraSnapshotPixels > 0 -> "FRAME"
                                                else -> compactSessionStateLabel(session)
                                            }
                                        },
                                        onActivate = { deviceId ->
                                            onActivateSession(deviceId)
                                            onRequestPreviewFrameForDevice(deviceId)
                                            onReadCameraParamsForDevice(deviceId)
                                        },
                                    )
                                }
                            }
                        }

                        OnlineToolsPane.Records -> {
                            item {
                                SignalRecordsShortcutCard(
                                    session = focusedSession,
                                    subtitle = "",
                                    onRefreshRecords = onFocusedRefreshRecords,
                                    onExportAllRecords = onFocusedExportAllRecords,
                                    onShowRecordExportPath = onFocusedShowRecordExportPath,
                                    onOpenRecords = onOpenRecords,
                                )
                            }
                            items(recordsWallSessions, key = { it.id }) { session ->
                                CompactRecordFleetCard(
                                    session = session,
                                    isActive = session.id == uiState.activeSessionId,
                                    onActivate = {
                                        onActivateSession(session.id)
                                        onRefreshRecordsForDevice(session.id)
                                    },
                                    onRefresh = { onRefreshRecordsForDevice(session.id) },
                                    onExportAll = { onExportAllRecordsForDevice(session.id) },
                                    onOpenRecords = {
                                        onActivateSession(session.id)
                                        onOpenRecords()
                                    },
                                    onShowExportPath = { onShowRecordExportPathForDevice(session.id) },
                                )
                            }
                        }
                    }
                } else {
                    item {
                        EmptyStateCard(compactUiCopy("Connect and focus a device to open the online tools and record index actions.", "Focus a connected device."))
                    }
                }
            }

            LivePane.Camera -> {
                val cameraWallSessions = cameraScopeSessions.filter { it.id != focusedSession?.id }
                item {
                    if (focusedSession != null) {
                        CameraMonitorCard(
                            session = focusedSession,
                            onPreviewSnapshot = onFocusedPreviewSnapshot,
                            onSnapshot = onFocusedSnapshot,
                            onSetCameraPreviewStreaming = onFocusedSetCameraPreviewStreaming,
                        )
                    } else if (plotSessions.isNotEmpty()) {
                        EmptyStateCard("Choose an active device to inspect the full camera panes.")
                    } else {
                        EmptyStateCard("Connect a device to request preview frames and snapshots.")
                    }
                }
                if (focusedSession != null) {
                    item {
                        CameraParamControlCard(
                            session = focusedSession,
                            scope = uiState.controlScope,
                            targetCount = targetCount,
                            canControlScope = canControlScope,
                            onReadCameraParams = onFocusedReadCameraParams,
                            onSetCameraParams = onFocusedSetCameraParams,
                        )
                    }
                }
                if (cameraWallSessions.isNotEmpty()) {
                    items(cameraWallSessions, key = { it.id }) { session ->
                        CameraFleetDeviceCard(
                            session = session,
                            isActive = session.id == uiState.activeSessionId,
                            onActivate = { onActivateSession(session.id) },
                            onPreviewSnapshot = { onRequestPreviewFrameForDevice(session.id) },
                            onSnapshot = { onRequestSnapshotForDevice(session.id) },
                            onSetCameraPreviewStreaming = { enabled ->
                                onSetCameraPreviewStreamingForDevice(session.id, enabled)
                            },
                            onReadCameraParams = { onReadCameraParamsForDevice(session.id) },
                            onSetCameraParams = { reg0, reg1 ->
                                onSetCameraParamsForDevice(session.id, reg0, reg1)
                            },
                        )
                    }
                }
            }

            LivePane.Status -> {
                val statusWallSessions = monitorSessions.filter { it.id != focusedSession?.id }
                when (statusPane) {
                    StatusPane.Health -> {
                        item {
                            if (focusedSession != null) {
                                StimMonitorCard(focusedSession)
                            } else {
                                EmptyStateCard("Connect a device to inspect stim, sync, and BLE link health.")
                            }
                        }

                        item {
                            if (focusedSession != null) {
                                SessionMonitorCard(
                                    session = focusedSession,
                                    onLinkAction = onFocusedLinkAction,
                                    onResync = onFocusedResync,
                                    onResyncNoRtc = onFocusedResyncNoRtc,
                                    onShowSyncLogPath = onFocusedShowSyncLogPath,
                                    onShowTriggerWaveformPath = onFocusedShowTriggerWaveformPath,
                                )
                            }
                        }
                        if (statusWallSessions.isNotEmpty()) {
                            items(statusWallSessions, key = { it.id }) { session ->
                                CompactSessionMonitorCard(
                                    session = session,
                                    isActive = session.id == uiState.activeSessionId,
                                    onActivate = { onActivateSession(session.id) },
                                    onLinkAction = {
                                        if (session.isConnected || session.isLinkingLike) {
                                            onDisconnectSession(session.id)
                                        } else {
                                            onConnectSession(session.id)
                                        }
                                    },
                                    onResync = { onResyncForDevice(session.id) },
                                    onResyncNoRtc = { onResyncNoRtcForDevice(session.id) },
                                    onShowSyncLogPath = { onShowSyncLogPathForDevice(session.id) },
                                    onShowTriggerWaveformPath = { onShowTriggerWaveformPathForDevice(session.id) },
                                )
                            }
                        }
                    }

                    StatusPane.Link -> {
                        item {
                            if (focusedSession != null) {
                                SessionLinkCard(
                                    session = focusedSession,
                                    onLinkAction = onFocusedLinkAction,
                                    onResync = onFocusedResync,
                                    onResyncNoRtc = onFocusedResyncNoRtc,
                                    onShowSyncLogPath = onFocusedShowSyncLogPath,
                                    onShowTriggerWaveformPath = onFocusedShowTriggerWaveformPath,
                                )
                            } else {
                                EmptyStateCard("Connect a device to inspect BLE link RSSI, loss, packet gaps, and transport activity.")
                            }
                        }
                        if (statusWallSessions.isNotEmpty()) {
                            items(statusWallSessions, key = { it.id }) { session ->
                                CompactSessionLinkCard(
                                    session = session,
                                    isActive = session.id == uiState.activeSessionId,
                                    onActivate = { onActivateSession(session.id) },
                                    onLinkAction = {
                                        if (session.isConnected || session.isLinkingLike) {
                                            onDisconnectSession(session.id)
                                        } else {
                                            onConnectSession(session.id)
                                        }
                                    },
                                    onResync = { onResyncForDevice(session.id) },
                                    onResyncNoRtc = { onResyncNoRtcForDevice(session.id) },
                                    onShowSyncLogPath = { onShowSyncLogPathForDevice(session.id) },
                                    onShowTriggerWaveformPath = { onShowTriggerWaveformPathForDevice(session.id) },
                                )
                            }
                        }
                    }

                    StatusPane.Activity -> {
                        item {
                            if (focusedSession != null) {
                                SessionActivityCard(
                                    session = focusedSession,
                                    onLinkAction = onFocusedLinkAction,
                                    onResync = onFocusedResync,
                                    onResyncNoRtc = onFocusedResyncNoRtc,
                                    onShowSyncLogPath = onFocusedShowSyncLogPath,
                                    onShowTriggerWaveformPath = onFocusedShowTriggerWaveformPath,
                                )
                            } else {
                                EmptyStateCard("Connect a device to inspect BLE activity, saved logs, and waveform capture files.")
                            }
                        }
                        if (statusWallSessions.isNotEmpty()) {
                            items(statusWallSessions, key = { it.id }) { session ->
                                CompactSessionActivityCard(
                                    session = session,
                                    isActive = session.id == uiState.activeSessionId,
                                    onActivate = { onActivateSession(session.id) },
                                    onLinkAction = {
                                        if (session.isConnected || session.isLinkingLike) {
                                            onDisconnectSession(session.id)
                                        } else {
                                            onConnectSession(session.id)
                                        }
                                    },
                                    onResync = { onResyncForDevice(session.id) },
                                    onResyncNoRtc = { onResyncNoRtcForDevice(session.id) },
                                    onShowSyncLogPath = { onShowSyncLogPathForDevice(session.id) },
                                    onShowTriggerWaveformPath = { onShowTriggerWaveformPathForDevice(session.id) },
                                )
                            }
                        }
                    }
                }
            }

            LivePane.Fleet -> {
                val fleetSessions = if (fleetPane == FleetPane.Health) monitorSessions else plotSessions
                if (shouldShowTargetSelectionCard(uiState)) {
                    item {
                        TargetSelectionCard(
                            sessions = uiState.connectedSessions,
                            selectedSessionIds = uiState.selectedSessionIds,
                            onToggleSelection = onToggleSessionSelection,
                            onSelectAll = onSelectAllConnectedSessions,
                            onClearSelection = onClearSelectedSessions,
                        )
                    }
                }
                if (fleetSessions.isNotEmpty()) {
                    items(fleetSessions, key = { it.id }) { session ->
                        ConnectedMiniCard(
                            session = session,
                            pane = fleetPane,
                            isActive = session.id == uiState.activeSessionId,
                            onFocus = { onFocusSession(session.id) },
                            onStartPreview = { onStartPreviewForDevice(session.id) },
                            onStopPreview = { onStopPreviewForDevice(session.id) },
                            onSetPreviewSelection = { selection ->
                                onSetPreviewSelectionForDevice(session.id, selection)
                            },
                            onStartRecording = { onStartRecordingForDevice(session.id) },
                            onStopRecording = { onStopRecordingForDevice(session.id) },
                            onForceStopRecording = { onForceStopRecordingForDevice(session.id) },
                            onPreviewFrame = { onRequestPreviewFrameForDevice(session.id) },
                            onSnapshot = { onRequestSnapshotForDevice(session.id) },
                            onSetCameraPreviewStreaming = { enabled ->
                                onSetCameraPreviewStreamingForDevice(session.id, enabled)
                            },
                            onResync = { onResyncForDevice(session.id) },
                            onResyncNoRtc = { onResyncNoRtcForDevice(session.id) },
                            onImpedance = { onRequestImpedanceForDevice(session.id) },
                            onShowSyncLogPath = { onShowSyncLogPathForDevice(session.id) },
                            onShowTriggerWaveformPath = { onShowTriggerWaveformPathForDevice(session.id) },
                            onLinkAction = {
                                if (session.isConnected || session.isLinkingLike) {
                                    onDisconnectSession(session.id)
                                } else {
                                    onConnectSession(session.id)
                                }
                            },
                        )
                    }
                } else if (uiState.connectedSessions.isNotEmpty()) {
                    item {
                        EmptyStateCard(
                            when (uiState.controlScope) {
                                ControlScope.ActiveDevice -> if (hasReconnectingActiveScope(uiState)) {
                                    "The active device is still selected but reconnecting. Wait for it to relink or switch scope."
                                } else {
                                    "Choose a device to continue."
                                }
                                ControlScope.SelectedDevices -> if (hasReconnectingSelectedScope(uiState)) {
                                    "The selected devices are reconnecting. Wait for one to relink or switch scope."
                                } else {
                                    "Select connected devices to show them here."
                                }
                                ControlScope.AllConnected -> "No connected devices are available."
                            }
                        )
                    }
                } else {
                    item {
                        EmptyStateCard("Connect devices to continue.")
                    }
                }
            }
        }
    }
}

@Composable
private fun PocketCameraHubCard(
    connectedSessions: List<DeviceSessionUiState>,
    scopeSessions: List<DeviceSessionUiState>,
    linkedScopeCount: Int,
    activeSession: DeviceSessionUiState?,
    activeSessionId: String?,
    canOpenOnline: Boolean,
    canOpenStatus: Boolean,
    canOpenConnected: Boolean,
    scope: ControlScope,
    selectedCount: Int,
    onScopeChange: (ControlScope) -> Unit,
    onActivate: (String) -> Unit,
    onOpenOnline: () -> Unit,
    onOpenStatus: () -> Unit,
    onOpenConnected: () -> Unit,
) {
    val liveCount = scopeSessions.count { it.cameraPreviewStreaming }
    val previewCacheCount = scopeSessions.count { it.cameraPreviewPixels > 0 }
    val snapshotCount = scopeSessions.count { it.cameraSnapshotPixels > 0 }
    val cachedFrameCount = scopeSessions.count { it.cameraPreviewPixels > 0 || it.cameraSnapshotPixels > 0 }
    val previewSignalCount = scopeSessions.count(::isPreviewingSession)
    val focusSessions = monitorFocusSessions(activeSession, scopeSessions, connectedSessions)
    val scopeOptions = buildCompactScopeOptions(
        scope = scope,
        selectedCount = selectedCount,
        connectedCount = connectedSessions.size,
    )
    val focusName = activeSession?.name ?: focusSessions.firstOrNull()?.name ?: "No scoped devices"
    val targetMetricValue = when {
        scopeSessions.size > linkedScopeCount -> "$linkedScopeCount/${scopeSessions.size}"
        else -> scopeSessions.size.toString()
    }
    val reconnectNote = cameraScopeReconnectNote(
        scope = scope,
        preservedScopeCount = scopeSessions.size,
        linkedScopeCount = linkedScopeCount,
        cachedFrameCount = cachedFrameCount,
        activeSessionName = activeSession?.name,
    )

    ControlCard(
        title = "Camera",
        subtitle = "",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (focusSessions.isNotEmpty()) {
                CompactFocusSessionSelector(
                    sessions = focusSessions,
                    activeSessionId = activeSessionId,
                    onActivate = onActivate,
                    compact = true,
                    modifier = Modifier.weight(1.6f),
                )
            } else {
                InfoPill("Focus", focusName, Modifier.weight(1.6f))
            }
            InfoPill("Signal", previewSignalCount.toString(), Modifier.weight(1f))
        }

        CompactHubMetricBand(
            metrics = listOf(
                "Devices" to targetMetricValue,
                "Live" to liveCount.toString(),
                "Prev" to previewCacheCount.toString(),
                "Snap" to snapshotCount.toString(),
                "Linked" to connectedSessions.size.toString(),
            ),
        )

        if (reconnectNote != null) {
            Text(
                reconnectNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (scopeOptions.size > 1) {
                CompactDropdownSelector(
                    currentLabel = scopeOptions.firstOrNull { it.first == scope }?.second ?: compactScopeSelectorLabel(scope),
                    options = scopeOptions.map { (targetScope, label, _) -> targetScope.name to label },
                    selectedOptionName = scope.name,
                    onSelectOption = { onScopeChange(ControlScope.valueOf(it)) },
                    optionEnabled = { optionName ->
                        scopeOptions.firstOrNull { it.first.name == optionName }?.third == true
                    },
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
            }
            if (canOpenOnline) {
                PreviewCompactToggleChip(
                    label = "Online",
                    selected = false,
                    onClick = onOpenOnline,
                    compact = true,
                )
            }
            if (canOpenStatus) {
                PreviewCompactToggleChip(
                    label = "Status",
                    selected = false,
                    onClick = onOpenStatus,
                    compact = true,
                )
            }
            if (canOpenConnected) {
                PreviewCompactToggleChip(
                    label = "Dev",
                    selected = false,
                    onClick = onOpenConnected,
                    compact = true,
                )
            }
        }

        if (SHOW_UI_DESCRIPTIONS) {
            Text(
                "Open remote camera monitoring on one device while keeping nearby routes back to signal, online helpers, diagnostics, records, and the link hub on the same phone page.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            )
        }

    }
}

@Composable
private fun PocketStatusHubCard(
    connectedSessions: List<DeviceSessionUiState>,
    scopeSessions: List<DeviceSessionUiState>,
    activeSession: DeviceSessionUiState?,
    activeSessionId: String?,
    canOpenOnline: Boolean,
    canOpenCamera: Boolean,
    canOpenConnected: Boolean,
    scope: ControlScope,
    selectedCount: Int,
    statusPane: StatusPane,
    onScopeChange: (ControlScope) -> Unit,
    onStatusPaneChange: (StatusPane) -> Unit,
    onActivate: (String) -> Unit,
    onOpenOnline: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenConnected: () -> Unit,
) {
    val attentionCount = scopeSessions.count {
        it.hostState == BleHostSessionState.Error || it.hostState == BleHostSessionState.Reconnecting
    }
    val previewCount = scopeSessions.count(::isPreviewingSession)
    val recordingCount = scopeSessions.count { it.isRecordingLike }
    val syncCount = scopeSessions.count { it.liveSync != null || it.lastSyncMetric != null }
    val linkCount = scopeSessions.count { it.bleLinkStats != null }
    val waveformFileCount = scopeSessions.count { it.triggerWaveformCapturePath.isNotBlank() }
    val focusSessions = monitorFocusSessions(activeSession, scopeSessions, connectedSessions)
    val scopeOptions = buildCompactScopeOptions(
        scope = scope,
        selectedCount = selectedCount,
        connectedCount = connectedSessions.size,
    )
    val focusName = activeSession?.name ?: focusSessions.firstOrNull()?.name ?: "No scoped devices"

    ControlCard(
        title = "Status",
        subtitle = "",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (focusSessions.isNotEmpty()) {
                CompactFocusSessionSelector(
                    sessions = focusSessions,
                    activeSessionId = activeSessionId,
                    onActivate = onActivate,
                    compact = true,
                    modifier = Modifier.weight(1.6f),
                )
            } else {
                InfoPill("Focus", focusName, Modifier.weight(1.6f))
            }
            InfoPill("Alert", attentionCount.toString(), Modifier.weight(1f))
            InfoPill("View", statusPane.label, Modifier.weight(1f))
        }

        CompactHubMetricBand(
            metrics = listOf(
                "Devices" to scopeSessions.size.toString(),
                "Sync" to syncCount.toString(),
                "Link" to linkCount.toString(),
                "Prev" to previewCount.toString(),
                "Rec" to recordingCount.toString(),
                "Files" to waveformFileCount.toString(),
                "Alert" to attentionCount.toString(),
            ),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (scopeOptions.size > 1) {
                CompactDropdownSelector(
                    currentLabel = scopeOptions.firstOrNull { it.first == scope }?.second ?: compactScopeSelectorLabel(scope),
                    options = scopeOptions.map { (targetScope, label, _) -> targetScope.name to label },
                    selectedOptionName = scope.name,
                    onSelectOption = { onScopeChange(ControlScope.valueOf(it)) },
                    optionEnabled = { optionName ->
                        scopeOptions.firstOrNull { it.first.name == optionName }?.third == true
                    },
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
            }
            CompactDropdownSelector(
                currentLabel = statusPane.label,
                options = StatusPane.entries.map { it.name to it.label },
                selectedOptionName = statusPane.name,
                onSelectOption = { onStatusPaneChange(StatusPane.valueOf(it)) },
                compact = true,
                modifier = Modifier.weight(if (scopeOptions.size > 1) 1f else 1.35f),
            )
            if (canOpenOnline) {
                PreviewCompactToggleChip(
                    label = "Online",
                    selected = false,
                    onClick = onOpenOnline,
                    compact = true,
                )
            }
            if (canOpenCamera) {
                PreviewCompactToggleChip(
                    label = "Cam",
                    selected = false,
                    onClick = onOpenCamera,
                    compact = true,
                )
            }
            if (canOpenConnected) {
                PreviewCompactToggleChip(
                    label = "Dev",
                    selected = false,
                    onClick = onOpenConnected,
                    compact = true,
                )
            }
        }

        if (SHOW_UI_DESCRIPTIONS) {
            Text(
                "Jump between signal, online helpers, camera, records, and diagnostics without leaving the live workflow, then switch the focused device directly from this status hub.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            )
        }

    }
}

@Composable
private fun PocketSignalHubCard(
    connectedSessions: List<DeviceSessionUiState>,
    scopeSessions: List<DeviceSessionUiState>,
    activeSession: DeviceSessionUiState?,
    activeSessionId: String?,
    scope: ControlScope,
    selectedCount: Int,
    pane: LivePane,
    signalViewMode: SignalViewMode,
    onScopeChange: (ControlScope) -> Unit,
    onPaneChange: (LivePane) -> Unit,
    onSignalViewModeChange: (SignalViewMode) -> Unit,
    onActivate: (String) -> Unit,
    onOpenDevices: () -> Unit,
    onOpenClosedLoop: () -> Unit,
    onOpenRecords: () -> Unit,
) {
    val previewCount = scopeSessions.count(::isPreviewingSession)
    val recordingCount = scopeSessions.count { it.isRecordingLike }
    val focusSessions = monitorFocusSessions(activeSession, scopeSessions, connectedSessions)
    val hasPreservedScope = focusSessions.isNotEmpty()
    val focusName = activeSession?.name ?: focusSessions.firstOrNull()?.name ?: "No scoped devices"
    ControlCard(
        title = "Preview",
        subtitle = "",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (focusSessions.isNotEmpty()) {
                CompactFocusSessionSelector(
                    sessions = focusSessions,
                    activeSessionId = activeSessionId,
                    onActivate = onActivate,
                    compact = true,
                    modifier = Modifier.weight(1.6f),
                )
            } else {
                InfoPill("Focus", focusName, Modifier.weight(1.6f))
            }
            InfoPill(
                "Scope",
                when (scope) {
                    ControlScope.ActiveDevice -> "Active"
                    ControlScope.SelectedDevices -> "Selected"
                    ControlScope.AllConnected -> "All"
                },
                Modifier.weight(1f),
            )
            InfoPill("Mode", signalViewMode.label, Modifier.weight(1f))
        }

        CompactHubMetricBand(
            metrics = listOf(
                "Targets" to scopeSessions.size.toString(),
                "Preview" to previewCount.toString(),
                "Rec" to recordingCount.toString(),
            ),
        )

        PocketButtonGrid(
            options = listOf(
                PocketButtonSpec(
                    label = "Active",
                    onClick = { onScopeChange(ControlScope.ActiveDevice) },
                    selected = scope == ControlScope.ActiveDevice,
                ),
                PocketButtonSpec(
                    label = "Selected $selectedCount",
                    onClick = { onScopeChange(ControlScope.SelectedDevices) },
                    selected = scope == ControlScope.SelectedDevices,
                ),
                PocketButtonSpec(
                    label = "All ${connectedSessions.size}",
                    onClick = { onScopeChange(ControlScope.AllConnected) },
                    selected = scope == ControlScope.AllConnected,
                ),
            ),
        )

        PocketButtonGrid(
            options = LivePane.entries.map { entry ->
                PocketButtonSpec(
                    label = entry.label,
                    onClick = { onPaneChange(entry) },
                    selected = pane == entry,
                )
            },
        )

        PocketButtonGrid(
            options = listOf(
                PocketButtonSpec("Closed-Loop", onOpenClosedLoop, enabled = connectedSessions.isNotEmpty()),
                PocketButtonSpec("Records", onOpenRecords, enabled = hasPreservedScope),
                PocketButtonSpec("Devices", onOpenDevices),
            ),
        )

        if (SHOW_UI_DESCRIPTIONS) {
            Text(
                "Online, Camera, and Status already have dedicated live-page routes now. Keep the remaining cross-workflow jumps compact here so the signal hub stays focused on preview, recording, and scope.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            )
        }

        if (scopeSessions.size > 1) {
            PocketButtonGrid(
                options = SignalViewMode.entries.map { entry ->
                    PocketButtonSpec(
                        label = entry.label,
                        onClick = { onSignalViewModeChange(entry) },
                        selected = signalViewMode == entry,
                    )
                },
                columns = 2,
            )
        }

    }
}

private data class PocketButtonSpec(
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val selected: Boolean = false,
)

@Composable
private fun PocketButtonGrid(
    options: List<PocketButtonSpec>,
    columns: Int = 3,
) {
    options.chunked(columns).forEach { rowOptions ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            rowOptions.forEach { spec ->
                PocketRouteButton(
                    label = spec.label,
                    onClick = spec.onClick,
                    enabled = spec.enabled,
                    selected = spec.selected,
                    modifier = Modifier.weight(1f),
                )
            }
            repeat(columns - rowOptions.size) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SessionOrderGrid(
    title: String,
    sessions: List<DeviceSessionUiState>,
    activeSessionId: String?,
    helperText: String,
    onSessionClick: (String) -> Unit,
) {
    if (sessions.isEmpty()) {
        return
    }

    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
    )
    FocusSessionGrid(
        sessions = sessions,
        activeSessionId = activeSessionId,
        helperText = helperText,
        subtitleForSession = { session ->
            "Remove | ${compactSessionStateLabel(session)}"
        },
        onActivate = onSessionClick,
    )
}

@Composable
private fun FocusSessionGrid(
    sessions: List<DeviceSessionUiState>,
    activeSessionId: String?,
    subtitleForSession: (DeviceSessionUiState) -> String,
    onActivate: (String) -> Unit,
    helperText: String? = null,
) {
    if (SHOW_UI_DESCRIPTIONS && !helperText.isNullOrBlank()) {
        Text(
            helperText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
        )
    }

    sessions.chunked(2).forEach { rowSessions ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            rowSessions.forEach { session ->
                FocusSessionTile(
                    session = session,
                    subtitle = subtitleForSession(session),
                    selected = session.id == activeSessionId,
                    onClick = { onActivate(session.id) },
                    modifier = Modifier.weight(1f),
                )
            }
            if (rowSessions.size == 1) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

private fun compactSessionStateLabel(session: DeviceSessionUiState): String {
    return when {
        session.hostState == BleHostSessionState.Error -> "ERR"
        session.isRecordingLike -> "REC"
        session.cameraPreviewStreaming -> "CAM"
        isPreviewingSession(session) -> "LIVE"
        session.awaitingLiveSync && session.isConnected -> "Syncing..."
        session.statusText.isNotBlank() -> session.statusText
        else -> "Ready"
    }
}

private fun monitorFocusSessions(
    activeSession: DeviceSessionUiState?,
    scopeSessions: List<DeviceSessionUiState>,
    connectedSessions: List<DeviceSessionUiState>,
): List<DeviceSessionUiState> {
    val ordered = linkedMapOf<String, DeviceSessionUiState>()
    listOfNotNull(activeSession).forEach { session ->
        ordered[session.id] = session
    }
    scopeSessions.forEach { session ->
        ordered.putIfAbsent(session.id, session)
    }
    connectedSessions.forEach { session ->
        ordered.putIfAbsent(session.id, session)
    }
    return ordered.values.toList()
}

@Composable
private fun CompactFocusSessionSelector(
    sessions: List<DeviceSessionUiState>,
    activeSessionId: String?,
    onActivate: (String) -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (sessions.isEmpty()) {
        return
    }

    val activeSession = sessions.firstOrNull { it.id == activeSessionId } ?: sessions.first()
    CompactDropdownSelector(
        currentLabel = compactDeviceUiLabel(activeSession.name, activeSession.address),
        options = sessions.map { session ->
            session.id to compactDeviceUiLabel(session.name, session.address)
        },
        selectedOptionName = activeSession.id,
        onSelectOption = onActivate,
        compact = compact,
        modifier = modifier,
    )
}

@Composable
private fun FocusSessionTile(
    session: DeviceSessionUiState,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.46f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = if (selected) 0.2f else 0.1f),
                    shape = RoundedCornerShape(20.dp),
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                session.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.92f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LiveCommandDeckCard(
    targetSessions: List<DeviceSessionUiState>,
    onStartPreview: () -> Unit,
    onStopPreview: () -> Unit,
) {
    val actionSummary = buildLiveActionSummary(targetSessions)
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledTonalButton(
                    onClick = onStartPreview,
                    modifier = Modifier.weight(1f),
                    enabled = actionSummary.canStartPreview,
                ) {
                    Text("Start")
                }
                OutlinedButton(
                    onClick = onStopPreview,
                    modifier = Modifier.weight(1f),
                    enabled = actionSummary.canStopPreview,
                ) {
                    Text("Stop")
                }
            }
        }
    }
}

@Composable
private fun DashboardMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(22.dp),
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                label.uppercase(Locale.US),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.88f),
            )
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun DeviceParameterScreen(
    modifier: Modifier = Modifier,
    uiState: WildUiState,
    launchSectionName: String,
    launchClosedLoopPaneName: String,
    launchSystemPaneName: String,
    launchRequestToken: Int,
    onBackToDevices: () -> Unit,
    onOpenPreview: () -> Unit,
    onActivateSession: (String) -> Unit,
    onScopeChange: (ControlScope) -> Unit,
    onToggleSessionSelection: (String) -> Unit,
    onSelectAllConnectedSessions: () -> Unit,
    onClearSelectedSessions: () -> Unit,
    onLinkAction: () -> Unit,
    onResync: () -> Unit,
    onReadParams: () -> Unit,
    onReadDsp: () -> Unit,
    onReadAllParams: () -> Unit,
    onApplyStreamRates: (Int, Boolean, Boolean) -> Unit,
    onQuickSetFs: (Int) -> Unit,
    onApplyAcquisitionSystemProfileToSystem: (Int, Int, Int) -> Unit,
    onSnapshot: () -> Unit,
    onPreviewSnapshot: () -> Unit,
    onReadCameraParams: () -> Unit,
    onSetCameraParams: (Int, Int) -> Unit,
    onSetStimEnabled: (Boolean) -> Unit,
    onSetTriggerGain: (Int, Float) -> Unit,
    onSetStimIntensity: (Int, Float) -> Unit,
    onSetTriggerThreshold: (Int, Float) -> Unit,
    onForceTrigger: (Int) -> Unit,
    onSetStimParams: (Int, Float, Float, Float, Float, Int) -> Unit,
    onSetDspLiveParams: (Int, Int, Int, Int, List<Int>) -> Unit,
    onApplyClosedLoopProfileToSystem: (Int, Int, Int, Int, Int, List<Float>, List<Float>, List<Int>) -> Unit,
    onApplyClosedLoopProfileToAll: (Int, Int, Int, Int, Int, List<Float>, List<Float>, List<Int>) -> Unit,
    onImpedance: () -> Unit,
    onExportImpedance: () -> Unit,
    onShowImpedanceExportPath: () -> Unit,
    onStartAutoImpedance: (Int) -> Unit,
    onStopAutoImpedance: () -> Unit,
    onLed: (Boolean) -> Unit,
    onGpio0: (GpioMode) -> Unit,
    onGpio1: (GpioMode) -> Unit,
    onTriggerWaveform: (Boolean) -> Unit,
    onShowTriggerWaveformPath: () -> Unit,
    onSleep: () -> Unit,
    onReset: () -> Unit,
    onBootloader: () -> Unit,
    onFirmwareUpdate: () -> Unit,
    onPickBleOtaPackage: () -> Unit,
    onStageBleOta: () -> Unit,
    onInstallStagedBleOta: () -> Unit,
    onRequestAiModuleInstall: (Int) -> Unit,
    onRefreshAiStatus: () -> Unit,
    onSelectAiModule: (Int?) -> Unit,
    onSetAiRuntimeEnabled: (Boolean) -> Unit,
    onUploadSystemParams: () -> Unit,
    onUploadDspParams: (Int) -> Unit,
    onUploadAllParams: () -> Unit,
    onRole: (Int, String) -> Unit,
    onRefreshSignalAnalysis: () -> Unit,
    onSetSpikeDetectorConfig: (SpikeDetectorConfigUiState) -> Unit,
    onSetSpectrumConfig: (SpectrumConfigUiState) -> Unit,
    onSetSchedulerEnabled: (Boolean) -> Unit,
    onSetSchedulerRule: (SchedulerRuleUiState) -> Unit,
    onSetSchedulerRuleEnabled: (Int, Boolean) -> Unit,
    onClearSchedulerRule: (Int) -> Unit,
    onClearScheduler: () -> Unit,
    onReadScheduler: () -> Unit,
    onSaveSchedulerProfile: (Int) -> Unit,
) {
    val activeSession = uiState.activeSession
    val quickScopeTargets = scopedSessions(uiState)
    val quickScopeSummary = buildScopeStatusSummary(quickScopeTargets)
    var pendingLaunchPayloadSessionId by rememberSaveable { mutableStateOf<String?>(null) }
    val connectedControlSessions = uiState.connectedSessions.sortedWith(
        compareByDescending<DeviceSessionUiState> { it.id == uiState.activeSessionId }
            .thenBy { it.name.lowercase(Locale.US) },
    )
    val controlHeaderSessions = remember(activeSession, connectedControlSessions, uiState.activeSessionId) {
        buildList {
            activeSession?.let { session ->
                add(session)
            }
            connectedControlSessions
                .filterNot { session -> session.id == activeSession?.id }
                .forEach(::add)
        }
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val session = activeSession ?: run {
            EmptyStateCard("Tap a device on Devices to open its parameter controls.")
            return@Column
        }
        val controlLaunchCorePayloadReady = sessionHasControlLaunchCorePayload(session)
        val controlLaunchCameraPayloadReady = sessionHasCameraControlPayload(session)

        LaunchedEffect(launchRequestToken, session.id) {
            if (launchRequestToken > 0) {
                pendingLaunchPayloadSessionId = session.id
            }
        }
        LaunchedEffect(
            pendingLaunchPayloadSessionId,
            session.id,
            session.isConnected,
            controlLaunchCorePayloadReady,
            controlLaunchCameraPayloadReady,
        ) {
            if (pendingLaunchPayloadSessionId != session.id) {
                return@LaunchedEffect
            }
            if (!session.isConnected) {
                return@LaunchedEffect
            }
            // The BLE bootstrap reads system, DSP1, and DSP2 in order.  Do not
            // issue another 0x90/0x91/0x92 batch while those responses are in
            // flight; that can interleave the 512-byte payloads on CE64.
            if (!controlLaunchCorePayloadReady) {
                return@LaunchedEffect
            }
            if (shouldReadCameraDuringControlLaunch(session)) {
                onReadCameraParams()
            }
            pendingLaunchPayloadSessionId = null
        }

        DeviceControlTopStrip(
            session = session,
            sessions = controlHeaderSessions,
            activeSessionId = uiState.activeSessionId,
            scope = uiState.controlScope,
            scopeSummary = quickScopeSummary,
            selectedCount = uiState.selectedConnectedSessions.size,
            selectedSessionIds = uiState.selectedSessionIds,
            onBackToDevices = onBackToDevices,
            onActivateSession = onActivateSession,
            onScopeChange = onScopeChange,
            onToggleSessionSelection = onToggleSessionSelection,
            onSelectAllConnectedSessions = onSelectAllConnectedSessions,
            onClearSelectedSessions = onClearSelectedSessions,
            onLinkAction = onLinkAction,
            onResync = onResync,
            onOpenPreview = onOpenPreview,
        )

        if (!session.isConnected) {
            PendingDeviceControlCard(
                session = session,
                onLinkAction = onLinkAction,
            )
        }

        ControlScreen(
            modifier = Modifier.weight(1f),
            uiState = uiState,
            singleDeviceMode = true,
            launchSectionName = launchSectionName,
            launchClosedLoopPaneName = launchClosedLoopPaneName,
            launchSystemPaneName = launchSystemPaneName,
            launchRequestToken = launchRequestToken,
            onActivateSession = onActivateSession,
            onScopeChange = onScopeChange,
            onToggleSessionSelection = onToggleSessionSelection,
            onSelectAllConnectedSessions = onSelectAllConnectedSessions,
            onClearSelectedSessions = onClearSelectedSessions,
            onResync = onResync,
            onReadParams = onReadParams,
            onReadDsp = onReadDsp,
            onReadAllParams = onReadAllParams,
            onApplyStreamRates = onApplyStreamRates,
            onQuickSetFs = onQuickSetFs,
            onApplyAcquisitionSystemProfileToSystem = onApplyAcquisitionSystemProfileToSystem,
            onSnapshot = onSnapshot,
            onPreviewSnapshot = onPreviewSnapshot,
            onReadCameraParams = onReadCameraParams,
            onSetCameraParams = onSetCameraParams,
            onSetStimEnabled = onSetStimEnabled,
            onSetTriggerGain = onSetTriggerGain,
            onSetStimIntensity = onSetStimIntensity,
            onSetTriggerThreshold = onSetTriggerThreshold,
            onForceTrigger = onForceTrigger,
            onSetStimParams = onSetStimParams,
            onSetDspLiveParams = onSetDspLiveParams,
            onApplyClosedLoopProfileToSystem = onApplyClosedLoopProfileToSystem,
            onApplyClosedLoopProfileToAll = onApplyClosedLoopProfileToAll,
            onImpedance = onImpedance,
            onExportImpedance = onExportImpedance,
            onShowImpedanceExportPath = onShowImpedanceExportPath,
            onStartAutoImpedance = onStartAutoImpedance,
            onStopAutoImpedance = onStopAutoImpedance,
            onLed = onLed,
            onGpio0 = onGpio0,
            onGpio1 = onGpio1,
            onTriggerWaveform = onTriggerWaveform,
            onShowTriggerWaveformPath = onShowTriggerWaveformPath,
            onSleep = onSleep,
            onReset = onReset,
            onBootloader = onBootloader,
            onFirmwareUpdate = onFirmwareUpdate,
            onPickBleOtaPackage = onPickBleOtaPackage,
            onStageBleOta = onStageBleOta,
            onInstallStagedBleOta = onInstallStagedBleOta,
            onRequestAiModuleInstall = onRequestAiModuleInstall,
            onRefreshAiStatus = onRefreshAiStatus,
            onSelectAiModule = onSelectAiModule,
            onSetAiRuntimeEnabled = onSetAiRuntimeEnabled,
            onUploadSystemParams = onUploadSystemParams,
            onUploadDspParams = onUploadDspParams,
            onUploadAllParams = onUploadAllParams,
            onRole = onRole,
            onRefreshSignalAnalysis = onRefreshSignalAnalysis,
            onSetSpikeDetectorConfig = onSetSpikeDetectorConfig,
            onSetSpectrumConfig = onSetSpectrumConfig,
            onSetSchedulerEnabled = onSetSchedulerEnabled,
            onSetSchedulerRule = onSetSchedulerRule,
            onSetSchedulerRuleEnabled = onSetSchedulerRuleEnabled,
            onClearSchedulerRule = onClearSchedulerRule,
            onClearScheduler = onClearScheduler,
                            onReadScheduler = onReadScheduler,
                            onSaveSchedulerProfile = onSaveSchedulerProfile,
        )
    }
}

@Composable
private fun PendingDeviceControlCard(
    session: DeviceSessionUiState,
    onLinkAction: () -> Unit,
) {
    val batteryLabel = formatBatteryLevelLabel(preferredAdvertisementBattery(session))
    val latestLine = session.lastFailure.ifBlank {
        session.recentEvents.lastOrNull()?.summary ?: session.lastMessage.ifBlank { "Waiting for BLE link." }
    }

    ControlCard(
        title = "Connect",
        subtitle = "",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoPill("State", compactSessionStateLabel(session), Modifier.weight(1f))
            InfoPill("RSSI", session.rssi?.let { "$it dBm" } ?: "--", Modifier.weight(1f))
            InfoPill("Batt", batteryLabel.ifBlank { "--" }, Modifier.weight(1f))
        }

        Text(
            latestLine,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        FilledTonalButton(
            onClick = onLinkAction,
            enabled = sessionLinkActionEnabled(session),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(sessionLinkActionLabel(session))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceControlTopStrip(
    session: DeviceSessionUiState,
    sessions: List<DeviceSessionUiState>,
    activeSessionId: String?,
    scope: ControlScope,
    scopeSummary: ScopeStatusSummary,
    selectedCount: Int,
    selectedSessionIds: Set<String>,
    onBackToDevices: () -> Unit,
    onActivateSession: (String) -> Unit,
    onScopeChange: (ControlScope) -> Unit,
    onToggleSessionSelection: (String) -> Unit,
    onSelectAllConnectedSessions: () -> Unit,
    onClearSelectedSessions: () -> Unit,
    onLinkAction: () -> Unit,
    onResync: () -> Unit,
    onOpenPreview: () -> Unit,
) {
    val showActiveControls = session.isConnected
    var showTargetPicker by rememberSaveable { mutableStateOf(false) }
    val activeSyncLine = when {
        session.awaitingLiveSync || session.hostState == BleHostSessionState.Syncing ->
            formatSyncProgressTelemetryAscii(session.lastSyncMetric, session.liveSync)
        else -> formatSyncTightLineAscii(session.lastSyncMetric, session.liveSync)
    }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedIconButton(onClick = onBackToDevices) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back to devices",
                    )
                }
                ControlDeviceSelectorChip(
                    sessions = sessions,
                    activeSessionId = activeSessionId,
                    onActivate = onActivateSession,
                    modifier = Modifier.weight(1f),
                )
                if (showActiveControls) {
                    PreviewTransportButton(
                        icon = Icons.Outlined.Sync,
                        contentDescription = "Sync",
                        enabled = scopeSummary.targetCount > 0,
                        filled = false,
                        label = "Sync",
                        buttonSize = 48.dp,
                        onClick = onResync,
                    )
                    PreviewTransportButton(
                        icon = Icons.AutoMirrored.Outlined.ShowChart,
                        contentDescription = "Open preview",
                        enabled = scopeSummary.targetCount > 0,
                        filled = false,
                        label = "Preview",
                        buttonSize = 48.dp,
                        onClick = onOpenPreview,
                    )
                }
            }

            if (!showActiveControls) {
                return@Column
            }

            Text(
                text = listOfNotNull(
                    compactSessionStateLabel(session).takeIf { it.isNotBlank() },
                    activeSyncLine?.takeIf { it.isNotBlank() },
                ).joinToString("  |  ").ifBlank { "No active device" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (session.configurationPendingApply) {
                Text(
                    text = "Settings pending — stop recording, then read System, DSP1 and DSP2 to confirm.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onLinkAction,
                    modifier = Modifier.heightIn(min = 48.dp),
                    enabled = sessionLinkActionEnabled(session),
                ) {
                    Text("Disconnect")
                }
                if (sessions.size > 1) {
                    FilterChip(
                        selected = scope == ControlScope.ActiveDevice,
                        onClick = { onScopeChange(ControlScope.ActiveDevice) },
                        label = { Text("This device") },
                    )
                    FilterChip(
                        selected = scope == ControlScope.SelectedDevices,
                        onClick = {
                            onScopeChange(ControlScope.SelectedDevices)
                            showTargetPicker = true
                        },
                        label = {
                            Text(
                                if (selectedCount > 0) {
                                    "Selected $selectedCount"
                                } else {
                                    "Selected"
                                }
                            )
                        },
                    )
                    FilterChip(
                        selected = scope == ControlScope.AllConnected,
                        onClick = { onScopeChange(ControlScope.AllConnected) },
                        label = { Text("All ${sessions.size}") },
                    )
                }
            }
        }
    }

    if (showTargetPicker) {
        Dialog(onDismissRequest = { showTargetPicker = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.padding(20.dp),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Choose control devices", style = MaterialTheme.typography.titleMedium)
                    TargetSelectionCard(
                        sessions = sessions,
                        selectedSessionIds = selectedSessionIds,
                        onToggleSelection = onToggleSessionSelection,
                        onSelectAll = onSelectAllConnectedSessions,
                        onClearSelection = onClearSelectedSessions,
                    )
                    TextButton(
                        onClick = { showTargetPicker = false },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text("Done")
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlDeviceSelectorChip(
    sessions: List<DeviceSessionUiState>,
    activeSessionId: String?,
    onActivate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (sessions.isEmpty()) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
            modifier = modifier,
        ) {
            Text(
                text = "No device",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
        }
        return
    }

    val activeSession = sessions.firstOrNull { it.id == activeSessionId } ?: sessions.first()
    var expanded by remember(activeSession.id, sessions.size) { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)

    Box(modifier = modifier) {
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .border(
                    width = 1.dp,
                    color = Color(activeSession.traceColorArgb).copy(alpha = 0.34f),
                    shape = shape,
                )
                .clickable(enabled = sessions.size > 1) { expanded = true },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .background(Color(activeSession.traceColorArgb), CircleShape)
                )
                Text(
                    text = compactDeviceUiLabel(activeSession.name, activeSession.address),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (sessions.size > 1) {
                    Text(
                        text = sessions.size.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                    )
                    Icon(
                        imageVector = Icons.Outlined.ArrowDropDown,
                        contentDescription = "Select control device",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        DropdownMenu(
            expanded = expanded && sessions.size > 1,
            onDismissRequest = { expanded = false },
        ) {
            sessions.forEach { candidate ->
                DropdownMenuItem(
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color(candidate.traceColorArgb), CircleShape)
                            )
                            Column {
                                Text(
                                    text = compactDeviceUiLabel(candidate.name, candidate.address),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (candidate.id == activeSession.id) {
                                        Color(candidate.traceColorArgb)
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                                Text(
                                    text = sessionSelectorTelemetryLabel(candidate),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        onActivate(candidate.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun QuickDeviceControlCard(
    session: DeviceSessionUiState,
    scope: ControlScope,
    scopeSummary: ScopeStatusSummary,
    selectedCount: Int,
    actionSummary: LiveActionSummary,
    onResync: () -> Unit,
    onOpenPreview: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
) {
    val scopeValue = when (scope) {
        ControlScope.ActiveDevice -> "One"
        ControlScope.SelectedDevices -> if (selectedCount > 0) selectedCount.toString() else "0"
        ControlScope.AllConnected -> scopeSummary.targetCount.toString()
    }
    val liveValue = when {
        scopeSummary.targetCount <= 0 -> "Off"
        scopeSummary.previewingCount <= 0 -> "Off"
        else -> scopedCountLabel(scopeSummary.previewingCount, scopeSummary.targetCount)
    }
    val recordValue = when {
        scopeSummary.targetCount <= 0 -> "Off"
        actionSummary.recordingStopPendingCount > 0 && actionSummary.recordingActiveCount <= 0 ->
            "Pending ${actionSummary.recordingStopPendingCount}"
        actionSummary.recordingStopPendingCount > 0 ->
            "Retry ${actionSummary.recordingStopPendingCount}"
        scopeSummary.targetCount == 1 && session.isRecordingLike -> formatSeconds(session.recordingSeconds)
        scopeSummary.recordingCount <= 0 -> "Off"
        else -> scopedCountLabel(scopeSummary.recordingCount, scopeSummary.targetCount)
    }
    ControlCard(
        title = "",
        subtitle = "",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoPill("Scope", scopeValue, Modifier.weight(1f))
            InfoPill("Live", liveValue, Modifier.weight(1f))
            InfoPill(
                "Rec",
                recordValue,
                Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PreviewTransportButton(
                icon = Icons.Outlined.Sync,
                contentDescription = "Sync",
                enabled = scopeSummary.targetCount > 0,
                filled = false,
                label = "Sync",
                buttonSize = 42.dp,
                onClick = onResync,
            )
            PreviewTransportButton(
                icon = Icons.AutoMirrored.Outlined.ShowChart,
                contentDescription = "Open preview",
                enabled = scopeSummary.targetCount > 0,
                filled = false,
                label = "Preview",
                buttonSize = 42.dp,
                onClick = onOpenPreview,
            )
            PreviewTransportButton(
                icon = Icons.Outlined.FiberManualRecord,
                contentDescription = "Start recording",
                enabled = actionSummary.canStartRecording,
                filled = true,
                label = "Record",
                tint = Color(0xFFD64545),
                buttonSize = 42.dp,
                onClick = onStartRecording,
            )
            PreviewTransportButton(
                icon = Icons.Outlined.Stop,
                contentDescription = if (actionSummary.hasPendingRecordingStop) {
                    "Retry pending recording stop"
                } else {
                    "Stop recording"
                },
                enabled = actionSummary.canStopRecording,
                filled = false,
                label = "Stop rec",
                tint = Color(0xFFD64545),
                buttonSize = 42.dp,
                onClick = onStopRecording,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompactControlScopeCard(
    scope: ControlScope,
    connectedCount: Int,
    selectedCount: Int,
    onScopeChange: (ControlScope) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
) {
    ControlCard(
        title = "",
        subtitle = "",
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = scope == ControlScope.ActiveDevice,
                onClick = { onScopeChange(ControlScope.ActiveDevice) },
                label = { Text("One") },
            )
            FilterChip(
                selected = scope == ControlScope.SelectedDevices,
                onClick = { onScopeChange(ControlScope.SelectedDevices) },
                label = {
                    Text(
                        if (selectedCount > 0) {
                            "Selected $selectedCount"
                        } else {
                            "Selected"
                        }
                    )
                },
            )
            FilterChip(
                selected = scope == ControlScope.AllConnected,
                onClick = { onScopeChange(ControlScope.AllConnected) },
                label = { Text("All $connectedCount") },
            )
            AssistChip(
                onClick = onSelectAll,
                enabled = connectedCount > 0 && selectedCount < connectedCount,
                label = { Text("Select All") },
            )
            AssistChip(
                onClick = onClearSelection,
                enabled = selectedCount > 0,
                label = { Text("Clear") },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ControlScreen(
    modifier: Modifier = Modifier,
    uiState: WildUiState,
    singleDeviceMode: Boolean = false,
    launchSectionName: String,
    launchClosedLoopPaneName: String,
    launchSystemPaneName: String,
    launchRequestToken: Int,
    onActivateSession: (String) -> Unit,
    onScopeChange: (ControlScope) -> Unit,
    onToggleSessionSelection: (String) -> Unit,
    onSelectAllConnectedSessions: () -> Unit,
    onClearSelectedSessions: () -> Unit,
    onResync: () -> Unit,
    onReadParams: () -> Unit,
    onReadDsp: () -> Unit,
    onReadAllParams: () -> Unit,
    onApplyStreamRates: (Int, Boolean, Boolean) -> Unit,
    onQuickSetFs: (Int) -> Unit,
    onApplyAcquisitionSystemProfileToSystem: (Int, Int, Int) -> Unit,
    onSnapshot: () -> Unit,
    onPreviewSnapshot: () -> Unit,
    onReadCameraParams: () -> Unit,
    onSetCameraParams: (Int, Int) -> Unit,
    onSetStimEnabled: (Boolean) -> Unit,
    onSetTriggerGain: (Int, Float) -> Unit,
    onSetStimIntensity: (Int, Float) -> Unit,
    onSetTriggerThreshold: (Int, Float) -> Unit,
    onForceTrigger: (Int) -> Unit,
    onSetStimParams: (Int, Float, Float, Float, Float, Int) -> Unit,
    onSetDspLiveParams: (Int, Int, Int, Int, List<Int>) -> Unit,
    onApplyClosedLoopProfileToSystem: (Int, Int, Int, Int, Int, List<Float>, List<Float>, List<Int>) -> Unit,
    onApplyClosedLoopProfileToAll: (Int, Int, Int, Int, Int, List<Float>, List<Float>, List<Int>) -> Unit,
    onImpedance: () -> Unit,
    onExportImpedance: () -> Unit,
    onShowImpedanceExportPath: () -> Unit,
    onStartAutoImpedance: (Int) -> Unit,
    onStopAutoImpedance: () -> Unit,
    onLed: (Boolean) -> Unit,
    onGpio0: (GpioMode) -> Unit,
    onGpio1: (GpioMode) -> Unit,
    onTriggerWaveform: (Boolean) -> Unit,
    onShowTriggerWaveformPath: () -> Unit,
    onSleep: () -> Unit,
    onReset: () -> Unit,
    onBootloader: () -> Unit,
    onFirmwareUpdate: () -> Unit,
    onPickBleOtaPackage: () -> Unit,
    onStageBleOta: () -> Unit,
    onInstallStagedBleOta: () -> Unit,
    onRequestAiModuleInstall: (Int) -> Unit,
    onRefreshAiStatus: () -> Unit,
    onSelectAiModule: (Int?) -> Unit,
    onSetAiRuntimeEnabled: (Boolean) -> Unit,
    onUploadSystemParams: () -> Unit,
    onUploadDspParams: (Int) -> Unit,
    onUploadAllParams: () -> Unit,
    onRole: (Int, String) -> Unit,
    onRefreshSignalAnalysis: () -> Unit,
    onSetSpikeDetectorConfig: (SpikeDetectorConfigUiState) -> Unit,
    onSetSpectrumConfig: (SpectrumConfigUiState) -> Unit,
    onSetSchedulerEnabled: (Boolean) -> Unit,
    onSetSchedulerRule: (SchedulerRuleUiState) -> Unit,
    onSetSchedulerRuleEnabled: (Int, Boolean) -> Unit,
    onClearSchedulerRule: (Int) -> Unit,
    onClearScheduler: () -> Unit,
    onReadScheduler: () -> Unit,
    onSaveSchedulerProfile: (Int) -> Unit,
) {
    val activeSession = uiState.activeSession
    // Keep the active device in the compact selector even after a link drops.
    // This preserves the user's context instead of switching the control page to
    // another device while they are working through a configuration.
    val controlContextSessions = buildList {
        activeSession?.let(::add)
        uiState.connectedSessions
            .filterNot { it.id == activeSession?.id }
            .forEach(::add)
    }
    val canControlScope = when (uiState.controlScope) {
        ControlScope.ActiveDevice -> activeSession?.isConnected == true
        ControlScope.SelectedDevices -> uiState.selectedConnectedSessions.isNotEmpty()
        ControlScope.AllConnected -> uiState.connectedSessions.isNotEmpty()
    }
    val canControlActive = activeSession?.isConnected == true
    val scopeTargets = scopedSessions(uiState)
    val scopeSummary = buildScopeStatusSummary(scopeTargets)
    val scopedSystemReadyCount = scopeTargets.count { it.parsedSystemParams != null && it.systemParamHex.isNotBlank() }
    val scopedDsp1ReadyCount = scopeTargets.count { it.parsedDsp1Params != null && it.dsp1ParamHex.isNotBlank() }
    val scopedDsp2ReadyCount = scopeTargets.count { it.parsedDsp2Params != null && it.dsp2ParamHex.isNotBlank() }
    val scopedFullPayloadReadyCount = scopeTargets.count {
        it.parsedSystemParams != null &&
            it.systemParamHex.isNotBlank() &&
            it.parsedDsp1Params != null &&
            it.dsp1ParamHex.isNotBlank() &&
            it.parsedDsp2Params != null &&
            it.dsp2ParamHex.isNotBlank()
    }
    val enableTriggerWaveform = scopeSummary.triggerWaveformCount < scopeSummary.targetCount
    val enableLed = scopeSummary.ledOnCount < scopeSummary.targetCount
    var sectionName by rememberSaveable { mutableStateOf(ControlSection.Acquisition.name) }
    val section = ControlSection.valueOf(sectionName)
    var acquisitionPaneName by rememberSaveable { mutableStateOf(AcquisitionPane.Quick.name) }
    var closedLoopPaneName by rememberSaveable { mutableStateOf(ClosedLoopPane.Quick.name) }
    var analysisPaneName by rememberSaveable { mutableStateOf(AnalysisPane.Spike.name) }
    var systemPaneName by rememberSaveable { mutableStateOf(SystemPane.Push.name) }
    val controlListState = rememberLazyListState()
    val acquisitionPane = AcquisitionPane.valueOf(acquisitionPaneName)
    val closedLoopPane = ClosedLoopPane.valueOf(closedLoopPaneName)
    val analysisPane = AnalysisPane.valueOf(analysisPaneName)
    val systemPane = SystemPane.valueOf(systemPaneName)
    val taskKey = "$sectionName/$acquisitionPaneName/$closedLoopPaneName/$analysisPaneName/$systemPaneName"
    var previousTaskKey by rememberSaveable { mutableStateOf(taskKey) }
    LaunchedEffect(taskKey) {
        if (previousTaskKey != taskKey) {
            previousTaskKey = taskKey
            controlListState.scrollToItem(0)
        }
    }
    var pendingSystemAction by remember { mutableStateOf<PendingConfirmAction?>(null) }
    val activeRoleIdentity = activeSession?.let { formatBleRoleIdentity(it.roleTag, it.functionTag) }.orEmpty()

    pendingSystemAction?.let { action ->
        ConfirmActionDialog(
            title = action.title,
            message = action.message,
            confirmLabel = action.confirmLabel,
            onConfirm = {
                pendingSystemAction = null
                action.onConfirm()
            },
            onDismiss = { pendingSystemAction = null },
        )
    }

    LaunchedEffect(launchRequestToken) {
        if (launchRequestToken <= 0) {
            return@LaunchedEffect
        }
        sectionName = launchSectionName
        if (launchSectionName == ControlSection.ClosedLoop.name) {
            closedLoopPaneName = launchClosedLoopPaneName
        } else if (launchSectionName == ControlSection.System.name) {
            systemPaneName = launchSystemPaneName
        }
    }

    LazyColumn(
        modifier = modifier,
        state = controlListState,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (singleDeviceMode) {
            stickyHeader(key = "single-device-control-selector") {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SingleDeviceControlSelectorRow(
                        section = section,
                        acquisitionPane = acquisitionPane,
                        closedLoopPane = closedLoopPane,
                        analysisPane = analysisPane,
                        systemPane = systemPane,
                        onSectionChange = { sectionName = it.name },
                        onAcquisitionPaneChange = { acquisitionPaneName = it.name },
                        onClosedLoopPaneChange = { closedLoopPaneName = it.name },
                        onAnalysisPaneChange = { analysisPaneName = it.name },
                        onSystemPaneChange = { systemPaneName = it.name },
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
        } else {
            item(key = "control-launchpad") {
                ControlLaunchpadCard(
                    scope = uiState.controlScope,
                    selectedCount = uiState.selectedSessions.size,
                    scopeTargetCount = scopeTargets.size,
                    preservedScopeCount = preservedScopedSessions(uiState).size,
                    section = section,
                    sessions = controlContextSessions,
                    activeSessionId = uiState.activeSessionId,
                    activeSessionName = activeSession?.name,
                    onScopeChange = onScopeChange,
                    onSectionChange = { sectionName = it.name },
                    onActivate = onActivateSession,
                    subtitle = compactUiCopy("", ""),
                )
            }

            if (shouldShowTargetSelectionCard(uiState)) {
                item(key = "control-target-selection") {
                    TargetSelectionCard(
                        sessions = uiState.connectedSessions,
                        selectedSessionIds = uiState.selectedSessionIds,
                        onToggleSelection = onToggleSessionSelection,
                        onSelectAll = onSelectAllConnectedSessions,
                        onClearSelection = onClearSelectedSessions,
                    )
                }
            }
        }

        when (section) {
            ControlSection.Acquisition -> {
                if (!singleDeviceMode) {
                    item {
                        ControlSubpanelSelectorCard(
                            sectionLabel = section.label,
                            subtitle = compactUiCopy("", ""),
                            options = AcquisitionPane.entries.map { it.name to it.label },
                            selectedOptionName = acquisitionPane.name,
                            onSelectOption = { acquisitionPaneName = it },
                            compact = false,
                        )
                    }
                }

                when (acquisitionPane) {
                    AcquisitionPane.Quick -> item {
                        PocketAcquisitionDeckCard(
                            scope = uiState.controlScope,
                            targetCount = scopeSummary.targetCount,
                            session = activeSession,
                            canControlScope = canControlScope,
                            onResync = onResync,
                            onReadAllParams = onReadAllParams,
                            compactSingleDevice = singleDeviceMode,
                        )
                    }

                    AcquisitionPane.Rates -> item {
                        if (activeSession != null) {
                            StreamRateControlCard(
                                scope = uiState.controlScope,
                                targetCount = scopeSummary.targetCount,
                                session = activeSession,
                                onApplyStreamRates = onApplyStreamRates,
                                onQuickSetFs = onQuickSetFs,
                                onApplySystemProfileToSystem = onApplyAcquisitionSystemProfileToSystem,
                                compactSingleDevice = singleDeviceMode,
                            )
                        } else {
                            EmptyStateCard("Connect an active device first to read or push acquisition rates.")
                        }
                    }

                    AcquisitionPane.Camera -> item {
                        if (activeSession != null) {
                            CameraParamControlCard(
                                session = activeSession,
                                scope = uiState.controlScope,
                                targetCount = scopeSummary.targetCount,
                                canControlScope = canControlScope,
                                onReadCameraParams = onReadCameraParams,
                                onSetCameraParams = onSetCameraParams,
                                compactSingleDevice = singleDeviceMode,
                            )
                        } else {
                            EmptyStateCard("Camera parameter actions need an active connected device.")
                        }
                    }

                    AcquisitionPane.Impedance -> {
                        item {
                            if (activeSession != null) {
                                ImpedanceDiagnosticsCard(
                                    session = activeSession,
                                    scope = uiState.controlScope,
                                    targetCount = scopeSummary.targetCount,
                                    canControlScope = canControlScope,
                                    onRunImpedance = onImpedance,
                                    onExportImpedance = onExportImpedance,
                                    onShowExportPath = onShowImpedanceExportPath,
                                    compactSingleDevice = singleDeviceMode,
                                )
                            } else {
                                EmptyStateCard("Impedance diagnostics need an active connected device.")
                            }
                        }

                        item {
                            if (activeSession != null) {
                                AutoImpedanceCard(
                                    session = activeSession,
                                    running = uiState.autoImpedanceRunning,
                                    scheduledDeviceId = uiState.autoImpedanceDeviceId,
                                    scheduledDeviceName = uiState.autoImpedanceDeviceName,
                                    intervalMinutes = uiState.autoImpedanceIntervalMinutes,
                                    nextRunAtMs = uiState.autoImpedanceNextRunAtMs,
                                    statusMessage = uiState.autoImpedanceStatusMessage,
                                    onStartAuto = onStartAutoImpedance,
                                    onStopAuto = onStopAutoImpedance,
                                )
                            } else {
                                EmptyStateCard("Select a BLE device to arm scheduled impedance sweeps.")
                            }
                        }
                    }
                }
            }

            ControlSection.ClosedLoop -> {
                if (!singleDeviceMode) {
                    item {
                        ControlSubpanelSelectorCard(
                            sectionLabel = section.label,
                            subtitle = compactUiCopy("", ""),
                            options = ClosedLoopPane.entries.map { it.name to it.label },
                            selectedOptionName = closedLoopPane.name,
                            onSelectOption = { closedLoopPaneName = it },
                            compact = false,
                        )
                    }
                }

                when (closedLoopPane) {
                    ClosedLoopPane.Quick -> item {
                        if (activeSession != null) {
                            ClosedLoopQuickControlCard(
                                session = activeSession,
                                scope = uiState.controlScope,
                                targetCount = scopeSummary.targetCount,
                                canControlScope = canControlScope,
                                onSetStimEnabled = onSetStimEnabled,
                                onSetTriggerGain = onSetTriggerGain,
                                onSetStimIntensity = onSetStimIntensity,
                                onSetTriggerThreshold = onSetTriggerThreshold,
                                onForceTrigger = onForceTrigger,
                                compactSingleDevice = singleDeviceMode,
                            )
                        } else {
                            EmptyStateCard("Closed-loop controls need an active connected device.")
                        }
                    }

                    ClosedLoopPane.Profile -> item {
                        if (activeSession != null) {
                            ClosedLoopProfileCard(
                                session = activeSession,
                                scope = uiState.controlScope,
                                targetCount = scopeSummary.targetCount,
                                systemReadyCount = scopedSystemReadyCount,
                                fullPayloadReadyCount = scopedFullPayloadReadyCount,
                                canControlScope = canControlScope,
                                onApplyProfileToSystem = onApplyClosedLoopProfileToSystem,
                                onApplyProfileToAll = onApplyClosedLoopProfileToAll,
                                compactSingleDevice = singleDeviceMode,
                            )
                        } else {
                            EmptyStateCard("Read system params first to edit closed-loop routing and profile fields.")
                        }
                    }

                    ClosedLoopPane.Advanced -> item {
                        if (activeSession != null) {
                            AdvancedClosedLoopCard(
                                session = activeSession,
                                scope = uiState.controlScope,
                                targetCount = scopeSummary.targetCount,
                                canControlScope = canControlScope,
                                onSetStimParams = onSetStimParams,
                                onSetDspLiveParams = onSetDspLiveParams,
                                compactSingleDevice = singleDeviceMode,
                            )
                        } else {
                            EmptyStateCard("Read system and DSP params from an active device to unlock the advanced closed-loop editors.")
                        }
                    }

                    ClosedLoopPane.Monitor -> {
                        if (activeSession != null) {
                            item {
                                StimMonitorCard(activeSession)
                            }
                        } else {
                            item {
                                EmptyStateCard("Closed-loop monitoring needs an active connected device.")
                            }
                        }

                        item {
                            ControlCard(
                                title = "Monitoring Helpers",
                                subtitle = compactUiCopy("", ""),
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    InfoPill(
                                        "State",
                                        activeSession?.stimControlStatus?.stateLabel ?: "Idle",
                                        Modifier.weight(1f),
                                    )
                                    InfoPill(
                                        "Wave Blocks",
                                        activeSession?.triggeredWaveformBlockCount?.toString() ?: "0",
                                        Modifier.weight(1f),
                                    )
                                    InfoPill(
                                        "Targets",
                                        scopeSummary.targetCount.toString(),
                                        Modifier.weight(1f),
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedButton(
                                        onClick = onReadAllParams,
                                        modifier = Modifier.weight(1f),
                                        enabled = canControlScope,
                                    ) {
                                        Text(scopeActionLabel("Read All", scopeSummary.targetCount))
                                    }
                                    OutlinedButton(
                                        onClick = onReadDsp,
                                        modifier = Modifier.weight(1f),
                                        enabled = canControlScope,
                                    ) {
                                        Text(scopeActionLabel("Read DSP", scopeSummary.targetCount))
                                    }
                                }

                                if (scopeSummary.targetCount > 1) {
                                    InfoPill(
                                        "Waveform Scope",
                                        "${scopeSummary.triggerWaveformCount}/${scopeSummary.targetCount} enabled",
                                        Modifier.fillMaxWidth(),
                                    )
                                }
                                if (activeSession?.triggerWaveformCapturePath?.isNotBlank() == true) {
                                    Text(
                                        "Active capture: ${compactPathLabel(activeSession.triggerWaveformCapturePath)}${if (activeSession.triggerWaveformCaptureActive) " (capturing)" else ""}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    FilledTonalButton(
                                        onClick = { onTriggerWaveform(enableTriggerWaveform) },
                                        modifier = Modifier.weight(1f),
                                        enabled = canControlScope,
                                    ) {
                                        Text(
                                            if (scopeSummary.targetCount > 1) {
                                                if (enableTriggerWaveform) {
                                                    "Enable Waveform (${scopeSummary.targetCount})"
                                                } else {
                                                    "Disable Waveform (${scopeSummary.targetCount})"
                                                }
                                            } else if (activeSession?.triggerWaveformEnabled == true) {
                                                "Disable Trigger Waveform"
                                            } else {
                                                "Enable Trigger Waveform"
                                            }
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = onShowTriggerWaveformPath,
                                        modifier = Modifier.weight(1f),
                                        enabled = activeSession?.triggerWaveformCapturePath?.isNotBlank() == true,
                                    ) {
                                        Text("Wave Path")
                                    }
                                }
                            }
                        }
                        if (activeSession != null) {
                            item {
                                ClosedLoopWaveformViewerCard(activeSession)
                            }
                        }
                    }
                }
            }

            ControlSection.Analysis -> {
                if (!singleDeviceMode) {
                    item {
                        ControlSubpanelSelectorCard(
                            sectionLabel = section.label,
                            subtitle = compactUiCopy("", ""),
                            options = AnalysisPane.entries.map { it.name to it.label },
                            selectedOptionName = analysisPane.name,
                            onSelectOption = { analysisPaneName = it },
                            compact = false,
                        )
                    }
                }

                item {
                    if (activeSession != null) {
                        SignalAnalysisCard(
                            pane = analysisPane,
                            session = activeSession,
                            scope = uiState.controlScope,
                            targetCount = scopeSummary.targetCount,
                            canControlScope = canControlScope,
                            onRefresh = onRefreshSignalAnalysis,
                            onSetSpikeConfig = onSetSpikeDetectorConfig,
                            onSetSpectrumConfig = onSetSpectrumConfig,
                            onSetSchedulerEnabled = onSetSchedulerEnabled,
                            onSetSchedulerRule = onSetSchedulerRule,
                            onSetSchedulerRuleEnabled = onSetSchedulerRuleEnabled,
                            onClearSchedulerRule = onClearSchedulerRule,
                            onClearScheduler = onClearScheduler,
                            onReadScheduler = onReadScheduler,
                            onSaveSchedulerProfile = onSaveSchedulerProfile,
                        )
                    } else {
                        EmptyStateCard("Signal tools need an active connected device.")
                    }
                }
            }

            ControlSection.Io -> item {
                ControlCard(
                    title = "GPIO + LED",
                    subtitle = compactUiCopy("", ""),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoPill(
                            "LED",
                            if (scopeSummary.targetCount > 1) {
                                "${scopeSummary.ledOnCount}/${scopeSummary.targetCount} on"
                            } else if (activeSession?.ledOn == true) {
                                "On"
                            } else {
                                "Off"
                            },
                            Modifier.weight(1f),
                        )
                        InfoPill("GPIO0", scopeSummary.gpio0ModeLabel, Modifier.weight(1f))
                        InfoPill("GPIO1", scopeSummary.gpio1ModeLabel, Modifier.weight(1f))
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledTonalButton(onClick = { onLed(enableLed) }, modifier = Modifier.weight(1f), enabled = canControlScope) {
                            Text(
                                if (scopeSummary.targetCount > 1) {
                                    if (enableLed) "LED On (${scopeSummary.targetCount})" else "LED Off (${scopeSummary.targetCount})"
                                } else if (activeSession?.ledOn == true) {
                                    "LED Off"
                                } else {
                                    "LED On"
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    GpioModeSelectorRow(
                        title = "GPIO0",
                        currentLabel = scopeSummary.gpio0ModeLabel,
                        selectedMode = scopeSummary.gpio0ConsensusMode,
                        enabled = canControlScope,
                        onSelectMode = onGpio0,
                    )

                    Spacer(Modifier.height(12.dp))

                    GpioModeSelectorRow(
                        title = "GPIO1",
                        currentLabel = scopeSummary.gpio1ModeLabel,
                        selectedMode = scopeSummary.gpio1ConsensusMode,
                        enabled = canControlScope,
                        onSelectMode = onGpio1,
                    )
                }
            }

            ControlSection.System -> {
                if (!singleDeviceMode) {
                    item {
                        ControlSubpanelSelectorCard(
                            sectionLabel = section.label,
                            subtitle = compactUiCopy("", ""),
                            options = SystemPane.entries.map { it.name to it.label },
                            selectedOptionName = systemPane.name,
                            onSelectOption = { systemPaneName = it },
                            compact = false,
                        )
                    }
                }

                when (systemPane) {
                    SystemPane.Push -> item {
                        if (activeSession != null) {
                            PocketSystemDeckCard(
                                session = activeSession,
                                scope = uiState.controlScope,
                                targetCount = scopeSummary.targetCount,
                                systemReadyCount = scopedSystemReadyCount,
                                dsp1ReadyCount = scopedDsp1ReadyCount,
                                dsp2ReadyCount = scopedDsp2ReadyCount,
                                fullPayloadReadyCount = scopedFullPayloadReadyCount,
                                activeRoleIdentity = activeRoleIdentity,
                                onUploadSystemParams = onUploadSystemParams,
                                onUploadDspParams = onUploadDspParams,
                                onUploadAllParams = onUploadAllParams,
                                compactSingleDevice = singleDeviceMode,
                            )
                        } else {
                            EmptyStateCard("Read system and DSP payloads from an active device before sending scoped parameter pushes.")
                        }
                    }

                    SystemPane.Lifecycle -> item {
                        if (activeSession != null) {
                            SystemLifecycleCard(
                                session = activeSession,
                                activeRoleIdentity = activeRoleIdentity,
                                onSleep = onSleep,
                                onRequestReset = {
                                    pendingSystemAction = PendingConfirmAction(
                                        title = "Reset connected device?",
                                        message = "This issues a software reset to the active device and drops the current BLE session while it reboots.",
                                        confirmLabel = "Reset",
                                        onConfirm = onReset,
                                    )
                                },
                                onRequestBootloader = {
                                    pendingSystemAction = PendingConfirmAction(
                                        title = "Enter STM32 bootloader?",
                                        message = "The active device will reboot into the STM32 system bootloader and disconnect from BLE until it is reset or flashed.",
                                        confirmLabel = "Enter Bootloader",
                                        onConfirm = onBootloader,
                                    )
                                },
                                onRequestFirmwareUpdate = {
                                    pendingSystemAction = PendingConfirmAction(
                                        title = "Install SD-card firmware update?",
                                        message = "This does not upload a file. The active device will reboot and install the bootloader image already stored on its SD card. BLE will disconnect during the restart.",
                                        confirmLabel = "Install SD update",
                                        onConfirm = onFirmwareUpdate,
                                    )
                                },
                                onPickBleOtaPackage = onPickBleOtaPackage,
                                onRequestStageBleOta = {
                                    pendingSystemAction = PendingConfirmAction(
                                        title = "Stage firmware over BLE?",
                                        message = "The selected single fused .hex firmware will be extracted, copied to the device SD staging area, and fully verified. Recording must be stopped. This does not reboot or install firmware yet.",
                                        confirmLabel = "Stage & verify",
                                        onConfirm = onStageBleOta,
                                    )
                                },
                                onRequestInstallStagedBleOta = {
                                    pendingSystemAction = PendingConfirmAction(
                                        title = "Install verified firmware?",
                                        message = "CE64 has verified the complete staged image. Install will restart the device into Bootloader V3. Keep power and the SD card in place until it finishes.",
                                        confirmLabel = "Install firmware",
                                        onConfirm = onInstallStagedBleOta,
                                    )
                                },
                                onRequestAiModuleInstall = { slot, label ->
                                    pendingSystemAction = PendingConfirmAction(
                                        title = "Activate staged $label AI module?",
                                        message = "The $label AI module must already be staged on the device SD card. This sends the console-equivalent activation request only; it does not copy a model file to the device.",
                                        confirmLabel = "Activate $label",
                                        onConfirm = { onRequestAiModuleInstall(slot) },
                                    )
                                },
                                onRefreshAiStatus = onRefreshAiStatus,
                                onSelectAiModule = onSelectAiModule,
                                onSetAiRuntimeEnabled = onSetAiRuntimeEnabled,
                                onRequestRoleOverride = { mode, label ->
                                    val title = when (mode) {
                                        0 -> "Request AUTO role override?"
                                        1 -> "Request MASTER role override?"
                                        else -> "Request SLAVE role override?"
                                    }
                                    val message = when (mode) {
                                        0 -> "This sends the desktop AUTO role override to the active device and then drops the BLE session while the device reconfigures."
                                        1 -> "This mirrors the Windows MASTER override. The active device will drop BLE while it reboots or reconfigures into the host/master role."
                                        else -> "This mirrors the Windows SLAVE override. The active device will drop BLE while it reboots or reconfigures into the follower/device role."
                                    }
                                    val confirmLabel = when (mode) {
                                        0 -> "Request AUTO"
                                        1 -> "Request MASTER"
                                        else -> "Request SLAVE"
                                    }
                                    pendingSystemAction = PendingConfirmAction(
                                        title = title,
                                        message = message,
                                        confirmLabel = confirmLabel,
                                        onConfirm = { onRole(mode, label) },
                                    )
                                },
                            )
                        } else {
                            EmptyStateCard("Power, boot, firmware, and role actions need an active connected device.")
                        }
                    }

                    SystemPane.Dump -> item {
                        if (activeSession != null) {
                            ParamDumpCard(activeSession)
                        } else {
                            EmptyStateCard("Lifecycle, role, camera, and parameter actions need an active device.")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SignalAnalysisCard(
    pane: AnalysisPane,
    session: DeviceSessionUiState,
    scope: ControlScope,
    targetCount: Int,
    canControlScope: Boolean,
    onRefresh: () -> Unit,
    onSetSpikeConfig: (SpikeDetectorConfigUiState) -> Unit,
    onSetSpectrumConfig: (SpectrumConfigUiState) -> Unit,
    onSetSchedulerEnabled: (Boolean) -> Unit,
    onSetSchedulerRule: (SchedulerRuleUiState) -> Unit,
    onSetSchedulerRuleEnabled: (Int, Boolean) -> Unit,
    onClearSchedulerRule: (Int) -> Unit,
    onClearScheduler: () -> Unit,
    onReadScheduler: () -> Unit,
    onSaveSchedulerProfile: (Int) -> Unit,
) {
    ControlCard(
        title = when (pane) {
            AnalysisPane.Spike -> "Live transient viewer"
            AnalysisPane.Spectrum -> "Selected-signal spectrum"
            AnalysisPane.Schedule -> "Device recording schedule"
        },
        subtitle = "",
    ) {
        when (pane) {
            AnalysisPane.Spike -> SpikeAnalysisPane(
                session = session,
                targetCount = targetCount,
                canControlScope = canControlScope,
                onRefresh = onRefresh,
                onSetConfig = onSetSpikeConfig,
            )

            AnalysisPane.Spectrum -> SpectrumAnalysisPane(
                session = session,
                targetCount = targetCount,
                canControlScope = canControlScope,
                onRefresh = onRefresh,
                onSetConfig = onSetSpectrumConfig,
            )

            AnalysisPane.Schedule -> SchedulerAnalysisPane(
                session = session,
                scope = scope,
                targetCount = targetCount,
                canControlScope = canControlScope,
                onRefresh = onReadScheduler,
                onSetEnabled = onSetSchedulerEnabled,
                onSetRule = onSetSchedulerRule,
                onSetRuleEnabled = onSetSchedulerRuleEnabled,
                onClearRule = onClearSchedulerRule,
                onClearAll = onClearScheduler,
                onSaveProfile = onSaveSchedulerProfile,
            )
        }
    }
}

@Composable
private fun ClosedLoopWaveformViewerCard(session: DeviceSessionUiState) {
    ControlCard(title = "Live closed-loop events", subtitle = "") {
        Text(
            "Continuous CL1 + CL2 capture is best-effort. The vertical guide marks the trigger sample; SD recording remains the priority.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoPill("CL1", (session.triggeredWaveformBlocksByLane[0] ?: 0).toString(), Modifier.weight(1f))
            InfoPill("CL2", (session.triggeredWaveformBlocksByLane[1] ?: 0).toString(), Modifier.weight(1f))
            InfoPill("State", if (session.triggerWaveformEnabled) "On" else "Off", Modifier.weight(1f))
        }
        listOf(0 to "CL1", 1 to "CL2").forEach { (lane, label) ->
            val samples = session.latestTriggeredWaveforms[lane]
            Text(label, style = MaterialTheme.typography.labelLarge)
            if (samples.isNullOrEmpty()) {
                EmptyAnalysisPlot("No $label event yet")
            } else {
                ClosedLoopWaveformPlot(samples, lane)
            }
        }
    }
}

@Composable
private fun ClosedLoopWaveformPlot(samples: List<Int>, lane: Int) {
    val guideColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.32f)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(132.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f), RoundedCornerShape(14.dp)),
    ) {
        if (samples.size < 2) return@Canvas
        val midpoint = size.height / 2f
        drawLine(guideColor, Offset(0f, midpoint), Offset(size.width, midpoint), 1f)
        val triggerX = 127f * size.width / (samples.size - 1).toFloat()
        drawLine(guideColor, Offset(triggerX, 0f), Offset(triggerX, size.height), 1f)
        val maxAmplitude = samples.maxOf { abs(it) }.coerceAtLeast(1).toFloat()
        val trace = Path()
        samples.forEachIndexed { index, sample ->
            val x = index * size.width / (samples.size - 1).toFloat()
            val y = midpoint - sample / maxAmplitude * (size.height * 0.40f)
            if (index == 0) trace.moveTo(x, y) else trace.lineTo(x, y)
        }
        drawPath(
            trace,
            color = if (lane == 0) Color(0xFF287BB3) else Color(0xFFB34F82),
            style = Stroke(width = 2.2f, cap = StrokeCap.Round),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpikeAnalysisPane(
    session: DeviceSessionUiState,
    targetCount: Int,
    canControlScope: Boolean,
    onRefresh: () -> Unit,
    onSetConfig: (SpikeDetectorConfigUiState) -> Unit,
) {
    val config = session.spikeDetectorConfig ?: SpikeDetectorConfigUiState(channelEnableMask = -1L)
    var selectedChannel by rememberSaveable(session.id) { mutableStateOf(0) }
    var thresholdText by remember(config, selectedChannel) {
        mutableStateOf(config.thresholds.getOrElse(selectedChannel) { 120 }.toString())
    }
    val selectedEnabled = config.channelEnabled(selectedChannel)
    val selectedPositive = config.positivePolarity(selectedChannel)
    val latest = session.recentSpikeEvents.firstOrNull { it.channel == selectedChannel }

    Text(
        "Best-effort events are available only during active 20 kHz SD recording. This never changes the recorded raw data.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        InfoPill("Viewer", if (config.enabled) "On" else "Off", Modifier.weight(1f))
        InfoPill("Events", session.recentSpikeEvents.size.toString(), Modifier.weight(1f))
        InfoPill("Drops", session.spikeViewerDropCount.toString(), Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FilledTonalButton(
            onClick = { onSetConfig(config.copy(enabled = !config.enabled)) },
            enabled = canControlScope,
            modifier = Modifier.weight(1f),
        ) {
            Text(scopeActionLabel(if (config.enabled) "Disable viewer" else "Enable viewer", targetCount))
        }
        OutlinedButton(onClick = onRefresh, enabled = canControlScope, modifier = Modifier.weight(0.7f)) {
            Text("Refresh")
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(
            onClick = { selectedChannel = (selectedChannel + 63) % 64 },
            enabled = canControlScope,
        ) { Text("‹") }
        Text("Channel ${selectedChannel + 1}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        OutlinedButton(
            onClick = { selectedChannel = (selectedChannel + 1) % 64 },
            enabled = canControlScope,
        ) { Text("›") }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = selectedEnabled,
            onClick = {
                val bit = 1L shl selectedChannel
                val mask = if (selectedEnabled) config.channelEnableMask and bit.inv() else config.channelEnableMask or bit
                onSetConfig(config.copy(channelEnableMask = mask))
            },
            enabled = canControlScope,
            label = { Text(if (selectedEnabled) "Detect on" else "Detect off") },
        )
        FilterChip(
            selected = selectedPositive,
            onClick = {
                val bit = 1L shl selectedChannel
                val mask = if (selectedPositive) config.positivePolarityMask and bit.inv() else config.positivePolarityMask or bit
                onSetConfig(config.copy(positivePolarityMask = mask))
            },
            enabled = canControlScope,
            label = { Text(if (selectedPositive) "Positive slope" else "Negative slope") },
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = thresholdText,
            onValueChange = { thresholdText = it.filter(Char::isDigit).take(5) },
            label = { Text("Slope threshold (counts)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        FilledTonalButton(
            onClick = {
                val thresholds = config.thresholds.toMutableList()
                thresholds[selectedChannel] = thresholdText.toIntOrNull()?.coerceIn(1, Short.MAX_VALUE.toInt()) ?: thresholds[selectedChannel]
                onSetConfig(config.copy(thresholds = thresholds))
            },
            enabled = canControlScope,
        ) { Text("Apply") }
    }
    if (latest != null) {
        Text(
            "Latest event: #${latest.sequence} · sample ${latest.sampleIndex} · ${if (latest.positivePolarity) "positive" else "negative"} slope",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        SpikeWaveformPlot(latest.samples, positive = latest.positivePolarity)
    } else {
        EmptyAnalysisPlot("No event from this channel yet")
    }
}

@Composable
private fun SpikeWaveformPlot(samples: List<Int>, positive: Boolean) {
    val guideColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.32f)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(152.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f), RoundedCornerShape(14.dp)),
    ) {
        if (samples.size < 2) return@Canvas
        val midpoint = size.height / 2f
        drawLine(guideColor, Offset(0f, midpoint), Offset(size.width, midpoint), 1f)
        val maxAmplitude = samples.maxOf { abs(it) }.coerceAtLeast(1).toFloat()
        val trace = Path()
        samples.forEachIndexed { index, sample ->
            val x = index * size.width / (samples.size - 1).toFloat()
            val y = midpoint - sample / maxAmplitude * (size.height * 0.40f)
            if (index == 0) trace.moveTo(x, y) else trace.lineTo(x, y)
        }
        drawPath(
            trace,
            color = if (positive) Color(0xFFC4486D) else Color(0xFF3374C4),
            style = Stroke(width = 2.5f, cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun EmptyAnalysisPlot(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpectrumAnalysisPane(
    session: DeviceSessionUiState,
    targetCount: Int,
    canControlScope: Boolean,
    onRefresh: () -> Unit,
    onSetConfig: (SpectrumConfigUiState) -> Unit,
) {
    val deviceConfig = session.spectrumConfig ?: SpectrumConfigUiState()
    var source by rememberSaveable(session.id) { mutableStateOf(deviceConfig.source) }
    var channelText by rememberSaveable(session.id) { mutableStateOf(deviceConfig.channel.toString()) }
    var periodText by rememberSaveable(session.id) { mutableStateOf(deviceConfig.periodMs.toString()) }
    val snapshot = session.spectrumSnapshot
    val sourceLabel = if (source == Ce32Protocol.SpectrumSourceEphys) "Ephys" else "ADC"

    Text(
        "A runtime-only 128-point FFT. It samples one selected signal and automatically yields to recording, SD, and command traffic.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        InfoPill("Source", deviceConfig.sourceLabel, Modifier.weight(1f))
        InfoPill("State", if (deviceConfig.enabled) "On" else "Off", Modifier.weight(1f))
        InfoPill("Skipped", deviceConfig.skippedUpdates.toString(), Modifier.weight(1f))
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = source == Ce32Protocol.SpectrumSourceAdc,
            onClick = { source = Ce32Protocol.SpectrumSourceAdc; channelText = "0" },
            enabled = canControlScope,
            label = { Text("ADC") },
        )
        FilterChip(
            selected = source == Ce32Protocol.SpectrumSourceEphys,
            onClick = { source = Ce32Protocol.SpectrumSourceEphys },
            enabled = canControlScope,
            label = { Text("Ephys") },
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (source == Ce32Protocol.SpectrumSourceEphys) {
            OutlinedTextField(
                value = channelText,
                onValueChange = { channelText = it.filter(Char::isDigit).take(2) },
                label = { Text("Channel (0–63)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedTextField(
            value = periodText,
            onValueChange = { periodText = it.filter(Char::isDigit).take(4) },
            label = { Text("Update ms") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FilledTonalButton(
            onClick = {
                val enabled = !deviceConfig.enabled
                onSetConfig(
                    deviceConfig.copy(
                        enabled = enabled,
                        source = source,
                        channel = if (source == Ce32Protocol.SpectrumSourceEphys) channelText.toIntOrNull()?.coerceIn(0, 63) ?: 0 else 0,
                        firstBin = if (source == Ce32Protocol.SpectrumSourceEphys) 1 else 16,
                        lastBin = 63,
                        periodMs = periodText.toIntOrNull()?.coerceIn(100, 1_000) ?: 200,
                    ),
                )
            },
            enabled = canControlScope,
            modifier = Modifier.weight(1f),
        ) {
            Text(scopeActionLabel(if (deviceConfig.enabled) "Stop spectrum" else "Start spectrum", targetCount))
        }
        OutlinedButton(onClick = onRefresh, enabled = canControlScope, modifier = Modifier.weight(0.7f)) { Text("Refresh") }
    }
    if (snapshot?.isComplete == true) {
        val frequencyHz = snapshot.peakBin * if (snapshot.source == Ce32Protocol.SpectrumSourceEphys) 625.0 / 128.0 else 160_000.0 / 128.0
        Text(
            "Peak ${formatAnalysisFrequency(frequencyHz)} · confidence ${snapshot.confidence} · ${snapshot.sourceLabel()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        SpectrumLevelPlot(snapshot)
    } else {
        EmptyAnalysisPlot("No complete spectrum snapshot yet")
    }
}

@Composable
private fun SpectrumLevelPlot(snapshot: SpectrumSnapshotUiState) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(152.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f), RoundedCornerShape(14.dp)),
    ) {
        val levels = snapshot.levels
        if (levels.isEmpty()) return@Canvas
        val maxLevel = levels.maxOrNull()?.coerceAtLeast(1) ?: 1
        val spacing = size.width / levels.size
        levels.forEachIndexed { index, level ->
            val barWidth = spacing * 0.66f
            val height = size.height * (level.toFloat() / maxLevel.toFloat()).coerceIn(0.02f, 1f)
            val x = index * spacing + (spacing - barWidth) / 2f
            drawRect(
                color = if (index == snapshot.peakBin % levels.size) Color(0xFFEA9F32) else Color(0xFF5C82C9),
                topLeft = Offset(x, size.height - height),
                size = Size(barWidth, height),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SchedulerAnalysisPane(
    session: DeviceSessionUiState,
    scope: ControlScope,
    targetCount: Int,
    canControlScope: Boolean,
    onRefresh: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onSetRule: (SchedulerRuleUiState) -> Unit,
    onSetRuleEnabled: (Int, Boolean) -> Unit,
    onClearRule: (Int) -> Unit,
    onClearAll: () -> Unit,
    onSaveProfile: (Int) -> Unit,
) {
    SchedulerRuleEditor(
        session = session,
        targetCount = targetCount,
        canControl = canControlScope,
        onRefresh = onRefresh,
        onSetEnabled = onSetEnabled,
        onSetRule = onSetRule,
        onSetRuleEnabled = onSetRuleEnabled,
        onClearRule = onClearRule,
        onClearAll = onClearAll,
        onSaveProfile = onSaveProfile,
    )
}

private fun SpectrumSnapshotUiState.sourceLabel(): String =
    if (source == Ce32Protocol.SpectrumSourceEphys) "ephys ch ${channel + 1}" else "ADC"

private fun formatAnalysisFrequency(valueHz: Double): String = when {
    valueHz >= 1_000_000.0 -> "${"%.1f".format(Locale.US, valueHz / 1_000_000.0)} MHz"
    valueHz >= 1_000.0 -> "${"%.1f".format(Locale.US, valueHz / 1_000.0)} kHz"
    else -> "${"%.1f".format(Locale.US, valueHz)} Hz"
}

@Composable
private fun ConfirmActionDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun RecordsScreen(
    modifier: Modifier = Modifier,
    uiState: WildUiState,
    launchPaneName: String,
    launchRequestToken: Int,
    onActivateSession: (String) -> Unit,
    onScopeChange: (ControlScope) -> Unit,
    onToggleSessionSelection: (String) -> Unit,
    onSelectAllConnectedSessions: () -> Unit,
    onClearSelectedSessions: () -> Unit,
    onRefreshScope: () -> Unit,
    onRefresh: () -> Unit,
    onRefreshForDevice: (String) -> Unit,
    onDeleteLast: () -> Unit,
    onDeleteAll: () -> Unit,
    onExportRecord: (Int) -> Unit,
    onExportAllScope: () -> Unit,
    onExportAll: () -> Unit,
    onExportAllForDevice: (String) -> Unit,
    onShowRecordExportPath: () -> Unit,
    onShowRecordExportPathForDevice: (String) -> Unit,
) {
    val activeSession = uiState.activeSession
    val linkedScopeTargets = scopedSessions(uiState)
    val displayScopeTargets = recordDisplayScopeSessions(uiState)
    val exportScopeSessions = recordExportScopeSessions(uiState)
    val exportReadyCount = exportScopeSessions.count { it.records.isNotEmpty() }
    var recordsPaneName by rememberSaveable { mutableStateOf(launchPaneName) }
    val recordsPane = RecordsPane.valueOf(recordsPaneName)
    var pendingRecordAction by remember(activeSession?.id, activeSession?.records?.size) { mutableStateOf<PendingConfirmAction?>(null) }

    LaunchedEffect(launchRequestToken) {
        recordsPaneName = launchPaneName
    }

    pendingRecordAction?.let { action ->
        ConfirmActionDialog(
            title = action.title,
            message = action.message,
            confirmLabel = action.confirmLabel,
            onConfirm = {
                pendingRecordAction = null
                action.onConfirm()
            },
            onDismiss = { pendingRecordAction = null },
        )
    }

    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            RecordsLaunchpadCard(
                scope = uiState.controlScope,
                selectedCount = uiState.selectedSessions.size,
                sessions = uiState.connectedSessions,
                scopeTargetCount = linkedScopeTargets.size,
                exportScopeCount = exportScopeSessions.size,
                preservedScopeCount = preservedScopedSessions(uiState).size,
                exportReadyCount = exportReadyCount,
                activeSessionId = uiState.activeSessionId,
                activeSessionName = activeSession?.name,
                onScopeChange = onScopeChange,
                onActivate = onActivateSession,
                onRefreshScope = onRefreshScope,
                onExportScope = onExportAllScope,
            )
        }

        item {
            if (shouldShowTargetSelectionCard(uiState)) {
                TargetSelectionCard(
                    sessions = uiState.connectedSessions,
                    selectedSessionIds = uiState.selectedSessionIds,
                    onToggleSelection = onToggleSessionSelection,
                    onSelectAll = onSelectAllConnectedSessions,
                    onClearSelection = onClearSelectedSessions,
                )
            }
        }

        item {
            ControlSubpanelSelectorCard(
                sectionLabel = "Records",
                subtitle = compactUiCopy("", ""),
                options = RecordsPane.entries.map { it.name to it.label },
                selectedOptionName = recordsPane.name,
                onSelectOption = { recordsPaneName = it },
            )
        }

        when (recordsPane) {
            RecordsPane.Fleet -> {
                item {
                    if (displayScopeTargets.size > 1) {
                        Text(
                            when (uiState.controlScope) {
                                ControlScope.ActiveDevice -> "Record Device"
                                ControlScope.SelectedDevices -> "Selected Records"
                                ControlScope.AllConnected -> "All Records"
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                    } else if (displayScopeTargets.size == 1) {
                        Text("Record Device", style = MaterialTheme.typography.titleMedium)
                    } else {
                        EmptyStateCard(
                            when {
                                hasReconnectingActiveScope(uiState) ->
                                    "The active record device is still selected but reconnecting. Refresh and export will return once it relinks."
                                hasReconnectingSelectedScope(uiState) ->
                                    "The selected devices are reconnecting. Refresh and export will return once one device relinks."
                                else ->
                                    "Connect one or more devices to view records."
                            }
                        )
                    }
                }

                if (displayScopeTargets.isNotEmpty()) {
                    items(displayScopeTargets, key = { it.id }) { session ->
                        RecordFleetCard(
                            session = session,
                            isActive = session.id == uiState.activeSessionId,
                            onActivate = { onActivateSession(session.id) },
                            onRefresh = { onRefreshForDevice(session.id) },
                            onExportAll = { onExportAllForDevice(session.id) },
                            onShowExportPath = { onShowRecordExportPathForDevice(session.id) },
                        )
                    }
                }
            }

            RecordsPane.Vault -> {
                item {
                    if (activeSession == null) {
                        EmptyStateCard("Choose an active connected device to inspect BLE record blocks.")
                    } else {
                        RecordVaultCard(
                            session = activeSession,
                            onRefresh = onRefresh,
                            onDeleteLast = {
                                pendingRecordAction = PendingConfirmAction(
                                    title = "Delete latest record?",
                                    message = "Remove the newest BLE record entry from ${activeSession.name}. This only affects the active device and cannot be undone.",
                                    confirmLabel = "Delete Latest",
                                    onConfirm = onDeleteLast,
                                )
                            },
                            onDeleteAll = {
                                pendingRecordAction = PendingConfirmAction(
                                    title = "Delete all records?",
                                    message = "Delete every BLE record entry from ${activeSession.name}. This only affects the active device and cannot be undone.",
                                    confirmLabel = "Delete All",
                                    onConfirm = onDeleteAll,
                                )
                            },
                            onExportAll = onExportAll,
                            onShowExportPath = onShowRecordExportPath,
                        )
                    }
                }
            }

            RecordsPane.Index -> {
                if (activeSession != null) {
                    if (activeSession.records.isEmpty()) {
                        item {
                            EmptyStateCard("No record list is loaded yet. Refresh while the device is connected.")
                        }
                    } else {
                        item {
                            Text("On-device Record Index", style = MaterialTheme.typography.titleMedium)
                        }
                        items(activeSession.records, key = { it.index }) { record ->
                            RecordBlockCard(
                                record = record,
                                enabled = true,
                                onExport = { onExportRecord(record.index) },
                            )
                        }
                    }
                } else {
                    item {
                        EmptyStateCard("Choose an active connected device to inspect the raw BLE record index.")
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordsLaunchpadCard(
    scope: ControlScope,
    selectedCount: Int,
    sessions: List<DeviceSessionUiState>,
    scopeTargetCount: Int,
    exportScopeCount: Int,
    preservedScopeCount: Int,
    exportReadyCount: Int,
    activeSessionId: String?,
    activeSessionName: String?,
    onScopeChange: (ControlScope) -> Unit,
    onActivate: (String) -> Unit,
    onRefreshScope: () -> Unit,
    onExportScope: () -> Unit,
) {
    val reconnectNote = recordScopeReconnectNote(
        scope = scope,
        preservedScopeCount = preservedScopeCount,
        linkedScopeCount = scopeTargetCount,
        exportReadyCount = exportReadyCount,
        activeSessionName = activeSessionName,
    )
    ControlCard(
        title = "Records",
        subtitle = compactUiCopy("", ""),
    ) {
        val scopeLabel = when (scope) {
            ControlScope.ActiveDevice -> "Active"
            ControlScope.SelectedDevices -> "Selected"
            ControlScope.AllConnected -> "All"
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoPill("Target", scopeLabel, Modifier.weight(1f))
            InfoPill("Selected", selectedCount.toString(), Modifier.weight(1f))
            InfoPill("Ready", scopedCountLabel(exportReadyCount, exportScopeCount), Modifier.weight(1f))
        }

        PocketButtonGrid(
            options = listOf(
                PocketButtonSpec(
                    label = "Active",
                    onClick = { onScopeChange(ControlScope.ActiveDevice) },
                    enabled = sessions.isNotEmpty(),
                    selected = scope == ControlScope.ActiveDevice,
                ),
                PocketButtonSpec(
                    label = "Selected $selectedCount",
                    onClick = { onScopeChange(ControlScope.SelectedDevices) },
                    enabled = selectedCount > 0,
                    selected = scope == ControlScope.SelectedDevices,
                ),
                PocketButtonSpec(
                    label = "All ${sessions.size}",
                    onClick = { onScopeChange(ControlScope.AllConnected) },
                    enabled = sessions.isNotEmpty(),
                    selected = scope == ControlScope.AllConnected,
                ),
            ),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(
                onClick = onRefreshScope,
                modifier = Modifier.weight(1f),
                enabled = scopeTargetCount > 0,
            ) {
                Text(scopeActionLabel("Refresh Scope", scopeTargetCount))
            }
            OutlinedButton(
                onClick = onExportScope,
                modifier = Modifier.weight(1f),
                enabled = exportReadyCount > 0,
            ) {
                Text(scopeActionLabel("Save Scope CSVs", exportReadyCount))
            }
        }

        if (reconnectNote != null && sessions.isNotEmpty()) {
            Text(
                reconnectNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            )
        }

        if (sessions.isEmpty()) {
            Text(
                reconnectNote ?: "Connect at least one device to run record-index refresh or export from this page.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
            return@ControlCard
        }

        InfoPill("Focused Device", activeSessionName ?: sessions.first().name, Modifier.fillMaxWidth())
        FocusSessionGrid(
            sessions = sessions,
            activeSessionId = activeSessionId,
            helperText = "Tap a device tile to focus the records vault and keep delete actions on that one device without sideways chip scrolling.",
            subtitleForSession = { session ->
                buildString {
                    append(compactSessionStateLabel(session))
                    append(" | ")
                    append("${session.records.size} rec")
                }
            },
            onActivate = onActivate,
        )

        if (SHOW_UI_DESCRIPTIONS) {
            Text(
                "Scope refresh fans out to the current linked target set. Save Scope CSVs can still export any in-scope device whose record index is already cached, while delete actions remain limited to the active device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            )
        }
    }
}

@Composable
private fun RecordFleetCard(
    session: DeviceSessionUiState,
    isActive: Boolean,
    onActivate: () -> Unit,
    onRefresh: () -> Unit,
    onExportAll: () -> Unit,
    onShowExportPath: () -> Unit,
) {
    val totalSizeMb = session.records.sumOf { it.sizeMb }
    val stateBadgeLabel = recordSessionBadgeLabel(session = session, isActive = isActive)
    val stateBadgeContainerColor = when {
        isActive -> MaterialTheme.colorScheme.primaryContainer
        session.isConnected -> MaterialTheme.colorScheme.surfaceVariant
        session.records.isNotEmpty() -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val stateBadgeContentColor = when {
        isActive -> MaterialTheme.colorScheme.onPrimaryContainer
        session.isConnected -> MaterialTheme.colorScheme.onSurface
        session.records.isNotEmpty() -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onTertiaryContainer
    }
    val latestLine = recordSessionSummaryLine(
        session = session,
        fallback = "Refresh this device to load its BLE record index.",
    )
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(session.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        session.statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                    )
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = stateBadgeContainerColor,
                ) {
                    Text(
                        stateBadgeLabel,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = stateBadgeContentColor,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoPill("Records", session.records.size.toString(), Modifier.weight(1f))
                InfoPill("Total", "%.2f MB".format(totalSizeMb), Modifier.weight(1f))
                InfoPill("Rec Time", formatSeconds(session.recordingSeconds), Modifier.weight(1f))
            }

            Text(
                latestLine,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )

            if (session.recordExportPath.isNotBlank()) {
                Text(
                    "Export folder: ${compactPathLabel(session.recordExportPath)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(
                    onClick = onActivate,
                    modifier = Modifier.weight(1f),
                    enabled = !isActive,
                ) {
                    Text(if (isActive) "Focused" else "Focus")
                }
                OutlinedButton(
                    onClick = onRefresh,
                    modifier = Modifier.weight(1f),
                    enabled = session.isConnected,
                ) {
                    Text("Refresh")
                }
                OutlinedButton(
                    onClick = onExportAll,
                    modifier = Modifier.weight(1f),
                    enabled = session.records.isNotEmpty(),
                ) {
                    Text("Save CSV")
                }
            }

            OutlinedButton(
                onClick = onShowExportPath,
                modifier = Modifier.fillMaxWidth(),
                enabled = session.isConnected || session.records.isNotEmpty(),
            ) {
                Text("Export Path")
            }
        }
    }
}

@Composable
private fun RecordVaultCard(
    session: DeviceSessionUiState,
    onRefresh: () -> Unit,
    onDeleteLast: () -> Unit,
    onDeleteAll: () -> Unit,
    onExportAll: () -> Unit,
    onShowExportPath: () -> Unit,
) {
    val totalSizeMb = session.records.sumOf { it.sizeMb }
    val hasRecords = session.records.isNotEmpty()
    ControlCard(
        title = "Record Files",
        subtitle = compactUiCopy("", ""),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoPill("Device", session.name, Modifier.weight(1f))
            InfoPill("State", compactSessionStateLabel(session), Modifier.weight(1f))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoPill("Records", session.records.size.toString(), Modifier.weight(1f))
            InfoPill("Total", "%.2f MB".format(totalSizeMb), Modifier.weight(1f))
            InfoPill("Rec Time", formatSeconds(session.recordingSeconds), Modifier.weight(1f))
        }

        if (session.syncText.isNotBlank()) {
            Text(
                session.syncText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }

        Text(
            session.lastFailure.ifBlank { session.lastMessage.ifBlank { "Refresh to load the current BLE record index." } },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
        )

        if (session.recordExportPath.isNotBlank()) {
            Text(
                "Export folder: ${compactPathLabel(session.recordExportPath)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(onClick = onRefresh, modifier = Modifier.weight(1f), enabled = session.isConnected) {
                Text("Refresh")
            }
            OutlinedButton(onClick = onDeleteLast, modifier = Modifier.weight(1f), enabled = session.isConnected && hasRecords) {
                Text("Delete Last")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(
                onClick = onExportAll,
                modifier = Modifier.weight(1f),
                enabled = hasRecords,
            ) {
                Text("Save Index CSV")
            }
            OutlinedButton(onClick = onDeleteAll, modifier = Modifier.weight(1f), enabled = session.isConnected && hasRecords) {
                Text("Delete All")
            }
        }

        OutlinedButton(
            onClick = onShowExportPath,
            modifier = Modifier.fillMaxWidth(),
            enabled = session.isConnected || hasRecords,
        ) {
            Text("Export Path")
        }

        Text(
            "Exports save cached record-index reports into the app files area for the active device, even after a disconnect. Delete actions apply only to the active device and cannot be undone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
        )
    }
}

@Composable
private fun RecordBlockCard(
    record: com.wild.android.ble.RecordSummary,
    enabled: Boolean,
    onExport: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Record ${record.index + 1}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Sectors ${record.startSector} - ${record.endSector}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                    )
                }
                Text("${"%.2f".format(record.sizeMb)} MB", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoPill("Start", record.startSector.toString(), Modifier.weight(1f))
                InfoPill("End", record.endSector.toString(), Modifier.weight(1f))
                InfoPill("Blocks", record.sizeSectors.toString(), Modifier.weight(1f))
            }

            FilledTonalButton(
                onClick = onExport,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled && record.sizeSectors > 0,
            ) {
                Text("Save Record Info")
            }
        }
    }
}

@Composable
private fun StreamRateControlCard(
    scope: ControlScope,
    targetCount: Int,
    session: DeviceSessionUiState,
    onApplyStreamRates: (Int, Boolean, Boolean) -> Unit,
    onQuickSetFs: (Int) -> Unit,
    onApplySystemProfileToSystem: (Int, Int, Int) -> Unit,
    compactSingleDevice: Boolean = false,
) {
    val parsed = session.parsedSystemParams
    var requestedRateText by remember(session.id) {
        mutableStateOf(parsed?.ephysSamplingRate?.takeIf { it >= 0 }?.toString() ?: "1250")
    }
    var cameraEnabled by remember(session.id) {
        mutableStateOf(parsed?.cameraEnabled == true)
    }
    var adcEnabled by remember(session.id) {
        mutableStateOf(parsed?.adcEnabled == true)
    }
    var vbattThresholdText by remember(session.id) {
        mutableStateOf(parsed?.vbattThresholdRaw?.toString() ?: "330")
    }
    var audioRatioText by remember(session.id) {
        mutableStateOf(parsed?.audioRatio?.toString() ?: "0")
    }
    var cameraRatioText by remember(session.id) {
        mutableStateOf(parsed?.cameraRatio?.toString() ?: "0")
    }

    LaunchedEffect(
        session.id,
        parsed?.ephysSamplingRate,
        parsed?.cameraEnabled,
        parsed?.adcEnabled,
    ) {
        requestedRateText = parsed?.ephysSamplingRate?.takeIf { it >= 0 }?.toString() ?: requestedRateText
        cameraEnabled = parsed?.cameraEnabled == true
        adcEnabled = parsed?.adcEnabled == true
    }
    LaunchedEffect(session.id, parsed?.vbattThresholdRaw, parsed?.audioRatio, parsed?.cameraRatio) {
        if (parsed != null) {
            vbattThresholdText = parsed.vbattThresholdRaw.toString()
            audioRatioText = parsed.audioRatio.toString()
            cameraRatioText = parsed.cameraRatio.toString()
        }
    }

    val requestedRate = requestedRateText.toIntOrNull()
    val vbattThresholdRaw = vbattThresholdText.toIntOrNull()
    val audioRatio = audioRatioText.toIntOrNull()
    val cameraRatio = cameraRatioText.toIntOrNull()
    val ratePresets = (listOfNotNull(parsed?.ephysSamplingRate?.takeIf { it >= 0 }) + Ce32Protocol.CommonEphysRates)
        .distinct()
        .sorted()
    val scopeLabel = when (scope) {
        ControlScope.ActiveDevice -> "Active"
        ControlScope.SelectedDevices -> "Selected"
        ControlScope.AllConnected -> "All"
    }

    ControlCard(
        title = if (compactSingleDevice) "" else "Acquisition",
        subtitle = compactUiCopy("", ""),
    ) {
        if (parsed == null) {
            Text(
                "Read system first for full write. Quick FS still works.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
        }

        if (!compactSingleDevice) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoPill("Scope", scopeLabel, Modifier.weight(1f))
                InfoPill("Targets", targetCount.toString(), Modifier.weight(1f))
                InfoPill("Source", session.name, Modifier.weight(1.4f))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoPill("Current FS", formatRateLabel(parsed?.ephysSamplingRate), Modifier.weight(1f))
            InfoPill("Camera", if (parsed?.cameraEnabled == true) "${parsed.cameraSamplingRate} Hz" else "Off", Modifier.weight(1f))
            InfoPill("ADC", if (parsed?.adcEnabled == true) "${parsed.adcSamplingRate} Hz" else "Off", Modifier.weight(1f))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoPill("VBatt Raw", parsed?.vbattThresholdRaw?.toString() ?: "--", Modifier.weight(1f))
            InfoPill("Audio Ratio", parsed?.audioRatio?.toString() ?: "--", Modifier.weight(1f))
            InfoPill("Camera Ratio", parsed?.cameraRatio?.toString() ?: "--", Modifier.weight(1f))
        }

        if (SHOW_UI_DESCRIPTIONS) {
            Text(
                if (targetCount > 1) {
                    "The current values above come from the active device, but applying rates or quick FS will fan out to the $targetCount scoped devices."
                } else {
                    "The current values above come from the active device and the write stays focused on that one target."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            )
        }

        OutlinedTextField(
            value = requestedRateText,
            onValueChange = { next ->
                if (next.all { it.isDigit() }) {
                    requestedRateText = next.take(6)
                }
            },
            label = { Text("FS / ephys rate (0 = Off)") },
            enabled = session.isConnected,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )

        PocketButtonGrid(
            options = ratePresets.map { rate ->
                PocketButtonSpec(
                    label = formatRateLabel(rate),
                    onClick = { requestedRateText = rate.toString() },
                    enabled = session.isConnected,
                    selected = requestedRate == rate,
                )
            },
        )

        PocketButtonGrid(
            options = listOf(
                PocketButtonSpec(
                    label = if (cameraEnabled) "Camera 16 Hz" else "Camera Off",
                    onClick = { cameraEnabled = !cameraEnabled },
                    enabled = session.isConnected,
                    selected = cameraEnabled,
                ),
                PocketButtonSpec(
                    label = if (adcEnabled) "ADC 160 kHz" else "ADC Off",
                    onClick = { adcEnabled = !adcEnabled },
                    enabled = session.isConnected,
                    selected = adcEnabled,
                ),
            ),
            columns = 2,
        )

        if (SHOW_UI_DESCRIPTIONS) {
            Text(
                "The raw system fields below live in the full 512-byte system payload. Applying them stages the scoped payload and sends `Push Sys` immediately.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = vbattThresholdText,
                onValueChange = { next ->
                    if (next.all { it.isDigit() }) {
                        vbattThresholdText = next.take(5)
                    }
                },
                label = { Text("VBatt raw") },
                enabled = session.isConnected && parsed != null,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
            OutlinedTextField(
                value = audioRatioText,
                onValueChange = { next ->
                    if (next.all { it.isDigit() }) {
                        audioRatioText = next.take(3)
                    }
                },
                label = { Text("Audio ratio") },
                enabled = session.isConnected && parsed != null,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
        }

        OutlinedTextField(
            value = cameraRatioText,
            onValueChange = { next ->
                if (next.all { it.isDigit() }) {
                    cameraRatioText = next.take(3)
                }
            },
            label = { Text("Camera ratio") },
            enabled = session.isConnected && parsed != null,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )

        FilledTonalButton(
            onClick = { requestedRate?.let { onApplyStreamRates(it, cameraEnabled, adcEnabled) } },
            modifier = Modifier.fillMaxWidth(),
            enabled = session.isConnected && parsed != null && requestedRate != null && requestedRate in 0..0xFFFF,
        ) {
            Text(scopeActionLabel("Apply Rates", targetCount))
        }

        OutlinedButton(
            onClick = { requestedRate?.let(onQuickSetFs) },
            modifier = Modifier.fillMaxWidth(),
            enabled = session.isConnected && requestedRate != null && requestedRate in 0..0xFFFF,
        ) {
            Text(scopeActionLabel("FS Only", targetCount))
        }

        OutlinedButton(
            onClick = {
                val nextVbatt = vbattThresholdRaw ?: return@OutlinedButton
                val nextAudioRatio = audioRatio ?: return@OutlinedButton
                val nextCameraRatio = cameraRatio ?: return@OutlinedButton
                onApplySystemProfileToSystem(nextVbatt, nextAudioRatio, nextCameraRatio)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = session.isConnected &&
                parsed != null &&
                vbattThresholdRaw != null &&
                audioRatio != null &&
                cameraRatio != null,
        ) {
            Text(scopeActionLabel("Apply Fields", targetCount))
        }
    }
}

@Composable
private fun PocketAcquisitionDeckCard(
    scope: ControlScope,
    targetCount: Int,
    session: DeviceSessionUiState?,
    canControlScope: Boolean,
    onResync: () -> Unit,
    onReadAllParams: () -> Unit,
    compactSingleDevice: Boolean = false,
) {
    val parsed = session?.parsedSystemParams
    val scopeLabel = when (scope) {
        ControlScope.ActiveDevice -> "Active"
        ControlScope.SelectedDevices -> "Selected"
        ControlScope.AllConnected -> "All"
    }
    val cameraLabel = when {
        parsed == null -> "Not read"
        parsed?.cameraEnabled == true -> "${parsed.cameraSamplingRate} Hz"
        else -> "Off"
    }
    val adcLabel = when {
        parsed == null -> "Not read"
        parsed?.adcEnabled == true -> "${parsed.adcSamplingRate} Hz"
        else -> "Off"
    }

    ControlCard(
        title = if (compactSingleDevice) "" else "Quick Control",
        subtitle = compactUiCopy("", ""),
    ) {
        if (!compactSingleDevice) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoPill("Scope", scopeLabel, Modifier.weight(1f))
                InfoPill("Targets", targetCount.toString(), Modifier.weight(1f))
                InfoPill("Active", session?.name ?: "None", Modifier.weight(1.4f))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoPill("Sample rate", formatRateLabel(parsed?.ephysSamplingRate), Modifier.weight(1f))
            InfoPill("Camera", cameraLabel, Modifier.weight(1f))
            InfoPill("ADC", adcLabel, Modifier.weight(1f))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoPill("Ephys channels", parsed?.ephysChannelCount?.toString() ?: "Not read", Modifier.weight(1f))
            InfoPill("Firmware", session?.swVersion ?: "Not read", Modifier.weight(1f))
            InfoPill("Hardware", session?.hwVersion ?: "Not read", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val voltage = session?.voltage ?: session?.advertisedVoltage
            InfoPill("Battery", voltage?.let { "%.2f V".format(Locale.US, it) } ?: "Not reported", Modifier.weight(1f))
            InfoPill("Storage used", session?.advertisedHealthStatus?.storageUsedPercent?.let { "$it%" }
                ?: session?.usedSpaceMb?.let { "%.1f MB".format(Locale.US, it) } ?: "Not reported", Modifier.weight(1f))
            InfoPill("Schedule", when (session?.schedulerStatus?.enabled) { true -> "Enabled"; false -> "Disabled"; null -> "Not read" }, Modifier.weight(1f))
        }

        if (SHOW_UI_DESCRIPTIONS) {
            Text(
                "Resync and payload reads fan out to the scoped target set. Snapshot and preview-shot stay on the active device, while quick impedance and camera-register reads follow the current scope.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            )
        }

        FilledTonalButton(
            onClick = onResync,
            modifier = Modifier.fillMaxWidth(),
            enabled = canControlScope,
        ) {
            Text(if (compactSingleDevice) "Resync device" else "Resync selected devices")
        }

        FilledTonalButton(
            onClick = onReadAllParams,
            modifier = Modifier.fillMaxWidth(),
            enabled = canControlScope,
        ) {
            Text(if (compactSingleDevice) "Refresh device settings" else "Read All")
        }

        if (compactSingleDevice) {
            Text(
                "Use the Task menu for sampling, stimulation, signal tools, recording schedules, and firmware updates. Reading settings confirms what this device reports; its name alone does not confirm firmware support.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            )
        }
    }
}

@Composable
private fun CameraParamControlCard(
    session: DeviceSessionUiState,
    scope: ControlScope,
    targetCount: Int,
    canControlScope: Boolean,
    onReadCameraParams: () -> Unit,
    onSetCameraParams: (Int, Int) -> Unit,
    compactSingleDevice: Boolean = false,
) {
    val parsed = session.parsedCameraParams
    var reg0Text by remember(session.id) {
        mutableStateOf(parsed?.reg0?.let(::formatHexU16) ?: "0000")
    }
    var reg1Text by remember(session.id) {
        mutableStateOf(parsed?.reg1?.let(::formatHexU16) ?: "0000")
    }

    LaunchedEffect(session.id, parsed?.reg0, parsed?.reg1) {
        reg0Text = parsed?.reg0?.let(::formatHexU16) ?: reg0Text
        reg1Text = parsed?.reg1?.let(::formatHexU16) ?: reg1Text
    }

    val reg0 = parseHexU16(reg0Text)
    val reg1 = parseHexU16(reg1Text)
    val scopeLabel = when (scope) {
        ControlScope.ActiveDevice -> "Active"
        ControlScope.SelectedDevices -> "Selected"
        ControlScope.AllConnected -> "All"
    }

    ControlCard(
        title = if (compactSingleDevice) "" else "Camera Registers",
        subtitle = compactUiCopy("", ""),
    ) {
        if (!compactSingleDevice) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoPill("Scope", scopeLabel, Modifier.weight(1f))
                InfoPill("Targets", targetCount.toString(), Modifier.weight(0.8f))
                InfoPill("Source", session.name, Modifier.weight(1.4f))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoPill("Current Reg0", parsed?.reg0?.let(::formatHexU16) ?: "--", Modifier.weight(1f))
            InfoPill("Current Reg1", parsed?.reg1?.let(::formatHexU16) ?: "--", Modifier.weight(1f))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = reg0Text,
                onValueChange = { reg0Text = sanitizeHexInput(it) },
                label = { Text("Reg0") },
                modifier = Modifier.weight(1f),
                enabled = session.isConnected,
                singleLine = true,
            )
            OutlinedTextField(
                value = reg1Text,
                onValueChange = { reg1Text = sanitizeHexInput(it) },
                label = { Text("Reg1") },
                modifier = Modifier.weight(1f),
                enabled = session.isConnected,
                singleLine = true,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onReadCameraParams, modifier = Modifier.weight(1f), enabled = canControlScope) {
                Text(scopeActionLabel("Read Camera", targetCount))
            }
            FilledTonalButton(
                onClick = {
                    if (reg0 != null && reg1 != null) {
                        onSetCameraParams(reg0, reg1)
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = canControlScope && reg0 != null && reg1 != null,
            ) {
                Text(scopeActionLabel("Apply Camera", targetCount))
            }
        }
    }
}

@Composable
private fun ImpedanceDiagnosticsCard(
    session: DeviceSessionUiState,
    scope: ControlScope,
    targetCount: Int,
    canControlScope: Boolean,
    onRunImpedance: () -> Unit,
    onExportImpedance: () -> Unit,
    onShowExportPath: () -> Unit,
    compactSingleDevice: Boolean = false,
) {
    val snapshot = session.impedanceSnapshot
    val hasAnySnapshot = snapshot.magnitudeValues.isNotEmpty() ||
        snapshot.phaseValues.isNotEmpty() ||
        snapshot.crosstalkByDriveChannel.isNotEmpty()
    val updatedLabel = if (snapshot.updatedAtMs > 0L) {
        Instant.ofEpochMilli(snapshot.updatedAtMs)
            .atZone(ZoneId.systemDefault())
            .format(eventTimeFormatter)
    } else {
        "--"
    }
    val scopeLabel = when (scope) {
        ControlScope.ActiveDevice -> "Active"
        ControlScope.SelectedDevices -> "Selected"
        ControlScope.AllConnected -> "All"
    }

    ControlCard(
        title = if (compactSingleDevice) "" else "Impedance",
        subtitle = compactUiCopy("", ""),
    ) {
        if (!compactSingleDevice) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoPill("Scope", scopeLabel, Modifier.weight(1f))
                InfoPill("Targets", targetCount.toString(), Modifier.weight(0.8f))
                InfoPill("Source", session.name, Modifier.weight(1.4f))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoPill("Mag", snapshot.magnitudeValues.size.toString(), Modifier.weight(1f))
            InfoPill("Phase", snapshot.phaseValues.size.toString(), Modifier.weight(1f))
            InfoPill("XTalk", snapshot.crosstalkByDriveChannel.size.toString(), Modifier.weight(1f))
            InfoPill("Updated", updatedLabel, Modifier.weight(1f))
        }

        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(
                onClick = onRunImpedance,
                modifier = Modifier.weight(1f),
                enabled = canControlScope,
            ) {
                Text(scopeActionLabel("Impedance Test", targetCount))
            }
            OutlinedButton(
                onClick = onExportImpedance,
                modifier = Modifier.weight(1f),
                enabled = hasAnySnapshot,
            ) {
                Text("Save CSV")
            }
        }

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = onShowExportPath,
            modifier = Modifier.fillMaxWidth(),
            enabled = hasAnySnapshot || session.isConnected,
        ) {
            Text("CSV Path")
        }

        if (session.impedanceExportPath.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "CSV folder: ${compactPathLabel(session.impedanceExportPath)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
            )
        }

        Spacer(Modifier.height(10.dp))

        if (!hasAnySnapshot) {
            if (!SHOW_UI_DESCRIPTIONS || compactSingleDevice) {
                return@ControlCard
            }
            Text(
                "Run an impedance test to capture magnitude, crosstalk, and phase packets for the active device view. Multi-device runs still write results back into each scoped session.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
        } else {
            snapshot.magnitudeValues.takeIf { it.isNotEmpty() }?.let { values ->
                Text(
                    "Magnitude: ${formatImpedancePreview(values)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                )
            }

            snapshot.phaseValues.takeIf { it.isNotEmpty() }?.let { values ->
                Spacer(Modifier.height(6.dp))
                Text(
                    "Phase: ${formatImpedancePreview(values)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                )
            }

            snapshot.crosstalkByDriveChannel.takeIf { it.isNotEmpty() }?.let { columns ->
                Spacer(Modifier.height(6.dp))
                Text(
                    "Crosstalk: ${formatCrosstalkSummary(columns)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                )
            }
        }
    }
}

@Composable
private fun AutoImpedanceCard(
    session: DeviceSessionUiState,
    running: Boolean,
    scheduledDeviceId: String?,
    scheduledDeviceName: String,
    intervalMinutes: Int,
    nextRunAtMs: Long?,
    statusMessage: String,
    onStartAuto: (Int) -> Unit,
    onStopAuto: () -> Unit,
) {
    var intervalText by rememberSaveable(session.id, scheduledDeviceId, intervalMinutes) {
        mutableStateOf(intervalMinutes.toString())
    }
    val parsedIntervalMinutes = intervalText.toIntOrNull()?.coerceIn(1, 24 * 60)
    val runningHere = running && scheduledDeviceId == session.id
    val targetLabel = when {
        scheduledDeviceName.isNotBlank() -> scheduledDeviceName
        !scheduledDeviceId.isNullOrBlank() -> scheduledDeviceId
        else -> session.name
    }
    val nextRunLabel = when {
        running && nextRunAtMs == null -> "Running"
        nextRunAtMs != null -> formatEventTime(nextRunAtMs)
        else -> "--"
    }
    val helperText = when {
        runningHere -> "Scheduled sweeps are armed on this device. Each pass runs BLE impedance and auto-saves CSV output."
        running -> "A schedule is already running on $targetLabel. Starting here will move the schedule to ${session.name}."
        session.isConnected -> "Arm repeated BLE impedance sweeps for the active device and keep exports in the app files area."
        else -> "The scheduler will reconnect ${session.name} if needed before each BLE impedance sweep."
    }
    val statusLine = statusMessage.ifBlank { helperText }

    ControlCard(
        title = "Auto Impedance",
        subtitle = compactUiCopy("", ""),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoPill("Target", targetLabel, Modifier.weight(1f))
            InfoPill("Every", "${intervalMinutes} min", Modifier.weight(1f))
            InfoPill("Next", nextRunLabel, Modifier.weight(1f))
        }

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = intervalText,
            onValueChange = { intervalText = sanitizeIntInput(it).take(4) },
            label = { Text("Interval (min)") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )

        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(
                onClick = { parsedIntervalMinutes?.let(onStartAuto) },
                modifier = Modifier.weight(1f),
                enabled = parsedIntervalMinutes != null && (session.bulkConnectEligible || session.isConnected),
            ) {
                Text(
                    when {
                        runningHere -> "Update Schedule"
                        running -> "Move Here"
                        else -> "Start Auto"
                    }
                )
            }
            OutlinedButton(
                onClick = onStopAuto,
                modifier = Modifier.weight(1f),
                enabled = running,
            ) {
                Text("Stop Auto")
            }
        }

        Spacer(Modifier.height(10.dp))

        Text(
            statusLine,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
        )
    }
}

@Composable
private fun GpioModeSelectorRow(
    title: String,
    currentLabel: String,
    selectedMode: GpioMode?,
    enabled: Boolean,
    onSelectMode: (GpioMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                currentLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            )
        }

        PocketButtonGrid(
            options = GpioMode.SelectableModes.map { mode ->
                PocketButtonSpec(
                    label = mode.label,
                    onClick = { onSelectMode(mode) },
                    enabled = enabled,
                    selected = selectedMode == mode,
                )
            },
            columns = 2,
        )
    }
}

@Composable
private fun ClosedLoopQuickControlCard(
    session: DeviceSessionUiState,
    scope: ControlScope,
    targetCount: Int,
    canControlScope: Boolean,
    onSetStimEnabled: (Boolean) -> Unit,
    onSetTriggerGain: (Int, Float) -> Unit,
    onSetStimIntensity: (Int, Float) -> Unit,
    onSetTriggerThreshold: (Int, Float) -> Unit,
    onForceTrigger: (Int) -> Unit,
    compactSingleDevice: Boolean = false,
) {
    val stim = session.stimControlStatus
    val systemParams = session.parsedSystemParams
    var gain1Text by remember(session.id) { mutableStateOf(stim?.takeIf { it.id == 0 }?.triggerGain?.format(2) ?: "1.00") }
    var gain2Text by remember(session.id) { mutableStateOf(stim?.takeIf { it.id == 1 }?.triggerGain?.format(2) ?: "1.00") }
    var threshold1Text by remember(session.id) { mutableStateOf(stim?.takeIf { it.id == 0 }?.triggerLevel?.format(2) ?: "0.00") }
    var threshold2Text by remember(session.id) { mutableStateOf(stim?.takeIf { it.id == 1 }?.triggerLevel?.format(2) ?: "0.00") }
    var intensity1Text by remember(session.id) { mutableStateOf(systemParams?.stimIntensityPercent(0)?.format(0) ?: "50") }
    var intensity2Text by remember(session.id) { mutableStateOf(systemParams?.stimIntensityPercent(1)?.format(0) ?: "50") }
    var stimEnabled by remember(session.id) { mutableStateOf(systemParams?.stimEnabled ?: false) }

    LaunchedEffect(session.id, stim?.id, stim?.triggerGain, stim?.triggerLevel, systemParams?.stimMode, systemParams?.stimIntensities) {
        when (stim?.id) {
            0 -> {
                gain1Text = stim.triggerGain.format(2)
                threshold1Text = stim.triggerLevel.format(2)
            }
            1 -> {
                gain2Text = stim.triggerGain.format(2)
                threshold2Text = stim.triggerLevel.format(2)
            }
        }
        if (systemParams != null) {
            intensity1Text = systemParams.stimIntensityPercent(0).format(0)
            intensity2Text = systemParams.stimIntensityPercent(1).format(0)
            stimEnabled = systemParams.stimEnabled
        } else {
            stimEnabled = stim?.stimCount?.let { it > 0 } ?: stimEnabled
        }
    }

    val gain1 = gain1Text.toFloatOrNull()
    val gain2 = gain2Text.toFloatOrNull()
    val threshold1 = threshold1Text.toFloatOrNull()
    val threshold2 = threshold2Text.toFloatOrNull()
    val intensity1 = intensity1Text.toFloatOrNull()
    val intensity2 = intensity2Text.toFloatOrNull()
    var expanded by rememberSaveable(session.id) { mutableStateOf(false) }
    val scopeLabel = when (scope) {
        ControlScope.ActiveDevice -> "Active"
        ControlScope.SelectedDevices -> "Selected"
        ControlScope.AllConnected -> "All"
    }

    ControlCard(
        title = if (compactSingleDevice) "" else "Closed-Loop Quick Controls",
        subtitle = compactUiCopy("", ""),
    ) {
        if (!compactSingleDevice) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoPill("Scope", scopeLabel, Modifier.weight(1f))
                InfoPill("Targets", targetCount.toString(), Modifier.weight(0.8f))
                InfoPill("Source", session.name, Modifier.weight(1.4f))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoPill("Stim", if (stimEnabled) "On" else "Off", Modifier.weight(1f))
            InfoPill("Ch1", "G $gain1Text  I $intensity1Text%", Modifier.weight(1f))
            InfoPill("Ch2", "G $gain2Text  I $intensity2Text%", Modifier.weight(1f))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(
                onClick = {
                    val next = !stimEnabled
                    stimEnabled = next
                    onSetStimEnabled(next)
                },
                modifier = Modifier.weight(1f),
                enabled = canControlScope,
            ) {
                Text(scopeActionLabel(if (stimEnabled) "Disable Stim" else "Enable Stim", targetCount))
            }
            OutlinedButton(
                onClick = { onForceTrigger(0) },
                modifier = Modifier.weight(1f),
                enabled = canControlScope,
            ) {
                Text(scopeActionLabel("Force Trig 1", targetCount))
            }
            OutlinedButton(
                onClick = { onForceTrigger(1) },
                modifier = Modifier.weight(1f),
                enabled = canControlScope,
            ) {
                Text(scopeActionLabel("Force Trig 2", targetCount))
            }
        }

        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.align(Alignment.Start),
        ) {
            Text(if (expanded) "Hide Channel Editors" else "Show Channel Editors")
        }

        if (expanded) {
            ClosedLoopChannelEditor(
                title = "Channel 1",
                gainText = gain1Text,
                onGainTextChange = { gain1Text = it },
                thresholdText = threshold1Text,
                onThresholdTextChange = { threshold1Text = it },
                intensityText = intensity1Text,
                onIntensityTextChange = { intensity1Text = it },
                enabled = canControlScope,
                applyLabel = scopeActionLabel("Apply", targetCount),
                onApply = {
                    if (gain1 != null) onSetTriggerGain(0, gain1)
                    if (threshold1 != null) onSetTriggerThreshold(0, threshold1)
                    if (intensity1 != null) onSetStimIntensity(0, intensity1)
                },
            )

            ClosedLoopChannelEditor(
                title = "Channel 2",
                gainText = gain2Text,
                onGainTextChange = { gain2Text = it },
                thresholdText = threshold2Text,
                onThresholdTextChange = { threshold2Text = it },
                intensityText = intensity2Text,
                onIntensityTextChange = { intensity2Text = it },
                enabled = canControlScope,
                applyLabel = scopeActionLabel("Apply", targetCount),
                onApply = {
                    if (gain2 != null) onSetTriggerGain(1, gain2)
                    if (threshold2 != null) onSetTriggerThreshold(1, threshold2)
                    if (intensity2 != null) onSetStimIntensity(1, intensity2)
                },
            )
        }
    }
}

@Composable
private fun AdvancedClosedLoopCard(
    session: DeviceSessionUiState,
    scope: ControlScope,
    targetCount: Int,
    canControlScope: Boolean,
    onSetStimParams: (Int, Float, Float, Float, Float, Int) -> Unit,
    onSetDspLiveParams: (Int, Int, Int, Int, List<Int>) -> Unit,
    compactSingleDevice: Boolean = false,
) {
    val sys = session.parsedSystemParams
    val dsp0 = session.parsedDsp1Params
    val dsp1 = session.parsedDsp2Params

    var stim1Delay by remember(session.id) { mutableStateOf(sys?.let { it.timingUnits(0, it.stimDelays).format(1) } ?: "0.0") }
    var stim1Random by remember(session.id) { mutableStateOf(sys?.let { it.timingUnits(0, it.stimRandomDelays).format(1) } ?: "0.0") }
    var stim1Width by remember(session.id) { mutableStateOf(sys?.let { it.timingUnits(0, it.pulseWidths).format(1) } ?: "0.0") }
    var stim1Interval by remember(session.id) { mutableStateOf(sys?.let { it.timingUnits(0, it.stimIntervals).format(1) } ?: "0.0") }
    var stim1Cycles by remember(session.id) { mutableStateOf(sys?.pulseCounts?.getOrNull(0)?.toString() ?: "0") }
    var stim2Delay by remember(session.id) { mutableStateOf(sys?.let { it.timingUnits(1, it.stimDelays).format(1) } ?: "0.0") }
    var stim2Random by remember(session.id) { mutableStateOf(sys?.let { it.timingUnits(1, it.stimRandomDelays).format(1) } ?: "0.0") }
    var stim2Width by remember(session.id) { mutableStateOf(sys?.let { it.timingUnits(1, it.pulseWidths).format(1) } ?: "0.0") }
    var stim2Interval by remember(session.id) { mutableStateOf(sys?.let { it.timingUnits(1, it.stimIntervals).format(1) } ?: "0.0") }
    var stim2Cycles by remember(session.id) { mutableStateOf(sys?.pulseCounts?.getOrNull(1)?.toString() ?: "0") }

    var dsp0Ma by remember(session.id) { mutableStateOf(dsp0?.maOrder?.toString() ?: "0") }
    var dsp0Filter by remember(session.id) { mutableStateOf(dsp0?.filterType?.toString() ?: "0") }
    var dsp0Formula by remember(session.id) { mutableStateOf(dsp0?.formula?.toString() ?: "0") }
    var dsp0ChA by remember(session.id) { mutableStateOf(dspChannelInputValue(dsp0?.channels, 0)) }
    var dsp0ChB by remember(session.id) { mutableStateOf(dspChannelInputValue(dsp0?.channels, 1)) }
    var dsp0ChC by remember(session.id) { mutableStateOf(dspChannelInputValue(dsp0?.channels, 2)) }
    var dsp0ChD by remember(session.id) { mutableStateOf(dspChannelInputValue(dsp0?.channels, 3)) }
    var dsp1Ma by remember(session.id) { mutableStateOf(dsp1?.maOrder?.toString() ?: "0") }
    var dsp1Filter by remember(session.id) { mutableStateOf(dsp1?.filterType?.toString() ?: "0") }
    var dsp1Formula by remember(session.id) { mutableStateOf(dsp1?.formula?.toString() ?: "0") }
    var dsp1ChA by remember(session.id) { mutableStateOf(dspChannelInputValue(dsp1?.channels, 0)) }
    var dsp1ChB by remember(session.id) { mutableStateOf(dspChannelInputValue(dsp1?.channels, 1)) }
    var dsp1ChC by remember(session.id) { mutableStateOf(dspChannelInputValue(dsp1?.channels, 2)) }
    var dsp1ChD by remember(session.id) { mutableStateOf(dspChannelInputValue(dsp1?.channels, 3)) }

    LaunchedEffect(session.id, sys?.stimDelays, sys?.stimRandomDelays, sys?.pulseWidths, sys?.stimIntervals, sys?.pulseCounts) {
        if (sys != null) {
            stim1Delay = sys.timingUnits(0, sys.stimDelays).format(1)
            stim1Random = sys.timingUnits(0, sys.stimRandomDelays).format(1)
            stim1Width = sys.timingUnits(0, sys.pulseWidths).format(1)
            stim1Interval = sys.timingUnits(0, sys.stimIntervals).format(1)
            stim1Cycles = sys.pulseCounts.getOrElse(0) { 0 }.toString()
            stim2Delay = sys.timingUnits(1, sys.stimDelays).format(1)
            stim2Random = sys.timingUnits(1, sys.stimRandomDelays).format(1)
            stim2Width = sys.timingUnits(1, sys.pulseWidths).format(1)
            stim2Interval = sys.timingUnits(1, sys.stimIntervals).format(1)
            stim2Cycles = sys.pulseCounts.getOrElse(1) { 0 }.toString()
        }
    }

    LaunchedEffect(session.id, dsp0?.maOrder, dsp0?.filterType, dsp0?.formula, dsp0?.channels) {
        if (dsp0 != null) {
            dsp0Ma = dsp0.maOrder.toString()
            dsp0Filter = dsp0.filterType.toString()
            dsp0Formula = dsp0.formula.toString()
            dsp0ChA = dspChannelInputValue(dsp0.channels, 0)
            dsp0ChB = dspChannelInputValue(dsp0.channels, 1)
            dsp0ChC = dspChannelInputValue(dsp0.channels, 2)
            dsp0ChD = dspChannelInputValue(dsp0.channels, 3)
        }
    }

    LaunchedEffect(session.id, dsp1?.maOrder, dsp1?.filterType, dsp1?.formula, dsp1?.channels) {
        if (dsp1 != null) {
            dsp1Ma = dsp1.maOrder.toString()
            dsp1Filter = dsp1.filterType.toString()
            dsp1Formula = dsp1.formula.toString()
            dsp1ChA = dspChannelInputValue(dsp1.channels, 0)
            dsp1ChB = dspChannelInputValue(dsp1.channels, 1)
            dsp1ChC = dspChannelInputValue(dsp1.channels, 2)
            dsp1ChD = dspChannelInputValue(dsp1.channels, 3)
        }
    }
    val scopeLabel = when (scope) {
        ControlScope.ActiveDevice -> "Active"
        ControlScope.SelectedDevices -> "Selected"
        ControlScope.AllConnected -> "All"
    }

    ControlCard(
        title = if (compactSingleDevice) "" else "Advanced Closed-Loop",
        subtitle = compactUiCopy("", ""),
    ) {
        if (!compactSingleDevice) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoPill("Scope", scopeLabel, Modifier.weight(1f))
                InfoPill("Targets", targetCount.toString(), Modifier.weight(0.8f))
                InfoPill("Source", session.name, Modifier.weight(1.4f))
            }
        }

        if (sys == null) {
            Text(
                "Read system params first to prefill advanced stimulation settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
        }

        StimParamEditor(
            title = "Stim 1",
            delayText = stim1Delay,
            onDelayChange = { stim1Delay = sanitizeFloatInput(it) },
            randomDelayText = stim1Random,
            onRandomDelayChange = { stim1Random = sanitizeFloatInput(it) },
            widthText = stim1Width,
            onWidthChange = { stim1Width = sanitizeFloatInput(it) },
            intervalText = stim1Interval,
            onIntervalChange = { stim1Interval = sanitizeFloatInput(it) },
            cyclesText = stim1Cycles,
            onCyclesChange = { stim1Cycles = sanitizeIntInput(it) },
            enabled = canControlScope,
            applyLabel = scopeActionLabel("Apply Stim 1", targetCount),
            onApply = {
                val delay = stim1Delay.toFloatOrNull()
                val random = stim1Random.toFloatOrNull()
                val width = stim1Width.toFloatOrNull()
                val interval = stim1Interval.toFloatOrNull()
                val cycles = stim1Cycles.toIntOrNull()
                if (delay != null && random != null && width != null && interval != null && cycles != null) {
                    onSetStimParams(0, delay, random, width, interval, cycles)
                }
            },
        )

        StimParamEditor(
            title = "Stim 2",
            delayText = stim2Delay,
            onDelayChange = { stim2Delay = sanitizeFloatInput(it) },
            randomDelayText = stim2Random,
            onRandomDelayChange = { stim2Random = sanitizeFloatInput(it) },
            widthText = stim2Width,
            onWidthChange = { stim2Width = sanitizeFloatInput(it) },
            intervalText = stim2Interval,
            onIntervalChange = { stim2Interval = sanitizeFloatInput(it) },
            cyclesText = stim2Cycles,
            onCyclesChange = { stim2Cycles = sanitizeIntInput(it) },
            enabled = canControlScope,
            applyLabel = scopeActionLabel("Apply Stim 2", targetCount),
            onApply = {
                val delay = stim2Delay.toFloatOrNull()
                val random = stim2Random.toFloatOrNull()
                val width = stim2Width.toFloatOrNull()
                val interval = stim2Interval.toFloatOrNull()
                val cycles = stim2Cycles.toIntOrNull()
                if (delay != null && random != null && width != null && interval != null && cycles != null) {
                    onSetStimParams(1, delay, random, width, interval, cycles)
                }
            },
        )

        DspLiveEditor(
            title = "DSP 1 Live",
            maText = dsp0Ma,
            onMaChange = { dsp0Ma = sanitizeIntInput(it) },
            filterText = dsp0Filter,
            onFilterChange = { dsp0Filter = sanitizeIntInput(it) },
            formulaText = dsp0Formula,
            onFormulaChange = { dsp0Formula = sanitizeIntInput(it) },
            chAText = dsp0ChA,
            onChAChange = { dsp0ChA = sanitizeIntInput(it) },
            chBText = dsp0ChB,
            onChBChange = { dsp0ChB = sanitizeIntInput(it) },
            chCText = dsp0ChC,
            onChCChange = { dsp0ChC = sanitizeIntInput(it) },
            chDText = dsp0ChD,
            onChDChange = { dsp0ChD = sanitizeIntInput(it) },
            enabled = canControlScope,
            applyLabel = scopeActionLabel("Apply DSP 1 Live", targetCount),
            onApply = {
                val ma = dsp0Ma.toIntOrNull()
                val filter = dsp0Filter.toIntOrNull()
                val formula = dsp0Formula.toIntOrNull()
                val chA = dspChannelInputToZeroBased(dsp0ChA)
                val chB = dspChannelInputToZeroBased(dsp0ChB)
                val chC = dspChannelInputToZeroBased(dsp0ChC)
                val chD = dspChannelInputToZeroBased(dsp0ChD)
                if (ma != null && filter != null && formula != null && chA != null && chB != null && chC != null && chD != null) {
                    onSetDspLiveParams(0, ma, filter, formula, listOf(chA, chB, chC, chD))
                }
            },
        )

        DspLiveEditor(
            title = "DSP 2 Live",
            maText = dsp1Ma,
            onMaChange = { dsp1Ma = sanitizeIntInput(it) },
            filterText = dsp1Filter,
            onFilterChange = { dsp1Filter = sanitizeIntInput(it) },
            formulaText = dsp1Formula,
            onFormulaChange = { dsp1Formula = sanitizeIntInput(it) },
            chAText = dsp1ChA,
            onChAChange = { dsp1ChA = sanitizeIntInput(it) },
            chBText = dsp1ChB,
            onChBChange = { dsp1ChB = sanitizeIntInput(it) },
            chCText = dsp1ChC,
            onChCChange = { dsp1ChC = sanitizeIntInput(it) },
            chDText = dsp1ChD,
            onChDChange = { dsp1ChD = sanitizeIntInput(it) },
            enabled = canControlScope,
            applyLabel = scopeActionLabel("Apply DSP 2 Live", targetCount),
            onApply = {
                val ma = dsp1Ma.toIntOrNull()
                val filter = dsp1Filter.toIntOrNull()
                val formula = dsp1Formula.toIntOrNull()
                val chA = dspChannelInputToZeroBased(dsp1ChA)
                val chB = dspChannelInputToZeroBased(dsp1ChB)
                val chC = dspChannelInputToZeroBased(dsp1ChC)
                val chD = dspChannelInputToZeroBased(dsp1ChD)
                if (ma != null && filter != null && formula != null && chA != null && chB != null && chC != null && chD != null) {
                    onSetDspLiveParams(1, ma, filter, formula, listOf(chA, chB, chC, chD))
                }
            },
        )
    }
}

private fun dspChannelInputValue(channels: List<Int>?, index: Int): String {
    val zeroBased = channels?.getOrNull(index)
    return zeroBased?.takeIf { it in 0..63 }?.plus(1)?.toString() ?: "1"
}

private fun dspChannelInputToZeroBased(value: String): Int? {
    return value.toIntOrNull()?.takeIf { it in 1..64 }?.minus(1)
}

@Composable
private fun ClosedLoopProfileCard(
    session: DeviceSessionUiState,
    scope: ControlScope,
    targetCount: Int,
    systemReadyCount: Int,
    fullPayloadReadyCount: Int,
    canControlScope: Boolean,
    onApplyProfileToSystem: (Int, Int, Int, Int, Int, List<Float>, List<Float>, List<Int>) -> Unit,
    onApplyProfileToAll: (Int, Int, Int, Int, Int, List<Float>, List<Float>, List<Int>) -> Unit,
    compactSingleDevice: Boolean = false,
) {
    val sys = session.parsedSystemParams
    var modeValue by remember(session.id) { mutableStateOf(sys?.closedLoopMode ?: 0) }
    var trainStartText by remember(session.id) { mutableStateOf(sys?.triggerTrainStart?.toString() ?: "0") }
    var trainDurationText by remember(session.id) { mutableStateOf(sys?.triggerTrainDuration?.toString() ?: "0") }
    var randomMinText by remember(session.id) { mutableStateOf(sys?.randomTriggerMin?.toString() ?: "0") }
    var randomMaxText by remember(session.id) { mutableStateOf(sys?.randomTriggerMax?.toString() ?: "0") }
    var paramA1Text by remember(session.id) { mutableStateOf(sys?.clParam1?.getOrNull(0)?.format(3) ?: "0.000") }
    var paramA2Text by remember(session.id) { mutableStateOf(sys?.clParam2?.getOrNull(0)?.format(3) ?: "0.000") }
    var paramB1Text by remember(session.id) { mutableStateOf(sys?.clParam1?.getOrNull(1)?.format(3) ?: "0.000") }
    var paramB2Text by remember(session.id) { mutableStateOf(sys?.clParam2?.getOrNull(1)?.format(3) ?: "0.000") }
    var cl1Stim1 by remember(session.id) { mutableStateOf(sys?.stimChannels?.getOrNull(0)?.and(0x01) != 0) }
    var cl1Stim2 by remember(session.id) { mutableStateOf(sys?.stimChannels?.getOrNull(0)?.and(0x02) != 0) }
    var cl2Stim1 by remember(session.id) { mutableStateOf(sys?.stimChannels?.getOrNull(1)?.and(0x01) != 0) }
    var cl2Stim2 by remember(session.id) { mutableStateOf(sys?.stimChannels?.getOrNull(1)?.and(0x02) != 0) }

    LaunchedEffect(
        session.id,
        sys?.closedLoopMode,
        sys?.triggerTrainStart,
        sys?.triggerTrainDuration,
        sys?.randomTriggerMin,
        sys?.randomTriggerMax,
        sys?.clParam1,
        sys?.clParam2,
        sys?.stimChannels,
    ) {
        if (sys != null) {
            modeValue = sys.closedLoopMode
            trainStartText = sys.triggerTrainStart.toString()
            trainDurationText = sys.triggerTrainDuration.toString()
            randomMinText = sys.randomTriggerMin.toString()
            randomMaxText = sys.randomTriggerMax.toString()
            paramA1Text = sys.clParam1.getOrElse(0) { 0f }.format(3)
            paramA2Text = sys.clParam2.getOrElse(0) { 0f }.format(3)
            paramB1Text = sys.clParam1.getOrElse(1) { 0f }.format(3)
            paramB2Text = sys.clParam2.getOrElse(1) { 0f }.format(3)
            cl1Stim1 = sys.stimChannels.getOrElse(0) { 0 } and 0x01 != 0
            cl1Stim2 = sys.stimChannels.getOrElse(0) { 0 } and 0x02 != 0
            cl2Stim1 = sys.stimChannels.getOrElse(1) { 0 } and 0x01 != 0
            cl2Stim2 = sys.stimChannels.getOrElse(1) { 0 } and 0x02 != 0
        }
    }

    val modeLabel = closedLoopModeOptions.firstOrNull { it.value == modeValue }?.label ?: "Mode $modeValue"
    val scopeLabel = when (scope) {
        ControlScope.ActiveDevice -> "Active"
        ControlScope.SelectedDevices -> "Selected"
        ControlScope.AllConnected -> "All"
    }
    fun buildProfileInputs(): ClosedLoopProfileInputs? {
        val start = trainStartText.toIntOrNull()
        val duration = trainDurationText.toIntOrNull()
        val randMin = randomMinText.toIntOrNull()
        val randMax = randomMaxText.toIntOrNull()
        val a1 = paramA1Text.toFloatOrNull()
        val a2 = paramA2Text.toFloatOrNull()
        val b1 = paramB1Text.toFloatOrNull()
        val b2 = paramB2Text.toFloatOrNull()
        if (sys == null || start == null || duration == null || randMin == null || randMax == null || a1 == null || a2 == null || b1 == null || b2 == null) {
            return null
        }

        val nextStimChannels = sys.stimChannels.toMutableList()
        if (nextStimChannels.isEmpty()) {
            nextStimChannels.addAll(listOf(0, 0, 0, 0))
        }
        while (nextStimChannels.size < 4) {
            nextStimChannels.add(0)
        }
        nextStimChannels[0] = (if (cl1Stim1) 0x01 else 0) or (if (cl1Stim2) 0x02 else 0)
        nextStimChannels[1] = (if (cl2Stim1) 0x01 else 0) or (if (cl2Stim2) 0x02 else 0)

        val nextClParam1 = sys.clParam1.toMutableList()
        val nextClParam2 = sys.clParam2.toMutableList()
        if (nextClParam1.isEmpty()) nextClParam1.addAll(listOf(0f, 0f, 0f, 0f))
        if (nextClParam2.isEmpty()) nextClParam2.addAll(listOf(0f, 0f, 0f, 0f))
        while (nextClParam1.size < 4) nextClParam1.add(0f)
        while (nextClParam2.size < 4) nextClParam2.add(0f)
        nextClParam1[0] = a1
        nextClParam2[0] = a2
        nextClParam1[1] = b1
        nextClParam2[1] = b2

        return ClosedLoopProfileInputs(
            closedLoopMode = modeValue,
            triggerTrainStart = start,
            triggerTrainDuration = duration,
            randomTriggerMin = randMin,
            randomTriggerMax = randMax,
            clParam1 = nextClParam1,
            clParam2 = nextClParam2,
            stimChannels = nextStimChannels,
        )
    }

    ControlCard(
        title = if (compactSingleDevice) "" else "Closed-Loop Profile",
        subtitle = compactUiCopy("", ""),
    ) {
        if (!compactSingleDevice) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoPill("Scope", scopeLabel, Modifier.weight(1f))
                InfoPill("Targets", targetCount.toString(), Modifier.weight(0.8f))
                InfoPill("Source", session.name, Modifier.weight(1.4f))
            }
        }

        if (sys == null) {
            Text(
                "Read system params first to populate the profile editor.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
        }

        InfoPill("Selected Mode", modeLabel, Modifier.fillMaxWidth())

        Text(
            "Closed-Loop Mode",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        )
        PocketButtonGrid(
            options = closedLoopModeOptions.map { option ->
                PocketButtonSpec(
                    label = option.label,
                    onClick = { modeValue = option.value },
                    enabled = canControlScope && sys != null,
                    selected = modeValue == option.value,
                )
            },
            columns = 2,
        )

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Stim Routing", fontWeight = FontWeight.SemiBold)
                PocketButtonGrid(
                    options = listOf(
                        PocketButtonSpec(
                            label = "CL1 -> STIM1",
                            onClick = { cl1Stim1 = !cl1Stim1 },
                            enabled = canControlScope && sys != null,
                            selected = cl1Stim1,
                        ),
                        PocketButtonSpec(
                            label = "CL1 -> STIM2",
                            onClick = { cl1Stim2 = !cl1Stim2 },
                            enabled = canControlScope && sys != null,
                            selected = cl1Stim2,
                        ),
                        PocketButtonSpec(
                            label = "CL2 -> STIM1",
                            onClick = { cl2Stim1 = !cl2Stim1 },
                            enabled = canControlScope && sys != null,
                            selected = cl2Stim1,
                        ),
                        PocketButtonSpec(
                            label = "CL2 -> STIM2",
                            onClick = { cl2Stim2 = !cl2Stim2 },
                            enabled = canControlScope && sys != null,
                            selected = cl2Stim2,
                        ),
                    ),
                    columns = 2,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = trainStartText,
                onValueChange = { trainStartText = sanitizeIntInput(it) },
                label = { Text("Train Start") },
                modifier = Modifier.weight(1f),
                enabled = canControlScope && sys != null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                value = trainDurationText,
                onValueChange = { trainDurationText = sanitizeIntInput(it) },
                label = { Text("Train Duration") },
                modifier = Modifier.weight(1f),
                enabled = canControlScope && sys != null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = randomMinText,
                onValueChange = { randomMinText = sanitizeIntInput(it) },
                label = { Text("Rand Min Raw") },
                modifier = Modifier.weight(1f),
                enabled = canControlScope && sys != null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                value = randomMaxText,
                onValueChange = { randomMaxText = sanitizeIntInput(it) },
                label = { Text("Rand Max Raw") },
                modifier = Modifier.weight(1f),
                enabled = canControlScope && sys != null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = paramA1Text,
                onValueChange = { paramA1Text = sanitizeFloatInput(it) },
                label = { Text("A1 Raw") },
                modifier = Modifier.weight(1f),
                enabled = canControlScope && sys != null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            OutlinedTextField(
                value = paramA2Text,
                onValueChange = { paramA2Text = sanitizeFloatInput(it) },
                label = { Text("A2 Raw") },
                modifier = Modifier.weight(1f),
                enabled = canControlScope && sys != null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = paramB1Text,
                onValueChange = { paramB1Text = sanitizeFloatInput(it) },
                label = { Text("B1 Raw") },
                modifier = Modifier.weight(1f),
                enabled = canControlScope && sys != null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            OutlinedTextField(
                value = paramB2Text,
                onValueChange = { paramB2Text = sanitizeFloatInput(it) },
                label = { Text("B2 Raw") },
                modifier = Modifier.weight(1f),
                enabled = canControlScope && sys != null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = {
                    val inputs = buildProfileInputs() ?: return@OutlinedButton
                    onApplyProfileToSystem(
                        inputs.closedLoopMode,
                        inputs.triggerTrainStart,
                        inputs.triggerTrainDuration,
                        inputs.randomTriggerMin,
                        inputs.randomTriggerMax,
                        inputs.clParam1,
                        inputs.clParam2,
                        inputs.stimChannels,
                    )
                },
                modifier = Modifier.weight(1f),
                enabled = canControlScope && sys != null && systemReadyCount > 0,
            ) {
                Text(scopeReadyActionLabel("Upload Params", systemReadyCount, targetCount))
            }
            OutlinedButton(
                onClick = {
                    val inputs = buildProfileInputs() ?: return@OutlinedButton
                    onApplyProfileToAll(
                        inputs.closedLoopMode,
                        inputs.triggerTrainStart,
                        inputs.triggerTrainDuration,
                        inputs.randomTriggerMin,
                        inputs.randomTriggerMax,
                        inputs.clParam1,
                        inputs.clParam2,
                        inputs.stimChannels,
                    )
                },
                modifier = Modifier.weight(1f),
                enabled = canControlScope && sys != null && fullPayloadReadyCount > 0,
            ) {
                Text(scopeReadyActionLabel("Upload All", fullPayloadReadyCount, targetCount))
            }
        }
    }
}

private data class ClosedLoopProfileInputs(
    val closedLoopMode: Int,
    val triggerTrainStart: Int,
    val triggerTrainDuration: Int,
    val randomTriggerMin: Int,
    val randomTriggerMax: Int,
    val clParam1: List<Float>,
    val clParam2: List<Float>,
    val stimChannels: List<Int>,
)

@Composable
private fun StimParamEditor(
    title: String,
    delayText: String,
    onDelayChange: (String) -> Unit,
    randomDelayText: String,
    onRandomDelayChange: (String) -> Unit,
    widthText: String,
    onWidthChange: (String) -> Unit,
    intervalText: String,
    onIntervalChange: (String) -> Unit,
    cyclesText: String,
    onCyclesChange: (String) -> Unit,
    enabled: Boolean,
    applyLabel: String,
    onApply: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = delayText,
                    onValueChange = onDelayChange,
                    label = { Text("Delay") },
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = randomDelayText,
                    onValueChange = onRandomDelayChange,
                    label = { Text("Rnd") },
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = widthText,
                    onValueChange = onWidthChange,
                    label = { Text("Width") },
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = intervalText,
                    onValueChange = onIntervalChange,
                    label = { Text("Interval") },
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = cyclesText,
                    onValueChange = onCyclesChange,
                    label = { Text("Cycles") },
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            FilledTonalButton(
                onClick = onApply,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
            ) {
                Text(applyLabel)
            }
        }
    }
}

@Composable
private fun DspLiveEditor(
    title: String,
    maText: String,
    onMaChange: (String) -> Unit,
    filterText: String,
    onFilterChange: (String) -> Unit,
    formulaText: String,
    onFormulaChange: (String) -> Unit,
    chAText: String,
    onChAChange: (String) -> Unit,
    chBText: String,
    onChBChange: (String) -> Unit,
    chCText: String,
    onChCChange: (String) -> Unit,
    chDText: String,
    onChDChange: (String) -> Unit,
    enabled: Boolean,
    applyLabel: String,
    onApply: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = maText,
                    onValueChange = onMaChange,
                    label = { Text("MA") },
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = filterText,
                    onValueChange = onFilterChange,
                    label = { Text("Filter") },
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = formulaText,
                    onValueChange = onFormulaChange,
                    label = { Text("Formula") },
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            Text(
                "Input channel map (A-D, 1-64)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = chAText,
                    onValueChange = onChAChange,
                    label = { Text("A") },
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = chBText,
                    onValueChange = onChBChange,
                    label = { Text("B") },
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = chCText,
                    onValueChange = onChCChange,
                    label = { Text("C") },
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = chDText,
                    onValueChange = onChDChange,
                    label = { Text("D") },
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            FilledTonalButton(
                onClick = onApply,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
            ) {
                Text(applyLabel)
            }
        }
    }
}

@Composable
private fun ClosedLoopChannelEditor(
    title: String,
    gainText: String,
    onGainTextChange: (String) -> Unit,
    thresholdText: String,
    onThresholdTextChange: (String) -> Unit,
    intensityText: String,
    onIntensityTextChange: (String) -> Unit,
    enabled: Boolean,
    applyLabel: String,
    onApply: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = gainText,
                    onValueChange = onGainTextChange,
                    label = { Text("Gain") },
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = thresholdText,
                    onValueChange = onThresholdTextChange,
                    label = { Text("Threshold") },
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = intensityText,
                    onValueChange = onIntensityTextChange,
                    label = { Text("Intensity %") },
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                FilledTonalButton(
                    onClick = onApply,
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                ) {
                    Text(applyLabel)
                }
            }
        }
    }
}

@Composable
private fun PocketSystemDeckCard(
    session: DeviceSessionUiState,
    scope: ControlScope,
    targetCount: Int,
    systemReadyCount: Int,
    dsp1ReadyCount: Int,
    dsp2ReadyCount: Int,
    fullPayloadReadyCount: Int,
    activeRoleIdentity: String,
    onUploadSystemParams: () -> Unit,
    onUploadDspParams: (Int) -> Unit,
    onUploadAllParams: () -> Unit,
    compactSingleDevice: Boolean = false,
) {
    var expanded by rememberSaveable(session.id) { mutableStateOf(false) }
    val roleLabel = activeRoleIdentity.ifBlank { "Unknown" }
    val scopeLabel = when (scope) {
        ControlScope.ActiveDevice -> "Active"
        ControlScope.SelectedDevices -> "Selected"
        ControlScope.AllConnected -> "All"
    }

    ControlCard(
        title = if (compactSingleDevice) "" else "System",
        subtitle = compactUiCopy("", ""),
    ) {
        if (!compactSingleDevice) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoPill("Scope", scopeLabel, Modifier.weight(1f))
                InfoPill("Targets", targetCount.toString(), Modifier.weight(1f))
                InfoPill("Role", roleLabel, Modifier.weight(1.2f))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoPill("System", readinessValue(systemReadyCount, targetCount), Modifier.weight(1f))
            InfoPill(
                "DSP",
                readinessValue(fullPayloadReadyCount, targetCount),
                Modifier.weight(1f),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(
                onClick = onUploadSystemParams,
                modifier = Modifier.weight(1f),
                enabled = systemReadyCount > 0,
            ) {
                Text(scopeReadyActionLabel("Upload Params", systemReadyCount, targetCount))
            }
            Button(
                onClick = onUploadAllParams,
                modifier = Modifier.weight(1f),
                enabled = fullPayloadReadyCount > 0,
            ) {
                Text(scopeReadyActionLabel("Upload All", fullPayloadReadyCount, targetCount))
            }
        }

        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.align(Alignment.Start),
        ) {
            Text(if (expanded) "Hide DSP Pushes" else "Show DSP Pushes")
        }

        if (expanded) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { onUploadDspParams(0) },
                    modifier = Modifier.weight(1f),
                    enabled = dsp1ReadyCount > 0,
                ) {
                    Text(scopeReadyActionLabel("Upload DSP1", dsp1ReadyCount, targetCount))
                }
                OutlinedButton(
                    onClick = { onUploadDspParams(1) },
                    modifier = Modifier.weight(1f),
                    enabled = dsp2ReadyCount > 0,
                ) {
                    Text(scopeReadyActionLabel("Upload DSP2", dsp2ReadyCount, targetCount))
                }
            }
        }
    }
}

@Composable
private fun SystemLifecycleCard(
    session: DeviceSessionUiState,
    activeRoleIdentity: String,
    onSleep: () -> Unit,
    onRequestReset: () -> Unit,
    onRequestBootloader: () -> Unit,
    onRequestFirmwareUpdate: () -> Unit,
    onPickBleOtaPackage: () -> Unit,
    onRequestStageBleOta: () -> Unit,
    onRequestInstallStagedBleOta: () -> Unit,
    onRequestAiModuleInstall: (Int, String) -> Unit,
    onRefreshAiStatus: () -> Unit,
    onSelectAiModule: (Int?) -> Unit,
    onSetAiRuntimeEnabled: (Boolean) -> Unit,
    onRequestRoleOverride: (Int, String) -> Unit,
) {
    val roleLabel = activeRoleIdentity.ifBlank { "Unknown" }
    val aiRuntime = session.aiRuntimeStatus
    val aiRuntimeLabel = when {
        aiRuntime == null -> "Status not read"
        aiRuntime.running -> "${aiSlotLabel(aiRuntime.activeSlot)} running"
        aiRuntime.requestedEnabled -> "${aiSlotLabel(aiRuntime.activeSlot)} ready"
        aiRuntime.activeSlot != null -> "${aiSlotLabel(aiRuntime.activeSlot)} selected"
        else -> "No slot selected"
    }
    val ephysImageReady = session.aiResidentSlotStatuses[Ce32Protocol.AiModuleEphysSlot]?.present
    val imuImageReady = session.aiResidentSlotStatuses[Ce32Protocol.AiModuleImuSlot]?.present
    val ota = session.bleOta
    ControlCard(
        title = "Maintenance & roles",
        subtitle = compactUiCopy("", ""),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoPill("Device", session.name, Modifier.weight(1.2f))
            InfoPill("Role", roleLabel, Modifier.weight(1f))
            InfoPill("State", compactSessionStateLabel(session), Modifier.weight(1f))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onSleep,
                modifier = Modifier.weight(1f),
                enabled = session.isConnected,
            ) {
                Text("Sleep")
            }
            OutlinedButton(
                onClick = onRequestReset,
                modifier = Modifier.weight(1f),
                enabled = session.isConnected,
            ) {
                Text("Reset")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onRequestBootloader,
                modifier = Modifier.weight(1f),
                enabled = session.isConnected,
            ) {
                Text("Bootloader")
            }
            Button(
                onClick = onRequestFirmwareUpdate,
                modifier = Modifier.weight(1f),
                enabled = session.isConnected,
            ) {
                Text("SD Update")
            }
        }

        HorizontalDivider(modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
        Text(
            "BLE firmware update",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Choose the single fused .hex for this device's hardware variant. BLE installs only its application, not the bootloader or resident recovery service. The installed firmware must support BLE staging. CRC checks file integrity; the file does not provide a reliable CE64/CE128 board-identity check.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
        )
        if (ota.hasPreparedPackage) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoPill("Firmware", ota.packageName.ifBlank { "CE64 firmware" }, Modifier.weight(1.4f))
                InfoPill("Gen", ota.generation.toString(), Modifier.weight(0.55f))
                InfoPill("State", bleOtaPhaseLabel(ota.phase), Modifier.weight(0.8f))
            }
            if (ota.totalBlocks > 0) {
                Text(
                    "${ota.completedBlocks}/${ota.totalBlocks} blocks · ${ota.imageBytes / 1024} KB · CRC ${ota.imageCrc32.toString(16).uppercase().padStart(8, '0')}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                )
            }
        }
        Text(
            ota.failureMessage.ifBlank { ota.statusMessage },
            style = MaterialTheme.typography.bodySmall,
            color = if (ota.phase == BleOtaPhase.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onPickBleOtaPackage,
                modifier = Modifier.weight(1f),
                enabled = session.isConnected && ota.phase !in setOf(BleOtaPhase.Staging, BleOtaPhase.Verifying, BleOtaPhase.InstallRequested),
            ) {
                Text(if (ota.hasPreparedPackage) "Choose another" else "Choose fused .hex")
            }
            val canStage = session.isConnected && ota.totalBlocks > 0 && ota.phase in setOf(BleOtaPhase.PackageReady, BleOtaPhase.Failed)
            Button(
                onClick = onRequestStageBleOta,
                modifier = Modifier.weight(1f),
                enabled = canStage,
            ) {
                Text(if (ota.phase == BleOtaPhase.Staging || ota.phase == BleOtaPhase.Verifying) "Working…" else "Stage & verify")
            }
        }
        if (ota.phase == BleOtaPhase.ReadyToInstall) {
            Button(
                onClick = onRequestInstallStagedBleOta,
                modifier = Modifier.fillMaxWidth(),
                enabled = session.isConnected,
            ) {
                Text("Install verified firmware")
            }
        }

        Text(
            "MASTER keeps the Windows-compatible host role, while SLAVE keeps the follower/device role.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { onRequestRoleOverride(0, "AUTO") },
                modifier = Modifier.weight(1f),
                enabled = session.isConnected,
            ) {
                Text("AUTO")
            }
            OutlinedButton(
                onClick = { onRequestRoleOverride(1, "MASTER") },
                modifier = Modifier.weight(1f),
                enabled = session.isConnected,
            ) {
                Text("MASTER")
            }
            OutlinedButton(
                onClick = { onRequestRoleOverride(2, "SLAVE") },
                modifier = Modifier.weight(1f),
                enabled = session.isConnected,
            ) {
                Text("SLAVE")
            }
        }

        HorizontalDivider(modifier = Modifier.padding(top = 14.dp, bottom = 12.dp))

        Text(
            "Resident AI runtime",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "$aiRuntimeLabel · Ephys ${aiImageReadinessLabel(ephysImageReady)} · IMU ${aiImageReadinessLabel(imuImageReady)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onRefreshAiStatus,
                modifier = Modifier.weight(1f),
                enabled = session.isConnected,
            ) {
                Text("Refresh")
            }
            OutlinedButton(
                onClick = { onSelectAiModule(Ce32Protocol.AiModuleEphysSlot) },
                modifier = Modifier.weight(1f),
                enabled = session.isConnected,
            ) {
                Text("Use ephys")
            }
            OutlinedButton(
                onClick = { onSelectAiModule(Ce32Protocol.AiModuleImuSlot) },
                modifier = Modifier.weight(1f),
                enabled = session.isConnected,
            ) {
                Text("Use IMU")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { onSetAiRuntimeEnabled(true) },
                modifier = Modifier.weight(1f),
                enabled = session.isConnected,
            ) {
                Text("Enable AI")
            }
            OutlinedButton(
                onClick = { onSetAiRuntimeEnabled(false) },
                modifier = Modifier.weight(1f),
                enabled = session.isConnected,
            ) {
                Text("Disable AI")
            }
            OutlinedButton(
                onClick = { onSelectAiModule(null) },
                modifier = Modifier.weight(1f),
                enabled = session.isConnected,
            ) {
                Text("Detach")
            }
        }

        HorizontalDivider(modifier = Modifier.padding(top = 14.dp, bottom = 12.dp))

        Text(
            "Staged AI module",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Activate a module already staged on the device SD card. This request does not transfer model files.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { onRequestAiModuleInstall(Ce32Protocol.AiModuleEphysSlot, "Ephys") },
                modifier = Modifier.weight(1f),
                enabled = session.isConnected,
            ) {
                Text("Ephys slot")
            }
            OutlinedButton(
                onClick = { onRequestAiModuleInstall(Ce32Protocol.AiModuleImuSlot, "IMU") },
                modifier = Modifier.weight(1f),
                enabled = session.isConnected,
            ) {
                Text("IMU slot")
            }
        }
    }
}

private fun aiSlotLabel(slot: Int?): String = when (slot) {
    Ce32Protocol.AiModuleEphysSlot -> "Ephys AI"
    Ce32Protocol.AiModuleImuSlot -> "IMU AI"
    else -> "AI"
}

private fun aiImageReadinessLabel(ready: Boolean?): String = when (ready) {
    true -> "ready"
    false -> "not installed"
    null -> "unknown"
}

@Composable
private fun StimMonitorCard(
    session: DeviceSessionUiState,
) {
    val stim = session.stimControlStatus
    ControlCard(
        title = "Closed-Loop Monitor",
        subtitle = compactUiCopy("", ""),
    ) {
        if (stim == null && session.triggeredWaveformBlockCount == 0) {
            Text(
                "No stimulation or triggered-waveform packets have arrived yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
        } else {
            if (stim != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    InfoPill("Trigger", "${stim.triggerLevel.format(2)}", Modifier.weight(1f))
                    InfoPill("Gain", "${stim.triggerGain.format(2)}", Modifier.weight(1f))
                    InfoPill("State", stim.stateLabel, Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    InfoPill("Stim Cnt", stim.stimCount.toString(), Modifier.weight(1f))
                    InfoPill("Cycle", stim.count.toString(), Modifier.weight(1f))
                    InfoPill("ID", stim.id.toString(), Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    InfoPill("Delay", "${stim.triggerDelay} ms", Modifier.weight(1f))
                    InfoPill("Random", "${stim.triggerRandomDelay} ms", Modifier.weight(1f))
                    InfoPill("Elapsed", stim.triggerElapsed.toString(), Modifier.weight(1f))
                }
            }

            if (session.triggeredWaveformBlockCount > 0) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Triggered waveform blocks: ${session.triggeredWaveformBlockCount}  |  Last block: ${session.lastTriggeredWaveformBytes} bytes  |  Saved: ${session.triggerWaveformCaptureBytes} bytes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                )
                if (session.triggerWaveformCapturePath.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Waveform file: ${compactPathLabel(session.triggerWaveformCapturePath)}${if (session.triggerWaveformCaptureActive) " (capturing)" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ControlCard(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = {
                if (title.isNotBlank()) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                }
                if (SHOW_UI_DESCRIPTIONS && subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
                content()
            }
        )
    }
}

@Composable
private fun LivePaneSelector(
    pane: LivePane,
    onPaneChange: (LivePane) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("View", style = MaterialTheme.typography.titleMedium)
            LivePane.entries.forEach { entry ->
                FilterChip(
                    selected = pane == entry,
                    onClick = { onPaneChange(entry) },
                    label = { Text(entry.label) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControlLaunchpadCard(
    scope: ControlScope,
    selectedCount: Int,
    scopeTargetCount: Int,
    preservedScopeCount: Int,
    section: ControlSection,
    sessions: List<DeviceSessionUiState>,
    activeSessionId: String?,
    activeSessionName: String?,
    onScopeChange: (ControlScope) -> Unit,
    onSectionChange: (ControlSection) -> Unit,
    onActivate: (String) -> Unit,
    subtitle: String,
) {
    val reconnectNote = controlScopeReconnectNote(
        scope = scope,
        preservedScopeCount = preservedScopeCount,
        linkedScopeCount = scopeTargetCount,
        activeSessionName = activeSessionName,
    )
    ControlCard(
        title = "Control",
        subtitle = subtitle,
    ) {
        if (sessions.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ControlDeviceSelectorChip(
                    sessions = sessions,
                    activeSessionId = activeSessionId,
                    onActivate = onActivate,
                    modifier = Modifier.weight(1.3f),
                )
                CompactDropdownSelector(
                    currentLabel = section.label,
                    options = ControlSection.entries.map { it.name to it.label },
                    selectedOptionName = section.name,
                    onSelectOption = { onSectionChange(ControlSection.valueOf(it)) },
                    compact = true,
                    modifier = Modifier.weight(0.9f),
                )
            }
        }

        Text(
            "Apply to",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = scope == ControlScope.ActiveDevice,
                onClick = { onScopeChange(ControlScope.ActiveDevice) },
                enabled = activeSessionId != null,
                label = { Text("This device") },
            )
            FilterChip(
                selected = scope == ControlScope.SelectedDevices,
                onClick = { onScopeChange(ControlScope.SelectedDevices) },
                enabled = sessions.any { it.isConnected },
                label = {
                    Text(
                        if (selectedCount > 0) "Selected $selectedCount" else "Choose devices",
                    )
                },
            )
            FilterChip(
                selected = scope == ControlScope.AllConnected,
                onClick = { onScopeChange(ControlScope.AllConnected) },
                enabled = sessions.any { it.isConnected },
                label = {
                    Text("All ${sessions.count { it.isConnected }}")
                },
            )
        }

        if (reconnectNote != null && sessions.isNotEmpty()) {
            Text(
                reconnectNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            )
        }

        if (sessions.isEmpty()) {
            Text(
                reconnectNote ?: "Connect a device to use controls.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveDeviceSelectorCard(
    sessions: List<DeviceSessionUiState>,
    activeSessionId: String?,
    onActivate: (String) -> Unit,
    subtitle: String,
) {
    ControlCard(
        title = "Active Device",
        subtitle = subtitle,
    ) {
        if (sessions.isEmpty()) {
            Text(
                "Connect at least one device to switch context from this page.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
            return@ControlCard
        }

        val activeSession = sessions.firstOrNull { it.id == activeSessionId } ?: sessions.first()
        InfoPill("Current", activeSession.name, Modifier.fillMaxWidth())

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            sessions.forEach { session ->
                val label = buildString {
                    append(session.name)
                    if (session.isRecordingLike) {
                        append(" REC")
                    }
                }
                FilterChip(
                    selected = session.id == activeSession.id,
                    onClick = { onActivate(session.id) },
                    label = { Text(label) },
                )
            }
        }
    }
}

private fun shouldShowTargetSelectionCard(uiState: WildUiState): Boolean {
    return uiState.connectedSessions.isNotEmpty() &&
        uiState.controlScope == ControlScope.SelectedDevices
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TargetSelectionCard(
    sessions: List<DeviceSessionUiState>,
    selectedSessionIds: Set<String>,
    onToggleSelection: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
) {
    val activeIds = sessions.filter { it.isActive }.map { it.id }
    val liveIds = sessions.filter { it.hostState == BleHostSessionState.Previewing }.map { it.id }
    val recordingIds = sessions.filter { it.isRecordingLike }.map { it.id }
    val readyIds = sessions.filter { session ->
        session.hostState == BleHostSessionState.Connected ||
            session.hostState == BleHostSessionState.Synced
    }.map { it.id }
    val selectedConnectedCount = selectedSessionIds.intersect(sessions.map { it.id }.toSet()).size
    val applySubsetSelection: (List<String>) -> Unit = { ids ->
        onClearSelection()
        ids.forEach(onToggleSelection)
    }
    val presetActions = buildList {
        if (activeIds.isNotEmpty()) {
            add(SelectionPresetAction(label = "Active", ids = activeIds))
        }
        if (liveIds.isNotEmpty()) {
            add(SelectionPresetAction(label = "Preview", ids = liveIds))
        }
        if (recordingIds.isNotEmpty()) {
            add(SelectionPresetAction(label = "REC", ids = recordingIds))
        }
        if (readyIds.isNotEmpty() && readyIds.size < sessions.size) {
            add(SelectionPresetAction(label = "Ready", ids = readyIds))
        }
    }
    ControlCard(
        title = "",
        subtitle = "",
    ) {
        if (sessions.isEmpty()) {
            Text(
                "Connect devices to continue.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
            return@ControlCard
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$selectedConnectedCount / ${sessions.size} selected",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            PreviewCompactToggleChip(
                label = "All",
                selected = selectedConnectedCount == sessions.size && sessions.isNotEmpty(),
                onClick = onSelectAll,
                enabled = sessions.isNotEmpty(),
            )
            PreviewCompactToggleChip(
                label = "Clear",
                selected = false,
                onClick = onClearSelection,
                enabled = selectedConnectedCount > 0,
            )
        }

        if (presetActions.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                presetActions.forEach { action ->
                    PreviewCompactToggleChip(
                        label = "${action.label} ${action.ids.size}",
                        selected = action.ids.isNotEmpty() && action.ids.all { it in selectedSessionIds } && action.ids.size == selectedConnectedCount,
                        onClick = { applySubsetSelection(action.ids) },
                        enabled = action.ids.isNotEmpty(),
                    )
                }
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            sessions.forEach { session ->
                SelectionDeviceChip(
                    session = session,
                    selected = session.id in selectedSessionIds,
                    onToggleSelection = { onToggleSelection(session.id) },
                )
            }
        }
    }
}

private data class SelectionPresetAction(
    val label: String,
    val ids: List<String>,
)

@Composable
private fun SelectionDeviceChip(
    session: DeviceSessionUiState,
    selected: Boolean,
    onToggleSelection: () -> Unit,
) {
    val detailLine = buildString {
        when {
            session.isRecordingLike -> append("REC")
            session.hostState == BleHostSessionState.Previewing -> append("Preview")
            session.awaitingLiveSync && session.isConnected -> append("Sync")
            else -> {
                compactSessionStateLabel(session)
                    .takeIf { it.isNotBlank() }
                    ?.let(::append)
            }
        }
        if (session.isActive) {
            if (isNotBlank()) append("  |  ")
            append("Active")
        }
    }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.46f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
        modifier = Modifier
            .widthIn(min = 94.dp, max = 132.dp)
            .clickable(onClick = onToggleSelection),
    ) {
        Column(
            modifier = Modifier
                .border(
                    width = 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                    },
                    shape = RoundedCornerShape(18.dp),
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color(session.traceColorArgb), CircleShape)
                )
                Text(
                    text = compactDeviceUiLabel(session.name, session.address),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (detailLine.isNotBlank()) {
                Text(
                    text = detailLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CompactDevicePreviewSourceSelector(
    session: DeviceSessionUiState,
    onSetPreviewSelection: (PreviewSelection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val normalizedSelection = session.previewSelection.normalizedForDevice(session.parsedSystemParams?.ephysChannelCount)
    val optionCount = normalizedSelection.optionCount(session.parsedSystemParams?.ephysChannelCount)
    PreviewCompactStepperChip(
        axisLabel = "",
        currentLabel = compactPreviewSourceLabel(
            normalizedSelection,
            session.parsedSystemParams?.ephysChannelCount,
        ).let { "Source: $it" },
        canStepDown = session.isConnected && optionCount > 1,
        canStepUp = session.isConnected && optionCount > 1,
        minWidth = 168.dp,
        maxWidth = 210.dp,
        buttonSize = 48.dp,
        onLabelClick = {
            if (!session.isConnected) {
                return@PreviewCompactStepperChip
            }
            onSetPreviewSelection(
                normalizedSelection.copy(auxMode = !normalizedSelection.auxMode)
                    .normalizedForDevice(session.parsedSystemParams?.ephysChannelCount),
            )
        },
        onStepDown = {
            onSetPreviewSelection(
                shiftPreviewSelection(
                    normalizedSelection,
                    -1,
                    session.parsedSystemParams?.ephysChannelCount,
                ),
            )
        },
        onStepUp = {
            onSetPreviewSelection(
                shiftPreviewSelection(
                    normalizedSelection,
                    1,
                    session.parsedSystemParams?.ephysChannelCount,
                ),
            )
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompactPreviewSelector(
    selection: PreviewSelection,
    ephysChannelCount: Int?,
    enabled: Boolean,
    expanded: Boolean,
    showModeToggle: Boolean = false,
    onSelection: (PreviewSelection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val normalizedSelection = selection.normalizedForDevice(ephysChannelCount)
    val optionCount = normalizedSelection.optionCount(ephysChannelCount)
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showModeToggle || expanded || normalizedSelection.auxMode) {
            FilterChip(
                selected = normalizedSelection.auxMode,
                onClick = {
                    onSelection(
                        normalizedSelection.copy(auxMode = !normalizedSelection.auxMode).normalizedForDevice(ephysChannelCount),
                    )
                },
                enabled = enabled,
                label = { Text(if (normalizedSelection.auxMode) "Aux" else "Ephys") },
            )
        }

        AssistChip(
            onClick = { onSelection(shiftPreviewSelection(normalizedSelection, -1, ephysChannelCount)) },
            enabled = enabled && optionCount > 1,
            label = { Text("<") },
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Text(
                text = if (normalizedSelection.auxMode) {
                    "Aux ${normalizedSelection.label(ephysChannelCount)}"
                } else {
                    normalizedSelection.label(ephysChannelCount)
                },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f),
            )
        }
        AssistChip(
            onClick = { onSelection(shiftPreviewSelection(normalizedSelection, 1, ephysChannelCount)) },
            enabled = enabled && optionCount > 1,
            label = { Text(">") },
        )

        if (expanded) {
            AssistChip(
                onClick = {},
                enabled = false,
                label = {
                    Text(
                        if (!normalizedSelection.auxMode) {
                            "Bank ${previewBankLabel(previewBankForIndex(normalizedSelection.index, optionCount))} | ${normalizedSelection.index + 1} / $optionCount"
                        } else {
                            "${normalizedSelection.index + 1} / $optionCount"
                        },
                    )
                },
            )
        }
    }
}

private fun shiftPreviewSelection(
    selection: PreviewSelection,
    delta: Int,
    ephysChannelCount: Int?,
): PreviewSelection {
    val normalizedSelection = selection.normalizedForDevice(ephysChannelCount)
    val optionCount = normalizedSelection.optionCount(ephysChannelCount).coerceAtLeast(1)
    val shiftedIndex = (((normalizedSelection.index + delta) % optionCount) + optionCount) % optionCount
    return normalizedSelection.copy(index = shiftedIndex).normalizedForDevice(ephysChannelCount)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControlSectionSelector(
    section: ControlSection,
    onSectionChange: (ControlSection) -> Unit,
    showLabel: Boolean = true,
) {
    if (!showLabel) {
        CompactDropdownSelector(
            currentLabel = section.label,
            options = ControlSection.entries.map { it.name to it.label },
            selectedOptionName = section.name,
            onSelectOption = { onSectionChange(ControlSection.valueOf(it)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        )
        return
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Sections", style = MaterialTheme.typography.titleMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ControlSection.entries.forEach { entry ->
                    FilterChip(
                        selected = section == entry,
                        onClick = { onSectionChange(entry) },
                        label = { Text(entry.label, maxLines = 1) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SingleDeviceControlSelectorRow(
    section: ControlSection,
    acquisitionPane: AcquisitionPane,
    closedLoopPane: ClosedLoopPane,
    analysisPane: AnalysisPane,
    systemPane: SystemPane,
    onSectionChange: (ControlSection) -> Unit,
    onAcquisitionPaneChange: (AcquisitionPane) -> Unit,
    onClosedLoopPaneChange: (ClosedLoopPane) -> Unit,
    onAnalysisPaneChange: (AnalysisPane) -> Unit,
    onSystemPaneChange: (SystemPane) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = when (section) {
        ControlSection.Acquisition -> "acq:${acquisitionPane.name}"
        ControlSection.ClosedLoop -> "cl:${closedLoopPane.name}"
        ControlSection.Analysis -> "analysis:${analysisPane.name}"
        ControlSection.System -> "system:${systemPane.name}"
        ControlSection.Io -> "io"
    }
    val tasks = buildList {
        addAll(AcquisitionPane.entries.map { "acq:${it.name}" to "Acquisition · ${it.label}" })
        addAll(ClosedLoopPane.entries.map { "cl:${it.name}" to "Closed-loop · ${it.label}" })
        addAll(AnalysisPane.entries.map { "analysis:${it.name}" to it.label })
        add("io" to "I/O · LED, GPIO & trigger output")
        addAll(SystemPane.entries.map { "system:${it.name}" to it.label })
    }
    CompactDropdownSelector(
        currentLabel = "Task: " + tasks.first { it.first == selected }.second,
        options = tasks,
        selectedOptionName = selected,
        onSelectOption = { target ->
            val lane = target.substringAfter(':')
            when (target.substringBefore(':')) {
                "acq" -> { onSectionChange(ControlSection.Acquisition); onAcquisitionPaneChange(AcquisitionPane.valueOf(lane)) }
                "cl" -> { onSectionChange(ControlSection.ClosedLoop); onClosedLoopPaneChange(ClosedLoopPane.valueOf(lane)) }
                "analysis" -> { onSectionChange(ControlSection.Analysis); onAnalysisPaneChange(AnalysisPane.valueOf(lane)) }
                "system" -> { onSectionChange(ControlSection.System); onSystemPaneChange(SystemPane.valueOf(lane)) }
                else -> onSectionChange(ControlSection.Io)
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun CompactDropdownSelector(
    currentLabel: String,
    options: List<Pair<String, String>>,
    selectedOptionName: String,
    onSelectOption: (String) -> Unit,
    optionEnabled: (String) -> Boolean = { true },
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var expanded by remember(selectedOptionName, options.size) { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (compact) 14.dp else 16.dp)

    Box(modifier = modifier) {
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
                    shape = shape,
                )
                .clickable { expanded = true },
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = if (compact) 10.dp else 12.dp,
                    vertical = if (compact) 6.dp else 9.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = currentLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = Icons.Outlined.ArrowDropDown,
                    contentDescription = "Open selector",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    modifier = Modifier.size(if (compact) 16.dp else 18.dp),
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (name, label) ->
                DropdownMenuItem(
                    enabled = optionEnabled(name),
                    text = {
                        Text(
                            text = label,
                            color = if (name == selectedOptionName) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelectOption(name)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControlSubpanelSelectorCard(
    sectionLabel: String,
    subtitle: String,
    options: List<Pair<String, String>>,
    selectedOptionName: String,
    onSelectOption: (String) -> Unit,
    compact: Boolean = false,
) {
    if (compact) {
        val currentLabel = options.firstOrNull { it.first == selectedOptionName }?.second ?: sectionLabel
        CompactDropdownSelector(
            currentLabel = currentLabel,
            options = options,
            selectedOptionName = selectedOptionName,
            onSelectOption = onSelectOption,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        )
        return
    }

    ControlCard(
        title = sectionLabel,
        subtitle = subtitle,
    ) {
        options.chunked(2).forEach { rowOptions ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowOptions.forEach { (name, label) ->
                    PocketRouteButton(
                        label = label,
                        onClick = { onSelectOption(name) },
                        modifier = Modifier.weight(1f),
                        selected = selectedOptionName == name,
                    )
                }
                if (rowOptions.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PreviewSelector(
    selection: PreviewSelection,
    ephysChannelCount: Int? = null,
    enabled: Boolean,
    onSelection: (PreviewSelection) -> Unit,
) {
    val normalizedSelection = selection.normalizedForDevice(ephysChannelCount)
    val optionCount = normalizedSelection.optionCount(ephysChannelCount)
    val ephysBanks = previewBankRanges(optionCount)
    val activeEphysBank = previewBankForIndex(normalizedSelection.index, optionCount)
    ControlCard(
        title = "Preview Source",
        subtitle = compactUiCopy("", ""),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(
                selected = !normalizedSelection.auxMode,
                onClick = {
                    onSelection(
                        normalizedSelection.copy(auxMode = false).normalizedForDevice(ephysChannelCount),
                    )
                },
                label = { Text("Ephys") },
                enabled = enabled,
            )
            FilterChip(
                selected = normalizedSelection.auxMode,
                onClick = {
                    onSelection(
                        normalizedSelection.copy(auxMode = true).normalizedForDevice(ephysChannelCount),
                    )
                },
                label = { Text("Aux") },
                enabled = enabled,
            )
        }

        if (normalizedSelection.auxMode) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(optionCount) { index ->
                    FilterChip(
                        selected = normalizedSelection.index == index,
                        onClick = { onSelection(normalizedSelection.copy(index = index)) },
                        label = {
                            Text(
                                PreviewSelection(
                                    auxMode = normalizedSelection.auxMode,
                                    index = index,
                                ).label(ephysChannelCount),
                            )
                        },
                        enabled = enabled,
                    )
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoPill("Channel", normalizedSelection.label(ephysChannelCount), Modifier.weight(1f))
                InfoPill("Index", "${normalizedSelection.index + 1} / $optionCount", Modifier.weight(1f))
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ephysBanks.forEach { bank ->
                    FilterChip(
                        selected = normalizedSelection.index in bank,
                        onClick = { onSelection(normalizedSelection.copy(index = bank.first)) },
                        label = { Text(previewBankLabel(bank)) },
                        enabled = enabled,
                    )
                }
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                activeEphysBank.forEach { channelIndex ->
                    FilterChip(
                        selected = normalizedSelection.index == channelIndex,
                        onClick = { onSelection(normalizedSelection.copy(index = channelIndex)) },
                        label = { Text(PreviewSelection(auxMode = false, index = channelIndex).label(ephysChannelCount)) },
                        enabled = enabled,
                    )
                }
            }
        }
    }
}

@Composable
private fun ParamDumpCard(
    session: DeviceSessionUiState,
) {
    ControlCard(
        title = "Raw Payloads",
        subtitle = compactUiCopy("", ""),
    ) {
        session.parsedSystemParams?.let { parsed ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoPill("Preview", parsed.previewSelection.label(parsed.ephysChannelCount), Modifier.weight(1f))
                InfoPill("FW / HW", "${parsed.firmwareVersion} / ${parsed.hardwareVersion}", Modifier.weight(1f))
                InfoPill("Base FS", formatRateLabel(parsed.baseFs), Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoPill("AUX Mode", parsed.auxMode.toString(), Modifier.weight(1f))
                InfoPill("Prev Ratio", parsed.previewRatio.toString(), Modifier.weight(1f))
                InfoPill("Misc Ratio", parsed.miscRatio.toString(), Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoPill("VBatt Raw", parsed.vbattThresholdRaw.toString(), Modifier.weight(1f))
                InfoPill("Audio Ratio", parsed.audioRatio.toString(), Modifier.weight(1f))
                InfoPill("Camera Ratio", parsed.cameraRatio.toString(), Modifier.weight(1f))
            }
            if (parsed.errorCode != 0L) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Error code: ${formatHex(parsed.errorCode)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                )
            }
            HorizontalDivider()
        }
        PayloadBlock("System", session.systemParamHex)
        HorizontalDivider()
        PayloadBlock("DSP1", session.dsp1ParamHex)
        HorizontalDivider()
        PayloadBlock("DSP2", session.dsp2ParamHex)
        HorizontalDivider()
        PayloadBlock("Camera", session.cameraParamHex)
    }
}

@Composable
private fun PayloadBlock(
    label: String,
    payload: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontWeight = FontWeight.SemiBold)
        Text(
            text = if (payload.isBlank()) "No payload received yet." else payload,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 6,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.76f),
        )
    }
}

private fun compactPathLabel(path: String): String {
    return path.substringAfterLast('\\').substringAfterLast('/').ifBlank { path }
}

internal fun formatByteCountCompact(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0L)
    return when {
        safe >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", safe / (1024f * 1024f))
        safe >= 1024L -> String.format(Locale.US, "%.1f KB", safe / 1024f)
        else -> "$safe B"
    }
}

internal fun previewBankRanges(optionCount: Int, bankSize: Int = 8): List<IntRange> {
    val safeCount = optionCount.coerceAtLeast(1)
    val safeBankSize = bankSize.coerceAtLeast(1)
    return (0 until safeCount step safeBankSize).map { start ->
        start..minOf(start + safeBankSize - 1, safeCount - 1)
    }
}

internal fun previewBankForIndex(index: Int, optionCount: Int, bankSize: Int = 8): IntRange {
    val ranges = previewBankRanges(optionCount, bankSize)
    val normalizedIndex = index.coerceIn(0, optionCount.coerceAtLeast(1) - 1)
    return ranges.firstOrNull { normalizedIndex in it } ?: ranges.first()
}

internal fun previewBankLabel(range: IntRange): String {
    val start = range.first + 1
    val end = range.last + 1
    return if (start == end) {
        "E$start"
    } else {
        "E$start-$end"
    }
}

internal fun sessionLatestActivityLine(session: DeviceSessionUiState): String {
    return session.recentEvents.lastOrNull()?.summary
        ?: session.lastFailure.ifBlank { session.lastMessage.ifBlank { "No recent BLE activity." } }
}

@Composable
private fun DigitalFlagStrip(
    session: DeviceSessionUiState,
) {
    ControlCard(
        title = "Live Digital Flags",
        subtitle = session.syncText.ifBlank { "Preview and recording status bits from the incoming packets." },
    ) {
        if (session.digitalFlags.isEmpty()) {
            AssistChip(onClick = {}, label = { Text("No flags yet") })
        } else {
            session.digitalFlags.chunked(2).forEach { rowFlags ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowFlags.forEach { flag ->
                        DigitalFlagTile(
                            name = flag.name,
                            active = flag.active,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowFlags.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnlineLaneHeaderCard(
    connectedSessions: List<DeviceSessionUiState>,
    focusSessions: List<DeviceSessionUiState>,
    session: DeviceSessionUiState,
    activeSessionId: String?,
    canOpenStatus: Boolean,
    canOpenConnected: Boolean,
    activeToolsPane: OnlineToolsPane,
    scope: ControlScope,
    selectedCount: Int,
    payloadCachedCount: Int,
    cameraCachedCount: Int,
    recordCachedCount: Int,
    onScopeChange: (ControlScope) -> Unit,
    onActivate: (String) -> Unit,
    onLinkAction: () -> Unit,
    onResync: () -> Unit,
    onOpenStatus: () -> Unit,
    onOpenConnected: () -> Unit,
    onOpenConsole: () -> Unit,
    onOpenPayload: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenRecords: () -> Unit,
) {
    val canOpenPayloadLane = session.isConnected || payloadCachedCount > 0
    val canOpenCameraLane = session.isConnected || cameraCachedCount > 0
    val canOpenRecordLane = session.isConnected || recordCachedCount > 0
    val scopeOptions = buildCompactScopeOptions(
        scope = scope,
        selectedCount = selectedCount,
        connectedCount = connectedSessions.size,
    )
    val laneOptions = listOf(
        Triple(OnlineToolsPane.Console, compactOnlineToolsLabel(OnlineToolsPane.Console), true),
        Triple(OnlineToolsPane.Payload, compactOnlineToolsLabel(OnlineToolsPane.Payload), canOpenPayloadLane),
        Triple(OnlineToolsPane.Camera, compactOnlineToolsLabel(OnlineToolsPane.Camera), canOpenCameraLane),
        Triple(OnlineToolsPane.Records, compactOnlineToolsLabel(OnlineToolsPane.Records), canOpenRecordLane),
    )
    val statusSummary = when {
        session.isRecordingLike -> "REC"
        session.cameraPreviewStreaming -> "CAM"
        isPreviewingSession(session) -> "LIVE"
        else -> session.statusText
    }

    ControlCard(
        title = "",
        subtitle = "",
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (focusSessions.isNotEmpty()) {
                CompactFocusSessionSelector(
                    sessions = focusSessions,
                    activeSessionId = activeSessionId,
                    onActivate = onActivate,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
            } else {
                InfoPill("Focus", compactDeviceUiLabel(session.name, session.address), Modifier.weight(1f))
            }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
            ) {
                Text(
                    statusSummary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            PreviewTransportButton(
                icon = if (session.isConnected || session.isLinkingLike) {
                    Icons.Outlined.Stop
                } else {
                    Icons.Outlined.SettingsInputAntenna
                },
                contentDescription = sessionLinkActionLabel(session),
                enabled = sessionLinkActionEnabled(session),
                filled = false,
                label = sessionLinkActionLabel(session),
                tint = if (session.isConnected || session.isLinkingLike) Color(0xFFD64545) else MaterialTheme.colorScheme.primary,
                buttonSize = 34.dp,
                onClick = onLinkAction,
            )
            if (session.isConnected) {
                PreviewTransportButton(
                    icon = Icons.Outlined.Sync,
                    contentDescription = "Resync",
                    enabled = true,
                    filled = false,
                    label = "Sync",
                    buttonSize = 34.dp,
                    onClick = onResync,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (scopeOptions.size > 1) {
                CompactDropdownSelector(
                    currentLabel = scopeOptions.firstOrNull { it.first == scope }?.second ?: compactScopeSelectorLabel(scope),
                    options = scopeOptions.map { (targetScope, label, _) -> targetScope.name to label },
                    selectedOptionName = scope.name,
                    onSelectOption = { onScopeChange(ControlScope.valueOf(it)) },
                    optionEnabled = { optionName ->
                        scopeOptions.firstOrNull { it.first.name == optionName }?.third == true
                    },
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
            }
            CompactDropdownSelector(
                currentLabel = compactOnlineToolsLabel(activeToolsPane),
                options = laneOptions.map { (pane, label, _) -> pane.name to label },
                selectedOptionName = activeToolsPane.name,
                onSelectOption = { optionName ->
                    when (OnlineToolsPane.valueOf(optionName)) {
                        OnlineToolsPane.Hub -> onOpenConsole()
                        OnlineToolsPane.Console -> onOpenConsole()
                        OnlineToolsPane.Payload -> onOpenPayload()
                        OnlineToolsPane.Camera -> onOpenCamera()
                        OnlineToolsPane.Records -> onOpenRecords()
                    }
                },
                optionEnabled = { optionName ->
                    laneOptions.firstOrNull { it.first.name == optionName }?.third == true
                },
                compact = true,
                modifier = Modifier.weight(if (scopeOptions.size > 1) 1f else 1.4f),
            )
            if (canOpenStatus) {
                PreviewCompactToggleChip(
                    label = "Status",
                    selected = false,
                    onClick = onOpenStatus,
                    compact = true,
                )
            }
            if (canOpenConnected) {
                PreviewCompactToggleChip(
                    label = "Dev",
                    selected = false,
                    onClick = onOpenConnected,
                    compact = true,
                )
            }
        }
    }
}

@Composable
private fun PocketLaunchTile(
    title: String,
    summary: String,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = when {
            !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            selected -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.surface
        },
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(
                        alpha = when {
                            !enabled -> 0.08f
                            selected -> 0.34f
                            else -> 0.2f
                        }
                    ),
                    shape = RoundedCornerShape(22.dp),
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title.uppercase(Locale.US),
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.46f)
                        selected -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f)
                        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.88f)
                    },
                )
            }
            Text(
                summary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = when {
                    !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f)
                    selected -> MaterialTheme.colorScheme.onPrimary
                    else -> MaterialTheme.colorScheme.onSurface
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (SHOW_UI_DESCRIPTIONS) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f)
                        selected -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.76f)
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    },
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PocketRouteButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    if (selected) {
        FilledTonalButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
        ) {
            Text(
                label,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
        ) {
            Text(
                label,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun sessionLinkActionLabel(session: DeviceSessionUiState): String {
    return when (session.hostState) {
        BleHostSessionState.Connecting -> "Cancel"
        BleHostSessionState.Reconnecting -> "Stop"
        BleHostSessionState.Disconnecting -> "Busy"
        else -> when {
            session.hostState == BleHostSessionState.Error -> "Retry"
            session.isConnected -> "Disconnect"
            else -> "Connect"
        }
    }
}

private fun sessionLinkActionEnabled(session: DeviceSessionUiState): Boolean {
    return session.hostState != BleHostSessionState.Disconnecting
}

private fun sessionLinkActionHelper(session: DeviceSessionUiState): String {
    return when (session.hostState) {
        BleHostSessionState.Connecting ->
            "Cancel the focused BLE link attempt here without leaving the live working page."
        BleHostSessionState.Reconnecting ->
            "Stop the reconnect loop for this device from the same phone-sized console."
        BleHostSessionState.Disconnecting ->
            "The focused device is already tearing down its BLE transport."
        else -> when {
            session.isConnected ->
                "Disconnect the focused device here when you want to stop remote monitoring without jumping back to the roster."
            session.bulkConnectEligible ->
                "Connect the focused device here so preview, records, and online helpers stay in one phone workflow."
            else ->
                "This device needs the manual verification path, but you can still start that link from here."
        }
    }
}

@Composable
private fun SignalMonitorCompanionCard(
    session: DeviceSessionUiState,
    subtitle: String = "",
    onLinkAction: () -> Unit,
    onResync: () -> Unit,
    onResyncNoRtc: (() -> Unit)? = null,
    onShowSyncLogPath: () -> Unit,
    onShowTriggerWaveformPath: () -> Unit,
) {
    val syncLine = formatSyncCompactLineAscii(session.lastSyncMetric, session.liveSync)
    val linkLine = session.bleLinkStats?.let(::formatBleLinkCompactLine)
    val companionLine = syncLine ?: linkLine ?: "Sync and BLE link health will appear here once the session is active."
    val latestEvent = session.recentEvents.lastOrNull()?.summary
        ?: session.lastFailure.ifBlank { session.lastMessage.ifBlank { "No recent BLE activity." } }
    val linkLabel = when {
        session.bleLinkStats != null -> formatHexByte(session.bleLinkStats.lastRssiRaw)
        session.rssi != null -> "${session.rssi} dBm"
        else -> "--"
    }
    val syncSampleCount = resolveSyncMeasurementCount(session.lastSyncMetric, session.liveSync) ?: 0
    val syncAccuracyLabel = resolveSyncAccuracyEstimateMs(session.lastSyncMetric, session.liveSync)?.let(::formatMs)
    val compactMetrics = buildList {
        add("Link" to linkLabel)
        add("Sync" to (syncAccuracyLabel ?: if (session.awaitingLiveSync) "Wait" else "--"))
        add("N" to syncSampleCount.toString())
        add("Prev" to session.previewPacketCount.toString())
        add("Rec" to formatSeconds(session.recordingSeconds))
        add("Used" to formatUsedSpaceLabel(session.usedSpaceMb))
        preferredAdvertisementBattery(session)?.let { add("Batt" to formatVoltageLabel(it)) }
        session.bleLinkStats?.let { add("Loss" to formatPercent(it.lossPercent)) }
    }

    ControlCard(
        title = "Signal",
        subtitle = subtitle,
    ) {
        CompactHubMetricBand(
            metrics = compactMetrics,
        )

        Text(
            companionLine,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.76f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (latestEvent.isNotBlank() && latestEvent != companionLine) {
            Text(
                latestEvent,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        CompactActionChipFlow {
            CompactActionChip(
                label = sessionLinkActionLabel(session),
                onClick = onLinkAction,
                enabled = sessionLinkActionEnabled(session),
                emphasized = true,
            )
            CompactActionChip(
                label = "Sync",
                onClick = onResync,
                enabled = session.isConnected,
            )
            if (SHOW_UI_DESCRIPTIONS && onResyncNoRtc != null) {
                CompactActionChip(
                    label = "Resync (no RTC write)",
                    onClick = onResyncNoRtc,
                    enabled = session.isConnected,
                )
            }
            CompactActionChip(
                label = "Log",
                onClick = onShowSyncLogPath,
                enabled = session.isConnected,
            )
            CompactActionChip(
                label = "Wave",
                onClick = onShowTriggerWaveformPath,
                enabled = session.triggerWaveformCapturePath.isNotBlank(),
            )
        }
    }
}

@Composable
private fun SignalOnlineToolkitCard(
    session: DeviceSessionUiState,
    scope: ControlScope,
    preservedScopeCount: Int,
    targetCount: Int,
    systemReadyCount: Int,
    dsp1ReadyCount: Int,
    dsp2ReadyCount: Int,
    fullPayloadReadyCount: Int,
    subtitle: String = "",
    onReadSystemParams: () -> Unit,
    onReadDspParams: () -> Unit,
    onReadAllParams: () -> Unit,
    onUploadSystemParams: () -> Unit,
    onUploadDspParams: (Int) -> Unit,
    onUploadAllParams: () -> Unit,
    onApplyStreamRates: (Int, Boolean, Boolean) -> Unit,
    onQuickSetFs: (Int) -> Unit,
    onApplySystemProfileToSystem: (Int, Int, Int) -> Unit,
    onSetTriggerWaveform: (Boolean) -> Unit,
    onShowTriggerWaveformPath: () -> Unit,
) {
    val parsed = session.parsedSystemParams
    val scopeLabel = when (scope) {
        ControlScope.ActiveDevice -> "Active"
        ControlScope.SelectedDevices -> "Selected"
        ControlScope.AllConnected -> "All"
    }
    val targetMetricValue = when {
        preservedScopeCount > targetCount -> "$targetCount/$preservedScopeCount"
        else -> targetCount.toString()
    }
    val waveformState = if (session.triggerWaveformEnabled) "ON" else "OFF"
    val payloadState = if (parsed != null || session.systemParamHex.isNotBlank()) "READY" else "READ"
    val sourceLabel = compactPreviewSourceLabel(
        session.previewSelection.normalizedForDevice(parsed?.ephysChannelCount),
        parsed?.ephysChannelCount,
    )
    val currentRate = parsed?.ephysSamplingRate?.takeIf { it >= 0 } ?: 1250
    var requestedRateText by remember(session.id) {
        mutableStateOf(currentRate.toString())
    }
    var cameraEnabled by remember(session.id) {
        mutableStateOf(parsed?.cameraEnabled == true)
    }
    var adcEnabled by remember(session.id) {
        mutableStateOf(parsed?.adcEnabled == true)
    }
    var vbattThresholdText by remember(session.id) {
        mutableStateOf(parsed?.vbattThresholdRaw?.toString() ?: "330")
    }
    var audioRatioText by remember(session.id) {
        mutableStateOf(parsed?.audioRatio?.toString() ?: "0")
    }
    var cameraRatioText by remember(session.id) {
        mutableStateOf(parsed?.cameraRatio?.toString() ?: "0")
    }
    LaunchedEffect(session.id, parsed?.ephysSamplingRate) {
        requestedRateText = (parsed?.ephysSamplingRate?.takeIf { it >= 0 } ?: currentRate).toString()
    }
    LaunchedEffect(session.id, parsed?.cameraEnabled, parsed?.adcEnabled) {
        cameraEnabled = parsed?.cameraEnabled == true
        adcEnabled = parsed?.adcEnabled == true
    }
    LaunchedEffect(session.id, parsed?.vbattThresholdRaw, parsed?.audioRatio, parsed?.cameraRatio) {
        if (parsed != null) {
            vbattThresholdText = parsed.vbattThresholdRaw.toString()
            audioRatioText = parsed.audioRatio.toString()
            cameraRatioText = parsed.cameraRatio.toString()
        }
    }
    val requestedRate = requestedRateText.toIntOrNull()
    val vbattThresholdRaw = vbattThresholdText.toIntOrNull()
    val audioRatio = audioRatioText.toIntOrNull()
    val cameraRatio = cameraRatioText.toIntOrNull()
    val ratePresets = (listOf(currentRate) + Ce32Protocol.CommonEphysRates).distinct().sorted()
    val applyLabel = if (parsed != null) "Apply Stream" else "Quick FS"
    var showStreamEditor by rememberSaveable(session.id) { mutableStateOf(false) }
    var showSystemEditor by rememberSaveable(session.id) { mutableStateOf(false) }

    ControlCard(
        title = "Parameters",
        subtitle = subtitle,
    ) {
        CompactHubMetricBand(
            metrics = buildList {
                add("Scope" to scopeLabel)
                add("Targets" to targetMetricValue)
                add("Payload" to payloadState)
                add("FS" to formatRateLabel(parsed?.ephysSamplingRate))
                add("Sys" to readinessValue(systemReadyCount, targetCount))
                add("Full" to readinessValue(fullPayloadReadyCount, targetCount))
                add("Wave" to waveformState)
                add("Src" to sourceLabel)
                if (parsed != null) {
                    add("VBatt" to parsed.vbattThresholdRaw.toString())
                    add("Audio" to parsed.audioRatio.toString())
                    add("Cam" to parsed.cameraRatio.toString())
                }
            },
        )

        CompactActionChipFlow {
            CompactActionChip(
                label = scopeActionLabel("Read Sys", targetCount),
                onClick = onReadSystemParams,
                enabled = targetCount > 0,
            )
            CompactActionChip(
                label = scopeActionLabel("Read DSP", targetCount),
                onClick = onReadDspParams,
                enabled = targetCount > 0,
            )
            CompactActionChip(
                label = scopeActionLabel("Read All", targetCount),
                onClick = onReadAllParams,
                enabled = targetCount > 0,
            )
            CompactActionChip(
                label = scopeReadyActionLabel("Push Sys", systemReadyCount, targetCount),
                onClick = onUploadSystemParams,
                enabled = systemReadyCount > 0,
            )
            CompactActionChip(
                label = scopeReadyActionLabel("Push D1", dsp1ReadyCount, targetCount),
                onClick = { onUploadDspParams(0) },
                enabled = dsp1ReadyCount > 0,
            )
            CompactActionChip(
                label = scopeReadyActionLabel("Push D2", dsp2ReadyCount, targetCount),
                onClick = { onUploadDspParams(1) },
                enabled = dsp2ReadyCount > 0,
            )
            CompactActionChip(
                label = scopeReadyActionLabel("Push All", fullPayloadReadyCount, targetCount),
                onClick = onUploadAllParams,
                enabled = fullPayloadReadyCount > 0,
            )
            CompactActionChip(
                label = if (showStreamEditor) "Hide FS" else "Edit FS",
                onClick = { showStreamEditor = !showStreamEditor },
                enabled = session.isConnected,
                emphasized = showStreamEditor,
            )
            if (parsed != null) {
                CompactActionChip(
                    label = if (showSystemEditor) "Hide Sys" else "Edit Sys",
                    onClick = { showSystemEditor = !showSystemEditor },
                    enabled = session.isConnected,
                    emphasized = showSystemEditor,
                )
            }
            CompactActionChip(
                label = scopeActionLabel(if (session.triggerWaveformEnabled) "Wave Off" else "Wave On", targetCount),
                onClick = { onSetTriggerWaveform(!session.triggerWaveformEnabled) },
                enabled = targetCount > 0,
            )
            if (session.triggerWaveformCapturePath.isNotBlank()) {
                CompactActionChip(
                    label = "Wave Path",
                    onClick = onShowTriggerWaveformPath,
                    enabled = true,
                )
            }
        }

        if (showStreamEditor) {
            OutlinedTextField(
                value = requestedRateText,
                onValueChange = { next ->
                    if (next.all { it.isDigit() }) {
                        requestedRateText = next.take(6)
                    }
                },
                label = { Text("FS / ephys rate (0 = Off)") },
                enabled = session.isConnected,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )

            CompactActionChipFlow {
                ratePresets.forEach { rate ->
                    PreviewCompactToggleChip(
                        label = formatRateLabel(rate),
                        selected = requestedRate == rate,
                        onClick = { requestedRateText = rate.toString() },
                        enabled = session.isConnected,
                        compact = true,
                    )
                }
            }

            CompactActionChipFlow {
                PreviewCompactToggleChip(
                    label = if (cameraEnabled) "Cam 16" else "Cam Off",
                    selected = cameraEnabled,
                    onClick = { cameraEnabled = !cameraEnabled },
                    enabled = session.isConnected && parsed != null,
                    compact = true,
                )
                PreviewCompactToggleChip(
                    label = if (adcEnabled) "ADC 160k" else "ADC Off",
                    selected = adcEnabled,
                    onClick = { adcEnabled = !adcEnabled },
                    enabled = session.isConnected && parsed != null,
                    compact = true,
                )
                CompactActionChip(
                    label = scopeActionLabel(applyLabel, targetCount),
                    onClick = {
                        val rate = requestedRate ?: return@CompactActionChip
                        if (parsed != null) {
                            onApplyStreamRates(rate, cameraEnabled, adcEnabled)
                        } else {
                            onQuickSetFs(rate)
                        }
                    },
                    enabled = targetCount > 0 && session.isConnected && requestedRate != null && requestedRate in 0..0xFFFF,
                    emphasized = true,
                )
            }
        }

        if (session.triggerWaveformCapturePath.isNotBlank()) {
            Text(
                "Waveform file: ${compactPathLabel(session.triggerWaveformCapturePath)}${if (session.triggerWaveformCaptureActive) " (capturing)" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (parsed != null && showSystemEditor) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = vbattThresholdText,
                    onValueChange = { next ->
                        if (next.all { it.isDigit() }) {
                            vbattThresholdText = next.take(5)
                        }
                    },
                    label = { Text("VBatt raw") },
                    enabled = session.isConnected,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = audioRatioText,
                    onValueChange = { next ->
                        if (next.all { it.isDigit() }) {
                            audioRatioText = next.take(3)
                        }
                    },
                    label = { Text("Audio ratio") },
                    enabled = session.isConnected,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }

            OutlinedTextField(
                value = cameraRatioText,
                onValueChange = { next ->
                    if (next.all { it.isDigit() }) {
                        cameraRatioText = next.take(3)
                    }
                },
                label = { Text("Camera ratio") },
                enabled = session.isConnected,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )

            CompactActionChipFlow {
                CompactActionChip(
                    label = scopeActionLabel("Apply Sys", targetCount),
                    onClick = {
                        val nextVbatt = vbattThresholdRaw ?: return@CompactActionChip
                        val nextAudioRatio = audioRatio ?: return@CompactActionChip
                        val nextCameraRatio = cameraRatio ?: return@CompactActionChip
                        onApplySystemProfileToSystem(nextVbatt, nextAudioRatio, nextCameraRatio)
                    },
                    enabled = targetCount > 0 &&
                        session.isConnected &&
                        vbattThresholdRaw != null &&
                        audioRatio != null &&
                        cameraRatio != null,
                    emphasized = true,
                )
            }
        }
    }
}

@Composable
private fun CompactPayloadFleetCard(
    session: DeviceSessionUiState,
    isActive: Boolean,
    onActivate: () -> Unit,
    onReadSystem: () -> Unit,
    onReadDsp: () -> Unit,
    onReadAll: () -> Unit,
    onUploadSystem: () -> Unit,
    onUploadDsp: (Int) -> Unit,
    onUploadAll: () -> Unit,
    onApplyStreamRates: (Int, Boolean, Boolean) -> Unit,
    onQuickSetFs: (Int) -> Unit,
    onApplySystemProfile: (Int, Int, Int) -> Unit,
    onSetTriggerWaveform: (Boolean) -> Unit,
    onShowTriggerWaveformPath: () -> Unit,
) {
    val parsed = session.parsedSystemParams
    val payloadReady = parsed != null || session.systemParamHex.isNotBlank()
    val dsp1Ready = session.parsedDsp1Params != null && session.dsp1ParamHex.isNotBlank()
    val dsp2Ready = session.parsedDsp2Params != null && session.dsp2ParamHex.isNotBlank()
    val fullPayloadReady =
        payloadReady &&
            dsp1Ready &&
            dsp2Ready
    val latestLine = payloadSessionSummaryLine(
        session = session,
        payloadReady = payloadReady || fullPayloadReady,
        fallback = "Read this device to load its system and DSP payloads.",
    )
    val fsLabel = formatRateLabel(parsed?.ephysSamplingRate)
    val cameraLabel = if (parsed?.cameraEnabled == true) {
        "${parsed.cameraSamplingRate} Hz"
    } else {
        "Off"
    }
    val adcLabel = if (parsed?.adcEnabled == true) {
        "${parsed.adcSamplingRate} Hz"
    } else {
        "Off"
    }
    var showStreamEditor by rememberSaveable(session.id) { mutableStateOf(false) }
    var showSystemEditor by rememberSaveable(session.id) { mutableStateOf(false) }
    var requestedRateText by remember(session.id) {
        mutableStateOf(parsed?.ephysSamplingRate?.takeIf { it >= 0 }?.toString() ?: "1250")
    }
    var cameraEnabled by remember(session.id) {
        mutableStateOf(parsed?.cameraEnabled == true)
    }
    var adcEnabled by remember(session.id) {
        mutableStateOf(parsed?.adcEnabled == true)
    }
    var vbattThresholdText by remember(session.id) {
        mutableStateOf(parsed?.vbattThresholdRaw?.toString() ?: "330")
    }
    var audioRatioText by remember(session.id) {
        mutableStateOf(parsed?.audioRatio?.toString() ?: "0")
    }
    var cameraRatioText by remember(session.id) {
        mutableStateOf(parsed?.cameraRatio?.toString() ?: "0")
    }
    LaunchedEffect(session.id, parsed?.ephysSamplingRate, parsed?.cameraEnabled, parsed?.adcEnabled) {
        requestedRateText = parsed?.ephysSamplingRate?.takeIf { it >= 0 }?.toString() ?: requestedRateText
        cameraEnabled = parsed?.cameraEnabled == true
        adcEnabled = parsed?.adcEnabled == true
    }
    LaunchedEffect(session.id, parsed?.vbattThresholdRaw, parsed?.audioRatio, parsed?.cameraRatio) {
        if (parsed != null) {
            vbattThresholdText = parsed.vbattThresholdRaw.toString()
            audioRatioText = parsed.audioRatio.toString()
            cameraRatioText = parsed.cameraRatio.toString()
        }
    }
    val requestedRate = requestedRateText.toIntOrNull()
    val vbattThresholdRaw = vbattThresholdText.toIntOrNull()
    val audioRatio = audioRatioText.toIntOrNull()
    val cameraRatio = cameraRatioText.toIntOrNull()
    val ratePresets = (listOfNotNull(parsed?.ephysSamplingRate?.takeIf { it >= 0 }) + Ce32Protocol.CommonEphysRates)
        .distinct()
        .sorted()

    ControlCard(
        title = session.name,
        subtitle = "",
    ) {
        CompactHubMetricBand(
            metrics = buildList {
                add("System" to if (payloadReady) "READY" else "READ")
                add("Full" to if (fullPayloadReady) "READY" else "READ")
                add("FS" to fsLabel)
                add("Wave" to if (session.triggerWaveformEnabled) "ON" else "OFF")
                add("Cam" to cameraLabel)
                add("ADC" to adcLabel)
                add("State" to compactSessionStateLabel(session))
                if (parsed != null) {
                    add("VBatt" to parsed.vbattThresholdRaw.toString())
                    add("Audio" to parsed.audioRatio.toString())
                    add("CamR" to parsed.cameraRatio.toString())
                }
            },
        )

        Text(
            latestLine,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        CompactActionChipFlow {
            if (!isActive) {
                CompactActionChip(
                    label = "Select",
                    onClick = onActivate,
                    enabled = true,
                )
            }
            CompactActionChip(
                label = "Read Sys",
                onClick = onReadSystem,
                enabled = session.isConnected,
            )
            CompactActionChip(
                label = "Read DSP",
                onClick = onReadDsp,
                enabled = session.isConnected,
            )
            CompactActionChip(
                label = "Read All",
                onClick = onReadAll,
                enabled = session.isConnected,
            )
            CompactActionChip(
                label = "Push Sys",
                onClick = onUploadSystem,
                enabled = payloadReady && session.isConnected,
            )
            CompactActionChip(
                label = "Push D1",
                onClick = { onUploadDsp(0) },
                enabled = dsp1Ready && session.isConnected,
            )
            CompactActionChip(
                label = "Push D2",
                onClick = { onUploadDsp(1) },
                enabled = dsp2Ready && session.isConnected,
            )
            CompactActionChip(
                label = "Push All",
                onClick = onUploadAll,
                enabled = fullPayloadReady && session.isConnected,
            )
            CompactActionChip(
                label = if (showStreamEditor) "Hide FS" else "Edit FS",
                onClick = { showStreamEditor = !showStreamEditor },
                enabled = session.isConnected,
                emphasized = showStreamEditor,
            )
            if (parsed != null) {
                CompactActionChip(
                    label = if (showSystemEditor) "Hide Sys" else "Edit Sys",
                    onClick = { showSystemEditor = !showSystemEditor },
                    enabled = session.isConnected,
                    emphasized = showSystemEditor,
                )
            }
            CompactActionChip(
                label = if (session.triggerWaveformEnabled) "Wave Off" else "Wave On",
                onClick = { onSetTriggerWaveform(!session.triggerWaveformEnabled) },
                enabled = session.isConnected,
            )
            if (session.triggerWaveformCapturePath.isNotBlank()) {
                CompactActionChip(
                    label = "Wave Path",
                    onClick = onShowTriggerWaveformPath,
                    enabled = true,
                )
            }
        }

        if (session.triggerWaveformCapturePath.isNotBlank()) {
            Text(
                "Waveform file: ${compactPathLabel(session.triggerWaveformCapturePath)}${if (session.triggerWaveformCaptureActive) " (capturing)" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (showStreamEditor) {
            OutlinedTextField(
                value = requestedRateText,
                onValueChange = { next ->
                    if (next.all { it.isDigit() }) {
                        requestedRateText = next.take(6)
                    }
                },
                label = { Text("FS / ephys rate (0 = Off)") },
                enabled = session.isConnected,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )

            CompactActionChipFlow {
                ratePresets.forEach { rate ->
                    PreviewCompactToggleChip(
                        label = formatRateLabel(rate),
                        selected = requestedRate == rate,
                        onClick = { requestedRateText = rate.toString() },
                        enabled = session.isConnected,
                        compact = true,
                    )
                }
            }

            CompactActionChipFlow {
                PreviewCompactToggleChip(
                    label = if (cameraEnabled) "Cam 16" else "Cam Off",
                    selected = cameraEnabled,
                    onClick = { cameraEnabled = !cameraEnabled },
                    enabled = session.isConnected && parsed != null,
                    compact = true,
                )
                PreviewCompactToggleChip(
                    label = if (adcEnabled) "ADC 160k" else "ADC Off",
                    selected = adcEnabled,
                    onClick = { adcEnabled = !adcEnabled },
                    enabled = session.isConnected && parsed != null,
                    compact = true,
                )
                CompactActionChip(
                    label = "Quick FS",
                    onClick = {
                        val nextRate = requestedRate ?: return@CompactActionChip
                        onQuickSetFs(nextRate)
                    },
                    enabled = session.isConnected && requestedRate != null && requestedRate in 0..0xFFFF,
                )
                CompactActionChip(
                    label = "Apply Stream",
                    onClick = {
                        val nextRate = requestedRate ?: return@CompactActionChip
                        onApplyStreamRates(nextRate, cameraEnabled, adcEnabled)
                    },
                    enabled = session.isConnected && parsed != null && requestedRate != null && requestedRate in 0..0xFFFF,
                    emphasized = true,
                )
            }
        }

        if (showSystemEditor && parsed != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = vbattThresholdText,
                    onValueChange = { next ->
                        if (next.all { it.isDigit() }) {
                            vbattThresholdText = next.take(5)
                        }
                    },
                    label = { Text("VBatt raw") },
                    enabled = session.isConnected,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = audioRatioText,
                    onValueChange = { next ->
                        if (next.all { it.isDigit() }) {
                            audioRatioText = next.take(3)
                        }
                    },
                    label = { Text("Audio ratio") },
                    enabled = session.isConnected,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }

            OutlinedTextField(
                value = cameraRatioText,
                onValueChange = { next ->
                    if (next.all { it.isDigit() }) {
                        cameraRatioText = next.take(3)
                    }
                },
                label = { Text("Camera ratio") },
                enabled = session.isConnected,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )

            CompactActionChipFlow {
                CompactActionChip(
                    label = "Apply Sys",
                    onClick = {
                        val nextVbatt = vbattThresholdRaw ?: return@CompactActionChip
                        val nextAudioRatio = audioRatio ?: return@CompactActionChip
                        val nextCameraRatio = cameraRatio ?: return@CompactActionChip
                        onApplySystemProfile(nextVbatt, nextAudioRatio, nextCameraRatio)
                    },
                    enabled = session.isConnected &&
                        vbattThresholdRaw != null &&
                        audioRatio != null &&
                        cameraRatio != null,
                    emphasized = true,
                )
            }
        }
    }
}

@Composable
private fun SignalRecordsShortcutCard(
    session: DeviceSessionUiState,
    subtitle: String = "",
    onRefreshRecords: () -> Unit,
    onExportAllRecords: () -> Unit,
    onShowRecordExportPath: () -> Unit,
    onOpenRecords: () -> Unit,
) {
    val totalSizeMb = session.records.sumOf { it.sizeMb }
    val hasRecords = session.records.isNotEmpty()

    ControlCard(
        title = "Records",
        subtitle = subtitle,
    ) {
        CompactHubMetricBand(
            metrics = listOf(
                "Records" to session.records.size.toString(),
                "Total" to "%.2f MB".format(totalSizeMb),
                "Used" to formatUsedSpaceLabel(session.usedSpaceMb),
            ),
        )

        if (session.recordExportPath.isNotBlank()) {
            Text(
                "Export folder: ${compactPathLabel(session.recordExportPath)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        CompactActionChipFlow {
            CompactActionChip(
                label = "Refresh",
                onClick = onRefreshRecords,
                enabled = session.isConnected,
                emphasized = true,
            )
            CompactActionChip(
                label = "CSV",
                onClick = onExportAllRecords,
                enabled = hasRecords,
            )
            CompactActionChip(
                label = "Index",
                onClick = onOpenRecords,
                enabled = session.isConnected || hasRecords,
            )
            CompactActionChip(
                label = "Folder",
                onClick = onShowRecordExportPath,
                enabled = session.isConnected || hasRecords,
            )
        }
    }
}

@Composable
private fun CompactRecordFleetCard(
    session: DeviceSessionUiState,
    isActive: Boolean,
    onActivate: () -> Unit,
    onRefresh: () -> Unit,
    onExportAll: () -> Unit,
    onOpenRecords: () -> Unit,
    onShowExportPath: () -> Unit,
) {
    val totalSizeMb = session.records.sumOf { it.sizeMb }
    val latestLine = recordSessionSummaryLine(
        session = session,
        fallback = "Refresh this device to load its BLE record index.",
    )

    ControlCard(
        title = session.name,
        subtitle = "",
    ) {
        CompactHubMetricBand(
            metrics = listOf(
                "Records" to session.records.size.toString(),
                "Total" to "%.2f MB".format(totalSizeMb),
                "Rec" to formatSeconds(session.recordingSeconds),
            ),
        )

        Text(
            latestLine,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (session.recordExportPath.isNotBlank()) {
            Text(
                "Export folder: ${compactPathLabel(session.recordExportPath)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        CompactActionChipFlow {
            if (!isActive) {
                CompactActionChip(
                    label = "Select",
                    onClick = onActivate,
                    enabled = true,
                )
            }
            CompactActionChip(
                label = "Refresh",
                onClick = onRefresh,
                enabled = session.isConnected,
                emphasized = true,
            )
            CompactActionChip(
                label = "CSV",
                onClick = onExportAll,
                enabled = session.records.isNotEmpty(),
            )
            CompactActionChip(
                label = "Index",
                onClick = onOpenRecords,
                enabled = session.isConnected || session.records.isNotEmpty(),
            )
            CompactActionChip(
                label = "Folder",
                onClick = onShowExportPath,
                enabled = session.isConnected || session.records.isNotEmpty(),
            )
        }
    }
}

@Composable
private fun CameraMonitorCard(
    session: DeviceSessionUiState,
    onPreviewSnapshot: () -> Unit,
    onSnapshot: () -> Unit,
    onSetCameraPreviewStreaming: (Boolean) -> Unit,
) {
    ControlCard(
        title = "Camera",
        subtitle = "",
    ) {
        CompactHubMetricBand(
            metrics = listOf(
                "Preview" to if (session.cameraPreviewStreaming) "LIVE" else if (session.cameraPreviewPixels > 0) "Cached" else "Idle",
                "Snapshot" to if (session.cameraSnapshotPixels > 0) "${session.cameraSnapshotPixels}x${session.cameraSnapshotPixels}" else "None",
                "State" to compactSessionStateLabel(session),
            ),
        )

        CompactActionChipFlow {
            CompactActionChip(
                label = if (session.cameraPreviewStreaming) "Stop Live" else "Start Live",
                onClick = { onSetCameraPreviewStreaming(!session.cameraPreviewStreaming) },
                enabled = session.isConnected,
                emphasized = session.cameraPreviewStreaming,
            )
            CompactActionChip(
                label = "Snapshot",
                onClick = onSnapshot,
                enabled = session.isConnected,
            )
            CompactActionChip(
                label = "Frame",
                onClick = onPreviewSnapshot,
                enabled = session.isConnected,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CameraFramePane(
                title = "Preview",
                pixels = session.cameraPreviewPixels,
                image = session.cameraPreviewImage,
                frameId = session.cameraPreviewFrameId,
                modifier = Modifier.weight(1f),
            )
            CameraFramePane(
                title = "Snapshot",
                pixels = session.cameraSnapshotPixels,
                image = session.cameraSnapshotImage,
                frameId = session.cameraSnapshotFrameId,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CameraFleetDeviceCard(
    session: DeviceSessionUiState,
    isActive: Boolean,
    onActivate: () -> Unit,
    onPreviewSnapshot: () -> Unit,
    onSnapshot: () -> Unit,
    onSetCameraPreviewStreaming: (Boolean) -> Unit,
    onReadCameraParams: () -> Unit,
    onSetCameraParams: (Int, Int) -> Unit,
) {
    val parsed = session.parsedCameraParams
    val summaryLine = cameraSessionSummaryLine(session)
    var showRegisterEditor by rememberSaveable(session.id) { mutableStateOf(false) }
    var reg0Text by remember(session.id) {
        mutableStateOf(parsed?.reg0?.let(::formatHexU16) ?: "0000")
    }
    var reg1Text by remember(session.id) {
        mutableStateOf(parsed?.reg1?.let(::formatHexU16) ?: "0000")
    }

    LaunchedEffect(session.id, parsed?.reg0, parsed?.reg1) {
        reg0Text = parsed?.reg0?.let(::formatHexU16) ?: reg0Text
        reg1Text = parsed?.reg1?.let(::formatHexU16) ?: reg1Text
    }

    val reg0 = parseHexU16(reg0Text)
    val reg1 = parseHexU16(reg1Text)

    ControlCard(
        title = session.name,
        subtitle = "",
    ) {
        CompactHubMetricBand(
            metrics = listOf(
                "Preview" to if (session.cameraPreviewStreaming) "LIVE" else if (session.cameraPreviewPixels > 0) "Cached" else "Idle",
                "Snapshot" to if (session.cameraSnapshotPixels > 0) "${session.cameraSnapshotPixels}x${session.cameraSnapshotPixels}" else "None",
                "State" to compactSessionStateLabel(session),
                "Reg0" to (parsed?.reg0?.let(::formatHexU16) ?: "--"),
                "Reg1" to (parsed?.reg1?.let(::formatHexU16) ?: "--"),
            ),
        )

        Text(
            summaryLine,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        CompactActionChipFlow {
            if (!isActive) {
                CompactActionChip(
                    label = "Select",
                    onClick = onActivate,
                    enabled = true,
                )
            }
            CompactActionChip(
                label = if (session.cameraPreviewStreaming) "Stop Live" else "Start Live",
                onClick = { onSetCameraPreviewStreaming(!session.cameraPreviewStreaming) },
                enabled = session.isConnected,
                emphasized = session.cameraPreviewStreaming,
            )
            CompactActionChip(
                label = "Frame",
                onClick = onPreviewSnapshot,
                enabled = session.isConnected,
            )
            CompactActionChip(
                label = "Snapshot",
                onClick = onSnapshot,
                enabled = session.isConnected,
            )
            CompactActionChip(
                label = if (showRegisterEditor) "Hide Regs" else "Edit Regs",
                onClick = { showRegisterEditor = !showRegisterEditor },
                enabled = session.isConnected,
                emphasized = showRegisterEditor,
            )
        }

        if (showRegisterEditor) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = reg0Text,
                    onValueChange = { reg0Text = sanitizeHexInput(it) },
                    label = { Text("Reg0") },
                    modifier = Modifier.weight(1f),
                    enabled = session.isConnected,
                    singleLine = true,
                )
                OutlinedTextField(
                    value = reg1Text,
                    onValueChange = { reg1Text = sanitizeHexInput(it) },
                    label = { Text("Reg1") },
                    modifier = Modifier.weight(1f),
                    enabled = session.isConnected,
                    singleLine = true,
                )
            }

            CompactActionChipFlow {
                CompactActionChip(
                    label = "Read Camera",
                    onClick = onReadCameraParams,
                    enabled = session.isConnected,
                )
                CompactActionChip(
                    label = "Apply Camera",
                    onClick = {
                        if (reg0 != null && reg1 != null) {
                            onSetCameraParams(reg0, reg1)
                        }
                    },
                    enabled = session.isConnected && reg0 != null && reg1 != null,
                    emphasized = true,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CameraFramePane(
                title = "Preview",
                pixels = session.cameraPreviewPixels,
                image = session.cameraPreviewImage,
                frameId = session.cameraPreviewFrameId,
                modifier = Modifier.weight(1f),
            )
            CameraFramePane(
                title = "Snapshot",
                pixels = session.cameraSnapshotPixels,
                image = session.cameraSnapshotImage,
                frameId = session.cameraSnapshotFrameId,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CameraFramePane(
    title: String,
    pixels: Int,
    image: ByteArray,
    frameId: Int,
    modifier: Modifier = Modifier,
) {
    val bitmap = remember(frameId, pixels, image.size) {
        createGrayscaleBitmap(image, pixels)
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, fontWeight = FontWeight.SemiBold)
            if (bitmap == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No frame",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            } else {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = title,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(Color.Black, RoundedCornerShape(16.dp)),
                )
            }
            Text(
                if (pixels > 0) "${pixels}x$pixels" else "Waiting",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            )
        }
    }
}

@Composable
private fun ConnectedMiniCard(
    session: DeviceSessionUiState,
    pane: FleetPane,
    isActive: Boolean,
    onFocus: () -> Unit,
    onStartPreview: () -> Unit,
    onStopPreview: () -> Unit,
    onSetPreviewSelection: (PreviewSelection) -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onForceStopRecording: () -> Unit,
    onPreviewFrame: () -> Unit,
    onSnapshot: () -> Unit,
    onSetCameraPreviewStreaming: (Boolean) -> Unit,
    onResync: () -> Unit,
    onResyncNoRtc: (() -> Unit)? = null,
    onImpedance: () -> Unit,
    onShowSyncLogPath: () -> Unit,
    onShowTriggerWaveformPath: () -> Unit,
    onLinkAction: () -> Unit,
) {
    val canStartPreview = canStartLivePreview(session)
    val canStopPreview = hasLivePreviewControl(session)
    val canStartRecording = canStartLiveRecording(session)
    val canStopRecording = canStopLiveRecording(session)
    val stopConfirmationPending = session.hostState == BleHostSessionState.StoppingRecording
    var expanded by rememberSaveable(session.id, pane.name) { mutableStateOf(false) }
    val detailLine = formatSyncCompactLineAscii(session.lastSyncMetric, session.liveSync)
        ?: session.bleLinkStats?.let(::formatBleLinkCompactLine)
        ?: session.syncText.ifBlank { session.lastMessage }
    val latestLine = sessionLatestActivityLine(session)
    val cameraBitmap = remember(session.cameraPreviewFrameId, session.cameraPreviewPixels, session.cameraPreviewImage.size) {
        createGrayscaleBitmap(session.cameraPreviewImage, session.cameraPreviewPixels)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(Color(session.traceColorArgb), CircleShape)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(session.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        session.statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
                if (isActive) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            "Active",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PreviewTransportButton(
                    icon = Icons.Outlined.Memory,
                    contentDescription = if (isActive) "Active device" else "Focus device",
                    enabled = !isActive,
                    filled = isActive,
                    label = if (isActive) "Active" else "Focus",
                    buttonSize = 34.dp,
                    onClick = onFocus,
                )
                when (pane) {
                    FleetPane.Operate,
                    FleetPane.Health -> {
                        PreviewTransportButton(
                            icon = Icons.Outlined.Sync,
                            contentDescription = "Resync device",
                            enabled = session.isConnected,
                            filled = false,
                            label = "Sync",
                            buttonSize = 34.dp,
                            onClick = onResync,
                        )
                    }
                    FleetPane.Camera -> {
                        PreviewTransportButton(
                            icon = if (session.cameraPreviewStreaming) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                            contentDescription = if (session.cameraPreviewStreaming) "Stop camera live" else "Start camera live",
                            enabled = session.isConnected,
                            filled = session.cameraPreviewStreaming,
                            label = if (session.cameraPreviewStreaming) "Stop camera" else "Camera",
                            buttonSize = 34.dp,
                            onClick = { onSetCameraPreviewStreaming(!session.cameraPreviewStreaming) },
                        )
                    }
                }
                PreviewTransportButton(
                    icon = Icons.Outlined.Tune,
                    contentDescription = if (expanded) "Hide extra device controls" else "Show extra device controls",
                    enabled = true,
                    filled = expanded,
                    label = if (expanded) "Less" else "Details",
                    buttonSize = 34.dp,
                    onClick = { expanded = !expanded },
                )
            }

            if (pane == FleetPane.Health) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = onResync,
                        modifier = Modifier.weight(1f),
                        enabled = session.isConnected,
                    ) {
                        Text("Resync")
                    }
                    if (SHOW_UI_DESCRIPTIONS && onResyncNoRtc != null) {
                        TextButton(
                            onClick = onResyncNoRtc,
                            modifier = Modifier.weight(1f),
                            enabled = session.isConnected,
                        ) {
                            Text("Resync (no RTC write)")
                        }
                    }
                }
                OutlinedButton(
                    onClick = onImpedance,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = session.isConnected,
                ) {
                    Text("Impedance")
                }
            }

            ConnectedDeviceInfoBand(session)

            when (pane) {
                FleetPane.Operate -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoPill("Source", sessionPreviewLabel(session), Modifier.weight(1f))
                        InfoPill("Preview", session.previewPacketCount.toString(), Modifier.weight(1f))
                        InfoPill("Rec", formatSeconds(session.recordingSeconds), Modifier.weight(1f))
                    }

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CompactDevicePreviewSourceSelector(
                            session = session,
                            onSetPreviewSelection = onSetPreviewSelection,
                        )
                        PreviewTransportButton(
                            icon = if (canStopPreview) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                            contentDescription = if (canStopPreview) "Stop live signal" else "Start live signal",
                            enabled = canStartPreview || canStopPreview,
                            filled = canStopPreview,
                            label = if (canStopPreview) "Stop live" else "Live",
                            buttonSize = 34.dp,
                            onClick = if (canStopPreview) onStopPreview else onStartPreview,
                        )
                        PreviewTransportButton(
                            icon = if (canStopRecording) Icons.Outlined.Stop else Icons.Outlined.FiberManualRecord,
                            contentDescription = when {
                                stopConfirmationPending -> "Retry pending recording stop"
                                canStopRecording -> "Stop recording"
                                else -> "Start recording"
                            },
                            enabled = canStartRecording || canStopRecording,
                            filled = canStopRecording,
                            label = if (canStopRecording) "Stop rec" else "Record",
                            tint = Color(0xFFD64545),
                            buttonSize = 34.dp,
                            onClick = if (canStopRecording) onStopRecording else onStartRecording,
                        )
                    }
                    if (expanded && detailLine.isNotBlank()) {
                        Text(
                            detailLine,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (expanded && canStopRecording) {
                        OutlinedButton(
                            onClick = onForceStopRecording,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = session.isConnected,
                        ) {
                            Text(if (stopConfirmationPending) "Retry Stop" else "Force Stop")
                        }
                    }
                }

                FleetPane.Camera -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        MiniCameraPreviewTile(
                            bitmap = cameraBitmap,
                            pixels = session.cameraPreviewPixels,
                            streaming = session.cameraPreviewStreaming,
                            modifier = Modifier.weight(1f),
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            InfoPill(
                                "Preview",
                                if (session.cameraPreviewStreaming) "LIVE" else if (session.cameraPreviewPixels > 0) "Cached" else "Idle",
                                Modifier.fillMaxWidth(),
                            )
                            InfoPill(
                                "Snapshot",
                                if (session.cameraSnapshotPixels > 0) "${session.cameraSnapshotPixels}x${session.cameraSnapshotPixels}" else "None",
                                Modifier.fillMaxWidth(),
                            )
                            if (expanded) {
                                Text(
                                    when {
                                        session.cameraPreviewStreaming -> "Remote camera monitoring is live on this device."
                                        session.cameraSnapshotPixels > 0 -> "Full snapshot is cached. Focus this device to inspect it on the camera pane."
                                        session.cameraPreviewPixels > 0 -> "Last low-resolution preview frame is cached on this card."
                                        else -> "Request a frame or start live camera monitoring for this device."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onPreviewFrame,
                            modifier = Modifier.weight(1f),
                            enabled = session.isConnected,
                        ) {
                            Text("Frame")
                        }
                        OutlinedButton(
                            onClick = onSnapshot,
                            modifier = Modifier.weight(1f),
                            enabled = session.isConnected,
                        ) {
                            Text("Snapshot")
                        }
                    }
                }

                FleetPane.Health -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoPill("RSSI", session.rssi?.let { "$it dBm" } ?: "--", Modifier.weight(1f))
                        InfoPill("Traffic", "TX ${session.commandTxCount} / RX ${session.commandRxCount}", Modifier.weight(1f))
                        InfoPill("Link", if (session.verifiedTransport) "Verified" else "Scan only", Modifier.weight(1f))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoPill("Preview", session.previewPacketCount.toString(), Modifier.weight(1f))
                        InfoPill("Wave", session.triggeredWaveformBlockCount.toString(), Modifier.weight(1f))
                        InfoPill("Capture", formatByteCountCompact(session.triggerWaveformCaptureBytes), Modifier.weight(1f))
                    }

                    FilledTonalButton(
                        onClick = onLinkAction,
                        enabled = sessionLinkActionEnabled(session),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(sessionLinkActionLabel(session))
                    }
                    if (SHOW_UI_DESCRIPTIONS) {
                        Text(
                            sessionLinkActionHelper(session),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f),
                        )
                    }

                    if (expanded && detailLine.isNotBlank()) {
                        Text(
                            detailLine,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Text(
                        "Latest: $latestLine",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    if (expanded) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = onShowSyncLogPath, modifier = Modifier.weight(1f)) {
                                Text("Sync Log")
                            }
                            OutlinedButton(
                                onClick = onShowTriggerWaveformPath,
                                modifier = Modifier.weight(1f),
                                enabled = session.triggerWaveformCapturePath.isNotBlank(),
                            ) {
                                Text("Wave Path")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConnectedDeviceInfoBand(session: DeviceSessionUiState) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        InfoPill("BLE", session.address)
        InfoPill("Battery", formatConnectedBatteryLabel(session))
        InfoPill("Used storage", formatUsedSpaceLabel(session.usedSpaceMb).let { value -> if (value == "--") "Not reported" else value })
        InfoPill("Firmware", formatDeviceFirmwareLabel(session))
    }
}

@Composable
private fun MiniCameraPreviewTile(
    bitmap: Bitmap?,
    pixels: Int,
    streaming: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Remote Cam", fontWeight = FontWeight.SemiBold)
            if (bitmap == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (streaming) "Waiting" else "No frame",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            } else {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Remote camera preview",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(Color.Black, RoundedCornerShape(14.dp)),
                )
            }
            Text(
                when {
                    pixels > 0 -> "${pixels}x$pixels"
                    streaming -> "Streaming"
                    else -> "Idle"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            )
        }
    }
}

@Composable
private fun SessionMonitorCard(
    session: DeviceSessionUiState,
    onLinkAction: () -> Unit,
    onResync: () -> Unit,
    onResyncNoRtc: (() -> Unit)? = null,
    onShowSyncLogPath: () -> Unit,
    onShowTriggerWaveformPath: () -> Unit,
) {
    val roleIdentity = formatBleRoleIdentity(session.roleTag, session.functionTag)
    ControlCard(
        title = "Health Monitor",
        subtitle = compactUiCopy("", ""),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoPill("State", compactSessionStateLabel(session), Modifier.weight(1f))
            InfoPill("RSSI", session.rssi?.let { "$it dBm" } ?: "--", Modifier.weight(1f))
            InfoPill("Wave", session.triggeredWaveformBlockCount.toString(), Modifier.weight(1f))
        }

        if (roleIdentity.isNotBlank()) {
            Text(
                "BLE identity: $roleIdentity",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
        }
        Text(
            "Sync: ${session.syncText.ifBlank { "Pending" }}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
        )
        Text(
            "Latest: ${session.lastFailure.ifBlank { session.lastMessage.ifBlank { "No recent BLE activity." } }}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
        )

        if (session.syncLogPath.isNotBlank()) {
            Text(
                "Sync log: ${compactPathLabel(session.syncLogPath)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (session.triggerWaveformCapturePath.isNotBlank()) {
            Text(
                "Waveform file: ${compactPathLabel(session.triggerWaveformCapturePath)}${if (session.triggerWaveformCaptureActive) " (capturing)" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        FilledTonalButton(
            onClick = onLinkAction,
            enabled = sessionLinkActionEnabled(session),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(sessionLinkActionLabel(session))
        }
        if (SHOW_UI_DESCRIPTIONS) {
            Text(
                sessionLinkActionHelper(session),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onResync,
                modifier = Modifier.weight(1f),
                enabled = session.isConnected,
            ) {
                Text("Resync")
            }
            if (SHOW_UI_DESCRIPTIONS && onResyncNoRtc != null) {
                OutlinedButton(
                    onClick = onResyncNoRtc,
                    modifier = Modifier.weight(1f),
                    enabled = session.isConnected,
                ) {
                    Text("Resync (no RTC write)")
                }
            }
        }

        if (SHOW_UI_DESCRIPTIONS && onResyncNoRtc != null) {
            Text(
                "Resync without RTC write resets sync without pushing a fresh host RTC value first. Use it only while inspecting recovery from diagnostics.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onShowSyncLogPath,
                modifier = Modifier.weight(1f),
                enabled = session.isConnected,
            ) {
                Text("Sync Log")
            }
            OutlinedButton(
                onClick = onShowTriggerWaveformPath,
                modifier = Modifier.weight(1f),
                enabled = session.triggerWaveformCapturePath.isNotBlank(),
            ) {
                Text("Wave Path")
            }
        }

        session.lastSyncMetric?.let { metric ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoPill("Sync", metric.modeLabel, Modifier.weight(1f))
                InfoPill(
                    "Offset",
                    if (session.liveSync != null && !session.liveSync.outlier) {
                        formatSignedMs(session.liveSync.rollingMeanMs)
                    } else {
                        formatSignedMs(metric.offsetMs)
                    },
                    Modifier.weight(1f),
                )
                InfoPill("Delay", formatMs(metric.delayMs), Modifier.weight(1f))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoPill(
                    "Acc",
                    formatMs(resolveSyncAccuracyEstimateMs(metric, session.liveSync)),
                    Modifier.weight(1f),
                )
                InfoPill(
                    "Samples",
                    (resolveSyncMeasurementCount(metric, session.liveSync) ?: 0).toString(),
                    Modifier.weight(1f),
                )
                InfoPill(
                    "Clock",
                    session.liveSync?.deviceClockLabel ?: "--",
                    Modifier.weight(1f),
                )
            }

            Text(
                session.liveSync?.let { formatLiveSyncDetailAscii(it, metric) } ?: metric.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            )
        }

        session.bleLinkStats?.let { stats ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoPill("Link RSSI", formatHexByte(stats.lastRssiRaw), Modifier.weight(1f))
                InfoPill("Loss", formatPercent(stats.lossPercent), Modifier.weight(1f))
                InfoPill("Rx Rate", formatRateHz(stats.rxRateHz), Modifier.weight(1f))
            }
            Text(
                "Packets ${stats.packetCount}  |  Missing ${stats.missingPacketCount}  |  SeqErr ${stats.sequenceErrorCount}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            )
        }

        if (SHOW_UI_DESCRIPTIONS) {
            Text(
                "Open Activity for recent BLE events, saved sync logs, and waveform capture files.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
            )
        }
    }
}

@Composable
private fun SessionActivityCard(
    session: DeviceSessionUiState,
    onLinkAction: () -> Unit,
    onResync: () -> Unit,
    onResyncNoRtc: (() -> Unit)? = null,
    onShowSyncLogPath: () -> Unit,
    onShowTriggerWaveformPath: () -> Unit,
) {
    val latestLine = sessionLatestActivityLine(session)
    ControlCard(
        title = "Session Activity",
        subtitle = compactUiCopy("", ""),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoPill("Traffic", "TX ${session.commandTxCount} / RX ${session.commandRxCount}", Modifier.weight(1f))
            InfoPill("Preview", session.previewPacketCount.toString(), Modifier.weight(1f))
            InfoPill("Wave", session.triggeredWaveformBlockCount.toString(), Modifier.weight(1f))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoPill("Rec Time", session.recTimePacketCount.toString(), Modifier.weight(1f))
            InfoPill("Record", formatSeconds(session.recordingSeconds), Modifier.weight(1f))
            InfoPill("Capture", formatByteCountCompact(session.triggerWaveformCaptureBytes), Modifier.weight(1f))
        }

        Text(
            "Latest: $latestLine",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.74f),
        )

        if (session.lastFailure.isNotBlank() && latestLine != session.lastFailure) {
            Text(
                "Failure: ${session.lastFailure}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            )
        }

        if (session.syncLogPath.isNotBlank()) {
            Text(
                "Sync log: ${compactPathLabel(session.syncLogPath)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (session.triggerWaveformCapturePath.isNotBlank()) {
            Text(
                "Waveform file: ${compactPathLabel(session.triggerWaveformCapturePath)}${if (session.triggerWaveformCaptureActive) " (capturing)" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        FilledTonalButton(
            onClick = onLinkAction,
            enabled = sessionLinkActionEnabled(session),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(sessionLinkActionLabel(session))
        }
        if (SHOW_UI_DESCRIPTIONS) {
            Text(
                sessionLinkActionHelper(session),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onResync,
                modifier = Modifier.weight(1f),
                enabled = session.isConnected,
            ) {
                Text("Resync")
            }
            if (SHOW_UI_DESCRIPTIONS && onResyncNoRtc != null) {
                OutlinedButton(
                    onClick = onResyncNoRtc,
                    modifier = Modifier.weight(1f),
                    enabled = session.isConnected,
                ) {
                    Text("Resync (no RTC write)")
                }
            }
        }

        if (SHOW_UI_DESCRIPTIONS && onResyncNoRtc != null) {
            Text(
                "Resync without RTC write keeps the sync-recovery path available while you inspect recent session activity.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onShowSyncLogPath,
                modifier = Modifier.weight(1f),
                enabled = session.isConnected,
            ) {
                Text("Sync Log")
            }
            OutlinedButton(
                onClick = onShowTriggerWaveformPath,
                modifier = Modifier.weight(1f),
                enabled = session.triggerWaveformCapturePath.isNotBlank(),
            ) {
                Text("Wave Path")
            }
        }

        if (session.recentEvents.isEmpty()) {
            Text(
                compactUiCopy(
                    "Recent events will appear here after commands, sync activity, and device responses.",
                    "No events yet.",
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                session.recentEvents.asReversed().take(10).forEachIndexed { index, event ->
                    SessionEventRow(event)
                    if (index < minOf(session.recentEvents.size, 10) - 1) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionLinkCard(
    session: DeviceSessionUiState,
    onLinkAction: () -> Unit,
    onResync: () -> Unit,
    onResyncNoRtc: (() -> Unit)? = null,
    onShowSyncLogPath: () -> Unit,
    onShowTriggerWaveformPath: () -> Unit,
) {
    val linkStats = session.bleLinkStats
    val recentLine = session.recentEvents.lastOrNull()?.summary
        ?: session.lastFailure.ifBlank { session.lastMessage.ifBlank { "No recent BLE transport activity." } }
    val syncLine = formatSyncCompactLineAscii(session.lastSyncMetric, session.liveSync)
        ?: session.syncText.ifBlank { "Sync pending" }

    ControlCard(
        title = "BLE Link",
        subtitle = compactUiCopy("", ""),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoPill("State", compactSessionStateLabel(session), Modifier.weight(1f))
            InfoPill("RSSI", session.rssi?.let { "$it dBm" } ?: "--", Modifier.weight(1f))
            InfoPill("TX/RX", "${session.commandTxCount}/${session.commandRxCount}", Modifier.weight(1f))
        }

        if (linkStats != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoPill("Link RSSI", formatHexByte(linkStats.lastRssiRaw), Modifier.weight(1f))
                InfoPill("Loss", formatPercent(linkStats.lossPercent), Modifier.weight(1f))
                InfoPill("Rx Rate", formatRateHz(linkStats.rxRateHz), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoPill("Packets", linkStats.packetCount.toString(), Modifier.weight(1f))
                InfoPill("Missing", linkStats.missingPacketCount.toString(), Modifier.weight(1f))
                InfoPill("SeqErr", linkStats.sequenceErrorCount.toString(), Modifier.weight(1f))
            }
        } else {
            Text(
                compactUiCopy(
                    "Link-test packets have not arrived yet. Once the transport is active, packet loss and RSSI telemetry will appear here.",
                    "Waiting for link stats.",
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
        }

        Text(
            syncLine,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
        )
        Text(
            "Latest: $recentLine",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
        )

        if (session.syncLogPath.isNotBlank()) {
            Text(
                "Sync log: ${compactPathLabel(session.syncLogPath)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (session.triggerWaveformCapturePath.isNotBlank()) {
            Text(
                "Waveform file: ${compactPathLabel(session.triggerWaveformCapturePath)}${if (session.triggerWaveformCaptureActive) " (capturing)" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        FilledTonalButton(
            onClick = onLinkAction,
            enabled = sessionLinkActionEnabled(session),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(sessionLinkActionLabel(session))
        }
        if (SHOW_UI_DESCRIPTIONS) {
            Text(
                sessionLinkActionHelper(session),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onResync,
                modifier = Modifier.weight(1f),
                enabled = session.isConnected,
            ) {
                Text("Resync")
            }
            if (SHOW_UI_DESCRIPTIONS && onResyncNoRtc != null) {
                OutlinedButton(
                    onClick = onResyncNoRtc,
                    modifier = Modifier.weight(1f),
                    enabled = session.isConnected,
                ) {
                    Text("Resync (no RTC write)")
                }
            }
        }

        if (SHOW_UI_DESCRIPTIONS && onResyncNoRtc != null) {
            Text(
                "Resync without RTC write keeps the sync-recovery path available here for transport troubleshooting.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onShowSyncLogPath,
                modifier = Modifier.weight(1f),
                enabled = session.isConnected,
            ) {
                Text("Sync Log")
            }
            OutlinedButton(
                onClick = onShowTriggerWaveformPath,
                modifier = Modifier.weight(1f),
                enabled = session.triggerWaveformCapturePath.isNotBlank(),
            ) {
                Text("Wave Path")
            }
        }
    }
}

@Composable
private fun CompactSessionMonitorCard(
    session: DeviceSessionUiState,
    isActive: Boolean,
    onActivate: () -> Unit,
    onLinkAction: () -> Unit,
    onResync: () -> Unit,
    onResyncNoRtc: (() -> Unit)? = null,
    onShowSyncLogPath: () -> Unit,
    onShowTriggerWaveformPath: () -> Unit,
) {
    val syncLine = formatSyncCompactLineAscii(session.lastSyncMetric, session.liveSync)
        ?: session.syncText.ifBlank { "Sync pending" }
    val latestLine = session.lastFailure.ifBlank {
        session.recentEvents.lastOrNull()?.summary ?: session.lastMessage.ifBlank { "No recent BLE activity." }
    }

    ControlCard(
        title = session.name,
        subtitle = "",
    ) {
        CompactHubMetricBand(
            metrics = buildList {
                add("State" to session.statusText)
                add("RSSI" to (session.rssi?.let { "$it dBm" } ?: "--"))
                add("Wave" to session.triggeredWaveformBlockCount.toString())
                add("Pkt" to "${session.commandTxCount}/${session.commandRxCount}")
            },
        )

        Text(
            syncLine,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
        )
        Text(
            latestLine,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        CompactActionChipFlow {
            CompactActionChip(
                label = sessionLinkActionLabel(session),
                onClick = onLinkAction,
                enabled = sessionLinkActionEnabled(session),
                emphasized = true,
            )
            if (!isActive) {
                CompactActionChip(
                    label = "Select",
                    onClick = onActivate,
                    enabled = true,
                )
            }
            CompactActionChip(
                label = "Sync",
                onClick = onResync,
                enabled = session.isConnected,
            )
            if (SHOW_UI_DESCRIPTIONS && onResyncNoRtc != null) {
                CompactActionChip(
                    label = "Resync (no RTC write)",
                    onClick = onResyncNoRtc,
                    enabled = session.isConnected,
                )
            }
            CompactActionChip(
                label = "Log",
                onClick = onShowSyncLogPath,
                enabled = session.isConnected,
            )
            CompactActionChip(
                label = "Wave",
                onClick = onShowTriggerWaveformPath,
                enabled = session.triggerWaveformCapturePath.isNotBlank(),
            )
        }
    }
}

@Composable
private fun CompactSessionLinkCard(
    session: DeviceSessionUiState,
    isActive: Boolean,
    onActivate: () -> Unit,
    onLinkAction: () -> Unit,
    onResync: () -> Unit,
    onResyncNoRtc: (() -> Unit)? = null,
    onShowSyncLogPath: () -> Unit,
    onShowTriggerWaveformPath: () -> Unit,
) {
    val linkStats = session.bleLinkStats
    val latestLine = session.lastFailure.ifBlank {
        session.recentEvents.lastOrNull()?.summary ?: session.lastMessage.ifBlank { "No recent BLE transport activity." }
    }
    val syncLine = formatSyncCompactLineAscii(session.lastSyncMetric, session.liveSync)
        ?: session.syncText.ifBlank { "Sync pending" }

    ControlCard(
        title = session.name,
        subtitle = "",
    ) {
        CompactHubMetricBand(
            metrics = buildList {
                add("State" to session.statusText)
                add("RSSI" to (session.rssi?.let { "$it dBm" } ?: "--"))
                add("Loss" to (linkStats?.let { formatPercent(it.lossPercent) } ?: "--"))
                add("Pkt" to "${session.commandTxCount}/${session.commandRxCount}")
            },
        )

        Text(
            linkStats?.let(::formatBleLinkCompactLine) ?: "Link-test packets have not arrived yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
        )
        Text(
            syncLine,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
        )
        Text(
            latestLine,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        CompactActionChipFlow {
            CompactActionChip(
                label = sessionLinkActionLabel(session),
                onClick = onLinkAction,
                enabled = sessionLinkActionEnabled(session),
                emphasized = true,
            )
            if (!isActive) {
                CompactActionChip(
                    label = "Select",
                    onClick = onActivate,
                    enabled = true,
                )
            }
            CompactActionChip(
                label = "Sync",
                onClick = onResync,
                enabled = session.isConnected,
            )
            if (SHOW_UI_DESCRIPTIONS && onResyncNoRtc != null) {
                CompactActionChip(
                    label = "Resync (no RTC write)",
                    onClick = onResyncNoRtc,
                    enabled = session.isConnected,
                )
            }
            CompactActionChip(
                label = "Log",
                onClick = onShowSyncLogPath,
                enabled = session.isConnected,
            )
            CompactActionChip(
                label = "Wave",
                onClick = onShowTriggerWaveformPath,
                enabled = session.triggerWaveformCapturePath.isNotBlank(),
            )
        }
    }
}

@Composable
private fun CompactSessionActivityCard(
    session: DeviceSessionUiState,
    isActive: Boolean,
    onActivate: () -> Unit,
    onLinkAction: () -> Unit,
    onResync: () -> Unit,
    onResyncNoRtc: (() -> Unit)? = null,
    onShowSyncLogPath: () -> Unit,
    onShowTriggerWaveformPath: () -> Unit,
) {
    val latestLine = sessionLatestActivityLine(session)

    ControlCard(
        title = session.name,
        subtitle = "",
    ) {
        CompactHubMetricBand(
            metrics = buildList {
                add("TX/RX" to "${session.commandTxCount}/${session.commandRxCount}")
                add("Preview" to session.previewPacketCount.toString())
                add("Wave" to session.triggeredWaveformBlockCount.toString())
                add("Cap" to formatByteCountCompact(session.triggerWaveformCaptureBytes))
            },
        )

        Text(
            latestLine,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        CompactActionChipFlow {
            CompactActionChip(
                label = sessionLinkActionLabel(session),
                onClick = onLinkAction,
                enabled = sessionLinkActionEnabled(session),
                emphasized = true,
            )
            if (!isActive) {
                CompactActionChip(
                    label = "Select",
                    onClick = onActivate,
                    enabled = true,
                )
            }
            CompactActionChip(
                label = "Sync",
                onClick = onResync,
                enabled = session.isConnected,
            )
            if (SHOW_UI_DESCRIPTIONS && onResyncNoRtc != null) {
                CompactActionChip(
                    label = "Resync (no RTC write)",
                    onClick = onResyncNoRtc,
                    enabled = session.isConnected,
                )
            }
            CompactActionChip(
                label = "Log",
                onClick = onShowSyncLogPath,
                enabled = session.isConnected,
            )
            CompactActionChip(
                label = "Wave",
                onClick = onShowTriggerWaveformPath,
                enabled = session.triggerWaveformCapturePath.isNotBlank(),
            )
        }
    }
}

@Composable
private fun SessionEventRow(event: SessionEventUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            formatEventTime(event.timestampMs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            event.summary,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
        )
    }
}

private fun isPreviewingSession(session: DeviceSessionUiState): Boolean {
    return session.hostState in setOf(
        BleHostSessionState.Previewing,
        BleHostSessionState.StartingRecording,
        BleHostSessionState.Recording,
        BleHostSessionState.StoppingRecording,
    )
}

private fun scopedSessions(uiState: WildUiState): List<DeviceSessionUiState> {
    return when (uiState.controlScope) {
        ControlScope.ActiveDevice -> listOfNotNull(uiState.activeSession).filter { it.isConnected }
        ControlScope.SelectedDevices -> uiState.selectedConnectedSessions
        ControlScope.AllConnected -> uiState.connectedSessions
    }
}

private fun preservedScopedSessions(uiState: WildUiState): List<DeviceSessionUiState> {
    return when (uiState.controlScope) {
        ControlScope.ActiveDevice -> listOfNotNull(uiState.activeSession)
        ControlScope.SelectedDevices -> uiState.selectedSessions
        ControlScope.AllConnected -> uiState.connectedSessions
    }
}

private fun cameraDisplayScopeSessions(uiState: WildUiState): List<DeviceSessionUiState> {
    return when (uiState.controlScope) {
        ControlScope.ActiveDevice -> listOfNotNull(uiState.activeSession)
        ControlScope.SelectedDevices -> uiState.selectedSessions
        ControlScope.AllConnected -> uiState.connectedSessions
    }
}

private fun payloadDisplayScopeSessions(uiState: WildUiState): List<DeviceSessionUiState> {
    return when (uiState.controlScope) {
        ControlScope.ActiveDevice -> listOfNotNull(uiState.activeSession)
        ControlScope.SelectedDevices -> uiState.selectedSessions
        ControlScope.AllConnected -> uiState.connectedSessions
    }
}

private fun recordDisplayScopeSessions(uiState: WildUiState): List<DeviceSessionUiState> {
    return when (uiState.controlScope) {
        ControlScope.ActiveDevice -> listOfNotNull(uiState.activeSession)
        ControlScope.SelectedDevices -> uiState.selectedSessions
        ControlScope.AllConnected -> uiState.connectedSessions
    }
}

private fun recordExportScopeSessions(uiState: WildUiState): List<DeviceSessionUiState> {
    return when (uiState.controlScope) {
        ControlScope.ActiveDevice -> listOfNotNull(uiState.activeSession)
        ControlScope.SelectedDevices -> uiState.selectedSessions
        ControlScope.AllConnected -> uiState.connectedSessions
    }
}

private fun controlScopeReconnectNote(
    scope: ControlScope,
    preservedScopeCount: Int,
    linkedScopeCount: Int,
    activeSessionName: String?,
): String? {
    return when {
        scope == ControlScope.ActiveDevice &&
            activeSessionName != null &&
            preservedScopeCount > linkedScopeCount ->
            "Active control device relinking. Scope writes wait for reconnect."
        scope == ControlScope.SelectedDevices &&
            preservedScopeCount > linkedScopeCount &&
            linkedScopeCount == 0 ->
            "Selected control group relinking. Scope writes wait for one reconnect."
        scope == ControlScope.SelectedDevices &&
            preservedScopeCount > linkedScopeCount ->
            "Scope writes target $linkedScopeCount linked device(s)."
        else -> null
    }
}

private fun recordScopeReconnectNote(
    scope: ControlScope,
    preservedScopeCount: Int,
    linkedScopeCount: Int,
    exportReadyCount: Int,
    activeSessionName: String?,
): String? {
    return when {
        scope == ControlScope.ActiveDevice &&
            activeSessionName != null &&
            preservedScopeCount > linkedScopeCount ->
            if (exportReadyCount > 0) {
                "Active record device relinking. Cached export still ready."
            } else {
                "Active record device relinking. Refresh waits for reconnect."
            }
        scope == ControlScope.SelectedDevices &&
            preservedScopeCount > linkedScopeCount &&
            linkedScopeCount == 0 ->
            if (exportReadyCount > 0) {
                "Selected record group relinking. Cached exports still ready."
            } else {
                "Selected record group relinking. Refresh waits for one reconnect."
            }
        scope == ControlScope.SelectedDevices &&
            preservedScopeCount > linkedScopeCount ->
            if (exportReadyCount > 0) {
                "Refresh targets $linkedScopeCount linked device(s). Cached exports still ready."
            } else {
                "Refresh targets $linkedScopeCount linked device(s)."
            }
        else -> null
    }
}

private fun onlineScopeReconnectNote(
    scope: ControlScope,
    preservedScopeCount: Int,
    linkedScopeCount: Int,
    activeSessionName: String?,
): String? {
    return when {
        scope == ControlScope.ActiveDevice &&
            activeSessionName != null &&
            preservedScopeCount > linkedScopeCount ->
            "Active online device relinking. Online tools wait for reconnect."
        scope == ControlScope.SelectedDevices &&
            preservedScopeCount > linkedScopeCount &&
            linkedScopeCount == 0 ->
            "Selected online group relinking. Tools wait for one reconnect."
        scope == ControlScope.SelectedDevices &&
            preservedScopeCount > linkedScopeCount ->
            "Online tools target $linkedScopeCount linked device(s)."
        else -> null
    }
}

private fun cameraScopeReconnectNote(
    scope: ControlScope,
    preservedScopeCount: Int,
    linkedScopeCount: Int,
    cachedFrameCount: Int,
    activeSessionName: String?,
): String? {
    return when {
        scope == ControlScope.ActiveDevice &&
            activeSessionName != null &&
            preservedScopeCount > linkedScopeCount ->
            if (cachedFrameCount > 0) {
                "Active camera device relinking. Cached frames still visible."
            } else {
                "Active camera device relinking. Live camera waits for BLE."
            }
        scope == ControlScope.SelectedDevices &&
            preservedScopeCount > linkedScopeCount &&
            linkedScopeCount == 0 ->
            if (cachedFrameCount > 0) {
                "Selected camera group relinking. Cached frames still visible."
            } else {
                "Selected camera group relinking. Live camera waits for one reconnect."
            }
        scope == ControlScope.SelectedDevices &&
            preservedScopeCount > linkedScopeCount ->
            if (cachedFrameCount > 0) {
                "Live camera targets $linkedScopeCount linked device(s). Cached frames stay visible."
            } else {
                "Live camera targets $linkedScopeCount linked device(s)."
            }
        else -> null
    }
}

private fun sessionHasPayloadCache(session: DeviceSessionUiState): Boolean {
    return session.parsedSystemParams != null ||
        session.systemParamHex.isNotBlank() ||
        session.parsedDsp1Params != null ||
        session.dsp1ParamHex.isNotBlank() ||
        session.parsedDsp2Params != null ||
        session.dsp2ParamHex.isNotBlank()
}

internal fun sessionHasControlLaunchCorePayload(session: DeviceSessionUiState): Boolean {
    return (session.parsedSystemParams != null || session.systemParamHex.isNotBlank()) &&
        (session.parsedDsp1Params != null || session.dsp1ParamHex.isNotBlank()) &&
        (session.parsedDsp2Params != null || session.dsp2ParamHex.isNotBlank())
}

internal fun sessionHasCameraControlPayload(session: DeviceSessionUiState): Boolean {
    return session.parsedCameraParams != null || session.cameraParamHex.isNotBlank()
}

internal fun shouldReadCameraDuringControlLaunch(session: DeviceSessionUiState): Boolean {
    return session.isConnected &&
        sessionHasControlLaunchCorePayload(session) &&
        !sessionHasCameraControlPayload(session)
}

private fun sessionHasCameraCache(session: DeviceSessionUiState): Boolean {
    return session.cameraPreviewPixels > 0 || session.cameraSnapshotPixels > 0
}

private fun onlinePayloadLaneSummary(
    session: DeviceSessionUiState,
    targetCount: Int,
    preservedScopeCount: Int,
    systemReadyCount: Int,
    payloadCachedCount: Int,
): String {
    return when {
        targetCount > 0 -> buildString {
            append(readinessValue(systemReadyCount, targetCount))
            session.parsedSystemParams?.ephysSamplingRate?.let { rate ->
                append(" | ")
                append(formatRateLabel(rate))
            }
        }
        payloadCachedCount > 0 -> buildString {
            append("Cached")
            session.parsedSystemParams?.ephysSamplingRate?.let { rate ->
                append(" | ")
                append(formatRateLabel(rate))
            }
            if (preservedScopeCount > 1) {
                append(" | ")
                append("$payloadCachedCount/$preservedScopeCount")
            }
        }
        else -> "Relinking"
    }
}

private fun onlineCameraLaneSummary(
    targetCount: Int,
    preservedScopeCount: Int,
    cameraLiveCount: Int,
    cameraCachedCount: Int,
): String {
    return when {
        targetCount > 0 -> {
            val displayTargetCount = if (preservedScopeCount > targetCount) preservedScopeCount else targetCount
            "${scopedCountLabel(cameraLiveCount, displayTargetCount)} live"
        }
        cameraCachedCount > 0 ->
            if (preservedScopeCount > 1) "$cameraCachedCount/$preservedScopeCount cached" else "Cached frame"
        else -> "Relinking"
    }
}

private fun onlineRecordLaneSummary(
    session: DeviceSessionUiState,
    targetCount: Int,
    preservedScopeCount: Int,
    recordReadyCount: Int,
    recordCachedCount: Int,
): String {
    return when {
        targetCount > 1 -> "${recordReadyCount}/$targetCount ready"
        targetCount == 1 -> "${session.records.size} log(s)"
        recordCachedCount > 0 ->
            if (preservedScopeCount > 1) "$recordCachedCount/$preservedScopeCount cached" else "${session.records.size} log(s)"
        else -> "Relinking"
    }
}

private fun targetStripSessions(uiState: WildUiState): List<DeviceSessionUiState> {
    val sessionsById = uiState.sessions.associateBy { it.id }
    return linkedSetOf<String>().apply {
        uiState.connectedSessions.forEach { add(it.id) }
        uiState.selectedSessions.forEach { add(it.id) }
        uiState.activeSessionId?.let { add(it) }
    }.mapNotNull { sessionsById[it] }
}

private fun hasReconnectingActiveScope(uiState: WildUiState): Boolean {
    return uiState.controlScope == ControlScope.ActiveDevice && uiState.activeSession?.isConnected == false
}

private fun hasReconnectingSelectedScope(uiState: WildUiState): Boolean {
    return uiState.controlScope == ControlScope.SelectedDevices &&
        uiState.selectedSessions.isNotEmpty() &&
        uiState.selectedConnectedSessions.isEmpty()
}

private fun recordSessionBadgeLabel(
    session: DeviceSessionUiState,
    isActive: Boolean,
): String {
    return when {
        isActive -> "Active"
        session.isConnected -> "Linked"
        session.isLinkingLike -> "Relinking"
        session.records.isNotEmpty() -> "Cached"
        else -> "Offline"
    }
}

private fun recordSessionSummaryLine(
    session: DeviceSessionUiState,
    fallback: String,
): String {
    val latestLine = session.lastFailure.ifBlank { session.lastMessage }
    if (latestLine.isNotBlank()) {
        return latestLine
    }
    return when {
        session.isConnected -> fallback
        session.isLinkingLike && session.records.isNotEmpty() ->
            "Relinking. Cached record export still ready."
        session.isLinkingLike ->
            "Relinking. Refresh waits for BLE."
        session.records.isNotEmpty() ->
            "Offline. Cached record export still ready."
        else -> fallback
    }
}

private fun cameraSessionSummaryLine(session: DeviceSessionUiState): String {
    val hasCachedPreview = session.cameraPreviewPixels > 0
    val hasCachedSnapshot = session.cameraSnapshotPixels > 0
    val hasCachedFrames = hasCachedPreview || hasCachedSnapshot
    return when {
        session.isConnected && session.cameraPreviewStreaming ->
            "Remote camera live."
        session.isConnected && hasCachedFrames ->
            "Cached frame ready."
        session.isConnected ->
            "Ready for live, frame, or snapshot."
        session.isLinkingLike && hasCachedFrames ->
            "Relinking. Cached frames still visible."
        session.isLinkingLike ->
            "Relinking. Live camera waits for BLE."
        hasCachedFrames ->
            "Offline. Cached frame still visible."
        else -> "No cached frames. Reconnect to capture."
    }
}

private fun payloadSessionSummaryLine(
    session: DeviceSessionUiState,
    payloadReady: Boolean,
    fallback: String,
): String {
    val latestLine = session.lastFailure.ifBlank { sessionLatestActivityLine(session) }
    if (latestLine.isNotBlank()) {
        return latestLine
    }
    return when {
        session.isConnected && payloadReady -> "Payload cached and ready."
        session.isConnected -> fallback
        session.isLinkingLike && payloadReady ->
            "Relinking. Cached payload still visible."
        session.isLinkingLike ->
            "Relinking. Payload reads wait for BLE."
        payloadReady ->
            "Offline. Cached payload still visible."
        else -> fallback
    }
}

private fun scopedMonitorSessions(uiState: WildUiState): List<DeviceSessionUiState> {
    val sessionsById = uiState.sessions.associateBy { it.id }
    return when (uiState.controlScope) {
        ControlScope.ActiveDevice -> listOfNotNull(uiState.activeSession)
        ControlScope.SelectedDevices -> {
            val selected = uiState.selectedSessions
            if (selected.isNotEmpty()) selected else uiState.selectedConnectedSessions
        }
        ControlScope.AllConnected -> linkedSetOf<String>().apply {
            uiState.connectedSessions.forEach { add(it.id) }
            uiState.sessions.filter { it.isLinkingLike }.forEach { add(it.id) }
            uiState.activeSessionId?.let { add(it) }
        }.mapNotNull { sessionsById[it] }
    }
}

private fun sessionPreviewLabel(session: DeviceSessionUiState?): String {
    return session?.previewSelection?.label(session.parsedSystemParams?.ephysChannelCount) ?: "--"
}

private fun scopeConsensusGpioMode(
    sessions: List<DeviceSessionUiState>,
    selector: (DeviceSessionUiState) -> GpioMode,
): GpioMode? {
    if (sessions.isEmpty()) {
        return null
    }
    val modes = sessions.map(selector).distinct()
    val consensus = modes.singleOrNull() ?: return null
    return consensus.takeIf { it != GpioMode.Unknown }
}

private fun scopeGpioModeLabel(
    sessions: List<DeviceSessionUiState>,
    selector: (DeviceSessionUiState) -> GpioMode,
): String {
    if (sessions.isEmpty()) {
        return "--"
    }
    val modes = sessions.map(selector).distinct()
    return when {
        modes.isEmpty() -> "--"
        modes.size == 1 && modes.first() == GpioMode.Unknown -> "--"
        modes.size == 1 -> modes.first().label
        else -> "Mixed"
    }
}

private fun buildScopeStatusSummary(sessions: List<DeviceSessionUiState>): ScopeStatusSummary {
    val previewLabels = sessions.map(::sessionPreviewLabel).distinct()
    val gpio0ConsensusMode = scopeConsensusGpioMode(sessions) { it.gpio0Mode }
    val gpio1ConsensusMode = scopeConsensusGpioMode(sessions) { it.gpio1Mode }
    return ScopeStatusSummary(
        targetCount = sessions.size,
        previewingCount = sessions.count(::isPreviewingSession),
        recordingCount = sessions.count { it.isRecordingLike },
        ledOnCount = sessions.count { it.ledOn },
        gpio0ModeLabel = scopeGpioModeLabel(sessions) { it.gpio0Mode },
        gpio1ModeLabel = scopeGpioModeLabel(sessions) { it.gpio1Mode },
        gpio0ConsensusMode = gpio0ConsensusMode,
        gpio1ConsensusMode = gpio1ConsensusMode,
        triggerWaveformCount = sessions.count { it.triggerWaveformEnabled },
        totalUsedSpaceMb = sessions.sumOf { it.usedSpaceMb ?: 0.0 },
        totalTrafficTx = sessions.sumOf { it.commandTxCount },
        totalTrafficRx = sessions.sumOf { it.commandRxCount },
        previewSourceLabel = when {
            previewLabels.isEmpty() -> "--"
            previewLabels.size == 1 -> previewLabels.first()
            else -> "Mixed"
        },
    )
}

private fun scopedCountLabel(count: Int, total: Int): String {
    return when {
        total <= 0 -> "--"
        total == 1 -> count.toString()
        else -> "$count/$total"
    }
}

private fun scopeStatusBadge(session: DeviceSessionUiState?, summary: ScopeStatusSummary): String {
    return when {
        summary.targetCount <= 0 -> session?.statusText ?: "Idle"
        summary.targetCount == 1 -> session?.statusText ?: "Ready"
        summary.recordingCount > 0 -> "${summary.recordingCount}/${summary.targetCount} REC"
        summary.previewingCount > 0 -> "${summary.previewingCount}/${summary.targetCount} LIVE"
        else -> "${summary.targetCount} Armed"
    }
}

private fun scopeActionLabel(base: String, targetCount: Int): String {
    return if (targetCount > 1) "$base ($targetCount)" else base
}

private fun scopeReadyActionLabel(base: String, readyCount: Int, targetCount: Int): String {
    return when {
        targetCount <= 1 -> base
        readyCount in 1 until targetCount -> "$base ($readyCount/$targetCount)"
        readyCount > 1 -> "$base ($readyCount)"
        else -> base
    }
}

private fun readinessValue(readyCount: Int, targetCount: Int): String {
    return if (targetCount <= 1) {
        if (readyCount > 0) "Ready" else "Read first"
    } else {
        "$readyCount/$targetCount"
    }
}

internal fun canStartLivePreview(session: DeviceSessionUiState): Boolean {
    return session.hostState == BleHostSessionState.Connected ||
        session.hostState == BleHostSessionState.Syncing ||
        session.hostState == BleHostSessionState.Synced ||
        (
            !session.waveformPreviewActive &&
                session.hostState in setOf(
                    BleHostSessionState.StartingRecording,
                    BleHostSessionState.Recording,
                )
            )
}

internal fun hasLivePreviewControl(session: DeviceSessionUiState): Boolean {
    return session.hostState == BleHostSessionState.Previewing ||
        session.waveformPreviewActive ||
        (
            session.recorderBackedLiveSignal &&
                session.hostState in setOf(
                    BleHostSessionState.StartingRecording,
                    BleHostSessionState.Recording,
                )
            )
}

private fun canStartLiveRecording(session: DeviceSessionUiState): Boolean {
    return session.hostState == BleHostSessionState.Connected ||
        session.hostState == BleHostSessionState.Syncing ||
        session.hostState == BleHostSessionState.Synced ||
        session.hostState == BleHostSessionState.Previewing
}

private fun canStopLiveRecording(session: DeviceSessionUiState): Boolean {
    return session.hostState in setOf(
        BleHostSessionState.StartingRecording,
        BleHostSessionState.Recording,
        BleHostSessionState.StoppingRecording,
    )
}

private fun canRunLivePreviewGroupResync(sessions: List<DeviceSessionUiState>): Boolean {
    if (sessions.size < 2) {
        return false
    }

    var anyPreviewing = false
    var anyNeedsRestart = false
    for (session in sessions) {
        if (session.hostState == BleHostSessionState.Recording ||
            session.hostState == BleHostSessionState.StartingRecording ||
            session.hostState == BleHostSessionState.StoppingRecording ||
            session.isLinkingLike
        ) {
            return false
        }

        when {
            session.hostState == BleHostSessionState.Previewing -> anyPreviewing = true
            canStartLivePreview(session) -> anyNeedsRestart = true
            else -> return false
        }
    }

    return anyPreviewing && anyNeedsRestart
}

private fun buildLiveActionSummary(sessions: List<DeviceSessionUiState>): LiveActionSummary {
    val canPreviewGroupResync = canRunLivePreviewGroupResync(sessions)
    val previewStartBlockedByScope =
        sessions.size > 1 &&
            sessions.any { !canStartLivePreview(it) } &&
            !canPreviewGroupResync
    val recordingStartBlockedByScope =
        sessions.size > 1 &&
            sessions.any { !canStartLiveRecording(it) }

    return LiveActionSummary(
        previewStartCount = if (previewStartBlockedByScope) 0 else sessions.count(::canStartLivePreview),
        previewStopCount = sessions.count(::hasLivePreviewControl),
        recordingStartCount = if (recordingStartBlockedByScope) 0 else sessions.count(::canStartLiveRecording),
        recordingActiveCount = sessions.count { session ->
            session.hostState == BleHostSessionState.StartingRecording ||
                session.hostState == BleHostSessionState.Recording
        },
        recordingStopPendingCount = sessions.count { session ->
            session.hostState == BleHostSessionState.StoppingRecording
        },
        recordingStopCount = sessions.count(::canStopLiveRecording),
        canRunPreviewGroupResync = canPreviewGroupResync,
        previewStartBlockedByScope = previewStartBlockedByScope,
        recordingStartBlockedByScope = recordingStartBlockedByScope,
    )
}

private fun previewDisplaySampleRate(session: DeviceSessionUiState): Int {
    val parsed = session.parsedSystemParams ?: return PreviewDefaultSampleRateHz
    val previewRateFromRatio = parsed.previewRatio
        .takeIf { it > 0 }
        ?.let { ratio ->
            parsed.ephysSamplingRate
                .takeIf { it > 0 }
                ?.div(ratio)
        }
        ?.takeIf { it in 1..PreviewMaxDisplaySampleRateHz }

    return parsed.baseFs.takeIf { it in 1..PreviewMaxDisplaySampleRateHz }
        ?: previewRateFromRatio
        ?: parsed.ephysSamplingRate.takeIf { it in 1..PreviewMaxDisplaySampleRateHz }
        ?: PreviewDefaultSampleRateHz
}

private fun bufferedPreviewWindowSeconds(session: DeviceSessionUiState): Float {
    val sampleRate = previewDisplaySampleRate(session).coerceAtLeast(1)
    return session.previewPoints.size.toFloat() / sampleRate.toFloat()
}

private fun previewWindowSampleCount(
    session: DeviceSessionUiState,
    window: SignalWindowPreset,
): Int {
    return (window.seconds * previewDisplaySampleRate(session)).coerceAtLeast(PreviewPacketSampleCount)
}

private fun previewTotalSampleCount(session: DeviceSessionUiState): Long {
    return session.previewPacketCount.toLong() * PreviewPacketSampleCount.toLong()
}

private fun inferredPreviewScanStartSample(
    totalSamples: Long,
    windowSampleCount: Int,
): Long {
    if (totalSamples <= 0L || windowSampleCount <= 0) {
        return totalSamples.coerceAtLeast(0L)
    }

    val safeWindow = windowSampleCount.toLong()
    val visibleSamples = when {
        totalSamples < safeWindow -> totalSamples
        totalSamples % safeWindow == 0L -> safeWindow
        else -> totalSamples % safeWindow
    }
    return (totalSamples - visibleSamples).coerceAtLeast(0L)
}

private fun maxPreviewScanBacktrackWindows(
    session: DeviceSessionUiState,
    window: SignalWindowPreset,
): Float {
    val windowSamples = previewWindowSampleCount(session, window).coerceAtLeast(1)
    val liveScanStart = inferredPreviewScanStartSample(previewTotalSampleCount(session), windowSamples)
    return (liveScanStart.toFloat() / windowSamples.toFloat()).coerceAtLeast(0f)
}

private fun previewViewportStartSample(
    session: DeviceSessionUiState,
    window: SignalWindowPreset,
    scanBacktrackWindows: Float,
): Long {
    val windowSamples = previewWindowSampleCount(session, window).coerceAtLeast(1)
    val liveScanStart = inferredPreviewScanStartSample(previewTotalSampleCount(session), windowSamples)
    val olderSamples = (scanBacktrackWindows.coerceAtLeast(0f) * windowSamples.toFloat()).roundToLong()
    return (liveScanStart - olderSamples).coerceAtLeast(0L)
}

private fun previewThresholdChannelId(session: DeviceSessionUiState): Int? {
    return when (session.previewSelection.normalizedForDevice(session.parsedSystemParams?.ephysChannelCount).label(session.parsedSystemParams?.ephysChannelCount)) {
        "DSP output A" -> 0
        "DSP output B" -> 1
        else -> null
    }
}

private fun previewThresholdLabel(session: DeviceSessionUiState): String? {
    return when (previewThresholdChannelId(session)) {
        0 -> "Trigger A"
        1 -> "Trigger B"
        else -> null
    }
}

private fun currentPreviewThreshold(session: DeviceSessionUiState): Float? {
    val channelId = previewThresholdChannelId(session) ?: return null
    return session.stimControlStatuses[channelId]?.triggerLevel
}

private fun preparePreviewTraceForDisplay(
    session: DeviceSessionUiState,
    config: SignalDisplayConfig,
    scanStartSample: Long? = null,
    maxRenderedColumns: Int = 640,
): PreparedPreviewTrace {
    val rawPoints = session.previewPoints
    if (rawPoints.isEmpty()) {
        return PreparedPreviewTrace(emptyList())
    }

    val requestedSamples = previewWindowSampleCount(session, config.window)
    val totalSamples = previewTotalSampleCount(session)
    val effectiveScanStart = (scanStartSample ?: inferredPreviewScanStartSample(totalSamples, requestedSamples))
        .coerceIn(0L, totalSamples)
    val progressedSamples = (totalSamples - effectiveScanStart).coerceAtLeast(0L).toInt()
    val visibleSampleCount = when {
        scanStartSample != null && progressedSamples <= 0 -> 0
        progressedSamples <= 0 -> minOf(rawPoints.size, requestedSamples)
        else -> progressedSamples.coerceAtMost(minOf(rawPoints.size, requestedSamples))
    }
    val visibleRawPoints = rawPoints.takeLast(visibleSampleCount)
    if (visibleRawPoints.isEmpty()) {
        return PreparedPreviewTrace(emptyList())
    }

    val thresholdBaseline = if (config.removeDc) visibleRawPoints.average().toFloat() else 0f
    val centeredPoints = if (config.removeDc) {
        applyPreviewHighPassFilter(visibleRawPoints, previewDisplaySampleRate(session))
    } else {
        visibleRawPoints
    }
    val threshold = currentPreviewThreshold(session)?.let { it - thresholdBaseline }

    return PreparedPreviewTrace(
        columns = buildPreviewRenderColumns(
            points = centeredPoints,
            windowSampleCount = requestedSamples,
            maxColumns = maxRenderedColumns,
        ),
        threshold = threshold,
        thresholdLabel = previewThresholdLabel(session),
        filledFraction = visibleRawPoints.size.toFloat() / requestedSamples.toFloat(),
    )
}

private fun applyPreviewHighPassFilter(
    points: List<Float>,
    sampleRateHz: Int,
): List<Float> {
    if (points.isEmpty()) {
        return emptyList()
    }

    val safeRate = sampleRateHz.coerceAtLeast(1).toFloat()
    val dt = 1f / safeRate
    val rc = (1.0 / (2.0 * PI * PreviewHighPassCutoffHz.toDouble())).toFloat()
    val alpha = rc / (rc + dt)
    val filtered = ArrayList<Float>(points.size)
    var previousInput = points.first()
    var previousOutput = 0f
    filtered += 0f
    for (index in 1 until points.size) {
        val currentInput = points[index]
        val output = alpha * (previousOutput + currentInput - previousInput)
        filtered += output
        previousInput = currentInput
        previousOutput = output
    }
    return filtered
}

private fun buildPreviewRenderColumns(
    points: List<Float>,
    windowSampleCount: Int,
    maxColumns: Int,
): List<PreviewRenderColumn> {
    if (points.isEmpty() || windowSampleCount <= 0 || maxColumns <= 0) {
        return emptyList()
    }

    val columnCount = minOf(points.size, maxColumns)
    val bucketWidth = points.size.toFloat() / columnCount.toFloat()
    val safeWindowIndex = (windowSampleCount - 1).coerceAtLeast(1).toFloat()
    return List(columnCount) { bucketIndex ->
        val start = (bucketIndex * bucketWidth).toInt().coerceAtMost(points.lastIndex)
        val endExclusive = (((bucketIndex + 1) * bucketWidth).toInt()).coerceIn(start + 1, points.size)
        var minValue = points[start]
        var maxValue = points[start]
        for (index in start until endExclusive) {
            val candidate = points[index]
            if (candidate < minValue) {
                minValue = candidate
            }
            if (candidate > maxValue) {
                maxValue = candidate
            }
        }
        PreviewRenderColumn(
            xFraction = ((endExclusive - 1).coerceAtLeast(0)).toFloat() / safeWindowIndex,
            minValue = minValue,
            maxValue = maxValue,
        )
    }
}

private inline fun DrawScope.drawScopeBandConnector(
    previous: PreviewRenderColumn?,
    current: PreviewRenderColumn,
    previousX: Float?,
    currentX: Float,
    color: Color,
    strokeWidth: Float,
    toPlotY: (Float) -> Float,
) {
    if (previous == null || previousX == null || currentX <= previousX) {
        return
    }

    when {
        previous.maxValue < current.minValue -> drawLine(
            color = color,
            start = Offset(previousX, toPlotY(previous.maxValue)),
            end = Offset(currentX, toPlotY(current.minValue)),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        previous.minValue > current.maxValue -> drawLine(
            color = color,
            start = Offset(previousX, toPlotY(previous.minValue)),
            end = Offset(currentX, toPlotY(current.maxValue)),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

private fun formatSignalWindowSeconds(seconds: Float): String {
    return String.format(Locale.US, "%.1f", seconds)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SignalPlotCard(
    sessions: List<DeviceSessionUiState>,
    allConnectedSessions: List<DeviceSessionUiState>,
    commandScopeLabel: String,
    activeSessionId: String?,
    onActivateSession: (String) -> Unit,
    onOpenDevices: () -> Unit,
    onOpenOperate: () -> Unit,
    onSetPreviewSelection: (String, PreviewSelection) -> Unit,
    displayConfig: SignalDisplayConfig,
    onWindowChange: (SignalWindowPreset) -> Unit,
    onGainChange: (SignalGainPreset) -> Unit,
    onRemoveDcChange: (Boolean) -> Unit,
    signalViewMode: SignalViewMode = SignalViewMode.Stacked,
    onStartPreview: () -> Unit,
    onStopPreview: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onStartAllPreview: () -> Unit,
    onStopAllPreview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val actionSummary = buildLiveActionSummary(sessions)
    val allConnectedActionSummary = buildLiveActionSummary(allConnectedSessions)
    val sessionKey = sessions.joinToString(separator = "|") { it.id }
    val activeSession = sessions.firstOrNull { it.id == activeSessionId } ?: sessions.firstOrNull()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var gestureOffset by rememberSaveable(sessionKey) { mutableStateOf(0f) }
    var scanBacktrackWindows by rememberSaveable(sessionKey) { mutableStateOf(0f) }
    val telemetrySession = activeSession ?: sessions.firstOrNull()
    var displaySettingsVisible by rememberSaveable(sessionKey) { mutableStateOf(false) }
    val maxScanBacktrackWindows = activeSession?.let { session ->
        maxPreviewScanBacktrackWindows(session, displayConfig.window)
    } ?: 0f
    LaunchedEffect(sessionKey, displayConfig.window.name, maxScanBacktrackWindows) {
        scanBacktrackWindows = scanBacktrackWindows.coerceIn(0f, maxScanBacktrackWindows)
    }
    val plottedSessions = sessions.map { session ->
        session to preparePreviewTraceForDisplay(
            session = session,
            config = displayConfig,
            scanStartSample = previewViewportStartSample(
                session = session,
                window = displayConfig.window,
                scanBacktrackWindows = scanBacktrackWindows,
            ),
        )
    }
    val zoomReadout = buildString {
        append("X ")
        append(displayConfig.window.label)
        append("  Y ")
        append(displayConfig.gain.label)
        append("  Offset ")
        append(formatWaveformOffsetLabel(gestureOffset))
        if (displayConfig.removeDc) {
            append("  HPF")
        }
        append("  ")
        if (scanBacktrackWindows > 0.02f) {
            append("Back ")
            append(scanBacktrackWindows.format(1))
            append("x")
        } else {
            append("Live")
        }
    }
    val waveformReadout = zoomReadout
    val gridLineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    // Keep data traces clear of the explicit transport controls rather than drawing beneath them.
    val waveformBottomInset = if (isLandscape) 74.dp else 228.dp
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier,
    ) {
        PreviewWaveformPanel(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp, vertical = 2.dp),
            selectorSessions = plottedSessions.map { it.first },
            activeSessionId = activeSessionId,
            onActivateSession = onActivateSession,
            plottedSessions = plottedSessions,
            telemetrySession = telemetrySession,
            displayConfig = displayConfig,
            signalViewMode = signalViewMode,
            sessionKey = sessionKey,
            maxScanBacktrackWindows = maxScanBacktrackWindows,
            gridLineColor = gridLineColor,
            zoomReadout = waveformReadout,
            waveformBottomInset = waveformBottomInset,
            gestureOffset = gestureOffset,
            onGestureOffsetChange = { gestureOffset = it },
            scanBacktrackWindows = scanBacktrackWindows,
            onScanBacktrackWindowsChange = { scanBacktrackWindows = it },
            onWindowChange = onWindowChange,
            onGainChange = onGainChange,
            trailingOverlayContent = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    OutlinedButton(
                        onClick = onOpenDevices,
                        modifier = Modifier.heightIn(min = 54.dp),
                    ) {
                        Text("Devices")
                    }
                    activeSession?.let {
                    OutlinedButton(
                        onClick = { displaySettingsVisible = true },
                        modifier = Modifier.heightIn(min = 54.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.Start) {
                            Text("Display", style = MaterialTheme.typography.labelLarge)
                            Text(
                                "${displayConfig.window.label} · ${displayConfig.gain.label}",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    }
                }
            },
            bottomOverlayContent = {
                PreviewCompactControlStrip(
                    actionSummary = actionSummary,
                    allConnectedActionSummary = allConnectedActionSummary,
                    allConnectedDeviceCount = allConnectedSessions.size,
                    commandScopeLabel = commandScopeLabel,
                    activeSessionName = activeSession?.name,
                    isLandscape = isLandscape,
                    onStartPreview = onStartPreview,
                    onStopPreview = onStopPreview,
                    onStartRecording = onStartRecording,
                    onStopRecording = onStopRecording,
                    onStartAllPreview = onStartAllPreview,
                    onStopAllPreview = onStopAllPreview,
                    onOpenOperate = onOpenOperate,
                )
            },
        )
    }

    if (displaySettingsVisible && activeSession != null) {
        DisplaySettingsSheet(
            session = activeSession,
            displayConfig = displayConfig,
            onDismiss = { displaySettingsVisible = false },
            onWindowChange = onWindowChange,
            onGainChange = onGainChange,
            onRemoveDcChange = onRemoveDcChange,
            onSetPreviewSelection = { selection ->
                onSetPreviewSelection(activeSession.id, selection)
            },
        )
    }
}

@Composable
private fun DisplaySettingsSheet(
    session: DeviceSessionUiState,
    displayConfig: SignalDisplayConfig,
    onDismiss: () -> Unit,
    onWindowChange: (SignalWindowPreset) -> Unit,
    onGainChange: (SignalGainPreset) -> Unit,
    onRemoveDcChange: (Boolean) -> Unit,
    onSetPreviewSelection: (PreviewSelection) -> Unit,
) {
    val channelCount = session.parsedSystemParams?.ephysChannelCount
    val selection = session.previewSelection.normalizedForDevice(channelCount)
    val sourceOptionCount = selection.optionCount(channelCount)
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var windowMenuVisible by remember(session.id) { mutableStateOf(false) }
    var gainMenuVisible by remember(session.id) { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth(if (isLandscape) 0.78f else 0.94f)
                .widthIn(max = 880.dp),
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Display settings", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        compactDeviceUiLabel(session.name, session.address),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.heightIn(min = 52.dp),
                ) {
                    Text("Done")
                }
            }

            Text(
                text = "Gestures: pinch horizontally for time scale, pinch vertically for gain, and swipe vertically to move the trace.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Signal source", style = MaterialTheme.typography.titleSmall)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilterChip(
                            selected = !selection.auxMode,
                            onClick = {
                                onSetPreviewSelection(
                                    PreviewSelection(auxMode = false, index = 0).normalizedForDevice(channelCount),
                                )
                            },
                            label = { Text("Electrode") },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                        FilterChip(
                            selected = selection.auxMode,
                            onClick = {
                                onSetPreviewSelection(
                                    PreviewSelection(auxMode = true, index = 0).normalizedForDevice(channelCount),
                                )
                            },
                            label = { Text("Auxiliary") },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedIconButton(
                            onClick = {
                                onSetPreviewSelection(
                                    shiftPreviewSelection(selection, -1, channelCount),
                                )
                            },
                            enabled = sourceOptionCount > 1,
                            modifier = Modifier.size(52.dp),
                        ) {
                            Icon(Icons.Outlined.Remove, contentDescription = "Previous signal source")
                        }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.64f),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    selection.label(channelCount),
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        OutlinedIconButton(
                            onClick = {
                                onSetPreviewSelection(
                                    shiftPreviewSelection(selection, 1, channelCount),
                                )
                            },
                            enabled = sourceOptionCount > 1,
                            modifier = Modifier.size(52.dp),
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = "Next signal source")
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Waveform scale", style = MaterialTheme.typography.titleSmall)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { windowMenuVisible = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp),
                        ) {
                            Text("Time window  ·  ${displayConfig.window.label}")
                            Icon(
                                imageVector = Icons.Outlined.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = windowMenuVisible,
                            onDismissRequest = { windowMenuVisible = false },
                        ) {
                            SignalWindowPreset.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = {
                                        windowMenuVisible = false
                                        onWindowChange(option)
                                    },
                                )
                            }
                        }
                    }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { gainMenuVisible = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp),
                        ) {
                            Text("Amplitude  ·  ${displayConfig.gain.label}")
                            Icon(
                                imageVector = Icons.Outlined.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = gainMenuVisible,
                            onDismissRequest = { gainMenuVisible = false },
                        ) {
                            SignalGainPreset.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = {
                                        gainMenuVisible = false
                                        onGainChange(option)
                                    },
                                )
                            }
                        }
                    }
                }
            }

            FilledTonalButton(
                onClick = { onRemoveDcChange(!displayConfig.removeDc) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) {
                Icon(Icons.Outlined.FilterAlt, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text(
                    if (displayConfig.removeDc) {
                        "High-pass correction  ·  On"
                    } else {
                        "High-pass correction  ·  Off"
                    },
                )
            }
        }
        }
    }
}

@Composable
private fun PreviewWaveformPanel(
    selectorSessions: List<DeviceSessionUiState>,
    activeSessionId: String?,
    onActivateSession: (String) -> Unit,
    plottedSessions: List<Pair<DeviceSessionUiState, PreparedPreviewTrace>>,
    telemetrySession: DeviceSessionUiState?,
    displayConfig: SignalDisplayConfig,
    signalViewMode: SignalViewMode,
    sessionKey: String,
    maxScanBacktrackWindows: Float,
    gridLineColor: Color,
    zoomReadout: String,
    waveformBottomInset: Dp,
    gestureOffset: Float,
    onGestureOffsetChange: (Float) -> Unit,
    scanBacktrackWindows: Float,
    onScanBacktrackWindowsChange: (Float) -> Unit,
    onWindowChange: (SignalWindowPreset) -> Unit,
    onGainChange: (SignalGainPreset) -> Unit,
    trailingOverlayContent: (@Composable () -> Unit)? = null,
    bottomOverlayContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val effectiveSignalViewMode = if (plottedSessions.size > 1) signalViewMode else SignalViewMode.Stacked
    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(20.dp),
            )
            .pointerInput(sessionKey, activeSessionId, displayConfig.window.name, displayConfig.gain.name) {
                awaitEachGesture {
                    // Let controls layered over the waveform consume their own touches first.
                    // Unconsumed touches still drive waveform pinch and offset gestures.
                    awaitFirstDown(requireUnconsumed = true)
                    val pinchStepDistance = 32.dp.toPx()
                    var horizontalPinchDistance = 0f
                    var verticalPinchDistance = 0f
                    var gestureActive = true
                    while (gestureActive) {
                        val event = awaitPointerEvent()
                        val activeChanges = event.changes.filter { change -> change.pressed }
                        if (activeChanges.size >= 2) {
                            val first = activeChanges[0]
                            val second = activeChanges[1]
                            if (first.previousPressed && second.previousPressed) {
                                val horizontalSpan = abs(second.position.x - first.position.x)
                                val verticalSpan = abs(second.position.y - first.position.y)
                                val previousHorizontalSpan = abs(second.previousPosition.x - first.previousPosition.x)
                                val previousVerticalSpan = abs(second.previousPosition.y - first.previousPosition.y)
                                if (horizontalSpan >= verticalSpan) {
                                    horizontalPinchDistance += horizontalSpan - previousHorizontalSpan
                                    when {
                                        horizontalPinchDistance >= pinchStepDistance -> {
                                            onWindowChange(displayConfig.window.step(-1))
                                            horizontalPinchDistance = 0f
                                        }

                                        horizontalPinchDistance <= -pinchStepDistance -> {
                                            onWindowChange(displayConfig.window.step(1))
                                            horizontalPinchDistance = 0f
                                        }
                                    }
                                } else {
                                    verticalPinchDistance += verticalSpan - previousVerticalSpan
                                    when {
                                        verticalPinchDistance >= pinchStepDistance -> {
                                            onGainChange(displayConfig.gain.step(1))
                                            verticalPinchDistance = 0f
                                        }

                                        verticalPinchDistance <= -pinchStepDistance -> {
                                            onGainChange(displayConfig.gain.step(-1))
                                            verticalPinchDistance = 0f
                                        }
                                    }
                                }
                                activeChanges.forEach { it.consume() }
                            }
                        } else if (activeChanges.size == 1) {
                            val change = activeChanges.single()
                            if (change.previousPressed && size.height > 0) {
                                val verticalDelta = change.position.y - change.previousPosition.y
                                if (verticalDelta != 0f) {
                                    onGestureOffsetChange(
                                        (gestureOffset + verticalDelta / size.height).coerceIn(-1.2f, 1.2f),
                                    )
                                    change.consume()
                                }
                            }
                        }
                        gestureActive = event.changes.any { change -> change.pressed }
                    }
                }
            },
    ) {
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .padding(bottom = waveformBottomInset),
        ) {
            val widthStep = size.width / 5f
            val heightStep = size.height / 4f
            repeat(6) { index ->
                val x = index * widthStep
                drawLine(gridLineColor, start = Offset(x, 0f), end = Offset(x, size.height), strokeWidth = 1f)
            }
            repeat(5) { index ->
                val y = index * heightStep
                drawLine(gridLineColor, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 1f)
            }

            if (plottedSessions.isEmpty()) {
                return@Canvas
            }

            val laneCount = if (effectiveSignalViewMode == SignalViewMode.Overlay) {
                1
            } else {
                plottedSessions.size.coerceAtLeast(1)
            }
            val laneSpacing = if (laneCount == 1) size.height else size.height / laneCount.toFloat()
            val baselineShift = gestureOffset * laneSpacing
            val laneAmplitude = when {
                effectiveSignalViewMode == SignalViewMode.Overlay -> size.height * 0.24f
                laneCount == 1 -> size.height * 0.38f
                else -> laneSpacing * 0.32f
            }

            plottedSessions.forEachIndexed { index, (session, trace) ->
                val columns = trace.columns
                if (columns.isEmpty()) {
                    return@forEachIndexed
                }

                val baseline = if (effectiveSignalViewMode == SignalViewMode.Overlay || laneCount == 1) {
                    size.height * 0.5f + baselineShift
                } else {
                    laneSpacing * (index + 0.5f) + baselineShift
                }
                drawLine(
                    color = Color(session.traceColorArgb).copy(alpha = 0.18f),
                    start = Offset(0f, baseline.coerceIn(0f, size.height)),
                    end = Offset(size.width, baseline.coerceIn(0f, size.height)),
                    strokeWidth = 1.5f,
                )

                fun toPlotY(value: Float): Float {
                    val normalized = value * displayConfig.gain.normalizedPerCount
                    return (baseline - normalized * laneAmplitude).coerceIn(0f, size.height)
                }

                var previousX: Float? = null
                var previousColumn: PreviewRenderColumn? = null
                columns.forEach { column ->
                    val x = column.xFraction.coerceIn(0f, 1f) * size.width
                    val maxY = toPlotY(column.maxValue)
                    val minY = toPlotY(column.minValue)

                    drawScopeBandConnector(
                        previous = previousColumn,
                        current = column,
                        previousX = previousX,
                        currentX = x,
                        color = Color(session.traceColorArgb),
                        strokeWidth = if (session.id == activeSessionId) 3.2f else 2.2f,
                        toPlotY = ::toPlotY,
                    )

                    drawLine(
                        color = Color(session.traceColorArgb).copy(
                            alpha = if (session.id == activeSessionId) 0.96f else 0.82f,
                        ),
                        start = Offset(x, maxY),
                        end = Offset(x, minY),
                        strokeWidth = if (session.id == activeSessionId) 2.6f else 1.8f,
                        cap = StrokeCap.Round,
                    )

                    previousX = x
                    previousColumn = column
                }

                if (session.id == activeSessionId && trace.threshold != null) {
                    val normalizedThreshold = trace.threshold * displayConfig.gain.normalizedPerCount
                    val thresholdY = (baseline - normalizedThreshold * laneAmplitude).coerceIn(0f, size.height)
                    drawLine(
                        color = Color(session.traceColorArgb).copy(alpha = 0.58f),
                        start = Offset(0f, thresholdY),
                        end = Offset(size.width, thresholdY),
                        strokeWidth = 2f,
                    )
                }
            }
        }

        if (plottedSessions.none { (_, trace) -> trace.columns.isNotEmpty() }) {
            val session = telemetrySession ?: selectorSessions.firstOrNull { it.id == activeSessionId }
            val emptyWaveformMessage = when {
                session?.recorderBackedLiveSignal == true ->
                    "Recorder is active, but this device has not sent live waveform samples."

                session?.isRecordingLike == true && !session.waveformPreviewActive ->
                    "Recording is active. Start live signal to view its waveform."

                session?.isRecordingLike == true ->
                    "Waiting for live waveform samples…"

                session?.hostState == BleHostSessionState.Previewing ->
                    "Waiting for live waveform samples…"

                else -> "Start live signal to show a waveform."
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(start = 32.dp, end = 32.dp, bottom = waveformBottomInset),
            ) {
                Text(
                    text = emptyWaveformMessage,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                    textAlign = TextAlign.Center,
                )
            }
        }

        if (selectorSessions.isNotEmpty()) {
            PreviewDeviceLaneLegend(
                sessions = selectorSessions,
                activeSessionId = activeSessionId,
                onActivateSession = onActivateSession,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        start = if (isLandscape) 8.dp else 10.dp,
                        top = 8.dp,
                    ),
            )
        }

        trailingOverlayContent?.let { overlay ->
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = if (isLandscape) 10.dp else 8.dp),
            ) {
                overlay()
            }
        }

        telemetrySession?.let { session ->
            if (session.isRecordingLike) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val stopping = session.hostState == BleHostSessionState.StoppingRecording
                            Text(
                                text = when {
                                    stopping -> "STOP PENDING"
                                    session.recorderBackedLiveSignal -> "LIVE REC"
                                    else -> "REC"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (stopping) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    Color(0xFFD64545).copy(alpha = 0.92f)
                                },
                            )
                            Text(
                                text = if (stopping) {
                                    "Waiting for device confirmation"
                                } else {
                                    "${formatSeconds(session.recordingSeconds)} | ${formatUsedSpaceLabel(session.usedSpaceMb)}"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        bottomOverlayContent?.let { overlay ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp),
            ) {
                overlay()
            }
        }

        if (!isLandscape) {
            val readoutBottomPadding = if (bottomOverlayContent != null) {
                waveformBottomInset + 8.dp
            } else {
                10.dp
            }
            PreviewZoomReadoutBadge(
                text = zoomReadout,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 10.dp, bottom = readoutBottomPadding),
            )
        }
    }
}

@Composable
private fun PreviewDeviceLaneLegend(
    sessions: List<DeviceSessionUiState>,
    activeSessionId: String?,
    onActivateSession: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visibleSessions = sessions.take(3)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        visibleSessions.forEachIndexed { index, session ->
            val active = session.id == activeSessionId
            val shape = RoundedCornerShape(14.dp)
            Surface(
                shape = shape,
                color = if (active) {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.84f)
                },
                modifier = Modifier
                    .widthIn(min = 108.dp, max = 138.dp)
                    .heightIn(min = 48.dp)
                    .border(
                        width = 1.dp,
                        color = if (active) {
                            Color(session.traceColorArgb).copy(alpha = 0.86f)
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
                        },
                        shape = shape,
                    )
                    .clickable { onActivateSession(session.id) },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .background(Color(session.traceColorArgb), CircleShape),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = compactDeviceUiLabel(session.name, session.address),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (active) "Selected" else sessionPreviewLabel(session),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) {
                                Color(session.traceColorArgb)
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        if (sessions.size > visibleSessions.size) {
            Text(
                text = "+${sessions.size - visibleSessions.size} more device streams",
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
        }
    }
}

@Composable
private fun PreviewControlTargetSelector(
    sessions: List<DeviceSessionUiState>,
    activeSessionId: String?,
    onActivateSession: (String) -> Unit,
    compact: Boolean = false,
    sideDocked: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val activeSession = sessions.firstOrNull { it.id == activeSessionId }
        ?: sessions.firstOrNull()
        ?: return
    val orderedSessions = sessions.distinctBy { it.id }
    var expanded by remember(activeSession.id, sessions.size) { mutableStateOf(false) }
    val selectorShape = RoundedCornerShape(
        when {
            sideDocked -> 18.dp
            compact -> 14.dp
            else -> 18.dp
        },
    )
    val selectorLabel = if (compact) {
        compactPreviewSelectorLabel(activeSession.name, activeSession.address)
    } else {
        compactDeviceUiLabel(activeSession.name, activeSession.address)
    }

    Box(modifier = modifier) {
        Surface(
            shape = selectorShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = if (compact) 0.94f else 0.92f),
            modifier = Modifier
                .widthIn(
                    min = when {
                        sideDocked -> 82.dp
                        compact -> 72.dp
                        else -> 104.dp
                    },
                    max = when {
                        sideDocked -> 110.dp
                        compact -> 104.dp
                        else -> 156.dp
                    },
                )
                .border(
                    width = 1.dp,
                    color = Color(activeSession.traceColorArgb).copy(alpha = if (compact) 0.72f else 0.56f),
                    shape = selectorShape,
                )
                .clickable(enabled = orderedSessions.size > 1) { expanded = true },
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = when {
                        sideDocked -> 9.dp
                        compact -> 8.dp
                        else -> 10.dp
                    },
                    vertical = when {
                        sideDocked -> 8.dp
                        compact -> 7.dp
                        else -> 8.dp
                    },
                ),
                horizontalArrangement = Arrangement.spacedBy(
                    when {
                        sideDocked -> 4.dp
                        compact -> 5.dp
                        else -> 6.dp
                    },
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(
                            when {
                                sideDocked -> 7.dp
                                compact -> 8.dp
                                else -> 10.dp
                            },
                        )
                        .background(Color(activeSession.traceColorArgb), CircleShape)
                )
                Text(
                    text = selectorLabel,
                    style = if (compact || sideDocked) {
                        MaterialTheme.typography.labelSmall
                    } else {
                        MaterialTheme.typography.labelMedium
                    },
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (orderedSessions.size > 1) {
                    Icon(
                        imageVector = Icons.Outlined.ArrowDropDown,
                        contentDescription = "Select preview device",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.size(
                            when {
                                sideDocked -> 14.dp
                                compact -> 16.dp
                                else -> 18.dp
                            },
                        ),
                    )
                }
            }
        }

        DropdownMenu(
            expanded = expanded && orderedSessions.size > 1,
            onDismissRequest = { expanded = false },
        ) {
            orderedSessions.forEach { session ->
                DropdownMenuItem(
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color(session.traceColorArgb), CircleShape)
                            )
                            Column {
                                Text(
                                    text = compactDeviceUiLabel(session.name, session.address),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (session.id == activeSession.id) {
                                        Color(session.traceColorArgb)
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                                Text(
                                    text = sessionSelectorTelemetryLabel(session),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        onActivateSession(session.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun PreviewControlScopeSelector(
    scope: ControlScope,
    selectedCount: Int,
    connectedCount: Int,
    onScopeChange: (ControlScope) -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val showSelectedScope = selectedCount > 0 || scope == ControlScope.SelectedDevices
    val showAllScope = connectedCount > 1 || scope == ControlScope.AllConnected
    if (!showSelectedScope && !showAllScope) {
        return
    }

    val options = buildList {
        add(ControlScope.ActiveDevice to "Active")
        if (showSelectedScope) {
            add(ControlScope.SelectedDevices to if (selectedCount > 0) "Selected $selectedCount" else "Selected")
        }
        if (showAllScope) {
            add(ControlScope.AllConnected to if (connectedCount > 1) "All $connectedCount" else "All")
        }
    }
    val currentLabel = when (scope) {
        ControlScope.ActiveDevice -> "1"
        ControlScope.SelectedDevices -> if (selectedCount > 0) "S$selectedCount" else "Sel"
        ControlScope.AllConnected -> if (connectedCount > 1) "A$connectedCount" else "All"
    }
    val shape = RoundedCornerShape(if (compact) 14.dp else 16.dp)
    var expanded by remember(scope, selectedCount, connectedCount) { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            modifier = Modifier
                .widthIn(min = if (compact) 46.dp else 72.dp, max = if (compact) 68.dp else 104.dp)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.24f),
                    shape = shape,
                )
                .clickable { expanded = true },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = currentLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (options.size > 1) {
                    Icon(
                        imageVector = Icons.Outlined.ArrowDropDown,
                        contentDescription = "Select preview scope",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }

        DropdownMenu(
            expanded = expanded && options.size > 1,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (optionScope, label) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = label,
                            color = if (optionScope == scope) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        expanded = false
                        onScopeChange(optionScope)
                    },
                )
            }
        }
    }
}

@Composable
private fun PreviewCompactControlStrip(
    actionSummary: LiveActionSummary,
    allConnectedActionSummary: LiveActionSummary,
    allConnectedDeviceCount: Int,
    commandScopeLabel: String,
    activeSessionName: String?,
    isLandscape: Boolean,
    onStartPreview: () -> Unit,
    onStopPreview: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onStartAllPreview: () -> Unit,
    onStopAllPreview: () -> Unit,
    onOpenOperate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val previewIsRunning = actionSummary.previewStopCount > 0
    val recordingIsRunning = actionSummary.recordingStopCount > 0
    val allPreviewIsRunning = allConnectedActionSummary.previewStopCount > 0
    val focusLabel = activeSessionName?.takeIf { it.isNotBlank() } ?: "No focused device"
    val previewLabel = if (previewIsRunning) {
        "Stop live signal (${actionSummary.previewStopCount})"
    } else {
        "Start live signal"
    }
    val recordingLabel = when {
        actionSummary.recordingStopPendingCount > 0 && actionSummary.recordingActiveCount > 0 ->
            "Stop / retry (${actionSummary.recordingStopCount})"
        actionSummary.recordingStopPendingCount > 0 ->
            "Retry stop (${actionSummary.recordingStopPendingCount})"
        recordingIsRunning -> "Stop recording (${actionSummary.recordingActiveCount})"
        else -> "Start recording"
    }
    val previewEnabled = if (previewIsRunning) actionSummary.canStopPreview else actionSummary.canStartPreview
    val recordingEnabled = if (recordingIsRunning) actionSummary.canStopRecording else actionSummary.canStartRecording
    val allPreviewLabel = if (allPreviewIsRunning) {
        "Stop all live ($allConnectedDeviceCount)"
    } else {
        "Start all live ($allConnectedDeviceCount)"
    }
    val allPreviewEnabled = if (allPreviewIsRunning) {
        allConnectedActionSummary.canStopPreview
    } else {
        allConnectedActionSummary.canStartPreview
    }

    Surface(
        shape = RoundedCornerShape(if (isLandscape) 18.dp else 16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = if (isLandscape) 12.dp else 8.dp)
            .animateContentSize(),
    ) {
        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LiveSignalDockSummary(
                    targetLabel = commandScopeLabel,
                    focusLabel = focusLabel,
                    modifier = Modifier.weight(1.15f),
                )
                PreviewDockActionButton(
                    label = previewLabel,
                    enabled = previewEnabled,
                    onClick = if (previewIsRunning) onStopPreview else onStartPreview,
                    modifier = Modifier.weight(1f),
                )
                PreviewDockActionButton(
                    label = recordingLabel,
                    enabled = recordingEnabled,
                    recording = true,
                    onClick = if (recordingIsRunning) onStopRecording else onStartRecording,
                    modifier = Modifier.weight(1.08f),
                )
                PreviewDockActionButton(
                    label = allPreviewLabel,
                    enabled = allPreviewEnabled,
                    onClick = if (allPreviewIsRunning) onStopAllPreview else onStartAllPreview,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = onOpenOperate,
                    modifier = Modifier
                        .weight(0.8f)
                        .heightIn(min = 50.dp),
                ) {
                    Text("Operate")
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LiveSignalDockSummary(targetLabel = commandScopeLabel, focusLabel = focusLabel)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PreviewDockActionButton(
                        label = previewLabel,
                        enabled = previewEnabled,
                        onClick = if (previewIsRunning) onStopPreview else onStartPreview,
                        modifier = Modifier.weight(1f),
                    )
                    PreviewDockActionButton(
                        label = recordingLabel,
                        enabled = recordingEnabled,
                        recording = true,
                        onClick = if (recordingIsRunning) onStopRecording else onStartRecording,
                        modifier = Modifier.weight(1f),
                    )
                }
                PreviewDockActionButton(
                    label = allPreviewLabel,
                    enabled = allPreviewEnabled,
                    onClick = if (allPreviewIsRunning) onStopAllPreview else onStartAllPreview,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = onOpenOperate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp),
                ) {
                    Text("Operate")
                }
            }
        }
    }
}

@Composable
private fun LiveSignalRecordingStatus(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.heightIn(min = 50.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ShowChart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Column {
                Text("Live signal", style = MaterialTheme.typography.labelLarge)
                Text(
                    "From recording",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.74f),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun LiveSignalDockSummary(
    targetLabel: String,
    focusLabel: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = targetLabel,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "Operate: $focusLabel",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }

}

@Composable
private fun PreviewDockActionButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    recording: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 50.dp),
        colors = if (recording) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            )
        } else {
            ButtonDefaults.buttonColors()
        },
    ) {
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PreviewAdjustmentRail(
    session: DeviceSessionUiState,
    displayConfig: SignalDisplayConfig,
    onWindowChange: (SignalWindowPreset) -> Unit,
    onGainChange: (SignalGainPreset) -> Unit,
    onRemoveDcChange: (Boolean) -> Unit,
    onSetPreviewSelection: (PreviewSelection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val normalizedSelection = session.previewSelection.normalizedForDevice(session.parsedSystemParams?.ephysChannelCount)
    val optionCount = normalizedSelection.optionCount(session.parsedSystemParams?.ephysChannelCount)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        modifier = modifier.animateContentSize(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.End,
        ) {
            PreviewCompactStepperChip(
                axisLabel = "",
                currentLabel = compactPreviewSourceLabel(
                    normalizedSelection,
                    session.parsedSystemParams?.ephysChannelCount,
                ),
                canStepDown = optionCount > 1,
                canStepUp = optionCount > 1,
                minWidth = 82.dp,
                maxWidth = 132.dp,
                buttonSize = 36.dp,
                compact = false,
                onLabelClick = {
                    if (!session.isConnected) {
                        return@PreviewCompactStepperChip
                    }
                    onSetPreviewSelection(
                        normalizedSelection.copy(auxMode = !normalizedSelection.auxMode)
                            .normalizedForDevice(session.parsedSystemParams?.ephysChannelCount),
                    )
                },
                onStepDown = {
                    onSetPreviewSelection(
                        shiftPreviewSelection(
                            normalizedSelection,
                            -1,
                            session.parsedSystemParams?.ephysChannelCount,
                        ),
                    )
                },
                onStepUp = {
                    onSetPreviewSelection(
                        shiftPreviewSelection(
                            normalizedSelection,
                            1,
                            session.parsedSystemParams?.ephysChannelCount,
                        ),
                    )
                },
            )
            PreviewCompactToggleChip(
                label = "HPF",
                selected = displayConfig.removeDc,
                onClick = { onRemoveDcChange(!displayConfig.removeDc) },
                leadingIcon = Icons.Outlined.FilterAlt,
                compact = false,
                modifier = Modifier.heightIn(min = 40.dp),
            )
            PreviewCompactAdjustChip(
                label = "X",
                valueLabel = displayConfig.window.label,
                canStepDown = displayConfig.window != SignalWindowPreset.entries.first(),
                canStepUp = displayConfig.window != SignalWindowPreset.entries.last(),
                buttonSize = 36.dp,
                compact = false,
                onStepDown = { onWindowChange(displayConfig.window.step(-1)) },
                onStepUp = { onWindowChange(displayConfig.window.step(1)) },
            )
            PreviewCompactAdjustChip(
                label = "Y",
                valueLabel = displayConfig.gain.label,
                canStepDown = displayConfig.gain != SignalGainPreset.entries.first(),
                canStepUp = displayConfig.gain != SignalGainPreset.entries.last(),
                buttonSize = 36.dp,
                compact = false,
                onStepDown = { onGainChange(displayConfig.gain.step(-1)) },
                onStepUp = { onGainChange(displayConfig.gain.step(1)) },
            )
        }
    }
}

@Composable
private fun PreviewZoomReadoutBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
        modifier = modifier,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PreviewPreviewSourceControls(
    session: DeviceSessionUiState,
    displayConfig: SignalDisplayConfig,
    compact: Boolean = false,
    onRemoveDcChange: (Boolean) -> Unit,
    onSetPreviewSelection: (PreviewSelection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val normalizedSelection = session.previewSelection.normalizedForDevice(session.parsedSystemParams?.ephysChannelCount)
    val optionCount = normalizedSelection.optionCount(session.parsedSystemParams?.ephysChannelCount)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PreviewCompactStepperChip(
            axisLabel = "",
            currentLabel = compactPreviewSourceLabel(
                normalizedSelection,
                session.parsedSystemParams?.ephysChannelCount,
            ),
            canStepDown = optionCount > 1,
            canStepUp = optionCount > 1,
            minWidth = if (compact) 46.dp else 54.dp,
            maxWidth = if (compact) 70.dp else 88.dp,
            buttonSize = if (compact) 14.dp else 16.dp,
            compact = compact,
            onLabelClick = {
                if (!session.isConnected) {
                    return@PreviewCompactStepperChip
                }
                onSetPreviewSelection(
                    normalizedSelection.copy(auxMode = !normalizedSelection.auxMode)
                        .normalizedForDevice(session.parsedSystemParams?.ephysChannelCount),
                )
            },
            onStepDown = {
                onSetPreviewSelection(
                    shiftPreviewSelection(
                        normalizedSelection,
                        -1,
                        session.parsedSystemParams?.ephysChannelCount,
                    ),
                )
            },
            onStepUp = {
                onSetPreviewSelection(
                    shiftPreviewSelection(
                        normalizedSelection,
                        1,
                        session.parsedSystemParams?.ephysChannelCount,
                    ),
                )
            },
        )
        PreviewCompactToggleChip(
            label = "HPF",
            selected = displayConfig.removeDc,
            onClick = { onRemoveDcChange(!displayConfig.removeDc) },
            leadingIcon = Icons.Outlined.FilterAlt,
            compact = compact,
        )
    }
}

@Composable
private fun PreviewAxisControlsRow(
    displayConfig: SignalDisplayConfig,
    compact: Boolean = false,
    onWindowChange: (SignalWindowPreset) -> Unit,
    onGainChange: (SignalGainPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PreviewCompactAdjustChip(
            label = "X",
            valueLabel = displayConfig.window.label,
            canStepDown = displayConfig.window != SignalWindowPreset.entries.first(),
            canStepUp = displayConfig.window != SignalWindowPreset.entries.last(),
            buttonSize = if (compact) 14.dp else 16.dp,
            compact = compact,
            onStepDown = { onWindowChange(displayConfig.window.step(-1)) },
            onStepUp = { onWindowChange(displayConfig.window.step(1)) },
        )
        PreviewCompactAdjustChip(
            label = "Y",
            valueLabel = displayConfig.gain.label,
            canStepDown = displayConfig.gain != SignalGainPreset.entries.first(),
            canStepUp = displayConfig.gain != SignalGainPreset.entries.last(),
            buttonSize = if (compact) 14.dp else 16.dp,
            compact = compact,
            onStepDown = { onGainChange(displayConfig.gain.step(-1)) },
            onStepUp = { onGainChange(displayConfig.gain.step(1)) },
        )
    }
}

@Composable
private fun PreviewCompactAdjustChip(
    label: String,
    valueLabel: String? = null,
    canStepDown: Boolean,
    canStepUp: Boolean,
    onStepDown: () -> Unit,
    onStepUp: () -> Unit,
    buttonSize: Dp = 16.dp,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val compactLabel = buildString {
        append(label)
        valueLabel
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let {
                append(' ')
                append(it)
            }
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 2.dp else 3.dp,
                vertical = if (compact) 1.dp else 2.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 1.dp else 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = compactLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                modifier = Modifier.padding(start = if (compact) 3.dp else 4.dp, end = 1.dp),
                maxLines = 1,
            )
            OutlinedIconButton(
                onClick = onStepDown,
                enabled = canStepDown,
                modifier = Modifier.size(buttonSize),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Remove,
                    contentDescription = "$label down",
                    modifier = Modifier.size(buttonSize * 0.5f),
                )
            }
            OutlinedIconButton(
                onClick = onStepUp,
                enabled = canStepUp,
                modifier = Modifier.size(buttonSize),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "$label up",
                    modifier = Modifier.size(buttonSize * 0.5f),
                )
            }
        }
    }
}

@Composable
private fun PreviewCompactStepperChip(
    axisLabel: String,
    currentLabel: String,
    canStepDown: Boolean,
    canStepUp: Boolean,
    onStepDown: () -> Unit,
    onStepUp: () -> Unit,
    minWidth: Dp = 100.dp,
    maxWidth: Dp = 132.dp,
    buttonSize: Dp = 16.dp,
    compact: Boolean = false,
    onLabelClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val compactLabel = if (axisLabel.isBlank()) currentLabel else "$axisLabel $currentLabel"
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        modifier = modifier.widthIn(min = minWidth, max = maxWidth),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 1.dp else 2.dp,
                vertical = if (compact) 1.dp else 2.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 1.dp else 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = compactLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (onLabelClick != null) {
                            Modifier.clickable(onClick = onLabelClick)
                        } else {
                            Modifier
                        }
                    ),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            OutlinedIconButton(
                onClick = onStepDown,
                enabled = canStepDown,
                modifier = Modifier.size(buttonSize),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Remove,
                    contentDescription = if (axisLabel.isBlank()) "Select previous source" else "$axisLabel down",
                    modifier = Modifier.size(buttonSize * 0.5f),
                )
            }
            OutlinedIconButton(
                onClick = onStepUp,
                enabled = canStepUp,
                modifier = Modifier.size(buttonSize),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = if (axisLabel.isBlank()) "Select next source" else "$axisLabel up",
                    modifier = Modifier.size(buttonSize * 0.5f),
                )
            }
        }
    }
}

private fun compactPreviewSourceLabel(
    selection: PreviewSelection,
    ephysChannelCount: Int? = null,
): String {
    if (!selection.auxMode) {
        return selection.label(ephysChannelCount)
    }

    return when (selection.label(ephysChannelCount)) {
        "Digital Signal" -> "Dig"
        "Accelerometer X" -> "AccX"
        "Accelerometer Y" -> "AccY"
        "Accelerometer Z" -> "AccZ"
        "Gyroscope X" -> "GyrX"
        "Gyroscope Y" -> "GyrY"
        "Gyroscope Z" -> "GyrZ"
        "Magnetic X" -> "MagX"
        "Magnetic Y" -> "MagY"
        "Magnetic Z" -> "MagZ"
        "Test Signal" -> "Test"
        "DSP output A" -> "DspA"
        "DSP output B" -> "DspB"
        else -> "Aux${selection.index + 1}"
    }
}

@Composable
private fun PreviewCompactToggleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    compact: Boolean = false,
) {
    val shape = RoundedCornerShape(11.dp)
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.26f)
    }
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
    }
    Surface(
        shape = shape,
        color = containerColor,
        modifier = modifier
            .border(width = 1.dp, color = borderColor, shape = shape)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 5.dp else 4.dp,
                vertical = if (compact) 1.dp else 2.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leadingIcon?.let { icon ->
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(if (compact) 9.dp else 10.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
            )
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreviewTransportRow(
    modifier: Modifier = Modifier,
    actionSummary: LiveActionSummary,
    canResync: Boolean,
    onResync: () -> Unit,
    onStartPreview: () -> Unit,
    onStopPreview: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    buttonSize: Dp = 42.dp,
    spacing: Dp = 8.dp,
) {
    val previewActive = actionSummary.previewStopCount > 0
    val previewEnabled = actionSummary.canStartPreview || actionSummary.canStopPreview
    val recordingActive = actionSummary.recordingStopCount > 0
    val recordingEnabled = actionSummary.canStartRecording || actionSummary.canStopRecording

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PreviewTransportButton(
            icon = Icons.Outlined.Sync,
            contentDescription = "Resync scoped preview device",
            enabled = canResync,
            filled = false,
            label = "Sync",
            buttonSize = buttonSize,
            onClick = onResync,
        )
        PreviewTransportButton(
            icon = if (previewActive) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
            contentDescription = if (previewActive) "Stop live signal" else "Start live signal",
            enabled = previewEnabled,
            filled = previewActive,
            label = if (previewActive) "Stop live" else "Live",
            buttonSize = buttonSize,
            onClick = if (previewActive) onStopPreview else onStartPreview,
        )
        PreviewTransportButton(
            icon = if (recordingActive) Icons.Outlined.Stop else Icons.Outlined.FiberManualRecord,
            contentDescription = when {
                actionSummary.hasPendingRecordingStop -> "Retry pending recording stop"
                recordingActive -> "Stop recording"
                else -> "Start recording"
            },
            enabled = recordingEnabled,
            filled = recordingActive,
            label = if (recordingActive) "Stop rec" else "Record",
            tint = Color(0xFFD64545),
            buttonSize = buttonSize,
            onClick = if (recordingActive) onStopRecording else onStartRecording,
        )
    }
}

@Composable
private fun PreviewTransportButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    filled: Boolean,
    label: String? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
    buttonSize: Dp = 42.dp,
    onClick: () -> Unit,
) {
    if (label != null) {
        val modifier = Modifier.heightIn(min = 48.dp)
        if (filled) {
            FilledTonalButton(onClick = onClick, enabled = enabled, modifier = modifier) {
                Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) {
                Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        return
    }
    val touchTargetSize = maxOf(buttonSize, 48.dp)
    if (filled) {
        FilledTonalIconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(touchTargetSize),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size((touchTargetSize * 0.52f)),
            )
        }
    } else {
        OutlinedIconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(touchTargetSize),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size((touchTargetSize * 0.52f)),
            )
        }
    }
}

@Composable
private fun PreviewWaveformSyncOverlay(
    session: DeviceSessionUiState,
    modifier: Modifier = Modifier,
) {
    val syncing = session.awaitingLiveSync || session.hostState == BleHostSessionState.Syncing
    val progressLine = formatSyncProgressTelemetryAscii(session.lastSyncMetric, session.liveSync)
    val metricLine = formatSyncCompactLineAscii(session.lastSyncMetric, session.liveSync)
    val statusLine = session.syncText.ifBlank { session.statusText }
    val primaryLine = progressLine
        ?: metricLine
        ?: statusLine.takeIf { it.isNotBlank() }
        ?: if (syncing) "Waiting for live sync" else null

    if (primaryLine == null) {
        return
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (syncing) "SYNCING" else "SYNC",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
            )
            Text(
                text = primaryLine,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StackedSignalMonitorCard(
    sessions: List<DeviceSessionUiState>,
    activeSessionId: String?,
    onActivateSession: (String) -> Unit,
    displayConfig: SignalDisplayConfig,
    title: String,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(30.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                if (sessions.isEmpty()) "No live traces yet." else "${sessions.size} device${if (sessions.size == 1) "" else "s"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            )

            if (sessions.isEmpty()) {
                Text(
                    "Start live signal to populate the stack.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                )
            } else {
                sessions.forEach { session ->
                    SignalTraceCard(
                        session = session,
                        active = session.id == activeSessionId,
                        displayConfig = displayConfig,
                        onActivate = { onActivateSession(session.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SignalTraceCard(
    session: DeviceSessionUiState,
    active: Boolean,
    displayConfig: SignalDisplayConfig,
    onActivate: () -> Unit,
) {
    val syncLine = formatSyncCompactLineAscii(session.lastSyncMetric, session.liveSync)
        ?: session.bleLinkStats?.let(::formatBleLinkCompactLine)
        ?: session.syncText.ifBlank { session.statusText }
    val preparedTrace = preparePreviewTraceForDisplay(session, displayConfig)

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = if (active) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(Color(session.traceColorArgb), CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        session.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        buildTraceLegendStatus(session, active),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                    )
                }
                OutlinedButton(onClick = onActivate, enabled = !active) {
                    Text(if (active) "Focused" else "Focus")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoPill("Source", sessionPreviewLabel(session), Modifier.weight(1f))
                InfoPill("Traffic", "${session.commandTxCount}/${session.commandRxCount}", Modifier.weight(1f))
                InfoPill("Rec", formatSeconds(session.recordingSeconds), Modifier.weight(1f))
            }

            val gridLineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(18.dp),
                    )
            ) {
                val widthStep = size.width / 5f
                val heightStep = size.height / 4f
                repeat(6) { index ->
                    val x = index * widthStep
                    drawLine(gridLineColor, start = Offset(x, 0f), end = Offset(x, size.height), strokeWidth = 1f)
                }
                repeat(5) { index ->
                    val y = index * heightStep
                    drawLine(gridLineColor, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 1f)
                }

                val columns = preparedTrace.columns
                if (columns.isEmpty()) {
                    return@Canvas
                }

                fun toPlotY(value: Float): Float {
                    val normalized = value * displayConfig.gain.normalizedPerCount
                    return (size.height * 0.5f - normalized * size.height * 0.42f)
                        .coerceIn(0f, size.height)
                }

                var previousX: Float? = null
                var previousColumn: PreviewRenderColumn? = null
                columns.forEach { column ->
                    val x = column.xFraction.coerceIn(0f, 1f) * size.width
                    val maxY = toPlotY(column.maxValue)
                    val minY = toPlotY(column.minValue)

                    drawScopeBandConnector(
                        previous = previousColumn,
                        current = column,
                        previousX = previousX,
                        currentX = x,
                        color = Color(session.traceColorArgb),
                        strokeWidth = 2.8f,
                        toPlotY = ::toPlotY,
                    )

                    drawLine(
                        color = Color(session.traceColorArgb).copy(alpha = 0.9f),
                        start = Offset(x, maxY),
                        end = Offset(x, minY),
                        strokeWidth = 2.2f,
                        cap = StrokeCap.Round,
                    )

                    previousX = x
                    previousColumn = column
                }

                if (preparedTrace.threshold != null) {
                    val normalizedThreshold = preparedTrace.threshold * displayConfig.gain.normalizedPerCount
                    val thresholdY = (size.height * 0.5f - normalizedThreshold * size.height * 0.42f)
                        .coerceIn(0f, size.height)
                    drawLine(
                        color = Color(session.traceColorArgb).copy(alpha = 0.55f),
                        start = Offset(0f, thresholdY),
                        end = Offset(size.width, thresholdY),
                        strokeWidth = 2f,
                    )
                }
            }

            preparedTrace.thresholdLabel?.let { label ->
                Text(
                    "$label ${preparedTrace.threshold?.format(2) ?: "--"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(session.traceColorArgb).copy(alpha = 0.78f),
                )
            }

            Text(
                syncLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
        }
    }
}

@Composable
private fun PreviewDeviceSelectorChip(
    session: DeviceSessionUiState,
    active: Boolean,
    compact: Boolean = false,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(if (compact) 18.dp else 16.dp)
    Surface(
        shape = shape,
        color = if (active) {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)
        },
        modifier = modifier
            .border(
                width = 1.dp,
                color = if (active) Color(session.traceColorArgb) else MaterialTheme.colorScheme.outline.copy(alpha = 0.26f),
                shape = shape,
            )
            .clickable(onClick = onActivate),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 9.dp else 10.dp,
                vertical = if (compact) 7.dp else 8.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(if (compact) 8.dp else 10.dp)
                    .background(Color(session.traceColorArgb), CircleShape)
            )
            Text(
                compactPreviewSelectorLabel(session.name, session.address),
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TraceLegendChip(
    session: DeviceSessionUiState,
    active: Boolean,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (active) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        },
        modifier = modifier
            .border(
                width = 1.dp,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                shape = RoundedCornerShape(18.dp),
            )
            .clickable(onClick = onActivate),
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .background(Color(session.traceColorArgb), CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    compactDeviceUiLabel(session.name, session.address),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                buildTraceLegendStatus(session, active),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DigitalFlagTile(
    name: String,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = if (active) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.42f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.24f),
                    shape = RoundedCornerShape(18.dp),
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (active) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (active) "Active" else "Idle",
                style = MaterialTheme.typography.bodySmall,
                color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f),
            )
        }
    }
}

private fun buildTraceLegendStatus(
    session: DeviceSessionUiState,
    active: Boolean,
): String {
    val mode = when {
        session.isRecordingLike && session.recorderBackedLiveSignal -> "LIVE REC"
        session.isRecordingLike -> "REC"
        isPreviewingSession(session) -> "LIVE"
        else -> session.statusText
    }
    val source = sessionPreviewLabel(session)
    return if (active) {
        "Focused  |  $mode  |  $source"
    } else {
        "$mode  |  $source"
    }
}

private fun formatImpedancePreview(values: List<Int>): String {
    return values.take(8).joinToString() + if (values.size > 8) "..." else ""
}

private fun formatCrosstalkSummary(columns: Map<Int, List<Int>>): String {
    return columns.toSortedMap().entries.joinToString { (channel, values) ->
        "ch$channel (${values.size})"
    }
}

@Composable
private fun InfoPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(18.dp),
                )
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                label.uppercase(Locale.US),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
            )
            Text(
                value,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompactHubMetricBand(
    metrics: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    if (metrics.isEmpty()) {
        return
    }

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        metrics.forEach { (label, value) ->
            InfoPill(label = label, value = value)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompactActionChipFlow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

@Composable
private fun CompactActionChip(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    emphasized: Boolean = false,
    modifier: Modifier = Modifier,
) {
    AssistChip(
        onClick = onClick,
        enabled = enabled,
        label = { Text(label) },
        modifier = modifier.heightIn(min = 48.dp),
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (emphasized) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            labelColor = if (emphasized) {
                MaterialTheme.colorScheme.onBackground
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
            },
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
            disabledLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        ),
    )
}

@Composable
private fun EmptyStateCard(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(28.dp),
    ) {
        Text(
            text = message,
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatEventTime(timestampMs: Long): String {
    return Instant.ofEpochMilli(timestampMs)
        .atZone(ZoneId.systemDefault())
        .format(eventTimeFormatter)
}

private fun formatBleRoleIdentity(roleTag: String, functionTag: String): String {
    val cleanRole = roleTag.trim()
    val cleanFunction = functionTag.trim()
    if (cleanRole.isEmpty()) {
        return cleanFunction
    }
    if (cleanFunction.isEmpty()) {
        return cleanRole
    }
    return "$cleanRole ($cleanFunction)"
}

private fun compactDeviceUiLabel(primary: String?, secondary: String? = null): String {
    val raw = sequenceOf(primary, secondary)
        .map { it?.trim().orEmpty() }
        .firstOrNull { candidate ->
            candidate.isNotBlank() && !looksLikeBleMac(candidate)
        }
        .orEmpty()
    if (raw.isBlank()) {
        return "Device"
    }
    return when {
        Ce32Protocol.matchesKnownNamePrefix(raw) -> raw
        raw.length <= 12 -> raw
        raw.contains(" ") -> raw
        else -> raw.take(12)
    }
}

private fun compactPreviewRailLabel(primary: String?, secondary: String? = null): String {
    val raw = sequenceOf(primary, secondary)
        .map { it?.trim().orEmpty() }
        .firstOrNull { candidate ->
            candidate.isNotBlank() && !looksLikeBleMac(candidate)
        }
        .orEmpty()
    if (raw.isBlank()) {
        return "--"
    }

    if (Ce32Protocol.matchesKnownNamePrefix(raw)) {
        val prefix = raw.substringBefore('_').ifBlank { raw }.take(6)
        val suffixSource = raw.substringAfter('_', "")
        val suffix = when {
            suffixSource.length >= 4 -> suffixSource.takeLast(4)
            raw.length >= 4 -> raw.takeLast(4)
            else -> ""
        }
        return if (suffix.isNotBlank() && !prefix.endsWith(suffix, ignoreCase = true)) {
            "$prefix\n$suffix"
        } else {
            prefix
        }
    }

    return raw.take(8)
}

private fun compactPreviewSelectorLabel(primary: String?, secondary: String? = null): String {
    return compactPreviewRailLabel(primary, secondary).replace('\n', ' ')
}

private fun looksLikeBleMac(value: String): Boolean {
    return value.matches(Regex("^[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}$"))
}

private fun preferredAdvertisementBattery(session: DeviceSessionUiState): Double? {
    return session.advertisedVoltage ?: session.voltage
}

private fun sessionSelectorTelemetryLabel(session: DeviceSessionUiState): String {
    val rssi = session.rssi?.let { "$it dBm" } ?: "RSSI --"
    val battery = preferredAdvertisementBattery(session)?.let(::formatVoltageLabel) ?: "Batt --"
    return "$rssi  ·  $battery"
}

private fun advertisementStateLabel(session: DeviceSessionUiState): String {
    return when {
        session.isRecordingLike -> "Recording"
        isPreviewingSession(session) -> "Live signal"
        session.isConnected -> "Connected"
        session.isLinkingLike -> "Connecting"
        session.advertisedHealthStatus?.recording == true -> "Recording (advertised)"
        session.advertisedHealthStatus?.previewing == true -> "Live signal (advertised)"
        session.lastSeenAtMs > 0L -> formatRecentSeenLabel(session)
        else -> "Awaiting advertisement"
    }
}

private fun advertisementSourceLabel(session: DeviceSessionUiState): String {
    return when {
        session.advertisedHealthStatus?.isAiAdvertisementPage == true -> "CE64 V8 AI advertisement"
        session.advertisedHealthStatus != null -> "CE64 v${session.advertisedHealthStatus.formatVersion} health/storage advertisement"
        session.hasAdvertisementTelemetry -> "Advertisement telemetry received"
        session.advertisedServiceMatch -> "CE service advertised"
        session.namePrefixMatch -> "CE name advertised"
        else -> "BLE advertisement"
    }
}

private fun advertisementAiLabel(status: Ce64AdvertisementStatus): String {
    return buildString {
        append("AI")
        status.aiModelId?.let { modelId -> append(" M$modelId") }
        status.aiClassId?.let { classId -> append(" C$classId") }
        status.aiConfidencePercentage?.let { confidence -> append(" $confidence%") }
        if (status.aiResultIsNew) append(" new")
    }
}

private fun advertisementHealthLabel(status: Ce64AdvertisementStatus): String {
    val failed = status.failedSubsystems
    val degraded = status.degradedSubsystems
    return when {
        failed != 0 -> "Fault F${failed.toString(16).uppercase().padStart(2, '0')}"
        else -> "Degraded D${degraded.toString(16).uppercase().padStart(2, '0')}"
    }
}

private fun formatConnectedBatteryLabel(session: DeviceSessionUiState): String {
    return when {
        session.voltage != null -> "${formatVoltageLabel(session.voltage)} live"
        session.advertisedVoltage != null -> "${formatVoltageLabel(session.advertisedVoltage)} ad"
        else -> "Not reported"
    }
}

private fun formatDeviceFirmwareLabel(session: DeviceSessionUiState): String {
    return buildList {
        session.swVersion?.takeIf { it.isNotBlank() }?.let { add("SW $it") }
        session.hwVersion?.takeIf { it.isNotBlank() }?.let { add("HW $it") }
    }.joinToString(" / ").ifBlank { "Not reported" }
}

private fun bleOtaPhaseLabel(phase: BleOtaPhase): String = when (phase) {
    BleOtaPhase.Idle -> "Idle"
    BleOtaPhase.PackageReady -> "Ready"
    BleOtaPhase.Staging -> "Staging"
    BleOtaPhase.Verifying -> "Verifying"
    BleOtaPhase.ReadyToInstall -> "Verified"
    BleOtaPhase.InstallRequested -> "Restarting"
    BleOtaPhase.Failed -> "Paused"
}

private fun formatWaveformOffsetLabel(offset: Float): String {
    return String.format(Locale.US, "%+.0f%%", offset * 100f)
}

private fun formatSeconds(seconds: Long): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hrs > 0) {
        "%d:%02d:%02d".format(hrs, mins, secs)
    } else {
        "%02d:%02d".format(mins, secs)
    }
}

private fun formatRateLabel(rate: Int?): String {
    val value = rate ?: return "--"
    if (value <= 0) {
        return "Off"
    }
    return if (value >= 1000) {
        if (value % 1000 == 0) {
            "${value / 1000} kHz"
        } else {
            "%.2f kHz".format(value / 1000f)
        }
    } else {
        "$value Hz"
    }
}

private fun formatPercent(value: Double): String = String.format(Locale.US, "%.1f%%", value)

private fun formatRateHz(value: Double): String = String.format(Locale.US, "%.2f/s", value)

private fun formatMs(value: Double?): String {
    val safe = value ?: return "--"
    return String.format(Locale.US, "%.1f ms", safe)
}

private fun formatSignedMs(value: Double?): String {
    val safe = value ?: return "--"
    return String.format(Locale.US, "%+.1f ms", safe)
}

private fun formatVoltageLabel(value: Double?): String {
    val safe = value ?: return "--"
    return String.format(Locale.US, "%.2f V", safe)
}

private fun formatBatteryLevelLabel(value: Double?): String {
    return formatVoltageLabel(value)
}

private fun formatRecentSeenLabel(session: DeviceSessionUiState, nowMs: Long = System.currentTimeMillis()): String {
    if (session.isConnected || session.lastSeenAtMs <= 0L) {
        return ""
    }
    val ageMs = (nowMs - session.lastSeenAtMs).coerceAtLeast(0L)
    if (ageMs < 5_000L) {
        return "Seen now"
    }
    val ageSeconds = ageMs / 1000L
    return when {
        ageSeconds < 60L -> "Seen ${ageSeconds}s ago"
        ageSeconds < 3600L -> "Seen ${ageSeconds / 60L}m ago"
        else -> "Seen ${ageSeconds / 3600L}h ago"
    }
}

internal fun formatUsedSpaceLabel(value: Double?): String {
    val safe = value ?: return "--"
    return String.format(Locale.US, "%.2f MB", safe)
}

internal fun formatOnlineCounterExtras(session: DeviceSessionUiState): String? {
    val segments = buildList {
        if (session.notificationRxCount > 0) {
            add("Notif ${session.notificationRxCount}")
        }
        if (session.legacyConfigBusyCount > 0) {
            add("Busy ${session.legacyConfigBusyCount}")
        }
    }
    return segments.takeIf { it.isNotEmpty() }?.joinToString("  |  ")
}

private fun formatHexByte(value: Int): String =
    value.coerceIn(0, 0xFF).toString(16).uppercase().padStart(2, '0').let { "0x$it" }

private fun formatBleLinkCompactLine(stats: BleLinkStatsUiState): String {
    return "Link ${formatHexByte(stats.lastRssiRaw)}  |  Lost ${formatPercent(stats.lossPercent)}  |  Rx ${formatRateHz(stats.rxRateHz)}"
}

private fun resolveSyncMeasurementCount(metric: SyncMetricUiState?, liveSync: LiveSyncUiState?): Int? {
    val metricCount = metric?.sampleCount ?: 0
    val liveCount = liveSync?.sampleCount ?: 0
    val resolved = maxOf(metricCount, liveCount)
    return resolved.takeIf { it > 0 }
}

private fun resolveSyncAccuracyEstimateMs(metric: SyncMetricUiState?, liveSync: LiveSyncUiState?): Double? {
    return when {
        liveSync != null && !liveSync.outlier && liveSync.sampleCount > 0 -> liveSync.rollingStdMs
        else -> metric?.accuracyMs
    }
}

private fun formatSyncProgressTelemetryAscii(metric: SyncMetricUiState?, liveSync: LiveSyncUiState?): String? {
    val parts = buildList {
        resolveSyncAccuracyEstimateMs(metric, liveSync)?.let { add("Acc ${formatMs(it)}") }
        resolveSyncMeasurementCount(metric, liveSync)?.let { add("N $it") }
        when {
            liveSync != null && liveSync.outlier -> add("Sync sample rejected")
            liveSync != null -> add("Off ${formatSignedMs(liveSync.rollingMeanMs)}")
            metric?.offsetMs != null -> add("Off ${formatSignedMs(metric.offsetMs)}")
        }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(separator = "  |  ")
}

private fun formatSyncCompactLineAscii(metric: SyncMetricUiState?, liveSync: LiveSyncUiState?): String? {
    val accuracyText = formatMs(resolveSyncAccuracyEstimateMs(metric, liveSync))
    val measurementCount = resolveSyncMeasurementCount(metric, liveSync) ?: 0
    if (liveSync != null) {
        return if (liveSync.outlier) {
            "Sync sample rejected  |  n $measurementCount"
        } else {
            "Sync live off ${formatSignedMs(liveSync.rollingMeanMs)}  |  acc $accuracyText  |  dly ${formatMs(liveSync.delayMs)}  |  n $measurementCount"
        }
    }

    metric ?: return null
    val offset = formatSignedMs(metric.offsetMs)
    val accuracy = accuracyText
    val delay = formatMs(metric.delayMs)
    return "Sync ${metric.modeLabel.lowercase(Locale.US)} $offset  |  acc $accuracy  |  dly $delay  |  n $measurementCount"
}

private fun formatSyncTightLineAscii(metric: SyncMetricUiState?, liveSync: LiveSyncUiState?): String? {
    val accuracyText = formatMs(resolveSyncAccuracyEstimateMs(metric, liveSync))
    val measurementCount = resolveSyncMeasurementCount(metric, liveSync) ?: 0
    if (liveSync != null) {
        if (liveSync.outlier) {
            return "sync sample rejected  |  n $measurementCount"
        }
        val offsetText = formatSignedMs(liveSync.rollingMeanMs)
        return buildString {
            append("off ")
            append(offsetText)
            append("  |  acc ")
            append(accuracyText)
            append("  |  n ")
            append(measurementCount)
        }
    }
    metric ?: return null
    val offset = metric.offsetMs?.let(::formatSignedMs) ?: "--"
    return "off $offset  |  acc $accuracyText  |  n $measurementCount"
}

private fun formatLiveSyncDetailAscii(liveSync: LiveSyncUiState, metric: SyncMetricUiState? = null): String {
    val accuracyText = formatMs(resolveSyncAccuracyEstimateMs(metric, liveSync))
    val measurementCount = resolveSyncMeasurementCount(metric, liveSync) ?: liveSync.sampleCount
    return if (liveSync.outlier) {
        "Live device clock ${liveSync.deviceClockLabel} reported an outlier offset of ${formatSignedMs(liveSync.lastOffsetMs.toDouble())} with estimated accuracy $accuracyText over $measurementCount measurements and ${formatMs(liveSync.delayMs)} one-way delay."
    } else {
        "Live device clock ${liveSync.deviceClockLabel} is tracking at ${formatSignedMs(liveSync.rollingMeanMs)} with estimated accuracy $accuracyText over $measurementCount measurements and ${formatMs(liveSync.delayMs)} one-way delay."
    }
}

private fun formatHex(value: Long): String = "0x${value.toString(16).uppercase()}"

private fun formatHexU16(value: Int): String = value.coerceIn(0, 0xFFFF).toString(16).uppercase().padStart(4, '0')

private fun parseHexU16(value: String): Int? {
    val normalized = value.trim().removePrefix("0x").removePrefix("0X")
    if (normalized.isEmpty()) {
        return null
    }
    return normalized.toIntOrNull(16)?.takeIf { it in 0..0xFFFF }
}

private fun sanitizeHexInput(value: String): String {
    return value
        .uppercase()
        .filter { it.isDigit() || it in 'A'..'F' }
        .take(4)
}

private fun sanitizeIntInput(value: String): String {
    return value.filter(Char::isDigit)
}

private fun sanitizeFloatInput(value: String): String {
    val builder = StringBuilder()
    var dotSeen = false
    for (char in value) {
        when {
            char.isDigit() -> builder.append(char)
            char == '.' && !dotSeen -> {
                builder.append(char)
                dotSeen = true
            }
        }
    }
    return builder.toString()
}

private fun Float.format(decimals: Int): String = "%.${decimals}f".format(this)

private fun createGrayscaleBitmap(image: ByteArray, pixels: Int): Bitmap? {
    if (pixels <= 0 || image.size < pixels * pixels) {
        return null
    }

    val colors = IntArray(pixels * pixels)
    for (index in colors.indices) {
        val value = image[index].toInt() and 0xFF
        colors[index] = android.graphics.Color.argb(255, value, value, value)
    }

    return Bitmap.createBitmap(colors, pixels, pixels, Bitmap.Config.ARGB_8888)
}
