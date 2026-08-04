-- Allow virtual group type DEVELOPER alongside SYSTEM and CUSTOM.
-- Existing rows keep their type; no data migration.

ALTER TABLE sys_virtual_groups DROP CONSTRAINT IF EXISTS chk_virtual_group_type;

ALTER TABLE sys_virtual_groups
    ADD CONSTRAINT chk_virtual_group_type
    CHECK (type IN ('SYSTEM', 'CUSTOM', 'DEVELOPER'));

COMMENT ON COLUMN sys_virtual_groups.type IS
    'SYSTEM (built-in, non-deletable), CUSTOM (business/task-pool), DEVELOPER (DW team)';
