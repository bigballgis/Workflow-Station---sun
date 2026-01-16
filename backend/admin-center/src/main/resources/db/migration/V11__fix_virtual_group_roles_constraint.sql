-- V11: Fix virtual group roles constraint
-- A virtual group should be able to have MULTIPLE roles, not just one.
-- This migration removes the incorrect single-role constraint and adds the correct one.

-- Drop the incorrect unique constraint (single role per virtual group)
ALTER TABLE sys_virtual_group_roles DROP CONSTRAINT IF EXISTS uk_virtual_group_role_vg;

-- Add the correct unique constraint (virtual_group_id + role_id combination must be unique)
-- This allows a virtual group to have multiple different roles
ALTER TABLE sys_virtual_group_roles 
ADD CONSTRAINT uk_virtual_group_role UNIQUE (virtual_group_id, role_id);

COMMENT ON TABLE sys_virtual_group_roles IS 'Virtual group role binding - each virtual group can have multiple roles, but each role can only be bound once per group';
