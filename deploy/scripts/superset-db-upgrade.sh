#!/bin/bash
# Run Superset database migrations on the host (requires Docker CLI).
# Do NOT place this under deploy/init-scripts/ — postgres auto-runs *.sh there.
# Usage: ./deploy/scripts/superset-db-upgrade.sh [container-name]
set -e
CONTAINER="${1:-platform-superset-dev}"
docker exec "$CONTAINER" superset db upgrade
echo "Superset db upgrade completed."
