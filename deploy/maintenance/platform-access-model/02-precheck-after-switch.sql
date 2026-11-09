-- =====================================================
-- Platform access model: precheck under the new rules (read-only)
-- docs/design/platform-access-model-requirements.md §10 step 4.5
-- =====================================================
-- Run after the switch (90-post-seed/04), this environment's switch configuration (§10 step 3) and the first AD
-- sync (step 4.4). It reads who gets what under the new rules, the way the services compute it, so the System
-- Administrator can confirm "who loses what" matches 01-inventory-before-switch.sql before opening (step 4.5).
-- It needs the new schema (00-schema/100); 03-switch-assertions.sql checks the switch itself.
--
-- Read-only: one READ ONLY transaction, rolled back at the end. Plain SQL, runs in psql and in GUI clients.
--   psql -X -v ON_ERROR_STOP=1 -U <user> -d <db> -f 02-precheck-after-switch.sql > precheck.txt
--   docker exec -i platform-postgres-dev psql -X -U <user> -d <db> < 02-precheck-after-switch.sql
--
-- Items (compare with the inventory item in brackets):
--   P-01 users without Portal entry who used the platform: in a group or list, holding a BU + Role, or signed in
--        before (§7.1) [I-01]
--   P-02 SYSTEM group members and team list rows, and whether each gives its role (§4.1, §5.5) [I-02, I-03]
--   P-03 groups and teams: AD mappings and members per list and source (§5.4, §5.6) [I-04, I-05]
--   P-04 users without an Entity / Function Manager, or whose two managers have no Portal entry (§7.3) [I-11]
--   P-05 published views nobody can see (§7.2, D-05) [I-12]
--   P-06 test accounts and their Portal entry [I-13]
--   P-07 pending permission requests that cannot be approved: group missing or not CUSTOM, role missing, or a type
--        without approver (the removed ROLE_ASSIGNMENT, the legacy FUNCTION / DATA / TEMPORARY) [I-14, I-15]
--   P-08 BU_UNBOUNDED roles still there; 90-post-seed/05 leaves none (D-64) [I-08]
--   P-09 published process tasks assigned the old ways, or naming a role that does not exist (such as TEAM_LEAD)
--        or a BU_UNBOUNDED role [I-09, I-10]
--   P-10 actions whose allowed roles name a role that does not exist [I-16]
-- =====================================================

START TRANSACTION READ ONLY;

-- P-01 Users without Portal entry (PortalEntryService: ACTIVE, not deleted, member of an ACTIVE CUSTOM group),
-- among those who used the platform. Expected: the I-01 list minus the people the switch configuration brought back.
-- Someone the switch dropped from every group (an I-03 "not moved" member) who holds no BU + Role and never signed
-- in leaves no trace to find here; I-03 is the record of them.
WITH entry AS (
    SELECT DISTINCT m.user_id
    FROM sys_virtual_group_members m
    JOIN sys_virtual_groups g ON g.id = m.group_id AND g.type = 'CUSTOM' AND g.status = 'ACTIVE'
    WHERE m.list_type = 'MEMBER'
)
SELECT 'P-01 no Portal entry' AS item, count(*) OVER () AS total, u.username, u.display_name, u.employee_id,
       (SELECT string_agg(vg.code || ' ' || m.list_type || ' ' || m.source || ' (' || vg.type || ', ' || vg.status || ')',
                          ', ' ORDER BY vg.code, m.list_type)
        FROM sys_virtual_group_members m JOIN sys_virtual_groups vg ON vg.id = m.group_id
        WHERE m.user_id = u.id) AS groups,
       (SELECT count(*) FROM sys_user_business_unit_roles ubr WHERE ubr.user_id = u.id) AS ubr_count,
       u.last_login_at
FROM sys_users u
WHERE u.status = 'ACTIVE' AND COALESCE(u.deleted, false) = false
  AND u.id NOT IN (SELECT user_id FROM entry)
  AND (EXISTS (SELECT 1 FROM sys_virtual_group_members m WHERE m.user_id = u.id)
       OR EXISTS (SELECT 1 FROM sys_user_business_unit_roles ubr WHERE ubr.user_id = u.id)
       OR u.last_login_at IS NOT NULL)
