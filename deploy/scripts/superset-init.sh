#!/bin/bash
# Initialize Superset admin + roles on the host (requires Docker CLI).
# Do NOT place this under deploy/init-scripts/ — postgres auto-runs *.sh there.
# Usage: ./deploy/scripts/superset-init.sh [container-name]
set -e
CONTAINER="${1:-platform-superset-dev}"

docker exec "$CONTAINER" superset fab create-admin \
    --username admin --firstname Admin --lastname User \
    --email admin@superset.com --password admin123 \
    || echo "Superset admin may already exist (skipping create-admin)."

docker exec "$CONTAINER" superset init
echo "Superset init completed."
