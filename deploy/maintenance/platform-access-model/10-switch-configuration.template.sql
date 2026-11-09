-- =====================================================
-- Platform access model: switch configuration of ONE environment (template)
-- docs/design/platform-access-model-requirements.md §10 step 3 (prepared ahead), run in step 4.3
-- =====================================================
-- Copy this file per environment (for example switch-configuration.uat.sql, kept with that environment's
-- operations records, not in init-scripts), fill the four lists below, rehearse it on a copy of the database
-- (UAT first), then run it in the downtime window after 00-schema/100, 90-post-seed/04 and 05, with the new
-- version's Admin started with LDAP_SYNC_ENABLED=false. Afterwards: 03-switch-assertions.sql ("configuration"
-- checks), turn the AD sync on and trigger it once, then 02-precheck-after-switch.sql (§10 steps 4.4, 4.5).
--
-- What it sets (each list is the desired state of the groups it names; groups it does not name stay as they are):
--   cfg_teams          teams by NAME: the code is generated from the name exactly as Admin does
--                      (VirtualGroupManagerComponent.generateCode) and must match the team's code in every other
--                      environment, so the name must be spelled the same everywhere (§5.4). A missing team is
--                      created; an existing one gets its Developer / Technical Lead AD groups (NULL = no mapping,
--                      the next sync then removes that list's AD rows, D-29). Public cannot be listed.
--   cfg_custom_groups  CUSTOM groups (Portal entry) by code with their AD group; at least one should map the AD
--                      group of the old Hermes Default Users, or most users cannot enter the Portal (§10 step 3).
--   cfg_system_groups  the AD groups of System Administrators and Auditors (§5.2).
--   cfg_members        manual members: administrators and managers who need the Portal go into a CUSTOM group
--                      (I-11, I-13 of the inventory), Technical Leads / Developers into a team list, extra
--                      System Administrators / Auditors into their SYSTEM group. Rows are MANUAL: the AD sync
--                      never removes them (§5.5).
-- Every name, code, AD group and username is checked first; any problem refuses the whole run with the full list,
-- and nothing changes. Running it again with the same lists changes nothing. Plain SQL in one transaction: psql or a
-- GUI client executing the whole file. Admin's operation audit does not see these changes; keep this file and its
-- output as the record.
--   psql -X -v ON_ERROR_STOP=1 -U <user> -d <db> -f switch-configuration.<env>.sql
-- =====================================================

BEGIN;

CREATE TEMP TABLE cfg_teams (
    name               TEXT NOT NULL,
    developer_ad_group TEXT,
    tech_lead_ad_group TEXT,
    description        TEXT
) ON COMMIT DROP;
-- INSERT INTO cfg_teams (name, developer_ad_group, tech_lead_ad_group, description) VALUES
--     ('Payment Ops', 'Payment-Ops-Developers', 'Payment-Ops-Technical-Leads', 'Payment operations team');

CREATE TEMP TABLE cfg_custom_groups (
    code     TEXT NOT NULL,
    name     TEXT NOT NULL,
    ad_group TEXT,
    description TEXT
) ON COMMIT DROP;
-- INSERT INTO cfg_custom_groups (code, name, ad_group, description) VALUES
--     ('PORTAL_USERS', 'Portal Users', 'Hermes-Default-Users', 'Everyone who uses the User Portal');

CREATE TEMP TABLE cfg_system_groups (
    code     TEXT NOT NULL CHECK (code IN ('SYSTEM_ADMINISTRATORS', 'AUDITORS')),
    ad_group TEXT
) ON COMMIT DROP;
-- INSERT INTO cfg_system_groups (code, ad_group) VALUES
--     ('SYSTEM_ADMINISTRATORS', 'Platform-Administrators'), ('AUDITORS', 'Platform-Auditors');

-- group_code: a SYSTEM / CUSTOM group's code, or a team's generated code (for 'Payment Ops': PAYMENT_OPS).
CREATE TEMP TABLE cfg_members (
    group_code TEXT NOT NULL,
    list_type  TEXT NOT NULL CHECK (list_type IN ('MEMBER', 'TECH_LEAD', 'DEVELOPER')),
    username   TEXT NOT NULL
) ON COMMIT DROP;
-- INSERT INTO cfg_members (group_code, list_type, username) VALUES
--     ('PORTAL_USERS', 'MEMBER', 'admin'),
--     ('PAYMENT_OPS', 'TECH_LEAD', 'alice');

DO $$
DECLARE
    v_problems TEXT;
    v_count    BIGINT;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_vg_member_list_source') THEN
        RAISE EXCEPTION 'The platform access model switch (90-post-seed/04) has not run on this database';
    END IF;

    CREATE TEMP TABLE cfg_team_codes ON COMMIT DROP AS
    SELECT t.*, regexp_replace(regexp_replace(regexp_replace(upper(TRIM(t.name)), '[^A-Z0-9]', '_', 'g'), '_+', '_', 'g'),
                               '^_|_$', '', 'g') AS code
    FROM cfg_teams t;

    -- 1. Check everything before changing anything. DISTINCT: a check that joins two lists (two team names giving one
    --    code, say) must not report the same problem once per row.
    SELECT string_agg(DISTINCT problem, '; ' ORDER BY problem) INTO v_problems FROM (
        SELECT 'team "' || name || '": the name gives an empty code, use letters or digits' AS problem
        FROM cfg_team_codes WHERE code = ''
        UNION ALL
        SELECT 'team "' || name || '": code ' || code || ' is longer than 50' FROM cfg_team_codes WHERE length(code) > 50
        UNION ALL
        SELECT 'teams ' || string_agg('"' || name || '"', ', ') || ' give the same code ' || code
        FROM cfg_team_codes WHERE code <> '' GROUP BY code HAVING count(*) > 1
        UNION ALL
        SELECT 'team "' || t.name || '": code ' || t.code || ' belongs to ' || vg.type || ' group "' || vg.name || '"'
        FROM cfg_team_codes t JOIN sys_virtual_groups vg ON vg.code = t.code
        WHERE vg.type <> 'DEVELOPER' OR vg.id = 'vg-dev-public'
        UNION ALL
        SELECT 'team "' || t.name || '": the existing team ' || t.code || ' is named "' || vg.name
               || '"; names must match across environments'
        FROM cfg_team_codes t JOIN sys_virtual_groups vg ON vg.code = t.code
        WHERE vg.type = 'DEVELOPER' AND vg.id <> 'vg-dev-public' AND vg.name <> TRIM(t.name)
        UNION ALL
        SELECT 'team "' || TRIM(t.name) || '": the name is already used by group ' || vg.code
        FROM cfg_team_codes t JOIN sys_virtual_groups vg ON vg.name = TRIM(t.name) AND vg.code <> t.code
        UNION ALL
        SELECT 'CUSTOM group "' || code || '": code must be 1 to 50 characters'
        FROM cfg_custom_groups WHERE TRIM(code) = '' OR length(TRIM(code)) > 50
        UNION ALL
        SELECT 'CUSTOM group ' || TRIM(c.code) || ': the code belongs to a ' || vg.type || ' group'
        FROM cfg_custom_groups c JOIN sys_virtual_groups vg ON vg.code = TRIM(c.code) WHERE vg.type <> 'CUSTOM'
        UNION ALL
        SELECT 'CUSTOM group ' || TRIM(c.code) || ' is also listed as a team code'
        FROM cfg_custom_groups c JOIN cfg_team_codes t ON t.code = TRIM(c.code)
        UNION ALL
        SELECT 'group ' || code || ' listed more than once' FROM (
            SELECT TRIM(code) AS code FROM cfg_custom_groups UNION ALL SELECT code FROM cfg_system_groups) g
        GROUP BY code HAVING count(*) > 1
        UNION ALL
        SELECT 'SYSTEM group ' || s.code || ' does not exist'
        FROM cfg_system_groups s WHERE NOT EXISTS (SELECT 1 FROM sys_virtual_groups vg WHERE vg.code = s.code AND vg.type = 'SYSTEM')
        UNION ALL
        SELECT 'member ' || m.username || ': no active account' FROM cfg_members m
        WHERE NOT EXISTS (SELECT 1 FROM sys_users u WHERE u.username = TRIM(m.username)
                          AND u.status = 'ACTIVE' AND COALESCE(u.deleted, false) = false)
        UNION ALL
        SELECT 'member ' || m.username || ': group ' || m.group_code || ' is neither listed above nor existing'
        FROM cfg_members m
        WHERE NOT EXISTS (SELECT 1 FROM sys_virtual_groups vg WHERE vg.code = TRIM(m.group_code))
          AND NOT EXISTS (SELECT 1 FROM cfg_team_codes t WHERE t.code = TRIM(m.group_code))
          AND NOT EXISTS (SELECT 1 FROM cfg_custom_groups c WHERE TRIM(c.code) = TRIM(m.group_code))
        UNION ALL
        SELECT 'member ' || m.username || ': ' || m.group_code || ' takes '
               || CASE WHEN g.type = 'DEVELOPER' THEN 'TECH_LEAD or DEVELOPER' ELSE 'MEMBER' END || ', not ' || m.list_type
        FROM cfg_members m
        JOIN (SELECT code, type FROM sys_virtual_groups
              UNION SELECT code, 'DEVELOPER' FROM cfg_team_codes
              UNION SELECT TRIM(code), 'CUSTOM' FROM cfg_custom_groups) g ON g.code = TRIM(m.group_code)
        WHERE (g.type = 'DEVELOPER' AND m.list_type = 'MEMBER') OR (g.type <> 'DEVELOPER' AND m.list_type <> 'MEMBER')
        UNION ALL
        SELECT 'member ' || m.username || ': Public has no lists' FROM cfg_members m WHERE TRIM(m.group_code) = 'DEV_TEAM_PUBLIC'
    ) p;
    IF v_problems IS NOT NULL THEN
        RAISE EXCEPTION 'Switch configuration refused, nothing changed: %', v_problems;
    END IF;

    -- 2. Teams.
    INSERT INTO sys_virtual_groups (id, code, name, display_name, type, status, ad_group, tech_lead_ad_group,
                                    created_at, created_by, updated_at, updated_by)
    SELECT gen_random_uuid()::text, t.code, TRIM(t.name), COALESCE(t.description, TRIM(t.name)), 'DEVELOPER', 'ACTIVE',
           NULLIF(TRIM(t.developer_ad_group), ''), NULLIF(TRIM(t.tech_lead_ad_group), ''),
           CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system'
    FROM cfg_team_codes t
    WHERE NOT EXISTS (SELECT 1 FROM sys_virtual_groups vg WHERE vg.code = t.code);
    GET DIAGNOSTICS v_count = ROW_COUNT;
    RAISE NOTICE 'Teams created: %', v_count;
    UPDATE sys_virtual_groups vg
    SET ad_group = NULLIF(TRIM(t.developer_ad_group), ''), tech_lead_ad_group = NULLIF(TRIM(t.tech_lead_ad_group), ''),
        updated_at = CURRENT_TIMESTAMP, updated_by = 'system'
    FROM cfg_team_codes t
    WHERE vg.code = t.code
      AND (vg.ad_group IS DISTINCT FROM NULLIF(TRIM(t.developer_ad_group), '')
           OR vg.tech_lead_ad_group IS DISTINCT FROM NULLIF(TRIM(t.tech_lead_ad_group), ''));
    GET DIAGNOSTICS v_count = ROW_COUNT;
    RAISE NOTICE 'Team AD mappings changed: %', v_count;

    -- 3. CUSTOM groups.
    INSERT INTO sys_virtual_groups (id, code, name, display_name, type, status, ad_group,
                                    created_at, created_by, updated_at, updated_by)
    SELECT gen_random_uuid()::text, TRIM(c.code), TRIM(c.name), COALESCE(c.description, TRIM(c.name)), 'CUSTOM', 'ACTIVE',
           NULLIF(TRIM(c.ad_group), ''), CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system'
    FROM cfg_custom_groups c
    WHERE NOT EXISTS (SELECT 1 FROM sys_virtual_groups vg WHERE vg.code = TRIM(c.code));
    GET DIAGNOSTICS v_count = ROW_COUNT;
    RAISE NOTICE 'CUSTOM groups created: %', v_count;
    UPDATE sys_virtual_groups vg
    SET ad_group = NULLIF(TRIM(c.ad_group), ''), updated_at = CURRENT_TIMESTAMP, updated_by = 'system'
    FROM cfg_custom_groups c
    WHERE vg.code = TRIM(c.code) AND vg.ad_group IS DISTINCT FROM NULLIF(TRIM(c.ad_group), '');
    GET DIAGNOSTICS v_count = ROW_COUNT;
    RAISE NOTICE 'CUSTOM AD mappings changed: %', v_count;

    -- 4. SYSTEM groups.
    UPDATE sys_virtual_groups vg
    SET ad_group = NULLIF(TRIM(s.ad_group), ''), updated_at = CURRENT_TIMESTAMP, updated_by = 'system'
    FROM cfg_system_groups s
    WHERE vg.code = s.code AND vg.type = 'SYSTEM' AND vg.ad_group IS DISTINCT FROM NULLIF(TRIM(s.ad_group), '');
    GET DIAGNOSTICS v_count = ROW_COUNT;
    RAISE NOTICE 'SYSTEM AD mappings changed: %', v_count;

    -- 5. Manual members.
    INSERT INTO sys_virtual_group_members (id, group_id, user_id, joined_at, added_by, list_type, source)
    SELECT gen_random_uuid()::text, vg.id, u.id, CURRENT_TIMESTAMP, 'system', m.list_type, 'MANUAL'
    FROM cfg_members m
    JOIN sys_virtual_groups vg ON vg.code = TRIM(m.group_code)
    JOIN sys_users u ON u.username = TRIM(m.username)
    ON CONFLICT (group_id, user_id, list_type, source) DO NOTHING;
    GET DIAGNOSTICS v_count = ROW_COUNT;
    RAISE NOTICE 'Manual members added: %', v_count;
END $$;

COMMIT;
