-- Add enabled field to dw_function_units table
-- Date: 2026-02-07
-- Purpose: Implement version management, allow enabling/disabling specific versions

-- Add enabled field, default true
ALTER TABLE dw_function_units 
ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT true;

-- Create index for query performance
CREATE INDEX IF NOT EXISTS idx_dw_function_units_enabled 
ON dw_function_units(enabled);

-- Create unique partial index: ensure only one enabled version per function unit code
CREATE UNIQUE INDEX IF NOT EXISTS idx_dw_function_unit_code_enabled 
ON dw_function_units (code) 
WHERE enabled = true;

-- Verify constraint
-- Query enabled version count per code, should all be 0 or 1
SELECT code, COUNT(*) as enabled_count
FROM dw_function_units
WHERE enabled = true
GROUP BY code
HAVING COUNT(*) > 1;

-- Add comments
COMMENT ON COLUMN dw_function_units.enabled IS 'Whether this version is enabled (only enabled versions are visible to users)';
COMMENT ON INDEX idx_dw_function_unit_code_enabled IS 'Ensure only one enabled version per function unit code';
