-- Grant DEVELOPER role permission to create and update function units (fix 403 on POST /api/v1/function-units)
INSERT INTO sys_developer_role_permissions (id, role_id, permission, created_at)
SELECT gen_random_uuid()::varchar, 'DEVELOPER_ROLE', p.permission, CURRENT_TIMESTAMP
FROM (VALUES ('FUNCTION_UNIT_CREATE'), ('FUNCTION_UNIT_UPDATE'), ('TABLE_UPDATE')) AS p(permission)
ON CONFLICT (role_id, permission) DO NOTHING;
