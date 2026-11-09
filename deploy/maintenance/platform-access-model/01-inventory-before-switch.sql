-- =====================================================
-- Platform access model: inventory before the switch (read-only)
-- docs/design/platform-access-model-requirements.md §10 step 1
-- =====================================================
-- Run on the database of the OLD version: once ahead of the release, so each team and the System
-- Administrators can fix or accept every row, and again in the downtime window right before
-- 00-schema/100-platform-access-model.sql, 90-post-seed/04-platform-access-model-switch.sql and
-- 90-post-seed/05-platform-access-model-drop-bu-unbounded-roles.sql. It predicts what 04 and 05 will do with the
-- rules those scripts apply, so it must not be run after the switch (02-precheck-after-switch.sql is the
-- post-switch counterpart).
--
-- Read-only: everything runs in one READ ONLY transaction that is rolled back at the end. Plain SQL
-- without psql meta-commands, so it runs in psql and in GUI clients (one result set per item; the
-- first column names the item). Columns the old schema lacks (list_type, source, team_code) are not
-- used; the AD source of a member row is added_by = 'HERMES_AD_SYNC', as the switch backfills it.
--   psql -X -v ON_ERROR_STOP=1 -U <user> -d <db> -f 01-inventory-before-switch.sql > inventory.txt
--   docker exec -i platform-postgres-dev psql -X -U <user> -d <db> < 01-inventory-before-switch.sql
--
-- Items:
--   I-01 users who lose Portal entry (§7.1, §4.3, D-04)
--   I-02 holders of SYS_ADMIN / AUDITOR / TECH_LEAD / TEAM_LEAD / DEVELOPER and where each grant comes from (§4.1, §4.2, §5.2)
--   I-03 team members and the list the switch moves them to; read-only members are not moved (D-24, D-28, D-30)
--   I-04 AD mappings the switch clears or drops (D-29, §5.2): set them again in the switch configuration (§10 step 3)
--   I-05 team codes regenerated from team names: empty or clashing ones make the switch refuse (§5.4)
--   I-06 function units that do not belong to exactly one team after the switch: the switch refuses (§6.3, §6.4)
--   I-07 group role bindings the switch deletes (§5.3, D-03)
--   I-08 BU_UNBOUNDED roles: the switch (90-post-seed/05) deletes each with its grants and access rules (§5.3, D-64)
--   I-09 published process tasks assigned the old ways (VIRTUAL_GROUP, BU_UNBOUNDED_ROLE) or naming TEAM_LEAD
--        or a BU_UNBOUNDED role (§10 step 1, A-28, A-30)
--   I-10 other references to TEAM_LEAD: access rules, BI (§4.2)
--   I-11 users without an Entity / Function Manager, or whose two managers would have no Portal entry (§7.3)
--   I-12 published views nobody can see: empty or unpaired access rules, rules naming BU / Role ids that do not
--        exist here (§7.2, D-05)
--   I-13 test accounts used by scripts and E2E: do they keep Portal entry (§10 step 1; edit the list per environment)
--   I-14 pending permission requests for a group that goes away or is no longer CUSTOM
--   I-15 pending permission requests that cannot be granted after the switch: the old direct role assignment
--        (ROLE_ASSIGNMENT) is removed, TEAM_LEAD / BU_UNBOUNDED roles are deleted, and the legacy FUNCTION / DATA /
--        TEMPORARY requests have no approver
--   I-16 action "allowed roles" naming TEAM_LEAD or a BU_UNBOUNDED role, in design (DW) and published (Admin)
--
-- Dev only: 90-post-seed/03-platform-access-model-dev-prepare.sql runs right before the switch; it puts
-- developer into E2E_WORKFLOW_SIMULATION and moves Platform Showcase to Public, which this file does not foresee.
-- =====================================================

START TRANSACTION READ ONLY;

-- I-01 Users who lose Portal entry. Today any ACTIVE SYSTEM / CUSTOM / DEVELOPER group lets a user in (the old
-- PortalEntitlementService rule, blank type counted as CUSTOM); after the switch only an ACTIVE CUSTOM group does,
-- and Department Managers is deleted while Public becomes a team. Keep them by adding them to a CUSTOM group or
-- by mapping a CUSTOM group to their AD group (for Hermes Default Users members: its AD group) in §10 step 3.
WITH old_entry AS (
    SELECT m.user_id,
           string_agg(DISTINCT vg.code || ' (' || COALESCE(NULLIF(TRIM(vg.type), ''), 'CUSTOM')
                      || CASE WHEN m.added_by = 'HERMES_AD_SYNC' THEN ', AD' ELSE '' END || ')', ', ') AS entered_through
    FROM sys_virtual_group_members m
    JOIN sys_virtual_groups vg ON vg.id = m.group_id
    WHERE UPPER(TRIM(COALESCE(vg.status, ''))) = 'ACTIVE'
      AND UPPER(TRIM(COALESCE(NULLIF(TRIM(vg.type), ''), 'CUSTOM'))) IN ('SYSTEM', 'CUSTOM', 'DEVELOPER')
    GROUP BY m.user_id
), new_entry AS (
    SELECT DISTINCT m.user_id
    FROM sys_virtual_group_members m
    JOIN sys_virtual_groups vg ON vg.id = m.group_id
    WHERE vg.type = 'CUSTOM' AND vg.status = 'ACTIVE' AND vg.code <> 'MANAGERS' AND vg.id <> 'vg-dev-public'
)
SELECT 'I-01 loses Portal entry' AS item, count(*) OVER () AS total,
       u.username, u.display_name, u.employee_id, o.entered_through,
       (SELECT count(*) FROM sys_user_business_unit_roles ubr WHERE ubr.user_id = u.id) AS ubr_count,
       u.last_login_at
