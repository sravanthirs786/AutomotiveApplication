#!/usr/bin/env python3
"""Stateful multi-ECU vehicle simulator for a SocketCAN test bench.

The broadcast arbitration IDs are intentionally lab-specific, not Kia IDs. Standard
OBD-II requests and a useful UDS subset use normal 11-bit ISO-TP addressing.
"""

from __future__ import annotations

import argparse
import math
import select
import signal
import socket
import struct
import time
from dataclasses import dataclass, field


CAN_FRAME = struct.Struct("=IB3x8s")
VIN = b"LABSIM26RPI400001"


@dataclass
class VehicleState:
    started_at: float = field(default_factory=time.monotonic)
    rpm: float = 820.0
    speed: float = 0.0
    throttle: float = 8.0
    coolant: float = 88.0
    intake: float = 31.0
    voltage: float = 11.6
    fuel: float = 62.0
    odometer: float = 18432.7
    steering_angle: float = 0.0
    brake_pressure: float = 0.0
    gear: int = 0  # 0=P, 1=R, 2=N, 3=D, 4=S
    transmission_temp: float = 72.0
    front_left_door_open: bool = False
    front_right_door_open: bool = False
    rear_left_door_open: bool = False
    rear_right_door_open: bool = False
    hood_open: bool = False
    tailgate_open: bool = False
    locked: bool = True
    ignition_on: bool = True
    parking_brake: bool = False
    driver_belt_fastened: bool = True
    passenger_belt_fastened: bool = True
    low_beam_on: bool = False
    hazard_on: bool = False
    tyre_pressure: tuple[float, float, float, float] = (168.0, 232.0, 230.0, 229.0)
    tyre_temperature: tuple[float, float, float, float] = (31.0, 30.0, 30.0, 30.0)
    tpms_battery: tuple[int, int, int, int] = (78, 91, 88, 86)
    # Deliberate roadside-breakdown scenario:
    # P0300 misfire, P0117 coolant sensor, P0562 low voltage,
    # P0420 catalyst, C0035 wheel-speed sensor, U0100/U0101 communication loss.
    dtcs: list[bytes] = field(default_factory=lambda: [
        b"\x03\x00", b"\x01\x17", b"\x05\x62", b"\x04\x20",
        b"\x40\x35", b"\xC1\x00", b"\xC1\x01",
    ])
    session: dict[int, int] = field(default_factory=dict)
    unlocked: dict[int, bool] = field(default_factory=dict)
    dtc_enabled: bool = True

    def update(self) -> None:
        t = time.monotonic() - self.started_at
        # Repeatable highway-breakdown drive cycle: cruise, misfire surge, slowdown.
        phase = t % 90.0
        if phase < 15:
            self.speed = phase * 5.2
        elif phase < 50:
            self.speed = 78.0 + 6.0 * math.sin(t / 4.0)
        elif phase < 70:
            self.speed = max(8.0, 78.0 - (phase - 50.0) * 3.5)
        else:
            self.speed = max(0.0, 8.0 - (phase - 70.0) * 0.5)
        misfire = 190.0 * math.sin(t * 5.0) if 40 < phase < 70 else 0.0
        self.rpm = max(710.0, 790.0 + self.speed * 28.0 + misfire)
        self.throttle = min(72.0, 7.0 + self.speed * 0.32 + 3.0 * math.sin(t / 3.0))
        # Faulted cooling system slowly overheats and charging voltage sags.
        self.coolant = min(114.0, 101.0 + t / 90.0)
        self.voltage = max(10.9, 11.8 - t / 1800.0)
        self.steering_angle = 12.0 * math.sin(t / 5.0)
        self.brake_pressure = 18.0 if 50.0 < phase < 70.0 else 0.0
        stopped = self.speed < 1.0
        self.gear = 0 if stopped else 3
        self.transmission_temp = min(96.0, 72.0 + t / 180.0)
        # Body state follows a plausible roadside sequence: doors remain closed while
        # moving; the driver door opens only after the vehicle has stopped.
        self.front_left_door_open = stopped and phase >= 78.0
        self.locked = phase < 70.0
        self.parking_brake = phase >= 78.0
        self.driver_belt_fastened = phase < 78.0
        self.low_beam_on = phase >= 45.0
        self.hazard_on = phase >= 70.0
        heat = min(9.0, self.speed / 12.0)
        self.tyre_temperature = (32.0 + heat, 31.0 + heat, 30.0 + heat, 30.5 + heat)
        # Direct TPMS values rise slightly with tyre temperature; FL remains
        # deliberately underinflated for the roadside fault scenario.
        self.tyre_pressure = (
            168.0 + heat * 0.35, 232.0 + heat * 0.45,
            230.0 + heat * 0.45, 229.0 + heat * 0.45,
        )

    def body_flags(self) -> int:
        values = (
            self.front_left_door_open, self.front_right_door_open,
            self.rear_left_door_open, self.rear_right_door_open,
            self.hood_open, self.tailgate_open, self.locked, self.ignition_on,
        )
        return sum((1 << bit) for bit, active in enumerate(values) if active)

    def body_secondary_flags(self) -> bytes:
        lighting = (0x01 if self.low_beam_on else 0) | (0x02 if self.hazard_on else 0)
        safety = ((0x01 if self.parking_brake else 0) |
                  (0x02 if self.driver_belt_fastened else 0) |
                  (0x04 if self.passenger_belt_fastened else 0))
        return bytes((lighting, safety))


