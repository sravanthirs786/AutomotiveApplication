#!/usr/bin/env python3
"""Bluetooth Classic RFCOMM bridge carrying raw 16-byte SocketCAN frames."""
from __future__ import annotations

import logging
import select
import socket
from canio import CAN_FRAME, Frame, open_can, receive, recv_exact, send

logging.basicConfig(level=logging.INFO, format="%(asctime)s bluetooth %(message)s")
RFCOMM_CHANNEL = 1


def serve(client: socket.socket) -> None:
    can = open_can("vcan_diag")
    try:
        while True:
            readable, _, _ = select.select([client, can], [], [])
            if client in readable:
                send(can, Frame.unpack(recv_exact(client, CAN_FRAME.size)))
            if can in readable:
                client.sendall(receive(can).pack())
    finally:
        can.close()
        client.close()


def main() -> None:
    server = socket.socket(socket.AF_BLUETOOTH, socket.SOCK_STREAM, socket.BTPROTO_RFCOMM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind((socket.BDADDR_ANY, RFCOMM_CHANNEL))
    server.listen(1)
    logging.info("VehicleSim-OBD listening on RFCOMM channel %d", RFCOMM_CHANNEL)
    while True:
        client, address = server.accept()
        logging.info("Android connected: %s", address)
        try:
            serve(client)
        except (ConnectionError, OSError, ValueError) as error:
            logging.info("client disconnected: %s", error)


if __name__ == "__main__":
    main()
