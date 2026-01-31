-- Grant DEVELOPER role permission to publish/deploy function units (Publish and Deploy buttons on developer-workstation)
INSERT INTO sys_developer_role_permissions (id, role_id, permission, created_at)
VALUES (gen_random_uuid()::varchar, 'DEVELOPER_ROLE', 'FUNCTION_UNIT_PUBLISH', CURRENT_TIMESTAMP)
ON CONFLICT (role_id, permission) DO NOTHING;
