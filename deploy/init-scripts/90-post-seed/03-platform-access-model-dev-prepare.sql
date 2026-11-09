-- =====================================================
-- Post-seed: dev seed data the platform access model switch expects (dev only)
-- =====================================================
-- Runs right before 04-platform-access-model-switch.sql. The k8s seed has neither the developer
-- account nor Platform Showcase, so this file has no k8s counterpart.
--
-- 1) developer (user-dev, password "password") is the dev environment's initial System Administrator
--    (PRD D-22). The 05 seed creates it; create it here only if it is missing, and keep it otherwise.
--    It joins the CUSTOM group E2E_WORKFLOW_SIMULATION so it can still enter the User Portal (PRD §7.1).
-- 2) Platform Showcase was assigned to the SYSTEM group Technical Leads for a workspace demo; that group
--    is deleted by the switch, which refuses a function unit left without a team. Move it to Public.
--
-- Idempotent; written without ON CONFLICT (group_id, user_id) so it also runs after the switch,
-- which replaces that unique key.
-- =====================================================

\echo '========================================='
\echo 'Post-seed: dev data for the platform access model switch...'
\echo '========================================='

INSERT INTO sys_users (id, username, password_hash, email, display_name, full_name, status, language,
                       must_change_password, created_at, updated_at, deleted)
VALUES ('user-dev', 'developer', '$2a$10$P/xQaseE4Hr8/9fhSws86ez3nTUDLUGC8XeQueVX4QKZmdM/LeiYa',
        'developer@e2e.workflow.local', 'Developer Tester', 'Developer Tester', 'ACTIVE', 'zh_CN',
        false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, false)
ON CONFLICT (username) DO NOTHING;

INSERT INTO sys_virtual_group_members (id, group_id, user_id, joined_at, added_by)
SELECT 'vgm-dev-e2e-workflow', vg.id, u.id, CURRENT_TIMESTAMP, 'system'
FROM sys_virtual_groups vg
JOIN sys_users u ON u.username = 'developer'
WHERE vg.code = 'E2E_WORKFLOW_SIMULATION'
  AND NOT EXISTS (SELECT 1 FROM sys_virtual_group_members m WHERE m.group_id = vg.id AND m.user_id = u.id);

UPDATE dw_function_unit_dev_groups g
SET virtual_group_id = 'vg-dev-public'
FROM dw_function_units fu
WHERE fu.id = g.function_unit_id
  AND fu.code = 'fu-20260403-a1b2c4'
  AND g.virtual_group_id = 'vg-tech-leads'
  AND NOT EXISTS (SELECT 1 FROM dw_function_unit_dev_groups p
                  WHERE p.function_unit_id = fu.id AND p.virtual_group_id = 'vg-dev-public');

\echo '✓ Dev data for the platform access model switch done'
