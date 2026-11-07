package com.example

import java.util.UUID

/**
 * UUIDs specified for ESP32 BLE GATT Server communication.
 */
val ESP32_SERVICE_UUID: UUID = UUID.fromString("19B10000-E8F2-537E-4F6C-D104768A1214")
val ESP32_CHARACTERISTIC_UUID: UUID = UUID.fromString("19B10001-E8F2-537E-4F6C-D104768A1214")

enum class BleStatus {
    STOPPED,
    SCANNING,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    ERROR
}

data class BatteryInfo(
    val percentage: Int = 0,
    val voltageMv: Int = 0,
    val isCharging: Boolean = false,
    val chargeStatusText: String = "Unknown",
    val temperatureCelsius: Float = 0f,
    val updatedAtMs: Long = 0L
) {
    val voltageVolts: Float
        get() = if (voltageMv > 0) voltageMv / 1000f else 0f
}

data class TransmissionLog(
    val id: Long = System.currentTimeMillis(),
    val timestampFormatted: String,
    val payloadJson: String,
    val success: Boolean,
    val details: String
)

data class TelemetryState(
    val isServiceRunning: Boolean = false,
    val bleStatus: BleStatus = BleStatus.STOPPED,
    val connectedDeviceName: String? = null,
    val connectedDeviceAddress: String? = null,
    val batteryInfo: BatteryInfo = BatteryInfo(),
    val updateIntervalSeconds: Int = 30, // 10s, 30s, 60s
    val lastSentPayload: String? = null,
    val lastSentTimestampMs: Long = 0L,
    val packetsSentCount: Int = 0,
    val packetsFailedCount: Int = 0,
    val transmissionLogs: List<TransmissionLog> = emptyList(),
    val errorMessage: String? = null,
    val autoReconnectAttempt: Int = 0
)