FROM sys_users u
JOIN old_entry o ON o.user_id = u.id
WHERE u.status = 'ACTIVE' AND COALESCE(u.deleted, false) = false
  AND NOT EXISTS (SELECT 1 FROM new_entry n WHERE n.user_id = u.id)
ORDER BY u.last_login_at DESC NULLS LAST, u.username;

-- I-02 Platform role and TEAM_LEAD holders, one row per grant. After the switch SYS_ADMIN belongs to the initial
-- System Administrator only (D-22: developer in dev, admin in k8s), AUDITOR to nobody, TECH_LEAD / DEVELOPER come
-- from team lists only (I-03) and TEAM_LEAD is gone. Every other grant listed here is removed; bring the people
-- back through the AD mappings of System Administrators / Auditors / the teams, or add them by hand.
WITH grants AS (
    SELECT ur.user_id AS target_id, ur.role_id, 'sys_user_roles' AS granted_by,
           NULL::varchar AS group_code, NULL::varchar AS group_type, NULL::varchar AS group_status, NULL::boolean AS via_ad
    FROM sys_user_roles ur
    UNION ALL
    SELECT ra.target_id, ra.role_id, 'sys_role_assignments ' || ra.target_type, NULL, NULL, NULL, NULL
    FROM sys_role_assignments ra
    WHERE ra.target_type <> 'VIRTUAL_GROUP'
      AND (ra.valid_from IS NULL OR ra.valid_from <= CURRENT_TIMESTAMP)
      AND (ra.valid_to IS NULL OR ra.valid_to >= CURRENT_TIMESTAMP)
    UNION ALL
    SELECT m.user_id, vgr.role_id, 'sys_virtual_group_roles', vg.code, vg.type, vg.status,
           COALESCE(m.added_by = 'HERMES_AD_SYNC', false)
    FROM sys_virtual_group_members m
    JOIN sys_virtual_group_roles vgr ON vgr.virtual_group_id = m.group_id
    JOIN sys_virtual_groups vg ON vg.id = m.group_id
    UNION ALL
    SELECT m.user_id, ra.role_id, 'sys_role_assignments VIRTUAL_GROUP', vg.code, vg.type, vg.status,
           COALESCE(m.added_by = 'HERMES_AD_SYNC', false)
    FROM sys_role_assignments ra
    JOIN sys_virtual_group_members m ON ra.target_type = 'VIRTUAL_GROUP' AND ra.target_id = m.group_id
    JOIN sys_virtual_groups vg ON vg.id = m.group_id
    WHERE (ra.valid_from IS NULL OR ra.valid_from <= CURRENT_TIMESTAMP)
      AND (ra.valid_to IS NULL OR ra.valid_to >= CURRENT_TIMESTAMP)
)
SELECT 'I-02 platform role holders' AS item, r.code AS role, COALESCE(u.username, g.target_id) AS holder,
       u.status AS user_status, g.granted_by, g.group_code, g.group_type, g.group_status,
       CASE WHEN g.via_ad THEN 'AD' WHEN NOT g.via_ad THEN 'MANUAL' END AS membership_source
FROM grants g
JOIN sys_roles r ON r.id = g.role_id AND r.code IN ('SYS_ADMIN', 'AUDITOR', 'TECH_LEAD', 'TEAM_LEAD', 'DEVELOPER')
LEFT JOIN sys_users u ON u.id = g.target_id
ORDER BY r.code, holder, g.granted_by, g.group_code;

-- I-03 Team (DEVELOPER group) members and the lists the switch moves them to, by the global role they hold today
-- (D-24). A TECH_LEAD held only through an AD-synced membership counts as DEVELOPER (D-28); a Technical Lead row is
-- MANUAL whatever the membership's source (D-30); a Developer row keeps it. "not moved" rows are the read-only
-- members the switch drops: add them to a list by hand if they need the team. AD-sourced Developer rows are
-- removed by the first AD sync unless the Developer AD group configured for the team brings them back (D-29).
WITH global_roles AS (
    SELECT DISTINCT g.user_id,
           CASE WHEN r.code = 'TECH_LEAD' AND g.via_ad THEN 'DEVELOPER' ELSE r.code END AS code
    FROM (
        SELECT ur.user_id, ur.role_id, false AS via_ad FROM sys_user_roles ur
        UNION
        SELECT m.user_id, vgr.role_id, COALESCE(m.added_by = 'HERMES_AD_SYNC', false)
        FROM sys_virtual_group_members m
        JOIN sys_virtual_group_roles vgr ON vgr.virtual_group_id = m.group_id
        UNION
        SELECT ra.target_id, ra.role_id, false
        FROM sys_role_assignments ra
        WHERE ra.target_type = 'USER'
          AND (ra.valid_from IS NULL OR ra.valid_from <= CURRENT_TIMESTAMP)
          AND (ra.valid_to IS NULL OR ra.valid_to >= CURRENT_TIMESTAMP)
        UNION
        SELECT m.user_id, ra.role_id, COALESCE(m.added_by = 'HERMES_AD_SYNC', false)
        FROM sys_role_assignments ra
        JOIN sys_virtual_group_members m ON ra.target_type = 'VIRTUAL_GROUP' AND ra.target_id = m.group_id
        WHERE (ra.valid_from IS NULL OR ra.valid_from <= CURRENT_TIMESTAMP)
          AND (ra.valid_to IS NULL OR ra.valid_to >= CURRENT_TIMESTAMP)
    ) g
    JOIN sys_roles r ON r.id = g.role_id AND r.status = 'ACTIVE' AND r.code IN ('TECH_LEAD', 'DEVELOPER')
)
SELECT 'I-03 team members into lists' AS item, vg.code AS team_code, vg.name AS team_name, vg.status AS team_status,
       COALESCE(u.username, m.user_id) AS member, u.status AS user_status,
       CASE WHEN m.added_by = 'HERMES_AD_SYNC' THEN 'AD' ELSE 'MANUAL' END AS membership_source,
       COALESCE(string_agg(gr.code || ' list, ' || CASE WHEN gr.code = 'TECH_LEAD' OR COALESCE(m.added_by, '') <> 'HERMES_AD_SYNC'
                                                     THEN 'MANUAL' ELSE 'AD' END, '; ' ORDER BY gr.code),
                'not moved: read-only member (D-24)') AS after_switch
