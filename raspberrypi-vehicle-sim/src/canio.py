#!/usr/bin/env python3
"""Small dependency-free SocketCAN and Bluetooth framing helpers."""
from __future__ import annotations

import socket
import struct
from dataclasses import dataclass

CAN_FRAME = struct.Struct("=IB3x8s")  # Linux struct can_frame, 16 bytes
CAN_EFF_MASK = 0x1FFFFFFF


@dataclass(frozen=True)
class Frame:
    can_id: int
    data: bytes

    def __post_init__(self) -> None:
        if not 0 <= self.can_id <= CAN_EFF_MASK:
            raise ValueError("invalid CAN identifier")
        if len(self.data) > 8:
            raise ValueError("classic CAN payload exceeds 8 bytes")

    def pack(self) -> bytes:
        return CAN_FRAME.pack(self.can_id, len(self.data), self.data.ljust(8, b"\0"))

    @classmethod
    def unpack(cls, packet: bytes) -> "Frame":
        if len(packet) != CAN_FRAME.size:
            raise ValueError("CAN packet must be exactly 16 bytes")
        can_id, dlc, data = CAN_FRAME.unpack(packet)
        if dlc > 8:
            raise ValueError("invalid DLC")
        return cls(can_id & CAN_EFF_MASK, data[:dlc])


def open_can(interface: str) -> socket.socket:
    sock = socket.socket(socket.AF_CAN, socket.SOCK_RAW, socket.CAN_RAW)
    sock.bind((interface,))
    return sock


def send(sock: socket.socket, frame: Frame) -> None:
    sock.send(frame.pack())


def receive(sock: socket.socket) -> Frame:
    return Frame.unpack(sock.recv(CAN_FRAME.size))


def recv_exact(sock: socket.socket, size: int) -> bytes:
    chunks = bytearray()
    while len(chunks) < size:
        part = sock.recv(size - len(chunks))
        if not part:
            raise ConnectionError("peer disconnected")
        chunks.extend(part)
    return bytes(chunks)
