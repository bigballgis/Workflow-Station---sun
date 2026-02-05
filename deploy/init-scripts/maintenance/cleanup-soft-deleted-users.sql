-- Cleanup script for permanently deleting soft-deleted users
-- This script removes soft-deleted users and their related data from the database
-- 
-- WARNING: This operation is irreversible!
-- Use with caution in production environments.

-- Step 1: Delete password history for soft-deleted users
DELETE FROM admin_password_history 
WHERE user_id IN (SELECT id FROM sys_users WHERE deleted = true);

-- Step 2: Delete user role assignments for soft-deleted users
DELETE FROM sys_user_roles 
WHERE user_id IN (SELECT id FROM sys_users WHERE deleted = true);

-- Step 3: Delete user business unit associations for soft-deleted users
DELETE FROM sys_user_business_units 
WHERE user_id IN (SELECT id FROM sys_users WHERE deleted = true);

-- Step 4: Delete the soft-deleted users
DELETE FROM sys_users WHERE deleted = true;

-- Verify cleanup
SELECT 
    (SELECT COUNT(*) FROM sys_users WHERE deleted = true) as remaining_soft_deleted_users,
    (SELECT COUNT(*) FROM sys_users WHERE deleted = false OR deleted IS NULL) as active_users;
