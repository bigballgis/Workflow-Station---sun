-- Unified JSON row container for Developer Workstation Table Design tables.
--
-- Why: Table Design keeps STRUCTURE only (dw_table_definitions / dw_field_definitions);
-- row data is JSON, and no physical table is ever created per designer table name
-- (see .cursor/rules/json-row-storage-no-physical-tables.mdc).
--
-- ACTION-type tables (FORM_POPUP actions such as "Add Remark") are the one kind of
-- Table Design table whose rows live independently of the process variables: they are
-- keyed back to the running request through the binding's foreign_key_field, not through
-- __subTables__. They therefore need a durable JSON container of their own.
--
-- rt_table_data_rows could not be reused: its table_id carries
-- REFERENCES rt_table_definitions(id), so a dw_table_definitions id cannot be stored
-- there without violating that foreign key. This is the dw-side mirror of that table.
--
-- Idempotent: safe to re-run.

CREATE TABLE IF NOT EXISTS dw_table_data_rows (
    id          BIGSERIAL       PRIMARY KEY,
    table_id    BIGINT          NOT NULL REFERENCES dw_table_definitions(id) ON DELETE CASCADE,
    row_id      VARCHAR(100)    NOT NULL,
    data        JSONB           NOT NULL DEFAULT '{}',
    status      VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  VARCHAR(64),
    updated_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  VARCHAR(64),
    CONSTRAINT uk_dw_data_rows_table_row UNIQUE (table_id, row_id)
);

CREATE INDEX IF NOT EXISTS idx_dw_data_rows_table_id ON dw_table_data_rows(table_id);
CREATE INDEX IF NOT EXISTS idx_dw_data_rows_table_status ON dw_table_data_rows(table_id, status);

-- ACTION rows are always fetched by "this request's rows for this table", i.e.
-- table_id + the binding's foreign_key_field value inside data. Keep the JSONB
-- searchable the same way rt_table_data_rows is.
CREATE INDEX IF NOT EXISTS idx_dw_data_rows_data_trgm
    ON dw_table_data_rows USING GIN ((data::text) gin_trgm_ops);
