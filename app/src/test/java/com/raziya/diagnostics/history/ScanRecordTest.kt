package com.raziya.diagnostics.history

import com.raziya.diagnostics.DiagnosticsState
import com.raziya.diagnostics.FrameLog
import com.raziya.diagnostics.VehicleProfile
import com.raziya.diagnostics.can.CanFrame
import com.raziya.diagnostics.can.LiveReading
import com.raziya.diagnostics.can.VehicleStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanRecordTest {
    @Test fun completeScanRoundTripsThroughDatabaseRecord() {
        val original = DiagnosticsState(
            vehicleProfile = VehicleProfile("Kia Carens", "TS09AB1234", "Client", "9999999999", "18432"),
            deviceName = "Diagnostic adapter", readings = LiveReading(rpm = 1737, speedKph = 28, coolantC = 114,
                controlModuleVoltage = 10.9, fuelLevelPercent = 62),
            vehicleStatus = VehicleStatus(bodyDataAvailable = true, tpmsDataAvailable = true,
                chassisDataAvailable = true, frontLeftDoorOpen = true, locked = false,
                tyrePressureKpa = listOf(168, 232, 230, 229), selectedGear = "P", transmissionTemperatureC = 91),
            vin = "LABSIM26RPI400001", dtcs = listOf("P0300", "C0035"),
            frames = listOf(FrameLog("RX", CanFrame.of(0x7E8, 0x03, 0x41, 0x0D, 0x1C), 1234)),
            scanCompletedAt = 5678,
        )

        val restored = original.toScanRecord().copy(id = 7).toDiagnosticsState()

        assertEquals(original.vehicleProfile, restored.vehicleProfile)
        assertEquals(original.readings.rpm, restored.readings.rpm)
        assertEquals(original.vehicleStatus.tyrePressureKpa, restored.vehicleStatus.tyrePressureKpa)
        assertEquals(original.dtcs, restored.dtcs)
        assertEquals(original.frames.single().frame.hex, restored.frames.single().frame.hex)
        assertTrue(restored.vehicleStatus.frontLeftDoorOpen)
    }
}
