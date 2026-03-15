-- =====================================================
-- Common Table Deploy Feature Schema
-- Adds version, enabled, deploy tracking to common tables
-- =====================================================

-- =====================================================
-- 1. Add deploy-related columns to dw_common_table_definitions
-- =====================================================
ALTER TABLE dw_common_table_definitions
    ADD COLUMN IF NOT EXISTS version VARCHAR(20) DEFAULT '1.0.0',
    ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS deployed_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS deployed_by VARCHAR(100),
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(100);

-- Update status constraint to include PUBLISHED (was already in original, but make sure ARCHIVED is also allowed)
-- The original constraint already has DRAFT, PUBLISHED, ARCHIVED

CREATE INDEX IF NOT EXISTS idx_dw_common_table_defs_enabled ON dw_common_table_definitions(enabled);

-- =====================================================
-- 2. Common Table Deployment Records
-- =====================================================
CREATE TABLE IF NOT EXISTS dw_common_table_deployments (
    id BIGSERIAL PRIMARY KEY,
    common_table_id BIGINT NOT NULL,
    version VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    field_snapshot JSONB,
    deployed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deployed_by VARCHAR(100),
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_ctd_common_table FOREIGN KEY (common_table_id)
        REFERENCES dw_common_table_definitions(id) ON DELETE CASCADE,
    CONSTRAINT chk_ctd_status CHECK (status IN ('COMPLETED', 'FAILED', 'ROLLED_BACK'))
);

CREATE INDEX IF NOT EXISTS idx_dw_common_table_deployments_table ON dw_common_table_deployments(common_table_id);
CREATE INDEX IF NOT EXISTS idx_dw_common_table_deployments_at ON dw_common_table_deployments(deployed_at DESC);

-- =====================================================
-- 3. Common Table Access Control
-- =====================================================
CREATE TABLE IF NOT EXISTS dw_common_table_access (
    id BIGSERIAL PRIMARY KEY,
    common_table_id BIGINT NOT NULL,
    access_type VARCHAR(20) NOT NULL DEFAULT 'VIEW',
    target_type VARCHAR(20) NOT NULL DEFAULT 'ROLE',
    target_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    CONSTRAINT fk_cta_common_table FOREIGN KEY (common_table_id)
        REFERENCES dw_common_table_definitions(id) ON DELETE CASCADE,
    CONSTRAINT uk_cta_table_target UNIQUE (common_table_id, target_id)
);

CREATE INDEX IF NOT EXISTS idx_dw_common_table_access_table ON dw_common_table_access(common_table_id);

COMMENT ON TABLE dw_common_table_deployments IS 'Deployment history for common table definitions';
COMMENT ON TABLE dw_common_table_access IS 'Role-based access control for common tables in User Portal';
