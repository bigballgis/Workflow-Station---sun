-- Inbound IMAP endpoint synced from developer-workstation (used when direction is INBOUND/BOTH).
ALTER TABLE sys_email_connections ADD COLUMN IF NOT EXISTS imap_host VARCHAR(255);
ALTER TABLE sys_email_connections ADD COLUMN IF NOT EXISTS imap_port INTEGER;
ALTER TABLE sys_email_connections ADD COLUMN IF NOT EXISTS imap_use_ssl BOOLEAN DEFAULT TRUE;
