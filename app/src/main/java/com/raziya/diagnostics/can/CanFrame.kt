package com.raziya.diagnostics.can

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Linux SocketCAN can_frame: can_id(4 LE), len(1), pad(3), data(8). */
data class CanFrame(val id: Int, val data: ByteArray) {
    init {
        require(id in 0..0x1FFFFFFF) { "Invalid CAN identifier" }
        require(data.size <= 8) { "Classic CAN supports at most 8 data bytes" }
    }

    val dlc: Int get() = data.size
    val hex: String get() = data.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

    fun encode(): ByteArray = ByteBuffer.allocate(WIRE_SIZE)
        .order(ByteOrder.LITTLE_ENDIAN)
        .putInt(id)
        .put(dlc.toByte())
        .put(byteArrayOf(0, 0, 0))
        .put(data.copyOf(8))
        .array()

    companion object {
        const val WIRE_SIZE = 16

        fun decode(bytes: ByteArray): CanFrame {
            require(bytes.size == WIRE_SIZE)
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val id = buffer.int and 0x1FFFFFFF
            val len = buffer.get().toInt() and 0x0F
            require(len <= 8) { "Invalid DLC $len" }
            buffer.position(8)
            return CanFrame(id, ByteArray(8).also(buffer::get).copyOf(len))
        }

        fun of(id: Int, vararg bytes: Int) = CanFrame(id, bytes.map(Int::toByte).toByteArray())
    }
}
