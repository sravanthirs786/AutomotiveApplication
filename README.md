# RAZIYA Diagnostics

Native Android diagnostic client for the Raspberry Pi vehicle simulator. Bluetooth is only the transport: the app exchanges fixed-size binary classic-CAN frames and does not use ELM327 or AT commands.

## Bluetooth wire contract

Each RFCOMM packet is exactly the 16-byte Linux SocketCAN `struct can_frame` layout:

| Offset | Size | Field |
|---|---:|---|
| 0 | 4 | CAN ID, unsigned little-endian |
| 4 | 1 | DLC, 0–8 |
| 5 | 3 | Reserved, zero |
| 8 | 8 | CAN data, zero-padded |

Example OBD RPM request `7DF#02010C`:

`DF 07 00 00 03 00 00 00 02 01 0C 00 00 00 00 00`

The Raspberry Pi Bluetooth bridge must use the same framing contract. It can unpack each packet directly as a SocketCAN `can_frame` on little-endian Linux.

## Current feature slice

- Paired Bluetooth Classic SPP device selection
- Binary CAN transmit/receive and 16-byte stream framing
- Standard OBD live RPM, speed, coolant, and engine-load polling
- ISO-TP single/multi-frame reassembly and flow-control transmission
- UDS VIN request and generic OBD DTC decoding
- Live TX/RX CAN monitor
- Unit coverage for binary framing, RPM, and multi-frame VIN

## Build

Open the project in Android Studio (JDK 17+) and run the `app` configuration on an Android 8.0+ device. Pair `VehicleSim-OBD` in system Bluetooth settings before connecting in the app.
