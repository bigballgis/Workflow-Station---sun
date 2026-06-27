#!/bin/bash
# Initialize Superset: create admin user + load permissions/roles (host only).
# Usage: ./deploy/init-scripts/superset-init.sh
set -e
if ! command -v docker >/dev/null 2>&1; then
  echo "Skipping superset init (docker not available in this context; run from host after superset starts)."
  exit 0
fi
docker exec platform-superset-dev superset fab create-admin \
    --username admin --firstname Admin --lastname User \
    --email admin@superset.com --password admin123
docker exec platform-superset-dev superset init
echo "Superset init completed."
