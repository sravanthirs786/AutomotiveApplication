#!/usr/bin/env bash
set -euo pipefail

if [[ ${EUID} -ne 0 ]]; then
  echo "Run with sudo: sudo ./install.sh" >&2
  exit 1
fi

SOURCE_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
apt-get update
apt-get install -y bluez bluez-tools can-utils python3

id vehiclecan >/dev/null 2>&1 || useradd --system --no-create-home --shell /usr/sbin/nologin vehiclecan
install -d -m 0755 /opt/vehicle-sim/src
install -m 0644 "$SOURCE_DIR"/src/*.py /opt/vehicle-sim/src/
install -m 0644 "$SOURCE_DIR"/systemd/*.service /etc/systemd/system/
install -d -m 0755 /etc/systemd/system/bluetooth.service.d
install -m 0644 "$SOURCE_DIR"/systemd/bluetooth-compat.conf /etc/systemd/system/bluetooth.service.d/compat.conf

bluetoothctl system-alias VehicleSim-OBD
systemctl daemon-reload
systemctl enable vehicle-sim-can vehicle-sim-traffic vehicle-sim-diagnostics vehicle-sim-gateway vehicle-sim-bluetooth
systemctl restart bluetooth
systemctl restart vehicle-sim-can vehicle-sim-traffic vehicle-sim-diagnostics vehicle-sim-gateway vehicle-sim-bluetooth

echo "Installed. Pair Android with Bluetooth device VehicleSim-OBD."
echo "Validate with: candump vcan0"
