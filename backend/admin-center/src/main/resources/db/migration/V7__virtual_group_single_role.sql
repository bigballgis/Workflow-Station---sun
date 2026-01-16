-- V7: Enforce single role per virtual group
-- This migration adds a unique constraint on virtual_group_id and cleans up duplicate bindings

-- First, remove duplicate bindings (keep only the first one for each virtual group)
DELETE FROM sys_virtual_group_roles 
WHERE id NOT IN (
    SELECT MIN(id) 
    FROM sys_virtual_group_roles 
    GROUP BY virtual_group_id
);

-- Drop the old unique constraint if exists
ALTER TABLE sys_virtual_group_roles DROP CONSTRAINT IF EXISTS sys_virtual_group_roles_virtual_group_id_role_id_key;

-- Add new unique constraint on virtual_group_id only (single role per virtual group)
ALTER TABLE sys_virtual_group_roles 
ADD CONSTRAINT uk_virtual_group_role_vg UNIQUE (virtual_group_id);

COMMENT ON TABLE sys_virtual_group_roles IS 'Virtual group role binding - each virtual group can only bind one role';
