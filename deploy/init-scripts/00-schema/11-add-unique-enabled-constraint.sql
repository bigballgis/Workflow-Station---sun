-- Add unique constraint: ensure only one enabled version per function unit code
-- Date: 2026-02-06
-- Purpose: Prevent multiple versions of the same function unit from being enabled simultaneously

-- Create unique partial index
-- Unique constraint only applies when enabled = true
CREATE UNIQUE INDEX IF NOT EXISTS idx_function_unit_code_enabled 
ON sys_function_units (code) 
WHERE enabled = true;

-- Verify constraint
-- Query enabled version count per code, should all be 0 or 1
SELECT code, COUNT(*) as enabled_count
FROM sys_function_units
WHERE enabled = true
GROUP BY code
HAVING COUNT(*) > 1;

-- If the above query returns any results, there is data violating the constraint
-- Fix the data first, then create the constraint

COMMENT ON INDEX idx_function_unit_code_enabled IS 'Ensure only one enabled version per function unit code';
