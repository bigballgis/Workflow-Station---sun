-- =====================================================
-- Master Database Initialization Script (Standalone)
-- =====================================================
-- Use this script when running psql directly (not via Docker).
-- It uses relative paths instead of Docker mount paths.
--
-- Usage:
--   cd deploy/init-scripts
--   psql -h <host> -p <port> -U <user> -d <dbname> -f 00-schema/00-init-all-schemas-standalone.sql
-- =====================================================

SET client_min_messages = WARNING;
SET timezone = 'UTC';

BEGIN;

\echo '=== 1/5 Platform Security Schema (sys_*) ==='
\i 00-schema/01-platform-security-schema.sql

\echo '=== 2/5 Workflow Engine Schema (wf_*) ==='
\i 00-schema/02-workflow-engine-schema.sql

\echo '=== 3/5 User Portal Schema (up_*) ==='
\i 00-schema/03-user-portal-schema.sql

\echo '=== 4/5 Developer Workstation Schema (dw_*) ==='
\i 00-schema/04-developer-workstation-schema.sql

\echo '=== 5/5 Admin Center Schema (admin_*) ==='
\i 00-schema/05-admin-center-schema.sql

COMMIT;

-- Apply incremental migrations
\echo '=== Applying incremental migrations ==='
\i 00-schema/06-add-deployment-rollback-columns.sql
\i 00-schema/07-add-action-definitions-table.sql
\i 00-schema/08-add-function-unit-versioning.sql
\i 00-schema/10-add-approval-order-column.sql
\i 00-schema/11-add-unique-enabled-constraint.sql
\i 00-schema/12-add-enabled-field-to-dw-function-units.sql

\echo '=== All schemas created successfully ==='

SELECT 
    CASE 
        WHEN table_name LIKE 'sys_%' THEN 'Platform Security (sys_*)'
        WHEN table_name LIKE 'wf_%' THEN 'Workflow Engine (wf_*)'
        WHEN table_name LIKE 'up_%' THEN 'User Portal (up_*)'
        WHEN table_name LIKE 'dw_%' THEN 'Developer Workstation (dw_*)'
        WHEN table_name LIKE 'admin_%' THEN 'Admin Center (admin_*)'
        ELSE 'Other'
    END AS schema_group,
    COUNT(*) as table_count
FROM information_schema.tables 
WHERE table_schema = 'public' 
    AND table_type = 'BASE TABLE'
    AND (table_name LIKE 'sys_%' OR table_name LIKE 'wf_%' OR table_name LIKE 'up_%' OR table_name LIKE 'dw_%' OR table_name LIKE 'admin_%')
GROUP BY schema_group
ORDER BY schema_group;
