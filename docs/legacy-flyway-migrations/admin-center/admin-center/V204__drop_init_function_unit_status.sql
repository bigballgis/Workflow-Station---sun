-- Consolidate INIT into DRAFT for function unit lifecycle
UPDATE sys_function_units SET status = 'DRAFT' WHERE status = 'INIT';

ALTER TABLE sys_function_units DROP CONSTRAINT IF EXISTS chk_func_unit_status;
ALTER TABLE sys_function_units ADD CONSTRAINT chk_func_unit_status
    CHECK (status IN ('DRAFT', 'VALIDATED', 'DEPLOYED', 'DEPRECATED', 'ARCHIVED'));
