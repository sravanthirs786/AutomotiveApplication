package com.raziya.diagnostics.can

data class LiveReading(
    val rpm: Int? = null,
    val speedKph: Int? = null,
    val coolantC: Int? = null,
    val engineLoadPercent: Int? = null,
    val intakeTemperatureC: Int? = null,
    val controlModuleVoltage: Double? = null,
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
