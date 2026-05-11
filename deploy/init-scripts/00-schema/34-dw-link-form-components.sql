\set ON_ERROR_STOP on

-- =====================================================
-- Developer Workstation: Link Form Components
-- =====================================================
-- Mirrors Flyway migration
--   backend/developer-workstation/.../V312__add_sub_mode_and_link_components.sql
-- so the schema is present in dev where Flyway is disabled
-- (see docker-compose.dev.yml -> SPRING_FLYWAY_ENABLED=false; init-scripts are the
-- source of truth in dev per docs/schema-and-migration.md).
--
-- Fixes runtime 500 from
--   GET /api/v1/function-units/{id}/link-form-components
-- when the developer-workstation backend tries to query
-- dw_link_form_components but the table is absent.
--
-- The `sub_mode` column added by V312 is already provided by
-- 32-add-dw-form-table-binding-subview-columns.sql, so this script
-- focuses on the two link-form tables only.
--
-- All operations are idempotent.
-- =====================================================

-- Curated Link Form component definitions (functionUnit-scoped).
CREATE TABLE IF NOT EXISTS dw_link_form_components (
    id BIGSERIAL PRIMARY KEY,
    function_unit_id BIGINT NOT NULL,
    component_name VARCHAR(200) NOT NULL,
    linked_form_id BIGINT NOT NULL,
    display_field VARCHAR(100),
    link_text VARCHAR(200) DEFAULT '详情',
    column_label VARCHAR(200),
    sort_order INTEGER NOT NULL DEFAULT 0,
    config_json JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_link_form_components_function_unit
    ON dw_link_form_components(function_unit_id);
CREATE INDEX IF NOT EXISTS idx_link_form_components_sort_order
    ON dw_link_form_components(function_unit_id, sort_order);

-- Per-row data captured by Link Form widgets (one row per sub-table row).
CREATE TABLE IF NOT EXISTS dw_link_form_data (
    id BIGSERIAL PRIMARY KEY,
    component_id BIGINT NOT NULL,
    sub_table_row_id BIGINT NOT NULL,
    form_data JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_link_form_data_component_row
    ON dw_link_form_data(component_id, sub_table_row_id);
CREATE INDEX IF NOT EXISTS idx_link_form_data_row
    ON dw_link_form_data(sub_table_row_id);

COMMENT ON TABLE dw_link_form_components IS
    'Designer-curated Link Form widget definitions (function-unit scoped). '
    'Negative componentIds on list-view columns are GENERIC, not from this table; '
    'positive ids reference rows here.';
COMMENT ON TABLE dw_link_form_data IS
    'Per-row Link Form data captured at runtime, keyed by component_id + sub_table_row_id.';
