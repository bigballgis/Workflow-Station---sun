-- =====================================================
-- Relation Table Management Module: Database Tables
-- Tables with rt_* prefix for Relation Table features
-- Validates: Requirements 3.4, 5.2, 12.1, 13.5
-- =====================================================

-- =====================================================
-- 1. Table Definitions (rt_table_definitions)
-- Stores Relation Table metadata including name, status,
-- version, and portal visibility settings
-- =====================================================
CREATE TABLE IF NOT EXISTS rt_table_definitions (
    id                  BIGSERIAL       PRIMARY KEY,
    table_name          VARCHAR(100)    NOT NULL UNIQUE,
    display_name        VARCHAR(200),
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

-- =====================================================
-- 2. Field Definitions (rt_field_definitions)
-- Stores field-level metadata for each Relation Table
-- =====================================================
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
    comment             TEXT,
    sort_order          INTEGER         NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_rt_field_table_id ON rt_field_definitions(table_id);

COMMENT ON TABLE rt_field_definitions IS 'Field definitions for Relation Tables';
COMMENT ON COLUMN rt_field_definitions.data_type IS 'VARCHAR / INTEGER / BIGINT / DECIMAL / BOOLEAN / DATE / TIMESTAMP / TEXT';
COMMENT ON COLUMN rt_field_definitions.sort_order IS 'Display order of the field';

-- =====================================================
-- 3. Table Versions (rt_table_versions)
-- Stores deployment version snapshots for each table
-- =====================================================
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
COMMENT ON COLUMN rt_table_versions.snapshot_data IS 'JSON format complete table structure snapshot';

-- =====================================================
-- 4. Table Access (rt_table_access)
-- Stores Business Role access configuration per table
-- =====================================================
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

-- =====================================================
-- 5. Audit Logs (rt_audit_logs)
-- Records all data change operations for auditing
-- =====================================================
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
COMMENT ON COLUMN rt_audit_logs.old_value IS 'JSON format data before change';
COMMENT ON COLUMN rt_audit_logs.new_value IS 'JSON format data after change';