FROM sys_virtual_group_members m
JOIN sys_virtual_groups vg ON vg.id = m.group_id AND vg.type = 'DEVELOPER'
LEFT JOIN sys_users u ON u.id = m.user_id
LEFT JOIN global_roles gr ON gr.user_id = m.user_id
GROUP BY vg.code, vg.name, vg.status, u.username, m.user_id, u.status, m.added_by
ORDER BY vg.code, (count(gr.code) = 0) DESC, member;

-- I-04 AD mappings the switch clears or drops. Team mappings are cleared (D-29): configure each team's Developer
-- and Technical Lead AD groups again (§10 step 3). System Administrators and Auditors lose theirs (§5.2) and need
-- them set again; deleted groups take theirs with them. CUSTOM mappings stay. ad_members = members synced from AD.
WITH removed AS (
    SELECT vg.id FROM sys_virtual_groups vg
    WHERE (vg.type = 'SYSTEM' AND vg.code NOT IN ('SYSTEM_ADMINISTRATORS', 'AUDITORS'))
       OR (vg.type = 'CUSTOM' AND vg.code = 'MANAGERS')
)
SELECT 'I-04 AD mappings' AS item, vg.code, vg.name, vg.type, vg.status, vg.ad_group,
       CASE WHEN vg.id IN (SELECT id FROM removed) THEN 'group deleted'
            WHEN vg.id = 'vg-dev-public' THEN 'cleared: Public becomes a team without lists or AD groups'
            WHEN vg.type = 'SYSTEM' THEN 'cleared: set this SYSTEM group''s AD group again (§10 step 3)'
            WHEN vg.type = 'DEVELOPER' THEN 'cleared: set the team''s Developer / Technical Lead AD groups (§10 step 3, D-29)'
            ELSE 'kept' END AS after_switch,
       (SELECT count(*) FROM sys_virtual_group_members m WHERE m.group_id = vg.id) AS members,
       (SELECT count(*) FROM sys_virtual_group_members m WHERE m.group_id = vg.id AND m.added_by = 'HERMES_AD_SYNC') AS ad_members
FROM sys_virtual_groups vg
WHERE NULLIF(TRIM(vg.ad_group), '') IS NOT NULL
ORDER BY CASE vg.type WHEN 'SYSTEM' THEN 1 WHEN 'DEVELOPER' THEN 2 ELSE 3 END, vg.code;

-- I-05 Team codes regenerated from team names, as VirtualGroupManagerComponent.generateCode does (Public keeps
-- DEV_TEAM_PUBLIC). The switch refuses when a code comes out empty, longer than 50, or the same as another team's or
-- group's: rename those teams first. Changed codes are fine inside one environment (DW and Automation link teams by
-- id), but the same team must carry the same name in every environment so packages find it (§5.4, D-23).
WITH removed AS (
    SELECT vg.id FROM sys_virtual_groups vg
    WHERE (vg.type = 'SYSTEM' AND vg.code NOT IN ('SYSTEM_ADMINISTRATORS', 'AUDITORS'))
       OR (vg.type = 'CUSTOM' AND vg.code = 'MANAGERS')
), teams AS (
    SELECT id, name, status, code AS old_code,
           regexp_replace(regexp_replace(regexp_replace(upper(name), '[^A-Z0-9]', '_', 'g'), '_+', '_', 'g'),
                          '^_|_$', '', 'g') AS new_code
    FROM sys_virtual_groups
    WHERE type = 'DEVELOPER' AND id <> 'vg-dev-public'
), checked AS (
    SELECT t.*,
           CASE WHEN t.new_code = '' THEN 'REFUSED: name gives an empty code; use letters or digits'
                WHEN length(t.new_code) > 50 THEN 'REFUSED: code longer than 50'
                WHEN EXISTS (SELECT 1 FROM teams o WHERE o.new_code = t.new_code AND o.id <> t.id)
                    THEN 'REFUSED: same code as team ' || (SELECT string_agg(o.name, ', ') FROM teams o
                                                          WHERE o.new_code = t.new_code AND o.id <> t.id)
                WHEN EXISTS (SELECT 1 FROM sys_virtual_groups g
                             WHERE g.code = t.new_code AND g.id NOT IN (SELECT id FROM teams)
                               AND g.id NOT IN (SELECT id FROM removed))
                    THEN 'REFUSED: same code as group ' || t.new_code
                WHEN t.new_code <> t.old_code THEN 'code changes'
                ELSE 'unchanged' END AS verdict
    FROM teams t
)
SELECT 'I-05 team codes' AS item, name AS team_name, status, old_code, new_code, verdict
FROM checked
ORDER BY (verdict LIKE 'REFUSED%') DESC, name;

