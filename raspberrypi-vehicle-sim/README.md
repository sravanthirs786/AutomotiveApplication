# Raspberry Pi 4 Vehicle Diagnostic Bench

This package turns Shahid's Raspberry Pi into a standards-shaped passenger-vehicle lab for Android diagnostic-app development. It generates continuous synthetic CAN traffic and responds to OBD-II and UDS diagnostics through Bluetooth Classic RFCOMM.

It does **not** claim that synthetic broadcast IDs are Kia Carens 2026 proprietary frames. Exact Kia traffic requires licensed Kia DBC/ODX data or lawful, sanitized captures from the owner's vehicle. The diagnostic addressing and ISO-TP/OBD/UDS framing here are suitable for building and testing the app safely before touching the car.

## Architecture

```text
Android phone
  │ Bluetooth Classic RFCOMM, channel 1
  │ fixed 16-byte Linux can_frame packets
  ▼
Bluetooth raw-CAN bridge
  │
vcan_diag  (external diagnostic domain)
  │ allowlist: 7DF, 7E0, 7E1, 7E2 only
  ▼
Secure diagnostic gateway
  │
vcan0      (simulated in-vehicle CAN)
  ├── continuous vehicle signal generator, 10 Hz
  ├── ECM request 7E0 / response 7E8
  ├── TCM request 7E1 / response 7E9
  └── ABS request 7E2 / response 7EA
```

## 1. Required equipment

For the virtual bench:

- Raspberry Pi 4 Model B, official-quality 5 V / 3 A power supply
- 16 GB or larger microSD card
- Raspberry Pi OS Bookworm 64-bit
- Android phone with Bluetooth Classic support

No CAN HAT, OBD plug, or vehicle connection is needed for the virtual stage.

For the later physical bench:

- Isolated SocketCAN-compatible CAN HAT/transceiver
- Proper 120-ohm termination for a standalone two-node CAN bench
- Fused automotive power supply or isolated DC/DC conversion
- OBD breakout lead; never connect the vehicle directly to GPIO

OBD-II high-speed CAN commonly uses pin 6 (CAN-H), pin 14 (CAN-L), pins 4/5 (ground), and pin 16 (battery). Pin 16 must **never** be connected to Raspberry Pi 5 V or 3.3 V pins.

## 2. Prepare Raspberry Pi OS

Flash Raspberry Pi OS, boot it, enable SSH if remote administration is needed, and update it:

```bash
sudo apt update
sudo apt full-upgrade -y
sudo reboot
```

Copy this entire `raspberrypi-vehicle-sim` directory onto the Pi, then run:

```bash
cd raspberrypi-vehicle-sim
sudo ./install.sh
```

The installer adds `bluez`, `bluez-tools`, `can-utils`, creates both virtual CAN interfaces, installs five system services, names the Bluetooth device `VehicleSim-OBD`, makes it discoverable, and enables everything at boot.

## 3. Confirm that the bench is running

```bash
systemctl --no-pager --failed
systemctl status vehicle-sim-can vehicle-sim-traffic vehicle-sim-diagnostics vehicle-sim-gateway vehicle-sim-bluetooth
ip -details link show vcan0
ip -details link show vcan_diag
candump vcan0
```

`candump vcan0` should continuously show IDs `180`, `280`, `380`, and `420` at approximately 10 Hz.

Test an OBD RPM request locally:

```bash
cansend vcan_diag 7DF#02010C
candump -L vcan_diag,7E8:7FF
```

Expected response shape:

```text
7E8  [5]  04 41 0C AA BB
```

Decode RPM as `((AA × 256) + BB) / 4`.

Verify the gateway blocks arbitrary injection:

```bash
cansend vcan_diag 123#11223344
journalctl -u vehicle-sim-gateway -n 10 --no-pager
```

The journal should report that CAN ID `0x123` was blocked, and it should not appear on `vcan0` because of that request.

## 4. Pair Android

1. Open Android Bluetooth settings.
2. Select `VehicleSim-OBD`.
3. Confirm the pairing prompt on both devices if requested.
4. In the Android app, open an RFCOMM socket using SPP UUID `00001101-0000-1000-8000-00805F9B34FB`.
5. Exchange only the binary CAN packets described below.

## 5. Bluetooth binary packet format

