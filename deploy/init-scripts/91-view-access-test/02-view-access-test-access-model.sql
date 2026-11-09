-- =====================================================
-- Main Table View access: Portal test accounts and rules under the platform access model (dev only, manual)
-- =====================================================
-- Rebuilds the test data of docs/view-access-control-test-guide.md, which 01-view-access-test-users.sql (deleted in
-- a46995e9b) used to hold, under the current rules (PRD §7.1, §7.2, D-04, D-05):
--   - Portal entry comes from a CUSTOM group only: every account below joins E2E_WORKFLOW_SIMULATION;
--   - business roles are BU + Role (BU_BOUNDED); platform roles are never held per business unit, so the
--     "wrong role" and "no function unit access" accounts get two BU_BOUNDED test roles of their own;
--   - no System Administrator bypass: the System Administrator case is the existing developer account
--     (System Administrators, E2E_WORKFLOW_SIMULATION, BU 共享财务中心 + Department Manager); no account here joins
--     a SYSTEM group, so dev keeps developer as its only System Administrator (AC-37).
-- Function unit: Multi-Instance Subtask Demo (fu-20260422-23tfag), whose views exist in every dev database
-- (17-Multi-Instance-Subtask-Demo). Its views are matched by name, their ids differ between databases:
--   Meeting      BU 共享财务中心 + Department Manager, only involved users' rows (restrict_to_involved_users)
--   People       BU 共享财务中心 + Department Manager
--   Participants BU only (unpaired): nobody sees it
--   Attachment   no rule: nobody sees it
-- Function unit access (Admin catalog): Department Manager and VIEW_TEST_OTHER are added to every enabled
-- catalog row of the unit; VIEW_TEST_NOFU is left out. The unit must be deployed and enabled first: run this
-- again after each deployment of a new version.
--
-- Accounts (password "password"), expected in frontend/scripts/verify-view-access-control.mjs:
--   developer        SYS_ADMIN, 共享财务中心 + Department Manager   -> Meeting, People
--   view_allowed     共享财务中心 + Department Manager             -> Meeting, People
--   view_wrong_bu    数字化部 + Department Manager                 -> no view of the unit
--   view_wrong_role  共享财务中心 + VIEW_TEST_OTHER (unit access)  -> no view of the unit
--   view_nofu        共享财务中心 + VIEW_TEST_NOFU (no unit access) -> no view of the unit
--
-- Not registered in 00-init-all.sh. One transaction, idempotent; written without ON CONFLICT (group_id, user_id), so it runs before
-- and after the access model switch. Apply by hand (docker cp first: PowerShell expands the $ in the BCrypt hash):
--   docker cp deploy\init-scripts\91-view-access-test\02-view-access-test-access-model.sql platform-postgres-dev:/tmp/view-access-test.sql
--   docker exec platform-postgres-dev psql -v ON_ERROR_STOP=1 -U platform_dev -d workflow_platform_dev -f /tmp/view-access-test.sql
-- =====================================================

BEGIN;

