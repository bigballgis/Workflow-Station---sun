-- =====================================================
-- Common Table Feature Schema
-- Adds dw_common_* tables for shared table definitions
-- and their data storage, plus updates form bindings
-- =====================================================

-- =====================================================
-- 1. Common Table Definitions (dw_common_table_definitions)
-- =====================================================
CREATE TABLE IF NOT EXISTS dw_common_table_definitions (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_by VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_common_table_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);

CREATE INDEX IF NOT EXISTS idx_dw_common_table_defs_code ON dw_common_table_definitions(code);
CREATE INDEX IF NOT EXISTS idx_dw_common_table_defs_status ON dw_common_table_definitions(status);

-- =====================================================
-- 2. Common Field Definitions (dw_common_field_definitions)
-- =====================================================
CREATE TABLE IF NOT EXISTS dw_common_field_definitions (
    id BIGSERIAL PRIMARY KEY,
    common_table_id BIGINT NOT NULL,
    field_name VARCHAR(100) NOT NULL,
    display_name VARCHAR(200),
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
    CONSTRAINT fk_common_field_table FOREIGN KEY (common_table_id) REFERENCES dw_common_table_definitions(id) ON DELETE CASCADE,
    CONSTRAINT uk_common_field_name UNIQUE (common_table_id, field_name)
);

CREATE INDEX IF NOT EXISTS idx_dw_common_field_defs_table ON dw_common_field_definitions(common_table_id);

-- =====================================================
-- 3. Common Table Data (dw_common_table_data)
-- Stores actual data rows as JSONB for flexibility
-- =====================================================
CREATE TABLE IF NOT EXISTS dw_common_table_data (
    id BIGSERIAL PRIMARY KEY,
    common_table_id BIGINT NOT NULL,
    data_json JSONB NOT NULL DEFAULT '{}',
    created_by VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_common_data_table FOREIGN KEY (common_table_id) REFERENCES dw_common_table_definitions(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_dw_common_table_data_table ON dw_common_table_data(common_table_id);
CREATE INDEX IF NOT EXISTS idx_dw_common_table_data_json ON dw_common_table_data USING GIN (data_json);

-- =====================================================
-- 4. Alter dw_form_table_bindings to optionally reference a common table
-- When common_table_id is set, table_id should be NULL (and vice versa)
-- =====================================================
ALTER TABLE dw_form_table_bindings
    ADD COLUMN IF NOT EXISTS common_table_id BIGINT,
    ADD CONSTRAINT fk_binding_common_table FOREIGN KEY (common_table_id) REFERENCES dw_common_table_definitions(id);

-- Drop the old NOT NULL constraint on table_id by recreating it as nullable
-- (table_id OR common_table_id must be provided, enforced at application level)
ALTER TABLE dw_form_table_bindings
    ALTER COLUMN table_id DROP NOT NULL;

-- Remove the unique constraint that only covers (form_id, table_id)
-- and replace with a partial unique constraint for each case
ALTER TABLE dw_form_table_bindings
    DROP CONSTRAINT IF EXISTS uk_form_table_binding;

CREATE UNIQUE INDEX IF NOT EXISTS uk_form_fu_table_binding
    ON dw_form_table_bindings(form_id, table_id) WHERE table_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_form_common_table_binding
    ON dw_form_table_bindings(form_id, common_table_id) WHERE common_table_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_dw_form_bindings_common_table ON dw_form_table_bindings(common_table_id);

COMMENT ON TABLE dw_common_table_definitions IS 'Shared/reusable table definitions not tied to a single function unit';
COMMENT ON TABLE dw_common_field_definitions IS 'Field definitions for common tables';
COMMENT ON TABLE dw_common_table_data IS 'Actual data rows stored in common tables';