Bluetooth is only a byte transport. Each packet is exactly the 16-byte Linux `struct can_frame` used by SocketCAN on the little-endian Pi:

| Offset | Bytes | Meaning |
|---:|---:|---|
| 0 | 4 | CAN ID, unsigned little-endian |
| 4 | 1 | DLC from 0 to 8 |
| 5 | 3 | Reserved zero bytes |
| 8 | 8 | CAN payload, zero-padded after DLC |

The request `7DF#02010C` becomes:

```text
DF 07 00 00  03 00 00 00  02 01 0C 00 00 00 00 00
```

The leading `02` is the ISO-TP single-frame payload length. Do not add leading zeros before it inside the CAN data field.

## 6. Android diagnostic request frames

| Purpose | TX CAN ID | TX CAN data | Expected RX ID | Positive response prefix |
|---|---:|---|---:|---|
| Supported PIDs | 7DF | `02 01 00` | 7E8 | `06 41 00` |
| Engine RPM | 7DF | `02 01 0C` | 7E8 | `04 41 0C` |
| Vehicle speed | 7DF | `02 01 0D` | 7E8 | `03 41 0D` |
| Coolant temperature | 7DF | `02 01 05` | 7E8 | `03 41 05` |
| Engine load | 7DF | `02 01 04` | 7E8 | `03 41 04` |
| Stored OBD DTCs | 7DF | `01 03` | 7E8 | `05 43` |
| Extended UDS session | 7E0 | `02 10 03` | 7E8 | `06 50 03` |
| VIN by UDS | 7E0 | `03 22 F1 90` | 7E8 | first frame `10 14 62 F1 90` |
| ECU software DID | 7E0 | `03 22 F1 87` | 7E8 | first frame `10 .. 62 F1 87` |
| UDS DTC report | 7E0 | `03 19 02 FF` | 7E8 | first frame `10 .. 59 02 FF` |
| Tester present | 7E0 | `02 3E 00` | 7E8 | `02 7E 00` |

For a multi-frame response, Android must send flow control after the first frame:

```text
TX 7E0#300000
```

Then collect consecutive frames whose first byte is `21`, `22`, and so on, remove ISO-TP PCI bytes, and concatenate the diagnostic payload.

## 7. Simulated fault content

The initial lab state contains:

- `P0300` — random/multiple-cylinder misfire detected
- `U0100` — lost communication with ECM/PCM
- VIN `LABSIM26RPI400001`

These are controlled laboratory values. The next implementation stage should add scenario files so the mechanic can select conditions such as no-start, overheating, weak battery, wheel-speed failure, or communication loss.

## 8. Source layout

- `src/vehicle_sim.py` — continuous synthetic CAN frames
- `src/diagnostic_ecu.py` — ECM/TCM/ABS OBD-II and UDS responses
- `src/isotp.py` — ISO-TP segmentation and reassembly
- `src/gateway.py` — diagnostic ID allowlist and domain separation
- `src/bluetooth_bridge.py` — RFCOMM-to-SocketCAN binary bridge
- `src/canio.py` — exact 16-byte CAN packet codec
- `dbc/vehicle_sim_synthetic.dbc` — synthetic broadcast signal documentation
- `systemd/` — reboot-persistent services

## 9. Run the protocol tests

On the Pi or a development machine with Python 3.11+:

```bash
PYTHONPATH=src python3 -m unittest discover -s tests -v
```

## 10. Moving toward Kia Carens 2026

Do not guess Kia arbitration IDs, scaling, security algorithms, or diagnostic DIDs. Obtain one of the following lawfully:

1. Authorized Kia DBC/ODX/service documentation.
2. Sanitized CAN captures from the owner's vehicle using an isolated, listen-only interface.
3. Paired captures of known actions, logged with timestamps and vehicle state.

Start capture in listen-only mode. Validate bus bitrate and physical isolation before transmitting anything. Manufacturer-specific UDS routines, coding, security access, ECU reset, or actuator control must remain blocked until each service is understood and tested off-vehicle.

## Troubleshooting

```bash
journalctl -u vehicle-sim-bluetooth -f
journalctl -u vehicle-sim-diagnostics -f
bluetoothctl show
sdptool browse local
rfkill list bluetooth
```

If Bluetooth is soft-blocked, run `sudo rfkill unblock bluetooth` and restart `bluetooth.service` and `vehicle-sim-bluetooth.service`.
