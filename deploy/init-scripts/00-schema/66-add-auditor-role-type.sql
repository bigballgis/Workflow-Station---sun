-- Allow sys_roles.type = 'AUDITOR' independently of ADMIN.
-- Must run before any INSERT/UPDATE that stores type = 'AUDITOR'.

ALTER TABLE sys_roles DROP CONSTRAINT IF EXISTS chk_role_type;

ALTER TABLE sys_roles
    ADD CONSTRAINT chk_role_type
    CHECK (type IN ('ADMIN', 'AUDITOR', 'DEVELOPER', 'BU_BOUNDED', 'BU_UNBOUNDED'));

COMMENT ON COLUMN sys_roles.type IS
    'ADMIN (system administration), AUDITOR (read-only audit), DEVELOPER (design studio), BU_BOUNDED / BU_UNBOUNDED (business roles)';
