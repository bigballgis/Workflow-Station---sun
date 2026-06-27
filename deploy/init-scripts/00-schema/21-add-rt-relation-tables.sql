-- =====================================================
-- Relation Table: rt_* definitions, access, audit, view, lookup
-- Consolidates admin-center V202 and developer-workstation V1
-- Aligns with admin-center RelationTable* and developer Relation* entities
-- =====================================================

-- 1. Table Definitions (rt_table_definitions)
CREATE TABLE IF NOT EXISTS rt_table_definitions (
    id                  BIGSERIAL       PRIMARY KEY,
    table_name          VARCHAR(100)    NOT NULL UNIQUE,
    display_name        VARCHAR(200),
    deployed_display_name VARCHAR(200),
    description         TEXT,
    status              VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',
    enabled             BOOLEAN         NOT NULL DEFAULT TRUE,
    portal_visible      BOOLEAN         NOT NULL DEFAULT FALSE,
    current_version     INTEGER         DEFAULT 0,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          VARCHAR(64),
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(64)
);

COMMENT ON TABLE rt_table_definitions IS 'Relation Table definitions with metadata and versioning';
COMMENT ON COLUMN rt_table_definitions.status IS 'DRAFT / DEPLOYED / ROLLBACK';
COMMENT ON COLUMN rt_table_definitions.portal_visible IS 'Controls visibility in User Portal';
COMMENT ON COLUMN rt_table_definitions.current_version IS 'Current deployed version number';
COMMENT ON COLUMN rt_table_definitions.deployed_display_name IS 'Display name captured on last deploy; Table Data uses this while status is UPDATED/ROLLBACK';

-- 2. Field Definitions (rt_field_definitions)
CREATE TABLE IF NOT EXISTS rt_field_definitions (
    id                  BIGSERIAL       PRIMARY KEY,
    table_id            BIGINT          NOT NULL REFERENCES rt_table_definitions(id),
    field_name          VARCHAR(100)    NOT NULL,
    data_type           VARCHAR(50)     NOT NULL,
    length              INTEGER,
    precision_value     INTEGER,
    scale               INTEGER,
    nullable            BOOLEAN         DEFAULT TRUE,
    is_primary_key      BOOLEAN         DEFAULT FALSE,
    default_value       VARCHAR(500),
    display_name        TEXT,
    sort_order          INTEGER         NOT NULL,
    is_foreign_key      BOOLEAN         NOT NULL DEFAULT FALSE,
    ref_table_id        BIGINT          REFERENCES rt_table_definitions(id) ON DELETE SET NULL,
    ref_primary_key_fields JSONB,
    pk_generation_json  JSONB,
    fk_display_mode     VARCHAR(20)     DEFAULT 'readonly'
);

CREATE INDEX IF NOT EXISTS idx_rt_field_table_id ON rt_field_definitions(table_id);

COMMENT ON TABLE rt_field_definitions IS 'Field definitions for Relation Tables';

-- 2b. PK Sequences (rt_pk_sequences) — 与 admin-center Flyway V207 一致；
-- relation table 主键自动生成的计数器，全新初始化必须带上，否则 PK 生成在新库上崩溃
CREATE TABLE IF NOT EXISTS rt_pk_sequences (
    id              BIGSERIAL       PRIMARY KEY,
    table_id        BIGINT          NOT NULL,
    field_name      VARCHAR(100)    NOT NULL,
    scope_type      VARCHAR(32)     NOT NULL DEFAULT 'perTable',
    scope_key       VARCHAR(128)    NOT NULL DEFAULT '',
    prefix          VARCHAR(64)     DEFAULT '',
    pad_width       INTEGER         DEFAULT 6,
    current_value   BIGINT          NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_rt_pk_seq UNIQUE (table_id, field_name, scope_type, scope_key)
);

CREATE INDEX IF NOT EXISTS idx_rt_pk_sequences_table ON rt_pk_sequences(table_id);

COMMENT ON TABLE rt_pk_sequences IS 'Counter table for Relation Table auto-generated primary keys';

