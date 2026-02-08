# Function Unit Versioning Migration

## Overview

This migration adds version tracking capabilities to function units, enabling:
- Automatic version generation for each deployment
- Version history tracking
- Process isolation (processes bound to specific versions)
- Rollback capabilities
- Backward compatibility with existing function units

## Migration Scripts

### 1. Schema Migration
**File**: `08-add-function-unit-versioning.sql`

Adds the following columns to support versioning:

#### dw_function_units
- `version` (VARCHAR(20)): Semantic version number (MAJOR.MINOR.PATCH)
- `is_active` (BOOLEAN): Whether this version is currently active
- `deployed_at` (TIMESTAMP): When this version was deployed
- `previous_version_id` (BIGINT): Reference to the previous version

#### sys_function_units
- `is_active` (BOOLEAN): Whether this version is currently active
- `deployed_at` (TIMESTAMP): When this version was deployed
- `previous_version_id` (VARCHAR(64)): Reference to the previous version
- Note: `version` column already exists in this table

#### dw_process_definitions
- `function_unit_version_id` (BIGINT): Links to specific function unit version

#### up_process_instance
- `function_unit_version_id` (BIGINT): Links to specific function unit version

**Indexes Created**:
- `idx_dw_function_unit_version` - For version queries
- `idx_dw_function_unit_active` - For active version queries
- `idx_dw_function_unit_deployed_at` - For sorting by deployment time
- `idx_sys_function_unit_version` - For version queries
- `idx_sys_function_unit_active` - For active version queries
- `idx_sys_function_unit_deployed_at` - For sorting by deployment time
- `idx_dw_process_def_version` - For process definition version queries
- `idx_up_process_instance_version` - For process instance version queries

**Foreign Keys Created**:
- `fk_dw_function_unit_previous_version` - Links to previous version
- `fk_sys_function_unit_previous_version` - Links to previous version
- `fk_dw_process_def_function_unit_version` - Links to function unit version

### 2. Data Initialization
**File**: `09-initialize-function-unit-versions.sql`

Initializes existing data for backward compatibility:
- Sets all existing function units to version `1.0.0`
- Marks all existing function units as active
- Sets `deployed_at` timestamp (uses `created_at` or `imported_at` as fallback)
- Links existing process definitions to their function unit versions
- Clears `previous_version_id` (existing records are first versions)

## Rollback Scripts

### Schema Rollback
**File**: `08-add-function-unit-versioning-rollback.sql`

Removes all versioning columns, indexes, and constraints added by the schema migration.

### Data Rollback
**File**: `09-initialize-function-unit-versions-rollback.sql`

Resets version data to defaults:
- Resets all function units to version `1.0.0`
- Resets all function units to active status
- Clears version links from process instances

## Execution Order

### Forward Migration
1. Run `08-add-function-unit-versioning.sql` (schema changes)
2. Run `09-initialize-function-unit-versions.sql` (data initialization)

### Rollback
1. Run `09-initialize-function-unit-versions-rollback.sql` (reset data)
2. Run `08-add-function-unit-versioning-rollback.sql` (remove schema changes)

## Execution Instructions

### Using psql
```bash
# Forward migration
psql -U postgres -d workflow_platform_dev -f 08-add-function-unit-versioning.sql
psql -U postgres -d workflow_platform_dev -f 09-initialize-function-unit-versions.sql

# Rollback
psql -U postgres -d workflow_platform_dev -f 09-initialize-function-unit-versions-rollback.sql
psql -U postgres -d workflow_platform_dev -f 08-add-function-unit-versioning-rollback.sql
```

### Using PowerShell
```powershell
# Forward migration
& psql -U postgres -d workflow_platform_dev -f 08-add-function-unit-versioning.sql
& psql -U postgres -d workflow_platform_dev -f 09-initialize-function-unit-versions.sql

# Rollback
& psql -U postgres -d workflow_platform_dev -f 09-initialize-function-unit-versions-rollback.sql
& psql -U postgres -d workflow_platform_dev -f 08-add-function-unit-versioning-rollback.sql
```

## Verification

After running the migration, verify the changes:

```sql
-- Check dw_function_units structure
\d dw_function_units

-- Check sys_function_units structure
\d sys_function_units

-- Verify existing function units have version 1.0.0
SELECT name, version, is_active, deployed_at 
FROM dw_function_units 
ORDER BY name;

SELECT name, version, is_active, deployed_at 
FROM sys_function_units 
ORDER BY name;

-- Verify process definitions are linked to versions
SELECT pd.id, pd.function_unit_id, pd.function_unit_version_id, fu.name, fu.version
FROM dw_process_definitions pd
JOIN dw_function_units fu ON pd.function_unit_version_id = fu.id
LIMIT 10;

-- Check indexes
SELECT indexname, tablename 
FROM pg_indexes 
WHERE tablename IN ('dw_function_units', 'sys_function_units', 'dw_process_definitions', 'up_process_instance')
  AND indexname LIKE '%version%' OR indexname LIKE '%active%'
ORDER BY tablename, indexname;
```

## Requirements Addressed

This migration addresses the following requirements from the specification:

- **Requirement 8.1**: Version column added to dw_function_units
- **Requirement 8.2**: Active status column added to dw_function_units
- **Requirement 8.3**: Version column added to sys_function_units (already existed, validated)
- **Requirement 8.4**: Active status column added to sys_function_units
- **Requirement 8.5**: Foreign key linking dw_process_definitions to specific function unit versions
- **Requirement 8.6**: Column to track which version each process instance is using
- **Requirement 8.7**: Indexes on version and active status columns for query performance
- **Requirement 9.1**: Existing function units assigned version 1.0.0
- **Requirement 9.2**: Existing function units marked as active
- **Requirement 9.3**: Existing process definitions bound to version 1.0.0

## Notes

1. **Backward Compatibility**: All existing function units are automatically versioned as 1.0.0 and marked as active, ensuring no disruption to existing functionality.

2. **Process Instances**: The `up_process_instance.function_unit_version_id` column is added but not automatically populated for existing records. This requires additional business logic to map process instances to function units based on process definition keys.

3. **Semantic Versioning**: The system uses semantic versioning (MAJOR.MINOR.PATCH). The version column is VARCHAR(20) to accommodate this format.

4. **Active Version Constraint**: Only one version of each function unit should be active at any time. This is enforced at the application level, not by database constraints.

5. **Foreign Key Cascades**: 
   - `previous_version_id` uses `ON DELETE SET NULL` to preserve version history
   - `function_unit_version_id` in process definitions uses `ON DELETE CASCADE` to maintain referential integrity

6. **Performance**: Indexes are created on frequently queried columns (version, is_active, deployed_at) to ensure good query performance.

## Next Steps

After running this migration:

1. Update application code to use the new versioning columns
2. Implement version service for automatic version generation
3. Implement deployment service to create new versions
4. Implement rollback service to revert to previous versions
5. Update UI to display version information
6. Create additional migration script if needed to populate `up_process_instance.function_unit_version_id` for existing records

## Support

For questions or issues with this migration, refer to:
- Design Document: `.kiro/specs/function-unit-versioned-deployment/design.md`
- Requirements Document: `.kiro/specs/function-unit-versioned-deployment/requirements.md`
- Tasks Document: `.kiro/specs/function-unit-versioned-deployment/tasks.md`
