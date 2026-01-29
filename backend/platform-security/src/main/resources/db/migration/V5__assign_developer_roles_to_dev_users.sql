-- Assign developer roles to dev users so admin-center returns permissions (fix 403 on POST /api/v1/function-units)
-- V2 only assigned admin-001 and tech-director-001; developer, dev_lead, senior_dev had no sys_user_roles.

INSERT INTO sys_role_assignments (id, role_id, target_type, target_id, assigned_at, assigned_by)
VALUES
    ('ra-developer-developer', 'DEVELOPER_ROLE', 'USER', '635281da-5dbb-4118-9610-dd4d6318dcd6', NOW(), 'system'),
    ('ra-devlead-teamleader', 'TEAM_LEADER_ROLE', 'USER', 'b4fe69e8-7313-48c5-865b-878231c24b9f', NOW(), 'system'),
    ('ra-seniordev-developer', 'DEVELOPER_ROLE', 'USER', '7e468949-05ea-4c41-8ab5-484fb0626185', NOW(), 'system')
ON CONFLICT (role_id, target_type, target_id) DO NOTHING;

-- Sync new role assignments to sys_user_roles (same pattern as V2 section 13)
INSERT INTO sys_user_roles (id, user_id, role_id, assigned_at, assigned_by)
SELECT
    'ur-' || sra.target_id || '-' || sra.role_id,
    sra.target_id,
    sra.role_id,
    sra.assigned_at,
    COALESCE(sra.assigned_by, 'system')
FROM sys_role_assignments sra
WHERE sra.target_type = 'USER'
AND NOT EXISTS (
    SELECT 1 FROM sys_user_roles sur
    WHERE sur.user_id = sra.target_id AND sur.role_id = sra.role_id
);
