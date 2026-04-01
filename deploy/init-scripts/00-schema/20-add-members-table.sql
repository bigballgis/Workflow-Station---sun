-- =====================================================
-- Developer Workstation: members
-- Aligns with com.developer.entity.Member (@Table members)
-- =====================================================

CREATE TABLE IF NOT EXISTS members (
    id                   BIGSERIAL PRIMARY KEY,
    username             VARCHAR(50)  NOT NULL UNIQUE,
    full_name            VARCHAR(100) NOT NULL,
    email                VARCHAR(255) NOT NULL,
    employee_id          VARCHAR(20),
    business_unit_id     VARCHAR(50),
    business_unit_name   VARCHAR(100),
    role                 VARCHAR(50),
    active               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by           VARCHAR(50),
    updated_by           VARCHAR(50)
);

CREATE INDEX IF NOT EXISTS idx_members_email ON members(email);
CREATE INDEX IF NOT EXISTS idx_members_active ON members(active);

COMMENT ON TABLE members IS 'Developer workstation member directory (separate from sys_users)';
