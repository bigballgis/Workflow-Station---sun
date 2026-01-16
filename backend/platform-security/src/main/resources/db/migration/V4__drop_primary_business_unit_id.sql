-- =====================================================
-- V4: Drop primary_business_unit_id from sys_users
-- Users have many-to-many relationship with business units
-- via sys_user_business_units table, no need for primary BU
-- =====================================================

-- Drop the index first
DROP INDEX IF EXISTS idx_sys_users_primary_business_unit;

-- Drop the column
ALTER TABLE sys_users DROP COLUMN IF EXISTS primary_business_unit_id;
