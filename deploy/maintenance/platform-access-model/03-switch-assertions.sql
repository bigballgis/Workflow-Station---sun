-- =====================================================
-- Platform access model: assertions on the switch (read-only)
-- docs/design/platform-access-model-requirements.md §10 step 4.2 / 4.3
-- =====================================================
-- One row per check: PASS, FAIL or WARN with what was found. Run right after 90-post-seed/04 and 05 (the "switch"
-- checks must all PASS before any new service starts) and again after this environment's switch configuration
-- ("configuration" checks). FAIL on a switch check: restore the step 4.1 backup and stop; on a configuration
-- check: fix the configuration script and run it again. 02-precheck-after-switch.sql lists the details.
--
-- Read-only: one READ ONLY transaction, rolled back at the end. Plain SQL, runs in psql and in GUI clients.
--   psql -X -v ON_ERROR_STOP=1 -U <user> -d <db> -f 03-switch-assertions.sql
--   docker exec -i platform-postgres-dev psql -X -U <user> -d <db> < 03-switch-assertions.sql
-- =====================================================

START TRANSACTION READ ONLY;

WITH checks(id, stage, problem_count, detail) AS (
    -- A-01 The member key moved to (group, user, list, source) (04 step 0).
    SELECT 'A-01 member unique key', 'switch',
           (SELECT count(*) FROM pg_constraint c
            WHERE c.conrelid = 'sys_virtual_group_members'::regclass AND c.contype = 'u'
              AND (SELECT array_agg(a.attname::text ORDER BY a.attname) FROM pg_attribute a
                   WHERE a.attrelid = c.conrelid AND a.attnum = ANY (c.conkey)) = ARRAY['group_id', 'user_id'])
             + CASE WHEN EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_vg_member_list_source') THEN 0 ELSE 1 END,
           'old (group_id, user_id) key gone, uk_vg_member_list_source present'
    UNION ALL
    -- A-02 One team per function unit, enforced (§6.3, 04 step 13).
    SELECT 'A-02 function unit team key', 'switch',
           CASE WHEN EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_dw_fu_dev_group_fu') THEN 0 ELSE 1 END,
           'uq_dw_fu_dev_group_fu present'
    UNION ALL
    -- A-03 SYSTEM groups are System Administrators and Auditors only, both ACTIVE (§5.2).
    SELECT 'A-03 SYSTEM groups', 'switch',
           (SELECT count(*) FROM sys_virtual_groups WHERE type = 'SYSTEM'
              AND (code NOT IN ('SYSTEM_ADMINISTRATORS', 'AUDITORS') OR status <> 'ACTIVE'))
             + 2 - (SELECT count(*) FROM sys_virtual_groups WHERE type = 'SYSTEM'
                      AND code IN ('SYSTEM_ADMINISTRATORS', 'AUDITORS')),
           COALESCE((SELECT string_agg(code || ' ' || status, ', ' ORDER BY code) FROM sys_virtual_groups
                     WHERE type = 'SYSTEM'), 'none')
    UNION ALL
    -- A-04 The only group bindings are System Administrators -> SYS_ADMIN and Auditors -> AUDITOR, in both tables
    --      (§4.1, §5.3). A binding whose group or role no longer exists counts as a wrong one (target_id has no
    --      foreign key).
    SELECT 'A-04 group role bindings', 'switch',
           (SELECT count(*) FROM (
                SELECT vg.code, r.code AS role FROM sys_virtual_group_roles b
                LEFT JOIN sys_virtual_groups vg ON vg.id = b.virtual_group_id LEFT JOIN sys_roles r ON r.id = b.role_id
                UNION ALL
                SELECT vg.code, r.code FROM sys_role_assignments b
                LEFT JOIN sys_virtual_groups vg ON vg.id = b.target_id LEFT JOIN sys_roles r ON r.id = b.role_id
                WHERE b.target_type = 'VIRTUAL_GROUP') x
            WHERE x.code IS NULL OR x.role IS NULL
               OR NOT ((x.code = 'SYSTEM_ADMINISTRATORS' AND x.role = 'SYS_ADMIN') OR (x.code = 'AUDITORS' AND x.role = 'AUDITOR')))
             + 2 - (SELECT count(*) FROM sys_virtual_group_roles b JOIN sys_virtual_groups vg ON vg.id = b.virtual_group_id
                    JOIN sys_roles r ON r.id = b.role_id
                    WHERE (vg.code, r.code) IN (('SYSTEM_ADMINISTRATORS', 'SYS_ADMIN'), ('AUDITORS', 'AUDITOR')))
             + 2 - (SELECT count(*) FROM sys_role_assignments b JOIN sys_virtual_groups vg ON vg.id = b.target_id
                    JOIN sys_roles r ON r.id = b.role_id
                    WHERE b.target_type = 'VIRTUAL_GROUP'
                      AND (vg.code, r.code) IN (('SYSTEM_ADMINISTRATORS', 'SYS_ADMIN'), ('AUDITORS', 'AUDITOR'))),
           'exactly the two fixed bindings in sys_virtual_group_roles and sys_role_assignments'
    UNION ALL
    -- A-05 No platform role or TEAM_LEAD is granted to anyone directly (§4.1).
    SELECT 'A-05 no direct platform roles', 'switch',
           (SELECT count(*) FROM sys_role_assignments ra JOIN sys_roles r ON r.id = ra.role_id
            WHERE ra.target_type <> 'VIRTUAL_GROUP' AND r.code IN ('SYS_ADMIN', 'AUDITOR', 'TECH_LEAD', 'DEVELOPER', 'TEAM_LEAD'))
             + (SELECT count(*) FROM sys_user_roles ur JOIN sys_roles r ON r.id = ur.role_id
                WHERE r.code IN ('SYS_ADMIN', 'AUDITOR', 'TECH_LEAD', 'DEVELOPER', 'TEAM_LEAD')),
           'rows in sys_role_assignments (non-group) and sys_user_roles'
    UNION ALL
    -- A-06 TEAM_LEAD is gone, with the access rules and BI rows naming it by code (§4.2).
    SELECT 'A-06 TEAM_LEAD removed', 'switch',
           (SELECT count(*) FROM sys_roles WHERE code = 'TEAM_LEAD')
             + (SELECT count(*) FROM sys_function_unit_access WHERE target_type = 'ROLE' AND target_id = 'TEAM_LEAD')
             + (SELECT count(*) FROM sys_function_unit_audit_access WHERE target_type = 'ROLE' AND target_id = 'TEAM_LEAD')
             + (SELECT count(*) FROM dw_function_unit_access WHERE target_type = 'ROLE' AND target_id = 'TEAM_LEAD')
             + (SELECT count(*) FROM dw_main_table_view_access WHERE target_type = 'ROLE' AND target_id = 'TEAM_LEAD')
             + (SELECT count(*) FROM rt_table_access WHERE target_type = 'ROLE' AND target_id = 'TEAM_LEAD')
             + (SELECT count(*) FROM admin_data_permission_rules WHERE target_type = 'ROLE' AND target_id = 'TEAM_LEAD')
             + (SELECT count(*) FROM bi_dashboard_assignment WHERE target_type = 'ROLE' AND target_id = 'TEAM_LEAD'),
           'TEAM_LEAD role and rules naming it'
    UNION ALL
    -- A-07 Department Manager is a Custom business role; the Department Managers group is gone (§4.3).
    SELECT 'A-07 Department Manager', 'switch',
           (SELECT count(*) FROM sys_roles WHERE code = 'MANAGER' AND (is_system OR type <> 'BU_BOUNDED'))
             + (SELECT count(*) FROM sys_virtual_groups WHERE code = 'MANAGERS'),
           'MANAGER is_system = false and BU_BOUNDED; no MANAGERS group'
    UNION ALL
    -- A-08 Public is an ACTIVE team without lists or AD groups (§6.4).
    SELECT 'A-08 Public team', 'switch',
           CASE WHEN EXISTS (SELECT 1 FROM sys_virtual_groups WHERE id = 'vg-dev-public' AND type = 'DEVELOPER'
                               AND status = 'ACTIVE' AND code = 'DEV_TEAM_PUBLIC'
                               AND NULLIF(TRIM(ad_group), '') IS NULL AND NULLIF(TRIM(tech_lead_ad_group), '') IS NULL)
                THEN 0 ELSE 1 END
             + (SELECT count(*) FROM sys_virtual_group_members WHERE group_id = 'vg-dev-public'),
           'vg-dev-public: DEVELOPER, ACTIVE, DEV_TEAM_PUBLIC, no AD group, no members'
    UNION ALL
    -- A-09 Team codes are generated from team names (§5.4).
    SELECT 'A-09 team codes from names', 'switch',
           (SELECT count(*) FROM sys_virtual_groups
            WHERE type = 'DEVELOPER' AND id <> 'vg-dev-public'
              AND code <> regexp_replace(regexp_replace(regexp_replace(upper(name), '[^A-Z0-9]', '_', 'g'), '_+', '_', 'g'),
                                         '^_|_$', '', 'g')),
           COALESCE((SELECT string_agg(name || ' -> ' || code, ', ') FROM sys_virtual_groups
                     WHERE type = 'DEVELOPER' AND id <> 'vg-dev-public'
                       AND code <> regexp_replace(regexp_replace(regexp_replace(upper(name), '[^A-Z0-9]', '_', 'g'), '_+', '_', 'g'),
                                                  '^_|_$', '', 'g')), 'all match')
    UNION ALL
    -- A-10 System Administrators has an effective member: never a moment without one (§5.2 step 4).
    SELECT 'A-10 System Administrators member', 'switch',
           CASE WHEN EXISTS (SELECT 1 FROM sys_virtual_group_members m
                             JOIN sys_virtual_groups vg ON vg.id = m.group_id AND vg.code = 'SYSTEM_ADMINISTRATORS'
                             JOIN sys_users u ON u.id = m.user_id
                             WHERE m.list_type = 'MEMBER' AND u.status = 'ACTIVE' AND COALESCE(u.deleted, false) = false)
                THEN 0 ELSE 1 END,
           COALESCE((SELECT string_agg(u.username || ' ' || m.source, ', ' ORDER BY u.username)
                     FROM sys_virtual_group_members m
                     JOIN sys_virtual_groups vg ON vg.id = m.group_id AND vg.code = 'SYSTEM_ADMINISTRATORS'
                     JOIN sys_users u ON u.id = m.user_id), 'no members')
    UNION ALL
    -- A-11 Each row sits in a list of its group's type: SYSTEM / CUSTOM rows MEMBER, team rows TECH_LEAD / DEVELOPER.
    SELECT 'A-11 member lists fit group types', 'switch',
           (SELECT count(*) FROM sys_virtual_group_members m JOIN sys_virtual_groups vg ON vg.id = m.group_id
            WHERE (vg.type IN ('SYSTEM', 'CUSTOM') AND m.list_type <> 'MEMBER')
               OR (vg.type = 'DEVELOPER' AND m.list_type NOT IN ('TECH_LEAD', 'DEVELOPER'))),
           'rows in a list their group type does not have'
    UNION ALL
    -- A-12 Every function unit belongs to exactly one team (§6.3).
    SELECT 'A-12 one team per function unit', 'switch',
           (SELECT count(*) FROM dw_function_units fu
            WHERE (SELECT count(*) FROM dw_function_unit_dev_groups d JOIN sys_virtual_groups vg ON vg.id = d.virtual_group_id
                   WHERE d.function_unit_id = fu.id AND vg.type = 'DEVELOPER') <> 1
               OR EXISTS (SELECT 1 FROM dw_function_unit_dev_groups d LEFT JOIN sys_virtual_groups vg ON vg.id = d.virtual_group_id
                          WHERE d.function_unit_id = fu.id AND (vg.id IS NULL OR vg.type <> 'DEVELOPER'))),
           'function units without exactly one DEVELOPER group'
    UNION ALL
    -- A-13 A role has no default type (§5.3, 00-schema/100).
    SELECT 'A-13 sys_roles.type has no default', 'switch',
           (SELECT count(*) FROM information_schema.columns
            WHERE table_schema = current_schema() AND table_name = 'sys_roles' AND column_name = 'type'
              AND column_default IS NOT NULL),
           'column default of sys_roles.type'
    UNION ALL
    -- A-14 No BU_UNBOUNDED role is left and the type cannot come back (D-64, 90-post-seed/05).
    SELECT 'A-14 no BU_UNBOUNDED roles', 'switch',
           (SELECT count(*) FROM sys_roles WHERE type = 'BU_UNBOUNDED')
             + (SELECT count(*) FROM pg_constraint WHERE conrelid = 'sys_roles'::regclass AND conname = 'chk_role_type'
                  AND pg_get_constraintdef(oid) LIKE '%BU_UNBOUNDED%')
             + CASE WHEN EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'sys_roles'::regclass
                                   AND conname = 'chk_role_type') THEN 0 ELSE 1 END,
           COALESCE((SELECT string_agg(code, ', ' ORDER BY code) FROM sys_roles WHERE type = 'BU_UNBOUNDED'),
                    'no such role') || '; chk_role_type: '
             || COALESCE((SELECT pg_get_constraintdef(oid) FROM pg_constraint
                          WHERE conrelid = 'sys_roles'::regclass AND conname = 'chk_role_type'), 'missing')
    UNION ALL
    -- A-15 Some ACTIVE CUSTOM group maps an AD group, so AD users can enter the Portal (§10 step 3). WARN only:
    --      an environment without AD may run on manual members alone.
    SELECT 'A-15 CUSTOM group with an AD group', 'configuration',
           CASE WHEN EXISTS (SELECT 1 FROM sys_virtual_groups
                             WHERE type = 'CUSTOM' AND status = 'ACTIVE' AND NULLIF(TRIM(ad_group), '') IS NOT NULL)
                THEN 0 ELSE -1 END,
           COALESCE((SELECT string_agg(code || ' = ' || ad_group, ', ' ORDER BY code) FROM sys_virtual_groups
                     WHERE type = 'CUSTOM' AND status = 'ACTIVE' AND NULLIF(TRIM(ad_group), '') IS NOT NULL), 'none')
    UNION ALL
    -- A-16 Every ACTIVE team other than Public can get a Technical Lead: a Technical Lead AD group or a member on the
    --      list (§5.4: without one only a System Administrator can create its function units). WARN only.
    SELECT 'A-16 teams with a Technical Lead source', 'configuration',
           -(SELECT count(*) FROM sys_virtual_groups vg
             WHERE vg.type = 'DEVELOPER' AND vg.status = 'ACTIVE' AND vg.id <> 'vg-dev-public'
               AND NULLIF(TRIM(vg.tech_lead_ad_group), '') IS NULL
               AND NOT EXISTS (SELECT 1 FROM sys_virtual_group_members m
                               WHERE m.group_id = vg.id AND m.list_type = 'TECH_LEAD')),
           COALESCE((SELECT string_agg(vg.code, ', ' ORDER BY vg.code) FROM sys_virtual_groups vg
                     WHERE vg.type = 'DEVELOPER' AND vg.status = 'ACTIVE' AND vg.id <> 'vg-dev-public'
                       AND NULLIF(TRIM(vg.tech_lead_ad_group), '') IS NULL
                       AND NOT EXISTS (SELECT 1 FROM sys_virtual_group_members m
                                       WHERE m.group_id = vg.id AND m.list_type = 'TECH_LEAD')), 'all teams have one')
)
-- problem_count > 0 is a FAIL; a negative count marks a WARN-only check that found something.
SELECT id AS check_name, stage,
       CASE WHEN problem_count = 0 THEN 'PASS' WHEN problem_count < 0 THEN 'WARN' ELSE 'FAIL' END AS result,
       detail
FROM checks
ORDER BY id;

ROLLBACK;