-- Two business roles for the negative cases.
INSERT INTO sys_roles (id, code, name, type, display_name, status, is_system, created_at, updated_at)
VALUES ('role-view-test-other', 'VIEW_TEST_OTHER', 'View Test Other Role', 'BU_BOUNDED',
        'View access test: may start the demo function unit, matches no view rule', 'ACTIVE', false,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
       ('role-view-test-nofu', 'VIEW_TEST_NOFU', 'View Test No Unit Role', 'BU_BOUNDED',
        'View access test: no access to the demo function unit', 'ACTIVE', false,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO NOTHING;

INSERT INTO sys_business_unit_roles (id, business_unit_id, role_id, created_at, created_by) VALUES
    ('bur-view-it-mgr', 'bu-e2e-it', 'role-manager', CURRENT_TIMESTAMP, 'system'),
    ('bur-view-fin-other', 'bu-e2e-finance', 'role-view-test-other', CURRENT_TIMESTAMP, 'system'),
    ('bur-view-fin-nofu', 'bu-e2e-finance', 'role-view-test-nofu', CURRENT_TIMESTAMP, 'system')
ON CONFLICT (business_unit_id, role_id) DO NOTHING;

INSERT INTO sys_users (id, username, password_hash, email, display_name, full_name, employee_id, position,
                       status, language, must_change_password, created_at, updated_at, deleted)
VALUES
    ('user-view-allowed', 'view_allowed', '$2a$10$P/xQaseE4Hr8/9fhSws86ez3nTUDLUGC8XeQueVX4QKZmdM/LeiYa',
     'view_allowed@test.local', 'View Test Allowed', 'View Test Allowed User', 'VIEW-T-OK', 'BU + Role match',
     'ACTIVE', 'zh_CN', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, false),
    ('user-view-wrong-bu', 'view_wrong_bu', '$2a$10$P/xQaseE4Hr8/9fhSws86ez3nTUDLUGC8XeQueVX4QKZmdM/LeiYa',
     'view_wrong_bu@test.local', 'View Test Wrong BU', 'View Test Wrong BU User', 'VIEW-T-BU', 'BU mismatch',
     'ACTIVE', 'zh_CN', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, false),
    ('user-view-wrong-role', 'view_wrong_role', '$2a$10$P/xQaseE4Hr8/9fhSws86ez3nTUDLUGC8XeQueVX4QKZmdM/LeiYa',
     'view_wrong_role@test.local', 'View Test Wrong Role', 'View Test Wrong Role User', 'VIEW-T-ROL', 'Role mismatch',
     'ACTIVE', 'zh_CN', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, false),
    ('user-view-nofu', 'view_nofu', '$2a$10$P/xQaseE4Hr8/9fhSws86ez3nTUDLUGC8XeQueVX4QKZmdM/LeiYa',
     'view_nofu@test.local', 'View Test No FU', 'View Test No FU Access', 'VIEW-T-NFU', 'No function unit access',
     'ACTIVE', 'zh_CN', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, false)
ON CONFLICT (username) DO UPDATE SET
    password_hash = EXCLUDED.password_hash,
    display_name  = EXCLUDED.display_name,
    status        = 'ACTIVE',
    deleted       = false,
    updated_at    = CURRENT_TIMESTAMP;

INSERT INTO sys_user_business_units (id, user_id, business_unit_id, created_at, created_by) VALUES
    ('ubu-view-allowed', 'user-view-allowed', 'bu-e2e-finance', CURRENT_TIMESTAMP, 'system'),
    ('ubu-view-wrong-bu', 'user-view-wrong-bu', 'bu-e2e-it', CURRENT_TIMESTAMP, 'system'),
    ('ubu-view-wrong-role', 'user-view-wrong-role', 'bu-e2e-finance', CURRENT_TIMESTAMP, 'system'),
    ('ubu-view-nofu', 'user-view-nofu', 'bu-e2e-finance', CURRENT_TIMESTAMP, 'system')
ON CONFLICT (user_id, business_unit_id) DO NOTHING;

INSERT INTO sys_user_business_unit_roles (id, user_id, business_unit_id, role_id, created_at, created_by) VALUES
    ('ubur-view-allowed', 'user-view-allowed', 'bu-e2e-finance', 'role-manager', CURRENT_TIMESTAMP, 'system'),
    ('ubur-view-wrong-bu', 'user-view-wrong-bu', 'bu-e2e-it', 'role-manager', CURRENT_TIMESTAMP, 'system'),
    ('ubur-view-wrong-role', 'user-view-wrong-role', 'bu-e2e-finance', 'role-view-test-other', CURRENT_TIMESTAMP, 'system'),
    ('ubur-view-nofu', 'user-view-nofu', 'bu-e2e-finance', 'role-view-test-nofu', CURRENT_TIMESTAMP, 'system')
ON CONFLICT (user_id, business_unit_id, role_id) DO NOTHING;

-- Portal entry: the E2E CUSTOM group.
INSERT INTO sys_virtual_group_members (id, group_id, user_id, joined_at, added_by)
SELECT 'vgm-' || u.id || '-e2e', vg.id, u.id, CURRENT_TIMESTAMP, 'system'
FROM sys_users u
JOIN sys_virtual_groups vg ON vg.code = 'E2E_WORKFLOW_SIMULATION'
WHERE u.id IN ('user-view-allowed', 'user-view-wrong-bu', 'user-view-wrong-role', 'user-view-nofu')
  AND NOT EXISTS (SELECT 1 FROM sys_virtual_group_members m WHERE m.group_id = vg.id AND m.user_id = u.id);

-- View rules of the demo unit's published views (reset on every run).
CREATE TEMP TABLE view_access_test_views ON COMMIT DROP AS
SELECT v.id, v.view_name
FROM dw_main_table_view_configs v
JOIN dw_function_units fu ON fu.id = v.function_unit_id
WHERE fu.code = 'fu-20260422-23tfag' AND v.status = 'PUBLISHED'
  AND v.view_name IN ('Meeting', 'People', 'Participants', 'Attachment');

DELETE FROM dw_main_table_view_access WHERE view_config_id IN (SELECT id FROM view_access_test_views);

UPDATE dw_main_table_view_configs v
SET restrict_to_involved_users = (t.view_name = 'Meeting'), updated_at = CURRENT_TIMESTAMP
FROM view_access_test_views t
WHERE t.id = v.id;

INSERT INTO dw_main_table_view_access (view_config_id, target_type, target_id)
SELECT t.id, r.target_type, r.target_id
FROM view_access_test_views t
JOIN (VALUES ('Meeting', 'BUSINESS_UNIT', 'bu-e2e-finance'), ('Meeting', 'ROLE', 'role-manager'),
             ('People', 'BUSINESS_UNIT', 'bu-e2e-finance'), ('People', 'ROLE', 'role-manager'),
             ('Participants', 'BUSINESS_UNIT', 'bu-e2e-finance')) AS r(view_name, target_type, target_id)
  ON r.view_name = t.view_name;

-- Function unit access on every enabled catalog row of the unit.
INSERT INTO sys_function_unit_access (id, function_unit_id, access_type, target_type, target_id, created_at, created_by)
SELECT 'fua-view-test-' || md5(fu.id || r.role_id), fu.id, 'USER', 'ROLE', r.role_id, CURRENT_TIMESTAMP, 'system'
FROM sys_function_units fu
CROSS JOIN (VALUES ('role-manager'), ('role-view-test-other')) AS r(role_id)
WHERE fu.code = 'fu-20260422-23tfag' AND fu.enabled = true
  AND NOT EXISTS (SELECT 1 FROM sys_function_unit_access a
                  WHERE a.function_unit_id = fu.id AND a.target_type = 'ROLE' AND a.target_id = r.role_id);

COMMIT;
