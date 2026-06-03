#!/bin/bash
# ============================================================
# Gateway Governance DDL — apply to EXISTING database
# ============================================================
# Usage:
#   cd deploy/init-scripts
#   bash 00-schema/apply-gateway-ddl.sh
#
# This script applies Phase 1 + Phase 2 gateway DDL to an
# already-initialized database where the initdb.d scripts
# did not run (because they were added after first boot).
# ============================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
COMPOSE_FILE="$PROJECT_ROOT/deploy/environments/dev/docker-compose.dev.yml"

echo "=== Applying Gateway Governance Phase 1 DDL ==="
docker compose -f "$COMPOSE_FILE" exec -T postgres psql \
  -U platform_dev -d workflow_platform_dev \
  -f /docker-entrypoint-initdb.d/00-schema/36-gateway-governance-schema.sql

echo ""
echo "=== Applying Gateway Governance Phase 2 DDL ==="
docker compose -f "$COMPOSE_FILE" exec -T postgres psql \
  -U platform_dev -d workflow_platform_dev \
  -f /docker-entrypoint-initdb.d/00-schema/37-gateway-governance-phase2.sql

echo ""
echo "=== Seeding gateway environments ==="
docker compose -f "$COMPOSE_FILE" exec -T postgres psql \
  -U platform_dev -d workflow_platform_dev <<'SQL'
INSERT INTO ac_gateway_environment (id, tenant_id, env_code, name, gateway_provider, mode, admin_endpoint, enabled, created_at, updated_at) VALUES
  (1, 'default', 'DEV',  'Development',  'KONG', 'DB', 'http://kong-dev:8001',  true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (2, 'default', 'SIT',  'System Integration Test', 'KONG', 'DB', 'http://kong-sit:8001',  true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (3, 'default', 'UAT',  'User Acceptance Test',   'KONG', 'DB', 'http://kong-uat:8001',  true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (4, 'default', 'PROD', 'Production',             'KONG', 'DB', 'http://kong-prod:8001', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
SQL

echo ""
echo "=== Gateway DDL applied successfully ==="
echo "13 tables: ac_gateway_api_definition, ac_gateway_api_version,"
echo "  ac_gateway_application, ac_gateway_credential, ac_gateway_access_policy,"
echo "  ac_gateway_traffic_policy, ac_gateway_environment, ac_gateway_release,"
echo "  ac_gateway_publish_history, ac_gateway_audit_log,"
echo "  ac_gateway_drift_report, ac_gateway_release_approval,"
echo "  ac_gateway_metrics_snapshot"
echo "4 environments: DEV, SIT, UAT, PROD"
