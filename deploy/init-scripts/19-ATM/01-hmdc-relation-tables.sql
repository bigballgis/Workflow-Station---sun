-- =============================================================================
-- ATM lookup dictionaries (UAT relation table ids 14 / 28 / 29 / 30).
-- ATM forms bind lookups to these ids (hmdc_dropdown, hmdc_correspondence,
-- hmdc_case_status, hmdc_case_stage_definition). Dev previously only had
-- meeting_room / test, so Case Stage / Dispute Reason / Correspondence were empty.
-- Idempotent. Rows live in rt_table_data_rows (no physical per-table tables).
-- Refuses to run when those ids already belong to a different table_name.
-- =============================================================================

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM rt_table_definitions t
        WHERE t.id IN (14, 28, 29, 30)
          AND t.table_name NOT IN (
              'hmdc_dropdown',
              'hmdc_correspondence',
              'hmdc_case_status',
              'hmdc_case_stage_definition')
    ) THEN
        RAISE EXCEPTION
            'ATM HMDC seed aborted: rt_table_definitions id 14/28/29/30 already used by another table_name';
    END IF;
    IF EXISTS (
        SELECT 1
        FROM rt_table_definitions t
        WHERE t.table_name IN (
              'hmdc_dropdown',
              'hmdc_correspondence',
              'hmdc_case_status',
              'hmdc_case_stage_definition')
          AND t.id NOT IN (14, 28, 29, 30)
    ) THEN
        RAISE EXCEPTION
            'ATM HMDC seed aborted: expected HMDC table_name exists under a different id';
    END IF;
END $$;

-- rt_table_data_rows.id is BIGSERIAL. 17-Multi-Instance-Subtask-Demo's 00-init-kk.sql (which runs
-- before this package) already inserts explicit-id rows into rt_table_data_rows without advancing
-- the sequence (see 90-post-seed/00-align-id-sequences.sql, which only runs AFTER every seed
-- package). The identity INSERTs below would otherwise collide on rt_table_data_rows_pkey.
SELECT setval(pg_get_serial_sequence('rt_table_data_rows', 'id'),
              GREATEST(COALESCE((SELECT MAX(id) FROM rt_table_data_rows), 0), 1));

INSERT INTO rt_table_definitions (
    id, table_name, display_name, deployed_display_name, description,
    status, enabled, portal_visible, current_version,
    created_at, created_by, updated_at, updated_by, function_unit_id)
VALUES
    (14, 'hmdc_dropdown', 'HMDC Dropdown', 'HMDC Dropdown',
     'ATM / HMDC shared dropdowns: case type, channel, currency, dispute reason, urge type',
     'DEPLOYED', true, true, 1, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system',
     (SELECT id FROM sys_function_units WHERE code = 'atm-20260623-gaevus' LIMIT 1)),
    (28, 'hmdc_correspondence', 'HMDC Correspondence', 'HMDC Correspondence',
     'ATM correspondence type / mode / channel / MDC status',
     'DEPLOYED', true, true, 1, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system',
     (SELECT id FROM sys_function_units WHERE code = 'atm-20260623-gaevus' LIMIT 1)),
    (29, 'hmdc_case_status', 'HMDC Case Status', 'HMDC Case Status',
     'ATM case status values, filtered by stage_code from the selected case stage',
     'DEPLOYED', true, true, 1, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system',
     (SELECT id FROM sys_function_units WHERE code = 'atm-20260623-gaevus' LIMIT 1)),
    (30, 'hmdc_case_stage_definition', 'HMDC Case Stage', 'HMDC Case Stage',
     'ATM case stages aligned with work queues (Case Submission through Finalized)',
     'DEPLOYED', true, true, 1, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system',
     (SELECT id FROM sys_function_units WHERE code = 'atm-20260623-gaevus' LIMIT 1))
ON CONFLICT (id) DO UPDATE SET
    table_name = EXCLUDED.table_name,
    display_name = EXCLUDED.display_name,
    deployed_display_name = EXCLUDED.deployed_display_name,
    description = EXCLUDED.description,
    status = EXCLUDED.status,
    enabled = EXCLUDED.enabled,
    portal_visible = EXCLUDED.portal_visible,
    current_version = EXCLUDED.current_version,
    updated_at = EXCLUDED.updated_at,
    updated_by = EXCLUDED.updated_by,
    function_unit_id = COALESCE(EXCLUDED.function_unit_id, rt_table_definitions.function_unit_id)
