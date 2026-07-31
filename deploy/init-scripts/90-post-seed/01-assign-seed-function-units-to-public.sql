-- =====================================================
-- Post-seed: 种子功能单元归入 Public 开发组（幂等）
-- =====================================================
-- 背景：`01-admin/08-fu-public-group-migration.sql` 负责创建内置 Public 组
-- （vg-dev-public）并把「无团队归属」的历史 FU 归入该组。但它在 [3/6] 阶段执行，
-- 早于 08-/15-/16-/17-/18-/19- 各 FU 种子包（[4/6]、[5x/6]），因此在全新库上
-- 它的 FU 回填部分恒为 no-op —— 种子 FU 全部落地为「无归属」孤儿。
--
-- 后果：`FunctionUnitWorkspaceAccessService.visibleFunctionUnitIds()` 完全按组
-- 归属计算可见性，没有「未分配 = 全员可见」兜底。孤儿 FU 只在「管理员且未选团队」
-- 时可见；一旦选了任何团队，Developer Workstation 的功能单元列表就是空的。
--
-- 本脚本在所有种子包之后补齐该归属，使新装环境行为与 08 迁移的既定语义一致。
--
-- 幂等：仅插入「当前无任何组归属」的 FU；已显式分配到真实团队的 FU（如
-- Platform Showcase → vg-tech-leads，用于工作区权限演示）保持团队隔离，不被公开。
-- =====================================================

\echo '========================================='
\echo 'Post-seed: assigning unassigned function units to Public dev group...'
\echo '========================================='

-- 兜底：Public 组必须存在（正常情况下 01-admin/08 已建；此处防止单独执行本脚本时外键悬空）
INSERT INTO sys_virtual_groups (id, code, name, type, display_name, status, created_at, updated_at)
VALUES ('vg-dev-public', 'DEV_TEAM_PUBLIC', 'Public', 'CUSTOM',
        'Built-in public group: its function units are visible to every developer-workstation user', 'ACTIVE',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO NOTHING;

-- 无任何组归属的 FU → Public
INSERT INTO dw_function_unit_dev_groups (function_unit_id, virtual_group_id, created_at, created_by)
SELECT fu.id, 'vg-dev-public', CURRENT_TIMESTAMP, 'system'
FROM dw_function_units fu
WHERE NOT EXISTS (
        SELECT 1 FROM dw_function_unit_dev_groups g WHERE g.function_unit_id = fu.id
      )
ON CONFLICT (function_unit_id, virtual_group_id) DO NOTHING;

DO $$
DECLARE
    v_public  INTEGER;
    v_orphan  INTEGER;
BEGIN
    SELECT COUNT(*) INTO v_public
    FROM dw_function_unit_dev_groups WHERE virtual_group_id = 'vg-dev-public';

    SELECT COUNT(*) INTO v_orphan
    FROM dw_function_units fu
    WHERE NOT EXISTS (
        SELECT 1 FROM dw_function_unit_dev_groups g WHERE g.function_unit_id = fu.id
    );

    RAISE NOTICE '  Function units in Public group: %', v_public;
    RAISE NOTICE '  Function units still unassigned: %', v_orphan;

    IF v_orphan > 0 THEN
        RAISE WARNING '  % function unit(s) have no dev-group assignment and will be invisible in the workspace', v_orphan;
    END IF;
END $$;

\echo '✓ Post-seed Public dev-group assignment done'
