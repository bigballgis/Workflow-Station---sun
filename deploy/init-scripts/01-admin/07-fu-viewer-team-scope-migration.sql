-- =====================================================
-- FU Viewer + Team-Scope Migration (idempotent / upgrade-safe)
-- =====================================================
-- 支持存量库升级到「团队 scope × 能力角色」二维模型：
--   1) 确保 FU_VIEWER 角色与权限存在（新库已由 01/02 脚本创建，此处为存量库补齐）
--   2) 创建「Default Development Team」CUSTOM 虚拟组，绑定 FU_VIEWER（团队只读基线）
--   3) 将当前「无任何团队分配」的启用 FU 归入默认团队，避免切换后对
--      非 ADMIN / 非 TECH_LEAD 用户完全不可见
-- ADMIN / TECH_LEAD 为全局视角，不受团队隔离影响。
-- 幂等：可在新库初始化与存量库手工升级两种场景重复执行。
-- =====================================================

\echo '========================================='
\echo 'FU Viewer + Team-Scope migration...'
\echo '========================================='

-- 1) FU_VIEWER 角色（团队只读基线）
INSERT INTO sys_roles (id, code, name, type, display_name, status, is_system, created_at, updated_at)
VALUES ('role-fu-viewer', 'FU_VIEWER', 'Function Unit Viewer', 'DEVELOPER',
        'Read-only access to a team''s function units in the developer workstation (no edit permissions)',
        'ACTIVE', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO NOTHING;

-- 2) FU_VIEWER 开发者权限（仅 FUNCTION_UNIT_VIEW）
INSERT INTO sys_developer_role_permissions (id, role_id, permission, created_at)
SELECT gen_random_uuid()::varchar, 'role-fu-viewer', 'FUNCTION_UNIT_VIEW', CURRENT_TIMESTAMP
ON CONFLICT (role_id, permission) DO NOTHING;

-- 3) 默认团队虚拟组（CUSTOM）+ 绑定 FU_VIEWER
INSERT INTO sys_virtual_groups (id, code, name, type, display_name, status, created_at, updated_at)
VALUES ('vg-default-dev-team', 'DEFAULT_DEV_TEAM', 'Default Development Team', 'CUSTOM',
        'Fallback team for function units migrated before team-based visibility', 'ACTIVE',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO NOTHING;

INSERT INTO sys_virtual_group_roles (id, virtual_group_id, role_id, created_at, created_by)
VALUES ('vgr-default-dev-team-viewer', 'vg-default-dev-team', 'role-fu-viewer', CURRENT_TIMESTAMP, 'system')
ON CONFLICT (virtual_group_id, role_id) DO NOTHING;

-- 4) 将无团队分配的启用 FU 归入默认团队（新库时表为空 → 无操作）
INSERT INTO dw_function_unit_dev_groups (function_unit_id, virtual_group_id, created_at, created_by)
SELECT fu.id, 'vg-default-dev-team', CURRENT_TIMESTAMP, 'system'
FROM dw_function_units fu
WHERE fu.enabled = true
  AND NOT EXISTS (
      SELECT 1 FROM dw_function_unit_dev_groups g WHERE g.function_unit_id = fu.id
  )
ON CONFLICT (function_unit_id, virtual_group_id) DO NOTHING;

\echo '✓ FU Viewer + Team-Scope migration done'
