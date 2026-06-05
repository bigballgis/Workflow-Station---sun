-- =====================================================
-- Admin Center: System Permissions & Role Bindings
-- =====================================================
-- Inserts base permissions into sys_permissions and binds
-- them to the system roles created in 01-create-roles-and-groups.sql
-- =====================================================

\echo '========================================='
\echo 'Inserting admin center permissions...'
\echo '========================================='

-- ---- Permissions ----
-- sys_permissions columns: id, code, name, type, resource, action, display_name, created_at, parent_id, sort_order
INSERT INTO sys_permissions (id, code, name, type, resource, action, display_name, sort_order, created_at) VALUES
  ('perm-basic-access', 'basic:access',  'Basic Access',     'PLATFORM', 'platform', 'access', 'Basic access to the platform',              1,  CURRENT_TIMESTAMP),
  ('perm-user-read',    'user:read',     'Read Users',       'USER',     'user',     'read',   'View user list and details',                10, CURRENT_TIMESTAMP),
  ('perm-user-write',   'user:write',    'Write Users',      'USER',     'user',     'write',  'Create and update users',                   11, CURRENT_TIMESTAMP),
  ('perm-user-delete',  'user:delete',   'Delete Users',     'USER',     'user',     'delete', 'Delete users',                              12, CURRENT_TIMESTAMP),
  ('perm-role-read',    'role:read',     'Read Roles',       'ROLE',     'role',     'read',   'View roles and permissions',                20, CURRENT_TIMESTAMP),
  ('perm-role-write',   'role:write',    'Write Roles',      'ROLE',     'role',     'write',  'Create and update roles',                   21, CURRENT_TIMESTAMP),
  ('perm-role-delete',  'role:delete',   'Delete Roles',     'ROLE',     'role',     'delete', 'Delete roles',                              22, CURRENT_TIMESTAMP),
  ('perm-sys-admin',    'system:admin',  'System Admin',     'SYSTEM',   'system',   'admin',  'Full system administration access',         30, CURRENT_TIMESTAMP),
  ('perm-sys-config',   'system:config', 'System Config',    'SYSTEM',   'system',   'config', 'Manage system configuration',               31, CURRENT_TIMESTAMP),
  ('perm-audit-read',   'audit:read',    'Read Audit Logs',  'AUDIT',    'audit',    'read',   'View audit logs and operation history',     40, CURRENT_TIMESTAMP),
  ('perm-log-read',     'log:read',      'Read Logs',        'AUDIT',    'log',      'read',   'View system and security logs',             41, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO NOTHING;

\echo '✓ Permissions inserted'

-- ---- SYS_ADMIN role gets all permissions ----
INSERT INTO sys_role_permissions (id, role_id, permission_id, created_at)
SELECT
    'rp-sysadmin-' || p.id,
    'role-sys-admin',
    p.id,
    CURRENT_TIMESTAMP
FROM sys_permissions p
ON CONFLICT (role_id, permission_id) DO NOTHING;

\echo '✓ SYS_ADMIN bound to all permissions'

-- ---- AUDITOR role: audit:read, log:read, user:read, basic:access ----
INSERT INTO sys_role_permissions (id, role_id, permission_id, created_at)
SELECT
    'rp-auditor-' || p.id,
    'role-auditor',
    p.id,
    CURRENT_TIMESTAMP
FROM sys_permissions p
WHERE p.code IN ('basic:access', 'user:read', 'audit:read', 'log:read')
ON CONFLICT (role_id, permission_id) DO NOTHING;

\echo '✓ AUDITOR bound to audit:read, log:read, user:read'

\echo ''
\echo '========================================='
\echo 'Admin permissions setup complete!'
\echo '========================================='
