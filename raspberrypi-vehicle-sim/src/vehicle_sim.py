#!/usr/bin/env python3
"""Continuous synthetic passenger-vehicle CAN traffic for app development."""
from __future__ import annotations

import math
import signal
import time
from canio import Frame, open_can, send

running = True


def stop(*_) -> None:
    global running
    running = False


def main() -> None:
    signal.signal(signal.SIGTERM, stop)
    bus = open_can("vcan0")
    started = time.monotonic()
    counter = 0
    while running:
        elapsed = time.monotonic() - started
        speed = max(0, int(58 + 25 * math.sin(elapsed / 8)))
        rpm = max(750, int(850 + speed * 31 + 250 * math.sin(elapsed * 1.7)))
        coolant = min(92, 70 + int(elapsed / 30))
        throttle = max(5, min(90, int(24 + 18 * math.sin(elapsed / 3))))
        # Documented synthetic IDs, deliberately not claimed as Kia proprietary frames.
        frames = (
            Frame(0x180, rpm.to_bytes(2, "big") + speed.to_bytes(1, "big") + bytes([counter & 0xFF])),
            Frame(0x280, bytes([speed, throttle, coolant + 40, counter & 0x0F])),
            Frame(0x380, bytes([0, 0, 0, 0, counter & 0xFF])),
            Frame(0x420, bytes([0x01, 0x00, 0x00, 0x00, counter & 0xFF])),
        )
        for frame in frames:
            send(bus, frame)
        counter += 1
        time.sleep(0.1)


if __name__ == "__main__":
    main()
