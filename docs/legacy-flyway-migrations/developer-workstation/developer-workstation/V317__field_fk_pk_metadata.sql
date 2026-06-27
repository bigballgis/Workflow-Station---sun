-- V317: Field-level FK/PK metadata on dw_field_definitions (PRD §5, §12)

ALTER TABLE dw_field_definitions
    ADD COLUMN IF NOT EXISTS is_foreign_key BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS ref_table_id BIGINT,
    ADD COLUMN IF NOT EXISTS ref_primary_key_fields JSONB,
    ADD COLUMN IF NOT EXISTS pk_generation_json JSONB,
    ADD COLUMN IF NOT EXISTS fk_display_mode VARCHAR(20) DEFAULT 'readonly',
    ADD COLUMN IF NOT EXISTS relation_cardinality VARCHAR(20);

ALTER TABLE dw_field_definitions
    ADD CONSTRAINT fk_field_ref_table
        FOREIGN KEY (ref_table_id) REFERENCES dw_table_definitions(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_dw_field_definitions_fk_ref
    ON dw_field_definitions(ref_table_id) WHERE is_foreign_key = TRUE;
