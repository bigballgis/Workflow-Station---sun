-- V8: Create user business units table (user membership without role)
-- This table tracks which business units a user belongs to
-- Users get roles through virtual groups, and activate BU-Bounded roles by joining business units

-- Create user business units table
CREATE TABLE IF NOT EXISTS sys_user_business_units (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    business_unit_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    CONSTRAINT fk_ubu_user FOREIGN KEY (user_id) REFERENCES sys_users(id) ON DELETE CASCADE,
    CONSTRAINT fk_ubu_business_unit FOREIGN KEY (business_unit_id) REFERENCES sys_business_units(id) ON DELETE CASCADE,
    CONSTRAINT uk_user_business_unit UNIQUE (user_id, business_unit_id)
);

-- Create indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_ubu_user_id ON sys_user_business_units(user_id);
CREATE INDEX IF NOT EXISTS idx_ubu_business_unit_id ON sys_user_business_units(business_unit_id);

-- Migrate existing data from sys_user_business_unit_roles to sys_user_business_units
-- Each unique (user_id, business_unit_id) pair becomes a membership record
INSERT INTO sys_user_business_units (id, user_id, business_unit_id, created_at, created_by)
SELECT 
    CONCAT('ubu-', SUBSTRING(MD5(CONCAT(user_id, '-', business_unit_id)), 1, 32)),
    user_id,
    business_unit_id,
    MIN(created_at),
    MIN(created_by)
FROM sys_user_business_unit_roles
GROUP BY user_id, business_unit_id
ON CONFLICT (user_id, business_unit_id) DO NOTHING;

-- Add comment to table
COMMENT ON TABLE sys_user_business_units IS 'User business unit membership (without role). Users get roles through virtual groups.';
COMMENT ON COLUMN sys_user_business_units.user_id IS 'User ID';
COMMENT ON COLUMN sys_user_business_units.business_unit_id IS 'Business unit ID';
