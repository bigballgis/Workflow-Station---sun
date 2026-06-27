-- Audit log: persist operation duration and HTTP request metadata for Admin Center UI
ALTER TABLE admin_audit_logs ADD COLUMN IF NOT EXISTS duration_ms INTEGER;
ALTER TABLE admin_audit_logs ADD COLUMN IF NOT EXISTS request_method VARCHAR(10);
ALTER TABLE admin_audit_logs ADD COLUMN IF NOT EXISTS request_path VARCHAR(500);
