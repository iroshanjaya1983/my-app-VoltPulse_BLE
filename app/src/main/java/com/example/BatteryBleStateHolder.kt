package com.example

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shared state manager bridging the background BatteryBleService and Compose UI.
 */
object BatteryBleStateHolder {

    private val _telemetryState = MutableStateFlow(TelemetryState())
    val telemetryState: StateFlow<TelemetryState> = _telemetryState.asStateFlow()

    private val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun updateServiceRunning(isRunning: Boolean) {
        _telemetryState.update { current ->
            current.copy(
                isServiceRunning = isRunning,
                bleStatus = if (!isRunning) BleStatus.STOPPED else current.bleStatus
            )
        }
    }

    fun updateBleStatus(
        status: BleStatus,
        deviceName: String? = null,
        deviceAddress: String? = null,
        error: String? = null,
        reconnectAttempt: Int = 0
    ) {
        _telemetryState.update { current ->
            current.copy(
                bleStatus = status,
                connectedDeviceName = deviceName ?: current.connectedDeviceName,
                connectedDeviceAddress = deviceAddress ?: current.connectedDeviceAddress,
                errorMessage = error,
                autoReconnectAttempt = reconnectAttempt
            )
        }
    }

    fun updateBatteryInfo(info: BatteryInfo) {
        _telemetryState.update { current ->
            current.copy(batteryInfo = info)
        }
    }

    fun updateInterval(seconds: Int) {
        _telemetryState.update { current ->
            current.copy(updateIntervalSeconds = seconds)
        }
    }

    fun recordTransmission(payload: String, success: Boolean, details: String) {
        val now = System.currentTimeMillis()
        val timeString = timeFormatter.format(Date(now))
        val log = TransmissionLog(
            id = now,
            timestampFormatted = timeString,
            payloadJson = payload,
            success = success,
            details = details
        )

        _telemetryState.update { current ->
            val updatedLogs = (listOf(log) + current.transmissionLogs).take(30)
            current.copy(
                lastSentPayload = payload,
                lastSentTimestampMs = now,
                packetsSentCount = if (success) current.packetsSentCount + 1 else current.packetsSentCount,
                packetsFailedCount = if (!success) current.packetsFailedCount + 1 else current.packetsFailedCount,
                transmissionLogs = updatedLogs
            )
        }
    }

    fun clearLogs() {
        _telemetryState.update { current ->
            current.copy(transmissionLogs = emptyList())
        }
    }
}
