# Real-car alignment and validation boundary

## What is standardized

The external diagnostic path uses Classical CAN via Linux SocketCAN, ISO-TP-shaped
segmentation, functional OBD requests, and UDS-shaped physical ECU requests. Generic
OBD values are limited to emissions and powertrain parameters that a vehicle declares
as supported.

## What is manufacturer-specific

Door and closure states, locks, individual tyre pressures/temperatures, wheel speeds,
lighting, restraints, ABS/ESC internals, and detailed transmission data are not a
portable set of generic OBD-II PIDs. Their CAN identifiers, diagnostic addresses,
DIDs, bit layout, scaling, session rules, gateway routing and availability vary by
vehicle platform and ECU software.

The lab model uses separate ECM, TCM, ABS, BCM and TPMS endpoints and follows common
vehicle signal names/units. Every broadcast ID and `D1xx`–`D4xx` DID is deliberately
synthetic. This lets the Android application learn capability discovery, unavailable
data, ISO-TP, stale data and multi-ECU behavior without guessing OEM traffic.

## Required process before a real Kia test

1. Use an isolated, fused, SocketCAN-compatible interface and an OBD breakout lead.
2. Begin in listen-only mode; confirm bus bitrate and network stability.
3. Record timestamped baselines with ignition states clearly logged.
4. Capture one known action at a time: each door, hood, tailgate, lock, gear and wheel.
5. Never infer a signal from a single changing byte; repeat captures and validate
   counter, checksum, multiplexing, timeout and scaling behavior.
6. Keep transmission, actuator, coding, security-access, ECU-reset and DTC-clear
   requests blocked until supported by authorized information and an off-vehicle test.
7. Store discovered mappings in a versioned vehicle profile keyed by model year,
   market, powertrain, trim and ECU software identifiers—not in the generic decoder.

## Release gate

A signal may be labelled “supported” for a real vehicle only after it has an authorized
source or repeatable capture evidence, known units/scaling, timeout behavior, invalid
value handling, and a read-only on-vehicle validation record. Until then the app must
show “not supported or unavailable,” never a fabricated zero.