ORDER BY u.last_login_at DESC NULLS LAST, u.username;

-- P-02 Platform roles as UserRoleServiceImpl resolves them: SYS_ADMIN / AUDITOR from the fixed binding of an ACTIVE
-- SYSTEM group, TECH_LEAD / DEVELOPER from the list of an ACTIVE team; the account must be ACTIVE and not deleted.
-- One row per list row; "gives_role = false" rows explain who holds a row but gets nothing from it.
SELECT 'P-02 platform role sources' AS item, vg.type AS group_type, vg.code AS group_code, vg.status AS group_status,
       m.list_type, m.source, COALESCE(u.username, m.user_id) AS member, u.status AS user_status,
       CASE WHEN vg.type = 'SYSTEM' THEN (SELECT string_agg(r.code, ', ') FROM sys_role_assignments ra
                                          JOIN sys_roles r ON r.id = ra.role_id
                                          WHERE ra.target_type = 'VIRTUAL_GROUP' AND ra.target_id = vg.id)
            ELSE m.list_type END AS role,
       (u.status = 'ACTIVE' AND NOT COALESCE(u.deleted, false) AND vg.status = 'ACTIVE'
        AND ((vg.type = 'SYSTEM' AND m.list_type = 'MEMBER')
             OR (vg.type = 'DEVELOPER' AND m.list_type IN ('TECH_LEAD', 'DEVELOPER')))) AS gives_role
FROM sys_virtual_group_members m
JOIN sys_virtual_groups vg ON vg.id = m.group_id AND vg.type IN ('SYSTEM', 'DEVELOPER')
LEFT JOIN sys_users u ON u.id = m.user_id
ORDER BY vg.type DESC, vg.code, m.list_type DESC, member, m.source;

-- P-03 Groups and teams with their AD mappings and members per list and source. Notes: a team without a Technical
-- Lead (only a System Administrator can create its function units, §5.4), a group nobody is in, an ACTIVE group
-- without AD mapping whose AD rows the next sync removes (D-29), and whether any CUSTOM group maps an AD group (at
-- least one should map the AD group of the old Hermes Default Users, §10 step 3).
SELECT 'P-03 groups' AS item, vg.type, vg.code, vg.name, vg.status, vg.ad_group, vg.tech_lead_ad_group,
       count(m.id) FILTER (WHERE m.list_type = 'MEMBER' AND m.source = 'AD') AS member_ad,
       count(m.id) FILTER (WHERE m.list_type = 'MEMBER' AND m.source = 'MANUAL') AS member_manual,
       count(m.id) FILTER (WHERE m.list_type = 'TECH_LEAD' AND m.source = 'AD') AS tech_lead_ad,
       count(m.id) FILTER (WHERE m.list_type = 'TECH_LEAD' AND m.source = 'MANUAL') AS tech_lead_manual,
       count(m.id) FILTER (WHERE m.list_type = 'DEVELOPER' AND m.source = 'AD') AS developer_ad,
       count(m.id) FILTER (WHERE m.list_type = 'DEVELOPER' AND m.source = 'MANUAL') AS developer_manual,
       concat_ws('; ',
           CASE WHEN vg.type = 'DEVELOPER' AND vg.id <> 'vg-dev-public'
                     AND count(m.id) FILTER (WHERE m.list_type = 'TECH_LEAD') = 0
                     AND NULLIF(TRIM(vg.tech_lead_ad_group), '') IS NULL THEN 'no Technical Lead' END,
           CASE WHEN count(m.id) = 0 AND vg.id <> 'vg-dev-public' THEN 'nobody in it' END,
           CASE WHEN vg.status = 'ACTIVE' AND NULLIF(TRIM(vg.ad_group), '') IS NULL
                     AND count(m.id) FILTER (WHERE m.source = 'AD' AND m.list_type <> 'TECH_LEAD') > 0
                THEN 'AD rows but no AD group: the next sync removes them' END,
           CASE WHEN vg.type = 'DEVELOPER' AND NULLIF(TRIM(vg.tech_lead_ad_group), '') IS NULL
                     AND count(m.id) FILTER (WHERE m.source = 'AD' AND m.list_type = 'TECH_LEAD') > 0
                THEN 'AD Technical Lead rows but no Technical Lead AD group' END,
           CASE WHEN vg.type = 'CUSTOM' AND NOT EXISTS (
                    SELECT 1 FROM sys_virtual_groups c
                    WHERE c.type = 'CUSTOM' AND c.status = 'ACTIVE' AND NULLIF(TRIM(c.ad_group), '') IS NOT NULL)
                THEN 'no ACTIVE CUSTOM group maps an AD group' END) AS notes
