-- Extend sys_function_units.status CHECK to support INIT (import/restore) and ARCHIVED (soft delete)
ALTER TABLE sys_function_units DROP CONSTRAINT IF EXISTS chk_func_unit_status;
ALTER TABLE sys_function_units ADD CONSTRAINT chk_func_unit_status
    CHECK (status IN ('DRAFT', 'INIT', 'VALIDATED', 'DEPLOYED', 'DEPRECATED', 'ARCHIVED'));
