-- =============================================================================
-- Full wipe: function units (developer + deployed) + workflow / history data
-- =============================================================================
-- Target: PostgreSQL 16+ (TRUNCATE on app tables; Flowable act_* / flw_* are DROPped — see §6).
--
-- Clears:
--   • User Portal: process instances, drafts, favorites, delegation audit,
--     in-app notifications, form change history
--   • Workflow engine extensions: wf_* (tasks, variables, audit, exceptions)
--   • Platform security history: login audit, member change logs, virtual-group
--     task history, permission requests (portal-facing)
--   • Admin-center audit / logs / password history (not users or roles)
--   • Relation-table JSON rows + row audit (rt_table_data_rows, rt_audit_logs)
--   • Flowable runtime + history: DROP all public.act_* / public.flw_* (not TRUNCATE —
--     truncating clears act_ge_property and leaves tables empty, which breaks Flowable
--     startup with NPE in ProcessDbSchemaManager.schemaUpdate)
--   • Developer catalog (dw_*) and deployed catalog (sys_function_units tree)
--
-- Does NOT remove: sys_users, sys_roles, business units, virtual group definitions,
-- dictionaries, admin_system_configs, BI registry, user preferences/layout.
--
-- Usage (example):
--   psql -h HOST -U USER -d DB -v ON_ERROR_STOP=1 -f 00-wipe-all-function-units.sql
-- =============================================================================

BEGIN;

-- ---------------------------------------------------------------------------
-- 1) User Portal — runtime & history tied to processes / tasks
-- ---------------------------------------------------------------------------
TRUNCATE TABLE
    up_change_history,
    up_process_history,
    up_process_instance,
    up_process_draft,
    up_favorite_process,
    up_delegation_audit,
    up_notification,
    up_permission_request
RESTART IDENTITY CASCADE;

-- ---------------------------------------------------------------------------
-- 2) Workflow engine core extension tables
-- ---------------------------------------------------------------------------
TRUNCATE TABLE
    wf_exception_records,
    wf_extended_task_info,
    wf_process_variables,
    wf_audit_logs
RESTART IDENTITY CASCADE;

-- ---------------------------------------------------------------------------
-- 3) Platform security — historical / request rows (keep users & groups)
-- ---------------------------------------------------------------------------
TRUNCATE TABLE
    sys_virtual_group_task_history,
    sys_login_audit,
    sys_member_change_logs,
    sys_permission_requests
RESTART IDENTITY CASCADE;

-- ---------------------------------------------------------------------------
-- 4) Admin center — audit & log tables only
-- ---------------------------------------------------------------------------
TRUNCATE TABLE
    admin_audit_logs,
    admin_system_logs,
    admin_permission_change_history,
    admin_config_history,
    admin_password_history,
    admin_alerts
RESTART IDENTITY CASCADE;

-- ---------------------------------------------------------------------------
-- 5) Relation-table JSON row data + change audit
-- ---------------------------------------------------------------------------
TRUNCATE TABLE
    rt_table_data_rows,
    rt_audit_logs
RESTART IDENTITY CASCADE;

-- ---------------------------------------------------------------------------
-- 6) Flowable BPM (runtime + history) — DROP tables (see header: TRUNCATE breaks engine)
-- ---------------------------------------------------------------------------
DO $flw$
DECLARE
    r RECORD;
    dropped_count int := 0;
BEGIN
    FOR r IN
        SELECT tablename
        FROM pg_tables
        WHERE schemaname = 'public'
          AND (
              tablename LIKE 'act\_%' ESCAPE '\'
              OR tablename LIKE 'flw\_%' ESCAPE '\'
          )
        ORDER BY tablename
    LOOP
        EXECUTE format('DROP TABLE IF EXISTS public.%I CASCADE', r.tablename);
        RAISE NOTICE 'Dropped Flowable table: %', r.tablename;
        dropped_count := dropped_count + 1;
    END LOOP;

    IF dropped_count = 0 THEN
        RAISE NOTICE 'No act_* / flw_* tables found (Flowable schema not created yet).';
    END IF;
END
$flw$;

-- ---------------------------------------------------------------------------
-- 7) Deployed function unit catalog (application / admin-center)
-- ---------------------------------------------------------------------------
DELETE FROM sys_function_unit_approvals
WHERE deployment_id IN (SELECT id FROM sys_function_unit_deployments);

DELETE FROM sys_function_unit_deployments;

DELETE FROM sys_function_unit_dependencies;
DELETE FROM sys_function_unit_contents;
DELETE FROM sys_function_unit_access;

UPDATE sys_function_units SET previous_version_id = NULL WHERE previous_version_id IS NOT NULL;

DELETE FROM sys_function_units;

-- ---------------------------------------------------------------------------
-- 8) Developer workstation catalog (cascades to dw_* design artifacts)
-- ---------------------------------------------------------------------------
-- Sub-table view configs/fields have NO FK constraints (orphan-safe cleanup)
DELETE FROM dw_sub_table_view_fields;
DELETE FROM dw_sub_table_view_configs;

-- Bindings reference dw_table_definitions without ON DELETE CASCADE; delete them
-- before dw_function_units so CASCADE can drop tables vs. forms in any order.
DELETE FROM dw_form_table_bindings;

DELETE FROM dw_function_units;

COMMIT;
