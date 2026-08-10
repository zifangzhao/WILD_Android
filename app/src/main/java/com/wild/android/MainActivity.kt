package com.wild.android

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.wild.android.ble.bluetoothManagerCompat
import com.wild.android.ui.WildApp
import com.wild.android.ui.WildTheme

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<WildViewModel>()
    private var debugDestinationOverride by mutableStateOf<String?>(null)
    private var debugDestinationToken by mutableStateOf(0)
    private val wildApplication: WildApplication
        get() = application as WildApplication
    private val backgroundBleBackCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (ensureBackgroundRuntimeIfNeeded()) {
                moveTaskToBack(true)
                return
            }

            isEnabled = false
            onBackPressedDispatcher.onBackPressed()
            isEnabled = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        onBackPressedDispatcher.addCallback(this, backgroundBleBackCallback)

        setContent {
            WildTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    BleReadinessGate(
                        onBleReady = viewModel::startScan,
                        content = {
                            WildApp(
                                viewModel = viewModel,
                                debugDestinationOverride = debugDestinationOverride,
                                debugDestinationToken = debugDestinationToken,
                                onDebugDestinationConsumed = {
                                    debugDestinationOverride = null
                                },
                            )
                        },
                    )
                }
            }
        }

        handleDebugIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        wildApplication.setUiForeground(true)
    }

    override fun onUserLeaveHint() {
        ensureBackgroundRuntimeIfNeeded()
        super.onUserLeaveHint()
    }

    override fun onStop() {
        wildApplication.setUiForeground(false)
        if (!isChangingConfigurations && wildApplication.shouldKeepBackgroundBleRuntime()) {
            BleForegroundService.start(this)
        }
        super.onStop()
    }

    override fun onDestroy() {
        if (isFinishing && !isChangingConfigurations && wildApplication.shouldPreserveBleWorkAcrossUiExit()) {
            BleForegroundService.start(this)
        }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDebugIntent(intent)
    }

    private fun handleDebugIntent(intent: Intent?) {
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0) {
            return
        }

        val command = intent?.getStringExtra(DebugCommandExtra)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        if (command != null) {
            viewModel.runDebugCommand(command)
            intent.removeExtra(DebugCommandExtra)
        }

        val destinationOverride = intent?.getStringExtra(DebugDestinationExtra)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        if (destinationOverride != null) {
            debugDestinationOverride = destinationOverride
            debugDestinationToken += 1
            intent.removeExtra(DebugDestinationExtra)
        }
    }

    private fun ensureBackgroundRuntimeIfNeeded(): Boolean {
        if (!wildApplication.shouldPreserveBleWorkAcrossUiExit()) {
            return false
        }

        BleForegroundService.start(this)
        return true
    }

    private companion object {
        const val DebugCommandExtra = "codex_cmd"
        const val DebugDestinationExtra = "codex_destination"
    }
}

@Composable
private fun BleReadinessGate(
    onBleReady: () -> Unit,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val supportsBle by remember(context) {
        mutableStateOf(hasBleSupport(context))
    }
    var hasPermissions by remember {
        mutableStateOf(hasBlePermissions(context))
    }
    var bluetoothEnabled by remember {
        mutableStateOf(supportsBle && hasPermissions && isBluetoothEnabled(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        hasPermissions = hasBlePermissions(context)
        bluetoothEnabled = supportsBle && hasPermissions && isBluetoothEnabled(context)
    }

    val bluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        bluetoothEnabled = supportsBle && hasPermissions && isBluetoothEnabled(context)
    }

    DisposableEffect(lifecycleOwner, context, supportsBle, hasPermissions) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermissions = hasBlePermissions(context)
                bluetoothEnabled = supportsBle && hasPermissions && isBluetoothEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(supportsBle, hasPermissions, bluetoothEnabled) {
        if (supportsBle && hasPermissions && bluetoothEnabled) {
            onBleReady()
        }
    }

    when {
        !supportsBle -> GateCard(
            title = "Bluetooth LE is unavailable",
            message = "This Android device does not report Bluetooth Low Energy support. WILD Android requires BLE hardware to connect to CE32 / CE64 devices.",
            primaryLabel = null,
            onPrimary = null,
        )

        !hasPermissions -> GateCard(
            title = "Bluetooth permissions are required",
            message = "WILD Android scans for CE32 / CE64 / WILD devices, connects over BLE, and streams live monitoring data. Grant Bluetooth access to continue.",
            primaryLabel = "Grant Permissions",
            onPrimary = {
                permissionLauncher.launch(requiredBlePermissions())
            },
        )

        !bluetoothEnabled -> GateCard(
            title = "Bluetooth is turned off",
            message = "Turn Bluetooth on before scanning for CE32 / CE64 / WILD devices. The app will start scanning as soon as Bluetooth is enabled.",
            primaryLabel = "Enable Bluetooth",
            onPrimary = {
                bluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            },
            secondaryLabel = "Refresh State",
            onSecondary = {
                bluetoothEnabled = supportsBle && hasPermissions && isBluetoothEnabled(context)
            },
        )

        else -> content()
    }
}

@Composable
private fun GateCard(
    title: String,
    message: String,
    primaryLabel: String?,
    onPrimary: (() -> Unit)?,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.background,
                    )
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Pocket Console", style = MaterialTheme.typography.titleMedium)
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (primaryLabel != null && onPrimary != null) {
                    Button(
                        onClick = onPrimary,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(primaryLabel)
                    }
                }
                if (secondaryLabel != null && onSecondary != null) {
                    OutlinedButton(
                        onClick = onSecondary,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(secondaryLabel, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

private fun requiredBlePermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.POST_NOTIFICATIONS,
        )
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}

private fun hasBlePermissions(context: Context): Boolean {
    return requiredBlePermissions().all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}

private fun hasBleSupport(context: Context): Boolean {
    return context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
}

private fun isBluetoothEnabled(context: Context): Boolean {
    val bluetoothManager = context.bluetoothManagerCompat() ?: return false
    val adapter = bluetoothManager.adapter ?: return false
    return runCatching { adapter.isEnabled }.getOrDefault(false)
}
