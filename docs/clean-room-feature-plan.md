# RAZIYA diagnostics — clean-room product plan

This plan records observable capabilities from public product information and a
high-level inventory of the supplied package. No proprietary source, artwork,
branding, copy, PID database, or implementation is reused.

## Product areas

### Connection and vehicle profiles

- Bluetooth Classic SPP (current lab transport)
- BLE, Wi-Fi/TCP and USB serial adapter transports
- Adapter/ECU capability detection, protocol negotiation and connection health
- Multiple saved vehicles, VIN decoding, odometer and service metadata
- Standard ELM327 command transport alongside the current raw-CAN lab transport

### Live data

- Standard OBD-II PIDs: engine, fuel, air, emissions, temperatures and voltage
- Searchable parameter list with units, min/max and data freshness
- Custom dashboards with tiles, gauges and time-series charts
- Recording, replay, CSV export and trip summaries
- User-defined extended PIDs with safe formulas and vehicle profiles

### Diagnostics

- Full ECU discovery and per-module status
- Stored, pending and permanent DTCs; clear only after explicit confirmation
- Freeze-frame data and readiness monitors
- Mode $06 on-board monitoring test results
- UDS identification, DTC status masks and snapshot/extended records
- Curated, independently sourced DTC explanations and repair guidance
- Before/after repair comparison and verification drive

### Body, chassis and convenience data

- Door, hood, tailgate, lock and ignition status
- TPMS pressure, temperature, sensor battery and sensor health
- Individual wheel speeds, ABS/ESC status and steering angle
- Gear, transmission temperature and clutch/torque-converter status
- Fuel level/range, 12 V battery health and charging-system trends
- HVAC, lighting, seat belt, parking brake and restraint status where supported
- Hybrid/EV battery state, cell temperatures and isolation data where supported

These are not universal OBD-II values. They require manufacturer-specific ECU
profiles. The Raspberry Pi uses documented synthetic DIDs for safe lab testing.

### Reports and workshop workflow

- Scan-complete notification and a dedicated report area
- Branded PDF with vehicle/client/job details, severity, evidence and next steps
- Report history, notes, photos, signatures and share/export controls
- Privacy controls, local encryption, retention settings and audit history

## Delivery sequence

1. Lab-ready overview, expanded live data, scan, vehicle/TPMS and report navigation.
2. Persistent scan/report history, charts and data recording/replay.
3. Freeze-frame, readiness and Mode $06 with multi-ECU discovery.
4. ELM327, BLE, Wi-Fi and USB transport abstraction.
5. Custom dashboard/PID editor and vehicle-profile compatibility system.
6. Workshop job cards, client records and repair verification.
7. Validated Kia Carens profile based only on authorized documentation and captures.

## Safety rules

- Default to read-only diagnostics.
- Never send coding, actuator or clear-DTC operations automatically.
- Label simulated values and unsupported parameters plainly.
- Rate-limit requests, validate ISO-TP lengths and allowlist ECU addresses.
- Require vehicle-specific validation before using lab DIDs on a real car.