-- I-06 Function units that would not belong to exactly one team: the switch refuses until each has one (§6.3).
-- Links to the groups the switch deletes are dropped; Public counts as a team. Assign each one in DW (existing
-- screens can add and remove teams) before the switch (§10 step 2).
WITH removed AS (
    SELECT vg.id FROM sys_virtual_groups vg
    WHERE (vg.type = 'SYSTEM' AND vg.code NOT IN ('SYSTEM_ADMINISTRATORS', 'AUDITORS'))
       OR (vg.type = 'CUSTOM' AND vg.code = 'MANAGERS')
       OR (vg.code = 'DEFAULT_DEV_TEAM'
           AND NOT EXISTS (SELECT 1 FROM sys_virtual_group_members m WHERE m.group_id = vg.id)
           AND NOT EXISTS (SELECT 1 FROM dw_function_unit_dev_groups d WHERE d.virtual_group_id = vg.id))
), links AS (
    SELECT d.function_unit_id, COALESCE(vg.code, d.virtual_group_id) AS group_ref,
           CASE WHEN d.virtual_group_id IN (SELECT id FROM removed) THEN 'dropped'
                WHEN vg.id IS NULL THEN 'missing group'
                WHEN vg.type = 'DEVELOPER' OR vg.id = 'vg-dev-public' THEN 'team'
                ELSE 'not a team (' || COALESCE(vg.type, '?') || ')' END AS kind
    FROM dw_function_unit_dev_groups d
    LEFT JOIN sys_virtual_groups vg ON vg.id = d.virtual_group_id
)
SELECT 'I-06 function unit ownership' AS item, fu.id, fu.code, fu.name, fu.status,
       string_agg(l.group_ref || ' = ' || l.kind, '; ' ORDER BY l.group_ref) AS links,
       CASE WHEN count(l.kind) FILTER (WHERE l.kind <> 'dropped') = 0 THEN 'no team after the switch'
            WHEN count(l.kind) FILTER (WHERE l.kind <> 'dropped') > 1 THEN 'several groups: keep exactly one team'
            ELSE 'its one group is not a team' END AS verdict
FROM dw_function_units fu
LEFT JOIN links l ON l.function_unit_id = fu.id
GROUP BY fu.id, fu.code, fu.name, fu.status
HAVING count(l.kind) FILTER (WHERE l.kind <> 'dropped') <> 1
    OR count(l.kind) FILTER (WHERE l.kind = 'team') <> 1
ORDER BY fu.id;

-- I-07 Group role bindings the switch deletes: every binding but System Administrators -> SYS_ADMIN and
-- Auditors -> AUDITOR (D-03). Members of a CUSTOM group bound to a business role lose that role; they apply
-- for BU + Role in the Portal instead.
SELECT 'I-07 group role bindings removed' AS item, COALESCE(vg.code, b.group_id) AS group_ref, vg.type AS group_type,
       vg.status AS group_status, COALESCE(r.code, b.role_id) AS role, r.type AS role_type, b.stored_in,
       (SELECT count(*) FROM sys_virtual_group_members m WHERE m.group_id = b.group_id) AS members
FROM (
    SELECT virtual_group_id AS group_id, role_id, 'sys_virtual_group_roles' AS stored_in FROM sys_virtual_group_roles
    UNION ALL
    SELECT target_id, role_id, 'sys_role_assignments' FROM sys_role_assignments WHERE target_type = 'VIRTUAL_GROUP'
) b
LEFT JOIN sys_virtual_groups vg ON vg.id = b.group_id
LEFT JOIN sys_roles r ON r.id = b.role_id
WHERE NOT ((vg.code = 'SYSTEM_ADMINISTRATORS' AND r.code = 'SYS_ADMIN') OR (vg.code = 'AUDITORS' AND r.code = 'AUDITOR'))
ORDER BY vg.type, group_ref, role;

-- I-08 BU_UNBOUNDED roles. The switch deletes every one (90-post-seed/05, D-64): with it go its group bindings and
-- assignments, direct grants, BU bindings and UBR, role permissions, BI RBAC mappings and the access rules naming it
-- (counts below, all deleted). Its holders lose it; where the permission is still needed a System Administrator
-- creates a BU_BOUNDED role and the holders apply for it in the Portal. Not deleted: process tasks (I-09) and actions
-- (I-16) that name it, and requests for it (I-15) — the owning team changes and republishes those.
SELECT 'I-08 BU_UNBOUNDED roles deleted by the switch' AS item, r.code, r.name, r.status,
       (SELECT count(DISTINCT m.user_id) FROM sys_virtual_group_members m
        WHERE m.group_id IN (SELECT virtual_group_id FROM sys_virtual_group_roles WHERE role_id = r.id
                             UNION SELECT target_id FROM sys_role_assignments
                             WHERE role_id = r.id AND target_type = 'VIRTUAL_GROUP')) AS holders_via_groups,
       (SELECT count(*) FROM sys_virtual_group_roles x WHERE x.role_id = r.id)
         + (SELECT count(*) FROM sys_role_assignments x WHERE x.role_id = r.id) AS bindings_and_assignments,
       (SELECT count(*) FROM sys_user_roles x WHERE x.role_id = r.id) AS user_roles,
       (SELECT count(*) FROM sys_user_business_unit_roles x WHERE x.role_id = r.id) AS ubr,
       (SELECT count(*) FROM sys_business_unit_roles x WHERE x.role_id = r.id) AS bu_bindings,
       (SELECT count(*) FROM sys_role_permissions x WHERE x.role_id = r.id)
         + (SELECT count(*) FROM sys_developer_role_permissions x WHERE x.role_id = r.id) AS role_permissions,
       (SELECT count(*) FROM sys_function_unit_access x WHERE x.target_type = 'ROLE' AND x.target_id IN (r.id, r.code))
         + (SELECT count(*) FROM sys_function_unit_audit_access x WHERE x.target_type = 'ROLE' AND x.target_id IN (r.id, r.code))
         + (SELECT count(*) FROM dw_function_unit_access x WHERE x.target_type = 'ROLE' AND x.target_id IN (r.id, r.code)) AS fu_access_rules,
       (SELECT count(*) FROM dw_main_table_view_access x WHERE x.target_type = 'ROLE' AND x.target_id IN (r.id, r.code)) AS view_rules,
       (SELECT count(*) FROM rt_table_access x WHERE x.target_type = 'ROLE' AND x.target_id IN (r.id, r.code)) AS relation_table_rules,
       (SELECT count(*) FROM admin_data_permission_rules x WHERE x.target_type = 'ROLE' AND x.target_id IN (r.id, r.code)) AS data_rules,
       (SELECT count(*) FROM bi_rbac_mapping x WHERE x.sys_role_id = r.id)
         + (SELECT count(*) FROM bi_dashboard_assignment x WHERE x.target_type = 'ROLE' AND x.target_id IN (r.id, r.code)) AS bi_rows
