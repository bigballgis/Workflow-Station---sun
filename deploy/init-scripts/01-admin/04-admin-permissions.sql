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
-- sys_permissions columns: id, code, name, type, resource, action, description, created_at, parent_id, sort_order
INSERT INTO sys_permissions (id, code, name, type, resource, action, description, sort_order, created_at) VALUES
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
  ('perm-log-read',     'log:read',      'Read Logs',        'AUDIT',    'log',      'read',   'View system and security logs',             41, CURRENT_TIMESTAMP),
  -- Gateway Governance permissions
  ('perm-gw-api-read',        'gateway:api:read',         'Read Gateway APIs',        'GATEWAY', 'gateway:api',         'read',    'View gateway API definitions and versions',            50, CURRENT_TIMESTAMP),
  ('perm-gw-api-write',       'gateway:api:write',        'Write Gateway APIs',       'GATEWAY', 'gateway:api',         'write',   'Create and update gateway API definitions',            51, CURRENT_TIMESTAMP),
  ('perm-gw-app-read',        'gateway:application:read', 'Read Gateway Apps',        'GATEWAY', 'gateway:application', 'read',    'View gateway application definitions',                 52, CURRENT_TIMESTAMP),
  ('perm-gw-app-write',       'gateway:application:write','Write Gateway Apps',       'GATEWAY', 'gateway:application', 'write',   'Create and update gateway applications',               53, CURRENT_TIMESTAMP),
  ('perm-gw-policy-read',     'gateway:policy:read',      'Read Gateway Policies',    'GATEWAY', 'gateway:policy',      'read',    'View gateway access and traffic policies',             54, CURRENT_TIMESTAMP),
  ('perm-gw-policy-write',    'gateway:policy:write',     'Write Gateway Policies',   'GATEWAY', 'gateway:policy',      'write',   'Update gateway policies',                              55, CURRENT_TIMESTAMP),
  ('perm-gw-release-read',    'gateway:release:read',     'Read Gateway Releases',    'GATEWAY', 'gateway:release',     'read',    'View gateway release details and history',             56, CURRENT_TIMESTAMP),
  ('perm-gw-release-execute', 'gateway:release:execute',  'Execute Gateway Releases', 'GATEWAY', 'gateway:release',     'execute', 'Submit testing, publish, and rollback releases',       57, CURRENT_TIMESTAMP),
  ('perm-gw-env-read',        'gateway:environment:read', 'Read Gateway Environments', 'GATEWAY', 'gateway:environment', 'read',    'View gateway environment configurations',              58, CURRENT_TIMESTAMP),
  ('perm-gw-env-write',       'gateway:environment:write','Write Gateway Environments','GATEWAY', 'gateway:environment', 'write',   'Update gateway environment configurations',            59, CURRENT_TIMESTAMP),
  ('perm-gw-audit-read',      'gateway:audit:read',       'Read Gateway Audit Logs',  'GATEWAY', 'gateway:audit',       'read',    'View gateway domain audit logs',                       60, CURRENT_TIMESTAMP),
  -- Phase 2 additions
  ('perm-gw-drift-read',      'gateway:drift:read',       'Read Drift Reports',       'GATEWAY', 'gateway:drift',       'read',    'View gateway drift detection reports',                 61, CURRENT_TIMESTAMP),
  ('perm-gw-drift-sync',      'gateway:drift:sync',       'Trigger Drift Sync',       'GATEWAY', 'gateway:drift',       'execute', 'Trigger drift sync against gateway runtime',           62, CURRENT_TIMESTAMP),
  ('perm-gw-monitoring-read', 'gateway:monitoring:read',  'Read Monitoring Data',     'GATEWAY', 'gateway:monitoring',  'read',    'View gateway monitoring dashboard and metrics',        63, CURRENT_TIMESTAMP),
  ('perm-gw-release-approve', 'gateway:release:approve',  'Approve Releases',         'GATEWAY', 'gateway:release',     'approve', 'Approve releases for PROD publish',                    64, CURRENT_TIMESTAMP),
  ('perm-gw-release-promote', 'gateway:release:promote',  'Promote Releases',         'GATEWAY', 'gateway:release',     'execute', 'Promote releases across environments',                 65, CURRENT_TIMESTAMP),
  -- Phase 4: API Marketplace
  ('perm-gw-catalog-read',     'gateway:catalog:read',      'Read API Catalog',          'GATEWAY', 'gateway:catalog',     'read',    'Browse API catalog in marketplace',                     66, CURRENT_TIMESTAMP),
  ('perm-gw-catalog-admin',    'gateway:catalog:admin',     'Manage Catalog Visibility',  'GATEWAY', 'gateway:catalog',     'admin',   'Manage API catalog visibility settings',                67, CURRENT_TIMESTAMP),
  ('perm-gw-sub-request',      'gateway:subscription:request', 'Request API Subscription','GATEWAY', 'gateway:subscription','request',  'Submit API subscription requests',                      68, CURRENT_TIMESTAMP),
  ('perm-gw-sub-read',         'gateway:subscription:read',    'Read Subscriptions',      'GATEWAY', 'gateway:subscription','read',    'View subscriptions and requests',                       69, CURRENT_TIMESTAMP),
  ('perm-gw-sub-approve',      'gateway:subscription:approve', 'Approve Subscriptions',   'GATEWAY', 'gateway:subscription','approve', 'Approve or reject subscription requests',               70, CURRENT_TIMESTAMP),
  ('perm-gw-sub-revoke',       'gateway:subscription:revoke',  'Revoke Subscriptions',    'GATEWAY', 'gateway:subscription','revoke',  'Revoke active API subscriptions',                       71, CURRENT_TIMESTAMP),
  -- Phase 5: Multi-Gateway Governance
  ('perm-gw-gov-read',         'gateway:governance:read',     'Read Governance Rules',    'GATEWAY', 'gateway:governance',  'read',    'View governance rules',                                 72, CURRENT_TIMESTAMP),
  ('perm-gw-gov-write',        'gateway:governance:write',    'Write Governance Rules',   'GATEWAY', 'gateway:governance',  'write',   'Create and update governance rules',                    73, CURRENT_TIMESTAMP),
  ('perm-gw-compliance-read',   'gateway:compliance:read',     'Read Compliance Checks',   'GATEWAY', 'gateway:compliance',  'read',    'View compliance check results',                         74, CURRENT_TIMESTAMP),
  ('perm-gw-compliance-export',  'gateway:compliance:export',   'Export Compliance Report', 'GATEWAY', 'gateway:compliance',  'export',  'Export compliance reports for audit',                   75, CURRENT_TIMESTAMP),
  ('perm-gw-provider-read',      'gateway:provider:read',       'Read Provider Config',     'GATEWAY', 'gateway:provider',    'read',    'View environment provider configuration',               76, CURRENT_TIMESTAMP),
  ('perm-gw-provider-write',     'gateway:provider:write',      'Write Provider Config',    'GATEWAY', 'gateway:provider',    'write',   'Change environment gateway provider',                   77, CURRENT_TIMESTAMP),
  -- MFE Governance permissions (Phase 1)
  ('perm-mfe-module-read',         'frontend.module:read',           'Read MFE Module Registry',     'MFE', 'frontend:module',    'read',     'View frontend module registry list',                   70, CURRENT_TIMESTAMP),
  ('perm-mfe-module-write',        'frontend.module:write',          'Write MFE Module Config',      'MFE', 'frontend:module',    'write',    'Create and update frontend module config',             71, CURRENT_TIMESTAMP),
  ('perm-mfe-module-enable',       'frontend.module:enable',         'Enable/Disable MFE Module',    'MFE', 'frontend:module',    'enable',   'Enable or disable frontend modules',                   72, CURRENT_TIMESTAMP),
  ('perm-mfe-module-version-switch','frontend.module:version:switch','Switch MFE Module Version',    'MFE', 'frontend:module',    'version',  'Switch frontend module version',                       73, CURRENT_TIMESTAMP),
  ('perm-mfe-module-version-rollback','frontend.module:version:rollback','Rollback MFE Module Version','MFE','frontend:module',    'version',  'Rollback frontend module version',                     74, CURRENT_TIMESTAMP),
  ('perm-mfe-module-runtime-read',  'frontend.module:runtime:read',  'Read MFE Runtime Config',      'MFE', 'frontend:module',    'runtime',  'Query runtime frontend module config for host apps',   75, CURRENT_TIMESTAMP),
  ('perm-mfe-module-health-check',   'frontend.module:health:check',  'Run MFE Module Health Check',  'MFE', 'frontend:module',    'health',   'Check remote entry availability of frontend modules', 76, CURRENT_TIMESTAMP),
  ('perm-mfe-module-version-read',   'frontend.module:version:read',  'Read MFE Module Versions',     'MFE', 'frontend:module',    'version',  'View version history of frontend modules',            77, CURRENT_TIMESTAMP)
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

