#!/bin/bash
# MFE Phase 2 — Rollback Drill Script
# Simulates: create → switch-version → rollback → verify
#
# Usage:
#   chmod +x deploy/scripts/mfe-rollback-drill.sh
#   ./deploy/scripts/mfe-rollback-drill.sh [base_url] [tenant_id]

set -euo pipefail

BASE_URL="${1:-http://localhost:8090/api/v1/admin}"
TENANT="${2:-DEFAULT}"

RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

step() { echo -e "\n${BLUE}[STEP $1]${NC} $2"; }
ok()   { echo -e "  ${GREEN}OK${NC} $1"; }
fail() { echo -e "  ${RED}FAIL${NC} $1"; exit 1; }

# ======== Drill Setup ========
step 1 "Create drill module"

# Clean up from previous drill if exists
EXISTING=$(curl -s -H "X-Tenant-Id: $TENANT" \
    "$BASE_URL/frontend-modules?hostApp=user-portal&env=DEV" | \
    grep -o '"id":[0-9]*,"tenantId":"DEFAULT","hostApp":"user-portal","moduleCode":"drill-test"' | \
    grep -o '"id":[0-9]*' | head -1 | cut -d: -f2 || true)

if [ -n "${EXISTING:-}" ]; then
    echo "  Found existing drill module $EXISTING, reusing"
    MODULE_ID="$EXISTING"
else
    RESP=$(curl -s -H "X-Tenant-Id: $TENANT" \
        -H "Content-Type: application/json" \
        -X POST "$BASE_URL/frontend-modules" \
        -d '{
            "hostApp": "user-portal",
            "moduleCode": "drill-test",
            "displayName": "Drill Test Module",
            "routePath": "/drill-test",
            "icon": "Warning",
            "orderNo": 999,
            "remoteEntryUrl": "http://localhost:3100/assets/remoteEntry.js",
            "exposedModule": "./App",
            "enabled": true,
            "requiredPermissions": [],
            "tenantScope": [],
            "env": "DEV",
            "version": "1.0.0"
        }')
    MODULE_ID=$(echo "$RESP" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
    echo "  Created drill module ID=$MODULE_ID"
fi

# ======== Baseline: verify v1.0.0 ========
step 2 "Baseline: verify version is 1.0.0"
RESP=$(curl -s -H "X-Tenant-Id: $TENANT" \
    "$BASE_URL/frontend-modules?hostApp=user-portal&env=DEV")
VERSION=$(echo "$RESP" | grep -o '"version":"[^"]*"' | head -1)
echo "  Current version: $VERSION"
echo "$VERSION" | grep -q "1.0.0" && ok "Baseline = 1.0.0" || fail "Expected 1.0.0"

# ======== Switch to v1.0.1 ========
step 3 "Switch version to 1.0.1"
RESP=$(curl -s -w "\n%{http_code}" \
    -H "X-Tenant-Id: $TENANT" \
    -H "Content-Type: application/json" \
    -X POST "$BASE_URL/frontend-modules/$MODULE_ID/switch-version" \
    -d '{"version": "1.0.1", "remoteEntryUrl": "http://localhost:3100/assets/remoteEntry.js"}')
HTTP_CODE=$(echo "$RESP" | tail -1)
[ "$HTTP_CODE" = "200" ] && ok "Switch to 1.0.1 OK" || fail "Switch failed (HTTP $HTTP_CODE)"

# Verify
RESP=$(curl -s -H "X-Tenant-Id: $TENANT" \
    "$BASE_URL/frontend-modules?hostApp=user-portal&env=DEV")
echo "$RESP" | grep -q "1.0.1" && ok "Registry shows 1.0.1" || fail "Registry not updated"

# ======== Switch to v1.0.2 ========
step 4 "Switch version to 1.0.2"
curl -s -H "X-Tenant-Id: $TENANT" -H "Content-Type: application/json" \
    -X POST "$BASE_URL/frontend-modules/$MODULE_ID/switch-version" \
    -d '{"version": "1.0.2", "remoteEntryUrl": "http://localhost:3100/assets/remoteEntry.js"}' > /dev/null
RESP=$(curl -s -H "X-Tenant-Id: $TENANT" \
    "$BASE_URL/frontend-modules?hostApp=user-portal&env=DEV")
echo "$RESP" | grep -q "1.0.2" && ok "Switch to 1.0.2 OK" || fail "Switch to 1.0.2 failed"

# ======== Check version history ========
step 5 "Verify version history"
RESP=$(curl -s -H "X-Tenant-Id: $TENANT" \
    "$BASE_URL/frontend-modules/$MODULE_ID/versions")
echo "$RESP" | grep -q "1.0.0" && ok "History contains 1.0.0" || fail "Missing 1.0.0 in history"
echo "$RESP" | grep -q "1.0.1" && ok "History contains 1.0.1" || fail "Missing 1.0.1 in history"
echo "$RESP" | grep -q "1.0.2" && ok "History contains 1.0.2" || fail "Missing 1.0.2 in history"

# Confirm only v1.0.2 is active
ACTIVE_COUNT=$(echo "$RESP" | grep -o '"isActive":true' | wc -l | tr -d ' ')
[ "$ACTIVE_COUNT" = "1" ] && ok "Only 1 version is active" || fail "Expected 1 active, got $ACTIVE_COUNT"

# ======== ROLLBACK to v1.0.0 ========
step 6 "ROLLBACK to v1.0.0"
START_TIME=$(date +%s)
RESP=$(curl -s -w "\n%{http_code}" \
    -H "X-Tenant-Id: $TENANT" \
    -H "Content-Type: application/json" \
    -X POST "$BASE_URL/frontend-modules/$MODULE_ID/rollback-version" \
    -d '{"targetVersion": "1.0.0"}')
END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))
HTTP_CODE=$(echo "$RESP" | tail -1)
[ "$HTTP_CODE" = "200" ] && ok "Rollback OK (${DURATION}s)" || fail "Rollback failed (HTTP $HTTP_CODE)"

