-- =====================================================
-- Remove deprecated FU_VIEWER (Function Unit Viewer) role
-- =====================================================
-- Fresh installs still run 01-create-roles-and-groups.sql / 02-init-developer-permissions.sql
-- which insert FU_VIEWER; this script removes that role and related bindings afterwards.
-- Team read access no longer depends on FU_VIEWER (membership / other DW roles suffice).
-- Idempotent: safe to re-run.
-- =====================================================

-- Explicit cleanup (role FK children mostly CASCADE; clear known bindings first for clarity)
DELETE FROM sys_virtual_group_roles WHERE role_id = 'role-fu-viewer';
DELETE FROM sys_developer_role_permissions WHERE role_id = 'role-fu-viewer';
DELETE FROM sys_role_assignments WHERE role_id = 'role-fu-viewer';
DELETE FROM sys_user_roles WHERE role_id = 'role-fu-viewer';
DELETE FROM sys_user_business_unit_roles WHERE role_id = 'role-fu-viewer';
DELETE FROM sys_business_unit_roles WHERE role_id = 'role-fu-viewer';
DELETE FROM sys_roles WHERE id = 'role-fu-viewer' OR code = 'FU_VIEWER';

\echo '✓ Removed deprecated FU_VIEWER role (if present)'
