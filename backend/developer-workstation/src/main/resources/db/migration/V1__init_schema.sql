-- =====================================================
-- Developer Workstation V1: Core Tables
-- All dw_* tables for developer workstation features
-- Generated from JPA Entity definitions
-- =====================================================

-- =====================================================
-- 1. Icons Table (dw_icons) - Must be created first for FK references
-- =====================================================
CREATE TABLE IF NOT EXISTS dw_icons (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    category VARCHAR(30) NOT NULL,
    svg_content TEXT NOT NULL,
    file_size INTEGER,
    description VARCHAR(500),
    created_by VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(50),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_dw_icons_name ON dw_icons(name);
CREATE INDEX IF NOT EXISTS idx_dw_icons_category ON dw_icons(category);

-- =====================================================
-- 2. Function Units Table (dw_function_units)
-- =====================================================
CREATE TABLE IF NOT EXISTS dw_function_units (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    icon_id BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    current_version VARCHAR(20),
    created_by VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(50),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_function_unit_icon FOREIGN KEY (icon_id) REFERENCES dw_icons(id) ON DELETE SET NULL,
    CONSTRAINT chk_function_unit_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);

CREATE INDEX IF NOT EXISTS idx_dw_function_units_name ON dw_function_units(name);
CREATE INDEX IF NOT EXISTS idx_dw_function_units_status ON dw_function_units(status);

-- =====================================================
-- 3. Process Definitions Table (dw_process_definitions)
-- =====================================================
CREATE TABLE IF NOT EXISTS dw_process_definitions (
    id BIGSERIAL PRIMARY KEY,
    function_unit_id BIGINT NOT NULL,
    bpmn_xml TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_process_function_unit FOREIGN KEY (function_unit_id) REFERENCES dw_function_units(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_dw_process_definitions_fu ON dw_process_definitions(function_unit_id);

-- =====================================================
-- 4. Table Definitions Table (dw_table_definitions)
-- =====================================================
CREATE TABLE IF NOT EXISTS dw_table_definitions (
    id BIGSERIAL PRIMARY KEY,
    function_unit_id BIGINT NOT NULL,
    table_name VARCHAR(100) NOT NULL,
    table_type VARCHAR(20) NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_table_function_unit FOREIGN KEY (function_unit_id) REFERENCES dw_function_units(id) ON DELETE CASCADE,
    CONSTRAINT uk_table_name_fu UNIQUE (function_unit_id, table_name),
    CONSTRAINT chk_table_type CHECK (table_type IN ('MAIN', 'SUB', 'ACTION', 'RELATION'))
);

CREATE INDEX IF NOT EXISTS idx_dw_table_definitions_fu ON dw_table_definitions(function_unit_id);


-- =====================================================
-- 5. Field Definitions Table (dw_field_definitions)
-- =====================================================
CREATE TABLE IF NOT EXISTS dw_field_definitions (
    id BIGSERIAL PRIMARY KEY,
    table_id BIGINT NOT NULL,
    field_name VARCHAR(100) NOT NULL,
    data_type VARCHAR(50) NOT NULL,
    length INTEGER,
    precision_value INTEGER,
    scale INTEGER,
    nullable BOOLEAN DEFAULT TRUE,
    default_value VARCHAR(500),
    is_primary_key BOOLEAN DEFAULT FALSE,
    is_unique BOOLEAN DEFAULT FALSE,
    description TEXT,
    sort_order INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_field_table FOREIGN KEY (table_id) REFERENCES dw_table_definitions(id) ON DELETE CASCADE,
    CONSTRAINT uk_field_name_table UNIQUE (table_id, field_name)
);

CREATE INDEX IF NOT EXISTS idx_dw_field_definitions_table ON dw_field_definitions(table_id);

-- =====================================================
-- 6. Foreign Keys Table (dw_foreign_keys)
-- =====================================================
CREATE TABLE IF NOT EXISTS dw_foreign_keys (
    id BIGSERIAL PRIMARY KEY,
    table_id BIGINT NOT NULL,
    field_id BIGINT NOT NULL,
    ref_table_id BIGINT NOT NULL,
    ref_field_id BIGINT NOT NULL,
    on_delete VARCHAR(20) DEFAULT 'NO ACTION',
    on_update VARCHAR(20) DEFAULT 'NO ACTION',
    CONSTRAINT fk_fk_table FOREIGN KEY (table_id) REFERENCES dw_table_definitions(id) ON DELETE CASCADE,
    CONSTRAINT fk_fk_field FOREIGN KEY (field_id) REFERENCES dw_field_definitions(id) ON DELETE CASCADE,
    CONSTRAINT fk_fk_ref_table FOREIGN KEY (ref_table_id) REFERENCES dw_table_definitions(id) ON DELETE CASCADE,
    CONSTRAINT fk_fk_ref_field FOREIGN KEY (ref_field_id) REFERENCES dw_field_definitions(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_dw_foreign_keys_table ON dw_foreign_keys(table_id);

-- =====================================================
-- 7. Form Definitions Table (dw_form_definitions)
-- =====================================================
CREATE TABLE IF NOT EXISTS dw_form_definitions (
    id BIGSERIAL PRIMARY KEY,
    function_unit_id BIGINT NOT NULL,
    form_name VARCHAR(100) NOT NULL,
    form_type VARCHAR(20) NOT NULL,
    config_json JSONB NOT NULL DEFAULT '{}',
    description TEXT,
    bound_table_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_form_function_unit FOREIGN KEY (function_unit_id) REFERENCES dw_function_units(id) ON DELETE CASCADE,
    CONSTRAINT fk_form_bound_table FOREIGN KEY (bound_table_id) REFERENCES dw_table_definitions(id) ON DELETE SET NULL,
    CONSTRAINT uk_form_name_fu UNIQUE (function_unit_id, form_name),
    CONSTRAINT chk_form_type CHECK (form_type IN ('MAIN', 'SUB', 'ACTION', 'POPUP'))
);

CREATE INDEX IF NOT EXISTS idx_dw_form_definitions_fu ON dw_form_definitions(function_unit_id);

-- =====================================================
-- 8. Form Table Bindings Table (dw_form_table_bindings)
-- =====================================================
CREATE TABLE IF NOT EXISTS dw_form_table_bindings (
    id BIGSERIAL PRIMARY KEY,
    form_id BIGINT NOT NULL,
    table_id BIGINT NOT NULL,
    binding_type VARCHAR(20) NOT NULL,
    binding_mode VARCHAR(20) NOT NULL DEFAULT 'READONLY',
    foreign_key_field VARCHAR(100),
    sort_order INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT fk_binding_form FOREIGN KEY (form_id) REFERENCES dw_form_definitions(id) ON DELETE CASCADE,
    CONSTRAINT fk_binding_table FOREIGN KEY (table_id) REFERENCES dw_table_definitions(id),
    CONSTRAINT uk_form_table_binding UNIQUE (form_id, table_id),
    CONSTRAINT chk_binding_type CHECK (binding_type IN ('PRIMARY', 'SUB', 'RELATED')),
    CONSTRAINT chk_binding_mode CHECK (binding_mode IN ('EDITABLE', 'READONLY'))
);

CREATE INDEX IF NOT EXISTS idx_dw_form_table_bindings_form ON dw_form_table_bindings(form_id);
CREATE INDEX IF NOT EXISTS idx_dw_form_table_bindings_table ON dw_form_table_bindings(table_id);

-- =====================================================
-- 9. Action Definitions Table (dw_action_definitions)
-- =====================================================
CREATE TABLE IF NOT EXISTS dw_action_definitions (
    id BIGSERIAL PRIMARY KEY,
    function_unit_id BIGINT NOT NULL,
    action_name VARCHAR(100) NOT NULL,
    action_type VARCHAR(20) NOT NULL,
    config_json JSONB NOT NULL DEFAULT '{}',
    icon VARCHAR(50),
    button_color VARCHAR(20),
    description TEXT,
    is_default BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_action_function_unit FOREIGN KEY (function_unit_id) REFERENCES dw_function_units(id) ON DELETE CASCADE,
    CONSTRAINT uk_action_name_fu UNIQUE (function_unit_id, action_name)
);

CREATE INDEX IF NOT EXISTS idx_dw_action_definitions_fu ON dw_action_definitions(function_unit_id);

-- =====================================================
-- 10. Versions Table (dw_versions)
-- =====================================================
CREATE TABLE IF NOT EXISTS dw_versions (
    id BIGSERIAL PRIMARY KEY,
    function_unit_id BIGINT NOT NULL,
    version_number VARCHAR(20) NOT NULL,
    change_log TEXT,
    snapshot_data BYTEA NOT NULL,
    published_by VARCHAR(50) NOT NULL,
    published_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_version_function_unit FOREIGN KEY (function_unit_id) REFERENCES dw_function_units(id) ON DELETE CASCADE,
    CONSTRAINT uk_version_fu UNIQUE (function_unit_id, version_number)
);

CREATE INDEX IF NOT EXISTS idx_dw_versions_fu ON dw_versions(function_unit_id);

-- =====================================================
-- 11. Operation Logs Table (dw_operation_logs)
-- =====================================================
CREATE TABLE IF NOT EXISTS dw_operation_logs (
    id BIGSERIAL PRIMARY KEY,
    operator VARCHAR(50) NOT NULL,
    operation_type VARCHAR(50) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id BIGINT,
    description VARCHAR(500),
    details TEXT,
    ip_address VARCHAR(50),
    operation_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_dw_operation_logs_operator ON dw_operation_logs(operator);
CREATE INDEX IF NOT EXISTS idx_dw_operation_logs_target ON dw_operation_logs(target_type, target_id);
CREATE INDEX IF NOT EXISTS idx_dw_operation_logs_time ON dw_operation_logs(operation_time);

-- =====================================================
-- Comments
-- =====================================================
COMMENT ON TABLE dw_icons IS 'Icon library for function units and actions';
COMMENT ON TABLE dw_function_units IS 'Function unit definitions';
COMMENT ON TABLE dw_process_definitions IS 'BPMN process definitions';
COMMENT ON TABLE dw_table_definitions IS 'Data table definitions';
COMMENT ON TABLE dw_field_definitions IS 'Table field definitions';
COMMENT ON TABLE dw_foreign_keys IS 'Foreign key relationships between tables';
COMMENT ON TABLE dw_form_definitions IS 'Form definitions';
COMMENT ON TABLE dw_form_table_bindings IS 'Form-table binding relationships';
COMMENT ON TABLE dw_action_definitions IS 'Action/button definitions';
COMMENT ON TABLE dw_versions IS 'Function unit version history';
COMMENT ON TABLE dw_operation_logs IS 'Operation audit logs';
