-- Relation Table 行数据：统一 JSONB 存储，不创建 per-table 物理表
CREATE TABLE IF NOT EXISTS rt_table_data_rows (
    id          BIGSERIAL       PRIMARY KEY,
    table_id    BIGINT          NOT NULL REFERENCES rt_table_definitions(id) ON DELETE CASCADE,
    row_id      VARCHAR(100)    NOT NULL,
    data        JSONB           NOT NULL DEFAULT '{}',
    status      VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  VARCHAR(64),
    updated_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  VARCHAR(64),
    CONSTRAINT uk_rt_data_rows_table_row UNIQUE (table_id, row_id)
);

CREATE INDEX IF NOT EXISTS idx_rt_data_rows_table_id ON rt_table_data_rows(table_id);
CREATE INDEX IF NOT EXISTS idx_rt_data_rows_table_status ON rt_table_data_rows(table_id, status);

COMMENT ON TABLE rt_table_data_rows IS 'Relation Table row data stored as JSON (no per-table physical tables)';
COMMENT ON COLUMN rt_table_data_rows.data IS 'Field values keyed by field_name';
COMMENT ON COLUMN rt_table_data_rows.row_id IS 'Business row identifier (PK value or generated UUID)';
