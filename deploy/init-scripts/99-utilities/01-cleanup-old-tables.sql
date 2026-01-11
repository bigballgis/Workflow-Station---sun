-- =====================================================
-- CLEANUP: Remove deprecated tables
-- Run this MANUALLY after migrating data to new tables
-- DO NOT include in automatic init process
-- =====================================================

-- WARNING: This script will DROP tables. Make sure data is migrated first!

-- =====================================================
-- 1. Drop old user-related tables (replaced by sys_users)
-- =====================================================

-- Drop foreign key constraints first
ALTER TABLE sys_login_audit DROP CONSTRAINT IF EXISTS sys_login_audit_user_id_fkey;
ALTER TABLE sys_user_role DROP CONSTRAINT IF EXISTS sys_user_role_user_id_fkey;

-- Drop old tables
DROP TABLE IF EXISTS sys_user_role CASCADE;
DROP TABLE IF EXISTS sys_user CASCADE;
DROP TABLE IF EXISTS users CASCADE;
DROP TABLE IF EXISTS user_roles CASCADE;
DROP TABLE IF EXISTS user_organizations CASCADE;

-- =====================================================
-- 2. Drop old organization/role tables (replaced by sys_*)
-- =====================================================

DROP TABLE IF EXISTS organizations CASCADE;
DROP TABLE IF EXISTS roles CASCADE;
DROP TABLE IF EXISTS permissions CASCADE;
DROP TABLE IF EXISTS role_permissions CASCADE;
DROP TABLE IF EXISTS permission_delegations CASCADE;

-- =====================================================
-- 3. Drop duplicate/unused tables
-- =====================================================

DROP TABLE IF EXISTS audit_logs CASCADE;
DROP TABLE IF EXISTS data_dictionaries CASCADE;
DROP TABLE IF EXISTS data_dictionary_items CASCADE;
DROP TABLE IF EXISTS developer_role_permissions CASCADE;
DROP TABLE IF EXISTS function_units CASCADE;
DROP TABLE IF EXISTS function_unit_deployments CASCADE;
DROP TABLE IF EXISTS saga_steps CASCADE;
DROP TABLE IF EXISTS saga_transactions CASCADE;

-- =====================================================
-- 4. Drop old virtual group tables (replaced by sys_virtual_groups)
-- =====================================================

-- Virtual groups are now shared across services (sys_* prefix)
DROP TABLE IF EXISTS admin_virtual_group_task_history CASCADE;
DROP TABLE IF EXISTS admin_virtual_group_members CASCADE;
DROP TABLE IF EXISTS admin_virtual_groups CASCADE;

-- =====================================================
-- 5. Rename admin_roles to sys_roles if needed
-- =====================================================

-- If admin_roles exists and sys_roles doesn't have the data
-- INSERT INTO sys_roles SELECT * FROM admin_roles ON CONFLICT DO NOTHING;
-- DROP TABLE IF EXISTS admin_roles CASCADE;

-- =====================================================
-- 5. Update foreign key in sys_login_audit
-- =====================================================

-- Recreate foreign key to point to sys_users
-- ALTER TABLE sys_login_audit ADD CONSTRAINT sys_login_audit_user_id_fkey 
--     FOREIGN KEY (user_id) REFERENCES sys_users(id) ON DELETE SET NULL;

-- =====================================================
-- VERIFICATION QUERIES
-- =====================================================

-- Check remaining tables
-- SELECT tablename FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename;

-- Check table counts
-- SELECT 'sys_users' as table_name, COUNT(*) as count FROM sys_users
-- UNION ALL SELECT 'sys_roles', COUNT(*) FROM sys_roles
-- UNION ALL SELECT 'sys_user_roles', COUNT(*) FROM sys_user_roles
-- UNION ALL SELECT 'sys_departments', COUNT(*) FROM sys_departments;

COMMENT ON SCHEMA public IS 'Cleaned up schema - using unified sys_* tables';
