-- Promote the built-in AUDITOR role off ADMIN type and clamp its DW permissions
-- to FUNCTION_UNIT_VIEW only. Targets role code AUDITOR; do not apply to all ADMIN roles.

\echo '========================================='
\echo 'AUDITOR role type + developer permission clamp...'
\echo '========================================='

UPDATE sys_roles
SET type = 'AUDITOR',
    updated_at = CURRENT_TIMESTAMP
WHERE code = 'AUDITOR'
  AND type IS DISTINCT FROM 'AUDITOR';

DELETE FROM sys_developer_role_permissions
WHERE role_id IN (SELECT id FROM sys_roles WHERE code = 'AUDITOR')
  AND permission <> 'FUNCTION_UNIT_VIEW';

INSERT INTO sys_developer_role_permissions (id, role_id, permission, created_at)
SELECT
    gen_random_uuid()::varchar,
    r.id,
    'FUNCTION_UNIT_VIEW',
    CURRENT_TIMESTAMP
FROM sys_roles r
WHERE r.code = 'AUDITOR'
ON CONFLICT (role_id, permission) DO NOTHING;

\echo '✓ AUDITOR type set; DW permissions clamped to FUNCTION_UNIT_VIEW'
