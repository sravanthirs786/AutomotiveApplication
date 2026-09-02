package com.raziya.diagnostics.history

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.raziya.diagnostics.DiagnosticsState
import com.raziya.diagnostics.FrameLog
import com.raziya.diagnostics.VehicleProfile
import com.raziya.diagnostics.can.CanFrame
import com.raziya.diagnostics.can.LiveReading
import com.raziya.diagnostics.can.VehicleStatus

@Entity(tableName = "scan_records", indices = [Index("scannedAt")])
data class ScanRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scannedAt: Long,
    val vehicleName: String,
    val registrationNumber: String,
    val clientName: String,
    val clientPhone: String,
    val odometerKm: String,
    val vin: String?,
    val deviceName: String?,
    val dtcs: String,
    val rpm: Int?, val speedKph: Int?, val coolantC: Int?, val engineLoadPercent: Int?,
    val intakeTemperatureC: Int?, val controlModuleVoltage: Double?, val fuelLevelPercent: Int?,
    val throttlePercent: Int?, val intakeManifoldKpa: Int?, val massAirFlowGps: Double?,
    val ambientTemperatureC: Int?, val oilTemperatureC: Int?, val fuelRateLph: Double?,
    val bodyDataAvailable: Boolean, val tpmsDataAvailable: Boolean, val chassisDataAvailable: Boolean,
    val bodyFlags: Int,
    val tyrePressures: String,
    val tyreTemperatures: String,
    val wheelSpeeds: String,
    val selectedGear: String?,
    val transmissionTemperatureC: Int?,
    val frames: String,
)

fun DiagnosticsState.toScanRecord(): ScanRecord {
    val profile = requireNotNull(vehicleProfile)
    val bodyFlags = listOf(
        vehicleStatus.frontLeftDoorOpen, vehicleStatus.frontRightDoorOpen,
        vehicleStatus.rearLeftDoorOpen, vehicleStatus.rearRightDoorOpen,
        vehicleStatus.hoodOpen, vehicleStatus.tailgateOpen, vehicleStatus.locked,
        vehicleStatus.ignitionOn,
    ).foldIndexed(0) { index, flags, value -> if (value) flags or (1 shl index) else flags }
    return ScanRecord(
        scannedAt = scanCompletedAt ?: System.currentTimeMillis(),
        vehicleName = profile.vehicleName, registrationNumber = profile.registrationNumber,
        clientName = profile.clientName, clientPhone = profile.clientPhone, odometerKm = profile.odometerKm,
        vin = vin, deviceName = deviceName, dtcs = dtcs.joinToString(","),
        rpm = readings.rpm, speedKph = readings.speedKph, coolantC = readings.coolantC,
        engineLoadPercent = readings.engineLoadPercent, intakeTemperatureC = readings.intakeTemperatureC,
        controlModuleVoltage = readings.controlModuleVoltage, fuelLevelPercent = readings.fuelLevelPercent,
        throttlePercent = readings.throttlePercent, intakeManifoldKpa = readings.intakeManifoldKpa,
        massAirFlowGps = readings.massAirFlowGps, ambientTemperatureC = readings.ambientTemperatureC,
        oilTemperatureC = readings.oilTemperatureC, fuelRateLph = readings.fuelRateLph,
        bodyDataAvailable = vehicleStatus.bodyDataAvailable, tpmsDataAvailable = vehicleStatus.tpmsDataAvailable,
        chassisDataAvailable = vehicleStatus.chassisDataAvailable, bodyFlags = bodyFlags,
        tyrePressures = vehicleStatus.tyrePressureKpa.joinToString(",") { it?.toString().orEmpty() },
        tyreTemperatures = vehicleStatus.tyreTemperatureC.joinToString(",") { it?.toString().orEmpty() },
        wheelSpeeds = vehicleStatus.wheelSpeedKph.joinToString(",") { it?.toString().orEmpty() },
        selectedGear = vehicleStatus.selectedGear, transmissionTemperatureC = vehicleStatus.transmissionTemperatureC,
        frames = frames.reversed().joinToString("\n") { "${it.timestamp}|${it.direction}|${it.frame.id}|${it.frame.hex}" },
    )
}

fun ScanRecord.toDiagnosticsState(): DiagnosticsState {
    fun ints(value: String) = value.split(",").map { it.toIntOrNull() }.let { it + List((4 - it.size).coerceAtLeast(0)) { null } }.take(4)
    fun doubles(value: String) = value.split(",").map { it.toDoubleOrNull() }.let { it + List((4 - it.size).coerceAtLeast(0)) { null } }.take(4)
    fun bit(index: Int) = bodyFlags and (1 shl index) != 0
    val restoredFrames = frames.lineSequence().mapNotNull { line ->
        val parts = line.split("|", limit = 4)
        if (parts.size != 4) null else runCatching {
            FrameLog(parts[1], CanFrame(parts[2].toInt(), hexBytes(parts[3])), parts[0].toLong())
        }.getOrNull()
    }.toList()
    return DiagnosticsState(
        vehicleProfile = VehicleProfile(vehicleName, registrationNumber, clientName, clientPhone, odometerKm),
        deviceName = deviceName,
        readings = LiveReading(rpm, speedKph, coolantC, engineLoadPercent, intakeTemperatureC,
            controlModuleVoltage, fuelLevelPercent, throttlePercent, intakeManifoldKpa, massAirFlowGps,
            ambientTemperatureC, oilTemperatureC, fuelRateLph),
        vehicleStatus = VehicleStatus(bodyDataAvailable, tpmsDataAvailable, chassisDataAvailable,
            bit(0), bit(1), bit(2), bit(3), bit(4), bit(5), bit(6), bit(7),
            ints(tyrePressures), ints(tyreTemperatures), doubles(wheelSpeeds), selectedGear, transmissionTemperatureC),
        vin = vin, dtcs = dtcs.split(",").filter { it.isNotBlank() }, frames = restoredFrames,
        scanCompletedAt = scannedAt,
    )
}

private fun hexBytes(hex: String): ByteArray = hex.filterNot(Char::isWhitespace).chunked(2)
    .filter { it.length == 2 }.map { it.toInt(16).toByte() }.toByteArray()
