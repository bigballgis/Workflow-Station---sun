-- =====================================================
-- Workflow Engine Core V2 Migration
-- Note: Authentication tables (sys_users, sys_user_roles, sys_login_audit)
-- are created by platform-security
-- This migration is kept for version compatibility
-- =====================================================

-- No tables to create - auth tables are managed by platform-security

SELECT 'Auth tables delegated to platform-security' AS status;
