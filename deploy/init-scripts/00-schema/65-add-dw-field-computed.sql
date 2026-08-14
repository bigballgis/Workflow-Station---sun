\set ON_ERROR_STOP on

-- =====================================================
-- Developer Workstation: Computed (formula) field metadata
-- =====================================================
-- Column-level calculated fields (counterpart of Power Platform formula columns).
-- The definition lives on the field, so every form, main-table view, CSV export and
-- BPMN gateway observes the same value.
--
-- computed_field_json shape:
--   {
--     "version":   1,
--     "scope":     "row" | "aggregate",
--     "source":    "SUM(request_items.amount) * (1 + tax_rate)",
--     "ast":       { ... },        -- the ONLY evaluation authority; source is for editor redisplay
--     "dependsOn": ["tax_rate", "request_items.amount"],
--     "onError":   "fail" | "null"
--   }
--
-- Result type is NOT stored here: data_type / precision_value / scale on the same row
-- remain the single source of truth, and the backend validator asserts the expression's
-- inferred type is compatible with them.
--
-- Keep idempotent via ADD COLUMN IF NOT EXISTS.
-- =====================================================

ALTER TABLE dw_field_definitions
    ADD COLUMN IF NOT EXISTS is_computed         BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS computed_field_json JSONB;

COMMENT ON COLUMN dw_field_definitions.is_computed IS
    'Whether this column is derived from a formula instead of user input (read-only in forms)';
COMMENT ON COLUMN dw_field_definitions.computed_field_json IS
    'Computed field definition: version, scope (row|aggregate), source text, validated AST, dependsOn, onError';

-- Partial index, same shape as idx_dw_field_definitions_fk_ref above.
-- Every portal write asks "does this deployment have any computed field at all?" before doing
-- anything else; until the first one is designed that question must not scan the table.
CREATE INDEX IF NOT EXISTS idx_dw_field_definitions_computed
    ON dw_field_definitions(table_id) WHERE is_computed = TRUE;
