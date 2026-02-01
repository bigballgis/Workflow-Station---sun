# Database Initialization Scripts

This directory contains consolidated database initialization scripts that replace the automatic Flyway migrations. All automatic database operations have been disabled to provide controlled database management.

## Directory Structure

```
deploy/init-scripts/
├── 00-schema/                    # Core database schema
│   ├── 00-init-all-schemas.sql   # Master initialization script
│   ├── 01-platform-security-schema.sql    # Core sys_* tables
│   ├── 02-workflow-engine-schema.sql      # Workflow wf_* tables
│   ├── 03-user-portal-schema.sql          # User portal up_* tables
│   ├── 04-developer-workstation-schema.sql # Developer dw_* tables
│   └── 05-admin-center-schema.sql         # Admin admin_* tables
├── 01-admin/                     # Administrative data
├── 02-test-data/                 # Test data (optional)
├── 04-purchase-workflow/         # Workflow-specific data
└── README.md                     # This file
```

## Changes Made

### Automatic Database Operations Disabled

The following automatic database operations have been **DISABLED** across all services:

1. **Flyway Automatic Migrations**:
   - `spring.flyway.enabled=false` in all production application.yml files
   - Prevents automatic execution of migration scripts on startup

2. **Hibernate Automatic Schema Operations**:
   - `spring.jpa.hibernate.ddl-auto=none` in all production configurations
   - Prevents automatic schema creation, updates, or validation

3. **Flowable Schema Updates**:
   - `flowable.database-schema-update=false` in workflow-engine-core
   - Prevents automatic Flowable schema modifications

### Configuration Files Updated

- `backend/workflow-engine-core/src/main/resources/application.yml`
- `backend/user-portal/src/main/resources/application.yml`
- `backend/admin-center/src/main/resources/application.yml`
- `backend/developer-workstation/src/main/resources/application.yml`
- `docker-compose.yml` (updated comments)

### Test Configurations

Test configurations (`application-test.yml`) remain unchanged and may still use:
- `spring.jpa.hibernate.ddl-auto=create-drop` for test database setup
- `spring.flyway.enabled=false` (tests handle their own schema)

## Usage

### Initial Database Setup

1. **Create Database**: Ensure PostgreSQL database exists
2. **Run Master Script**: Execute the master initialization script
   ```bash
   psql -d workflow_platform -f deploy/init-scripts/00-schema/00-init-all-schemas.sql
   ```

### Individual Schema Installation

If needed, you can run individual schema files:

```bash
# Core platform security (required first)
psql -d workflow_platform -f deploy/init-scripts/00-schema/01-platform-security-schema.sql

# Workflow engine
psql -d workflow_platform -f deploy/init-scripts/00-schema/02-workflow-engine-schema.sql

# User portal
psql -d workflow_platform -f deploy/init-scripts/00-schema/03-user-portal-schema.sql

# Developer workstation
psql -d workflow_platform -f deploy/init-scripts/00-schema/04-developer-workstation-schema.sql

# Admin center
psql -d workflow_platform -f deploy/init-scripts/00-schema/05-admin-center-schema.sql
```

### Execution Order

**IMPORTANT**: Scripts must be executed in the correct order due to foreign key dependencies:

1. `01-platform-security-schema.sql` - Creates core `sys_*` tables (users, roles, permissions)
2. `02-workflow-engine-schema.sql` - Creates `wf_*` tables
3. `03-user-portal-schema.sql` - Creates `up_*` tables
4. `04-developer-workstation-schema.sql` - Creates `dw_*` tables
5. `05-admin-center-schema.sql` - Creates `admin_*` tables (references sys_users)

## Schema Overview

### Platform Security (sys_* tables)
- User management and authentication
- Role-based access control
- Business unit organization
- Virtual groups and permissions
- Audit logging and preferences

### Workflow Engine (wf_* tables)
- Extended task information
- Process variables and history
- Workflow audit logs
- Exception tracking and resolution

### User Portal (up_* tables)
- User preferences and dashboard layouts
- Delegation rules and audit
- Process drafts and favorites
- Notification preferences

### Developer Workstation (dw_* tables)
- Function unit definitions
- Process and form definitions
- Table and field definitions
- Version control and operation logs

### Admin Center (admin_* tables)
- Password history and policies
- Permission delegations and conflicts
- System configuration and alerts
- Audit logs and retention policies

## Migration from Automatic System

This consolidation replaces the following original migration files:

- `backend/platform-security/.../V100__init_schema.sql`
- `backend/workflow-engine-core/.../V500__init_schema.sql`
- `backend/user-portal/.../V400__init_schema.sql`
- `backend/developer-workstation/.../V300__init_schema.sql`
- `backend/admin-center/.../V200__init_schema.sql`

## Future Database Changes

All future database schema changes must be:

1. **Documented**: Create new SQL files in appropriate directories
2. **Tested**: Verify changes in development environment
3. **Versioned**: Use clear naming conventions (e.g., `06-new-feature-schema.sql`)
4. **Applied Manually**: Execute scripts manually in each environment
5. **Tracked**: Update this README with new files and procedures

## Verification

After running the initialization scripts, verify the setup:

```sql
-- Check table counts by schema
SELECT 
    CASE 
        WHEN table_name LIKE 'sys_%' THEN 'Platform Security'
        WHEN table_name LIKE 'wf_%' THEN 'Workflow Engine'
        WHEN table_name LIKE 'up_%' THEN 'User Portal'
        WHEN table_name LIKE 'dw_%' THEN 'Developer Workstation'
        WHEN table_name LIKE 'admin_%' THEN 'Admin Center'
        ELSE 'Other'
    END AS schema_group,
    COUNT(*) as table_count
FROM information_schema.tables 
WHERE table_schema = 'public' 
    AND table_type = 'BASE TABLE'
GROUP BY schema_group
ORDER BY schema_group;
```

Expected results:
- Platform Security: ~30 tables
- Workflow Engine: ~4 tables
- User Portal: ~10 tables
- Developer Workstation: ~11 tables
- Admin Center: ~14 tables

## Troubleshooting

### Common Issues

1. **Foreign Key Errors**: Ensure scripts are run in the correct order
2. **Permission Errors**: Verify database user has CREATE privileges
3. **Existing Tables**: Use `DROP TABLE IF EXISTS` if recreating schema

### Rollback

To completely reset the database:

```sql
-- WARNING: This will delete all data
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;
GRANT ALL ON SCHEMA public TO postgres;
GRANT ALL ON SCHEMA public TO public;
```

Then re-run the initialization scripts.

## Security Considerations

- Database initialization scripts contain schema definitions only
- No sensitive data (passwords, keys) should be in these files
- Production credentials should be managed separately
- Test data should be clearly marked and separated from production schema