#!/usr/bin/env python3
"""Allowlisted diagnostic gateway between the external OBD and internal CAN domains."""

from __future__ import annotations

import argparse
import select
import signal
import socket
import struct


def filtered_socket(interface: str, can_ids: list[int]) -> socket.socket:
    bus = socket.socket(socket.AF_CAN, socket.SOCK_RAW, socket.CAN_RAW)
    filters = b"".join(struct.pack("=II", can_id, 0x7FF) for can_id in can_ids)
    bus.setsockopt(getattr(socket, "SOL_CAN_RAW", 101), getattr(socket, "CAN_RAW_FILTER", 1), filters)
    bus.bind((interface,))
    return bus


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--external", default="vcan_diag")
    parser.add_argument("--internal", default="vcan0")
    args = parser.parse_args()

    # Functional OBD plus the lab ECM, TCM, ABS, BCM and TPMS endpoints.
    external_rx = filtered_socket(args.external, [0x7DF, 0x7E0, 0x7E1, 0x7E2, 0x7E3, 0x7E4])
    internal_rx = filtered_socket(args.internal, [0x7E8, 0x7E9, 0x7EA, 0x7EB, 0x7EC])
    external_tx = socket.socket(socket.AF_CAN, socket.SOCK_RAW, socket.CAN_RAW)
    external_tx.bind((args.external,))
    internal_tx = socket.socket(socket.AF_CAN, socket.SOCK_RAW, socket.CAN_RAW)
    internal_tx.bind((args.internal,))

    running = True

    def stop(*_args: object) -> None:
        nonlocal running
        running = False

    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)
    while running:
        ready, _, _ = select.select([external_rx, internal_rx], [], [], 0.5)
        for source in ready:
            frame = source.recv(16)
            (internal_tx if source is external_rx else external_tx).send(frame)


if __name__ == "__main__":
    main()
