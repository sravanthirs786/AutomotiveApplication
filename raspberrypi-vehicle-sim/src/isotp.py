#!/usr/bin/env python3
"""ISO 15765-2 classic-CAN segmentation for the simulated diagnostic ECUs."""
from __future__ import annotations

from dataclasses import dataclass, field
from canio import Frame, send


@dataclass
class Reassembler:
    expected: int = 0
    next_sequence: int = 1
    payload: bytearray = field(default_factory=bytearray)

    def accept(self, frame: Frame) -> tuple[bytes | None, Frame | None]:
        if not frame.data:
            return None, None
        kind = frame.data[0] >> 4
        if kind == 0:
            length = frame.data[0] & 0x0F
            return frame.data[1:1 + length], None
        if kind == 1:
            self.expected = ((frame.data[0] & 0x0F) << 8) | frame.data[1]
            self.payload = bytearray(frame.data[2:])
            self.next_sequence = 1
            return None, Frame(response_to_request(frame.can_id), b"\x30\x00\x00")
        if kind == 2 and (frame.data[0] & 0x0F) == self.next_sequence:
            self.next_sequence = (self.next_sequence + 1) & 0x0F
            self.payload.extend(frame.data[1:])
            if len(self.payload) >= self.expected:
                result = bytes(self.payload[:self.expected])
                self.reset()
                return result, None
        return None, None

    def reset(self) -> None:
        self.expected, self.next_sequence = 0, 1
        self.payload.clear()


def response_to_request(can_id: int) -> int:
    return can_id - 8 if 0x7E8 <= can_id <= 0x7EF else 0x7E0


def transmit(sock, can_id: int, payload: bytes) -> None:
    """Transmit a response. For the lab, consecutive frames use a conservative 5 ms gap."""
    import time
    if len(payload) <= 7:
        send(sock, Frame(can_id, bytes([len(payload)]) + payload))
        return
    send(sock, Frame(can_id, bytes([0x10 | (len(payload) >> 8), len(payload) & 0xFF]) + payload[:6]))
    sequence, offset = 1, 6
    # A standards-complete peer sends FC. The simulator also waits briefly so simple clients work.
    time.sleep(0.02)
    while offset < len(payload):
        chunk = payload[offset:offset + 7]
        send(sock, Frame(can_id, bytes([0x20 | (sequence & 0x0F)]) + chunk))
        sequence, offset = sequence + 1, offset + 7
        time.sleep(0.005)
