-- =====================================================
-- Master Database Initialization Script
-- Executes all schema creation scripts in the correct order
-- 
-- This script consolidates all database migrations from:
-- - backend/platform-security/src/main/resources/db/migration/platform-security/V100__init_schema.sql
-- - backend/workflow-engine-core/src/main/resources/db/migration/workflow-engine/V500__init_schema.sql
-- - backend/user-portal/src/main/resources/db/migration/user-portal/V400__init_schema.sql
-- - backend/developer-workstation/src/main/resources/db/migration/developer-workstation/V300__init_schema.sql
-- - backend/admin-center/src/main/resources/db/migration/admin-center/V200__init_schema.sql
--
-- IMPORTANT: Flyway and Hibernate automatic migrations have been disabled.
-- Database initialization must be performed manually using these scripts.
-- =====================================================

-- Set session parameters for consistent behavior
SET client_min_messages = WARNING;
SET timezone = 'UTC';

-- Begin transaction for atomic execution
BEGIN;

-- =====================================================
-- 1. Platform Security Schema (Core Foundation)
-- Must be executed first as other schemas depend on sys_* tables
-- =====================================================
\echo 'Creating Platform Security Schema (sys_* tables)...'
\i 01-platform-security-schema.sql

-- =====================================================
-- 2. Workflow Engine Schema
-- Depends on sys_users and other platform-security tables
-- =====================================================
\echo 'Creating Workflow Engine Schema (wf_* tables)...'
\i 02-workflow-engine-schema.sql

-- =====================================================
-- 3. User Portal Schema
-- Depends on platform-security for user references
-- =====================================================
\echo 'Creating User Portal Schema (up_* tables)...'
\i 03-user-portal-schema.sql

-- =====================================================
-- 4. Developer Workstation Schema
-- Independent schema for developer tools
-- =====================================================
\echo 'Creating Developer Workstation Schema (dw_* tables)...'
\i 04-developer-workstation-schema.sql

-- =====================================================
-- 5. Admin Center Schema
-- Depends on platform-security for user and permission references
-- =====================================================
\echo 'Creating Admin Center Schema (admin_* tables)...'
\i 05-admin-center-schema.sql

-- Commit transaction
COMMIT;

-- =====================================================
-- Verification Queries
-- =====================================================
\echo 'Database initialization completed successfully!'
\echo 'Verifying table creation...'

-- Count tables by prefix
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
    AND (table_name LIKE 'sys_%' 
         OR table_name LIKE 'wf_%' 
         OR table_name LIKE 'up_%' 
         OR table_name LIKE 'dw_%' 
         OR table_name LIKE 'admin_%')
GROUP BY schema_group
ORDER BY schema_group;

\echo 'Database initialization verification completed.'
\echo ''
\echo 'IMPORTANT NOTES:'
\echo '1. Flyway automatic migrations are DISABLED in all application.yml files'
\echo '2. Hibernate ddl-auto is set to "none" in all production configurations'
\echo '3. All future database changes must be managed through deploy/init-scripts'
\echo '4. Test configurations may still use create-drop for testing purposes'