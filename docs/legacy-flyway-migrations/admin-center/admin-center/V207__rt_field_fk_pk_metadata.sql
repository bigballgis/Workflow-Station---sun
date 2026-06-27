-- V207: Field-level FK/PK metadata on rt_field_definitions (PRD §9, AC parity)

ALTER TABLE rt_field_definitions
    ADD COLUMN IF NOT EXISTS is_foreign_key BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS ref_table_id BIGINT,
    ADD COLUMN IF NOT EXISTS ref_primary_key_fields JSONB,
    ADD COLUMN IF NOT EXISTS pk_generation_json JSONB,
    ADD COLUMN IF NOT EXISTS fk_display_mode VARCHAR(20) DEFAULT 'readonly';

ALTER TABLE rt_field_definitions
    ADD CONSTRAINT fk_rt_field_ref_table
        FOREIGN KEY (ref_table_id) REFERENCES rt_table_definitions(id) ON DELETE SET NULL;

CREATE TABLE IF NOT EXISTS rt_pk_sequences (
    id BIGSERIAL PRIMARY KEY,
    table_id BIGINT NOT NULL,
    field_name VARCHAR(100) NOT NULL,
    scope_type VARCHAR(32) NOT NULL DEFAULT 'perTable',
    scope_key VARCHAR(128) NOT NULL DEFAULT '',
    prefix VARCHAR(64) DEFAULT '',
    pad_width INTEGER DEFAULT 6,
    current_value BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_rt_pk_seq UNIQUE (table_id, field_name, scope_type, scope_key)
);

CREATE INDEX IF NOT EXISTS idx_rt_pk_sequences_table ON rt_pk_sequences(table_id);