FROM sys_virtual_groups vg
LEFT JOIN sys_virtual_group_members m ON m.group_id = vg.id
GROUP BY vg.id, vg.type, vg.code, vg.name, vg.status, vg.ad_group, vg.tech_lead_ad_group
ORDER BY CASE vg.type WHEN 'SYSTEM' THEN 1 WHEN 'DEVELOPER' THEN 2 ELSE 3 END, vg.code;

-- P-04 Managers (§7.3): a manager is available with an ACTIVE account and Portal entry. Same people as I-11.
WITH entry AS (
    SELECT DISTINCT m.user_id
    FROM sys_virtual_group_members m
    JOIN sys_virtual_groups g ON g.id = m.group_id AND g.type = 'CUSTOM' AND g.status = 'ACTIVE'
    WHERE m.list_type = 'MEMBER'
), people AS (
    SELECT u.* FROM sys_users u
    WHERE u.status = 'ACTIVE' AND COALESCE(u.deleted, false) = false
      AND (u.id IN (SELECT user_id FROM entry)
           OR EXISTS (SELECT 1 FROM sys_user_business_unit_roles ubr WHERE ubr.user_id = u.id))
), checked AS (
    SELECT p.username, p.display_name, p.entity_manager_id, p.function_manager_id,
           em.username AS entity_manager, fm.username AS function_manager,
           CASE WHEN p.entity_manager_id IS NULL THEN 'none'
                WHEN em.id IS NULL THEN 'id not found'
                WHEN em.status <> 'ACTIVE' OR COALESCE(em.deleted, false) THEN 'account ' || CASE WHEN em.deleted THEN 'deleted' ELSE em.status END
                WHEN em.id NOT IN (SELECT user_id FROM entry) THEN 'no Portal entry'
                ELSE 'available' END AS entity_manager_state,
           CASE WHEN p.function_manager_id IS NULL THEN 'none'
                WHEN fm.id IS NULL THEN 'id not found'
                WHEN fm.status <> 'ACTIVE' OR COALESCE(fm.deleted, false) THEN 'account ' || CASE WHEN fm.deleted THEN 'deleted' ELSE fm.status END
                WHEN fm.id NOT IN (SELECT user_id FROM entry) THEN 'no Portal entry'
                ELSE 'available' END AS function_manager_state
    FROM people p
    LEFT JOIN sys_users em ON em.id = p.entity_manager_id
    LEFT JOIN sys_users fm ON fm.id = p.function_manager_id
)
SELECT 'P-04 managers' AS item, count(*) OVER () AS total, username, display_name,
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