WHERE rt_table_definitions.table_name = EXCLUDED.table_name;

INSERT INTO rt_field_definitions (
    id, table_id, field_name, data_type, length, nullable, is_primary_key,
    display_name, sort_order, pk_generation_json, fk_display_mode)
VALUES
    (100, 14, 'id', 'VARCHAR', 100, false, true, 'Id', 0, '{"strategy":"uuid"}'::jsonb, 'readonly'),
    (101, 14, 'dropdown_category', 'VARCHAR', 255, false, false, 'Category', 1, NULL, 'readonly'),
    (102, 14, 'dropdown_name', 'VARCHAR', 255, false, false, 'Name', 2, NULL, 'readonly'),
    (103, 14, 'enabled', 'BOOLEAN', NULL, false, false, 'Enabled', 3, NULL, 'readonly'),
    (104, 14, 'created_at', 'TIMESTAMP', NULL, true, false, 'Created At', 4, NULL, 'readonly'),
    (105, 14, 'created_by', 'VARCHAR', 64, true, false, 'Created By', 5, NULL, 'readonly'),
    (106, 14, 'updated_at', 'TIMESTAMP', NULL, true, false, 'Updated At', 6, NULL, 'readonly'),
    (107, 14, 'updated_by', 'VARCHAR', 64, true, false, 'Updated By', 7, NULL, 'readonly'),
    (110, 28, 'id', 'VARCHAR', 100, false, true, 'Id', 0, '{"strategy":"uuid"}'::jsonb, 'readonly'),
    (111, 28, 'objectives', 'VARCHAR', 255, false, false, 'Objectives', 1, NULL, 'readonly'),
    (112, 28, 'standardizations', 'VARCHAR', 255, false, false, 'Standardizations', 2, NULL, 'readonly'),
    (113, 28, 'created_at', 'TIMESTAMP', NULL, true, false, 'Created At', 3, NULL, 'readonly'),
    (114, 28, 'created_by', 'VARCHAR', 64, true, false, 'Created By', 4, NULL, 'readonly'),
    (115, 28, 'updated_at', 'TIMESTAMP', NULL, true, false, 'Updated At', 5, NULL, 'readonly'),
    (116, 28, 'updated_by', 'VARCHAR', 64, true, false, 'Updated By', 6, NULL, 'readonly'),
    (120, 29, 'id', 'VARCHAR', 100, false, true, 'Id', 0, '{"strategy":"uuid"}'::jsonb, 'readonly'),
    (121, 29, 'stage_code', 'VARCHAR', 64, false, false, 'Stage Code', 1, NULL, 'readonly'),
    (122, 29, 'status_name', 'VARCHAR', 255, false, false, 'Status Name', 2, NULL, 'readonly'),
    (123, 29, 'created_at', 'TIMESTAMP', NULL, true, false, 'Created At', 3, NULL, 'readonly'),
    (124, 29, 'created_by', 'VARCHAR', 64, true, false, 'Created By', 4, NULL, 'readonly'),
    (125, 29, 'updated_at', 'TIMESTAMP', NULL, true, false, 'Updated At', 5, NULL, 'readonly'),
    (126, 29, 'updated_by', 'VARCHAR', 64, true, false, 'Updated By', 6, NULL, 'readonly'),
    (130, 30, 'id', 'VARCHAR', 100, false, true, 'Id', 0, '{"strategy":"uuid"}'::jsonb, 'readonly'),
    (131, 30, 'stage_code', 'VARCHAR', 64, false, false, 'Stage Code', 1, NULL, 'readonly'),
    (132, 30, 'stage_name', 'VARCHAR', 255, false, false, 'Stage Name', 2, NULL, 'readonly'),
    (133, 30, 'created_at', 'TIMESTAMP', NULL, true, false, 'Created At', 3, NULL, 'readonly'),
    (134, 30, 'created_by', 'VARCHAR', 64, true, false, 'Created By', 4, NULL, 'readonly'),
    (135, 30, 'updated_at', 'TIMESTAMP', NULL, true, false, 'Updated At', 5, NULL, 'readonly'),
    (136, 30, 'updated_by', 'VARCHAR', 64, true, false, 'Updated By', 6, NULL, 'readonly')
