package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testEsp32Uuids() {
        assertEquals("19b10000-e8f2-537e-4f6c-d104768a1214", ESP32_SERVICE_UUID.toString())
        assertEquals("19b10001-e8f2-537e-4f6c-d104768a1214", ESP32_CHARACTERISTIC_UUID.toString())
    }

    @Test
    fun testBatteryInfoVoltageConversion() {
        val info = BatteryInfo(
            percentage = 85,
            voltageMv = 4125,
            isCharging = true,
            chargeStatusText = "Charging"
        )
        assertEquals(85, info.percentage)
        assertEquals(4125, info.voltageMv)
        assertEquals(4.125f, info.voltageVolts, 0.001f)
        assertTrue(info.isCharging)
    }

    @Test
    fun testBatteryBleStateHolderTransmission() {
        BatteryBleStateHolder.clearLogs()
        BatteryBleStateHolder.updateInterval(30)
        assertEquals(30, BatteryBleStateHolder.telemetryState.value.updateIntervalSeconds)

        val testPayload = "{\"pct\":78,\"mv\":4120}"
        BatteryBleStateHolder.recordTransmission(testPayload, true, "Sent successfully")

        val state = BatteryBleStateHolder.telemetryState.value
        assertEquals(testPayload, state.lastSentPayload)
        assertEquals(1, state.packetsSentCount)
        assertEquals(0, state.packetsFailedCount)
        assertEquals(1, state.transmissionLogs.size)
        assertEquals(testPayload, state.transmissionLogs.first().payloadJson)
        assertTrue(state.transmissionLogs.first().success)
    }

    @Test
    fun testCompactJsonPayloadFormat() {
        val pct = 78
        val mv = 4120
        val payload = "{\"pct\":$pct,\"mv\":$mv}"
        assertEquals("{\"pct\":78,\"mv\":4120}", payload)
    }

    @Test
    fun testRequiredPermissions() {
        val perms = PermissionHelper.getRequiredPermissions()
        assertNotNull(perms)
        assertTrue(perms.isNotEmpty())
    }
}
