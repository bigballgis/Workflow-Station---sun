#!/bin/bash
# =====================================================
# Database Initialization (Docker entrypoint)
# =====================================================
# Auto-executed by PostgreSQL on first container start.
# Creates: schemas → roles/groups → admin user → single demo function unit
# =====================================================

set -e

PSQL="psql -v ON_ERROR_STOP=1 --username $POSTGRES_USER --dbname $POSTGRES_DB"

echo "========================================="
echo "  Database Initialization Starting..."
echo "========================================="

# --- Step 0: Create N8N database ---
echo ""
echo "[0/6] Creating N8N database..."
psql -U $POSTGRES_USER -d $POSTGRES_DB -tc "SELECT 1 FROM pg_database WHERE datname = 'n8n_dev'" | grep -q 1 || psql -U $POSTGRES_USER -d $POSTGRES_DB -c "CREATE DATABASE n8n_dev OWNER $POSTGRES_USER"
echo "  N8N database 'n8n_dev' ready."

# --- Step 1: Base schemas ---
echo ""
echo "[1/6] Creating base schemas..."
for f in /docker-entrypoint-initdb.d/00-schema/01-*.sql \
         /docker-entrypoint-initdb.d/00-schema/02-*.sql \
         /docker-entrypoint-initdb.d/00-schema/03-*.sql \
         /docker-entrypoint-initdb.d/00-schema/04-*.sql \
         /docker-entrypoint-initdb.d/00-schema/05-*.sql; do
    [ -f "$f" ] && echo "  Running $(basename $f)..." && $PSQL -f "$f"
done

# --- Step 2: Incremental migrations ---
# Note: 09-* reserved (intentionally unused). Flowable tables are managed by workflow-engine-core
# (flowable.database-schema-update=true in docker profile) and can be repaired via 99-maintenance/01-repair-flowable-schema.sql
echo ""
echo "[2/6] Applying incremental migrations..."
for f in /docker-entrypoint-initdb.d/00-schema/06-*.sql \
         /docker-entrypoint-initdb.d/00-schema/07-*.sql \
         /docker-entrypoint-initdb.d/00-schema/08-*.sql \
         /docker-entrypoint-initdb.d/00-schema/10-*.sql \
         /docker-entrypoint-initdb.d/00-schema/11-*.sql \
         /docker-entrypoint-initdb.d/00-schema/12-*.sql \
         /docker-entrypoint-initdb.d/00-schema/13-*.sql \
         /docker-entrypoint-initdb.d/00-schema/15-*.sql \
         /docker-entrypoint-initdb.d/00-schema/16-*.sql \
         /docker-entrypoint-initdb.d/00-schema/17-*.sql \
         /docker-entrypoint-initdb.d/00-schema/18-*.sql \
         /docker-entrypoint-initdb.d/00-schema/19-*.sql \
         /docker-entrypoint-initdb.d/00-schema/20-*.sql \
         /docker-entrypoint-initdb.d/00-schema/21-*.sql \
         /docker-entrypoint-initdb.d/00-schema/22-*.sql \
         /docker-entrypoint-initdb.d/00-schema/23-*.sql \
         /docker-entrypoint-initdb.d/00-schema/24-*.sql \
         /docker-entrypoint-initdb.d/00-schema/25-*.sql \
         /docker-entrypoint-initdb.d/00-schema/26-*.sql \
         /docker-entrypoint-initdb.d/00-schema/27-*.sql \
         /docker-entrypoint-initdb.d/00-schema/28-*.sql \
         /docker-entrypoint-initdb.d/00-schema/29-*.sql \
         /docker-entrypoint-initdb.d/00-schema/30-*.sql \
         /docker-entrypoint-initdb.d/00-schema/31-*.sql \
         /docker-entrypoint-initdb.d/00-schema/32-*.sql \
         /docker-entrypoint-initdb.d/00-schema/33-*.sql \
         /docker-entrypoint-initdb.d/00-schema/34-*.sql \
         /docker-entrypoint-initdb.d/00-schema/35-*.sql \
         /docker-entrypoint-initdb.d/00-schema/36-*.sql \
         /docker-entrypoint-initdb.d/00-schema/37-*.sql \
         /docker-entrypoint-initdb.d/00-schema/38-*.sql \
         /docker-entrypoint-initdb.d/00-schema/39-*.sql \
         /docker-entrypoint-initdb.d/00-schema/40-*.sql \
         /docker-entrypoint-initdb.d/00-schema/41-*.sql \
         /docker-entrypoint-initdb.d/00-schema/42-*.sql \
         /docker-entrypoint-initdb.d/00-schema/43-*.sql \
         /docker-entrypoint-initdb.d/00-schema/44-*.sql; do
    [ -f "$f" ] && echo "  Running $(basename $f)..." && $PSQL -f "$f"
