-- Align with Flyway V6 (developer-workstation): email templates per function unit
CREATE TABLE IF NOT EXISTS dw_email_templates (
    id BIGSERIAL PRIMARY KEY,
    function_unit_id BIGINT NOT NULL REFERENCES dw_function_units(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    subject VARCHAR(500),
    body_html TEXT,
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(function_unit_id, name)
);

CREATE INDEX IF NOT EXISTS idx_dw_email_tpl_fu ON dw_email_templates(function_unit_id);

COMMENT ON TABLE dw_email_templates IS 'Per–Function Unit email templates (HTML body + variable placeholders for Send Tasks)';