# ======== Verify rollback ========
step 7 "Verify rollback result"
RESP=$(curl -s -H "X-Tenant-Id: $TENANT" \
    "$BASE_URL/frontend-modules?hostApp=user-portal&env=DEV")
echo "$RESP" | grep -q "1.0.0" && ok "Registry shows 1.0.0" || fail "Registry not rolled back"

# Check that v1.0.0 is now active
VER_RESP=$(curl -s -H "X-Tenant-Id: $TENANT" \
    "$BASE_URL/frontend-modules/$MODULE_ID/versions")
ACTIVE_VER=$(echo "$VER_RESP" | grep -o '"isActive":true,"version":"[^"]*"' | grep -o '"version":"[^"]*"' | cut -d'"' -f4)
[ "$ACTIVE_VER" = "1.0.0" ] && ok "Active version = 1.0.0" || fail "Active version = $ACTIVE_VER, expected 1.0.0"

# ======== Audit check ========
step 8 "Verify audit trail"
echo "  Audit records are logged to admin_audit_log table."
echo "  Query: SELECT * FROM admin_audit_log WHERE resource_type = 'FRONTEND_MODULE' ORDER BY created_at DESC LIMIT 10;"
ok "Drill complete — audit check is manual"

# ======== Performance ========
echo ""
echo "============================================"
echo -e " ${GREEN}ROLLBACK DRILL PASSED${NC}"
echo ""
echo " Rollback duration: ${DURATION}s"
echo " Drill module ID:   $MODULE_ID"
echo " Target:            v1.0.2 → v1.0.0"
echo "============================================"
echo ""
echo " Manual verification SQL:"
echo "   SELECT version, is_active, created_at"
echo "   FROM ac_frontend_module_version"
echo "   WHERE module_registry_id = $MODULE_ID"
echo "   ORDER BY created_at DESC;"
