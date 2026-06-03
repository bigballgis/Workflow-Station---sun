#!/bin/bash
# MFE Governance Phase 2 — e2e API Test Script
# Tests all CRUD + version management + health check operations
#
# Usage:
#   chmod +x deploy/scripts/mfe-e2e-test.sh
#   ./deploy/scripts/mfe-e2e-test.sh [base_url] [tenant_id]
#
# Default: http://localhost:8090/api/v1/admin  DEFAULT

set -euo pipefail

BASE_URL="${1:-http://localhost:8090/api/v1/admin}"
TENANT="${2:-DEFAULT}"
PASS=0
FAIL=0
TOTAL=0

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

assert() {
    local desc="$1" expected="$2" actual="$3"
    TOTAL=$((TOTAL + 1))
    if [ "$actual" = "$expected" ]; then
        echo -e "  ${GREEN}PASS${NC} $desc"
        PASS=$((PASS + 1))
    else
        echo -e "  ${RED}FAIL${NC} $desc"
        echo "    expected: $expected"
        echo "    actual:   $actual"
        FAIL=$((FAIL + 1))
    fi
}

assert_contains() {
    local desc="$1" needle="$2" haystack="$3"
    TOTAL=$((TOTAL + 1))
    if echo "$haystack" | grep -q "$needle"; then
        echo -e "  ${GREEN}PASS${NC} $desc"
        PASS=$((PASS + 1))
    else
        echo -e "  ${RED}FAIL${NC} $desc"
        echo "    expected to contain: $needle"
        FAIL=$((FAIL + 1))
    fi
}

echo "============================================"
echo " MFE Phase 2 e2e Test Suite"
echo " Base URL: $BASE_URL"
echo " Tenant:   $TENANT"
echo "============================================"
echo ""

# ======== 1. List modules ========
echo "--- 1. List Modules ---"
RESP=$(curl -s -w "\n%{http_code}" \
    -H "X-Tenant-Id: $TENANT" \
    "$BASE_URL/frontend-modules?hostApp=user-portal&env=DEV")
HTTP_CODE=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
assert "GET /frontend-modules returns 200" "200" "$HTTP_CODE"
assert_contains "Response contains content array" '"content"' "$BODY"

# ======== 2. Create notification-mfe ========
echo "--- 2. Create notification-mfe ---"
RESP=$(curl -s -w "\n%{http_code}" \
    -H "X-Tenant-Id: $TENANT" \
    -H "Content-Type: application/json" \
    -X POST "$BASE_URL/frontend-modules" \
    -d '{
        "hostApp": "user-portal",
        "moduleCode": "notification-mfe",
        "displayName": "Notifications (MFE)",
        "routePath": "/mfe/notifications",
        "icon": "Bell",
        "orderNo": 40,
        "remoteEntryUrl": "http://localhost:3100/assets/remoteEntry.js",
        "exposedModule": "./App",
        "enabled": true,
        "requiredPermissions": [],
        "tenantScope": [],
        "env": "DEV",
        "version": "1.0.0"
    }')