-- P-05 Published views nobody can see; same rule and columns as I-12 (team = the function unit's one workspace).
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
SELECT 'P-05 published views nobody can see' AS item, code, function_unit, team, enabled_in_admin, view_id, view_name,
       is_default, bu_rules, bu_found, role_rules, role_found,
       CASE WHEN bu_rules + role_rules = 0 THEN 'empty: nobody sees it'
            WHEN bu_rules = 0 OR role_rules = 0 THEN 'unpaired: BU and Role must both be set'
            WHEN bu_found = 0 OR role_found = 0 THEN 'no rule BU or no rule Role exists here: nobody sees it'
            ELSE 'some ids missing: visible to the pairs that exist' END AS verdict,
       missing_ids
FROM per_view
WHERE bu_rules + role_rules = 0 OR bu_found = 0 OR role_found = 0 OR bu_found < bu_rules OR role_found < role_rules
ORDER BY code, view_id;

-- P-06 Test accounts of regression scripts and E2E (the I-13 list; edit per environment).
WITH accounts(username) AS (
    VALUES ('developer'), ('admin'), ('44027893'), ('12345'), ('123456'),
           ('e2e_zhangwei'), ('e2e_lina'), ('e2e_wangfang'), ('e2e_zhaomin'), ('e2e_sunqiang'), ('e2e_zhoujie'),
           ('e2e_wugang'), ('view_allowed'), ('view_wrong_bu'), ('view_wrong_role'), ('view_nofu')
), entry AS (
    SELECT m.user_id, string_agg(g.code, ', ' ORDER BY g.code) AS custom_groups
    FROM sys_virtual_group_members m
    JOIN sys_virtual_groups g ON g.id = m.group_id AND g.type = 'CUSTOM' AND g.status = 'ACTIVE'
    WHERE m.list_type = 'MEMBER'
    GROUP BY m.user_id
)
SELECT 'P-06 test accounts' AS item, a.username, u.status,
       CASE WHEN u.id IS NULL THEN 'no such account'
            WHEN u.status <> 'ACTIVE' OR COALESCE(u.deleted, false) THEN 'account not active'
            WHEN e.user_id IS NULL THEN 'NO Portal entry'
            ELSE 'Portal entry' END AS portal,
       e.custom_groups,
       (SELECT string_agg(vg.code || ' ' || m.list_type, ', ' ORDER BY vg.code) FROM sys_virtual_group_members m
        JOIN sys_virtual_groups vg ON vg.id = m.group_id WHERE m.user_id = u.id) AS all_lists
FROM accounts a
LEFT JOIN sys_users u ON u.username = a.username
LEFT JOIN entry e ON e.user_id = u.id
ORDER BY (e.user_id IS NULL AND u.id IS NOT NULL) DESC, a.username;

-- P-07 Pending permission requests that cannot be approved: their group is missing or not a CUSTOM group, their role
-- no longer exists, or they are of a type without approver (the removed ROLE_ASSIGNMENT, the legacy FUNCTION / DATA /
-- TEMPORARY). Expected empty when the I-14 / I-15 requests were cleared before the switch. A type without approver
-- answers "no permission" to approve and reject; only its applicant can withdraw it. A row whose applicant can no
-- longer sign in stays: record it as an explained difference in the sign-off, do not change it by hand.
SELECT 'P-07 pending requests that cannot be approved' AS item, pr.id, pr.request_type,
       COALESCE(u.username, pr.applicant_id) AS applicant, COALESCE(vg.code, pr.virtual_group_id) AS group_ref,
       COALESCE(pr.role_name, pr.role_id) AS role, pr.created_at,
       concat_ws('; ',
           CASE WHEN NULLIF(TRIM(pr.virtual_group_id), '') IS NOT NULL AND vg.id IS NULL THEN 'group no longer exists' END,
           CASE WHEN vg.id IS NOT NULL AND vg.type <> 'CUSTOM' THEN 'not a CUSTOM group (' || vg.type || ')' END,
           CASE WHEN NULLIF(TRIM(pr.role_id), '') IS NOT NULL AND r.id IS NULL THEN 'role no longer exists' END,
           CASE WHEN pr.request_type = 'ROLE_ASSIGNMENT' THEN 'old direct role assignment, removed' END,
           CASE WHEN pr.request_type IN ('FUNCTION', 'DATA', 'TEMPORARY')
                THEN 'legacy ' || pr.request_type || ' request, no approver' END) AS reason
FROM up_permission_request pr
LEFT JOIN sys_virtual_groups vg ON vg.id = pr.virtual_group_id
LEFT JOIN sys_roles r ON r.id = pr.role_id
LEFT JOIN sys_users u ON u.id = pr.applicant_id
WHERE pr.status = 'PENDING'
  AND ((NULLIF(TRIM(pr.virtual_group_id), '') IS NOT NULL AND (vg.id IS NULL OR vg.type <> 'CUSTOM'))
       OR (NULLIF(TRIM(pr.role_id), '') IS NOT NULL AND r.id IS NULL)
       OR pr.request_type IN ('ROLE_ASSIGNMENT', 'FUNCTION', 'DATA', 'TEMPORARY'))
ORDER BY pr.created_at;

-- P-08 BU_UNBOUNDED roles left: expected none (90-post-seed/05 deletes them, D-64).
SELECT 'P-08 BU_UNBOUNDED roles left' AS item, r.code, r.name, r.status,
       (SELECT count(*) FROM sys_user_business_unit_roles x WHERE x.role_id = r.id) AS ubr,
       (SELECT count(*) FROM sys_business_unit_roles x WHERE x.role_id = r.id) AS bu_bindings,
       (SELECT count(*) FROM sys_function_unit_access x WHERE x.target_type = 'ROLE' AND x.target_id IN (r.id, r.code))
         + (SELECT count(*) FROM sys_function_unit_audit_access x WHERE x.target_type = 'ROLE' AND x.target_id IN (r.id, r.code))
         + (SELECT count(*) FROM dw_function_unit_access x WHERE x.target_type = 'ROLE' AND x.target_id IN (r.id, r.code)) AS fu_access_rules,
       (SELECT count(*) FROM dw_main_table_view_access x WHERE x.target_type = 'ROLE' AND x.target_id IN (r.id, r.code)) AS view_rules,
       (SELECT count(*) FROM rt_table_access x WHERE x.target_type = 'ROLE' AND x.target_id IN (r.id, r.code)) AS relation_table_rules
FROM sys_roles r
WHERE r.type = 'BU_UNBOUNDED'
ORDER BY r.code;

-- P-09 Published process tasks to look at (I-09 rules): old assignment types, and roleId / roleIds naming a role
-- that does not exist here (TEAM_LEAD after the switch, or a role deleted by the switch configuration) or a
-- BU_UNBOUNDED role. Such a task assigns nobody; the owning team fixes and republishes it.
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
               (SELECT 'names a role that does not exist: ' || string_agg(DISTINCT ref, ', ')
                FROM regexp_split_to_table(a.role_refs, '\s*,\s*') AS ref
                WHERE ref <> '' AND NOT EXISTS (SELECT 1 FROM sys_roles r WHERE ref IN (r.id, r.code))),
               (SELECT 'names ' || string_agg(DISTINCT r.code || ' (BU_UNBOUNDED)', ', ')
                FROM regexp_split_to_table(a.role_refs, '\s*,\s*') AS ref
                JOIN sys_roles r ON ref IN (r.id, r.code)
                WHERE r.type = 'BU_UNBOUNDED')) AS reasons
    FROM assignments a
)
SELECT 'P-09 published process tasks' AS item, code, name, version, latest_version, task_id, task_name,
       assignee_type, assignee_value, role_refs, reasons
