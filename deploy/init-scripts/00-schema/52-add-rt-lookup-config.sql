\set ON_ERROR_STOP on

-- =====================================================
-- Relation Tables: Add per-column LOOKUP field config
-- =====================================================
-- Adds a JSONB `lookup_config` column to rt_field_definitions so a Relation
-- Table column can be typed as LOOKUP (references another Relation Table).
-- The config carries: refTableId, searchFields, displayFields,
-- selectedDisplayField, filterConditions, showBackfillView, multiple, and the
-- derivedFrom block (parentField + join columns) that drives derived
-- auto-fill / cascade filtering between two lookup columns.
--
-- Keep idempotent via ADD COLUMN IF NOT EXISTS.
-- =====================================================

ALTER TABLE rt_field_definitions
    ADD COLUMN IF NOT EXISTS lookup_config JSONB;

COMMENT ON COLUMN rt_field_definitions.lookup_config IS
    'LOOKUP field configuration (JSONB): refTableId, searchFields, displayFields, '
    'selectedDisplayField, filterConditions, showBackfillView, multiple, derivedFrom '
    '{parentField, joins[{fromColumn,toColumn,matchType}], derivedMode}. '
    'Only meaningful when data_type = LOOKUP.';
