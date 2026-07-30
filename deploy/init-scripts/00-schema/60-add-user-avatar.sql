-- Add avatar column to sys_users for storing LDAP jpegPhoto
ALTER TABLE sys_users ADD COLUMN IF NOT EXISTS avatar BYTEA;
