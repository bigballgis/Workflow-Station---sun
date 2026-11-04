-- Stable design identity travels in ZIPs; database IDs remain environment-local.
ALTER TABLE dw_main_table_view_configs ADD COLUMN IF NOT EXISTS view_key VARCHAR(36);
UPDATE dw_main_table_view_configs SET view_key = gen_random_uuid()::text WHERE view_key IS NULL;
ALTER TABLE dw_main_table_view_configs ALTER COLUMN view_key SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_dw_view_key ON dw_main_table_view_configs(function_unit_id, view_key);

CREATE TABLE IF NOT EXISTS ac_data_view_function_units (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE
);
CREATE TABLE IF NOT EXISTS ac_data_view_tables (
    id BIGSERIAL PRIMARY KEY,
    function_unit_id BIGINT NOT NULL REFERENCES ac_data_view_function_units(id),
    table_name VARCHAR(200) NOT NULL,
    UNIQUE(function_unit_id, table_name)
);
CREATE TABLE IF NOT EXISTS ac_data_view_identities (
    id BIGSERIAL PRIMARY KEY,
    function_unit_id BIGINT NOT NULL REFERENCES ac_data_view_function_units(id),
    view_key VARCHAR(36) NOT NULL,
    UNIQUE(function_unit_id, view_key)
);
-- Keep old numeric designer URLs from being reinterpreted as a different Admin View at cut-over.
SELECT setval('ac_data_view_identities_id_seq',GREATEST(1000000000000,
    COALESCE((SELECT MAX(id) FROM dw_main_table_view_configs),0)+1),FALSE)
WHERE NOT EXISTS (SELECT 1 FROM ac_data_view_identities);
CREATE TABLE IF NOT EXISTS ac_data_view_releases (
    view_id BIGINT NOT NULL REFERENCES ac_data_view_identities(id),
    catalog_id VARCHAR(64) NOT NULL REFERENCES sys_function_units(id) ON DELETE CASCADE,
    table_id BIGINT NOT NULL REFERENCES ac_data_view_tables(id),
    table_name VARCHAR(200) NOT NULL,
    table_display_name VARCHAR(200),
    table_type VARCHAR(20) NOT NULL,
    view_name VARCHAR(200) NOT NULL,
    definition JSONB NOT NULL,
    PRIMARY KEY(view_id, catalog_id)
);
CREATE INDEX IF NOT EXISTS idx_ac_view_releases_catalog ON ac_data_view_releases(catalog_id);
-- enabled is the existing activation pointer. Never expose an imported draft.
CREATE OR REPLACE VIEW ac_current_data_views AS
SELECT r.*, i.function_unit_id, i.view_key, fu.code AS function_unit_code,
       fu.name AS function_unit_name, fu.version, fu.icon_svg,
       (r.definition->>'isDefault')::boolean AS is_default
FROM ac_data_view_releases r
JOIN ac_data_view_identities i ON i.id = r.view_id
JOIN sys_function_units fu ON fu.id = r.catalog_id
WHERE fu.status = 'DEPLOYED' AND fu.enabled = TRUE
  AND r.definition->>'status' = 'PUBLISHED';

-- Keep old assignments and their display metadata. They cannot authorize a new numeric namespace.
ALTER TABLE bi_data_view_assignment ADD COLUMN IF NOT EXISTS runtime_target BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE bi_data_view_assignment ADD COLUMN IF NOT EXISTS legacy_target JSONB;
UPDATE bi_data_view_assignment a SET legacy_target = jsonb_build_object(
    'functionUnitCode', fu.code, 'functionUnitName', fu.name,
    'tableName', t.table_name, 'tableDisplayName', t.table_display_name,
    'tableType', t.table_type, 'viewName', v.view_name, 'viewKey', v.view_key)
FROM dw_function_units fu
LEFT JOIN dw_table_definitions t ON t.function_unit_id = fu.id
LEFT JOIN dw_main_table_view_configs v ON v.function_unit_id = fu.id AND v.main_table_id = t.id
WHERE a.function_unit_id = fu.id AND a.table_id = t.id AND a.view_id = v.id
  AND NOT a.runtime_target AND a.legacy_target IS NULL;
CREATE INDEX IF NOT EXISTS idx_bi_data_view_legacy_key
ON bi_data_view_assignment((legacy_target->>'functionUnitCode'),(legacy_target->>'viewKey')) WHERE NOT runtime_target;
-- Old and new targets may have equal numeric IDs; namespace is part of the binding identity.
ALTER TABLE bi_data_view_assignment DROP CONSTRAINT IF EXISTS uk_bi_data_view_assignment;
CREATE UNIQUE INDEX IF NOT EXISTS uk_bi_data_view_assignment_target
ON bi_data_view_assignment(dashboard_id, view_id, runtime_target);

-- Seed scripts may insert Views after this schema migration; new records still need an identity.
ALTER TABLE dw_main_table_view_configs ALTER COLUMN view_key SET DEFAULT gen_random_uuid()::text;

-- Runtime allocation must not query designer metadata or advance designer preview counters.
CREATE TABLE IF NOT EXISTS ac_runtime_pk_sequences (
    id BIGSERIAL PRIMARY KEY,
    table_id BIGINT NOT NULL,
    field_name VARCHAR(100) NOT NULL,
    scope_type VARCHAR(32) NOT NULL DEFAULT 'perTable',
    scope_key VARCHAR(128) NOT NULL DEFAULT '',
    prefix VARCHAR(64) DEFAULT '',
    pad_width INTEGER DEFAULT 6,
    current_value BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(table_id, field_name, scope_type, scope_key)
);
INSERT INTO ac_runtime_pk_sequences(table_id,field_name,scope_type,scope_key,prefix,pad_width,current_value)
SELECT table_id,field_name,scope_type,scope_key,prefix,pad_width,current_value FROM dw_pk_sequences
ON CONFLICT(table_id,field_name,scope_type,scope_key) DO NOTHING;
