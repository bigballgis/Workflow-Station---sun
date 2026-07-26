-- Global SMTP system config keys for Admin Center System Parameters.
-- Idempotent seed + migrate legacy smtp.server → smtp.host when host is empty.

INSERT INTO admin_system_configs (
    id, category, config_key, config_name, config_value, default_value,
    value_type, description, encrypted, editable, version, environment
) VALUES
    (gen_random_uuid()::text, 'SYSTEM', 'session.timeout', 'Session Timeout', '30', '30',
     'INTEGER', 'Session timeout in minutes', FALSE, TRUE, 1, 'DEV'),
    (gen_random_uuid()::text, 'SYSTEM', 'file.maxSize', 'File Upload Limit', '10', '10',
     'INTEGER', 'File upload limit in MB', FALSE, TRUE, 1, 'DEV'),
    (gen_random_uuid()::text, 'SYSTEM', 'smtp.host', 'SMTP Host', '', '',
     'STRING', 'Global SMTP host for outbound email connections', FALSE, TRUE, 1, 'DEV'),
    (gen_random_uuid()::text, 'SYSTEM', 'smtp.port', 'SMTP Port', '25', '25',
     'INTEGER', 'Global SMTP port for outbound email connections', FALSE, TRUE, 1, 'DEV'),
    (gen_random_uuid()::text, 'SYSTEM', 'smtp.useTls', 'SMTP Use TLS', 'true', 'true',
     'BOOLEAN', 'Global SMTP TLS for outbound email connections', FALSE, TRUE, 1, 'DEV'),
    (gen_random_uuid()::text, 'BUSINESS', 'process.timeout', 'Process Timeout', '7', '7',
     'INTEGER', 'Process timeout in days', FALSE, TRUE, 1, 'DEV'),
    (gen_random_uuid()::text, 'BUSINESS', 'task.assignRule', 'Task Assignment Rule', 'ROUND_ROBIN', 'ROUND_ROBIN',
     'STRING', 'Default task assignment rule', FALSE, TRUE, 1, 'DEV')
ON CONFLICT (config_key) DO NOTHING;

-- Copy legacy Mail Server value into smtp.host when host is still empty.
UPDATE admin_system_configs AS host_cfg
SET config_value = legacy.config_value,
    updated_at = CURRENT_TIMESTAMP
FROM admin_system_configs AS legacy
WHERE host_cfg.config_key = 'smtp.host'
  AND legacy.config_key = 'smtp.server'
  AND (host_cfg.config_value IS NULL OR btrim(host_cfg.config_value) = '')
  AND legacy.config_value IS NOT NULL
  AND btrim(legacy.config_value) <> '';