FROM flagged
WHERE reasons <> ''
ORDER BY code, latest_version DESC, version, task_id;

-- P-10 Actions whose allowed roles (config_json.allowedRoles) name a role code or id that does not exist here, in the
-- design and in published versions: that entry lets nobody use the action. The owning team fixes and republishes.
WITH actions AS (
    SELECT 'design' AS stored_in, fu.code AS function_unit, NULL::varchar AS version, a.action_name, a.config_json
    FROM dw_action_definitions a JOIN dw_function_units fu ON fu.id = a.function_unit_id
    UNION ALL
    SELECT 'published', fu.code, fu.version, a.action_name, a.config_json
    FROM sys_action_definitions a JOIN sys_function_units fu ON fu.id = a.function_unit_id
)
SELECT 'P-10 action allowed roles naming a missing role' AS item, a.stored_in, a.function_unit, a.version, a.action_name,
       string_agg(DISTINCT allowed.role, ', ') AS missing_roles
FROM actions a
CROSS JOIN LATERAL jsonb_array_elements_text(
    CASE WHEN jsonb_typeof(a.config_json -> 'allowedRoles') = 'array' THEN a.config_json -> 'allowedRoles'
         ELSE '[]'::jsonb END) AS allowed(role)
WHERE allowed.role <> '' AND NOT EXISTS (SELECT 1 FROM sys_roles r WHERE allowed.role IN (r.id, r.code))
GROUP BY a.stored_in, a.function_unit, a.version, a.action_name
ORDER BY a.function_unit, a.stored_in, a.version, a.action_name;

ROLLBACK;