ON CONFLICT (id) DO NOTHING;

-- Explicit ids (15-18): rt_table_versions.id is BIGSERIAL, and 17-Multi-Instance-Subtask-Demo's
-- 00-init-kk.sql (which runs before this package) already inserts rows 1-14 with explicit ids
-- without advancing the sequence (see 90-post-seed/00-align-id-sequences.sql). An identity/auto
-- INSERT here would collide on rt_table_versions_pkey before that alignment script ever runs.
INSERT INTO rt_table_versions (id, table_id, version_number, snapshot_data, deployed_by, deployed_at, change_log)
SELECT v.id, v.table_id, 1, v.snapshot_data, 'system', CURRENT_TIMESTAMP, 'ATM HMDC lookup seed'
FROM (VALUES
    (15, 14, '[{"fieldName":"id","dataType":"VARCHAR","isPrimaryKey":true,"displayName":"Id","sortOrder":0},{"fieldName":"dropdown_category","dataType":"VARCHAR","displayName":"Category","sortOrder":1},{"fieldName":"dropdown_name","dataType":"VARCHAR","displayName":"Name","sortOrder":2},{"fieldName":"enabled","dataType":"BOOLEAN","displayName":"Enabled","sortOrder":3}]'),
    (16, 28, '[{"fieldName":"id","dataType":"VARCHAR","isPrimaryKey":true,"displayName":"Id","sortOrder":0},{"fieldName":"objectives","dataType":"VARCHAR","displayName":"Objectives","sortOrder":1},{"fieldName":"standardizations","dataType":"VARCHAR","displayName":"Standardizations","sortOrder":2}]'),
    (17, 29, '[{"fieldName":"id","dataType":"VARCHAR","isPrimaryKey":true,"displayName":"Id","sortOrder":0},{"fieldName":"stage_code","dataType":"VARCHAR","displayName":"Stage Code","sortOrder":1},{"fieldName":"status_name","dataType":"VARCHAR","displayName":"Status Name","sortOrder":2}]'),
    (18, 30, '[{"fieldName":"id","dataType":"VARCHAR","isPrimaryKey":true,"displayName":"Id","sortOrder":0},{"fieldName":"stage_code","dataType":"VARCHAR","displayName":"Stage Code","sortOrder":1},{"fieldName":"stage_name","dataType":"VARCHAR","displayName":"Stage Name","sortOrder":2}]')
) AS v(id, table_id, snapshot_data)
WHERE NOT EXISTS (
    SELECT 1 FROM rt_table_versions existing
    WHERE existing.table_id = v.table_id AND existing.version_number = 1)
ON CONFLICT (id) DO NOTHING;

