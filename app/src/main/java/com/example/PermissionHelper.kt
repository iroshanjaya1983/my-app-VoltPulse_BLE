package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

object PermissionHelper {

    /**
     * Returns the array of runtime permissions required for BLE scanning, GATT connection,
     * and foreground notifications depending on the Android OS version.
     *
     * - Android 13+ (API 33+): BLUETOOTH_SCAN, BLUETOOTH_CONNECT, POST_NOTIFICATIONS
     * - Android 12 to 12L (API 31-32): BLUETOOTH_SCAN, BLUETOOTH_CONNECT
     * - Pre-Android 12 (API 21-30): ACCESS_FINE_LOCATION (runtime requirement for BLE scans)
     */
    fun getRequiredPermissions(): Array<String> {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ (API 31+) uses fine-grained BLE runtime permissions
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            // Legacy Android (API 21-30) requires Location permission to discover BLE beacons/peripherals
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ requires explicit runtime permission for ongoing foreground service notifications
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        return permissions.toTypedArray()
    }

    /**
     * Checks if all required runtime permissions are currently granted.
     */
    fun hasAllPermissions(context: Context): Boolean {
        return getRequiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Returns a list of permissions that have not yet been granted by the user.
     */
    fun getMissingPermissions(context: Context): List<String> {
        return getRequiredPermissions().filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Checks whether the app is currently whitelisted from system battery optimizations (Doze mode).
     * This prevents OEM killers (Samsung, Xiaomi MIUI, Huawei, etc.) from abruptly killing the BLE service.
     */
    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
        }
        return true
    }

    /**
     * Creates an Intent to prompt the user to ignore battery optimizations for this app.
     */
    fun createBatteryOptimizationIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } else {
            Intent(Settings.ACTION_SETTINGS)
        }
    }
}
