-- =====================================================
-- V2: Add code column to dw_function_units
-- =====================================================

-- Add code column (nullable first for existing data)
ALTER TABLE dw_function_units ADD COLUMN IF NOT EXISTS code VARCHAR(50);

-- Generate code for existing records
UPDATE dw_function_units 
SET code = 'fu-' || TO_CHAR(created_at, 'YYYYMMDD') || '-' || SUBSTRING(MD5(RANDOM()::TEXT), 1, 6)
WHERE code IS NULL;

-- Make code NOT NULL and add unique constraint
ALTER TABLE dw_function_units ALTER COLUMN code SET NOT NULL;
ALTER TABLE dw_function_units ADD CONSTRAINT uk_function_unit_code UNIQUE (code);

-- Add index for faster lookups
CREATE INDEX IF NOT EXISTS idx_function_unit_code ON dw_function_units(code);
