-- Post-seed: Email Inbound Reply (fu-20260910-emlrep) → Public dev group.
-- The UAT seed 20-email-inbound-reply/init.sql creates the function unit and is not
-- part of DEV docker init. Run this after that seed (or on a fresh init where the
-- unit already exists). No-op when the unit is absent. Idempotent.

INSERT INTO dw_function_unit_dev_groups (function_unit_id, virtual_group_id, created_at, created_by)
SELECT fu.id, 'vg-dev-public', CURRENT_TIMESTAMP, 'system'
FROM dw_function_units fu
WHERE fu.code = 'fu-20260910-emlrep'
ON CONFLICT (function_unit_id, virtual_group_id) DO NOTHING;
