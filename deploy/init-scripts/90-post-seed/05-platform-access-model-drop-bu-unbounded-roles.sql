-- =====================================================
-- Post-seed: platform access model, no BU_UNBOUNDED roles (PRD §5.3, D-64; §10 step 4.2)
-- =====================================================
-- Roles without a business unit are the system ones only (types ADMIN / AUDITOR / DEVELOPER). Every
-- BU_UNBOUNDED role is deleted with what grants or names it, and the type leaves the CHECK constraint, so no
-- environment keeps one and the code no longer knows the type.
--
-- Deleted with each role:
--   - by foreign key ON DELETE CASCADE: sys_role_assignments, sys_user_roles, sys_virtual_group_roles,
--     sys_role_permissions, sys_developer_role_permissions, sys_business_unit_roles,
--     sys_user_business_unit_roles;
--   - explicitly: bi_rbac_mapping (foreign key without cascade) and the access rules that name a role by id or
--     code without a foreign key, the same tables 04-platform-access-model-switch.sql cleans for TEAM_LEAD.
-- Kept: permission requests that name the role (history; pending ones are listed by the inventory), and role
-- codes inside designed or published content (BPMN roleIds, action allowedRoles, published views), which the
-- owning team changes and republishes (deploy/maintenance/platform-access-model/01 lists them as I-08 / I-09).
--
-- One DO block: commits or rolls back as a whole in psql and GUI clients. Idempotent: with no such role left
-- and the constraint already narrowed it changes nothing. Independent of 04, normally run right after it.
-- Contains DDL (the CHECK constraint) because the constraint can only narrow once the rows are gone.
--
-- The k8s copy is appended to deploy/k8s/init-data/init-platform-seed/all-in-one-for-gui.sql after 04.
-- Existing databases: run it by hand after 04:
--   docker exec -i platform-postgres-dev psql -v ON_ERROR_STOP=1 -U <user> -d <db> \
--     -f /docker-entrypoint-initdb.d/90-post-seed/05-platform-access-model-drop-bu-unbounded-roles.sql
-- =====================================================

DO $$
DECLARE
    v_roles VARCHAR(64)[];
    v_table TEXT;
    v_count BIGINT;
BEGIN
    v_roles := ARRAY(SELECT id FROM sys_roles WHERE type = 'BU_UNBOUNDED');
    IF cardinality(v_roles) > 0 THEN
        RAISE NOTICE 'Deleting BU_UNBOUNDED roles (D-64): %',
            (SELECT string_agg(code, ', ' ORDER BY code) FROM sys_roles WHERE id = ANY (v_roles));

        FOREACH v_table IN ARRAY ARRAY['sys_function_unit_access', 'sys_function_unit_audit_access',
                'dw_function_unit_access', 'dw_main_table_view_access', 'rt_table_access',
                'admin_data_permission_rules', 'bi_dashboard_assignment'] LOOP
            IF to_regclass(v_table) IS NOT NULL THEN
                EXECUTE format('DELETE FROM %I t USING sys_roles r WHERE r.id = ANY ($1) '
                               || 'AND t.target_type = ''ROLE'' AND t.target_id IN (r.id, r.code)', v_table)
                    USING v_roles;
                GET DIAGNOSTICS v_count = ROW_COUNT;
                IF v_count > 0 THEN
                    RAISE NOTICE '  % access rules removed from %', v_count, v_table;
                END IF;
            END IF;
        END LOOP;
        DELETE FROM bi_rbac_mapping WHERE sys_role_id = ANY (v_roles);
        DELETE FROM sys_roles WHERE id = ANY (v_roles);
    END IF;

    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'sys_roles'::regclass AND conname = 'chk_role_type'
                 AND pg_get_constraintdef(oid) LIKE '%BU_UNBOUNDED%') THEN
        ALTER TABLE sys_roles DROP CONSTRAINT chk_role_type;
        ALTER TABLE sys_roles
            ADD CONSTRAINT chk_role_type CHECK (type IN ('ADMIN', 'AUDITOR', 'DEVELOPER', 'BU_BOUNDED'));
    END IF;
END $$;

COMMENT ON COLUMN sys_roles.type IS
    'ADMIN (system administration), AUDITOR (read-only audit), DEVELOPER (design studio), BU_BOUNDED (business roles, held per business unit)';
