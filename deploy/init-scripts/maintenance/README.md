# Database Maintenance Scripts

This directory contains maintenance scripts for the workflow platform database.

## Available Scripts

### cleanup-all-data.sql

Comprehensive cleanup script that removes all function units, processes, tasks, and related data from the database.

**What gets deleted:**
- All Flowable task data (runtime and historical tasks)
- All Flowable process instances (runtime and historical)
- All Flowable process definitions and deployments
- All Flowable byte arrays and deployment resources
- All process instances (up_process_instance)
- All process definitions (dw_process_definitions)
- All form definitions
- All table definitions
- All field definitions
- All action definitions
- All function units (both dw_function_units and sys_function_units)
- All version records (dw_versions table)
- All related content and access records

**What gets preserved:**
- Database schema and table structures
- Version management columns and indexes (version, is_active, deployed_at, previous_version_id)
- User accounts and permissions
- Organization and role data
- Virtual groups and business units

**Usage:**

```bash
# Using PowerShell
Get-Content deploy/init-scripts/maintenance/cleanup-all-data.sql | docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev

# Using bash
cat deploy/init-scripts/maintenance/cleanup-all-data.sql | docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev
```

**When to use:**
- Before starting fresh testing
- When you need to reset all function units and processes
- When cleaning up test data
- After completing a test cycle

**Safety:**
- Runs within a transaction (can be rolled back if needed)
- Displays data counts before and after cleanup
- Provides detailed progress messages for each deletion step
- Respects foreign key constraints (deletes in correct order)
- Checks table existence before attempting deletions

**Last Verified:** 2026-02-06 - Successfully cleaned all data, confirmed 0 records in all tables

**Warning:** This operation is destructive and cannot be undone. Make sure you have backups if needed.

### cleanup-soft-deleted-users.sql

Permanently removes users that have been soft-deleted (is_deleted = true).

**Usage:**

```bash
# Using PowerShell
Get-Content cleanup-soft-deleted-users.sql | docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev

# Using bash
cat cleanup-soft-deleted-users.sql | docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev
```

### cleanup-process-data.sql

Removes completed and cancelled process instances older than a specified date.

**Usage:**

Edit the script to set the cutoff date, then run:

```bash
# Using PowerShell
Get-Content cleanup-process-data.sql | docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev

# Using bash
cat cleanup-process-data.sql | docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev
```

## Safety Features

All scripts:
- Run within transactions (can be rolled back if needed)
- Display counts before and after operations
- Show detailed progress messages
- Check for table existence before attempting deletions
- Respect foreign key constraints

## Verification

After running any cleanup script, you can verify the results:

```sql
-- Check function units
SELECT COUNT(*) FROM dw_function_units;

-- Check process definitions
SELECT COUNT(*) FROM dw_process_definitions;

-- Check process instances
SELECT COUNT(*) FROM up_process_instance;

-- Check all related tables
SELECT 
    'dw_function_units' as table_name, COUNT(*) as count FROM dw_function_units
UNION ALL
SELECT 'dw_process_definitions', COUNT(*) FROM dw_process_definitions
UNION ALL
SELECT 'up_process_instance', COUNT(*) FROM up_process_instance
UNION ALL
SELECT 'dw_form_definitions', COUNT(*) FROM dw_form_definitions
UNION ALL
SELECT 'dw_table_definitions', COUNT(*) FROM dw_table_definitions
UNION ALL
SELECT 'dw_action_definitions', COUNT(*) FROM dw_action_definitions;
```

## Troubleshooting

### Foreign Key Constraint Errors

If you encounter foreign key constraint errors, the script will automatically rollback. Check the error message to identify which table has dependencies and update the script to delete those records first.

### Permission Errors

Make sure you're running the script with a user that has DELETE privileges on all tables.

### Connection Issues

Verify that:
1. PostgreSQL container is running: `docker ps | grep postgres`
2. Database exists: `docker exec -i platform-postgres-dev psql -U platform_dev -l`
3. Credentials are correct

## Best Practices

1. **Always backup before cleanup**: Use `pg_dump` to create a backup
2. **Test in development first**: Never run cleanup scripts directly in production
3. **Review the script**: Understand what will be deleted before running
4. **Check dependencies**: Ensure no critical data will be lost
5. **Verify results**: Always check counts after cleanup

## Creating New Cleanup Scripts

When creating new cleanup scripts:

1. Use transactions (BEGIN/COMMIT)
2. Add progress messages (RAISE NOTICE)
3. Check table existence before operations
4. Delete in correct order (respect foreign keys)
5. Display before/after counts
6. Include rollback on error
7. Document what gets deleted

Example template:

```sql
BEGIN;

-- Display current counts
DO $$
BEGIN
    RAISE NOTICE 'Starting cleanup...';
END $$;

-- Perform deletions
DO $$
BEGIN
    DELETE FROM your_table;
    RAISE NOTICE 'Deleted records from your_table';
END $$;

-- Display final counts
DO $$
BEGIN
    RAISE NOTICE 'Cleanup completed';
END $$;

COMMIT;
```
