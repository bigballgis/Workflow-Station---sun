#!/bin/bash
# =====================================================
# Database Initialization (Docker entrypoint)
# =====================================================
# Auto-executed by PostgreSQL on first container start.
# Creates: schemas → roles/groups → admin user → test function unit
# =====================================================

set -e

PSQL="psql -v ON_ERROR_STOP=1 --username $POSTGRES_USER --dbname $POSTGRES_DB"

echo "========================================="
echo "  Database Initialization Starting..."
echo "========================================="

# --- Step 0: Create N8N database ---
echo ""
echo "[0/8] Creating N8N database..."
psql -U $POSTGRES_USER -d $POSTGRES_DB -tc "SELECT 1 FROM pg_database WHERE datname = 'n8n_dev'" | grep -q 1 || psql -U $POSTGRES_USER -d $POSTGRES_DB -c "CREATE DATABASE n8n_dev OWNER $POSTGRES_USER"
echo "  N8N database 'n8n_dev' ready."

# --- Step 1: Base schemas ---
echo ""
echo "[1/8] Creating base schemas..."
for f in /docker-entrypoint-initdb.d/00-schema/01-*.sql \
         /docker-entrypoint-initdb.d/00-schema/02-*.sql \
         /docker-entrypoint-initdb.d/00-schema/03-*.sql \
         /docker-entrypoint-initdb.d/00-schema/04-*.sql \
         /docker-entrypoint-initdb.d/00-schema/05-*.sql; do
    [ -f "$f" ] && echo "  Running $(basename $f)..." && $PSQL -f "$f"
done

# --- Step 2: Incremental migrations ---
echo ""
echo "[2/8] Applying incremental migrations..."
for f in /docker-entrypoint-initdb.d/00-schema/06-*.sql \
         /docker-entrypoint-initdb.d/00-schema/07-*.sql \
         /docker-entrypoint-initdb.d/00-schema/08-*.sql \
         /docker-entrypoint-initdb.d/00-schema/10-*.sql \
         /docker-entrypoint-initdb.d/00-schema/11-*.sql \
         /docker-entrypoint-initdb.d/00-schema/12-*.sql \
         /docker-entrypoint-initdb.d/00-schema/13-*.sql \
         /docker-entrypoint-initdb.d/00-schema/15-*.sql; do
    [ -f "$f" ] && echo "  Running $(basename $f)..." && $PSQL -f "$f"
done

# --- Step 3: Roles, groups, admin user ---
echo ""
echo "[3/8] Creating roles, groups, and admin user..."
$PSQL -f /docker-entrypoint-initdb.d/01-admin/01-create-roles-and-groups.sql
$PSQL -f /docker-entrypoint-initdb.d/01-admin/01-create-admin-only.sql
$PSQL -f /docker-entrypoint-initdb.d/01-admin/02-init-developer-permissions.sql
$PSQL -f /docker-entrypoint-initdb.d/01-admin/03-sync-role-tables.sql
$PSQL -f /docker-entrypoint-initdb.d/01-admin/04-admin-permissions.sql

# --- Step 4: Test function unit (Digital Lending V2 EN) ---
echo ""
echo "[4/8] Loading test function unit (Digital Lending V2 EN)..."
for f in /docker-entrypoint-initdb.d/08-digital-lending-v2-en/00-*.sql \
         /docker-entrypoint-initdb.d/08-digital-lending-v2-en/01-*.sql \
         /docker-entrypoint-initdb.d/08-digital-lending-v2-en/02-*.sql \
         /docker-entrypoint-initdb.d/08-digital-lending-v2-en/03-*.sql; do
    [ -f "$f" ] && echo "  Running $(basename $f)..." && $PSQL -f "$f"
done

# --- Step 5: Simple Approval Workflow ---
echo ""
echo "[5/8] Loading Simple Approval Workflow..."
for f in /docker-entrypoint-initdb.d/10-simple-approval/00-*.sql \
         /docker-entrypoint-initdb.d/10-simple-approval/01-*.sql \
         /docker-entrypoint-initdb.d/10-simple-approval/02-*.sql \
         /docker-entrypoint-initdb.d/10-simple-approval/03-*.sql \
         /docker-entrypoint-initdb.d/10-simple-approval/04-*.sql \
         /docker-entrypoint-initdb.d/10-simple-approval/05-*.sql; do
    [ -f "$f" ] && echo "  Running $(basename $f)..." && $PSQL -f "$f"
done

# --- Step 6: Simple Approval 12 ---
echo ""
echo "[6/8] Loading Simple Approval 12..."
for f in /docker-entrypoint-initdb.d/12-simple-approval/00-*.sql \
         /docker-entrypoint-initdb.d/12-simple-approval/01-*.sql \
         /docker-entrypoint-initdb.d/12-simple-approval/02-*.sql \
         /docker-entrypoint-initdb.d/12-simple-approval/03-*.sql; do
    [ -f "$f" ] && echo "  Running $(basename $f)..." && $PSQL -f "$f"
done

# --- Step 7: Procurement Workflow ---
echo ""
echo "[7/8] Loading Procurement Workflow..."
for f in /docker-entrypoint-initdb.d/13-procurement-workflow/00-*.sql \
         /docker-entrypoint-initdb.d/13-procurement-workflow/01-*.sql \
         /docker-entrypoint-initdb.d/13-procurement-workflow/02-*.sql \
         /docker-entrypoint-initdb.d/13-procurement-workflow/03-*.sql; do
    [ -f "$f" ] && echo "  Running $(basename $f)..." && $PSQL -f "$f"
done

# --- Step 8: Travel Expense Reimbursement ---
echo ""
echo "[8/8] Loading Travel Expense Reimbursement..."
for f in /docker-entrypoint-initdb.d/14-travel-expense-reimbursement/00-*.sql \
         /docker-entrypoint-initdb.d/14-travel-expense-reimbursement/01-*.sql \
         /docker-entrypoint-initdb.d/14-travel-expense-reimbursement/02-*.sql \
         /docker-entrypoint-initdb.d/14-travel-expense-reimbursement/03-*.sql \
         /docker-entrypoint-initdb.d/14-travel-expense-reimbursement/04-*.sql; do
    [ -f "$f" ] && echo "  Running $(basename $f)..." && $PSQL -f "$f"
done

echo ""
echo "========================================="
echo "  Database Initialization Complete!"
echo "========================================="
echo "  Login: admin / password"
echo "  Change password after first login!"
echo "  Test workflows loaded:"
echo "    - Digital Lending V2 EN"
echo "    - Simple Approval"
echo "    - Simple Approval 12"
echo "    - Procurement Workflow"
echo "    - Travel Expense Reimbursement"
echo "========================================="
