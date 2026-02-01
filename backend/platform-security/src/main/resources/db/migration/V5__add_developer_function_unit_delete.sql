-- Grant DEVELOPER role permission to delete function units (fix 403 on DELETE /api/v1/function-units/:id)
INSERT INTO sys_developer_role_permissions (id, role_id, permission, created_at)
VALUES (gen_random_uuid()::varchar, 'DEVELOPER_ROLE', 'FUNCTION_UNIT_DELETE', CURRENT_TIMESTAMP)
ON CONFLICT (role_id, permission) DO NOTHING;
