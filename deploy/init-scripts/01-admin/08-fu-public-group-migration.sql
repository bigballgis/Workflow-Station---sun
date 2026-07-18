-- =====================================================
-- FU Public Group Migration (idempotent / upgrade-safe)
-- =====================================================
-- 「进入工作区选团队 + 每个团队只看本团队与 Public」模型的数据侧：
--   1) 创建内置 Public 开发组（vg-dev-public）——其功能单元对所有能进入工作区者始终可见（叠加层）。
--      用户不会成为该组成员，故它不出现在「我的团队」选择列表中，仅由后端代码叠加可见。
--   2) 历史功能单元归入 Public：
--        (a) 完全无团队分配的 FU
--        (b) 分配给过渡期「Default Development Team」(vg-default-dev-team) 的 FU
--      → 保证切换到新模型后这些历史 FU 对所有用户仍可见。
--   3) 清理已被 Public 取代的 vg-default-dev-team 分配（该组当初仅为可见性兜底）。
-- 说明：显式分配给「真实团队」（非 default）的 FU 保持团队隔离，不被公开。
-- 幂等：仅影响「无分配 / default 团队」的 FU，可安全重复执行；不会误将新建的团队 FU 公开。
-- =====================================================

\echo '========================================='
\echo 'FU Public-group migration...'
\echo '========================================='

-- 1) 内置 Public 开发组（CUSTOM，无成员，作为始终可见的叠加层）
INSERT INTO sys_virtual_groups (id, code, name, type, display_name, status, created_at, updated_at)
VALUES ('vg-dev-public', 'DEV_TEAM_PUBLIC', 'Public', 'CUSTOM',
        'Built-in public group: its function units are visible to every developer-workstation user', 'ACTIVE',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO NOTHING;

-- 2) 历史 FU → Public（无分配的 + 过渡期默认团队的）
INSERT INTO dw_function_unit_dev_groups (function_unit_id, virtual_group_id, created_at, created_by)
SELECT DISTINCT fu.id, 'vg-dev-public', CURRENT_TIMESTAMP, 'system'
FROM dw_function_units fu
WHERE NOT EXISTS (
        SELECT 1 FROM dw_function_unit_dev_groups g WHERE g.function_unit_id = fu.id
      )
   OR EXISTS (
        SELECT 1 FROM dw_function_unit_dev_groups g2
        WHERE g2.function_unit_id = fu.id AND g2.virtual_group_id = 'vg-default-dev-team'
      )
ON CONFLICT (function_unit_id, virtual_group_id) DO NOTHING;

-- 3) 清理被 Public 取代的过渡期默认团队分配
DELETE FROM dw_function_unit_dev_groups WHERE virtual_group_id = 'vg-default-dev-team';

\echo '✓ FU Public-group migration done'
