-- =====================================================
-- Initialize Developer Role Permissions
-- =====================================================
-- This script initializes the default permissions for developer roles
-- in the sys_developer_role_permissions table.
-- =====================================================

\echo '========================================='
\echo 'Initializing Developer Role Permissions...'
\echo '========================================='

-- TECH_LEAD: All developer permissions (22 permissions)
INSERT INTO sys_developer_role_permissions (id, role_id, permission, created_at)
SELECT 
    gen_random_uuid()::varchar,
    'role-tech-lead',
    p.permission,
    CURRENT_TIMESTAMP
FROM (VALUES 
    ('FUNCTION_UNIT_CREATE'), ('FUNCTION_UNIT_UPDATE'), ('FUNCTION_UNIT_DELETE'), ('FUNCTION_UNIT_VIEW'),
    ('FUNCTION_UNIT_DEVELOP'), ('FUNCTION_UNIT_PUBLISH'),
    ('FORM_CREATE'), ('FORM_UPDATE'), ('FORM_DELETE'), ('FORM_VIEW'),
    ('PROCESS_CREATE'), ('PROCESS_UPDATE'), ('PROCESS_DELETE'), ('PROCESS_VIEW'),
    ('TABLE_CREATE'), ('TABLE_UPDATE'), ('TABLE_DELETE'), ('TABLE_VIEW'),
    ('ACTION_CREATE'), ('ACTION_UPDATE'), ('ACTION_DELETE'), ('ACTION_VIEW')
) AS p(permission)
ON CONFLICT (role_id, permission) DO NOTHING;

\echo '✓ Initialized permissions for TECH_LEAD role (22 permissions)'

-- TEAM_LEAD: Create, update, view, develop, publish permissions (22 permissions)
INSERT INTO sys_developer_role_permissions (id, role_id, permission, created_at)
SELECT 
    gen_random_uuid()::varchar,
    'role-team-lead',
    p.permission,
    CURRENT_TIMESTAMP
FROM (VALUES 
    ('FUNCTION_UNIT_CREATE'), ('FUNCTION_UNIT_UPDATE'), ('FUNCTION_UNIT_DELETE'), ('FUNCTION_UNIT_VIEW'),
    ('FUNCTION_UNIT_DEVELOP'), ('FUNCTION_UNIT_PUBLISH'),
    ('FORM_CREATE'), ('FORM_UPDATE'), ('FORM_DELETE'), ('FORM_VIEW'),
    ('PROCESS_CREATE'), ('PROCESS_UPDATE'), ('PROCESS_DELETE'), ('PROCESS_VIEW'),
    ('TABLE_CREATE'), ('TABLE_UPDATE'), ('TABLE_DELETE'), ('TABLE_VIEW'),
    ('ACTION_CREATE'), ('ACTION_UPDATE'), ('ACTION_DELETE'), ('ACTION_VIEW')
) AS p(permission)
ON CONFLICT (role_id, permission) DO NOTHING;

\echo '✓ Initialized permissions for TEAM_LEAD role (22 permissions)'

-- DEVELOPER: View, update, develop permissions (no create or delete) (10 permissions)
INSERT INTO sys_developer_role_permissions (id, role_id, permission, created_at)
SELECT 
    gen_random_uuid()::varchar,
    'role-developer',
    p.permission,
    CURRENT_TIMESTAMP
FROM (VALUES 
    ('FUNCTION_UNIT_VIEW'), ('FUNCTION_UNIT_DEVELOP'),
    ('FORM_VIEW'), ('FORM_UPDATE'),
    ('PROCESS_VIEW'), ('PROCESS_UPDATE'),
    ('TABLE_VIEW'),
    ('ACTION_VIEW'), ('ACTION_UPDATE')
) AS p(permission)
ON CONFLICT (role_id, permission) DO NOTHING;

\echo '✓ Initialized permissions for DEVELOPER role (9 permissions)'
\echo ''
\echo 'Developer role permissions initialized successfully!'
\echo '========================================='