HTTP_CODE=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
assert "POST /frontend-modules returns 201" "201" "$HTTP_CODE"
MODULE_ID=$(echo "$BODY" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
echo "  Module ID: ${MODULE_ID:-N/A}"

# ======== 3. Create delegation-mfe ========
echo "--- 3. Create delegation-mfe ---"
RESP=$(curl -s -w "\n%{http_code}" \
    -H "X-Tenant-Id: $TENANT" \
    -H "Content-Type: application/json" \
    -X POST "$BASE_URL/frontend-modules" \
    -d '{
        "hostApp": "user-portal",
        "moduleCode": "delegation-mfe",
        "displayName": "Delegation (MFE)",
        "routePath": "/mfe/delegations",
        "icon": "Share",
        "orderNo": 45,
        "remoteEntryUrl": "http://localhost:3101/assets/remoteEntry.js",
        "exposedModule": "./App",
        "enabled": true,
        "requiredPermissions": [],
        "tenantScope": [],
        "env": "DEV",
        "version": "1.0.0"
    }')
HTTP_CODE=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
assert "POST /frontend-modules returns 201" "201" "$HTTP_CODE"
DELEG_ID=$(echo "$BODY" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
echo "  Delegation Module ID: ${DELEG_ID:-N/A}"

# ======== 4. Runtime API ========
echo "--- 4. Runtime Config ---"
RESP=$(curl -s -w "\n%{http_code}" \
    -H "X-Tenant-Id: $TENANT" \
    "$BASE_URL/frontend-modules/runtime?hostApp=user-portal&env=DEV")
HTTP_CODE=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
assert "GET /frontend-modules/runtime returns 200" "200" "$HTTP_CODE"
assert_contains "Runtime config includes notification-mfe" '"notification-mfe"' "$BODY"

# ======== 5. Disable module ========
echo "--- 5. Disable notification-mfe ---"
if [ -n "${MODULE_ID:-}" ] && [ "$MODULE_ID" != "N/A" ]; then
    RESP=$(curl -s -w "\n%{http_code}" \
        -H "X-Tenant-Id: $TENANT" \
        -X POST "$BASE_URL/frontend-modules/$MODULE_ID/disable")
    HTTP_CODE=$(echo "$RESP" | tail -1)
    assert "POST disable returns 200" "200" "$HTTP_CODE"

    # Verify module disabled
    RESP=$(curl -s -w "\n%{http_code}" \
        -H "X-Tenant-Id: $TENANT" \
        "$BASE_URL/frontend-modules/runtime?hostApp=user-portal&env=DEV")
    BODY=$(echo "$RESP" | sed '$d')
    assert_contains "Runtime excludes disabled module" '[]' "$BODY"
else
    echo -e "  ${YELLOW}SKIP${NC} No module ID (create may have conflicted)"
fi

# ======== 6. Enable module ========
echo "--- 6. Enable notification-mfe ---"
if [ -n "${MODULE_ID:-}" ] && [ "$MODULE_ID" != "N/A" ]; then
    RESP=$(curl -s -w "\n%{http_code}" \
        -H "X-Tenant-Id: $TENANT" \
        -X POST "$BASE_URL/frontend-modules/$MODULE_ID/enable")
    HTTP_CODE=$(echo "$RESP" | tail -1)
    assert "POST enable returns 200" "200" "$HTTP_CODE"
fi

# ======== 7. Switch version ========
echo "--- 7. Switch Version ---"
if [ -n "${MODULE_ID:-}" ] && [ "$MODULE_ID" != "N/A" ]; then
    RESP=$(curl -s -w "\n%{http_code}" \
        -H "X-Tenant-Id: $TENANT" \
        -H "Content-Type: application/json" \
        -X POST "$BASE_URL/frontend-modules/$MODULE_ID/switch-version" \
        -d '{"version": "1.0.1", "remoteEntryUrl": "http://localhost:3100/assets/remoteEntry.js"}')
    HTTP_CODE=$(echo "$RESP" | tail -1)
    assert "POST switch-version returns 200" "200" "$HTTP_CODE"
fi

# ======== 8. Version history ========
echo "--- 8. Version History ---"
if [ -n "${MODULE_ID:-}" ] && [ "$MODULE_ID" != "N/A" ]; then
    RESP=$(curl -s -w "\n%{http_code}" \
        -H "X-Tenant-Id: $TENANT" \
        "$BASE_URL/frontend-modules/$MODULE_ID/versions")
    HTTP_CODE=$(echo "$RESP" | tail -1)
    BODY=$(echo "$RESP" | sed '$d')
    assert "GET /versions returns 200" "200" "$HTTP_CODE"
    assert_contains "Version history includes 1.0.0" '"1.0.0"' "$BODY"
    assert_contains "Version history includes 1.0.1" '"1.0.1"' "$BODY"
fi

# ======== 9. Rollback version ========
echo "--- 9. Rollback Version ---"
if [ -n "${MODULE_ID:-}" ] && [ "$MODULE_ID" != "N/A" ]; then
    RESP=$(curl -s -w "\n%{http_code}" \
        -H "X-Tenant-Id: $TENANT" \
        -H "Content-Type: application/json" \
        -X POST "$BASE_URL/frontend-modules/$MODULE_ID/rollback-version" \
        -d '{"targetVersion": "1.0.0"}')
    HTTP_CODE=$(echo "$RESP" | tail -1)
    assert "POST rollback-version returns 200" "200" "$HTTP_CODE"

    # Verify rollback
    RESP=$(curl -s -w "\n%{http_code}" \
        -H "X-Tenant-Id: $TENANT" \
        "$BASE_URL/frontend-modules?hostApp=user-portal&env=DEV")
    BODY=$(echo "$RESP" | sed '$d')
    assert_contains "Version rolled back to 1.0.0" '"version":"1.0.0"' "$BODY"
fi

# ======== 10. Health check ========
echo "--- 10. Health Check ---"
if [ -n "${MODULE_ID:-}" ] && [ "$MODULE_ID" != "N/A" ]; then
    RESP=$(curl -s -w "\n%{http_code}" \
        -H "X-Tenant-Id: $TENANT" \
        -X POST "$BASE_URL/frontend-modules/$MODULE_ID/health-check")
    HTTP_CODE=$(echo "$RESP" | tail -1)
    BODY=$(echo "$RESP" | sed '$d')
    assert "POST health-check returns 200" "200" "$HTTP_CODE"
    assert_contains "Health check result contains status" '"status"' "$BODY"
fi

# ======== Summary ========
echo ""
echo "============================================"
echo " Results: $PASS passed, $FAIL failed, $TOTAL total"
echo "============================================"

if [ "$FAIL" -gt 0 ]; then
    echo -e "${RED}Some tests FAILED${NC}"
    exit 1
else
    echo -e "${GREEN}All tests PASSED${NC}"
    exit 0
fi