done

# --- Step 3: Roles, groups, admin user ---
echo ""
echo "[3/6] Creating roles, groups, and admin user..."
$PSQL -f /docker-entrypoint-initdb.d/01-admin/01-create-roles-and-groups.sql
$PSQL -f /docker-entrypoint-initdb.d/01-admin/01-create-admin-only.sql
$PSQL -f /docker-entrypoint-initdb.d/01-admin/02-init-developer-permissions.sql
$PSQL -f /docker-entrypoint-initdb.d/01-admin/03-sync-role-tables.sql
$PSQL -f /docker-entrypoint-initdb.d/01-admin/04-admin-permissions.sql
$PSQL -f /docker-entrypoint-initdb.d/01-admin/05-e2e-test-users-and-business-units.sql

# --- Step 4: Wipe function units (dev catalog + deployed catalog), then seed Digital Lending EN only ---
echo ""
echo "[4/6] Wiping all function units (developer + deployed catalog)..."
if [ -f /docker-entrypoint-initdb.d/99-maintenance/00-wipe-all-function-units.sql ]; then
  $PSQL -f /docker-entrypoint-initdb.d/99-maintenance/00-wipe-all-function-units.sql
else
  echo "  (wipe script missing — skip)"
fi

echo ""
echo "[5a/6] Loading Platform Showcase (company promo demo)..."
for f in /docker-entrypoint-initdb.d/15-platform-showcase/00-*.sql \
         /docker-entrypoint-initdb.d/15-platform-showcase/01-*.sql \
         /docker-entrypoint-initdb.d/15-platform-showcase/02-*.sql \
         /docker-entrypoint-initdb.d/15-platform-showcase/03-*.sql \
         /docker-entrypoint-initdb.d/15-platform-showcase/04-*.sql \
         /docker-entrypoint-initdb.d/15-platform-showcase/05-*.sql; do
  [ -f "$f" ] && echo "  Running $(basename $f)..." && $PSQL -f "$f"
done

echo ""
echo "[5b/6] Loading Digital Lending V2 EN..."
for f in /docker-entrypoint-initdb.d/08-digital-lending-v2-en/00-*.sql \
         /docker-entrypoint-initdb.d/08-digital-lending-v2-en/01-*.sql \
         /docker-entrypoint-initdb.d/08-digital-lending-v2-en/02-*.sql \
         /docker-entrypoint-initdb.d/08-digital-lending-v2-en/03-*.sql \
         /docker-entrypoint-initdb.d/08-digital-lending-v2-en/04-*.sql; do
    [ -f "$f" ] && echo "  Running $(basename $f)..." && $PSQL -f "$f"
done

if [ -f /docker-entrypoint-initdb.d/08-digital-lending-v2-en/05-e2e-virtual-group-members.sql ]; then
  echo ""
  echo "  Running 05-e2e-virtual-group-members.sql..."
  $PSQL -f /docker-entrypoint-initdb.d/08-digital-lending-v2-en/05-e2e-virtual-group-members.sql
fi

