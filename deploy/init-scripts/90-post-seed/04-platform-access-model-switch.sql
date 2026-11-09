-- =====================================================
-- Post-seed: platform access model switch
-- (docs/design/platform-access-model-requirements.md §10 step 4.2)
-- =====================================================
-- Moves the role and group data to the access model:
--   - members of teams (DEVELOPER groups) join the Technical Lead / Developer list matching the global
--     role they hold today (D-24; a TECH_LEAD from the AD sync alone counts as Developer, D-28);
--   - every SYSTEM group is deleted except System Administrators and Auditors, which keep their id and
--     code, lose their members, AD mapping and bindings, and get back only their fixed binding (§5.2);
--   - the initial System Administrator becomes their only member (D-22);
--   - the task history of a deleted group stays, with the group's code and name in place of the reference;
--   - SYS_ADMIN / AUDITOR / TECH_LEAD / DEVELOPER / TEAM_LEAD lose every other grant: direct user
--     assignments and group bindings (§4.1); TEAM_LEAD is deleted (§4.2);
--   - MANAGER leaves the system list and the Department Managers group is deleted (§4.3);
--   - no other group keeps a role binding (§5.3);
--   - Public becomes a DEVELOPER team without lists; team codes are regenerated from team names (§5.4);
--   - every function unit belongs to exactly one team, enforced by a unique key (§6.3).
--
-- Must run after every seed package: the seeds still create the old groups and assign function units to
-- them. It contains DDL (the member and function-unit unique keys) because those keys can only change
-- once the data above is cleaned up.
--
-- All of it is one DO block, so it commits or rolls back as a whole in psql and in GUI clients alike.
-- It refuses (RAISE EXCEPTION, nothing changed) when the initial System Administrator account is missing
-- or not active, when a regenerated team code is empty or clashes, or when a function unit does not
-- belong to exactly one team.
--
-- Runs once: a database whose members table no longer has a unique key on exactly (group_id, user_id)
-- is left as it is. Running the body again would wipe the System Administrators members configured since.
-- The marker is the old key being gone, not the new one existing: a service started with Hibernate
-- ddl-auto=update can create the new key (the entity declares it) before this script has run.
--
-- The k8s copy of this script (deploy/k8s/init-data/init-platform-seed/all-in-one-for-gui.sql) differs
-- only in the SET line: the k8s initial System Administrator is admin.
-- Existing databases: apply 00-schema/100-platform-access-model.sql first, in dev then
-- 90-post-seed/03-platform-access-model-dev-prepare.sql, then this file manually:
--   docker exec -i platform-postgres-dev psql -v ON_ERROR_STOP=1 -U <user> -d <db> \
--     -f /docker-entrypoint-initdb.d/90-post-seed/04-platform-access-model-switch.sql
-- New services must not take traffic before 00-schema/100 has run: the role resolution reads its columns.
-- =====================================================

-- Initial System Administrator of this environment (D-22): developer in dev.
SET platform_access_model.initial_sys_admin = 'developer';

DO $$
DECLARE
    v_initial_username TEXT := current_setting('platform_access_model.initial_sys_admin');
    v_initial_user_id  VARCHAR(64);
    v_sys_admins_id    VARCHAR(64);
    v_auditors_id      VARCHAR(64);
    v_sys_admin_role   VARCHAR(64);
    v_auditor_role     VARCHAR(64);
    v_team_lead_role   VARCHAR(64);
    v_removed_groups   VARCHAR(64)[];
    v_constraint       TEXT;
    v_table            TEXT;
    v_problems         TEXT;
    v_old_member_keys  TEXT[];
