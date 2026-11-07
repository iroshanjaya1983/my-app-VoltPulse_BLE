package com.example

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceHighlight
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.ElectricAmber
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElectricGreen
import com.example.ui.theme.ElectricRed

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    telemetryState: TelemetryState,
    hasPermissions: Boolean,
    isBatteryOptIgnored: Boolean,
    onToggleService: (Boolean) -> Unit,
    onSelectInterval: (Int) -> Unit,
    onRequestPermissions: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    onManualSend: () -> Unit,
    onReconnect: () -> Unit,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Bar
            HeaderSection(
                isServiceRunning = telemetryState.isServiceRunning,
                bleStatus = telemetryState.bleStatus
            )

            // Permissions Alert Banner (if needed)
            if (!hasPermissions) {
                PermissionsAlertCard(onRequestPermissions = onRequestPermissions)
            }

            // Battery Optimization Exemption Banner (if needed)
            if (!isBatteryOptIgnored) {
                BatteryOptimizationCard(onRequestBatteryOptimization = onRequestBatteryOptimization)
            }

            // Connection Hero Status Card
            ConnectionStatusCard(
                telemetryState = telemetryState,
                onReconnect = onReconnect
            )

            // Live Battery Telemetry Card
            BatteryTelemetryCard(batteryInfo = telemetryState.batteryInfo)

            // Foreground Service Controls Card
            ServiceControlCard(
                isServiceRunning = telemetryState.isServiceRunning,
                onToggleService = onToggleService
            )

            // Background Broadcast Frequency Card
            IntervalSettingsCard(
                currentInterval = telemetryState.updateIntervalSeconds,
                onSelectInterval = onSelectInterval
            )

            // Telemetry Stream & Test Packet Card
            TelemetryStreamCard(
                telemetryState = telemetryState,
                onManualSend = onManualSend,
                onClearLogs = onClearLogs
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HeaderSection(
    isServiceRunning: Boolean,
    bleStatus: BleStatus
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF00E5A3), Color(0xFF007A50))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = "VoltPulse Logo",
                    tint = Color(0xFF090E17),
                    modifier = Modifier.size(26.dp)
                )
            }
            Column {
                Text(
                    text = "VoltPulse BLE",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 20.sp
                    )
                )
                Text(
                    text = "ESP32 Battery Telemetry Service",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = Color(0xFF94A3B8)
                    )
                )
            }
        }

        // Live status pill badge
        val (badgeColor, badgeText) = when {
            !isServiceRunning -> Color(0xFF64748B) to "OFF"
            bleStatus == BleStatus.CONNECTED -> ElectricGreen to "SYNCING"
            bleStatus == BleStatus.SCANNING -> ElectricCyan to "SCANNING"
            bleStatus == BleStatus.CONNECTING -> ElectricCyan to "CONNECTING"
            bleStatus == BleStatus.DISCONNECTED -> ElectricAmber to "RETRY"
            else -> ElectricRed to "ERROR"
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = badgeColor.copy(alpha = 0.15f),
            border = borderStroke(1.dp, badgeColor.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(badgeColor)
                )
                Text(
                    text = badgeText,
                    color = badgeColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun PermissionsAlertCard(
    onRequestPermissions: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("permissions_alert_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF3B1E1E)),
        border = borderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Permission Alert",
                    tint = ElectricRed
                )
                Text(
                    text = "Permissions Required",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
            }
            Text(
                text = "VoltPulse requires Bluetooth Scan, Bluetooth Connect, and Notification permissions to transmit battery telemetry in the background.",
                style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFFFCDD2))
            )
            Button(
                onClick = onRequestPermissions,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("grant_permissions_button"),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricRed)
            ) {
                Text(text = "Grant Permissions", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun BatteryOptimizationCard(
    onRequestBatteryOptimization: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("battery_opt_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2E2211)),
        border = borderStroke(1.dp, ElectricAmber.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.BatteryAlert,
                    contentDescription = "Battery Optimization",
                    tint = ElectricAmber
                )
                Text(
                    text = "Exclude from Battery Optimization",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
            }
            Text(
                text = "OEM system killers (Samsung, Xiaomi MIUI, Huawei) may suspend background services unless whitelisted from aggressive Doze mode.",
                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFFFECB3))
            )
            OutlinedButton(
                onClick = onRequestBatteryOptimization,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ignore_battery_opt_button"),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricAmber),
                border = borderStroke(1.dp, ElectricAmber)
            ) {
                Text(text = "Allow Unrestricted Background Running", fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun ConnectionStatusCard(
    telemetryState: TelemetryState,
    onReconnect: () -> Unit
) {
    val bleStatus = telemetryState.bleStatus
    val isConnected = bleStatus == BleStatus.CONNECTED
    val isScanning = bleStatus == BleStatus.SCANNING || bleStatus == BleStatus.CONNECTING

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val cardBorderColor by animateColorAsState(
        targetValue = when (bleStatus) {
            BleStatus.CONNECTED -> ElectricGreen
            BleStatus.SCANNING, BleStatus.CONNECTING -> ElectricCyan
            BleStatus.DISCONNECTED -> ElectricAmber
            BleStatus.ERROR -> ElectricRed
            BleStatus.STOPPED -> Color(0xFF24334C)
        },
        label = "cardBorderColor"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("connection_status_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = borderStroke(1.5.dp, cardBorderColor.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                when (bleStatus) {
                                    BleStatus.CONNECTED -> ElectricGreen.copy(alpha = 0.2f)
                                    BleStatus.SCANNING, BleStatus.CONNECTING -> ElectricCyan.copy(alpha = 0.2f)
                                    BleStatus.DISCONNECTED -> ElectricAmber.copy(alpha = 0.2f)
                                    else -> Color(0xFF1E293B)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                isConnected -> Icons.Default.BluetoothConnected
                                isScanning -> Icons.Default.BluetoothSearching
                                else -> Icons.Default.Bluetooth
                            },
                            contentDescription = "Bluetooth Status",
                            tint = when {
                                isConnected -> ElectricGreen
                                isScanning -> ElectricCyan
                                bleStatus == BleStatus.DISCONNECTED -> ElectricAmber
                                else -> Color(0xFF64748B)
                            },
                            modifier = Modifier
                                .size(28.dp)
                                .then(if (isScanning) Modifier.alpha(pulseAlpha) else Modifier)
                        )
                    }

                    Column {
                        Text(
                            text = when (bleStatus) {
                                BleStatus.CONNECTED -> "ESP32 Connected"
                                BleStatus.SCANNING -> "Scanning for ESP32…"
                                BleStatus.CONNECTING -> "Connecting to GATT…"
                                BleStatus.DISCONNECTED -> "Disconnected (Retrying)"
                                BleStatus.ERROR -> "Connection Error"
                                BleStatus.STOPPED -> "BLE Offline"
                            },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        Text(
                            text = telemetryState.connectedDeviceName ?: "ESP32 GATT Server",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
                        )
                    }
                }

                if (telemetryState.isServiceRunning) {
                    IconButton(
                        onClick = onReconnect,
                        modifier = Modifier.testTag("reconnect_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Scan and Reconnect",
                            tint = ElectricCyan
                        )
                    }
                }
            }

            // Target UUID metadata strip
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = DarkSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Target Service UUID",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF64748B))
                        )
                        Text(
                            text = "0x19B10000",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = ElectricCyan,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }
                    Text(
                        text = "19B10000-E8F2-537E-4F6C-D104768A1214",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFFCBD5E1),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Error display if any
            if (telemetryState.errorMessage != null && bleStatus == BleStatus.ERROR) {
                Text(
                    text = telemetryState.errorMessage,
                    style = MaterialTheme.typography.bodySmall.copy(color = ElectricRed)
                )
            }
        }
    }
}

