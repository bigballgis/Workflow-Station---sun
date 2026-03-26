-- =====================================================
-- Add relation_table_id column to dw_form_table_bindings
-- For RELATED type bindings referencing rt_table_definitions
-- =====================================================

ALTER TABLE dw_form_table_bindings
    ALTER COLUMN table_id DROP NOT NULL;

ALTER TABLE dw_form_table_bindings
    ADD COLUMN IF NOT EXISTS relation_table_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_binding_relation_table ON dw_form_table_bindings(relation_table_id)
    WHERE relation_table_id IS NOT NULL;
