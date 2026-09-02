#!/usr/bin/env bash
set -euo pipefail

if [[ ${EUID} -ne 0 ]]; then
  echo "Run with sudo: sudo ./deploy/update-live-simulator.sh" >&2
  exit 1
fi

SOURCE_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
STAMP=$(date +%Y%m%d-%H%M%S)

python3 -m py_compile \
  "$SOURCE_DIR/live-server/vehicle_sim.py" \
  "$SOURCE_DIR/live-server/secure_gateway.py"

id vehiclesim >/dev/null 2>&1 || useradd --system --no-create-home --shell /usr/sbin/nologin vehiclesim
install -d -m 0755 /opt/vehicle-sim "/opt/vehicle-sim/backups/$STAMP"

for file in vehicle_sim.py secure_gateway.py; do
  if [[ -f "/opt/vehicle-sim/$file" ]]; then
    cp -a "/opt/vehicle-sim/$file" "/opt/vehicle-sim/backups/$STAMP/$file"
  fi
done

install -m 0644 "$SOURCE_DIR/live-server/vehicle_sim.py" /opt/vehicle-sim/vehicle_sim.py
install -m 0644 "$SOURCE_DIR/live-server/secure_gateway.py" /opt/vehicle-sim/secure_gateway.py
install -m 0644 "$SOURCE_DIR/live-server/vehicle-sim.service" /etc/systemd/system/vehicle-sim.service
install -m 0644 "$SOURCE_DIR/live-server/vehicle-sim-gateway.service" /etc/systemd/system/vehicle-sim-gateway.service

systemctl daemon-reload
systemctl enable vehicle-sim vehicle-sim-gateway
systemctl restart vehicle-sim-gateway vehicle-sim
systemctl try-restart vehicle-sim-rfcomm.service || true

systemctl is-active --quiet vehicle-sim-gateway
systemctl is-active --quiet vehicle-sim

echo "Updated successfully. Backup: /opt/vehicle-sim/backups/$STAMP"
echo "Validate with: candump -L vcan0"
