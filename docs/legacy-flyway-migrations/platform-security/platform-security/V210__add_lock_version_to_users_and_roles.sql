-- Add optimistic locking column to sys_users and sys_roles
ALTER TABLE sys_users ADD COLUMN IF NOT EXISTS lock_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE sys_roles ADD COLUMN IF NOT EXISTS lock_version BIGINT NOT NULL DEFAULT 0;
