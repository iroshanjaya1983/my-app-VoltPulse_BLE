package com.example

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets
import kotlin.math.abs
import kotlin.math.min

class BatteryBleService : Service() {

    companion object {
        private const val TAG = "BatteryBleService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "battery_ble_service_channel"

        const val ACTION_START = "com.example.action.START"
        const val ACTION_STOP = "com.example.action.STOP"
        const val ACTION_SET_INTERVAL = "com.example.action.SET_INTERVAL"
        const val ACTION_SEND_NOW = "com.example.action.SEND_NOW"
        const val ACTION_RECONNECT = "com.example.action.RECONNECT"
        const val EXTRA_INTERVAL_SECONDS = "extra_interval_seconds"

        fun startService(context: Context) {
            val intent = Intent(context, BatteryBleService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, BatteryBleService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun setInterval(context: Context, intervalSeconds: Int) {
            val intent = Intent(context, BatteryBleService::class.java).apply {
                action = ACTION_SET_INTERVAL
                putExtra(EXTRA_INTERVAL_SECONDS, intervalSeconds)
            }
            context.startService(intent)
        }

        fun triggerManualSend(context: Context) {
            val intent = Intent(context, BatteryBleService::class.java).apply {
                action = ACTION_SEND_NOW
            }
            context.startService(intent)
        }

        fun reconnect(context: Context) {
            val intent = Intent(context, BatteryBleService::class.java).apply {
                action = ACTION_RECONNECT
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var periodicBroadcastJob: Job? = null
    private var reconnectJob: Job? = null

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var targetCharacteristic: BluetoothGattCharacteristic? = null
    private var isScanning = false

    private var currentBatteryInfo = BatteryInfo()
    private var lastSentPercentage: Int = -1
    private var lastSentTimestampMs: Long = 0L
    private var currentIntervalSeconds = 30
    private var reconnectBackoffSeconds = 2

    // BroadcastReceiver for native Android real-time battery stats
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                handleBatteryChanged(intent)
            }
        }
    }

    // BLE Scanner Callback
    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            val device = result?.device ?: return

            // Device matching: Check for explicit ESP32 service UUID or ESP32 device name
            val record = result.scanRecord
            val hasService = record?.serviceUuids?.any { it.uuid == ESP32_SERVICE_UUID } == true
            val name = try { device.name } catch (e: Exception) { null } ?: record?.deviceName
            val isEspCandidate = name?.contains("ESP32", ignoreCase = true) == true ||
                    name?.contains("Battery", ignoreCase = true) == true

            if (hasService || isEspCandidate) {
                Log.d(TAG, "Found target ESP32 peripheral: ${device.address} ($name)")
                stopBleScan()
                connectToDevice(device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e(TAG, "BLE Scan failed with errorCode: $errorCode")
            isScanning = false
            BatteryBleStateHolder.updateBleStatus(
                BleStatus.ERROR,
                error = "Scan failed (Error $errorCode). Ensure Bluetooth and Location are on."
            )
            scheduleAutoReconnect()
        }
    }

    // BLE GATT Callback
    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            val address = gatt?.device?.address
            val name = try { gatt?.device?.name } catch (e: Exception) { null } ?: "ESP32"

            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "GATT connected to $name ($address). Discovering services...")
                reconnectBackoffSeconds = 2 // reset backoff
                reconnectJob?.cancel()
                BatteryBleStateHolder.updateBleStatus(
                    BleStatus.CONNECTING,
                    deviceName = name,
                    deviceAddress = address
                )
                updateNotification()
                gatt?.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.w(TAG, "GATT disconnected from $name. Status: $status")
                targetCharacteristic = null
                try {
                    bluetoothGatt?.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing GATT", e)
                }
                bluetoothGatt = null

                BatteryBleStateHolder.updateBleStatus(
                    BleStatus.DISCONNECTED,
                    deviceName = name,
                    deviceAddress = address,
                    reconnectAttempt = reconnectBackoffSeconds
                )
                updateNotification()
                scheduleAutoReconnect()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            if (status == BluetoothGatt.GATT_SUCCESS && gatt != null) {
                Log.d(TAG, "GATT Services discovered")
                val service = gatt.getService(ESP32_SERVICE_UUID)
                val characteristic = service?.getCharacteristic(ESP32_CHARACTERISTIC_UUID)

                if (characteristic != null) {
                    targetCharacteristic = characteristic
                    val name = try { gatt.device.name } catch (e: Exception) { null } ?: "ESP32"
                    BatteryBleStateHolder.updateBleStatus(
                        BleStatus.CONNECTED,
                        deviceName = name,
                        deviceAddress = gatt.device.address
                    )
                    updateNotification()
                    Log.i(TAG, "ESP32 characteristic ready: $ESP32_CHARACTERISTIC_UUID")

                    // Transmit immediately upon connection
                    serviceScope.launch {
                        delay(300)
                        transmitCurrentBatteryData("Connected initial telemetry")
                    }
                } else {
                    Log.w(TAG, "Target service/characteristic not found on peripheral!")
                    BatteryBleStateHolder.updateBleStatus(
                        BleStatus.ERROR,
                        error = "Service $ESP32_SERVICE_UUID not found on connected peripheral"
                    )
                }
            } else {
                Log.e(TAG, "Service discovery failed with status $status")
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            val success = status == BluetoothGatt.GATT_SUCCESS
            val payload = characteristic?.value?.let { String(it, StandardCharsets.UTF_8) } ?: ""
            Log.d(TAG, "Characteristic write result: $status, success=$success, payload=$payload")
            BatteryBleStateHolder.recordTransmission(
                payload = payload,
                success = success,
                details = if (success) "Sent successfully to ESP32" else "Write failed (Status $status)"
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "BatteryBleService onCreate")
        createNotificationChannel()

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter

        // Register real-time battery status receiver
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, filter)

        // Query initial battery stats immediately
        val initialBatteryIntent = registerReceiver(null, filter)
        initialBatteryIntent?.let { handleBatteryChanged(it) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        Log.d(TAG, "onStartCommand action: $action")

        when (action) {
            ACTION_STOP -> {
                stopSelfService()
                return START_NOT_STICKY
            }
            ACTION_SET_INTERVAL -> {
                val newInterval = intent?.getIntExtra(EXTRA_INTERVAL_SECONDS, 30) ?: 30
                currentIntervalSeconds = newInterval
                BatteryBleStateHolder.updateInterval(newInterval)
                restartPeriodicLoop()
            }
            ACTION_SEND_NOW -> {
                serviceScope.launch {
                    transmitCurrentBatteryData("Manual user test packet")
                }
            }
            ACTION_RECONNECT -> {
                reconnectBackoffSeconds = 2
                startBleScan()
            }
            ACTION_START -> {
                startForegroundCompat()
                BatteryBleStateHolder.updateServiceRunning(true)
                restartPeriodicLoop()
                startBleScan()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "BatteryBleService onDestroy")
        stopPeriodicLoop()
        reconnectJob?.cancel()
        serviceScope.cancel()

        try {
            unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering battery receiver", e)
        }

        stopBleScan()
        disconnectGatt()

        BatteryBleStateHolder.updateServiceRunning(false)
        super.onDestroy()
    }

    /**
     * Start foreground service with backward and forward compatibility.
     * Uses ServiceCompat to handle Android 14+ (API 34+) connectedDevice type properly.
     */
    private fun startForegroundCompat() {
        val notification = buildNotification()
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            0
        }

        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                serviceType
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service: ${e.message}", e)
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopSelfService() {
        Log.d(TAG, "Stopping BatteryBleService")
        stopForegroundCompat()
        stopSelf()
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    /**
     * Extracts raw battery statistics from native Intent.ACTION_BATTERY_CHANGED.
     * Triggers BLE transmission if percentage changed by ±1%.
     */
    private fun handleBatteryChanged(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percentage = if (scale > 0 && level >= 0) (level * 100) / scale else level
        val voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        val chargeStatusText = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
            else -> "Plugged"
        }
        val tempRaw = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        val temperatureCelsius = tempRaw / 10f

        currentBatteryInfo = BatteryInfo(
            percentage = percentage,
            voltageMv = voltageMv,
            isCharging = isCharging,
            chargeStatusText = chargeStatusText,
            temperatureCelsius = temperatureCelsius,
            updatedAtMs = System.currentTimeMillis()
        )

        BatteryBleStateHolder.updateBatteryInfo(currentBatteryInfo)
        updateNotification()

        // Check delta: transmit when battery percentage changes by ±1%
        if (lastSentPercentage >= 0 && abs(percentage - lastSentPercentage) >= 1) {
            Log.d(TAG, "Battery delta detected ($lastSentPercentage% -> $percentage%). Transmitting...")
            serviceScope.launch {
                transmitCurrentBatteryData("Battery changed ±1%")
            }
        }
    }

    /**
     * Periodic transmission loop running at user-configured interval (10s, 30s, 60s).
     * Power-optimized: does NOT acquire WakeLocks.
     */
    private fun restartPeriodicLoop() {
        stopPeriodicLoop()
        periodicBroadcastJob = serviceScope.launch {
            while (isActive) {
                delay(currentIntervalSeconds * 1000L)
                val now = System.currentTimeMillis()
                // Only send if not just sent via delta trigger
                if (now - lastSentTimestampMs >= (currentIntervalSeconds - 2) * 1000L) {
                    transmitCurrentBatteryData("Periodic interval (${currentIntervalSeconds}s)")
                }
            }
        }
    }

    private fun stopPeriodicLoop() {
        periodicBroadcastJob?.cancel()
        periodicBroadcastJob = null
    }

    /**
     * Starts BLE central scan for the ESP32 peripheral.
     */
    @SuppressLint("MissingPermission")
    private fun startBleScan() {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            Log.w(TAG, "Bluetooth adapter unavailable or disabled")
            BatteryBleStateHolder.updateBleStatus(
                BleStatus.ERROR,
                error = "Bluetooth is turned off. Please enable Bluetooth."
            )
            return
        }

        if (!PermissionHelper.hasAllPermissions(this)) {
            Log.w(TAG, "Missing BLE runtime permissions")
            BatteryBleStateHolder.updateBleStatus(
                BleStatus.ERROR,
                error = "Missing Bluetooth/Location permissions."
            )
            return
        }

        if (isScanning) return

        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            Log.e(TAG, "BluetoothLeScanner is null")
            return
        }

        val filters = listOf(
            ScanFilter.Builder().setServiceUuid(ParcelUuid(ESP32_SERVICE_UUID)).build()
        )

        // API 21+ ScanSettings with low power mode to conserve battery
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .build()

        try {
            Log.d(TAG, "Starting BLE scan for ESP32...")
            // Scan with and without filter for maximum compatibility
            scanner.startScan(filters, settings, scanCallback)
            isScanning = true
            BatteryBleStateHolder.updateBleStatus(BleStatus.SCANNING)
            updateNotification()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting BLE scan", e)
            try {
                // Fallback: start scan without filter in case ESP32 manufacturer data overflows
                scanner.startScan(scanCallback)
                isScanning = true
                BatteryBleStateHolder.updateBleStatus(BleStatus.SCANNING)
            } catch (fallbackEx: Exception) {
                Log.e(TAG, "Fallback scan failed", fallbackEx)
                BatteryBleStateHolder.updateBleStatus(
                    BleStatus.ERROR,
                    error = "Failed to start BLE scanner: ${e.message}"
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopBleScan() {
        if (!isScanning) return
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        try {
            scanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping scan", e)
        }
        isScanning = false
    }

    /**
     * Connects to the discovered BluetoothDevice over GATT.
     */
    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        disconnectGatt()

        val name = try { device.name } catch (e: Exception) { null } ?: "ESP32"
        BatteryBleStateHolder.updateBleStatus(
            BleStatus.CONNECTING,
            deviceName = name,
            deviceAddress = device.address
        )
        updateNotification()

        @Suppress("DEPRECATION")
        bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(this, false, gattCallback)
        }
    }

    @SuppressLint("MissingPermission")
    private fun disconnectGatt() {
        targetCharacteristic = null
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting GATT", e)
        }
        bluetoothGatt = null
    }

    /**
     * Exponential backoff auto-reconnect loop if connection drops in the background.
     */
    private fun scheduleAutoReconnect() {
        reconnectJob?.cancel()
        reconnectJob = serviceScope.launch {
            val waitSec = reconnectBackoffSeconds
            Log.d(TAG, "Scheduling auto-reconnect in ${waitSec}s...")
            delay(waitSec * 1000L)

            // Double the backoff up to max 30 seconds
            reconnectBackoffSeconds = min(reconnectBackoffSeconds * 2, 30)
            startBleScan()
        }
    }

    /**
     * Serializes telemetry into compact JSON and writes to the ESP32 BLE characteristic.
     * Payload format: {"pct":78,"mv":4120}
     */
    @SuppressLint("MissingPermission")
    private fun transmitCurrentBatteryData(reason: String) {
        val pct = currentBatteryInfo.percentage
        val mv = currentBatteryInfo.voltageMv

        if (pct < 0) {
            Log.w(TAG, "Skipping transmission: invalid battery stats ($pct%)")
            return
        }

        // Compact JSON payload per technical specification
        val payloadJson = "{\"pct\":$pct,\"mv\":$mv}"
        val payloadBytes = payloadJson.toByteArray(StandardCharsets.UTF_8)

        val gatt = bluetoothGatt
        val char = targetCharacteristic

        if (gatt == null || char == null) {
            Log.d(TAG, "Cannot transmit: GATT or characteristic not ready ($reason)")
            BatteryBleStateHolder.recordTransmission(
                payload = payloadJson,
                success = false,
                details = "Waiting for ESP32 connection ($reason)"
            )
            return
        }

        try {
            @Suppress("DEPRECATION")
            val writeSuccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val status = gatt.writeCharacteristic(
                    char,
                    payloadBytes,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                )
                status == BluetoothStatusCodes.SUCCESS
            } else {
                char.value = payloadBytes
                char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                gatt.writeCharacteristic(char)
            }

            if (writeSuccess) {
                lastSentPercentage = pct
                lastSentTimestampMs = System.currentTimeMillis()
                Log.i(TAG, "Queued write to ESP32: $payloadJson ($reason)")
            } else {
                Log.w(TAG, "GATT writeCharacteristic returned false")
                BatteryBleStateHolder.recordTransmission(
                    payload = payloadJson,
                    success = false,
                    details = "GATT write declined"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during characteristic write", e)
            BatteryBleStateHolder.recordTransmission(
                payload = payloadJson,
                success = false,
                details = "Error: ${e.message}"
            )
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.service_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.service_notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingLaunchIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, BatteryBleService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStopIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val statusString = when (BatteryBleStateHolder.telemetryState.value.bleStatus) {
            BleStatus.CONNECTED -> "ESP32 Connected"
            BleStatus.CONNECTING -> "Connecting…"
            BleStatus.SCANNING -> "Scanning for ESP32…"
            BleStatus.DISCONNECTED -> "Retrying in ${reconnectBackoffSeconds}s"
            BleStatus.ERROR -> "Connection Error"
            BleStatus.STOPPED -> "Stopped"
        }

        val pct = currentBatteryInfo.percentage
        val mv = currentBatteryInfo.voltageMv
        val contentText = if (pct >= 0) {
            "Battery: $pct% ($mv mV) • $statusString"
        } else {
            "Monitoring battery telemetry • $statusString"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(pendingLaunchIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop Service",
                pendingStopIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification() {
        val notification = buildNotification()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, notification)
    }
}
