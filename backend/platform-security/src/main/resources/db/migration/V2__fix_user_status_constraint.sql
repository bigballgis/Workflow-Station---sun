-- =====================================================
-- V2: Fix user status constraint and clean up duplicate indexes
-- Date: 2026-01-22
-- Purpose: Update sys_users CHECK constraint to match admin-center UserStatus enum
-- =====================================================

-- Step 1: Drop existing CHECK constraint
ALTER TABLE sys_users DROP CONSTRAINT IF EXISTS chk_sys_user_status;

-- Step 2: Add updated CHECK constraint with 4 values (INACTIVE removed)
-- admin-center uses: ACTIVE, DISABLED, LOCKED, PENDING
-- platform-security uses: ACTIVE, INACTIVE, LOCKED (INACTIVE not in database)
ALTER TABLE sys_users ADD CONSTRAINT chk_sys_user_status 
    CHECK (status IN ('ACTIVE', 'DISABLED', 'LOCKED', 'PENDING'));

-- Step 3: Add comment explaining status values
COMMENT ON COLUMN sys_users.status IS 'User status: ACTIVE (can login), DISABLED (disabled by admin), LOCKED (security lock), PENDING (pending activation)';

-- Step 4: Clean up duplicate indexes (keep idx_sys_users_* series, remove idx_user_* series)
DROP INDEX IF EXISTS idx_user_username;
DROP INDEX IF EXISTS idx_user_email;
DROP INDEX IF EXISTS idx_user_status;

-- Step 5: Ensure correct indexes exist (idempotent)
CREATE INDEX IF NOT EXISTS idx_sys_users_username ON sys_users(username);
CREATE INDEX IF NOT EXISTS idx_sys_users_email ON sys_users(email);
CREATE INDEX IF NOT EXISTS idx_sys_users_status ON sys_users(status);
CREATE INDEX IF NOT EXISTS idx_sys_users_employee_id ON sys_users(employee_id);
CREATE INDEX IF NOT EXISTS idx_sys_users_entity_manager ON sys_users(entity_manager_id);
CREATE INDEX IF NOT EXISTS idx_sys_users_function_manager ON sys_users(function_manager_id);
CREATE INDEX IF NOT EXISTS idx_sys_users_deleted ON sys_users(deleted);

-- Step 6: Verify no users have invalid status (should be empty)
DO $$
DECLARE
    invalid_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO invalid_count 
    FROM sys_users 
    WHERE status NOT IN ('ACTIVE', 'DISABLED', 'LOCKED', 'PENDING');
    
    IF invalid_count > 0 THEN
        RAISE NOTICE 'Warning: % users have invalid status values', invalid_count;
        -- Log the invalid users
        RAISE NOTICE 'Invalid users: %', (
            SELECT string_agg(username || ':' || status, ', ')
            FROM sys_users 
            WHERE status NOT IN ('ACTIVE', 'DISABLED', 'LOCKED', 'PENDING')
        );
    ELSE
        RAISE NOTICE 'All users have valid status values';
    END IF;
END $$;
