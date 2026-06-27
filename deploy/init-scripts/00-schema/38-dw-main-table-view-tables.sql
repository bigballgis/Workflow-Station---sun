-- Main Table View configs (one default view per table: MAIN + SUB)
CREATE TABLE IF NOT EXISTS dw_main_table_view_configs (
    id BIGSERIAL PRIMARY KEY,
    function_unit_id BIGINT NOT NULL REFERENCES dw_function_units(id) ON DELETE CASCADE,
    main_table_id BIGINT NOT NULL REFERENCES dw_table_definitions(id) ON DELETE CASCADE,
    view_name VARCHAR(200) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    sort_config JSONB,
    filter_config JSONB,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_main_table_view_configs_fu ON dw_main_table_view_configs(function_unit_id);
CREATE INDEX IF NOT EXISTS idx_main_table_view_configs_table ON dw_main_table_view_configs(main_table_id);
-- Default view is now per-table (MAIN + SUB), not per-function-unit.
-- Drop the legacy per-FU unique index explicitly so already-running databases are converted
-- (snapshot-style CREATE IF NOT EXISTS does not replace an existing index).
DROP INDEX IF EXISTS idx_main_table_view_configs_fu_default;
CREATE UNIQUE INDEX IF NOT EXISTS idx_mtv_configs_table_default
    ON dw_main_table_view_configs(main_table_id) WHERE is_default = TRUE;

CREATE TABLE IF NOT EXISTS dw_main_table_view_fields (
    id BIGSERIAL PRIMARY KEY,
    view_config_id BIGINT NOT NULL REFERENCES dw_main_table_view_configs(id) ON DELETE CASCADE,
    field_name VARCHAR(100) NOT NULL,
    display_label VARCHAR(200),
    column_width INTEGER,
    sort_order INTEGER NOT NULL DEFAULT 0,
    visible BOOLEAN NOT NULL DEFAULT TRUE,
    is_system_field BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_main_table_view_fields_config ON dw_main_table_view_fields(view_config_id, sort_order);
