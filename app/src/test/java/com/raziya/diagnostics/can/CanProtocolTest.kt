package com.raziya.diagnostics.can

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class CanProtocolTest {
    @Test fun socketCanRoundTrip() {
        val frame = CanFrame.of(0x7DF, 0x02, 0x01, 0x0C)
        assertEquals(16, frame.encode().size)
        assertEquals(frame.id, CanFrame.decode(frame.encode()).id)
        assertArrayEquals(frame.data, CanFrame.decode(frame.encode()).data)
    }

    @Test fun rpmDecodes() {
        val reading = ObdDecoder.update(byteArrayOf(0x41, 0x0C, 0x2C, 0x32), LiveReading())
        assertEquals(2828, reading.rpm)
    }

    @Test fun multiFrameVinReassembles() {
        val iso = IsoTpReassembler()
        iso.accept(CanFrame.of(0x7E8, 0x10, 0x14, 0x62, 0xF1, 0x90, 0x4C, 0x41, 0x42))
        iso.accept(CanFrame.of(0x7E8, 0x21, 0x53, 0x49, 0x4D, 0x32, 0x36, 0x52, 0x50))
        val result = iso.accept(CanFrame.of(0x7E8, 0x22, 0x49, 0x34, 0x30, 0x30, 0x30, 0x30, 0x31))
        val payload = (result as IsoTpReassembler.Result.Complete).payload
        assertEquals("LABSIM26RPI400001", ObdDecoder.decodeVin(payload))
    }

    @Test fun multiSystemFaultsDecode() {
        val payload = bytes("43 03 00 01 17 05 62 04 20 40 35 C1 00 C1 01")
        assertEquals(
            listOf("P0300", "P0117", "P0562", "P0420", "C0035", "U0100", "U0101"),
            ObdDecoder.decodeObdDtcs(payload),
        )
    }

    private fun bytes(hex: String) = hex.split(" ").map { it.toInt(16).toByte() }.toByteArray()
}