FROM sys_roles r
WHERE r.type = 'BU_UNBOUNDED'
ORDER BY r.code;

-- I-09 Published process tasks to look at, every imported version (latest_version marks the newest import of the
-- code, the one new requests start). Reasons: assigned the old ways VIRTUAL_GROUP / BU_UNBOUNDED_ROLE, which assign
-- nobody today and after (A-30), or naming TEAM_LEAD or a BU_UNBOUNDED role in roleId / roleIds. The owning team
-- changes the task to a current assignment and republishes. Older imports stored the BPMN Base64-encoded.
WITH proc AS (
    SELECT fu.code, fu.name, fu.version,
           fu.id = (SELECT f2.id FROM sys_function_units f2 WHERE f2.code = fu.code
                    ORDER BY f2.imported_at DESC NULLS LAST, f2.created_at DESC NULLS LAST LIMIT 1) AS latest_version,
           CASE WHEN ltrim(c.content_data) LIKE '<%' THEN c.content_data
                ELSE convert_from(decode(btrim(c.content_data), 'base64'), 'UTF8') END AS xml
    FROM sys_function_unit_contents c
    JOIN sys_function_units fu ON fu.id = c.function_unit_id
    WHERE c.content_type = 'PROCESS' AND c.content_data IS NOT NULL
), tasks AS (
    -- Mark every userTask start, cut at every userTask end, keep the text from the last start of each piece.
    SELECT p.code, p.name, p.version, p.latest_version, regexp_replace(piece, '^.*\x01', '') AS task
    FROM proc p,
         regexp_split_to_table(regexp_replace(p.xml, '<([A-Za-z0-9_]+:)?userTask(\s|>)', E'\x01<userTask\\2', 'g'),
                               '</([A-Za-z0-9_]+:)?userTask>') AS piece
    WHERE strpos(piece, E'\x01') > 0
), props AS (
    SELECT t.code, t.name, t.version, t.latest_version,
           substring(t.task from '^<userTask[^>]*\sid\s*=\s*["'']([^"'']*)') AS task_id,
           substring(t.task from '^<userTask[^>]*\sname\s*=\s*["'']([^"'']*)') AS task_name,
           substring(tag[1] from '\sname\s*=\s*["'']([^"'']*)') AS prop,
           substring(tag[1] from '\svalue\s*=\s*["'']([^"'']*)') AS value
    FROM tasks t, regexp_matches(t.task, '(<([A-Za-z0-9_]+:)?property\s[^>]*>)', 'g') AS tag
), assignments AS (
    SELECT code, name, version, latest_version, task_id, task_name,
           max(value) FILTER (WHERE prop = 'assigneeType') AS assignee_type,
           max(value) FILTER (WHERE prop = 'assigneeValue') AS assignee_value,
           concat_ws(',', max(value) FILTER (WHERE prop = 'roleId'), max(value) FILTER (WHERE prop = 'roleIds')) AS role_refs
    FROM props
    GROUP BY code, name, version, latest_version, task_id, task_name
), flagged AS (
    SELECT a.*,
           concat_ws('; ',
               CASE WHEN upper(a.assignee_type) IN ('VIRTUAL_GROUP', 'VIRTUALGROUP', 'BU_UNBOUNDED_ROLE')
                    THEN 'old assignment ' || a.assignee_type || ' (assigns nobody)' END,
               (SELECT 'names ' || string_agg(DISTINCT r.code || ' (' || r.type || ')', ', ')
                FROM regexp_split_to_table(a.role_refs, '\s*,\s*') AS ref
                JOIN sys_roles r ON ref IN (r.id, r.code)
                WHERE r.code = 'TEAM_LEAD' OR r.type = 'BU_UNBOUNDED')) AS reasons
    FROM assignments a
)
SELECT 'I-09 published process tasks' AS item, code, name, version, latest_version, task_id, task_name,
       assignee_type, assignee_value, role_refs, reasons
FROM flagged
WHERE reasons <> ''
ORDER BY code, latest_version DESC, version, task_id;

