#!/usr/bin/env bash
set -euo pipefail
if [[ ${EUID} -ne 0 ]]; then echo "Run with sudo" >&2; exit 1; fi
for unit in vehicle-sim-bluetooth vehicle-sim-gateway vehicle-sim-diagnostics vehicle-sim-traffic vehicle-sim-can; do
  systemctl disable --now "$unit.service" 2>/dev/null || true
  rm -f "/etc/systemd/system/$unit.service"
done
rm -rf /opt/vehicle-sim
systemctl daemon-reload
