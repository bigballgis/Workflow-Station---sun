\set ON_ERROR_STOP on

-- =====================================================
-- Developer Workstation: Form table binding extra columns
-- =====================================================
-- Fixes runtime 500 when developer-workstation queries dw_form_table_bindings
-- with columns that are present in JPA entity but missing in init schema:
--   - sub_list_view_id
--   - sub_mode
--
-- Keep idempotent via IF NOT EXISTS.
-- =====================================================

ALTER TABLE dw_form_table_bindings
  ADD COLUMN IF NOT EXISTS sub_list_view_id BIGINT;

ALTER TABLE dw_form_table_bindings
  ADD COLUMN IF NOT EXISTS sub_mode VARCHAR(20);

CREATE INDEX IF NOT EXISTS idx_dw_form_table_bindings_sub_list_view
  ON dw_form_table_bindings(sub_list_view_id);

