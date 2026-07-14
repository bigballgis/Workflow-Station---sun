-- =====================================================
-- Initialize Developer Role Permissions
-- 与 admin-center DeveloperPermissionService 默认映射对齐
-- =====================================================

\echo '========================================='
\echo 'Initializing Developer Role Permissions...'
\echo '========================================='

-- TECH_LEAD: 全部 DeveloperPermission 枚举
INSERT INTO sys_developer_role_permissions (id, role_id, permission, created_at)
SELECT 
    gen_random_uuid()::varchar,
    'role-tech-lead',
    p.permission,
    CURRENT_TIMESTAMP
FROM (VALUES 
    ('FUNCTION_UNIT_CREATE'), ('FUNCTION_UNIT_UPDATE'), ('FUNCTION_UNIT_DELETE'), ('FUNCTION_UNIT_VIEW'),
    ('FUNCTION_UNIT_DEVELOP'), ('FUNCTION_UNIT_PUBLISH'), ('FUNCTION_UNIT_ASSIGN_DEV_GROUP'),
    ('FORM_CREATE'), ('FORM_UPDATE'), ('FORM_DELETE'), ('FORM_VIEW'),
    ('PROCESS_CREATE'), ('PROCESS_UPDATE'), ('PROCESS_DELETE'), ('PROCESS_VIEW'),
    ('TABLE_CREATE'), ('TABLE_UPDATE'), ('TABLE_DELETE'), ('TABLE_VIEW'),
    ('ACTION_CREATE'), ('ACTION_UPDATE'), ('ACTION_DELETE'), ('ACTION_VIEW')
) AS p(permission)
ON CONFLICT (role_id, permission) DO NOTHING;

\echo '✓ Initialized permissions for TECH_LEAD role'

-- TEAM_LEAD: 与 DEVELOPER 相同设计能力 + 功能单元创建/删除/分配开发组
INSERT INTO sys_developer_role_permissions (id, role_id, permission, created_at)
SELECT 
    gen_random_uuid()::varchar,
    'role-team-lead',
    p.permission,
    CURRENT_TIMESTAMP
FROM (VALUES 
    ('FUNCTION_UNIT_CREATE'), ('FUNCTION_UNIT_UPDATE'), ('FUNCTION_UNIT_DELETE'), ('FUNCTION_UNIT_VIEW'),
    ('FUNCTION_UNIT_DEVELOP'), ('FUNCTION_UNIT_PUBLISH'), ('FUNCTION_UNIT_ASSIGN_DEV_GROUP'),
    ('FORM_CREATE'), ('FORM_UPDATE'), ('FORM_DELETE'), ('FORM_VIEW'),
    ('PROCESS_CREATE'), ('PROCESS_UPDATE'), ('PROCESS_DELETE'), ('PROCESS_VIEW'),
    ('TABLE_CREATE'), ('TABLE_UPDATE'), ('TABLE_DELETE'), ('TABLE_VIEW'),
    ('ACTION_CREATE'), ('ACTION_UPDATE'), ('ACTION_DELETE'), ('ACTION_VIEW')
) AS p(permission)
ON CONFLICT (role_id, permission) DO NOTHING;

\echo '✓ Initialized permissions for TEAM_LEAD role'

-- DEVELOPER: TEAM_LEAD 去掉功能单元创建/删除/分配开发组
INSERT INTO sys_developer_role_permissions (id, role_id, permission, created_at)
SELECT 
    gen_random_uuid()::varchar,
    'role-developer',
    p.permission,
    CURRENT_TIMESTAMP
FROM (VALUES 
    ('FUNCTION_UNIT_UPDATE'), ('FUNCTION_UNIT_VIEW'),
    ('FUNCTION_UNIT_DEVELOP'), ('FUNCTION_UNIT_PUBLISH'),
    ('FORM_CREATE'), ('FORM_UPDATE'), ('FORM_DELETE'), ('FORM_VIEW'),
    ('PROCESS_CREATE'), ('PROCESS_UPDATE'), ('PROCESS_DELETE'), ('PROCESS_VIEW'),
    ('TABLE_CREATE'), ('TABLE_UPDATE'), ('TABLE_DELETE'), ('TABLE_VIEW'),
    ('ACTION_CREATE'), ('ACTION_UPDATE'), ('ACTION_DELETE'), ('ACTION_VIEW')
) AS p(permission)
ON CONFLICT (role_id, permission) DO NOTHING;

\echo '✓ Initialized permissions for DEVELOPER role'

-- FU_VIEWER: 团队只读基线，仅查看功能单元（子资源读操作均以 FUNCTION_UNIT_VIEW 门禁）
INSERT INTO sys_developer_role_permissions (id, role_id, permission, created_at)
SELECT 
    gen_random_uuid()::varchar,
    'role-fu-viewer',
    p.permission,
    CURRENT_TIMESTAMP
FROM (VALUES 
    ('FUNCTION_UNIT_VIEW')
) AS p(permission)
ON CONFLICT (role_id, permission) DO NOTHING;

\echo '✓ Initialized permissions for FU_VIEWER role'
\echo ''
\echo 'Developer role permissions initialized successfully!'
\echo '========================================='
