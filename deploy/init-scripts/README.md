# Database Initialization Scripts

This directory contains consolidated database initialization scripts for the Workflow Platform. All scripts are designed to be idempotent and can be run multiple times safely.

## Quick Start

### Windows (PowerShell)
```powershell
cd deploy/init-scripts
.\init-database.ps1 -DbHost localhost -DbPort 5432 -DbName workflow_platform -DbUser postgres -DbPassword yourpassword
```

### Linux/Mac (Bash)
```bash
cd deploy/init-scripts
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=workflow_platform
export DB_USER=postgres
export DB_PASSWORD=yourpassword
./init-database.sh
```

## What Gets Initialized

The initialization process creates:

1. **Database Schemas** - All tables, indexes, and constraints
2. **5 System Roles**:
   - `SYS_ADMIN` (系统管理员) - Full system access
   - `AUDITOR` (审计员) - Audit and monitoring access
   - `MANAGER` (部门经理) - Department management
   - `DEVELOPER` (工作流开发者) - Workflow development
   - `DESIGNER` (工作流设计师) - Process design

3. **5 Virtual Groups**:
   - `SYSTEM_ADMINISTRATORS` → SYS_ADMIN role
   - `AUDITORS` → AUDITOR role
   - `MANAGERS` → MANAGER role
   - `DEVELOPERS` → DEVELOPER role
   - `DESIGNERS` → DESIGNER role

4. **5 Test Users** (all with password: `password`):
   - `admin` - System Administrator
   - `auditor` - System Auditor
   - `manager` - Department Manager
   - `developer` - Workflow Developer
   - `designer` - Workflow Designer

## Directory Structure

```
deploy/init-scripts/
├── init-database.sh              # Main initialization script (Linux/Mac)
├── init-database.ps1             # Main initialization script (Windows)
├── 00-init-all.sh                # Docker entrypoint script
├── 00-schema/                    # Database schema definitions
│   ├── 00-init-all-schemas.sql   # Master schema script
│   ├── 01-platform-security-schema.sql    # Core sys_* tables
│   ├── 02-workflow-engine-schema.sql      # Workflow wf_* tables
│   ├── 03-user-portal-schema.sql          # User portal up_* tables
│   ├── 04-developer-workstation-schema.sql # Developer dw_* tables
│   └── 05-admin-center-schema.sql         # Admin admin_* tables
├── 01-admin/                     # System initialization
│   ├── 01-create-roles-and-groups.sql     # 5 roles + 5 virtual groups
│   └── 02-create-test-users.sql           # 5 test users
├── 02-test-data/                 # Optional test data
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

**Option 1: Using the automated script (Recommended)**

Windows:
```powershell
.\init-database.ps1 -DbHost localhost -DbPort 5432 -DbName workflow_platform -DbUser postgres
```

Linux/Mac:
```bash
./init-database.sh
```

**Option 2: Manual execution**

If you prefer to run scripts manually:

```bash
# 1. Create all schemas
psql -d workflow_platform -f deploy/init-scripts/00-schema/00-init-all-schemas.sql

# 2. Create roles and virtual groups
psql -d workflow_platform -f deploy/init-scripts/01-admin/01-create-roles-and-groups.sql

# 3. Create test users
psql -d workflow_platform -f deploy/init-scripts/01-admin/02-create-test-users.sql
```

### Docker Environment

The `00-init-all.sh` script is automatically executed when using Docker Compose:

```bash
docker-compose up -d postgres
```

All initialization scripts in the mounted directories will be executed automatically.

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