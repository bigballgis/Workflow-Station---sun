-- V208: Persist display name at deploy time for Table Data (undeployed draft must not leak)

ALTER TABLE rt_table_definitions
    ADD COLUMN IF NOT EXISTS deployed_display_name VARCHAR(200);

UPDATE rt_table_definitions
SET deployed_display_name = display_name
WHERE current_version > 0
  AND status = 'DEPLOYED'
  AND deployed_display_name IS NULL
  AND display_name IS NOT NULL;

COMMENT ON COLUMN rt_table_definitions.deployed_display_name IS
    'Display name captured on last deploy; Table Data uses this while status is UPDATED/ROLLBACK';
