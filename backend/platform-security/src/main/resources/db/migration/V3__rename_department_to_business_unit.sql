-- =====================================================
-- V3: Rename department_id to primary_business_unit_id
-- Migration from Department to BusinessUnit model
-- =====================================================

-- 1. Drop the foreign key constraint on department_id
ALTER TABLE sys_users DROP CONSTRAINT IF EXISTS fk_user_department;

-- 2. Drop the index on department_id
DROP INDEX IF EXISTS idx_sys_users_department;

-- 3. Rename the column
ALTER TABLE sys_users RENAME COLUMN department_id TO primary_business_unit_id;

-- 4. Create new index on primary_business_unit_id
CREATE INDEX IF NOT EXISTS idx_sys_users_primary_business_unit ON sys_users(primary_business_unit_id);

-- 5. Add comment for the renamed column
COMMENT ON COLUMN sys_users.primary_business_unit_id IS 'User primary business unit ID (migrated from department_id)';
