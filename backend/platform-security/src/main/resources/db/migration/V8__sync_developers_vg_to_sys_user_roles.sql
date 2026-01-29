-- Sync Developers virtual group members into sys_user_roles so admin-center sees their DEVELOPER role.
-- admin-center only reads sys_user_roles; it does not consider virtual group membership.
INSERT INTO sys_user_roles (id, user_id, role_id, assigned_at, assigned_by)
SELECT 'ur-vgdev-' || vgm.user_id, vgm.user_id, 'DEVELOPER_ROLE', NOW(), 'system'
FROM sys_virtual_group_members vgm
WHERE vgm.group_id = 'vg-developers'
AND NOT EXISTS (SELECT 1 FROM sys_user_roles sur WHERE sur.user_id = vgm.user_id AND sur.role_id = 'DEVELOPER_ROLE');
