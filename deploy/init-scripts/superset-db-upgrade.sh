#!/bin/bash
# Run Superset database migrations
# Usage: ./deploy/init-scripts/superset-db-upgrade.sh
set -e
docker exec platform-superset-dev superset db upgrade
echo "Superset db upgrade completed."
