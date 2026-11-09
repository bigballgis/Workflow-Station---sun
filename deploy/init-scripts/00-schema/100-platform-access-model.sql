-- Platform access model (docs/design/platform-access-model-requirements.md), schema part.
-- Columns only; the data switch that needs them is 90-post-seed/04-platform-access-model-switch.sql.
--
-- sys_virtual_group_members gets the list a row belongs to and where it came from:
--   list_type  MEMBER (SYSTEM / CUSTOM groups), DEVELOPER or TECH_LEAD (a team's two lists; the value
--              is the role code the list grants).
--   source     AD (written by the AD sync, added_by = HERMES_AD_SYNC) or MANUAL.
-- The defaults let the seed scripts that run after this one keep inserting plain members. The unique key
-- moves from (group_id, user_id) to (group_id, user_id, list_type, source) in the switch script, because
-- those seed scripts still say ON CONFLICT (group_id, user_id).
--
-- Idempotent: safe to re-run.
-- NOTE for existing environments: init-scripts only run on FIRST container start. Apply manually:
--   docker exec -i platform-postgres-dev psql -U <user> -d <db> \
--     -f /docker-entrypoint-initdb.d/00-schema/100-platform-access-model.sql

ALTER TABLE sys_virtual_group_members ADD COLUMN IF NOT EXISTS list_type VARCHAR(20) NOT NULL DEFAULT 'MEMBER';
ALTER TABLE sys_virtual_group_members ADD COLUMN IF NOT EXISTS source VARCHAR(20) NOT NULL DEFAULT 'MANUAL';

UPDATE sys_virtual_group_members SET source = 'AD' WHERE added_by = 'HERMES_AD_SYNC' AND source <> 'AD';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_vg_member_list_type') THEN
        ALTER TABLE sys_virtual_group_members
            ADD CONSTRAINT chk_vg_member_list_type CHECK (list_type IN ('MEMBER', 'DEVELOPER', 'TECH_LEAD'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_vg_member_source') THEN
        ALTER TABLE sys_virtual_group_members
            ADD CONSTRAINT chk_vg_member_source CHECK (source IN ('AD', 'MANUAL'));
    END IF;
END $$;

COMMENT ON COLUMN sys_virtual_group_members.list_type IS
    'MEMBER for SYSTEM / CUSTOM groups; DEVELOPER or TECH_LEAD for the two lists of a team (DEVELOPER group)';
COMMENT ON COLUMN sys_virtual_group_members.source IS
    'AD (AD sync) or MANUAL; one user may hold both rows in the same list';

-- A team maps two AD groups: ad_group feeds its Developer list, tech_lead_ad_group its Technical Lead list.
-- SYSTEM and CUSTOM groups use ad_group only.
ALTER TABLE sys_virtual_groups ADD COLUMN IF NOT EXISTS tech_lead_ad_group VARCHAR(100);
COMMENT ON COLUMN sys_virtual_groups.ad_group IS
    'AD group synced into the members (SYSTEM / CUSTOM) or into the Developer list (DEVELOPER team)';
COMMENT ON COLUMN sys_virtual_groups.tech_lead_ad_group IS
    'DEVELOPER teams only: AD group synced into the Technical Lead list';

-- A role is created with an explicit type; writing one without it must fail instead of becoming BU_UNBOUNDED.
ALTER TABLE sys_roles ALTER COLUMN type DROP DEFAULT;

-- Team (DEVELOPER group code) that owns the function unit, written on the imported version row.
ALTER TABLE sys_function_units ADD COLUMN IF NOT EXISTS team_code VARCHAR(50);
COMMENT ON COLUMN sys_function_units.team_code IS
    'Code of the owning team (DEVELOPER virtual group); the latest imported version row of a code is authoritative';

-- Why an assignment changed (platform access model D-40, D-42, D-59): a claimed BU + Role pool task goes back to the
-- pool once its holder no longer holds that BU + Role, the tasks of someone who left go to their manager, and a
-- single-task delegation ends when the task changes hands or its hold ends; the change history must say so.
ALTER TABLE up_change_history ADD COLUMN IF NOT EXISTS reason VARCHAR(40);
COMMENT ON COLUMN up_change_history.reason IS
    'POOL_MEMBERSHIP_LOST (system: holder no longer holds the pool''s BU + Role), ASSIGNEE_DEPARTED (system: holder left; task handed to their manager), HOLDER_CHANGED (DELEGATION_ENDED row, by the user or the system: the task changed hands or its hold ended); null otherwise';

-- A group's task history outlives the group (WP19, out-of-scope list row 26). The switch deletes the old SYSTEM
-- groups, and fk_vg_task_group does not cascade, so deleting such a group used to mean deleting its history first.
-- The history row now keeps the group's code and name and drops the reference instead.
ALTER TABLE sys_virtual_group_task_history ALTER COLUMN group_id DROP NOT NULL;
ALTER TABLE sys_virtual_group_task_history ADD COLUMN IF NOT EXISTS deleted_group_code VARCHAR(50);
ALTER TABLE sys_virtual_group_task_history ADD COLUMN IF NOT EXISTS deleted_group_name VARCHAR(100);
COMMENT ON COLUMN sys_virtual_group_task_history.deleted_group_code IS
    'Code of the group when it was deleted (group_id is then null); null while the group exists';
COMMENT ON COLUMN sys_virtual_group_task_history.deleted_group_name IS
    'Name of the group when it was deleted (group_id is then null); null while the group exists';