-- I-10 Other references to TEAM_LEAD; the switch deletes them with the role (§4.2). Its holders are in I-02.
WITH tl AS (SELECT id, code FROM sys_roles WHERE code = 'TEAM_LEAD')
SELECT 'I-10 TEAM_LEAD references' AS item, x.stored_in, x.reference
FROM (
    SELECT 'sys_function_unit_access' AS stored_in, COALESCE(fu.code, a.function_unit_id) || ' ' || a.access_type AS reference
    FROM sys_function_unit_access a JOIN tl ON a.target_type = 'ROLE' AND a.target_id IN (tl.id, tl.code)
    LEFT JOIN sys_function_units fu ON fu.id = a.function_unit_id
    UNION ALL
    SELECT 'sys_function_unit_audit_access', COALESCE(fu.code, a.function_unit_id)
    FROM sys_function_unit_audit_access a JOIN tl ON a.target_type = 'ROLE' AND a.target_id IN (tl.id, tl.code)
    LEFT JOIN sys_function_units fu ON fu.id = a.function_unit_id
    UNION ALL
    SELECT 'dw_function_unit_access', COALESCE(fu.code, a.function_unit_id::text) || ' ' || a.access_type
    FROM dw_function_unit_access a JOIN tl ON a.target_type = 'ROLE' AND a.target_id IN (tl.id, tl.code)
    LEFT JOIN dw_function_units fu ON fu.id = a.function_unit_id
    UNION ALL
    SELECT 'dw_main_table_view_access', COALESCE(fu.code || ' / ' || v.view_name, a.view_config_id::text)
    FROM dw_main_table_view_access a JOIN tl ON a.target_type = 'ROLE' AND a.target_id IN (tl.id, tl.code)
    LEFT JOIN dw_main_table_view_configs v ON v.id = a.view_config_id
    LEFT JOIN dw_function_units fu ON fu.id = v.function_unit_id
    UNION ALL
    SELECT 'rt_table_access', COALESCE(t.table_name, a.table_id::text) || ' ' || a.permission_level
    FROM rt_table_access a JOIN tl ON a.target_type = 'ROLE' AND a.target_id IN (tl.id, tl.code)
    LEFT JOIN rt_table_definitions t ON t.id = a.table_id
    UNION ALL
    SELECT 'admin_data_permission_rules', a.name
    FROM admin_data_permission_rules a JOIN tl ON a.target_type = 'ROLE' AND a.target_id IN (tl.id, tl.code)
    UNION ALL
    SELECT 'bi_rbac_mapping', 'Superset role ' || b.superset_role_id
    FROM bi_rbac_mapping b JOIN tl ON b.sys_role_id = tl.id
    UNION ALL
    SELECT 'bi_dashboard_assignment', 'dashboard ' || a.dashboard_id
    FROM bi_dashboard_assignment a JOIN tl ON a.target_type = 'ROLE' AND a.target_id IN (tl.id, tl.code)
) x
ORDER BY x.stored_in, x.reference;

-- I-11 Managers (§7.3). The tasks assigned to someone who leaves go to their Entity Manager, else their Function
-- Manager; a manager counts only with an ACTIVE account and Portal entry after the switch. Lists users who can use
-- the Portal today or hold a BU + Role: missing managers are a data problem to fix before the switch (§10 step 2),
-- and managers without entry go into a CUSTOM group in the switch configuration (§10 step 3).
WITH new_entry AS (
    SELECT DISTINCT m.user_id
    FROM sys_virtual_group_members m
    JOIN sys_virtual_groups vg ON vg.id = m.group_id
    WHERE vg.type = 'CUSTOM' AND vg.status = 'ACTIVE' AND vg.code <> 'MANAGERS' AND vg.id <> 'vg-dev-public'
), people AS (
    SELECT u.* FROM sys_users u
    WHERE u.status = 'ACTIVE' AND COALESCE(u.deleted, false) = false
      AND (EXISTS (SELECT 1 FROM sys_virtual_group_members m JOIN sys_virtual_groups vg ON vg.id = m.group_id
                   WHERE m.user_id = u.id AND vg.status = 'ACTIVE')
           OR EXISTS (SELECT 1 FROM sys_user_business_unit_roles ubr WHERE ubr.user_id = u.id))
), checked AS (
    SELECT p.username, p.display_name, p.entity_manager_id, p.function_manager_id,
           em.username AS entity_manager, fm.username AS function_manager,
           CASE WHEN p.entity_manager_id IS NULL THEN 'none'
                WHEN em.id IS NULL THEN 'id not found'
                WHEN em.status <> 'ACTIVE' OR COALESCE(em.deleted, false) THEN 'account ' || CASE WHEN em.deleted THEN 'deleted' ELSE em.status END
                WHEN em.id NOT IN (SELECT user_id FROM new_entry) THEN 'no Portal entry'
                ELSE 'available' END AS entity_manager_state,
           CASE WHEN p.function_manager_id IS NULL THEN 'none'
                WHEN fm.id IS NULL THEN 'id not found'
                WHEN fm.status <> 'ACTIVE' OR COALESCE(fm.deleted, false) THEN 'account ' || CASE WHEN fm.deleted THEN 'deleted' ELSE fm.status END
                WHEN fm.id NOT IN (SELECT user_id FROM new_entry) THEN 'no Portal entry'
                ELSE 'available' END AS function_manager_state
    FROM people p
    LEFT JOIN sys_users em ON em.id = p.entity_manager_id
    LEFT JOIN sys_users fm ON fm.id = p.function_manager_id
)
SELECT 'I-11 managers' AS item, count(*) OVER () AS total, username, display_name,
       COALESCE(entity_manager, entity_manager_id) AS entity_manager, entity_manager_state,
       COALESCE(function_manager, function_manager_id) AS function_manager, function_manager_state,
       concat_ws('; ',
           CASE WHEN entity_manager_id IS NULL THEN 'no Entity Manager' END,
           CASE WHEN function_manager_id IS NULL THEN 'no Function Manager' END,
           CASE WHEN entity_manager_state <> 'available' AND function_manager_state <> 'available'
                THEN 'neither manager available' END) AS problem