echo ""
echo "[5c/6] Loading Meeting Participant Info Collection..."
for f in /docker-entrypoint-initdb.d/16-meeting-participant-collection/00-*.sql \
         /docker-entrypoint-initdb.d/16-meeting-participant-collection/01-*.sql \
         /docker-entrypoint-initdb.d/16-meeting-participant-collection/02-*.sql \
         /docker-entrypoint-initdb.d/16-meeting-participant-collection/03-*.sql \
         /docker-entrypoint-initdb.d/16-meeting-participant-collection/04-*.sql \
         /docker-entrypoint-initdb.d/16-meeting-participant-collection/05-*.sql \
         /docker-entrypoint-initdb.d/16-meeting-participant-collection/06-*.sql; do
  [ -f "$f" ] && echo "  Running $(basename $f)..." && $PSQL -f "$f"
done

echo ""
echo "[5d/6] Loading Function Unit Multi-Instance Subtask Demo..."
if [ -f /docker-entrypoint-initdb.d/17-Multi-Instance-Subtask-Demo/00-init-kk.sql ]; then
  echo "  Running 00-init-kk.sql..."
  $PSQL -f /docker-entrypoint-initdb.d/17-Multi-Instance-Subtask-Demo/00-init-kk.sql
  if [ -f /docker-entrypoint-initdb.d/17-Multi-Instance-Subtask-Demo/03-set-main-table-request-id-config.sql ]; then
    echo "  Running 03-set-main-table-request-id-config.sql..."
    $PSQL -f /docker-entrypoint-initdb.d/17-Multi-Instance-Subtask-Demo/03-set-main-table-request-id-config.sql
  fi
else
  echo "  ERROR: Multi-Instance Subtask Demo init script not found at /docker-entrypoint-initdb.d/17-Multi-Instance-Subtask-Demo/00-init-kk.sql"
  exit 1
fi

echo ""
echo "[5e/6] Loading MCY Debit Card..."
if [ -f /docker-entrypoint-initdb.d/18-MCY/init.sql ]; then
  echo "  Running init.sql..."
  $PSQL -f /docker-entrypoint-initdb.d/18-MCY/init.sql
else
  echo "  ERROR: MCY Debit Card init script not found at /docker-entrypoint-initdb.d/18-MCY/init.sql"
  exit 1
fi

# --- Step 5f: Post-seed alignment ---
# Scripts under 90-post-seed/ run on every init, AFTER all seed packages above.
# They are not DDL and not seed data -- they reconcile state introduced by the
# seed step (e.g. pushing BIGSERIAL sequences past explicit-id seed rows so the
# next JPA `GenerationType.IDENTITY` insert does not collide on the primary key).
echo ""
echo "[5f/6] Running post-seed alignment scripts (90-post-seed/)..."
for f in /docker-entrypoint-initdb.d/90-post-seed/00-*.sql \
         /docker-entrypoint-initdb.d/90-post-seed/01-*.sql \
         /docker-entrypoint-initdb.d/90-post-seed/02-*.sql \
         /docker-entrypoint-initdb.d/90-post-seed/03-*.sql \
         /docker-entrypoint-initdb.d/90-post-seed/04-*.sql \
         /docker-entrypoint-initdb.d/90-post-seed/05-*.sql; do
  [ -f "$f" ] && echo "  Running $(basename $f)..." && $PSQL -f "$f"
done

echo ""
echo "[6/6] Seed scripts finished."

echo ""
echo "========================================="
echo "  Database Initialization Complete!"
echo "========================================="
echo "  Login: admin / admin123  (test: 44027893 / admin123)"
echo "  Change password after first login!"
echo "  Demo function units: Platform Showcase fu-20260403-a1b2c4; Digital Lending System V2 (EN) fu-20260403-a1b2c6; Meeting Participant Info Collection fu-20260403-a1b2c5; Multi-Instance Subtask Demo fu-20260422-23tfag; MCY Debit Card fu-20260505-thwmut"
echo "  E2E users (password=password): e2e_zhangwei e2e_lina e2e_wangfang e2e_zhaomin e2e_sunqiang e2e_zhoujie e2e_wugang"
echo "  (Re-seed: run init-scripts/99-maintenance/00-wipe-all-function-units.sql then reload 08-, 16-, 17-, and 18- scripts if needed.)"
echo "========================================="
