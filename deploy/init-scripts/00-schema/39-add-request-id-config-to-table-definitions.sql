-- Migration: Add request_id_config column to dw_table_definitions
-- JPA entity TableDefinition.requestIdConfig was added but existing DBs lack the column.

ALTER TABLE dw_table_definitions
    ADD COLUMN IF NOT EXISTS request_id_config JSONB;

COMMENT ON COLUMN dw_table_definitions.request_id_config IS 'Request ID generation configuration for auto-generated IDs';
