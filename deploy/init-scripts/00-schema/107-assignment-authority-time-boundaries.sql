-- Keep temporal-fence queries bounded to nearest role validity boundaries.
CREATE INDEX IF NOT EXISTS idx_assignment_role_valid_from ON sys_role_assignments(valid_from) WHERE valid_from IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_assignment_role_valid_to ON sys_role_assignments(valid_to) WHERE valid_to IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_assignment_user_role_valid_from ON sys_user_roles(valid_from) WHERE valid_from IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_assignment_user_role_valid_to ON sys_user_roles(valid_to) WHERE valid_to IS NOT NULL;
