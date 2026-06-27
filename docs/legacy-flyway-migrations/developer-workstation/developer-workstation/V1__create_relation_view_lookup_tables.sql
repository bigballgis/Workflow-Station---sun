-- =====================================================
-- Relation Table View & Lookup Module: Database Tables
-- Tables with rt_* prefix for Developer Workstation
-- Validates: Requirements 9.3-9.6, 10.3-10.5
-- =====================================================

-- =====================================================
-- 1. View Configs (rt_view_configs)
-- Stores View page configuration for bound Relation Tables
-- =====================================================
CREATE TABLE IF NOT EXISTS rt_view_configs (
    id                  BIGSERIAL       PRIMARY KEY,
    binding_id          BIGINT          NOT NULL,
    table_id            BIGINT          NOT NULL,
    field_config        TEXT,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_rt_view_config_binding ON rt_view_configs(binding_id);
CREATE INDEX IF NOT EXISTS idx_rt_view_config_table ON rt_view_configs(table_id);

COMMENT ON TABLE rt_view_configs IS 'View configuration for bound Relation Tables';
COMMENT ON COLUMN rt_view_configs.binding_id IS 'FK to dw_form_table_bindings.id';
COMMENT ON COLUMN rt_view_configs.field_config IS 'JSON: selected fields list and order';

-- =====================================================
-- 2. View Fields (rt_view_fields)
-- Stores individual field configuration within a View
-- =====================================================
CREATE TABLE IF NOT EXISTS rt_view_fields (
    id                  BIGSERIAL       PRIMARY KEY,
    view_config_id      BIGINT          NOT NULL REFERENCES rt_view_configs(id) ON DELETE CASCADE,
    field_name          VARCHAR(100)    NOT NULL,
    display_label       VARCHAR(200),
    column_width        INTEGER,
    sort_order          INTEGER         NOT NULL,
    visible             BOOLEAN         NOT NULL DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_rt_view_field_config ON rt_view_fields(view_config_id);

COMMENT ON TABLE rt_view_fields IS 'Field-level configuration within a View';
COMMENT ON COLUMN rt_view_fields.sort_order IS 'Display order of the field in the View';

-- =====================================================
-- 3. Lookup Configs (rt_lookup_configs)
-- Stores Lookup component configuration for form-create
-- =====================================================
CREATE TABLE IF NOT EXISTS rt_lookup_configs (
    id                  BIGSERIAL       PRIMARY KEY,
    form_id             BIGINT          NOT NULL,
    component_id        VARCHAR(100)    NOT NULL,
    view_config_id      BIGINT          REFERENCES rt_view_configs(id),
    table_id            BIGINT          NOT NULL,
    search_fields       TEXT,
    display_field       VARCHAR(100),
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_rt_lookup_form ON rt_lookup_configs(form_id);
CREATE INDEX IF NOT EXISTS idx_rt_lookup_component ON rt_lookup_configs(form_id, component_id);

COMMENT ON TABLE rt_lookup_configs IS 'Lookup component configuration for form-create';
COMMENT ON COLUMN rt_lookup_configs.component_id IS 'form-create component unique identifier';
COMMENT ON COLUMN rt_lookup_configs.search_fields IS 'JSON array: fields used for search in User Portal';
COMMENT ON COLUMN rt_lookup_configs.display_field IS 'Field name used for display in search results';
