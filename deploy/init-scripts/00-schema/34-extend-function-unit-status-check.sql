-- Extend sys_function_units.status CHECK to support ARCHIVED (soft delete)
-- Note: INIT was removed in 35-drop-init-function-unit-status.sql; use DRAFT for import/restore.
ALTER TABLE sys_function_units DROP CONSTRAINT IF EXISTS chk_func_unit_status;
ALTER TABLE sys_function_units ADD CONSTRAINT chk_func_unit_status
    CHECK (status IN ('DRAFT', 'VALIDATED', 'DEPLOYED', 'DEPRECATED', 'ARCHIVED'));

COMMENT ON COLUMN sys_function_units.status IS 'Lifecycle: DRAFT (imported/restored), VALIDATED, DEPLOYED, DEPRECATED, ARCHIVED (removed from portal)';