-- ---- GATEWAY_VIEWER role: read-only gateway access ---- (Phase 2)
INSERT INTO sys_role_permissions (id, role_id, permission_id, created_at)
SELECT
    'rp-gw-viewer-' || p.id,
    'role-gw-viewer',
    p.id,
    CURRENT_TIMESTAMP
FROM sys_permissions p
WHERE p.code IN (
    'basic:access',
    'gateway:api:read', 'gateway:application:read', 'gateway:policy:read',
    'gateway:release:read', 'gateway:environment:read', 'gateway:audit:read',
    'gateway:drift:read', 'gateway:monitoring:read'
)
ON CONFLICT (role_id, permission_id) DO NOTHING;

\echo '✓ GATEWAY_VIEWER bound to gateway read permissions'

-- ---- GATEWAY_OPERATOR role: viewer + write + drift sync + promote ---- (Phase 2)
INSERT INTO sys_role_permissions (id, role_id, permission_id, created_at)
SELECT
    'rp-gw-operator-' || p.id,
    'role-gw-operator',
    p.id,
    CURRENT_TIMESTAMP
FROM sys_permissions p
WHERE p.code IN (
    'basic:access',
    'gateway:api:read', 'gateway:api:write',
    'gateway:application:read', 'gateway:application:write',
    'gateway:policy:read', 'gateway:policy:write',
    'gateway:release:read', 'gateway:release:execute',
    'gateway:environment:read', 'gateway:environment:write',
    'gateway:audit:read',
    'gateway:drift:read', 'gateway:drift:sync',
    'gateway:monitoring:read',
    'gateway:release:promote'
)
ON CONFLICT (role_id, permission_id) DO NOTHING;

