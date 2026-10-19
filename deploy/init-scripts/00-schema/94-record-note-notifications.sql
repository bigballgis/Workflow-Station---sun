-- Record Note submissions and recoverable in-app delivery. Additive and repeatable.
ALTER TABLE up_record_note ADD COLUMN IF NOT EXISTS process_instance_id VARCHAR(64);
ALTER TABLE up_record_note ADD COLUMN IF NOT EXISTS submission_id VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_note_request_target ON up_record_note
    (process_instance_id, table_kind, table_id, target_type, target_id, created_at DESC);
CREATE TABLE IF NOT EXISTS up_record_note_submissions (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    client_key VARCHAR(64) NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    process_instance_id VARCHAR(64),
    target_type VARCHAR(20) NOT NULL,
    target_id VARCHAR(512) NOT NULL,
    table_kind VARCHAR(10) NOT NULL,
    table_id VARCHAR(64) NOT NULL,
    form_key VARCHAR(64) NOT NULL,
    control_id VARCHAR(64) NOT NULL,
    catalog_id VARCHAR(64),
    attachment_count INTEGER NOT NULL,
    policy JSONB NOT NULL,
    response JSONB,
    state VARCHAR(32) NOT NULL,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(user_id, client_key)
);
CREATE INDEX IF NOT EXISTS idx_note_submission_due ON up_record_note_submissions(state,next_attempt_at);
CREATE TABLE IF NOT EXISTS up_record_note_delivery_targets (
    submission_id VARCHAR(64) NOT NULL REFERENCES up_record_note_submissions(id),
    target_key VARCHAR(1024) NOT NULL,
    node_key VARCHAR(255) NOT NULL,
    row_key JSONB NOT NULL,
    state VARCHAR(32) NOT NULL,
    task_id VARCHAR(64),
    activation_id VARCHAR(64),
    recipient_id VARCHAR(64),
    last_error VARCHAR(128),
    PRIMARY KEY(submission_id,target_key)
);
CREATE TABLE IF NOT EXISTS up_record_note_deliveries (
    submission_id VARCHAR(64) NOT NULL REFERENCES up_record_note_submissions(id),
    user_id VARCHAR(64) NOT NULL,
    notification_id BIGINT,
    state VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(submission_id,user_id)
);
-- Current task pointer, advanced by actual lifecycle events, never by timestamps/rowVersion.
CREATE TABLE IF NOT EXISTS we_record_note_task_rounds (
    task_id VARCHAR(64) PRIMARY KEY,
    process_instance_id VARCHAR(64) NOT NULL,
    process_definition_id VARCHAR(255) NOT NULL,
    execution_id VARCHAR(64) NOT NULL,
    node_key VARCHAR(255) NOT NULL,
    scope_key VARCHAR(255) NOT NULL,
    table_name VARCHAR(255) NOT NULL,
    row_identity TEXT NOT NULL,
    row_key JSONB NOT NULL,
    activation_id VARCHAR(64) NOT NULL,
    assignment_mode VARCHAR(16) NOT NULL,
    person_field VARCHAR(255),
    recipient_id VARCHAR(64),
    state VARCHAR(32) NOT NULL,
    activation_context JSONB NOT NULL DEFAULT '{}'::jsonb,
    is_current BOOLEAN NOT NULL DEFAULT true,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE we_record_note_task_rounds ADD COLUMN IF NOT EXISTS activation_context JSONB NOT NULL DEFAULT '{}'::jsonb;
CREATE UNIQUE INDEX IF NOT EXISTS idx_note_round_current ON we_record_note_task_rounds
    (process_instance_id,node_key,md5(row_identity)) WHERE is_current;
CREATE INDEX IF NOT EXISTS idx_note_round_request ON we_record_note_task_rounds(process_instance_id,node_key,is_current);

UPDATE up_record_note n SET process_instance_id=n.target_id
WHERE n.target_type='TABLE' AND n.process_instance_id IS NULL
  AND EXISTS(SELECT 1 FROM up_process_instance p WHERE p.id=n.target_id);

-- Legacy RECORD streams sometimes used the Request itself as their anchor.
UPDATE up_record_note n SET process_instance_id=n.target_id
WHERE n.target_type='RECORD' AND n.process_instance_id IS NULL
  AND EXISTS(SELECT 1 FROM up_process_instance p WHERE p.id=n.target_id);

-- Backfill row streams only when their configured selector proves ONE owning Request.
-- Ambiguous/missing identities stay unresolved; never infer an owner from a familiar column name.
WITH schemas AS (
    SELECT function_unit_id,
           CASE WHEN jsonb_typeof(content_data::jsonb)='string'
                THEN (content_data::jsonb #>> '{}')::jsonb ELSE content_data::jsonb END AS design
    FROM sys_function_unit_contents WHERE content_type='DATA_TABLE'
), selectors AS (
    SELECT function_unit_id,design,
           (SELECT CASE WHEN count(*)=1 THEN max(f->>'fieldName') ELSE 'platformRowUuid' END
            FROM jsonb_array_elements(COALESCE(design->'fields','[]'::jsonb)) f
            WHERE f->>'isPrimaryKey'='true') AS selector
    FROM schemas
), candidates AS (
    SELECT DISTINCT n.id AS note_id,p.id AS process_id
    FROM up_record_note n
    JOIN up_process_instance p ON p.function_unit_catalog_id IS NOT NULL
    JOIN selectors s ON s.function_unit_id=p.function_unit_catalog_id
                     AND s.design->>'tableId'=n.table_id
    CROSS JOIN LATERAL jsonb_path_query(p.variables,'lax $.**.__subTables__') stores
    CROSS JOIN LATERAL jsonb_array_elements(
        CASE WHEN jsonb_typeof(stores->('dw:'||lower(s.design->>'tableName')))='array'
             THEN stores->('dw:'||lower(s.design->>'tableName')) ELSE '[]'::jsonb END) row_data
    WHERE n.process_instance_id IS NULL AND n.target_type='RECORD' AND n.table_kind='DW'
      AND row_data->>s.selector=n.target_id
), unique_owners AS (
    SELECT note_id,min(process_id) AS process_id FROM candidates GROUP BY note_id HAVING count(*)=1
)
UPDATE up_record_note n SET process_instance_id=u.process_id FROM unique_owners u WHERE n.id=u.note_id;
