-- Allow Relation Tables to be optionally grouped under a Function Unit,
-- so admin-center Table Structure / Table Data and user-portal Relation Tables
-- can group/filter the (growing) flat table list by Function Unit.
-- NULL = ungrouped; deleting the Function Unit does not delete the relation table.
ALTER TABLE rt_table_definitions ADD COLUMN IF NOT EXISTS function_unit_id VARCHAR(64);

ALTER TABLE rt_table_definitions DROP CONSTRAINT IF EXISTS fk_rt_table_function_unit;
ALTER TABLE rt_table_definitions ADD CONSTRAINT fk_rt_table_function_unit
    FOREIGN KEY (function_unit_id) REFERENCES sys_function_units(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_rt_table_function_unit ON rt_table_definitions(function_unit_id);

COMMENT ON COLUMN rt_table_definitions.function_unit_id IS 'Optional Function Unit grouping (sys_function_units.id); NULL = ungrouped';
