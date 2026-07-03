-- =====================================================
-- View 访问管控 — Portal 手测专用账号 + MCY View 规则样例
-- =====================================================
-- 密码（全部）: password
-- BCrypt: $2a$10$P/xQaseE4Hr8/9fhSws86ez3nTUDLUGC8XeQueVX4QKZmdM/LeiYa
-- 依赖: 01-admin/01-create-roles-and-groups.sql
--       01-admin/05-e2e-test-users-and-business-units.sql
--       18-MCY/init.sql（FU fu-20260505-thwmut，View 50205–50207）
-- =====================================================

-- 数字化部也绑定 Department Manager（供 view_wrong_bu 使用）
INSERT INTO sys_business_unit_roles (id, business_unit_id, role_id, created_at, created_by)
VALUES ('bur-e2e-it-mgr', 'bu-e2e-it', 'role-manager', CURRENT_TIMESTAMP, 'system')
ON CONFLICT (business_unit_id, role_id) DO NOTHING;

INSERT INTO sys_business_unit_roles (id, business_unit_id, role_id, created_at, created_by)
VALUES ('bur-e2e-fin-dev', 'bu-e2e-finance', 'role-developer', CURRENT_TIMESTAMP, 'system')
ON CONFLICT (business_unit_id, role_id) DO NOTHING;

-- ---------- 测试用户 ----------
INSERT INTO sys_users (
    id, username, password_hash, email, display_name, full_name,
    employee_id, position, status, language, must_change_password,
    created_at, updated_at, deleted
)
VALUES
(
    'user-view-admin',
    'view_admin',
    '$2a$10$P/xQaseE4Hr8/9fhSws86ez3nTUDLUGC8XeQueVX4QKZmdM/LeiYa',
    'view_admin@test.local',
    'View Test SysAdmin',
    'View Test System Administrator',
    'VIEW-T-ADM',
    'View access SYS_ADMIN',
    'ACTIVE', 'zh_CN', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, false
),
(
    'user-view-allowed',
    'view_allowed',
    '$2a$10$P/xQaseE4Hr8/9fhSws86ez3nTUDLUGC8XeQueVX4QKZmdM/LeiYa',
    'view_allowed@test.local',
    'View Test Allowed',
    'View Test Allowed User',
    'VIEW-T-OK',
    'BU+Role 匹配 View',
    'ACTIVE', 'zh_CN', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, false
),
(
    'user-view-wrong-bu',
    'view_wrong_bu',
    '$2a$10$P/xQaseE4Hr8/9fhSws86ez3nTUDLUGC8XeQueVX4QKZmdM/LeiYa',
    'view_wrong_bu@test.local',
    'View Test Wrong BU',
    'View Test Wrong BU User',
    'VIEW-T-BU',
    'BU 不匹配',
    'ACTIVE', 'zh_CN', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, false
),
(
    'user-view-wrong-role',
    'view_wrong_role',
    '$2a$10$P/xQaseE4Hr8/9fhSws86ez3nTUDLUGC8XeQueVX4QKZmdM/LeiYa',
    'view_wrong_role@test.local',
    'View Test Wrong Role',
    'View Test Wrong Role User',
    'VIEW-T-ROL',
    'Role 不匹配',
    'ACTIVE', 'zh_CN', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, false
),
(
    'user-view-nofu',
    'view_nofu',
    '$2a$10$P/xQaseE4Hr8/9fhSws86ez3nTUDLUGC8XeQueVX4QKZmdM/LeiYa',
    'view_nofu@test.local',
    'View Test No FU',
    'View Test No FU Access',
    'VIEW-T-NFU',
    '无 MCY FU 门禁',
    'ACTIVE', 'zh_CN', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, false
)
ON CONFLICT (username) DO UPDATE SET
    password_hash = EXCLUDED.password_hash,
    display_name  = EXCLUDED.display_name,
    status        = 'ACTIVE',
    updated_at    = CURRENT_TIMESTAMP;

-- 业务单元隶属
INSERT INTO sys_user_business_units (id, user_id, business_unit_id, created_at, created_by)
VALUES
    ('ubu-view-allowed', 'user-view-allowed',   'bu-e2e-finance', CURRENT_TIMESTAMP, 'system'),
    ('ubu-view-wrong-bu', 'user-view-wrong-bu', 'bu-e2e-it',      CURRENT_TIMESTAMP, 'system'),
    ('ubu-view-wrong-role', 'user-view-wrong-role', 'bu-e2e-finance', CURRENT_TIMESTAMP, 'system'),
    ('ubu-view-nofu', 'user-view-nofu', 'bu-e2e-finance', CURRENT_TIMESTAMP, 'system')