-- 3. Table Versions (rt_table_versions)
CREATE TABLE IF NOT EXISTS rt_table_versions (
    id                  BIGSERIAL       PRIMARY KEY,
    table_id            BIGINT          NOT NULL REFERENCES rt_table_definitions(id),
    version_number      INTEGER         NOT NULL,
    snapshot_data       TEXT            NOT NULL,
    deployed_by         VARCHAR(64)     NOT NULL,
    deployed_at         TIMESTAMP       NOT NULL,
    change_log          TEXT
);

CREATE INDEX IF NOT EXISTS idx_rt_version_table_id ON rt_table_versions(table_id);

COMMENT ON TABLE rt_table_versions IS 'Version snapshots created on each deployment';

-- 4. Table Access (rt_table_access)
CREATE TABLE IF NOT EXISTS rt_table_access (
    id                  VARCHAR(64)     PRIMARY KEY,
    table_id            BIGINT          NOT NULL REFERENCES rt_table_definitions(id),
    target_type         VARCHAR(20)     NOT NULL,
    target_id           VARCHAR(64)     NOT NULL,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          VARCHAR(64)
);

CREATE INDEX IF NOT EXISTS idx_rt_access_table_id ON rt_table_access(table_id);
CREATE INDEX IF NOT EXISTS idx_rt_access_target ON rt_table_access(target_type, target_id);

COMMENT ON TABLE rt_table_access IS 'Business Role access configuration for Relation Tables';
COMMENT ON COLUMN rt_table_access.target_type IS 'ROLE';

-- 5. Audit Logs (rt_audit_logs)
CREATE TABLE IF NOT EXISTS rt_audit_logs (
    id                  VARCHAR(64)     PRIMARY KEY,
    table_id            BIGINT          NOT NULL,
    table_name          VARCHAR(100)    NOT NULL,
    row_id              VARCHAR(100),
    action              VARCHAR(20)     NOT NULL,
    old_value           TEXT,
    new_value           TEXT,
    operator_id         VARCHAR(64)     NOT NULL,
    operator_name       VARCHAR(100),
    operated_at         TIMESTAMP       NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_rt_audit_table ON rt_audit_logs(table_id);
CREATE INDEX IF NOT EXISTS idx_rt_audit_action ON rt_audit_logs(action);
CREATE INDEX IF NOT EXISTS idx_rt_audit_operator ON rt_audit_logs(operator_id);
CREATE INDEX IF NOT EXISTS idx_rt_audit_time ON rt_audit_logs(operated_at);

COMMENT ON TABLE rt_audit_logs IS 'Audit log for Relation Table data changes';
COMMENT ON COLUMN rt_audit_logs.action IS 'ADD / UPDATE / DELETE / STATUS_CHANGE';

-- 5b. Row Data (rt_table_data_rows) — JSON storage, no per-table physical tables
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

-- 搜索/分页性能索引（与 admin-center Flyway V214 保持一致；全新初始化即带上）
-- 字段名由用户动态定义，无法逐字段建索引；用整行 data::text 的 pg_trgm GIN 加速 ILIKE '%term%'
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX IF NOT EXISTS idx_rt_data_rows_data_trgm
    ON rt_table_data_rows USING gin ((data::text) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_rt_data_rows_table_id_id
    ON rt_table_data_rows (table_id, id);

COMMENT ON TABLE rt_table_data_rows IS 'Relation Table row data stored as JSON';

-- 6. View Configs (rt_view_configs)
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

-- 7. View Fields (rt_view_fields)
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

-- 8. Lookup Configs (rt_lookup_configs)
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
COMMENT ON COLUMN rt_lookup_configs.search_fields IS 'JSON array: fields used for search in User Portal';

-- Parity with Flyway V2__add_relation_table_id_to_bindings (older DBs may lack this column)
ALTER TABLE dw_form_table_bindings ALTER COLUMN table_id DROP NOT NULL;
ALTER TABLE dw_form_table_bindings ADD COLUMN IF NOT EXISTS relation_table_id BIGINT;

-- Partial index for RELATED bindings
CREATE INDEX IF NOT EXISTS idx_binding_relation_table ON dw_form_table_bindings(relation_table_id)
    WHERE relation_table_id IS NOT NULL;
