\set ON_ERROR_STOP on

-- =====================================================
-- Relation Tables: Computed (formula) field metadata
-- =====================================================
-- Relation table counterpart of 65-add-dw-field-computed.sql. Same column shape and same
-- computed_field_json contract, so one validator and one evaluator serve both table kinds.
--
-- computed_field_json shape:
--   {
--     "version":   1,
--     "scope":     "row" | "aggregate",
--     "source":    "quantity * unit_price",
--     "ast":       { ... },        -- the ONLY evaluation authority; source is for editor redisplay
--     "dependsOn": ["quantity", "unit_price"],
--     "onError":   "fail" | "null"
--   }
--
-- Result type is NOT stored here: data_type / precision_value / scale on the same row
-- remain the single source of truth, and the backend validator asserts the expression's
-- inferred type is compatible with them.
--
-- Keep idempotent via ADD COLUMN IF NOT EXISTS.
-- =====================================================

ALTER TABLE rt_field_definitions
    ADD COLUMN IF NOT EXISTS is_computed         BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS computed_field_json JSONB;

COMMENT ON COLUMN rt_field_definitions.is_computed IS
    'Whether this column is derived from a formula instead of user input (read-only in forms)';
COMMENT ON COLUMN rt_field_definitions.computed_field_json IS
    'Computed field definition: version, scope (row|aggregate), source text, validated AST, dependsOn, onError';

-- Partial index, same purpose as idx_dw_field_definitions_computed: every relation-table write
-- asks "does this deployment have any computed field at all?" before doing anything else, and
-- until the first one is designed that question must not scan the table.
CREATE INDEX IF NOT EXISTS idx_rt_field_computed
    ON rt_field_definitions(table_id) WHERE is_computed = TRUE;
