\set ON_ERROR_STOP on

-- =====================================================
-- Per-FK fill source on form table bindings (F2)
-- =====================================================
-- Declares where each structural foreign key on a SUB binding takes its
-- parent value from: PARENT (host row), PRIMARY (main form), or ANCESTOR
-- (another binding on this form). Stored as JSON so each FK is independent
-- of the binding's filter_fk_field_id.
--
-- fieldId is dw_field_definitions.id (not the column name). ancestorBindingId
-- is another dw_form_table_bindings.id on the same form; ZIP export rewrites
-- both to portable names. NULL / missing column = undeclared, runtime keeps
-- unique-table fallback from context frames.
-- =====================================================

ALTER TABLE dw_form_table_bindings
  ADD COLUMN IF NOT EXISTS fk_fill_sources JSONB;

COMMENT ON COLUMN dw_form_table_bindings.fk_fill_sources IS
  'JSON array of {fieldId, kind, ancestorBindingId}. kind is PARENT, PRIMARY, or ANCESTOR. NULL = not declared.';
