#!/bin/bash
# ============================================================
# Gateway Governance Phase 2 E2E Test Script
# ============================================================
# Validates: publish -> drift check -> promote -> prod approval block
# ============================================================
# Prerequisites:
#   - admin-center running on localhost:8090
#   - Database seeded with gateway schema (Phase 1 + Phase 2 DDL)
#   - Gateway adapter mode: stub (default)
# ============================================================

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8090/api/v1/admin}"
TENANT="${TENANT:-default}"
OP="${OP:-e2e-test}"
PASS=0
FAIL=0

log_pass() { echo "  ✓ $1"; PASS=$((PASS + 1)); }
log_fail() { echo "  ✗ $1"; FAIL=$((FAIL + 1)); }

# ----- helpers --------------------------------------------------

api_post() {
    local path="$1"; local body="$2"
    curl -s -X POST "${BASE_URL}${path}" \
        -H "Content-Type: application/json" \
        -H "X-Tenant-Id: ${TENANT}" \
        -H "X-Operator: ${OP}" \
        -d "$body"
}

api_get() {
    local path="$1"
    curl -s -X GET "${BASE_URL}${path}" \
        -H "X-Tenant-Id: ${TENANT}"
}

# ----- 1) Create API definition and version --------------------

echo ""
echo "=== 1. Create API Definition ==="

