# Function Unit Versioning Migration - Quick Start Guide

## Overview

This migration adds version tracking to function units, enabling automatic versioning, rollback capabilities, and process isolation.

## Quick Start

### Option 1: Using PowerShell (Windows/Linux/Mac)

```powershell
# Run the migration
.\run-versioning-migration.ps1

# Test the migration (without applying changes)
.\run-versioning-migration.ps1 -Test

# Rollback the migration
.\run-versioning-migration.ps1 -Rollback

# Custom database connection
.\run-versioning-migration.ps1 -DbHost "localhost" -DbPort "5432" -DbName "workflow_platform_dev" -DbUser "postgres"
```

### Option 2: Using Bash (Linux/Mac)

```bash
# Make script executable (first time only)
chmod +x run-versioning-migration.sh

# Run the migration
./run-versioning-migration.sh

# Test the migration (without applying changes)
./run-versioning-migration.sh --test

# Rollback the migration
./run-versioning-migration.sh --rollback

# Custom database connection
./run-versioning-migration.sh --host localhost --port 5432 --dbname workflow_platform_dev --user postgres
```

### Option 3: Manual Execution with psql

```bash
# Set password (optional)
export PGPASSWORD=your_password

# Run schema migration
psql -U postgres -d workflow_platform_dev -f 08-add-function-unit-versioning.sql

# Run data initialization
psql -U postgres -d workflow_platform_dev -f 09-initialize-function-unit-versions.sql

# Test the migration
psql -U postgres -d workflow_platform_dev -f test-versioning-migration.sql
```

## What Gets Changed

### Tables Modified

1. **dw_function_units**
   - Added: `version`, `is_active`, `deployed_at`, `previous_version_id`
   - Indexes: version queries, active version queries

2. **sys_function_units**
   - Added: `is_active`, `deployed_at`, `previous_version_id`
   - Indexes: version queries, active version queries

3. **dw_process_definitions**
   - Added: `function_unit_version_id`
   - Links process definitions to specific function unit versions

4. **up_process_instance**
   - Added: `function_unit_version_id`
   - Links process instances to specific function unit versions

### Data Changes

- All existing function units set to version `1.0.0`
- All existing function units marked as active
- All existing process definitions linked to their function unit versions

## Verification

After running the migration, verify the changes:

```sql
-- Check table structure
\d dw_function_units
\d sys_function_units

-- Check existing data
SELECT name, version, is_active, deployed_at 
FROM dw_function_units 
ORDER BY name;

-- Check indexes
SELECT indexname, tablename 
FROM pg_indexes 
WHERE tablename IN ('dw_function_units', 'sys_function_units')
  AND (indexname LIKE '%version%' OR indexname LIKE '%active%')
ORDER BY tablename, indexname;
```

## Rollback

If you need to undo the migration:

```powershell
# PowerShell
.\run-versioning-migration.ps1 -Rollback

# Bash
./run-versioning-migration.sh --rollback
```

**Warning**: Rollback will remove all versioning columns and reset data to defaults.

## Troubleshooting

### Connection Issues

If you get connection errors:
1. Check PostgreSQL is running: `pg_isready -h localhost -p 5432`
2. Verify database exists: `psql -U postgres -l | grep workflow_platform_dev`
3. Check credentials are correct

### Permission Issues

If you get permission errors:
1. Ensure user has CREATE and ALTER privileges
2. Try connecting as superuser (postgres)

### Script Not Found

If scripts are not found:
1. Ensure you're in the correct directory: `cd deploy/init-scripts/00-schema`
2. Check files exist: `ls -la *.sql`

### Migration Already Applied

If migration was already applied:
- The scripts use `IF NOT EXISTS` and `ADD COLUMN IF NOT EXISTS`
- Safe to run multiple times
- Will skip already-applied changes

## Next Steps

After successful migration:

1. **Update Application Code**
   - Implement version service for automatic version generation
   - Update deployment service to create new versions
   - Update UI to display version information

2. **Test Versioning Features**
   - Deploy a new version of a function unit
   - Verify version history is tracked
   - Test rollback functionality

3. **Monitor Performance**
   - Check query performance with new indexes
   - Monitor version table growth
   - Adjust indexes if needed

## Support

For detailed information:
- Full documentation: `VERSIONING_MIGRATION_README.md`
- Design document: `.kiro/specs/function-unit-versioned-deployment/design.md`
- Requirements: `.kiro/specs/function-unit-versioned-deployment/requirements.md`

## Files Created

- `08-add-function-unit-versioning.sql` - Schema migration
- `08-add-function-unit-versioning-rollback.sql` - Schema rollback
- `09-initialize-function-unit-versions.sql` - Data initialization
- `09-initialize-function-unit-versions-rollback.sql` - Data rollback
- `test-versioning-migration.sql` - Test script
- `run-versioning-migration.ps1` - PowerShell runner
- `run-versioning-migration.sh` - Bash runner
- `VERSIONING_MIGRATION_README.md` - Full documentation
- `VERSIONING_MIGRATION_QUICK_START.md` - This file