@Composable
private fun BatteryTelemetryCard(
    batteryInfo: BatteryInfo
) {
    val pct = batteryInfo.percentage
    val mv = batteryInfo.voltageMv
    val volts = batteryInfo.voltageVolts

    val gaugeColor = when {
        pct > 50 -> ElectricGreen
        pct > 20 -> ElectricAmber
        else -> ElectricRed
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("battery_telemetry_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = borderStroke(1.dp, DarkSurfaceHighlight)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Live Battery Telemetry",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )

                // Charging badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (batteryInfo.isCharging) ElectricGreen.copy(alpha = 0.2f) else Color(0xFF1E293B)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (batteryInfo.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryStd,
                            contentDescription = "Charge status",
                            tint = if (batteryInfo.isCharging) ElectricGreen else Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = batteryInfo.chargeStatusText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (batteryInfo.isCharging) ElectricGreen else Color(0xFF94A3B8),
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }

            // Large Gauge & Readouts Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular Gauge
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(110.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.size(100.dp),
                        color = Color(0xFF1B273A),
                        strokeWidth = 10.dp
                    )
                    CircularProgressIndicator(
                        progress = { if (pct > 0) pct / 100f else 0f },
                        modifier = Modifier.size(100.dp),
                        color = gaugeColor,
                        strokeWidth = 10.dp
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (pct >= 0) "$pct%" else "--",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                fontSize = 24.sp
                            )
                        )
                        Text(
                            text = "SOC",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFF94A3B8),
                                fontSize = 10.sp
                            )
                        )
                    }
                }

                // Voltage and Temperature Metrics
                Column(
                    modifier = Modifier.weight(1f).padding(start = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Voltage Metric
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = DarkSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "BATTERY VOLTAGE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color(0xFF64748B),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp
                                )
                            )
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (mv > 0) "$mv" else "--",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        color = ElectricCyan,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                )
                                Text(
                                    text = "mV",
                                    style = MaterialTheme.typography.labelMedium.copy(color = ElectricCyan),
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                                Text(
                                    text = if (volts > 0f) "(${String.format("%.2f", volts)} V)" else "",
                                    style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF94A3B8)),
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                            }
                        }
                    }

                    // Temperature Metric
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = DarkSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Thermostat,
                                contentDescription = "Battery Temperature",
                                tint = ElectricAmber,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Temp: ${String.format("%.1f", batteryInfo.temperatureCelsius)} °C",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = Color(0xFFCBD5E1),
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceControlCard(
    isServiceRunning: Boolean,
    onToggleService: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("service_control_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = borderStroke(1.dp, if (isServiceRunning) ElectricGreen.copy(alpha = 0.5f) else DarkSurfaceHighlight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = "Power",
                        tint = if (isServiceRunning) ElectricGreen else Color(0xFF94A3B8),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Foreground BLE Service",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
                Text(
                    text = if (isServiceRunning) {
                        "Running 24/7 in background with persistent notification."
                    } else {
                        "Tap to start background monitoring and BLE transmission."
                    },
                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
                )
            }

            Switch(
                checked = isServiceRunning,
                onCheckedChange = onToggleService,
                modifier = Modifier.testTag("service_toggle"),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF090E17),
                    checkedTrackColor = ElectricGreen,
                    uncheckedThumbColor = Color(0xFF94A3B8),
                    uncheckedTrackColor = Color(0xFF1E293B)
                )
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IntervalSettingsCard(
    currentInterval: Int,
    onSelectInterval: (Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("interval_settings_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = borderStroke(1.dp, DarkSurfaceHighlight)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = "Schedule",
                    tint = ElectricCyan,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Broadcast Frequency",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
            }

            Text(
                text = "Data transmits every $currentInterval seconds or automatically whenever battery percentage changes by ±1%.",
                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(10, 30, 60).forEach { seconds ->
                    val isSelected = currentInterval == seconds
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectInterval(seconds) },
                        label = {
                            Text(
                                text = "${seconds}s ${if (seconds == 30) "(Default)" else ""}",
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.testTag("interval_${seconds}s_chip"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricCyan,
                            selectedLabelColor = Color(0xFF090E17),
                            containerColor = DarkSurfaceVariant,
                            labelColor = Color(0xFFCBD5E1)
                        ),
                        border = borderStroke(
                            1.dp,
                            if (isSelected) ElectricCyan else DarkSurfaceHighlight
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun TelemetryStreamCard(
    telemetryState: TelemetryState,
    onManualSend: () -> Unit,
    onClearLogs: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("telemetry_stream_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = borderStroke(1.dp, DarkSurfaceHighlight)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Telemetry Stream",
                        tint = ElectricGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Telemetry Packet Feed",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }

                if (telemetryState.transmissionLogs.isNotEmpty()) {
                    IconButton(onClick = onClearLogs) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear Logs",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Stats counters
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DarkSurfaceVariant,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "SENT PACKETS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFF64748B),
                                fontSize = 9.sp
                            )
                        )
                        Text(
                            text = "${telemetryState.packetsSentCount}",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = ElectricGreen,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DarkSurfaceVariant,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "FAILED / WAITING",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFF64748B),
                                fontSize = 9.sp
                            )
                        )
                        Text(
                            text = "${telemetryState.packetsFailedCount}",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = if (telemetryState.packetsFailedCount > 0) ElectricAmber else Color(0xFF94A3B8),
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            // Last Sent Payload Preview
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF090E17),
                border = borderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "LAST COMPACT JSON PAYLOAD",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFF64748B),
                            fontSize = 9.sp
                        )
                    )
                    Text(
                        text = telemetryState.lastSentPayload ?: "{\"pct\":0,\"mv\":0}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = ElectricCyan,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            // Test Send Button
            Button(
                onClick = onManualSend,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("send_test_packet_button"),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricGreen),
                enabled = telemetryState.isServiceRunning
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = null,
                    tint = Color(0xFF090E17),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Send Test BLE Packet Now",
                    color = Color(0xFF090E17),
                    fontWeight = FontWeight.Bold
                )
            }

            // Transmission Log List
            if (telemetryState.transmissionLogs.isNotEmpty()) {
                Text(
                    text = "Recent Activity (Last 30 Events)",
                    style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF64748B))
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    telemetryState.transmissionLogs.take(5).forEach { log ->
                        TransmissionLogRow(log = log)
                    }
                }
            }
        }
    }
}

@Composable
private fun TransmissionLogRow(log: TransmissionLog) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = DarkSurfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (log.success) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (log.success) ElectricGreen else ElectricAmber,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = log.payloadJson,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = Color.White,
                        fontSize = 11.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = log.timestampFormatted,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF94A3B8),
                    fontSize = 10.sp
                )
            )
        }
    }
}

private fun borderStroke(width: androidx.compose.ui.unit.Dp, color: Color) =
    androidx.compose.foundation.BorderStroke(width, color)
