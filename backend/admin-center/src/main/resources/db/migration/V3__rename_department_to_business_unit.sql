-- V3: Rename Department to Business Unit
-- This migration renames sys_departments to sys_business_units and updates all related references

-- Step 1: Rename the main table
ALTER TABLE IF EXISTS sys_departments RENAME TO sys_business_units;

-- Step 2: Update foreign key constraints in sys_users table
-- First drop the existing constraint, then recreate with new name
ALTER TABLE sys_users DROP CONSTRAINT IF EXISTS fk_user_department;
ALTER TABLE sys_users ADD CONSTRAINT fk_user_business_unit 
    FOREIGN KEY (department_id) REFERENCES sys_business_units(id);

-- Step 3: Rename the column in sys_users from department_id to business_unit_id
ALTER TABLE sys_users RENAME COLUMN department_id TO business_unit_id;

-- Step 4: Update any indexes that reference the old table name
DROP INDEX IF EXISTS idx_departments_parent_id;
DROP INDEX IF EXISTS idx_departments_code;
DROP INDEX IF EXISTS idx_departments_status;

CREATE INDEX IF NOT EXISTS idx_business_units_parent_id ON sys_business_units(parent_id);
CREATE INDEX IF NOT EXISTS idx_business_units_code ON sys_business_units(code);
CREATE INDEX IF NOT EXISTS idx_business_units_status ON sys_business_units(status);

-- Step 5: Update sequences if any
-- ALTER SEQUENCE IF EXISTS sys_departments_id_seq RENAME TO sys_business_units_id_seq;

-- Note: Application code changes are required to use the new table and column names
