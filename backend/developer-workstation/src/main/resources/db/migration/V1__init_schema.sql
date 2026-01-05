-- Developer Workstation Database Schema
-- Version: 1.0.0

-- 功能单元表
CREATE TABLE dw_function_units (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(500),
    icon_id BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    current_version VARCHAR(20),
    created_by VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(50),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_function_units_name ON dw_function_units(name);
CREATE INDEX idx_function_units_status ON dw_function_units(status);

-- 流程定义表
CREATE TABLE dw_process_definitions (
    id BIGSERIAL PRIMARY KEY,
    function_unit_id BIGINT NOT NULL UNIQUE,
    bpmn_xml TEXT,
    created_by VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(50),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_process_function_unit FOREIGN KEY (function_unit_id) 
        REFERENCES dw_function_units(id) ON DELETE CASCADE
);

-- 表定义表
CREATE TABLE dw_table_definitions (
    id BIGSERIAL PRIMARY KEY,
    function_unit_id BIGINT NOT NULL,
    table_name VARCHAR(100) NOT NULL,
    table_type VARCHAR(20) NOT NULL DEFAULT 'MAIN',
    description VARCHAR(500),
    created_by VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(50),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_table_function_unit FOREIGN KEY (function_unit_id) 
        REFERENCES dw_function_units(id) ON DELETE CASCADE,
    CONSTRAINT uk_table_name_function_unit UNIQUE (function_unit_id, table_name)
);

CREATE INDEX idx_table_definitions_function_unit ON dw_table_definitions(function_unit_id);

-- 字段定义表
CREATE TABLE dw_field_definitions (
    id BIGSERIAL PRIMARY KEY,
    table_definition_id BIGINT NOT NULL,
    field_name VARCHAR(100) NOT NULL,
    data_type VARCHAR(30) NOT NULL,
    length INTEGER,
    precision_val INTEGER,
    scale_val INTEGER,
    nullable BOOLEAN DEFAULT TRUE,
    default_value VARCHAR(200),
    is_primary_key BOOLEAN DEFAULT FALSE,
    is_unique BOOLEAN DEFAULT FALSE,
    description VARCHAR(500),
    sort_order INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_field_table FOREIGN KEY (table_definition_id) 
        REFERENCES dw_table_definitions(id) ON DELETE CASCADE,
    CONSTRAINT uk_field_name_table UNIQUE (table_definition_id, field_name)
);

CREATE INDEX idx_field_definitions_table ON dw_field_definitions(table_definition_id);

-- 外键关系表
CREATE TABLE dw_foreign_keys (
    id BIGSERIAL PRIMARY KEY,
    source_table_id BIGINT NOT NULL,
    source_field_id BIGINT NOT NULL,
    target_table_id BIGINT NOT NULL,
    target_field_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_fk_source_table FOREIGN KEY (source_table_id) 
        REFERENCES dw_table_definitions(id) ON DELETE CASCADE,
    CONSTRAINT fk_fk_source_field FOREIGN KEY (source_field_id) 
        REFERENCES dw_field_definitions(id) ON DELETE CASCADE,
    CONSTRAINT fk_fk_target_table FOREIGN KEY (target_table_id) 
        REFERENCES dw_table_definitions(id) ON DELETE CASCADE,
    CONSTRAINT fk_fk_target_field FOREIGN KEY (target_field_id) 
        REFERENCES dw_field_definitions(id) ON DELETE CASCADE
);

-- 表单定义表
CREATE TABLE dw_form_definitions (
    id BIGSERIAL PRIMARY KEY,
    function_unit_id BIGINT NOT NULL,
    form_name VARCHAR(100) NOT NULL,
    form_type VARCHAR(20) NOT NULL DEFAULT 'MAIN',
    bound_table_id BIGINT,
    config_json JSONB,
    description VARCHAR(500),
    created_by VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(50),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_form_function_unit FOREIGN KEY (function_unit_id) 
        REFERENCES dw_function_units(id) ON DELETE CASCADE,
    CONSTRAINT fk_form_bound_table FOREIGN KEY (bound_table_id) 
        REFERENCES dw_table_definitions(id) ON DELETE SET NULL,
    CONSTRAINT uk_form_name_function_unit UNIQUE (function_unit_id, form_name)
);

CREATE INDEX idx_form_definitions_function_unit ON dw_form_definitions(function_unit_id);

-- 动作定义表
CREATE TABLE dw_action_definitions (
    id BIGSERIAL PRIMARY KEY,
    function_unit_id BIGINT NOT NULL,
    action_name VARCHAR(100) NOT NULL,
    action_type VARCHAR(30) NOT NULL,
    icon_id BIGINT,
    button_color VARCHAR(20),
    config_json JSONB,
    description VARCHAR(500),
    is_default BOOLEAN DEFAULT FALSE,
    process_step_id VARCHAR(100),
    created_by VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(50),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_action_function_unit FOREIGN KEY (function_unit_id) 
        REFERENCES dw_function_units(id) ON DELETE CASCADE,
    CONSTRAINT uk_action_name_function_unit UNIQUE (function_unit_id, action_name)
);

CREATE INDEX idx_action_definitions_function_unit ON dw_action_definitions(function_unit_id);

-- 图标表
CREATE TABLE dw_icons (
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

CREATE INDEX idx_icons_category ON dw_icons(category);
CREATE INDEX idx_icons_name ON dw_icons(name);

-- 版本表
CREATE TABLE dw_versions (
    id BIGSERIAL PRIMARY KEY,
    function_unit_id BIGINT NOT NULL,
    version_number VARCHAR(20) NOT NULL,
    change_log TEXT,
    snapshot_data BYTEA,
    published_by VARCHAR(50),
    published_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_version_function_unit FOREIGN KEY (function_unit_id) 
        REFERENCES dw_function_units(id) ON DELETE CASCADE,
    CONSTRAINT uk_version_function_unit UNIQUE (function_unit_id, version_number)
);

CREATE INDEX idx_versions_function_unit ON dw_versions(function_unit_id);

-- 操作日志表
CREATE TABLE dw_operation_logs (
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

CREATE INDEX idx_operation_logs_operator ON dw_operation_logs(operator);
CREATE INDEX idx_operation_logs_target ON dw_operation_logs(target_type, target_id);
CREATE INDEX idx_operation_logs_time ON dw_operation_logs(operation_time);

-- 添加外键约束：功能单元图标
ALTER TABLE dw_function_units 
    ADD CONSTRAINT fk_function_unit_icon FOREIGN KEY (icon_id) 
    REFERENCES dw_icons(id) ON DELETE SET NULL;

-- 添加外键约束：动作图标
ALTER TABLE dw_action_definitions 
    ADD CONSTRAINT fk_action_icon FOREIGN KEY (icon_id) 
    REFERENCES dw_icons(id) ON DELETE SET NULL;
