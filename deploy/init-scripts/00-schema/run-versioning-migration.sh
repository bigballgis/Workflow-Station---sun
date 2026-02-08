#!/bin/bash
# =====================================================
# Run Function Unit Versioning Migration
# Executes the versioning migration scripts in the correct order
# =====================================================

# Default values
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-workflow_platform_dev}"
DB_USER="${DB_USER:-postgres}"
ROLLBACK=false
TEST=false

# Color output functions
print_success() {
    echo -e "\033[0;32m$1\033[0m"
}

print_info() {
    echo -e "\033[0;36m$1\033[0m"
}

print_warning() {
    echo -e "\033[0;33m$1\033[0m"
}

print_error() {
    echo -e "\033[0;31m$1\033[0m"
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --host)
            DB_HOST="$2"
            shift 2
            ;;
        --port)
            DB_PORT="$2"
            shift 2
            ;;
        --dbname)
            DB_NAME="$2"
            shift 2
            ;;
        --user)
            DB_USER="$2"
            shift 2
            ;;
        --rollback)
            ROLLBACK=true
            shift
            ;;
        --test)
            TEST=true
            shift
            ;;
        --help)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --host HOST       Database host (default: localhost)"
            echo "  --port PORT       Database port (default: 5432)"
            echo "  --dbname NAME     Database name (default: workflow_platform_dev)"
            echo "  --user USER       Database user (default: postgres)"
            echo "  --rollback        Rollback the migration"
            echo "  --test            Run migration tests"
            echo "  --help            Show this help message"
            echo ""
            echo "Environment variables:"
            echo "  PGPASSWORD        PostgreSQL password (will prompt if not set)"
            exit 0
            ;;
        *)
            print_error "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Get script directory
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Prompt for password if not set
if [ -z "$PGPASSWORD" ]; then
    read -s -p "Enter PostgreSQL password for user '$DB_USER': " PGPASSWORD
    echo ""
    export PGPASSWORD
fi

print_info "=========================================="
print_info "Function Unit Versioning Migration"
print_info "=========================================="
print_info "Database: $DB_NAME"
print_info "Host: $DB_HOST:$DB_PORT"
print_info "User: $DB_USER"
echo ""

if [ "$TEST" = true ]; then
    # Run test script
    print_info "Running migration tests..."
    TEST_SCRIPT="$SCRIPT_DIR/test-versioning-migration.sql"
    
    if [ ! -f "$TEST_SCRIPT" ]; then
        print_error "Test script not found: $TEST_SCRIPT"
        exit 1
    fi
    
    if psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -f "$TEST_SCRIPT" > /dev/null 2>&1; then
        print_success "✓ All tests passed!"
        echo ""
        print_info "The migration scripts are ready to be applied."
        print_info "Run this script without --test flag to apply the migration."
    else
        print_error "✗ Tests failed!"
        exit 1
    fi
elif [ "$ROLLBACK" = true ]; then
    # Rollback migration
    print_warning "=========================================="
    print_warning "WARNING: ROLLBACK OPERATION"
    print_warning "=========================================="
    print_warning "This will remove all versioning columns and data!"
    echo ""
    read -p "Are you sure you want to rollback? (yes/no): " confirmation
    
    if [ "$confirmation" != "yes" ]; then
        print_info "Rollback cancelled."
        exit 0
    fi
    
    print_info "Rolling back data initialization..."
    ROLLBACK_DATA_SCRIPT="$SCRIPT_DIR/09-initialize-function-unit-versions-rollback.sql"
    
    if ! psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -f "$ROLLBACK_DATA_SCRIPT"; then
        print_error "✗ Data rollback failed!"
        exit 1
    fi
    print_success "✓ Data rollback completed"
    
    print_info "Rolling back schema changes..."
    ROLLBACK_SCHEMA_SCRIPT="$SCRIPT_DIR/08-add-function-unit-versioning-rollback.sql"
    
    if ! psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -f "$ROLLBACK_SCHEMA_SCRIPT"; then
        print_error "✗ Schema rollback failed!"
        exit 1
    fi
    print_success "✓ Schema rollback completed"
    
    echo ""
    print_success "=========================================="
    print_success "Rollback completed successfully!"
    print_success "=========================================="
else
    # Forward migration
    print_info "Step 1: Applying schema changes..."
    SCHEMA_SCRIPT="$SCRIPT_DIR/08-add-function-unit-versioning.sql"
    
    if [ ! -f "$SCHEMA_SCRIPT" ]; then
        print_error "Schema script not found: $SCHEMA_SCRIPT"
        exit 1
    fi
    
    if ! psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -f "$SCHEMA_SCRIPT"; then
        print_error "✗ Schema migration failed!"
        exit 1
    fi
    print_success "✓ Schema changes applied"
    
    echo ""
    print_info "Step 2: Initializing version data..."
    DATA_SCRIPT="$SCRIPT_DIR/09-initialize-function-unit-versions.sql"
    
    if [ ! -f "$DATA_SCRIPT" ]; then
        print_error "Data script not found: $DATA_SCRIPT"
        exit 1
    fi
    
    if ! psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -f "$DATA_SCRIPT"; then
        print_error "✗ Data initialization failed!"
        exit 1
    fi
    print_success "✓ Version data initialized"
    
    echo ""
    print_success "=========================================="
    print_success "Migration completed successfully!"
    print_success "=========================================="
    echo ""
    print_info "Next steps:"
    print_info "1. Verify the migration with: ./run-versioning-migration.sh --test"
    print_info "2. Review the changes in your database"
    print_info "3. Update application code to use versioning features"
    echo ""
    print_info "For more information, see: VERSIONING_MIGRATION_README.md"
fi

# Clear password from environment
unset PGPASSWORD
