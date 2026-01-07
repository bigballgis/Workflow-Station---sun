-- =====================================================
-- Add soft delete fields to admin_users table
-- Validates: Requirements 5.1
-- =====================================================

-- Add soft delete columns
ALTER TABLE admin_users ADD COLUMN IF NOT EXISTS deleted BOOLEAN DEFAULT FALSE;
ALTER TABLE admin_users ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;
ALTER TABLE admin_users ADD COLUMN IF NOT EXISTS deleted_by VARCHAR(64);

-- Create index for soft delete queries
CREATE INDEX IF NOT EXISTS idx_admin_users_deleted ON admin_users(deleted);

-- Update existing records to have deleted = false
UPDATE admin_users SET deleted = FALSE WHERE deleted IS NULL;

-- Add NOT NULL constraint after setting default values
ALTER TABLE admin_users ALTER COLUMN deleted SET NOT NULL;

-- Add comment
COMMENT ON COLUMN admin_users.deleted IS 'Soft delete flag';
COMMENT ON COLUMN admin_users.deleted_at IS 'Timestamp when user was deleted';
COMMENT ON COLUMN admin_users.deleted_by IS 'User ID who performed the deletion';
