package com.raziya.diagnostics.can

data class LiveReading(
    val rpm: Int? = null,
    val speedKph: Int? = null,
    val coolantC: Int? = null,
    val engineLoadPercent: Int? = null,
    val intakeTemperatureC: Int? = null,
    val controlModuleVoltage: Double? = null,
    val fuelLevelPercent: Int? = null,
    val throttlePercent: Int? = null,
    val intakeManifoldKpa: Int? = null,
    val massAirFlowGps: Double? = null,
    val ambientTemperatureC: Int? = null,
    val oilTemperatureC: Int? = null,
    val fuelRateLph: Double? = null,
)

data class VehicleStatus(
    val bodyDataAvailable: Boolean = false,
    val tpmsDataAvailable: Boolean = false,
    val chassisDataAvailable: Boolean = false,
    val frontLeftDoorOpen: Boolean = false,
    val frontRightDoorOpen: Boolean = false,
    val rearLeftDoorOpen: Boolean = false,
    val rearRightDoorOpen: Boolean = false,
    val hoodOpen: Boolean = false,
    val tailgateOpen: Boolean = false,
    val locked: Boolean = true,
    val ignitionOn: Boolean = true,
    val tyrePressureKpa: List<Int?> = List(4) { null },
    val tyreTemperatureC: List<Int?> = List(4) { null },
    val wheelSpeedKph: List<Double?> = List(4) { null },
    val selectedGear: String? = null,
    val transmissionTemperatureC: Int? = null,
)

object ObdDecoder {
    fun update(payload: ByteArray, current: LiveReading): LiveReading {
        if (payload.size < 3 || payload[0].u() != 0x41) return current
        return when (payload[1].u()) {
            0x0C -> if (payload.size >= 4) current.copy(rpm = ((payload[2].u() shl 8) + payload[3].u()) / 4) else current
            0x0D -> current.copy(speedKph = payload[2].u())
            0x05 -> current.copy(coolantC = payload[2].u() - 40)
            0x04 -> current.copy(engineLoadPercent = payload[2].u() * 100 / 255)
            0x0F -> current.copy(intakeTemperatureC = payload[2].u() - 40)
            0x42 -> if (payload.size >= 4) current.copy(
                controlModuleVoltage = ((payload[2].u() shl 8) + payload[3].u()) / 1000.0
            ) else current
            0x0B -> current.copy(intakeManifoldKpa = payload[2].u())
            0x10 -> if (payload.size >= 4) current.copy(massAirFlowGps = ((payload[2].u() shl 8) + payload[3].u()) / 100.0) else current
            0x11 -> current.copy(throttlePercent = payload[2].u() * 100 / 255)
            0x2F -> current.copy(fuelLevelPercent = payload[2].u() * 100 / 255)
            0x46 -> current.copy(ambientTemperatureC = payload[2].u() - 40)
            0x5C -> current.copy(oilTemperatureC = payload[2].u() - 40)
            0x5E -> if (payload.size >= 4) current.copy(fuelRateLph = ((payload[2].u() shl 8) + payload[3].u()) * 0.05) else current
            else -> current
        }
    }

    fun updateVehicleStatus(payload: ByteArray, current: VehicleStatus): VehicleStatus {
        if (payload.size < 4 || payload[0].u() != 0x62) return current
        return when ((payload[1].u() shl 8) or payload[2].u()) {
            0xD100 -> {
                val flags = payload[3].u()
                current.copy(
                    bodyDataAvailable = true,
                    frontLeftDoorOpen = flags and 0x01 != 0,
                    frontRightDoorOpen = flags and 0x02 != 0,
                    rearLeftDoorOpen = flags and 0x04 != 0,
                    rearRightDoorOpen = flags and 0x08 != 0,
                    hoodOpen = flags and 0x10 != 0,
                    tailgateOpen = flags and 0x20 != 0,
                    locked = flags and 0x40 != 0,
                    ignitionOn = flags and 0x80 != 0,
                )
            }
            0xD200 -> if (payload.size >= 11) current.copy(
                tpmsDataAvailable = true,
                tyrePressureKpa = (0 until 4).map { i -> ((payload[3 + i * 2].u() shl 8) or payload[4 + i * 2].u()) }
            ) else current
            0xD201 -> if (payload.size >= 7) current.copy(
                tpmsDataAvailable = true,
                tyreTemperatureC = (0 until 4).map { i -> payload[3 + i].u() - 40 }
            ) else current
            0xD300 -> if (payload.size >= 11) current.copy(
                chassisDataAvailable = true,
                wheelSpeedKph = (0 until 4).map { i -> ((payload[3 + i * 2].u() shl 8) or payload[4 + i * 2].u()) / 100.0 }
            ) else current
            0xD400 -> if (payload.size >= 5) current.copy(
                chassisDataAvailable = true,
                selectedGear = listOf("P", "R", "N", "D", "S").getOrNull(payload[3].u()) ?: "?",
                transmissionTemperatureC = payload[4].u() - 40,
            ) else current
            else -> current
        }
    }

    fun decodeVin(payload: ByteArray): String? {
        val start = when {
            payload.size >= 3 && payload[0].u() == 0x62 && payload[1].u() == 0xF1 && payload[2].u() == 0x90 -> 3
            payload.size >= 3 && payload[0].u() == 0x49 && payload[1].u() == 0x02 -> 3
            else -> return null
        }
        return payload.copyOfRange(start, payload.size).toString(Charsets.US_ASCII).trim('\u0000').takeIf { it.length == 17 }
    }

    fun decodeObdDtcs(payload: ByteArray): List<String> {
        if (payload.isEmpty() || payload[0].u() != 0x43) return emptyList()
        return payload.drop(1).chunked(2).mapNotNull { pair ->
            if (pair.size < 2) null else decodeDtc(pair[0].u(), pair[1].u()).takeUnless { it == "P0000" }
        }
    }

    private fun decodeDtc(a: Int, b: Int): String {
        val family = "PCBU"[(a ushr 6) and 3]
        return "$family${(a ushr 4) and 3}${a and 0xF}${(b ushr 4) and 0xF}${b and 0xF}"
    }

    private fun Byte.u() = toInt() and 0xFF
}
