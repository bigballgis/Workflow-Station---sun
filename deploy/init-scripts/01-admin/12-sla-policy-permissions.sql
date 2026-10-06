-- =====================================================
-- SLA policy permissions (append-only)
-- sla:policy:view -> SYS_ADMIN, AUDITOR
-- sla:policy:edit -> SYS_ADMIN only
-- 04-admin-permissions.sql bound SYS_ADMIN to the permissions that existed then, so the
-- bindings for these new codes are written here.
-- =====================================================

INSERT INTO sys_permissions (id, code, name, type, resource, action, display_name, sort_order, created_at) VALUES
  ('perm-sla-policy-view', 'sla:policy:view', 'View SLA Policies', 'SYSTEM', 'sla_policy', 'view', 'View SLA lead times and recalculation jobs', 32, CURRENT_TIMESTAMP),
  ('perm-sla-policy-edit', 'sla:policy:edit', 'Edit SLA Policies', 'SYSTEM', 'sla_policy', 'edit', 'Change SLA lead times and trigger recalculation', 33, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO NOTHING;

INSERT INTO sys_role_permissions (id, role_id, permission_id, created_at)
SELECT 'rp-sysadmin-' || p.id, 'role-sys-admin', p.id, CURRENT_TIMESTAMP
FROM sys_permissions p
WHERE p.code IN ('sla:policy:view', 'sla:policy:edit')
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO sys_role_permissions (id, role_id, permission_id, created_at)
SELECT 'rp-auditor-' || p.id, 'role-auditor', p.id, CURRENT_TIMESTAMP
FROM sys_permissions p
WHERE p.code = 'sla:policy:view'
ON CONFLICT (role_id, permission_id) DO NOTHING;
