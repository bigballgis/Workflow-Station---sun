-- Allow ACTION-type tables to be bound to forms (dw_form_table_bindings.binding_type),
-- so FORM_POPUP action forms (e.g. Meeting Remark) can bind their own dw_table_definitions
-- row (table_type='ACTION') the same way SUB tables bind to PROCESS/TASK forms.
ALTER TABLE dw_form_table_bindings DROP CONSTRAINT IF EXISTS chk_binding_type;
ALTER TABLE dw_form_table_bindings ADD CONSTRAINT chk_binding_type
    CHECK (binding_type IN ('PRIMARY', 'SUB', 'RELATED', 'ACTION'));
