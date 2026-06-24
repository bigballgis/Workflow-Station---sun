-- Patch: MAIN table Request ID config for fu-20260422-23tfag (Multi-Instance Subtask Demo).
-- Idempotent — safe on DBs seeded before request_id_config was included in 00-init-kk.sql.
UPDATE dw_table_definitions
SET request_id_config = $json${"fieldNames":["I","id"],"separator":"_"}$json$::jsonb,
    updated_at = CURRENT_TIMESTAMP
WHERE function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'fu-20260422-23tfag' LIMIT 1)
  AND table_type = 'MAIN'
  AND table_name = 'main'
  AND (request_id_config IS NULL OR request_id_config = 'null'::jsonb);
