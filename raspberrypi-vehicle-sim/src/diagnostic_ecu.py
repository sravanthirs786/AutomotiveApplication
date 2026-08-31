#!/usr/bin/env python3
"""Standards-shaped OBD-II and UDS diagnostic ECUs on 11-bit ISO-TP addresses."""
from __future__ import annotations

import select
import time
from canio import Frame, open_can, receive, send
from isotp import Reassembler, transmit

VIN = b"LABSIM26RPI400001"
DTCS = bytes.fromhex("0300002FC100002F")  # P0300 and U0100, status 0x2F
ECUS = {0x7E0: (0x7E8, "ECM"), 0x7E1: (0x7E9, "TCM"), 0x7E2: (0x7EA, "ABS")}


def live_values() -> tuple[int, int, int, int]:
    t = time.monotonic()
    speed = max(0, int(58 + 25 * __import__("math").sin(t / 8)))
    rpm = max(750, int(850 + speed * 31))
    return rpm, speed, 92, 42


def obd(payload: bytes) -> bytes | None:
    if not payload:
        return None
    rpm, speed, coolant, load = live_values()
    service = payload[0]
    if service == 0x01 and len(payload) >= 2:
        pid = payload[1]
        values = {
            0x00: bytes.fromhex("983B8011"),
            0x04: bytes([load * 255 // 100]),
            0x05: bytes([coolant + 40]),
            0x0C: (rpm * 4).to_bytes(2, "big"),
            0x0D: bytes([speed]),
        }
        return bytes([0x41, pid]) + values[pid] if pid in values else bytes([0x7F, 0x01, 0x12])
    if service == 0x03:
        return bytes.fromhex("430300C100")
    if service == 0x04:
        return b"\x44"
    if service == 0x07:
        return b"\x47\x00\x00"
    if service == 0x09 and len(payload) >= 2 and payload[1] == 0x02:
        return b"\x49\x02\x01" + VIN
    return None


def uds(payload: bytes, ecu: str) -> bytes:
    sid = payload[0] if payload else 0
    if sid == 0x10 and len(payload) >= 2:
        return bytes([0x50, payload[1], 0x00, 0x32, 0x01, 0xF4])
    if sid == 0x22 and payload[1:3] == b"\xF1\x90":
        return b"\x62\xF1\x90" + VIN
    if sid == 0x22 and payload[1:3] == b"\xF1\x87":
        return b"\x62\xF1\x87" + f"VEHICLE-SIM-{ecu}-1.0".encode()
    if sid == 0x19 and payload[1:2] == b"\x02":
        return b"\x59\x02\xFF" + DTCS
    if sid == 0x3E:
        return b"\x7E\x00"
    if sid == 0x11 and len(payload) >= 2:
        return bytes([0x51, payload[1]])
    if sid == 0x27:
        return b"\x7F\x27\x33"  # security access denied until lab key flow is enabled
    return bytes([0x7F, sid, 0x11])


def main() -> None:
    bus = open_can("vcan0")
    assemblers = {request: Reassembler() for request in ECUS}
    while True:
        readable, _, _ = select.select([bus], [], [], 1)
        if not readable:
            continue
        frame = receive(bus)
        targets = list(ECUS) if frame.can_id == 0x7DF else [frame.can_id]
        for request_id in targets:
            if request_id not in ECUS:
                continue
            payload, flow = assemblers[request_id].accept(Frame(request_id, frame.data))
            if flow:
                send(bus, flow)
            if payload is not None:
                response_id, name = ECUS[request_id]
                answer = obd(payload) if frame.can_id == 0x7DF else uds(payload, name)
                if answer:
                    transmit(bus, response_id, answer)


if __name__ == "__main__":
    main()
