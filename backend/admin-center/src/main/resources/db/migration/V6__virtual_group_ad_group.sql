-- V6: Add ad_group column to sys_virtual_groups table
-- This column is used for Active Directory integration

ALTER TABLE sys_virtual_groups ADD COLUMN IF NOT EXISTS ad_group VARCHAR(100);

COMMENT ON COLUMN sys_virtual_groups.ad_group IS 'AD Group name for Active Directory integration';
