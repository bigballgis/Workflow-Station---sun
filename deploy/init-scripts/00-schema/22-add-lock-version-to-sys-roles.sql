-- =====================================================
-- sys_roles: optimistic locking (lock_version)
-- Aligns with com.platform.security.entity.Role @Version
-- and platform-security Flyway V210__add_lock_version_to_users_and_roles.sql
-- (sys_users.lock_version is already in 01-platform-security-schema.sql)
-- =====================================================

ALTER TABLE sys_roles
    ADD COLUMN IF NOT EXISTS lock_version BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN sys_roles.lock_version IS 'Optimistic locking version';
