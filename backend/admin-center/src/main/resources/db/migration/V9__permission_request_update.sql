-- V9: Update permission request for new business unit application flow
-- - Update request_type from BUSINESS_UNIT_ROLE to BUSINESS_UNIT
-- - Keep role_ids column for backward compatibility but mark as deprecated

-- Update existing BUSINESS_UNIT_ROLE requests to BUSINESS_UNIT
UPDATE sys_permission_requests 
SET request_type = 'BUSINESS_UNIT' 
WHERE request_type = 'BUSINESS_UNIT_ROLE';

-- Add comment to indicate role_ids is deprecated
COMMENT ON COLUMN sys_permission_requests.role_ids IS 'Deprecated: Roles are now obtained through virtual groups. Kept for backward compatibility.';

-- Add comment to table
COMMENT ON TABLE sys_permission_requests IS 'Permission requests. VIRTUAL_GROUP for joining virtual groups, BUSINESS_UNIT for joining business units.';