ON CONFLICT (user_id, business_unit_id) DO NOTHING;

-- 业务单元角色（Portal profileContext=PORTAL）
INSERT INTO sys_user_business_unit_roles (id, user_id, business_unit_id, role_id, created_at, created_by)
VALUES
    ('ubur-view-allowed',   'user-view-allowed',   'bu-e2e-finance', 'role-manager',   CURRENT_TIMESTAMP, 'system'),
    ('ubur-view-wrong-bu',  'user-view-wrong-bu',  'bu-e2e-it',      'role-manager',   CURRENT_TIMESTAMP, 'system'),
    ('ubur-view-wrong-role','user-view-wrong-role','bu-e2e-finance', 'role-developer', CURRENT_TIMESTAMP, 'system'),
    ('ubur-view-nofu',      'user-view-nofu',      'bu-e2e-finance', 'role-auditor',   CURRENT_TIMESTAMP, 'system')
ON CONFLICT (user_id, business_unit_id, role_id) DO NOTHING;

-- SYS_ADMIN：view_admin 加入 System Administrators 虚拟组
INSERT INTO sys_virtual_group_members (id, group_id, user_id, joined_at, added_by)
VALUES ('vgm-view-admin', 'vg-sys-admins', 'user-view-admin', CURRENT_TIMESTAMP, 'system')
ON CONFLICT (group_id, user_id) DO NOTHING;

-- view_admin 需 UBR 才能进入 FULL 门户（非权限自助模式）；SYS_ADMIN 特权仍来自虚拟组
INSERT INTO sys_user_business_units (id, user_id, business_unit_id, created_at, created_by)
VALUES ('ubu-view-admin', 'user-view-admin', 'bu-e2e-finance', CURRENT_TIMESTAMP, 'system')
ON CONFLICT (user_id, business_unit_id) DO NOTHING;

INSERT INTO sys_user_business_unit_roles (id, user_id, business_unit_id, role_id, created_at, created_by)
VALUES ('ubur-view-admin-fin-mgr', 'user-view-admin', 'bu-e2e-finance', 'role-manager', CURRENT_TIMESTAMP, 'system')
ON CONFLICT (user_id, business_unit_id, role_id) DO NOTHING;

INSERT INTO sys_virtual_group_members (id, group_id, user_id, joined_at, added_by)
VALUES ('vgm-view-admin-workflow', 'vg-e2e-workflow', 'user-view-admin', CURRENT_TIMESTAMP, 'system')
ON CONFLICT (group_id, user_id) DO NOTHING;

-- 其余测试用户加入 E2E 门户组（可登录 Portal）
INSERT INTO sys_virtual_group_members (id, group_id, user_id, joined_at, added_by)
VALUES
    ('vgm-view-allowed',    'vg-e2e-workflow', 'user-view-allowed',    CURRENT_TIMESTAMP, 'system'),
    ('vgm-view-wrong-bu',   'vg-e2e-workflow', 'user-view-wrong-bu',   CURRENT_TIMESTAMP, 'system'),
    ('vgm-view-wrong-role', 'vg-e2e-workflow', 'user-view-wrong-role', CURRENT_TIMESTAMP, 'system'),
    ('vgm-view-nofu',       'vg-e2e-workflow', 'user-view-nofu',       CURRENT_TIMESTAMP, 'system')
ON CONFLICT (group_id, user_id) DO NOTHING;

-- ---------- MCY View 访问规则样例（可重复执行） ----------
-- View ID（18-MCY seed）: 50205 Transaction, 50206 Case, 50207 Attachment
DELETE FROM dw_main_table_view_access
WHERE view_config_id IN (50205, 50206, 50207);

UPDATE dw_main_table_view_configs
SET restrict_to_involved_users = false
WHERE id IN (50205, 50206, 50207);

-- Attachment：BU/Role 均未配置 → 仅 SYS_ADMIN 可见
-- （故意不插入 access 行）

-- Case：BU + Role AND
INSERT INTO dw_main_table_view_access (view_config_id, target_type, target_id)
VALUES
    (50206, 'BUSINESS_UNIT', 'bu-e2e-finance'),
    (50206, 'ROLE', 'role-manager');

-- Transaction：BU + Role（成对配置）+ 参与用户过滤
INSERT INTO dw_main_table_view_access (view_config_id, target_type, target_id)
VALUES
    (50205, 'BUSINESS_UNIT', 'bu-e2e-finance'),
    (50205, 'ROLE', 'role-manager');

-- Transaction 开启「仅参与用户可见数据」供 T6/T7（admin 仍应看全量）
UPDATE dw_main_table_view_configs
SET restrict_to_involved_users = true
WHERE id = 50205;
