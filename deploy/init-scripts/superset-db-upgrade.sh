#!/bin/bash
# Run Superset database migrations (host only — after superset container is up).
# Usage: ./deploy/init-scripts/superset-db-upgrade.sh
set -e
if ! command -v docker >/dev/null 2>&1; then
  echo "Skipping superset db upgrade (docker not available in this context; run from host after superset starts)."
  exit 0
fi
docker exec platform-superset-dev superset db upgrade
echo "Superset db upgrade completed."
