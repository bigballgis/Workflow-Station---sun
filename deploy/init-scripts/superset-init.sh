#!/bin/bash
# Initialize Superset: create admin user + load permissions/roles
# Usage: ./deploy/init-scripts/superset-init.sh
set -e
docker exec platform-superset-dev superset fab create-admin \
    --username admin --firstname Admin --lastname User \
    --email admin@superset.com --password admin123
docker exec platform-superset-dev superset init
echo "Superset init completed."