BEGIN
    v_old_member_keys := ARRAY(
        SELECT c.conname FROM pg_constraint c
        WHERE c.conrelid = 'sys_virtual_group_members'::regclass AND c.contype = 'u'
          AND (SELECT array_agg(a.attname::text ORDER BY a.attname) FROM pg_attribute a
               WHERE a.attrelid = c.conrelid AND a.attnum = ANY (c.conkey)) = ARRAY['group_id', 'user_id']);
    IF cardinality(v_old_member_keys) = 0
            AND EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_vg_member_list_source') THEN
        RAISE NOTICE 'Platform access model switch already applied; nothing changed';
        RETURN;
    END IF;

    SELECT id INTO v_sys_admin_role FROM sys_roles WHERE code = 'SYS_ADMIN';
    SELECT id INTO v_auditor_role FROM sys_roles WHERE code = 'AUDITOR';
    IF v_sys_admin_role IS NULL OR v_auditor_role IS NULL
            OR (SELECT COUNT(*) FROM sys_roles WHERE code IN ('TECH_LEAD', 'DEVELOPER')) <> 2 THEN
        RAISE EXCEPTION 'Roles SYS_ADMIN, AUDITOR, TECH_LEAD and DEVELOPER must exist before the switch';
    END IF;

    SELECT id INTO v_initial_user_id FROM sys_users
    WHERE username = v_initial_username AND status = 'ACTIVE' AND COALESCE(deleted, false) = false;
    IF v_initial_user_id IS NULL THEN
        RAISE EXCEPTION 'Initial System Administrator "%" does not exist or is not active; nothing changed',
            v_initial_username;
    END IF;

    -- 0. One user may now hold several rows in a group: one per list and source.
    FOREACH v_constraint IN ARRAY v_old_member_keys LOOP
        EXECUTE format('ALTER TABLE sys_virtual_group_members DROP CONSTRAINT %I', v_constraint);
    END LOOP;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_vg_member_list_source') THEN
        ALTER TABLE sys_virtual_group_members
            ADD CONSTRAINT uk_vg_member_list_source UNIQUE (group_id, user_id, list_type, source);
    END IF;
    UPDATE sys_virtual_group_members SET source = 'AD' WHERE added_by = 'HERMES_AD_SYNC' AND source <> 'AD';

    -- 1. D-24: team members join the list of the global role they hold today, as the Developer
    --    Workstation computes it (RoleRepository.hasRoleByUserId before the switch): direct user roles,
    --    direct user assignments, and the role bindings of every group the user is in.
    --    D-28: a TECH_LEAD held only through an AD-synced membership counts as DEVELOPER. The old sync
    --    fed one AD Developer group into Technical Leads, Team Leads and Developers alike, so it says
    --    nothing about who leads a team; the Technical Lead lists are filled from their own AD groups.
    CREATE TEMP TABLE access_model_global_roles ON COMMIT DROP AS
    SELECT DISTINCT g.user_id,
           CASE WHEN r.code = 'TECH_LEAD' AND g.via_ad THEN 'DEVELOPER' ELSE r.code END AS code
    FROM (
        SELECT ur.user_id, ur.role_id, false AS via_ad FROM sys_user_roles ur
        UNION
        SELECT m.user_id, vgr.role_id, m.source = 'AD'
        FROM sys_virtual_group_members m
        JOIN sys_virtual_group_roles vgr ON vgr.virtual_group_id = m.group_id
        UNION
        SELECT ra.target_id, ra.role_id, false
        FROM sys_role_assignments ra
        WHERE ra.target_type = 'USER'
          AND (ra.valid_from IS NULL OR ra.valid_from <= CURRENT_TIMESTAMP)
          AND (ra.valid_to IS NULL OR ra.valid_to >= CURRENT_TIMESTAMP)
        UNION
        SELECT m.user_id, ra.role_id, m.source = 'AD'
        FROM sys_role_assignments ra
        JOIN sys_virtual_group_members m ON ra.target_type = 'VIRTUAL_GROUP' AND ra.target_id = m.group_id
        WHERE (ra.valid_from IS NULL OR ra.valid_from <= CURRENT_TIMESTAMP)
          AND (ra.valid_to IS NULL OR ra.valid_to >= CURRENT_TIMESTAMP)
    ) g
    JOIN sys_roles r ON r.id = g.role_id AND r.status = 'ACTIVE' AND r.code IN ('TECH_LEAD', 'DEVELOPER');

    --    D-30: a Technical Lead row is MANUAL whatever the membership's source. After D-28 the role itself
    --    was granted by hand, so the AD sync must not remove it; Developer rows keep the membership's source.
    INSERT INTO sys_virtual_group_members (id, group_id, user_id, joined_at, added_by, list_type, source)
    SELECT gen_random_uuid()::text, m.group_id, m.user_id, m.joined_at,
           CASE WHEN gr.code = 'TECH_LEAD' AND m.source = 'AD' THEN 'system' ELSE m.added_by END,
           gr.code,
           CASE WHEN gr.code = 'TECH_LEAD' THEN 'MANUAL' ELSE m.source END
    FROM sys_virtual_group_members m
    JOIN sys_virtual_groups vg ON vg.id = m.group_id AND vg.type = 'DEVELOPER'
    JOIN access_model_global_roles gr ON gr.user_id = m.user_id
    WHERE m.list_type = 'MEMBER';

    SELECT string_agg(vg.code || ': ' || u.username, ', ' ORDER BY vg.code, u.username) INTO v_problems
    FROM sys_virtual_group_members m
    JOIN sys_virtual_groups vg ON vg.id = m.group_id AND vg.type = 'DEVELOPER'
    JOIN sys_users u ON u.id = m.user_id
    WHERE m.list_type = 'MEMBER'
      AND NOT EXISTS (SELECT 1 FROM access_model_global_roles gr WHERE gr.user_id = m.user_id);
    IF v_problems IS NOT NULL THEN
        RAISE NOTICE 'Read-only team members not moved to any list (D-24): %', v_problems;
    END IF;
    DELETE FROM sys_virtual_group_members m
    USING sys_virtual_groups vg
    WHERE vg.id = m.group_id AND vg.type = 'DEVELOPER' AND m.list_type = 'MEMBER';

    -- 2. Groups that go away: every SYSTEM group but the two rebuilt below, Department Managers, and the
    --    transitional Default Development Team when nothing uses it any more.
    v_removed_groups := ARRAY(
        SELECT vg.id FROM sys_virtual_groups vg
        WHERE (vg.type = 'SYSTEM' AND vg.code NOT IN ('SYSTEM_ADMINISTRATORS', 'AUDITORS'))
           OR (vg.type = 'CUSTOM' AND vg.code = 'MANAGERS')
           OR (vg.code = 'DEFAULT_DEV_TEAM'
               AND NOT EXISTS (SELECT 1 FROM sys_virtual_group_members m WHERE m.group_id = vg.id)
               AND NOT EXISTS (SELECT 1 FROM dw_function_unit_dev_groups d WHERE d.virtual_group_id = vg.id)));
    RAISE NOTICE 'Deleting groups: %',
        (SELECT string_agg(code, ', ' ORDER BY code) FROM sys_virtual_groups WHERE id = ANY (v_removed_groups));
    -- Task history outlives the group: the row keeps the group's code and name instead of the reference.
    UPDATE sys_virtual_group_task_history h
    SET deleted_group_code = vg.code, deleted_group_name = vg.name, group_id = NULL
    FROM sys_virtual_groups vg
    WHERE vg.id = h.group_id AND vg.id = ANY (v_removed_groups);
    -- Configuration that points at a group without a cascading foreign key.
    DELETE FROM sys_role_assignments WHERE target_type = 'VIRTUAL_GROUP' AND target_id = ANY (v_removed_groups);
    DELETE FROM sys_approvers WHERE target_type = 'VIRTUAL_GROUP' AND target_id = ANY (v_removed_groups);
    DELETE FROM dw_function_unit_dev_groups WHERE virtual_group_id = ANY (v_removed_groups);
    DELETE FROM sys_virtual_groups WHERE id = ANY (v_removed_groups);

    -- 3. Rebuild System Administrators and Auditors: same id and code, no members, no AD mapping.
    SELECT id INTO v_sys_admins_id FROM sys_virtual_groups WHERE code = 'SYSTEM_ADMINISTRATORS';
    IF v_sys_admins_id IS NULL THEN
        v_sys_admins_id := 'vg-sys-admins';
        INSERT INTO sys_virtual_groups (id, code, name, type, display_name, status, created_at, updated_at)
        VALUES (v_sys_admins_id, 'SYSTEM_ADMINISTRATORS', 'System Administrators', 'SYSTEM',
                'Virtual group for system administrators with full system access', 'ACTIVE',
                CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
    END IF;
    SELECT id INTO v_auditors_id FROM sys_virtual_groups WHERE code = 'AUDITORS';
    IF v_auditors_id IS NULL THEN
        v_auditors_id := 'vg-auditors';
        INSERT INTO sys_virtual_groups (id, code, name, type, display_name, status, created_at, updated_at)
        VALUES (v_auditors_id, 'AUDITORS', 'Auditors', 'SYSTEM',
                'Virtual group for system auditors with monitoring and audit access', 'ACTIVE',
                CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
    END IF;
    UPDATE sys_virtual_groups
    SET name = CASE id WHEN v_sys_admins_id THEN 'System Administrators' ELSE 'Auditors' END,
        type = 'SYSTEM', status = 'ACTIVE', ad_group = NULL, tech_lead_ad_group = NULL,
        updated_at = CURRENT_TIMESTAMP
    WHERE id IN (v_sys_admins_id, v_auditors_id);
    DELETE FROM sys_virtual_group_members WHERE group_id IN (v_sys_admins_id, v_auditors_id);

    -- 4 + 8. Role bindings: only the two fixed ones remain. CUSTOM groups bind no role (D-03) and a
    --    team's roles come from its lists, so every other group binding goes.
    DELETE FROM sys_virtual_group_roles;
    DELETE FROM sys_role_assignments WHERE target_type = 'VIRTUAL_GROUP';
    INSERT INTO sys_virtual_group_roles (id, virtual_group_id, role_id, created_at, created_by) VALUES
        ('vgr-sys-admin-001', v_sys_admins_id, v_sys_admin_role, CURRENT_TIMESTAMP, 'system'),
        ('vgr-auditor-001', v_auditors_id, v_auditor_role, CURRENT_TIMESTAMP, 'system');
    INSERT INTO sys_role_assignments (id, role_id, target_type, target_id, assigned_by, assigned_at,
                                      created_at, updated_at) VALUES
        ('ra-vgr-sys-admin-001', v_sys_admin_role, 'VIRTUAL_GROUP', v_sys_admins_id, 'system',
         CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
        ('ra-vgr-auditor-001', v_auditor_role, 'VIRTUAL_GROUP', v_auditors_id, 'system',
         CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

    -- 5. The initial System Administrator, so there is never a moment without one (§5.2 step 4).
    INSERT INTO sys_virtual_group_members (id, group_id, user_id, joined_at, added_by, list_type, source)
    VALUES (gen_random_uuid()::text, v_sys_admins_id, v_initial_user_id, CURRENT_TIMESTAMP, 'system',
            'MEMBER', 'MANUAL');

    -- 6. Platform roles are never granted to a user directly (§4.1).
    DELETE FROM sys_role_assignments ra
    USING sys_roles r
    WHERE r.id = ra.role_id AND ra.target_type <> 'VIRTUAL_GROUP'
      AND r.code IN ('SYS_ADMIN', 'AUDITOR', 'TECH_LEAD', 'DEVELOPER', 'TEAM_LEAD');
    DELETE FROM sys_user_roles ur
    USING sys_roles r
    WHERE r.id = ur.role_id AND r.code IN ('SYS_ADMIN', 'AUDITOR', 'TECH_LEAD', 'DEVELOPER', 'TEAM_LEAD');

    -- 7. TEAM_LEAD goes with every reference to it (§4.2). Foreign keys cascade from sys_roles except
    --    bi_rbac_mapping; access rules name a role by id or by code without a foreign key.
    SELECT id INTO v_team_lead_role FROM sys_roles WHERE code = 'TEAM_LEAD';
    IF v_team_lead_role IS NOT NULL THEN
        DELETE FROM bi_rbac_mapping WHERE sys_role_id = v_team_lead_role;
        FOREACH v_table IN ARRAY ARRAY['sys_function_unit_access', 'sys_function_unit_audit_access',
                'dw_function_unit_access', 'dw_main_table_view_access', 'rt_table_access',
                'admin_data_permission_rules', 'bi_dashboard_assignment'] LOOP
            IF to_regclass(v_table) IS NOT NULL THEN
                EXECUTE format('DELETE FROM %I WHERE target_type = ''ROLE'' AND target_id IN ($1, $2)', v_table)
                    USING v_team_lead_role, 'TEAM_LEAD';
            END IF;
        END LOOP;
        DELETE FROM sys_roles WHERE id = v_team_lead_role;
    END IF;

    -- 9. Department Manager stays a business role, on the Custom list (§4.3).
    UPDATE sys_roles SET is_system = false, updated_at = CURRENT_TIMESTAMP WHERE code = 'MANAGER';

    -- 10. Public is a reserved team without lists (§6.4).
    UPDATE sys_virtual_groups SET type = 'DEVELOPER', updated_at = CURRENT_TIMESTAMP WHERE id = 'vg-dev-public';
    DELETE FROM sys_virtual_group_members WHERE group_id = 'vg-dev-public';

    -- 11. Team AD mappings are configured again per environment (§10 step 3, D-29): the old single mapping
    --     fed plain members, and syncing it into the Developer list would make every read-only member a
    --     Developer, which D-24 rules out. The AD-sourced list rows moved in step 1 stay until the first
    --     sync, which removes the AD rows of a list without a mapping and fills it from the new one.
    SELECT string_agg(code || ' = ' || ad_group, ', ' ORDER BY code) INTO v_problems
    FROM sys_virtual_groups WHERE type = 'DEVELOPER' AND ad_group IS NOT NULL;
    IF v_problems IS NOT NULL THEN
        RAISE NOTICE 'Cleared team AD mappings, set them again as Developer / Technical Lead AD groups: %',
            v_problems;
    END IF;
    UPDATE sys_virtual_groups SET ad_group = NULL, updated_at = CURRENT_TIMESTAMP
    WHERE type = 'DEVELOPER' AND ad_group IS NOT NULL;

    -- 12. Team codes come from team names, as VirtualGroupManagerComponent.generateCode does; Public keeps
    --     DEV_TEAM_PUBLIC (§5.4).
    CREATE TEMP TABLE access_model_team_codes ON COMMIT DROP AS
    SELECT id, code AS old_code,
           regexp_replace(regexp_replace(regexp_replace(upper(name), '[^A-Z0-9]', '_', 'g'), '_+', '_', 'g'),
                          '^_|_$', '', 'g') AS new_code
    FROM sys_virtual_groups
    WHERE type = 'DEVELOPER' AND id <> 'vg-dev-public';

    SELECT string_agg(t.old_code || ' -> "' || t.new_code || '"', ', ' ORDER BY t.old_code) INTO v_problems
    FROM access_model_team_codes t
    WHERE t.new_code = '' OR length(t.new_code) > 50
       OR EXISTS (SELECT 1 FROM access_model_team_codes o WHERE o.new_code = t.new_code AND o.id <> t.id)
       OR EXISTS (SELECT 1 FROM sys_virtual_groups vg
                  WHERE vg.code = t.new_code
                    AND vg.id NOT IN (SELECT id FROM access_model_team_codes));
    IF v_problems IS NOT NULL THEN
        RAISE EXCEPTION 'Team names that do not give a usable unique code, rename them first: %', v_problems;
    END IF;
    -- Two steps, so teams can trade codes without tripping the unique key in between.
    UPDATE sys_virtual_groups vg SET code = '~' || substr(md5(vg.id), 1, 20)
    FROM access_model_team_codes t WHERE t.id = vg.id AND t.new_code <> t.old_code;
    UPDATE sys_virtual_groups vg SET code = t.new_code, updated_at = CURRENT_TIMESTAMP
    FROM access_model_team_codes t WHERE t.id = vg.id AND t.new_code <> t.old_code;

    -- 13. Every function unit belongs to exactly one team (§6.3).
    SELECT string_agg(fu.id || ' ' || fu.name || ' [' || COALESCE(links.groups, 'no team') || ']', '; '
                      ORDER BY fu.id) INTO v_problems
    FROM dw_function_units fu
    LEFT JOIN (
        SELECT d.function_unit_id,
               COUNT(*) AS link_count,
               COUNT(*) FILTER (WHERE vg.type = 'DEVELOPER') AS team_count,
               string_agg(d.virtual_group_id || '=' || COALESCE(vg.type, 'missing'), ', ') AS groups
        FROM dw_function_unit_dev_groups d
        LEFT JOIN sys_virtual_groups vg ON vg.id = d.virtual_group_id
        GROUP BY d.function_unit_id
    ) links ON links.function_unit_id = fu.id
    WHERE links.function_unit_id IS NULL OR links.link_count <> 1 OR links.team_count <> 1;
    IF v_problems IS NOT NULL THEN
        RAISE EXCEPTION 'Function units that do not belong to exactly one team, assign each one first: %',
            v_problems;
    END IF;
    ALTER TABLE dw_function_unit_dev_groups ADD CONSTRAINT uq_dw_fu_dev_group_fu UNIQUE (function_unit_id);

    RAISE NOTICE 'Platform access model switch done; initial System Administrator: %', v_initial_username;
END $$;

RESET platform_access_model.initial_sys_admin;
