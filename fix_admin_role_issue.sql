-- ========================================
-- 诊断和修复 Admin 角色问题
-- ========================================

\echo '========================================='
\echo '步骤 1: 检查 sys_user_roles 表中的 role_id'
\echo '========================================='
SELECT 
    id,
    user_id,
    role_id,
    assigned_at
FROM sys_user_roles 
WHERE user_id = 'admin-001';

\echo ''
\echo '========================================='
\echo '步骤 2: 检查 sys_roles 表中是否存在对应的角色'
\echo '========================================='
SELECT 
    id,
    code,
    name,
    type,
    status,
    is_system
FROM sys_roles 
WHERE id IN (
    SELECT role_id FROM sys_user_roles WHERE user_id = 'admin-001'
);

\echo ''
\echo '========================================='
\echo '步骤 3: 检查 SYS_ADMIN_ROLE 是否存在'
\echo '========================================='
SELECT 
    id,
    code,
    name,
    type,
    status,
    is_system
FROM sys_roles 
WHERE code = 'SYS_ADMIN' OR id = 'SYS_ADMIN_ROLE';

\echo ''
\echo '========================================='
\echo '步骤 4: 检查所有 ADMIN 类型的角色'
\echo '========================================='
SELECT 
    id,
    code,
    name,
    type,
    status,
    is_system
FROM sys_roles 
WHERE type = 'ADMIN'
ORDER BY code;

\echo ''
\echo '========================================='
\echo '诊断完成！'
\echo '========================================='
\echo ''
\echo '如果步骤 2 返回 0 行，说明 role_id 指向的角色不存在'
\echo '如果步骤 3 返回 0 行，说明 SYS_ADMIN_ROLE 角色缺失'
\echo '如果步骤 4 返回 0 行，说明所有 ADMIN 角色都缺失'
\echo ''
\echo '========================================='
\echo '修复方案：'
\echo '========================================='
\echo ''
\echo '方案 1: 如果 SYS_ADMIN_ROLE 不存在，执行以下 SQL：'
\echo ''
\echo 'INSERT INTO sys_roles (id, code, name, type, description, status, is_system, created_at, updated_at)'
\echo 'VALUES ('
\echo '    ''SYS_ADMIN_ROLE'','
\echo '    ''SYS_ADMIN'','
\echo '    ''System Administrator'','
\echo '    ''ADMIN'','
\echo '    ''Full system access'','
\echo '    ''ACTIVE'','
\echo '    true,'
\echo '    CURRENT_TIMESTAMP,'
\echo '    CURRENT_TIMESTAMP'
\echo ') ON CONFLICT (id) DO NOTHING;'
\echo ''
\echo '方案 2: 如果 sys_user_roles 中的 role_id 不正确，执行以下 SQL：'
\echo ''
\echo 'UPDATE sys_user_roles'
\echo 'SET role_id = ''SYS_ADMIN_ROLE'''
\echo 'WHERE user_id = ''admin-001'';'
\echo ''
\echo '方案 3: 如果 sys_user_roles 记录不存在，执行以下 SQL：'
\echo ''
\echo 'INSERT INTO sys_user_roles (id, user_id, role_id, assigned_at, assigned_by)'
\echo 'VALUES ('
\echo '    ''ur-admin-001-SYS_ADMIN_ROLE'','
\echo '    ''admin-001'','
\echo '    ''SYS_ADMIN_ROLE'','
\echo '    CURRENT_TIMESTAMP,'
\echo '    ''system'''
\echo ') ON CONFLICT (id) DO NOTHING;'
\echo ''
