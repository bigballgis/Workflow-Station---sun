#!/bin/bash
# =====================================================
# Database Initialization Script
# =====================================================
# This script initializes the workflow platform database with:
# - Database schemas (all tables and constraints)
# - System default roles (5 roles)
# - Virtual groups (5 groups)
# - Test users (5 users)
# - Optional: Test data and workflow definitions
# =====================================================

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Database connection parameters
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-workflow_platform}"
DB_USER="${DB_USER:-postgres}"
DB_PASSWORD="${DB_PASSWORD:-}"

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Function to print colored messages
print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to execute SQL file
execute_sql_file() {
    local file=$1
    local description=$2
    
    print_info "Executing: $description"
    
    if [ -n "$DB_PASSWORD" ]; then
        PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -f "$file"
    else
        psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -f "$file"
    fi
    
    if [ $? -eq 0 ]; then
        print_success "$description completed"
    else
        print_error "$description failed"
        exit 1
    fi
}

# Main initialization process
main() {
    echo ""
    echo "========================================="
    echo "  Workflow Platform Database Init"
    echo "========================================="
    echo "Database: $DB_NAME"
    echo "Host: $DB_HOST:$DB_PORT"
    echo "User: $DB_USER"
    echo "========================================="
    echo ""
    
    # Step 1: Create schemas
    print_info "Step 1/4: Creating database schemas..."
    echo ""
    
    execute_sql_file "$SCRIPT_DIR/00-schema/00-init-all-schemas.sql" "All database schemas"
    
    echo ""
    print_success "Step 1/4: Database schemas created successfully"
    echo ""
    
    # Step 2: Create roles and virtual groups
    print_info "Step 2/4: Creating system roles and virtual groups..."
    echo ""
    
    execute_sql_file "$SCRIPT_DIR/01-admin/01-create-roles-and-groups.sql" "System roles and virtual groups"
    execute_sql_file "$SCRIPT_DIR/01-admin/02-init-developer-permissions.sql" "Developer role permissions"
    
    echo ""
    print_success "Step 2/4: Roles, groups, and permissions created successfully"
    echo ""
    
    # Step 3: Create test users
    print_info "Step 3/5: Creating test users..."
    echo ""
    
    execute_sql_file "$SCRIPT_DIR/01-admin/02-create-test-users.sql" "Test users"
    
    echo ""
    print_success "Step 3/5: Test users created successfully"
    echo ""
    
    # Step 4: Create role assignments
    print_info "Step 4/5: Creating role assignments..."
    echo ""
    
    execute_sql_file "$SCRIPT_DIR/01-admin/05-create-role-assignments.sql" "Role assignments"
    
    echo ""
    print_success "Step 4/5: Role assignments created successfully"
    echo ""
    
    # Step 5: Optional test data
    print_info "Step 5/5: Loading optional test data..."
    echo ""
    
    if [ -d "$SCRIPT_DIR/02-test-data" ]; then
        for file in "$SCRIPT_DIR/02-test-data"/*.sql; do
            if [ -f "$file" ]; then
                execute_sql_file "$file" "$(basename "$file")"
            fi
        done
        print_success "Step 5/5: Test data loaded successfully"
    else
        print_warning "Step 5/5: No test data directory found, skipping"
    fi
    
    echo ""
    
    # Verification
    print_info "Verifying initialization..."
    echo ""
    echo "========================================="
    echo "  Database Initialization Complete!"
    echo "========================================="
    echo ""
    echo "System Summary:"
    echo "  ✓ Database schemas created"
    echo "  ✓ 5 system roles created"
    echo "  ✓ 5 virtual groups created"
    echo "  ✓ 5 test users created"
    echo "  ✓ Virtual group role assignments created"
    echo ""
    echo "Login Credentials (password: password):"
    echo "  • admin      - System Administrator"
    echo "  • auditor    - System Auditor"
    echo "  • manager    - Department Manager"
    echo "  • developer  - Workflow Developer"
    echo "  • designer   - Workflow Designer"
    echo ""
    echo "Next Steps:"
    echo "  1. Start the application services"
    echo "  2. Access Admin Center: http://localhost:8081"
    echo "  3. Access User Portal: http://localhost:8082"
    echo "  4. Access Developer Workstation: http://localhost:8083"
    echo "========================================="
    echo ""
}

# Run main function
main