FROM checked
WHERE entity_manager_id IS NULL OR function_manager_id IS NULL
   OR (entity_manager_state <> 'available' AND function_manager_state <> 'available')
ORDER BY username;

-- I-12 Published views nobody can see. Access is the signed-in user's current BU + Role against the view's rules,
-- BU and Role paired, no role bypassing it (§7.2, D-05): empty or unpaired rules hide the view from everyone,
-- System Administrators included, and so do rules whose BU or Role ids do not exist here (rules store ids; views
-- imported from another environment carry that environment's ids). "some ids missing" views stay visible to the
-- pairs that do exist. Each team fixes its views in DW View Design and republishes before the switch.
WITH rules AS (
    SELECT a.view_config_id, a.target_type, a.target_id,
           CASE a.target_type
               WHEN 'BUSINESS_UNIT' THEN EXISTS (SELECT 1 FROM sys_business_units b WHERE b.id = a.target_id)
               WHEN 'ROLE' THEN EXISTS (SELECT 1 FROM sys_roles r WHERE r.id = a.target_id)
           END AS found
    FROM dw_main_table_view_access a
    WHERE NULLIF(TRIM(a.target_id), '') IS NOT NULL
), per_view AS (
    SELECT v.id AS view_id, v.view_name, v.is_default, fu.code, fu.name AS function_unit,
           (SELECT string_agg(COALESCE(vg.name, d.virtual_group_id), ', ') FROM dw_function_unit_dev_groups d
            LEFT JOIN sys_virtual_groups vg ON vg.id = d.virtual_group_id WHERE d.function_unit_id = fu.id) AS team,
           EXISTS (SELECT 1 FROM sys_function_units s WHERE s.code = fu.code AND s.enabled) AS enabled_in_admin,
           count(r.view_config_id) FILTER (WHERE upper(r.target_type) = 'BUSINESS_UNIT') AS bu_rules,
           count(r.view_config_id) FILTER (WHERE upper(r.target_type) = 'BUSINESS_UNIT' AND r.found) AS bu_found,
           count(r.view_config_id) FILTER (WHERE upper(r.target_type) = 'ROLE') AS role_rules,
           count(r.view_config_id) FILTER (WHERE upper(r.target_type) = 'ROLE' AND r.found) AS role_found,
           string_agg(r.target_type || ' ' || r.target_id, ', ') FILTER (WHERE NOT r.found) AS missing_ids
    FROM dw_main_table_view_configs v
    JOIN dw_function_units fu ON fu.id = v.function_unit_id
    LEFT JOIN rules r ON r.view_config_id = v.id
    WHERE v.status = 'PUBLISHED'
    GROUP BY v.id, v.view_name, v.is_default, fu.id, fu.code, fu.name
)
SELECT 'I-12 published views nobody can see' AS item, code, function_unit, team, enabled_in_admin, view_id, view_name,
       is_default, bu_rules, bu_found, role_rules, role_found,
       CASE WHEN bu_rules + role_rules = 0 THEN 'empty: nobody sees it'
            WHEN bu_rules = 0 OR role_rules = 0 THEN 'unpaired: BU and Role must both be set'
            WHEN bu_found = 0 OR role_found = 0 THEN 'no rule BU or no rule Role exists here: nobody sees it'
            ELSE 'some ids missing: visible to the pairs that exist' END AS verdict,
       missing_ids
FROM per_view
WHERE bu_rules + role_rules = 0 OR bu_found = 0 OR role_found = 0 OR bu_found < bu_rules OR role_found < role_rules
ORDER BY code, view_id;

-- I-13 Test accounts of regression scripts and E2E: they must stay in a CUSTOM group or the scripts cannot sign in
-- to the Portal (§10 step 1). The list is the accounts of this repository's scripts; add or remove per environment.
WITH accounts(username) AS (
    VALUES ('developer'), ('admin'), ('44027893'), ('12345'), ('123456'),
           ('e2e_zhangwei'), ('e2e_lina'), ('e2e_wangfang'), ('e2e_zhaomin'), ('e2e_sunqiang'), ('e2e_zhoujie'),
           ('e2e_wugang'), ('view_allowed'), ('view_wrong_bu'), ('view_wrong_role'), ('view_nofu')
), new_entry AS (
    SELECT m.user_id, string_agg(vg.code, ', ' ORDER BY vg.code) AS custom_groups
    FROM sys_virtual_group_members m
    JOIN sys_virtual_groups vg ON vg.id = m.group_id
    WHERE vg.type = 'CUSTOM' AND vg.status = 'ACTIVE' AND vg.code <> 'MANAGERS' AND vg.id <> 'vg-dev-public'
    GROUP BY m.user_id
)
SELECT 'I-13 test accounts' AS item, a.username, u.status,
       CASE WHEN u.id IS NULL THEN 'no such account'
            WHEN u.status <> 'ACTIVE' OR COALESCE(u.deleted, false) THEN 'account not active'
            WHEN n.user_id IS NULL THEN 'LOSES Portal entry'
            ELSE 'keeps Portal entry' END AS after_switch,
       n.custom_groups,
       (SELECT string_agg(vg.code, ', ' ORDER BY vg.code) FROM sys_virtual_group_members m
        JOIN sys_virtual_groups vg ON vg.id = m.group_id WHERE m.user_id = u.id) AS all_groups
FROM accounts a
LEFT JOIN sys_users u ON u.username = a.username
LEFT JOIN new_entry n ON n.user_id = u.id
ORDER BY (n.user_id IS NULL AND u.id IS NOT NULL) DESC, a.username;

-- I-14 Pending permission requests for a group that is deleted or stops being a CUSTOM group: approving them
-- fails after the switch. Reject or cancel them, or let the applicant apply again.
WITH removed AS (
    SELECT vg.id FROM sys_virtual_groups vg
    WHERE (vg.type = 'SYSTEM' AND vg.code NOT IN ('SYSTEM_ADMINISTRATORS', 'AUDITORS'))
       OR (vg.type = 'CUSTOM' AND vg.code = 'MANAGERS')
)
SELECT 'I-14 pending requests for a group that goes away' AS item, pr.id, pr.request_type,
       COALESCE(u.username, pr.applicant_id) AS applicant, COALESCE(vg.code, pr.virtual_group_id) AS group_ref,
       pr.virtual_group_name, pr.created_at,
       CASE WHEN vg.id IS NULL THEN 'group no longer exists'
            WHEN vg.id IN (SELECT id FROM removed) THEN 'group deleted by the switch'
            WHEN vg.id = 'vg-dev-public' THEN 'Public becomes a team'
            ELSE 'not a CUSTOM group (' || COALESCE(vg.type, '?') || ')' END AS reason
FROM up_permission_request pr
LEFT JOIN sys_virtual_groups vg ON vg.id = pr.virtual_group_id
LEFT JOIN sys_users u ON u.id = pr.applicant_id
WHERE pr.status = 'PENDING' AND NULLIF(TRIM(pr.virtual_group_id), '') IS NOT NULL
  AND (vg.id IS NULL OR vg.id IN (SELECT id FROM removed) OR vg.id = 'vg-dev-public' OR vg.type <> 'CUSTOM')
ORDER BY pr.created_at;

-- I-15 Pending permission requests that cannot be granted after the switch: ROLE_ASSIGNMENT requests (the old direct
-- role assignment, whose grant no role resolution ever read, is removed with this release), requests naming
-- TEAM_LEAD or a BU_UNBOUNDED role (deleted by the switch), and the legacy FUNCTION / DATA / TEMPORARY requests (the
-- retired POST /permissions/renew made TEMPORARY ones; nothing makes them any more). Clear them before the switch; the
-- applicant applies for a BU + Role in the Portal instead:
--   - ROLE_ASSIGNMENT, FUNCTION, DATA and TEMPORARY have no approver: approving and rejecting both answer "You do not
--     have permission to approve this request" and leave it PENDING. Only the applicant can withdraw it (Portal
--     DELETE /permissions/requests/{id});
--   - a role request naming TEAM_LEAD / BU_UNBOUNDED: its BU approver rejects it, or the applicant withdraws it.
-- A request whose applicant can no longer sign in stays PENDING: P-07 lists it again after the switch, and it is
-- signed off there as an explained difference. Do not change the rows by hand.
SELECT 'I-15 pending requests that cannot be granted' AS item, pr.id, pr.request_type,
       COALESCE(u.username, pr.applicant_id) AS applicant, COALESCE(r.code, pr.role_id) AS role, pr.role_name,
       COALESCE(pr.business_unit_name, pr.organization_unit_name) AS business_unit, pr.created_at,
       concat_ws('; ',
           CASE WHEN pr.request_type = 'ROLE_ASSIGNMENT' THEN 'old direct role assignment, removed' END,
           CASE WHEN pr.request_type IN ('FUNCTION', 'DATA', 'TEMPORARY')
                THEN 'legacy ' || pr.request_type || ' request, no approver' END,
           CASE WHEN r.code = 'TEAM_LEAD' OR r.type = 'BU_UNBOUNDED' THEN 'role ' || r.code || ' is deleted by the switch' END) AS reason
