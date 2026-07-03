-- =====================================================
-- Add login_platform column to sys_login_audit
-- Supports tracking which app (ADMIN_CENTER/USER_PORTAL/DEVELOPER_WORKSTATION) the login event originated from.
-- =====================================================
ALTER TABLE sys_login_audit ADD COLUMN IF NOT EXISTS login_platform VARCHAR(32);
