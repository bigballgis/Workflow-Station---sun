-- Email monitor template vs Start Event binding: templates hold mailbox + extraction;
-- bindings reference a template and store per–Start Event filters + process key.

ALTER TABLE dw_email_monitor_rules
    ADD COLUMN IF NOT EXISTS source_rule_id BIGINT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_dw_email_monitor_source_rule'
    ) THEN
        ALTER TABLE dw_email_monitor_rules
            ADD CONSTRAINT fk_dw_email_monitor_source_rule
                FOREIGN KEY (source_rule_id) REFERENCES dw_email_monitor_rules(id) ON DELETE RESTRICT;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_dw_email_monitor_source
    ON dw_email_monitor_rules(source_rule_id);

COMMENT ON COLUMN dw_email_monitor_rules.source_rule_id IS
    'When set, this row is a Start Event binding that inherits mailbox/extraction from the template rule id; filters and process key live on the binding.';
