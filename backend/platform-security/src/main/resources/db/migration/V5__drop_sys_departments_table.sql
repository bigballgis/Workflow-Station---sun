-- V5: Drop deprecated sys_departments table
-- Data has been migrated to sys_business_units table
-- This table is no longer used by any code

-- Drop the deprecated sys_departments table
DROP TABLE IF EXISTS sys_departments CASCADE;
