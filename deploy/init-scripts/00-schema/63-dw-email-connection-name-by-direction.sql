-- Same mailbox email may exist once per direction (OUTBOUND send vs INBOUND monitor) within a Function Unit.

ALTER TABLE dw_email_connections DROP CONSTRAINT IF EXISTS dw_email_connections_function_unit_id_name_key;

CREATE UNIQUE INDEX IF NOT EXISTS uk_dw_email_conn_fu_name_direction
    ON dw_email_connections (function_unit_id, name, direction);

ALTER TABLE sys_email_connections DROP CONSTRAINT IF EXISTS sys_email_connections_function_unit_id_name_key;

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_email_conn_fu_name_direction
    ON sys_email_connections (function_unit_id, name, direction);
