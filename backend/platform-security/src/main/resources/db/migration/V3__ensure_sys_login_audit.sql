-- =====================================================
-- Ensure sys_login_audit table exists
-- =====================================================
-- This migration ensures the sys_login_audit table exists
-- even if it was not created in previous migrations
-- This is important because platform-security scans entities
-- and expects this table to exist

DO $$
BEGIN
    -- Check if table exists
    IF NOT EXISTS (
        SELECT 1 
        FROM information_schema.tables 
        WHERE table_schema = 'public' 
        AND table_name = 'sys_login_audit'
    ) THEN
        -- Create the table if it doesn't exist
        CREATE TABLE sys_login_audit (
            id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            user_id VARCHAR(64),
            username VARCHAR(50) NOT NULL,
            action VARCHAR(20) NOT NULL,
            ip_address VARCHAR(45),
            user_agent TEXT,
            success BOOLEAN DEFAULT true,
            failure_reason VARCHAR(255),
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        );

        -- Create indexes
        CREATE INDEX IF NOT EXISTS idx_login_audit_user ON sys_login_audit(user_id);
        CREATE INDEX IF NOT EXISTS idx_login_audit_username ON sys_login_audit(username);
        CREATE INDEX IF NOT EXISTS idx_login_audit_created ON sys_login_audit(created_at);
        CREATE INDEX IF NOT EXISTS idx_login_audit_action ON sys_login_audit(action);

        RAISE NOTICE 'Created sys_login_audit table';
    ELSE
        -- Table exists, verify structure and add missing columns/indexes
        -- Add missing columns if they don't exist
        IF NOT EXISTS (
            SELECT 1 
            FROM information_schema.columns 
            WHERE table_schema = 'public' 
            AND table_name = 'sys_login_audit' 
            AND column_name = 'id'
        ) THEN
            ALTER TABLE sys_login_audit ADD COLUMN id UUID PRIMARY KEY DEFAULT uuid_generate_v4();
        END IF;

        IF NOT EXISTS (
            SELECT 1 
            FROM information_schema.columns 
            WHERE table_schema = 'public' 
            AND table_name = 'sys_login_audit' 
            AND column_name = 'user_id'
        ) THEN
            ALTER TABLE sys_login_audit ADD COLUMN user_id VARCHAR(64);
        END IF;

        IF NOT EXISTS (
            SELECT 1 
            FROM information_schema.columns 
            WHERE table_schema = 'public' 
            AND table_name = 'sys_login_audit' 
            AND column_name = 'username'
        ) THEN
            ALTER TABLE sys_login_audit ADD COLUMN username VARCHAR(50) NOT NULL DEFAULT '';
        END IF;

        IF NOT EXISTS (
            SELECT 1 
            FROM information_schema.columns 
            WHERE table_schema = 'public' 
            AND table_name = 'sys_login_audit' 
            AND column_name = 'action'
        ) THEN
            ALTER TABLE sys_login_audit ADD COLUMN action VARCHAR(20) NOT NULL DEFAULT 'LOGIN';
        END IF;

        IF NOT EXISTS (
            SELECT 1 
            FROM information_schema.columns 
            WHERE table_schema = 'public' 
            AND table_name = 'sys_login_audit' 
            AND column_name = 'ip_address'
        ) THEN
            ALTER TABLE sys_login_audit ADD COLUMN ip_address VARCHAR(45);
        END IF;

        IF NOT EXISTS (
            SELECT 1 
            FROM information_schema.columns 
            WHERE table_schema = 'public' 
            AND table_name = 'sys_login_audit' 
            AND column_name = 'user_agent'
        ) THEN
            ALTER TABLE sys_login_audit ADD COLUMN user_agent TEXT;
        END IF;

        IF NOT EXISTS (
            SELECT 1 
            FROM information_schema.columns 
            WHERE table_schema = 'public' 
            AND table_name = 'sys_login_audit' 
            AND column_name = 'success'
        ) THEN
            ALTER TABLE sys_login_audit ADD COLUMN success BOOLEAN DEFAULT true;
        END IF;

        IF NOT EXISTS (
            SELECT 1 
            FROM information_schema.columns 
            WHERE table_schema = 'public' 
            AND table_name = 'sys_login_audit' 
            AND column_name = 'failure_reason'
        ) THEN
            ALTER TABLE sys_login_audit ADD COLUMN failure_reason VARCHAR(255);
        END IF;

        IF NOT EXISTS (
            SELECT 1 
            FROM information_schema.columns 
            WHERE table_schema = 'public' 
            AND table_name = 'sys_login_audit' 
            AND column_name = 'created_at'
        ) THEN
            ALTER TABLE sys_login_audit ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
        END IF;

        -- Create missing indexes
        CREATE INDEX IF NOT EXISTS idx_login_audit_user ON sys_login_audit(user_id);
        CREATE INDEX IF NOT EXISTS idx_login_audit_username ON sys_login_audit(username);
        CREATE INDEX IF NOT EXISTS idx_login_audit_created ON sys_login_audit(created_at);
        CREATE INDEX IF NOT EXISTS idx_login_audit_action ON sys_login_audit(action);

        RAISE NOTICE 'Verified and updated sys_login_audit table structure';
    END IF;
END $$;

COMMENT ON TABLE sys_login_audit IS 'Login/logout audit trail for authentication events';
COMMENT ON COLUMN sys_login_audit.id IS 'Primary key (UUID)';
COMMENT ON COLUMN sys_login_audit.user_id IS 'User ID (nullable for failed login attempts)';
COMMENT ON COLUMN sys_login_audit.username IS 'Username';
COMMENT ON COLUMN sys_login_audit.action IS 'Action type: LOGIN, LOGOUT, REFRESH, TOKEN_REFRESH';
COMMENT ON COLUMN sys_login_audit.success IS 'Whether the operation was successful';
COMMENT ON COLUMN sys_login_audit.failure_reason IS 'Reason for failure (if success = false)';
COMMENT ON COLUMN sys_login_audit.created_at IS 'Timestamp when the event occurred';
