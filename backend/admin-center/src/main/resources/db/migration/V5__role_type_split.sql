-- V5: Split BUSINESS role type into BU_BOUNDED and BU_UNBOUNDED
-- This migration updates existing BUSINESS roles to BU_BOUNDED (default)

-- Update existing BUSINESS roles to BU_BOUNDED
UPDATE sys_roles SET type = 'BU_BOUNDED' WHERE type = 'BUSINESS';

-- Add comment for documentation
COMMENT ON COLUMN sys_roles.type IS 'Role type: BU_BOUNDED (requires business unit), BU_UNBOUNDED (independent), ADMIN, DEVELOPER';
