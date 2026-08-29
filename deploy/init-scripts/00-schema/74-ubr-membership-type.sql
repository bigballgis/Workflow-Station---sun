-- UBR Member/Leader tier (Claim Hold MVP2) and permission-request membership type.
ALTER TABLE sys_user_business_unit_roles
    ADD COLUMN IF NOT EXISTS membership_type VARCHAR(16) NOT NULL DEFAULT 'MEMBER';

COMMENT ON COLUMN sys_user_business_unit_roles.membership_type IS
    'MEMBER (claim/unclaim own holds) or LEADER (also force-unclaim others in this BU+Role)';

CREATE INDEX IF NOT EXISTS idx_ubr_bu_role_membership
    ON sys_user_business_unit_roles (business_unit_id, role_id, membership_type);

ALTER TABLE up_permission_request
    ADD COLUMN IF NOT EXISTS membership_type VARCHAR(16) NOT NULL DEFAULT 'MEMBER';

COMMENT ON COLUMN up_permission_request.membership_type IS
    'Requested UBR tier for BUSINESS_UNIT_JOIN: MEMBER or LEADER';
