\set ON_ERROR_STOP on

-- =====================================================
-- Developer Workstation: Add FieldDefinition FK/PK metadata columns
-- =====================================================
-- These columns exist in 04-developer-workstation-schema.sql base DDL but were
-- added later (Flyway V317 migration).  Older databases initialized before the
-- columns were added to the base DDL are missing them, causing cascading JPA
-- operations (delete, list) on dw_field_definitions to fail with
-- "column does not exist".
--
-- Keep idempotent via ADD COLUMN IF NOT EXISTS.
-- =====================================================

ALTER TABLE dw_field_definitions
    ADD COLUMN IF NOT EXISTS is_foreign_key        BOOLEAN   NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS ref_table_id           BIGINT,
    ADD COLUMN IF NOT EXISTS ref_primary_key_fields JSONB,
    ADD COLUMN IF NOT EXISTS pk_generation_json     JSONB,
    ADD COLUMN IF NOT EXISTS fk_display_mode        VARCHAR(20) DEFAULT 'readonly',
    ADD COLUMN IF NOT EXISTS relation_cardinality   VARCHAR(20);

COMMENT ON COLUMN dw_field_definitions.is_foreign_key IS
    'Whether this column is a foreign key reference to another table';
COMMENT ON COLUMN dw_field_definitions.ref_table_id IS
    'Target table ID when is_foreign_key = true';
COMMENT ON COLUMN dw_field_definitions.ref_primary_key_fields IS
    'List of primary-key fields in the referenced table (JSONB)';
COMMENT ON COLUMN dw_field_definitions.pk_generation_json IS
    'Primary key generation configuration (JSONB)';
COMMENT ON COLUMN dw_field_definitions.fk_display_mode IS
    'FK display mode: readonly, select, search, etc.';
COMMENT ON COLUMN dw_field_definitions.relation_cardinality IS
    'Relationship cardinality: ONE_TO_ONE, ONE_TO_MANY, MANY_TO_MANY';