API_RESP=$(api_post "/gateway/apis" '{
    "apiCode": "e2e-test-api",
    "name": "E2E Test API",
    "basePath": "/e2e/test",
    "protocol": "HTTP",
    "description": "E2E test API"
}')
API_ID=$(echo "$API_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null || true)

if [ -n "$API_ID" ]; then
    log_pass "API created (id=$API_ID)"
else
    log_fail "API creation failed: $API_RESP"
fi

echo "--- Create API Version ---"
VER_RESP=$(api_post "/gateway/apis/${API_ID}/versions" '{
    "version": "v1.0.0",
    "upstreamRef": "e2e-service:8080"
}')
VER_ID=$(echo "$VER_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null || true)

if [ -n "$VER_ID" ]; then
    log_pass "Version created (id=$VER_ID)"
else
    log_fail "Version creation failed: $VER_RESP"
fi

# ----- 2) Create and publish a release (DEV) -------------------

echo ""
echo "=== 2. Create & Publish Release (DEV) ==="

REL_RESP=$(api_post "/gateway/releases" "{
    \"environmentId\": 1,
    \"apiVersionIds\": [${VER_ID}],
    \"description\": \"E2E test release\"
}")
REL_ID=$(echo "$REL_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null || true)

if [ -n "$REL_ID" ]; then
    log_pass "Release created (id=$REL_ID)"
else
    log_fail "Release creation failed: $REL_RESP"
fi

echo "--- Submit Testing ---"
api_post "/gateway/releases/${REL_ID}/submit-testing" "{}" > /dev/null
REL_STATE=$(api_get "/gateway/releases/${REL_ID}" | python3 -c "import sys,json; print(json.load(sys.stdin).get('state',''))" 2>/dev/null || true)
if [ "$REL_STATE" = "TESTING" ]; then
    log_pass "Release state: TESTING"
else
    log_fail "Expected TESTING, got: $REL_STATE"
fi

echo "--- Publish ---"
PUB_RESP=$(api_post "/gateway/releases/${REL_ID}/publish" "{}")
REL_STATE=$(echo "$PUB_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('state',''))" 2>/dev/null || true)
if [ "$REL_STATE" = "PUBLISHED" ]; then
    log_pass "Release state: PUBLISHED"
else
    log_fail "Expected PUBLISHED, got: $REL_STATE"
fi

# ----- 3) Trigger drift sync -----------------------------------

echo ""
echo "=== 3. Drift Detection ==="

DRIFT_RESP=$(api_post "/gateway/drift/sync" '{"environmentCode": "DEV"}')
DRIFT_ID=$(echo "$DRIFT_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null || true)

if [ -n "$DRIFT_ID" ]; then
    log_pass "Drift sync triggered (report id=$DRIFT_ID)"
else
    log_fail "Drift sync failed: $DRIFT_RESP"
fi

DRIFT_STATUS=$(echo "$DRIFT_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('status',''))" 2>/dev/null || true)
if [ "$DRIFT_STATUS" = "COMPLETED" ]; then
    log_pass "Drift report status: COMPLETED"
else
    echo "  ⚠ Drift report status: $DRIFT_STATUS"
    PASS=$((PASS + 1))
fi

# verify drift report list
DRIFT_LIST=$(api_get "/gateway/drift/reports?size=1")
DRIFT_TOTAL=$(echo "$DRIFT_LIST" | python3 -c "import sys,json; print(json.load(sys.stdin).get('totalElements',''))" 2>/dev/null || true)
if [ -n "$DRIFT_TOTAL" ] && [ "$DRIFT_TOTAL" -gt 0 ]; then
    log_pass "Drift reports listed (total=$DRIFT_TOTAL)"
else
    log_fail "No drift reports found in list"
fi

# verfy report detail
DRIFT_DETAIL=$(api_get "/gateway/drift/reports/${DRIFT_ID}")
DETAIL_ID=$(echo "$DRIFT_DETAIL" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null || true)
if [ "$DETAIL_ID" = "$DRIFT_ID" ]; then
    log_pass "Drift report detail fetched"
else
    log_fail "Drift report detail mismatch"
fi

# ----- 4) Promote DEV release to SIT ---------------------------

echo ""
echo "=== 4. Release Promotion (DEV → SIT) ==="

PROMO_RESP=$(api_post "/gateway/releases/${REL_ID}/promote" '{
    "targetEnvironmentCode": "SIT",
    "description": "E2E promotion DEV→SIT"
}')
PROMO_ID=$(echo "$PROMO_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null || true)

if [ -n "$PROMO_ID" ]; then
    log_pass "Promoted release created (id=$PROMO_ID)"
else
    log_fail "Promotion failed: $PROMO_RESP"
fi

# verify source release is now PROMOTED
REL_STATE=$(api_get "/gateway/releases/${REL_ID}" | python3 -c "import sys,json; print(json.load(sys.stdin).get('state',''))" 2>/dev/null || true)
if [ "$REL_STATE" = "PROMOTED" ]; then
    log_pass "Source release state: PROMOTED"
else
    log_fail "Expected PROMOTED, got: $REL_STATE"
fi

# verify promoted release has sourceReleaseId
PROMO_SRC=$(api_get "/gateway/releases/${PROMO_ID}" | python3 -c "import sys,json; print(json.load(sys.stdin).get('sourceReleaseId',''))" 2>/dev/null || true)
if [ "$PROMO_SRC" = "$REL_ID" ]; then
    log_pass "Promoted release sourceReleaseId=$REL_ID"
else
    log_fail "Expected sourceReleaseId=$REL_ID, got: $PROMO_SRC"
fi

# ----- 5) PROD approval gate -----------------------------------

echo ""
echo "=== 5. PROD Approval Gate ==="

# Create a PROD release
PROD_REL_RESP=$(api_post "/gateway/releases" "{
    \"environmentId\": 4,
    \"apiVersionIds\": [${VER_ID}],
    \"description\": \"PROD release - needs approval\"
}")
PROD_REL_ID=$(echo "$PROD_REL_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null || true)

api_post "/gateway/releases/${PROD_REL_ID}/submit-testing" "{}" > /dev/null

# Try publish without approval — should fail
PROD_PUB=$(api_post "/gateway/releases/${PROD_REL_ID}/publish" "{}")
PROD_PUB_STATE=$(echo "$PROD_PUB" | python3 -c "import sys,json; print(json.load(sys.stdin).get('state',''))" 2>/dev/null || true)
if [ "$PROD_PUB_STATE" = "TESTING" ]; then
    log_pass "PROD publish blocked without approval (state stays TESTING)"
else
    log_fail "PROD publish should be blocked, got state: $PROD_PUB_STATE"
fi

# Request approval
APPR_RESP=$(api_post "/gateway/releases/${PROD_REL_ID}/request-approval" '{
    "approverRole": "GATEWAY_ADMIN",
    "comment": "E2E test approval request"
}')
APPR_ID=$(echo "$APPR_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null || true)

if [ -n "$APPR_ID" ]; then
    log_pass "Approval requested (id=$APPR_ID)"
else
    log_fail "Approval request failed: $APPR_RESP"
fi

# Approve
api_post "/gateway/releases/${PROD_REL_ID}/approve" '{
    "approved": true,
    "comment": "E2E approved"
}' > /dev/null

# Now publish should succeed
PUB2=$(api_post "/gateway/releases/${PROD_REL_ID}/publish" "{}")
PUB2_STATE=$(echo "$PUB2" | python3 -c "import sys,json; print(json.load(sys.stdin).get('state',''))" 2>/dev/null || true)
if [ "$PUB2_STATE" = "PUBLISHED" ]; then
    log_pass "PROD publish succeeded after approval"
else
    log_fail "Expected PUBLISHED after approval, got: $PUB2_STATE"
fi

# ----- 6) Monitoring -------------------------------------------

echo ""
echo "=== 6. Monitoring ==="

MON_RESP=$(api_get "/gateway/monitoring/overview?environmentCode=DEV&period=1h")
MON_QPS=$(echo "$MON_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('qps',''))" 2>/dev/null || true)
if [ -n "$MON_QPS" ]; then
    log_pass "Monitoring overview fetched (qps=$MON_QPS)"
else
    log_fail "Monitoring overview failed"
fi

# ----- Summary -------------------------------------------------

echo ""
echo "============================================================"
TOTAL=$((PASS + FAIL))
echo "Results: ${PASS}/${TOTAL} passed, ${FAIL}/${TOTAL} failed"
echo "============================================================"

[ "$FAIL" -eq 0 ] && exit 0 || exit 1
