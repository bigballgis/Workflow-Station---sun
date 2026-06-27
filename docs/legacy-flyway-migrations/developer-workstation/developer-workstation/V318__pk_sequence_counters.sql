-- V318: PK allocation counters (PRD §5.2, S3)

CREATE TABLE IF NOT EXISTS dw_pk_sequences (
    id BIGSERIAL PRIMARY KEY,
    table_id BIGINT NOT NULL,
    field_name VARCHAR(100) NOT NULL,
    scope_type VARCHAR(32) NOT NULL DEFAULT 'perTable',
    scope_key VARCHAR(128) NOT NULL DEFAULT '',
    prefix VARCHAR(64) DEFAULT '',
    pad_width INTEGER DEFAULT 6,
    current_value BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_dw_pk_seq UNIQUE (table_id, field_name, scope_type, scope_key)
);

CREATE INDEX IF NOT EXISTS idx_dw_pk_sequences_table ON dw_pk_sequences(table_id);
