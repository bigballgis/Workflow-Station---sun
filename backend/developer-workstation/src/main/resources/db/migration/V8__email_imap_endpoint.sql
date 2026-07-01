-- Inbound IMAP endpoint for email connections (used when direction is INBOUND/BOTH).
ALTER TABLE dw_email_connections ADD COLUMN IF NOT EXISTS imap_host VARCHAR(255);
ALTER TABLE dw_email_connections ADD COLUMN IF NOT EXISTS imap_port INTEGER;
ALTER TABLE dw_email_connections ADD COLUMN IF NOT EXISTS imap_use_ssl BOOLEAN DEFAULT TRUE;
