-- =====================================================
-- E2E 仿真：业务单元 + 门户测试用户
-- =====================================================
-- 与默认管理员相同密码: password
-- BCrypt: $2a$10$P/xQaseE4Hr8/9fhSws86ez3nTUDLUGC8XeQueVX4QKZmdM/LeiYa
-- 用途：采购/报销/简单审批等多租户与数字信贷虚拟组流程联调
-- =====================================================

-- 虚拟组：聚合所有 E2E 账号（不绑定额外角色亦可登录门户）
INSERT INTO sys_virtual_groups (id, code, name, type, description, status, created_at, updated_at)
VALUES (
    'vg-e2e-workflow',
    'E2E_WORKFLOW_SIMULATION',
    'E2E 流程仿真用户组',
    'CUSTOM',
    '端到端测试：报销、采购、审批、信贷流水线仿真账号',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (code) DO UPDATE SET
    name        = EXCLUDED.name,
    description = EXCLUDED.description,
    updated_at  = CURRENT_TIMESTAMP;

-- 业务单元：总部 + 财务共享 + 数字化部门
INSERT INTO sys_business_units (
    id, code, name, parent_id, level, path, sort_order, status, description,
    created_at, created_by, updated_at, updated_by
)
VALUES
(
    'bu-e2e-hq',
    'E2E_HQ',
    '华东制造集团总部',
    NULL,
    1,
    '/E2E_HQ',
    10,
    'ACTIVE',
    '仿真集团根节点',
    CURRENT_TIMESTAMP,
    'system',
    CURRENT_TIMESTAMP,
    'system'
),
(
    'bu-e2e-finance',
    'E2E_FINANCE',
    '共享财务中心',
    'bu-e2e-hq',
    2,
    '/E2E_HQ/E2E_FINANCE',
    20,
    'ACTIVE',
    '报销、采购审批归属组织',
    CURRENT_TIMESTAMP,
    'system',
    CURRENT_TIMESTAMP,
    'system'
),
(
    'bu-e2e-it',
    'E2E_IT',
    '数字化运营部',
    'bu-e2e-hq',
    2,
    '/E2E_HQ/E2E_IT',
    30,
    'ACTIVE',
    '功能单元设计与流程运维',
    CURRENT_TIMESTAMP,
    'system',
    CURRENT_TIMESTAMP,
    'system'
)
ON CONFLICT (code) DO UPDATE SET
    name        = EXCLUDED.name,
    parent_id   = EXCLUDED.parent_id,
    level       = EXCLUDED.level,
    path        = EXCLUDED.path,
    description = EXCLUDED.description,
    updated_at  = CURRENT_TIMESTAMP,
    updated_by  = 'system';

-- 财务中心可用「部门经理」角色（绑定到业务单元）
INSERT INTO sys_business_unit_roles (id, business_unit_id, role_id, created_at, created_by)
VALUES (
    'bur-e2e-fin-mgr',
    'bu-e2e-finance',
    'role-manager',
    CURRENT_TIMESTAMP,
    'system'
)
ON CONFLICT (business_unit_id, role_id) DO NOTHING;

-- 测试用户（7 人：申请人、经理、财务、信贷四岗）
INSERT INTO sys_users (
    id, username, password_hash, email, display_name, full_name,
    employee_id, position, status, language, must_change_password,
    created_at, updated_at, deleted
)
VALUES
(
    'user-e2e-zhangwei',
    'e2e_zhangwei',
    '$2a$10$P/xQaseE4Hr8/9fhSws86ez3nTUDLUGC8XeQueVX4QKZmdM/LeiYa',
    'zhangwei@e2e.workflow.local',
    '张伟',
    '张伟',
    'E26-1001',
    '业务专员',
    'ACTIVE',
    'zh_CN',
    false,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    false
),
(
    'user-e2e-lina',
    'e2e_lina',
    '$2a$10$P/xQaseE4Hr8/9fhSws86ez3nTUDLUGC8XeQueVX4QKZmdM/LeiYa',
    'lina@e2e.workflow.local',
    '李娜',
    '李娜',
    'E26-2001',
    '财务中心经理',
    'ACTIVE',
    'zh_CN',
    false,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    false
),
(
    'user-e2e-wangfang',
    'e2e_wangfang',
    '$2a$10$P/xQaseE4Hr8/9fhSws86ez3nTUDLUGC8XeQueVX4QKZmdM/LeiYa',
    'wangfang@e2e.workflow.local',
    '王芳',
    '王芳',
    'E26-1002',
    '费用会计',
    'ACTIVE',
    'zh_CN',
    false,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    false
),
(
    'user-e2e-zhaomin',
    'e2e_zhaomin',
    '$2a$10$P/xQaseE4Hr8/9fhSws86ez3nTUDLUGC8XeQueVX4QKZmdM/LeiYa',
    'zhaomin@e2e.workflow.local',
    '赵敏',
    '赵敏',
    'E26-3001',
    '信贷材料审核员',
    'ACTIVE',
    'zh_CN',
    false,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    false
),
(
    'user-e2e-sunqiang',
    'e2e_sunqiang',
    '$2a$10$P/xQaseE4Hr8/9fhSws86ez3nTUDLUGC8XeQueVX4QKZmdM/LeiYa',
    'sunqiang@e2e.workflow.local',
    '孙强',
    '孙强',
    'E26-3002',
    '信贷调查员',
    'ACTIVE',
    'zh_CN',
    false,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    false
),
(
    'user-e2e-zhoujie',
    'e2e_zhoujie',
    '$2a$10$P/xQaseE4Hr8/9fhSws86ez3nTUDLUGC8XeQueVX4QKZmdM/LeiYa',
    'zhoujie@e2e.workflow.local',
    '周杰',
    '周杰',
    'E26-3003',
    '风控专员',
    'ACTIVE',
    'zh_CN',
    false,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    false
),
(
    'user-e2e-wugang',
    'e2e_wugang',
    '$2a$10$P/xQaseE4Hr8/9fhSws86ez3nTUDLUGC8XeQueVX4QKZmdM/LeiYa',
    'wugang@e2e.workflow.local',
    '吴刚',
    '吴刚',
    'E26-3004',
    '放款执行',
    'ACTIVE',
    'zh_CN',
    false,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    false
)
ON CONFLICT (username) DO UPDATE SET
    email           = EXCLUDED.email,
    display_name    = EXCLUDED.display_name,
    full_name       = EXCLUDED.full_name,
    employee_id     = EXCLUDED.employee_id,
    position        = EXCLUDED.position,
    password_hash   = EXCLUDED.password_hash,
    updated_at      = CURRENT_TIMESTAMP;

-- 用户 — 业务单元隶属
INSERT INTO sys_user_business_units (id, user_id, business_unit_id, created_at, created_by)
VALUES
('ubu-e2e-zw-fin', 'user-e2e-zhangwei', 'bu-e2e-finance', CURRENT_TIMESTAMP, 'system'),
('ubu-e2e-ln-fin', 'user-e2e-lina',      'bu-e2e-finance', CURRENT_TIMESTAMP, 'system'),
('ubu-e2e-wf-fin', 'user-e2e-wangfang',  'bu-e2e-finance', CURRENT_TIMESTAMP, 'system'),
('ubu-e2e-zm-fin', 'user-e2e-zhaomin',   'bu-e2e-finance', CURRENT_TIMESTAMP, 'system'),
('ubu-e2e-sq-fin', 'user-e2e-sunqiang',  'bu-e2e-finance', CURRENT_TIMESTAMP, 'system'),
('ubu-e2e-zj-fin', 'user-e2e-zhoujie',   'bu-e2e-finance', CURRENT_TIMESTAMP, 'system'),
('ubu-e2e-wg-fin', 'user-e2e-wugang',    'bu-e2e-finance', CURRENT_TIMESTAMP, 'system'),
('ubu-e2e-44027893', 'user-test-44027893', 'bu-e2e-finance', CURRENT_TIMESTAMP, 'system')
ON CONFLICT (user_id, business_unit_id) DO NOTHING;

-- 李娜：财务中心 + 部门经理（业务单元角色）
INSERT INTO sys_user_business_unit_roles (id, user_id, business_unit_id, role_id, created_at, created_by)
VALUES (
    'ubur-e2e-lina-mgr',
    'user-e2e-lina',
    'bu-e2e-finance',
    'role-manager',
    CURRENT_TIMESTAMP,
    'system'
)
ON CONFLICT (user_id, business_unit_id, role_id) DO NOTHING;

-- 虚拟组成员：全员进入 E2E 组；经理进入 MANAGERS 虚拟组（与角色脚本中的 vg-managers 一致）
INSERT INTO sys_virtual_group_members (id, group_id, user_id, joined_at, added_by)
VALUES
('vgm-e2e-zw', 'vg-e2e-workflow', 'user-e2e-zhangwei', CURRENT_TIMESTAMP, 'system'),
('vgm-e2e-ln', 'vg-e2e-workflow', 'user-e2e-lina',      CURRENT_TIMESTAMP, 'system'),
('vgm-e2e-wf', 'vg-e2e-workflow', 'user-e2e-wangfang',  CURRENT_TIMESTAMP, 'system'),
('vgm-e2e-zm', 'vg-e2e-workflow', 'user-e2e-zhaomin',   CURRENT_TIMESTAMP, 'system'),
('vgm-e2e-sq', 'vg-e2e-workflow', 'user-e2e-sunqiang',  CURRENT_TIMESTAMP, 'system'),
('vgm-e2e-zj', 'vg-e2e-workflow', 'user-e2e-zhoujie',   CURRENT_TIMESTAMP, 'system'),
('vgm-e2e-wg', 'vg-e2e-workflow', 'user-e2e-wugang',    CURRENT_TIMESTAMP, 'system'),
('vgm-e2e-44027893', 'vg-e2e-workflow', 'user-test-44027893', CURRENT_TIMESTAMP, 'system')
ON CONFLICT (group_id, user_id) DO NOTHING;

INSERT INTO sys_virtual_group_members (id, group_id, user_id, joined_at, added_by)
VALUES (
    'vgm-e2e-ln-mgr',
    'vg-managers',
    'user-e2e-lina',
    CURRENT_TIMESTAMP,
    'system'
)
ON CONFLICT (group_id, user_id) DO NOTHING;