FROM up_permission_request pr
LEFT JOIN sys_roles r ON r.id = pr.role_id
LEFT JOIN sys_users u ON u.id = pr.applicant_id
WHERE pr.status = 'PENDING'
  AND (pr.request_type IN ('ROLE_ASSIGNMENT', 'FUNCTION', 'DATA', 'TEMPORARY')
       OR r.code = 'TEAM_LEAD' OR r.type = 'BU_UNBOUNDED')
ORDER BY pr.created_at;

-- I-16 Actions whose "allowed roles" name TEAM_LEAD or a BU_UNBOUNDED role (codes in config_json.allowedRoles), in
-- the design (dw_action_definitions) and in published versions (sys_action_definitions). After the switch such a
-- code names no role, so it lets nobody use the action through it; the owning team changes the action and republishes.
WITH deleted_roles AS (
    SELECT id, code, type FROM sys_roles WHERE code = 'TEAM_LEAD' OR type = 'BU_UNBOUNDED'
), actions AS (
    SELECT 'design' AS stored_in, fu.code AS function_unit, NULL::varchar AS version, a.action_name, a.config_json
    FROM dw_action_definitions a JOIN dw_function_units fu ON fu.id = a.function_unit_id
    UNION ALL
    SELECT 'published', fu.code, fu.version, a.action_name, a.config_json
    FROM sys_action_definitions a JOIN sys_function_units fu ON fu.id = a.function_unit_id
)
SELECT 'I-16 action allowed roles naming a deleted role' AS item, a.stored_in, a.function_unit, a.version, a.action_name,
       string_agg(DISTINCT d.code || ' (' || d.type || ')', ', ') AS roles
FROM actions a
CROSS JOIN LATERAL jsonb_array_elements_text(
    CASE WHEN jsonb_typeof(a.config_json -> 'allowedRoles') = 'array' THEN a.config_json -> 'allowedRoles'
         ELSE '[]'::jsonb END) AS allowed(role)
JOIN deleted_roles d ON allowed.role IN (d.id, d.code)
GROUP BY a.stored_in, a.function_unit, a.version, a.action_name
ORDER BY a.function_unit, a.stored_in, a.version, a.action_name;

ROLLBACK;
