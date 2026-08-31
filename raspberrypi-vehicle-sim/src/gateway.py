#!/usr/bin/env python3
"""Allowlisted diagnostic gateway between exposed and in-vehicle CAN domains."""
from __future__ import annotations

import logging
import select
from canio import open_can, receive, send

logging.basicConfig(level=logging.INFO, format="%(asctime)s gateway %(message)s")
DIAG_REQUESTS = {0x7DF, 0x7E0, 0x7E1, 0x7E2}
DIAG_RESPONSES = {0x7E8, 0x7E9, 0x7EA}


def main() -> None:
    exposed, vehicle = open_can("vcan_diag"), open_can("vcan0")
    while True:
        readable, _, _ = select.select([exposed, vehicle], [], [])
        for source in readable:
            frame = receive(source)
            if source is exposed:
                if frame.can_id in DIAG_REQUESTS:
                    send(vehicle, frame)
                else:
                    logging.warning("blocked external CAN ID 0x%X", frame.can_id)
            elif frame.can_id in DIAG_RESPONSES:
                send(exposed, frame)


if __name__ == "__main__":
    main()
