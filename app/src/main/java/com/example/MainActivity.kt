package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val telemetryState by BatteryBleStateHolder.telemetryState.collectAsStateWithLifecycle()

                var hasPermissions by remember {
                    mutableStateOf(PermissionHelper.hasAllPermissions(this@MainActivity))
                }

                var isBatteryOptIgnored by remember {
                    mutableStateOf(PermissionHelper.isBatteryOptimizationIgnored(this@MainActivity))
                }

                // Permission launcher for multi-version BLE & Notification permissions
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { result ->
                    val allGranted = result.values.all { it }
                    hasPermissions = allGranted
                    if (allGranted) {
                        Toast.makeText(
                            this@MainActivity,
                            "Permissions granted. Ready to start BLE service.",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "Some permissions were denied. BLE scanning may be restricted.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                // Battery optimization exemption launcher
                val batteryOptLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) {
                    isBatteryOptIgnored = PermissionHelper.isBatteryOptimizationIgnored(this@MainActivity)
                }

                // Refresh statuses on lifecycle RESUME
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            hasPermissions = PermissionHelper.hasAllPermissions(this@MainActivity)
                            isBatteryOptIgnored = PermissionHelper.isBatteryOptimizationIgnored(this@MainActivity)
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    HomeScreen(
                        telemetryState = telemetryState,
                        hasPermissions = hasPermissions,
                        isBatteryOptIgnored = isBatteryOptIgnored,
                        onToggleService = { enable ->
                            if (enable) {
                                if (!hasPermissions) {
                                    permissionLauncher.launch(PermissionHelper.getRequiredPermissions())
                                } else {
                                    BatteryBleService.startService(this@MainActivity)
                                }
                            } else {
                                BatteryBleService.stopService(this@MainActivity)
                            }
                        },
                        onSelectInterval = { seconds ->
                            BatteryBleService.setInterval(this@MainActivity, seconds)
                        },
                        onRequestPermissions = {
                            permissionLauncher.launch(PermissionHelper.getRequiredPermissions())
                        },
                        onRequestBatteryOptimization = {
                            try {
                                batteryOptLauncher.launch(
                                    PermissionHelper.createBatteryOptimizationIntent(this@MainActivity)
                                )
                            } catch (e: Exception) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Could not open battery optimization settings: ${e.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onManualSend = {
                            BatteryBleService.triggerManualSend(this@MainActivity)
                            Toast.makeText(this@MainActivity, "Manual BLE packet queued", Toast.LENGTH_SHORT).show()
                        },
                        onReconnect = {
                            BatteryBleService.reconnect(this@MainActivity)
                            Toast.makeText(this@MainActivity, "Searching for ESP32…", Toast.LENGTH_SHORT).show()
                        },
                        onClearLogs = {
                            BatteryBleStateHolder.clearLogs()
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