\echo '✓ GATEWAY_OPERATOR bound to gateway operator permissions'

-- ---- GATEWAY_ADMIN role: operator + approve ---- (Phase 2)
INSERT INTO sys_role_permissions (id, role_id, permission_id, created_at)
SELECT
    'rp-gw-admin-' || p.id,
    'role-gw-admin',
    p.id,
    CURRENT_TIMESTAMP
FROM sys_permissions p
WHERE p.code IN (
    'basic:access',
    'gateway:api:read', 'gateway:api:write',
    'gateway:application:read', 'gateway:application:write',
    'gateway:policy:read', 'gateway:policy:write',
    'gateway:release:read', 'gateway:release:execute',
    'gateway:environment:read', 'gateway:environment:write',
    'gateway:audit:read',
    'gateway:drift:read', 'gateway:drift:sync',
    'gateway:monitoring:read',
    'gateway:release:promote', 'gateway:release:approve',
    'gateway:catalog:read', 'gateway:catalog:admin',
    'gateway:subscription:request', 'gateway:subscription:read',
    'gateway:subscription:approve', 'gateway:subscription:revoke',
    'gateway:governance:read', 'gateway:governance:write',
    'gateway:compliance:read', 'gateway:compliance:export',
    'gateway:provider:read', 'gateway:provider:write'
)
ON CONFLICT (role_id, permission_id) DO NOTHING;

\echo '✓ GATEWAY_ADMIN bound to gateway admin permissions'

-- ---- SECURITY_AUDITOR role: drift read + monitoring read ---- (Phase 2)
INSERT INTO sys_role_permissions (id, role_id, permission_id, created_at)
SELECT
    'rp-sec-auditor-' || p.id,
    'role-sec-auditor',
    p.id,
    CURRENT_TIMESTAMP
FROM sys_permissions p
WHERE p.code IN (
    'basic:access', 'audit:read', 'log:read',
    'gateway:drift:read', 'gateway:monitoring:read', 'gateway:audit:read',
    'gateway:catalog:read', 'gateway:subscription:read',
    'gateway:governance:read', 'gateway:compliance:read',
    'gateway:compliance:export', 'gateway:provider:read'
)
ON CONFLICT (role_id, permission_id) DO NOTHING;

\echo '✓ SECURITY_AUDITOR bound to security audit permissions'

\echo ''
\echo '========================================='
\echo 'Admin permissions setup complete!'
\echo '========================================='