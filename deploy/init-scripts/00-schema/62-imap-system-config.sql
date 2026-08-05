-- Global IMAP system config keys for Admin Center System Parameters (email monitor inbound polling).

INSERT INTO admin_system_configs (
    id, category, config_key, config_name, config_value, default_value,
    value_type, description, encrypted, editable, version, environment
) VALUES
    (gen_random_uuid()::text, 'SYSTEM', 'imap.host', 'IMAP Host', '', '',
     'STRING', 'Global IMAP host for inbound email monitor connections', FALSE, TRUE, 1, 'DEV'),
    (gen_random_uuid()::text, 'SYSTEM', 'imap.port', 'IMAP Port', '993', '993',
     'INTEGER', 'Global IMAP port for inbound email monitor connections', FALSE, TRUE, 1, 'DEV'),
    (gen_random_uuid()::text, 'SYSTEM', 'imap.useSsl', 'IMAP Use SSL', 'true', 'true',
     'BOOLEAN', 'Global IMAP SSL (imaps) for inbound email monitor connections', FALSE, TRUE, 1, 'DEV')
ON CONFLICT (config_key) DO NOTHING;