class CanBus:
    def __init__(self, interface: str):
        self.socket = socket.socket(socket.AF_CAN, socket.SOCK_RAW, socket.CAN_RAW)
        self.socket.bind((interface,))

    def send(self, can_id: int, data: bytes) -> None:
        data = data[:8]
        self.socket.send(CAN_FRAME.pack(can_id, len(data), data.ljust(8, b"\0")))

    def recv(self) -> tuple[int, bytes]:
        raw = self.socket.recv(CAN_FRAME.size)
        can_id, length, data = CAN_FRAME.unpack(raw)
        return can_id & 0x1FFFFFFF, data[:length]


class VehicleSimulator:
    ENDPOINTS = {0x7E0: 0x7E8, 0x7E1: 0x7E9, 0x7E2: 0x7EA, 0x7E3: 0x7EB, 0x7E4: 0x7EC}

    def __init__(self, interface: str):
        self.bus = CanBus(interface)
        self.state = VehicleState()
        self.running = True
        self.rx_sessions: dict[int, dict[str, object]] = {}
        self.pending_tx: dict[int, list[bytes]] = {}
        self.next_broadcast = 0.0

    def stop(self, *_args: object) -> None:
        self.running = False

    @staticmethod
    def u16(value: float, factor: float = 1.0) -> bytes:
        return int(max(0, min(65535, round(value / factor)))).to_bytes(2, "little")

    def broadcast(self) -> None:
        s = self.state
        s.update()
        engine_flags = 0x03 | (0x04 if s.dtcs and s.dtc_enabled else 0)
        self.bus.send(0x100, self.u16(s.rpm, 0.25) + bytes((round(s.throttle / 0.4), 56, engine_flags, 0, 0)))
        self.bus.send(0x101, self.u16(s.speed, 0.01) + bytes((round(s.coolant + 40), round(s.intake + 40))) + self.u16(s.voltage, 0.001) + b"\0\0")
        wheel = self.u16(s.speed, 0.01)
        failed_front_left = self.u16(max(0.0, s.speed * 0.18), 0.01)
        self.bus.send(0x120, failed_front_left + wheel + wheel + wheel)
        steering = int(s.steering_angle / 0.1)
        brake = s.brake_pressure
        accel = (s.throttle / 100.0)
        self.bus.send(0x130, struct.pack("<hHhH", steering, round(brake / 0.1), round(accel / 0.001), 0))
        selected_ratio = 0 if s.gear == 0 else min(6, max(1, int(s.speed / 18) + 1))
        self.bus.send(0x180, bytes((s.gear, selected_ratio, round(s.transmission_temp + 40), 0)) + int(s.odometer / 0.1).to_bytes(4, "little"))
        self.bus.send(0x201, bytes((round(s.fuel / 0.4), 0)) + self.u16(480, 1) + self.u16(7.8, 0.01) + b"\0\0")
        self.bus.send(0x300, bytes((s.body_flags(),)) + s.body_secondary_flags() + b"\0\0\0\0\0")
        self.bus.send(0x310, b"".join(round(value).to_bytes(2, "little") for value in s.tyre_pressure))
        self.bus.send(0x311, bytes(round(value + 40) for value in s.tyre_temperature) + bytes(s.tpms_battery))
        fault_flags = 0x01 if s.dtcs and s.dtc_enabled else 0
        self.bus.send(0x400, bytes((fault_flags, 0, 0, 0, 0, 0, 0, 0)))

    @staticmethod
    def supported_bitmap(base: int, supported: set[int]) -> bytes:
        value = 0
        for pid in supported:
            offset = pid - base
            if 1 <= offset <= 32:
                value |= 1 << (32 - offset)
        return value.to_bytes(4, "big")

    def obd_response(self, service: int, data: bytes) -> bytes | None:
        s = self.state
        if service == 0x01 and data:
            pid = data[0]
            supported = {0x01, 0x04, 0x05, 0x0B, 0x0C, 0x0D, 0x0F, 0x10, 0x11, 0x1C,
                         0x20, 0x2F, 0x33, 0x40, 0x42, 0x46, 0x4D, 0x4E, 0x51, 0x5C, 0x5E}
            values: dict[int, bytes] = {
                0x00: self.supported_bitmap(0x00, supported),
                0x01: bytes(((0x80 if s.dtcs and s.dtc_enabled else 0) | len(s.dtcs), 0x07, 0xE0, 0x00)),
                0x04: bytes((round(min(92.0, 62.0 + abs(math.sin(time.monotonic())) * 20.0) / 100 * 255),)),
                0x05: bytes((round(s.coolant + 40),)),
                0x0B: bytes((38,)),
                0x0C: round(s.rpm * 4).to_bytes(2, "big"),
                0x0D: bytes((round(s.speed),)),
                0x0F: bytes((round(s.intake + 40),)),
                0x10: round(12.4 / 0.01).to_bytes(2, "big"),
                0x11: bytes((round(s.throttle / 100 * 255),)),
                0x1C: b"\x06",
                0x20: self.supported_bitmap(0x20, supported),
                0x2F: bytes((round(s.fuel / 100 * 255),)),
                0x33: bytes((101,)),
                0x40: self.supported_bitmap(0x40, supported),
                0x42: round(s.voltage * 1000).to_bytes(2, "big"),
                0x46: bytes((round(27 + 40),)),
                0x4D: int((time.monotonic() - s.started_at) / 60).to_bytes(2, "big"),
                0x4E: b"\x00\x00",
                0x51: b"\x01",
                0x5C: bytes((round(92 + 40),)),
                0x5E: round(1.9 / 0.05).to_bytes(2, "big"),
            }
            if pid in values:
                return bytes((0x41, pid)) + values[pid]
        elif service == 0x03:
            return b"\x43" + b"".join(s.dtcs if s.dtc_enabled else [])
        elif service == 0x04:
            s.dtcs.clear()
            return b"\x44"
        elif service in (0x07, 0x0A):
            return bytes((service + 0x40,))
        elif service == 0x09 and data:
            pid = data[0]
            if pid == 0x00:
                return b"\x49\x00\x55\x40\x00\x00"
            if pid == 0x02:
                return b"\x49\x02\x01" + VIN
            if pid == 0x04:
                return b"\x49\x04\x01LAB-ECM-CAL-2026"
            if pid == 0x0A:
                return b"\x49\x0A\x01LABECU01"
        return None

    @staticmethod
    def negative(sid: int, code: int) -> bytes:
        return bytes((0x7F, sid, code))

    def uds_response(self, req_id: int, payload: bytes) -> bytes | None:
        if not payload:
            return None
        sid, data = payload[0], payload[1:]
        s = self.state
        if sid == 0x10 and data:
            if data[0] not in (0x01, 0x02, 0x03):
                return self.negative(sid, 0x12)
            s.session[req_id] = data[0]
            return bytes((0x50, data[0], 0x00, 0x32, 0x01, 0xF4))
        if sid == 0x11 and data:
            return bytes((0x51, data[0]))
        if sid == 0x3E:
            return b"\x7E" + (data[:1] or b"\x00")
        if sid == 0x14:
            s.dtcs.clear()
            return b"\x54"
        if sid == 0x19 and data:
            sub = data[0]
            if sub == 0x01:
                return bytes((0x59, 0x01, data[1] if len(data) > 1 else 0xFF, 0, 0, len(s.dtcs)))
            if sub == 0x02:
                records = b"".join(code + b"\x00\x2F" for code in s.dtcs)
                return b"\x59\x02\xFF" + records
            return self.negative(sid, 0x12)
        if sid == 0x22 and len(data) >= 2:
            did = data[:2]
            if did == b"\xD1\x00" and req_id == 0x7E3:
                return b"\x62\xD1\x00" + bytes((s.body_flags(),))
            if did == b"\xD1\x01" and req_id == 0x7E3:
                return b"\x62\xD1\x01" + s.body_secondary_flags()
            if did == b"\xD2\x00" and req_id == 0x7E4:
                return b"\x62\xD2\x00" + b"".join(round(value).to_bytes(2, "big") for value in s.tyre_pressure)
            if did == b"\xD2\x01" and req_id == 0x7E4:
                return b"\x62\xD2\x01" + bytes(round(value + 40) for value in s.tyre_temperature)
            if did == b"\xD2\x02" and req_id == 0x7E4:
                # Battery estimate followed by sensor-present flags (FL/FR/RL/RR).
                return b"\x62\xD2\x02" + bytes(s.tpms_battery) + b"\x0F"
            if did == b"\xD3\x00" and req_id == 0x7E2:
                speeds = (s.speed * 0.18, s.speed, s.speed, s.speed)
                return b"\x62\xD3\x00" + b"".join(round(value * 100).to_bytes(2, "big") for value in speeds)
            if did == b"\xD4\x00" and req_id == 0x7E1:
                return b"\x62\xD4\x00" + bytes((s.gear, round(s.transmission_temp + 40)))
            if did == b"\xD4\x01" and req_id == 0x7E1:
                input_rpm = round(s.rpm)
                output_rpm = round(s.speed * 38.0)
                return b"\x62\xD4\x01" + input_rpm.to_bytes(2, "big") + output_rpm.to_bytes(2, "big")
            dids = {b"\xF1\x90": VIN, b"\xF1\x95": b"LAB-SW-1.0.0",
                    b"\xF1\x97": b"RPI4-VEHICLE-SIM", b"\xF1\x8C": b"ECU-LAB-0001"}
            answer = bytearray(b"\x62")
            for pos in range(0, len(data) - 1, 2):
                did = data[pos:pos + 2]
                if did not in dids:
                    return self.negative(sid, 0x31)
                answer.extend(did + dids[did])
            return bytes(answer)
        if sid == 0x27 and data:
            level = data[0]
            if level == 0x01:
                return b"\x67\x01\x12\x44"
            if level == 0x02 and len(data) >= 3:
                expected = (0x1244 ^ 0xA5C3).to_bytes(2, "big")
                if data[1:3] == expected:
                    s.unlocked[req_id] = True
                    return b"\x67\x02"
                return self.negative(sid, 0x35)
            return self.negative(sid, 0x12)
        if sid == 0x85 and data:
            s.dtc_enabled = data[0] == 0x01
            return b"\xC5" + data[:1]
        if sid in (0x2E, 0x31, 0x34, 0x36, 0x37):
            return self.negative(sid, 0x33)
        return self.negative(sid, 0x11)

    def send_payload(self, req_id: int, resp_id: int, payload: bytes) -> None:
        if len(payload) <= 7:
            self.bus.send(resp_id, bytes((len(payload),)) + payload)
            return
        self.bus.send(resp_id, bytes((0x10 | ((len(payload) >> 8) & 0x0F), len(payload) & 0xFF)) + payload[:6])
        chunks = [payload[pos:pos + 7] for pos in range(6, len(payload), 7)]
        self.pending_tx[req_id] = [bytes((0x20 | ((i + 1) & 0x0F),)) + chunk for i, chunk in enumerate(chunks)]

    def complete_request(self, incoming_id: int, payload: bytes) -> None:
        req_ids = list(self.ENDPOINTS) if incoming_id == 0x7DF else [incoming_id]
        for req_id in req_ids:
            resp_id = self.ENDPOINTS.get(req_id)
            if resp_id is None:
                continue
            if payload and payload[0] in (0x01, 0x03, 0x04, 0x07, 0x09, 0x0A):
                # Engine ECU owns generic emissions diagnostics on this lab vehicle.
                if req_id != 0x7E0:
                    continue
                answer = self.obd_response(payload[0], payload[1:])
            else:
                answer = self.uds_response(req_id, payload)
            if answer is not None:
                self.send_payload(req_id, resp_id, answer)

    def handle_frame(self, can_id: int, data: bytes) -> None:
        if can_id not in self.ENDPOINTS and can_id != 0x7DF:
            return
        if not data:
            return
        frame_type = data[0] >> 4
        if frame_type == 0x0:
            self.complete_request(can_id, data[1:1 + (data[0] & 0x0F)])
        elif frame_type == 0x1 and len(data) >= 2:
            total = ((data[0] & 0x0F) << 8) | data[1]
            self.rx_sessions[can_id] = {"total": total, "data": bytearray(data[2:]), "seq": 1}
            resp_id = self.ENDPOINTS.get(can_id, 0x7E8)
            self.bus.send(resp_id, b"\x30\x00\x05")
        elif frame_type == 0x2 and can_id in self.rx_sessions:
            session = self.rx_sessions[can_id]
            if (data[0] & 0x0F) != session["seq"]:
                self.rx_sessions.pop(can_id, None)
                return
            session["seq"] = (int(session["seq"]) + 1) & 0x0F
            session_data = session["data"]
            assert isinstance(session_data, bytearray)
            session_data.extend(data[1:])
            if len(session_data) >= int(session["total"]):
                payload = bytes(session_data[:int(session["total"])])
                self.rx_sessions.pop(can_id, None)
                self.complete_request(can_id, payload)
        elif frame_type == 0x3 and can_id in self.pending_tx:
            frames = self.pending_tx.pop(can_id)
            for frame in frames:
                self.bus.send(self.ENDPOINTS[can_id], frame)
                time.sleep(0.005)

    def run(self) -> None:
        signal.signal(signal.SIGTERM, self.stop)
        signal.signal(signal.SIGINT, self.stop)
        while self.running:
            now = time.monotonic()
            if now >= self.next_broadcast:
                self.broadcast()
                self.next_broadcast = now + 0.1
            readable, _, _ = select.select([self.bus.socket], [], [], 0.02)
            if readable:
                self.handle_frame(*self.bus.recv())


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--interface", default="vcan0")
    args = parser.parse_args()
    VehicleSimulator(args.interface).run()


if __name__ == "__main__":
    main()
