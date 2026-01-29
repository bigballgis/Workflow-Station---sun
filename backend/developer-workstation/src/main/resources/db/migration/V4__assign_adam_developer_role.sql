-- Assign DEVELOPER role to user Adam (userId from runtime) so they can create function units.
-- Tables sys_role_assignments / sys_user_roles are from platform-security (same DB).
INSERT INTO sys_role_assignments (id, role_id, target_type, target_id, assigned_at, assigned_by)
VALUES ('ra-adam-developer', 'DEVELOPER_ROLE', 'USER', 'bfe0805e-adcc-43cd-9c07-c368f3b947fb', NOW(), 'system')
ON CONFLICT (role_id, target_type, target_id) DO NOTHING;

INSERT INTO sys_user_roles (id, user_id, role_id, assigned_at, assigned_by)
SELECT 'ur-bfe0805e-adcc-43cd-9c07-c368f3b947fb-DEVELOPER_ROLE', 'bfe0805e-adcc-43cd-9c07-c368f3b947fb', 'DEVELOPER_ROLE', NOW(), 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_user_roles sur WHERE sur.user_id = 'bfe0805e-adcc-43cd-9c07-c368f3b947fb' AND sur.role_id = 'DEVELOPER_ROLE');