WITH src(row_id, category, name) AS (
    VALUES
        ('hmdc-dd-urge-normal', 'Urge Type', 'Normal'),
        ('hmdc-dd-urge-urgent', 'Urge Type', 'Urgent'),
        ('hmdc-dd-urge-escalated', 'Urge Type', 'Escalated'),
        ('hmdc-dd-type-atm-cash', 'Case type', 'ATM Cash Dispute'),
        ('hmdc-dd-type-unauth', 'Case type', 'Unauthorized ATM Withdrawal'),
        ('hmdc-dd-type-capture', 'Case type', 'Card Capture'),
        ('hmdc-dd-type-short', 'Case type', 'Short Dispense'),
        ('hmdc-dd-type-dup', 'Case type', 'Duplicate Debit'),
        ('hmdc-dd-type-other', 'Case type', 'Other ATM Issue'),
        ('hmdc-dd-ch-branch', 'Incoming channel', 'Branch'),
        ('hmdc-dd-ch-call', 'Incoming channel', 'Call Centre'),
        ('hmdc-dd-ch-email', 'Incoming channel', 'Email'),
        ('hmdc-dd-ch-ib', 'Incoming channel', 'Internet Banking'),
        ('hmdc-dd-ch-app', 'Incoming channel', 'Mobile App'),
        ('hmdc-dd-ch-letter', 'Incoming channel', 'Written Letter'),
        ('hmdc-dd-bcur-hkd', 'Billing currency', 'HKD'),
        ('hmdc-dd-bcur-usd', 'Billing currency', 'USD'),
        ('hmdc-dd-bcur-cny', 'Billing currency', 'CNY'),
        ('hmdc-dd-bcur-eur', 'Billing currency', 'EUR'),
        ('hmdc-dd-bcur-jpy', 'Billing currency', 'JPY'),
        ('hmdc-dd-bcur-gbp', 'Billing currency', 'GBP'),
        ('hmdc-dd-dcur-hkd', 'Dispute currency', 'HKD'),
        ('hmdc-dd-dcur-usd', 'Dispute currency', 'USD'),
        ('hmdc-dd-dcur-cny', 'Dispute currency', 'CNY'),
        ('hmdc-dd-dcur-eur', 'Dispute currency', 'EUR'),
        ('hmdc-dd-dcur-jpy', 'Dispute currency', 'JPY'),
        ('hmdc-dd-dcur-gbp', 'Dispute currency', 'GBP'),
        ('hmdc-dd-rsn-no-cash', 'Dispute reason', 'Cash not dispensed'),
        ('hmdc-dd-rsn-short', 'Dispute reason', 'Short cash'),
        ('hmdc-dd-rsn-unauth', 'Dispute reason', 'Unauthorized withdrawal'),
        ('hmdc-dd-rsn-card', 'Dispute reason', 'Card not returned'),
        ('hmdc-dd-rsn-amount', 'Dispute reason', 'Incorrect amount posted'),
        ('hmdc-dd-rsn-malfunc', 'Dispute reason', 'ATM malfunction'),
        ('hmdc-dd-rsn-dup', 'Dispute reason', 'Duplicate posting')
)
INSERT INTO rt_table_data_rows (table_id, row_id, data, status, created_at, created_by, updated_at, updated_by)
SELECT 14, src.row_id,
       jsonb_build_object(
           'id', src.row_id,
           'dropdown_category', src.category,
           'dropdown_name', src.name,
           'enabled', true,
           'created_at', '2026-08-24 16:00:00',
           'created_by', 'system',
           'updated_at', '2026-08-24 16:00:00',
           'updated_by', 'system'),
       'ACTIVE', TIMESTAMP '2026-08-24 16:00:00', 'system', TIMESTAMP '2026-08-24 16:00:00', 'system'
FROM src
ON CONFLICT (table_id, row_id) DO UPDATE
SET data = EXCLUDED.data, updated_at = EXCLUDED.updated_at, updated_by = EXCLUDED.updated_by;

WITH src(row_id, objectives, standardizations) AS (
    VALUES
        ('hmdc-corr-type-cust', 'Correspondence type', 'Customer Notification'),
        ('hmdc-corr-type-int', 'Correspondence type', 'Internal Request'),
        ('hmdc-corr-type-cb', 'Correspondence type', 'Chargeback'),
        ('hmdc-corr-type-inv', 'Correspondence type', 'Investigation Report'),
        ('hmdc-corr-type-rem', 'Correspondence type', 'Reminder'),
        ('hmdc-corr-mode-out', 'Correspondence Mode', 'Outbound'),
        ('hmdc-corr-mode-in', 'Correspondence Mode', 'Inbound'),
        ('hmdc-corr-ch-email', 'Correspondence Channel', 'Email'),
        ('hmdc-corr-ch-letter', 'Correspondence Channel', 'Letter'),
        ('hmdc-corr-ch-phone', 'Correspondence Channel', 'Phone'),
        ('hmdc-corr-ch-sms', 'Correspondence Channel', 'SMS'),
        ('hmdc-corr-ch-fax', 'Correspondence Channel', 'Fax'),
        ('hmdc-corr-mdc-draft', 'MDC Status', 'Draft'),
        ('hmdc-corr-mdc-sent', 'MDC Status', 'Sent'),
        ('hmdc-corr-mdc-recv', 'MDC Status', 'Received'),
        ('hmdc-corr-mdc-ack', 'MDC Status', 'Acknowledged'),
        ('hmdc-corr-mdc-closed', 'MDC Status', 'Closed')
)
INSERT INTO rt_table_data_rows (table_id, row_id, data, status, created_at, created_by, updated_at, updated_by)
SELECT 28, src.row_id,
       jsonb_build_object(
           'id', src.row_id,
           'objectives', src.objectives,
           'standardizations', src.standardizations,
           'created_at', '2026-08-24 16:00:00',
           'created_by', 'system',
           'updated_at', '2026-08-24 16:00:00',
           'updated_by', 'system'),
       'ACTIVE', TIMESTAMP '2026-08-24 16:00:00', 'system', TIMESTAMP '2026-08-24 16:00:00', 'system'
