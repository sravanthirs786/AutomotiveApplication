package com.raziya.diagnostics.can

class IsoTpReassembler {
    private var expected = 0
    private var nextSequence = 1
    private val payload = mutableListOf<Byte>()

    sealed interface Result {
        data class Complete(val payload: ByteArray) : Result
        data class FlowControlRequired(val frame: CanFrame) : Result
        data object Pending : Result
        data class Invalid(val reason: String) : Result
    }

    fun accept(frame: CanFrame): Result {
        if (frame.data.isEmpty()) return Result.Invalid("Empty frame")
        return when ((frame.data[0].toInt() ushr 4) and 0x0F) {
            0 -> {
                val length = frame.data[0].toInt() and 0x0F
                if (length > frame.data.size - 1) Result.Invalid("Truncated single frame")
                else Result.Complete(frame.data.copyOfRange(1, length + 1))
            }
            1 -> {
                if (frame.data.size < 2) return Result.Invalid("Truncated first frame")
                expected = ((frame.data[0].toInt() and 0x0F) shl 8) or (frame.data[1].toInt() and 0xFF)
                payload.clear()
                payload.addAll(frame.data.drop(2))
                nextSequence = 1
                Result.FlowControlRequired(CanFrame.of(responseToRequestId(frame.id), 0x30, 0x00, 0x00))
            }
            2 -> {
                val sequence = frame.data[0].toInt() and 0x0F
                if (sequence != nextSequence) return reset("Expected CF $nextSequence, got $sequence")
                nextSequence = (nextSequence + 1) and 0x0F
                payload.addAll(frame.data.drop(1))
                if (payload.size >= expected) {
                    val result = payload.take(expected).toByteArray()
                    clear()
                    Result.Complete(result)
                } else Result.Pending
            }
            else -> Result.Invalid("Unsupported ISO-TP frame type")
        }
    }

    private fun responseToRequestId(id: Int) = if (id in 0x7E8..0x7EF) id - 8 else 0x7E0
    private fun reset(reason: String): Result.Invalid { clear(); return Result.Invalid(reason) }
    private fun clear() { expected = 0; nextSequence = 1; payload.clear() }
}
