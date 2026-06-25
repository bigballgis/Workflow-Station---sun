\set ON_ERROR_STOP on

-- =====================================================
-- Developer Workstation: sub-table list view storage
-- =====================================================
-- JPA entities: SubTableViewConfig / SubTableViewField
--
-- Mirrors Flyway V311 (backend/.../V311__create_sub_table_view_tables.sql).
-- Docker Compose dev disables Flyway by default — these tables MUST exist in
-- init scripts or INSERT/SELECT on create SUB binding + FULL mode will fail with:
--   ERROR: relation "dw_sub_table_view_configs" does not exist
-- and poison the enclosing transaction ("current transaction is aborted").
-- =====================================================

CREATE TABLE IF NOT EXISTS dw_sub_table_view_configs (
    id BIGSERIAL PRIMARY KEY,
    binding_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_sub_table_view_configs_binding_id
    ON dw_sub_table_view_configs(binding_id);

CREATE TABLE IF NOT EXISTS dw_sub_table_view_fields (
    id BIGSERIAL PRIMARY KEY,
    view_config_id BIGINT NOT NULL,
    field_name VARCHAR(100) NOT NULL,
    display_label VARCHAR(200),
    column_width INTEGER,
    sort_order INTEGER NOT NULL DEFAULT 0,
    visible BOOLEAN NOT NULL DEFAULT TRUE
);

-- FK with ON DELETE CASCADE: without it, deleting a form-table binding (e.g. on re-import or FU delete)
-- orphans the config row, and the orphan's unique binding_id later collides with a new binding's id.
-- Clean any pre-existing orphans first so the constraint can be added on legacy/seeded DBs.
DELETE FROM dw_sub_table_view_fields f
 WHERE NOT EXISTS (SELECT 1 FROM dw_sub_table_view_configs c WHERE c.id = f.view_config_id)
    OR f.view_config_id IN (
       SELECT c.id FROM dw_sub_table_view_configs c
        WHERE NOT EXISTS (SELECT 1 FROM dw_form_table_bindings b WHERE b.id = c.binding_id));
DELETE FROM dw_sub_table_view_configs c
 WHERE NOT EXISTS (SELECT 1 FROM dw_form_table_bindings b WHERE b.id = c.binding_id);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_sub_view_config_binding') THEN
        ALTER TABLE dw_sub_table_view_configs
            ADD CONSTRAINT fk_sub_view_config_binding
            FOREIGN KEY (binding_id) REFERENCES dw_form_table_bindings(id) ON DELETE CASCADE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_sub_view_field_config') THEN
        ALTER TABLE dw_sub_table_view_fields
            ADD CONSTRAINT fk_sub_view_field_config
            FOREIGN KEY (view_config_id) REFERENCES dw_sub_table_view_configs(id) ON DELETE CASCADE;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_dw_sub_table_view_fields_config_id
    ON dw_sub_table_view_fields(view_config_id);

CREATE INDEX IF NOT EXISTS idx_dw_sub_table_view_fields_sort_order
    ON dw_sub_table_view_fields(view_config_id, sort_order);