FROM src
ON CONFLICT (table_id, row_id) DO UPDATE
SET data = EXCLUDED.data, updated_at = EXCLUDED.updated_at, updated_by = EXCLUDED.updated_by;

-- row_id = stage_name so stored lookup values match work-queue filters
-- (ATM Case - Completion Work Queue: case_stage = 'Finalized').
WITH src(row_id, stage_code, stage_name) AS (
    VALUES
        ('Case Submission', 'CS', 'Case Submission'),
        ('Transaction Assignment', 'TA', 'Transaction Assignment'),
        ('Transaction Investigation', 'TI', 'Transaction Investigation'),
        ('Finalized', 'FIN', 'Finalized')
)
INSERT INTO rt_table_data_rows (table_id, row_id, data, status, created_at, created_by, updated_at, updated_by)
SELECT 30, src.row_id,
       jsonb_build_object(
           'id', src.row_id,
           'stage_code', src.stage_code,
           'stage_name', src.stage_name,
           'created_at', '2026-08-24 16:00:00',
           'created_by', 'system',
           'updated_at', '2026-08-24 16:00:00',
           'updated_by', 'system'),
       'ACTIVE', TIMESTAMP '2026-08-24 16:00:00', 'system', TIMESTAMP '2026-08-24 16:00:00', 'system'
FROM src
ON CONFLICT (table_id, row_id) DO UPDATE
SET data = EXCLUDED.data, updated_at = EXCLUDED.updated_at, updated_by = EXCLUDED.updated_by;

WITH src(row_id, stage_code, status_name) AS (
    VALUES
        ('hmdc-st-cs-open', 'CS', 'Open'),
        ('hmdc-st-cs-draft', 'CS', 'Draft'),
        ('hmdc-st-ta-alloc', 'TA', 'Pending Allocation'),
        ('hmdc-st-ti-prog', 'TI', 'In Progress'),
        ('hmdc-st-ti-cust', 'TI', 'Pending Customer'),
        ('hmdc-st-ti-evid', 'TI', 'Pending Evidence'),
        ('hmdc-st-fin-done', 'FIN', 'Completed'),
        ('hmdc-st-fin-closed', 'FIN', 'Closed'),
        ('hmdc-st-fin-rej', 'FIN', 'Rejected'),
        ('hmdc-st-fin-can', 'FIN', 'Cancelled')
)
INSERT INTO rt_table_data_rows (table_id, row_id, data, status, created_at, created_by, updated_at, updated_by)
SELECT 29, src.row_id,
       jsonb_build_object(
           'id', src.row_id,
           'stage_code', src.stage_code,
           'status_name', src.status_name,
           'created_at', '2026-08-24 16:00:00',
           'created_by', 'system',
           'updated_at', '2026-08-24 16:00:00',
           'updated_by', 'system'),
       'ACTIVE', TIMESTAMP '2026-08-24 16:00:00', 'system', TIMESTAMP '2026-08-24 16:00:00', 'system'
FROM src
ON CONFLICT (table_id, row_id) DO UPDATE
SET data = EXCLUDED.data, updated_at = EXCLUDED.updated_at, updated_by = EXCLUDED.updated_by;

SELECT setval('rt_table_definitions_id_seq', GREATEST((SELECT MAX(id) FROM rt_table_definitions), 1));
SELECT setval('rt_field_definitions_id_seq', GREATEST((SELECT MAX(id) FROM rt_field_definitions), 1));
SELECT setval('rt_table_data_rows_id_seq', GREATEST((SELECT MAX(id) FROM rt_table_data_rows), 1));
SELECT setval('rt_table_versions_id_seq', GREATEST((SELECT MAX(id) FROM rt_table_versions), 1));
