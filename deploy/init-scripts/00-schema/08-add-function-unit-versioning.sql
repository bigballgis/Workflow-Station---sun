-- =====================================================
-- Function Unit Versioning Migration
-- Adds version tracking columns to function unit tables
-- Requirements: 8.1, 8.2, 8.3, 8.4, 8.7
-- =====================================================

-- =====================================================
-- 1. Add versioning columns to dw_function_units
-- =====================================================

-- Add version column (semantic version format: MAJOR.MINOR.PATCH)
ALTER TABLE dw_function_units 
ADD COLUMN IF NOT EXISTS version VARCHAR(20) NOT NULL DEFAULT '1.0.0';

-- Add is_active flag (only one version per function unit should be active)
ALTER TABLE dw_function_units 
ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;

-- Add deployed_at timestamp (when this version was deployed)
ALTER TABLE dw_function_units 
ADD COLUMN IF NOT EXISTS deployed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- Add previous_version_id (links to the previous version for version history)
ALTER TABLE dw_function_units 
ADD COLUMN IF NOT EXISTS previous_version_id BIGINT NULL;

-- Add foreign key constraint for previous_version_id
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_dw_function_unit_previous_version') THEN
        ALTER TABLE dw_function_units ADD CONSTRAINT fk_dw_function_unit_previous_version
        FOREIGN KEY (previous_version_id) REFERENCES dw_function_units(id) ON DELETE SET NULL;
    END IF;
END $$;

-- Create index for version queries (function unit name + version)
CREATE INDEX IF NOT EXISTS idx_dw_function_unit_version 
ON dw_function_units(name, version);

-- Create index for active version queries (function unit name + is_active)
CREATE INDEX IF NOT EXISTS idx_dw_function_unit_active 
ON dw_function_units(name, is_active);

-- Create index for deployed_at for sorting by deployment time
CREATE INDEX IF NOT EXISTS idx_dw_function_unit_deployed_at 
ON dw_function_units(deployed_at);

-- Add comments
COMMENT ON COLUMN dw_function_units.version IS 'Semantic version number (MAJOR.MINOR.PATCH)';
COMMENT ON COLUMN dw_function_units.is_active IS 'Whether this version is currently active (only one version per function unit should be active)';
COMMENT ON COLUMN dw_function_units.deployed_at IS 'Timestamp when this version was deployed';
COMMENT ON COLUMN dw_function_units.previous_version_id IS 'Reference to the previous version of this function unit';

-- =====================================================
-- 2. Add versioning columns to sys_function_units
-- =====================================================

-- Note: sys_function_units already has a 'version' column, but we need to ensure it follows semantic versioning
-- We'll add the other versioning columns

-- Add is_active flag
ALTER TABLE sys_function_units 
ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;

-- Add deployed_at timestamp (use imported_at as default if not set)
ALTER TABLE sys_function_units 
ADD COLUMN IF NOT EXISTS deployed_at TIMESTAMP;

-- Set deployed_at to imported_at for existing records
UPDATE sys_function_units 
SET deployed_at = COALESCE(imported_at, created_at, CURRENT_TIMESTAMP)
WHERE deployed_at IS NULL;

-- Make deployed_at NOT NULL after setting values
ALTER TABLE sys_function_units 
ALTER COLUMN deployed_at SET NOT NULL;

-- Add previous_version_id (links to the previous version)
-- Note: sys_function_units uses VARCHAR(64) for id, not BIGINT
ALTER TABLE sys_function_units 
ADD COLUMN IF NOT EXISTS previous_version_id VARCHAR(64) NULL;

-- Add foreign key constraint for previous_version_id
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_sys_function_unit_previous_version') THEN
        ALTER TABLE sys_function_units ADD CONSTRAINT fk_sys_function_unit_previous_version
        FOREIGN KEY (previous_version_id) REFERENCES sys_function_units(id) ON DELETE SET NULL;
    END IF;
END $$;

-- Create index for version queries (function unit name + version)
CREATE INDEX IF NOT EXISTS idx_sys_function_unit_version 
ON sys_function_units(name, version);

-- Create index for active version queries (function unit name + is_active)
CREATE INDEX IF NOT EXISTS idx_sys_function_unit_active 
ON sys_function_units(name, is_active);

-- Create index for deployed_at for sorting by deployment time
CREATE INDEX IF NOT EXISTS idx_sys_function_unit_deployed_at 
ON sys_function_units(deployed_at);

-- Add comments
COMMENT ON COLUMN sys_function_units.is_active IS 'Whether this version is currently active (only one version per function unit should be active)';
COMMENT ON COLUMN sys_function_units.deployed_at IS 'Timestamp when this version was deployed';
COMMENT ON COLUMN sys_function_units.previous_version_id IS 'Reference to the previous version of this function unit';

-- =====================================================
-- 3. Add function_unit_version_id to dw_process_definitions
-- =====================================================

-- Add function_unit_version_id column (links to specific version of function unit)
ALTER TABLE dw_process_definitions 
ADD COLUMN IF NOT EXISTS function_unit_version_id BIGINT;

-- Set function_unit_version_id to current function_unit_id for existing records
-- This assumes existing records should link to the active version
UPDATE dw_process_definitions pd
SET function_unit_version_id = pd.function_unit_id
WHERE function_unit_version_id IS NULL;

-- Make function_unit_version_id NOT NULL after setting values
ALTER TABLE dw_process_definitions 
ALTER COLUMN function_unit_version_id SET NOT NULL;

-- Add foreign key constraint
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_dw_process_def_function_unit_version') THEN
        ALTER TABLE dw_process_definitions ADD CONSTRAINT fk_dw_process_def_function_unit_version
        FOREIGN KEY (function_unit_version_id) REFERENCES dw_function_units(id) ON DELETE CASCADE;
    END IF;
END $$;

-- Create index for version queries
CREATE INDEX IF NOT EXISTS idx_dw_process_def_version 
ON dw_process_definitions(function_unit_version_id);

-- Add comment
COMMENT ON COLUMN dw_process_definitions.function_unit_version_id IS 'Reference to the specific version of the function unit this process definition belongs to';

-- =====================================================
-- 4. Add function_unit_version_id to up_process_instance
-- =====================================================

-- Add function_unit_version_id column (links to specific version of function unit)
-- Note: up_process_instance uses VARCHAR(64) for id
ALTER TABLE up_process_instance 
ADD COLUMN IF NOT EXISTS function_unit_version_id BIGINT;

-- Note: We cannot automatically populate this for existing records without knowing
-- which function unit they belong to. This will need to be handled by a separate
-- data migration script after the schema is in place.

-- Add comment
COMMENT ON COLUMN up_process_instance.function_unit_version_id IS 'Reference to the specific version of the function unit this process instance is bound to';

-- Create index for version queries
CREATE INDEX IF NOT EXISTS idx_up_process_instance_version 
ON up_process_instance(function_unit_version_id);

-- =====================================================
-- Migration Complete
-- =====================================================

-- Print success message
DO $$
BEGIN
    RAISE NOTICE 'Function unit versioning migration completed successfully';
    RAISE NOTICE 'Added version tracking columns to dw_function_units and sys_function_units';
    RAISE NOTICE 'Added function_unit_version_id to dw_process_definitions and up_process_instance';
    RAISE NOTICE 'Created indexes for performance optimization';
END $$;
