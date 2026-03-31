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
echo "[0/9] Creating N8N database..."
psql -U $POSTGRES_USER -d $POSTGRES_DB -tc "SELECT 1 FROM pg_database WHERE datname = 'n8n_dev'" | grep -q 1 || psql -U $POSTGRES_USER -d $POSTGRES_DB -c "CREATE DATABASE n8n_dev OWNER $POSTGRES_USER"
echo "  N8N database 'n8n_dev' ready."

# --- Step 1: Base schemas ---
echo ""
echo "[1/9] Creating base schemas..."
for f in /docker-entrypoint-initdb.d/00-schema/01-*.sql \
         /docker-entrypoint-initdb.d/00-schema/02-*.sql \
         /docker-entrypoint-initdb.d/00-schema/03-*.sql \
         /docker-entrypoint-initdb.d/00-schema/04-*.sql \
         /docker-entrypoint-initdb.d/00-schema/05-*.sql; do
    [ -f "$f" ] && echo "  Running $(basename $f)..." && $PSQL -f "$f"
done

# --- Step 2: Incremental migrations ---
# Note: Migration 09 was intentionally skipped (no 09-*.sql file exists)
echo ""
echo "[2/9] Applying incremental migrations..."
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
         /docker-entrypoint-initdb.d/00-schema/23-*.sql; do
    [ -f "$f" ] && echo "  Running $(basename $f)..." && $PSQL -f "$f"
done

# --- Step 3: Roles, groups, admin user ---
echo ""
echo "[3/9] Creating roles, groups, and admin user..."
$PSQL -f /docker-entrypoint-initdb.d/01-admin/01-create-roles-and-groups.sql
$PSQL -f /docker-entrypoint-initdb.d/01-admin/01-create-admin-only.sql
$PSQL -f /docker-entrypoint-initdb.d/01-admin/02-init-developer-permissions.sql
$PSQL -f /docker-entrypoint-initdb.d/01-admin/03-sync-role-tables.sql
$PSQL -f /docker-entrypoint-initdb.d/01-admin/04-admin-permissions.sql

# --- Step 4: Test function unit (Digital Lending V2 EN) ---
echo ""
echo "[4/9] Loading test function unit (Digital Lending V2 EN)..."
for f in /docker-entrypoint-initdb.d/08-digital-lending-v2-en/00-*.sql \
         /docker-entrypoint-initdb.d/08-digital-lending-v2-en/01-*.sql \
         /docker-entrypoint-initdb.d/08-digital-lending-v2-en/02-*.sql \
         /docker-entrypoint-initdb.d/08-digital-lending-v2-en/03-*.sql; do
    [ -f "$f" ] && echo "  Running $(basename $f)..." && $PSQL -f "$f"
done

# --- Step 5: Simple Approval Workflow ---
# Note: 04-*.sql matches both 04-form-table-bindings.sql and 04-insert-sample-data.sql
# Alphabetical order ensures bindings run before sample data (correct order)
echo ""
echo "[5/9] Loading Simple Approval Workflow..."
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
echo "[6/9] Loading Simple Approval 12..."
for f in /docker-entrypoint-initdb.d/12-simple-approval/00-*.sql \
         /docker-entrypoint-initdb.d/12-simple-approval/01-*.sql \
         /docker-entrypoint-initdb.d/12-simple-approval/02-*.sql \
         /docker-entrypoint-initdb.d/12-simple-approval/03-*.sql; do
    [ -f "$f" ] && echo "  Running $(basename $f)..." && $PSQL -f "$f"
done

# --- Step 7: Procurement Workflow ---
echo ""
echo "[7/9] Loading Procurement Workflow..."
for f in /docker-entrypoint-initdb.d/13-procurement-workflow/00-*.sql \
         /docker-entrypoint-initdb.d/13-procurement-workflow/01-*.sql \
         /docker-entrypoint-initdb.d/13-procurement-workflow/02-*.sql \
         /docker-entrypoint-initdb.d/13-procurement-workflow/03-*.sql \
         /docker-entrypoint-initdb.d/13-procurement-workflow/04-*.sql; do
    [ -f "$f" ] && echo "  Running $(basename $f)..." && $PSQL -f "$f"
done

# --- Step 8: Travel Expense Reimbursement ---
echo ""
echo "[8/9] Loading Travel Expense Reimbursement..."
for f in /docker-entrypoint-initdb.d/14-travel-expense-reimbursement/00-*.sql \
         /docker-entrypoint-initdb.d/14-travel-expense-reimbursement/01-*.sql \
         /docker-entrypoint-initdb.d/14-travel-expense-reimbursement/02-*.sql \
         /docker-entrypoint-initdb.d/14-travel-expense-reimbursement/03-*.sql \
         /docker-entrypoint-initdb.d/14-travel-expense-reimbursement/04-*.sql; do
    [ -f "$f" ] && echo "  Running $(basename $f)..." && $PSQL -f "$f"
done

# --- Step 9: Platform capability showcase function unit ---
echo ""
echo "[9/9] Loading Platform Showcase function unit..."
for f in /docker-entrypoint-initdb.d/15-platform-showcase/00-*.sql \
         /docker-entrypoint-initdb.d/15-platform-showcase/01-*.sql \
         /docker-entrypoint-initdb.d/15-platform-showcase/02-*.sql \
         /docker-entrypoint-initdb.d/15-platform-showcase/03-*.sql \
         /docker-entrypoint-initdb.d/15-platform-showcase/04-*.sql; do
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
echo "    - Platform Showcase (full capability demo)"
echo "========================================="
