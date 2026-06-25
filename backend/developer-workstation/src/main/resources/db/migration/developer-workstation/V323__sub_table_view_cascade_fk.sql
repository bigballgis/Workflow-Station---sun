-- Add ON DELETE CASCADE FKs for sub-table view storage.
--
-- dw_sub_table_view_configs.binding_id has a UNIQUE index but had no FK/cascade, so deleting a
-- form-table binding (on FU re-import or delete) orphaned the config row. The orphan's binding_id
-- then collided with a freshly-inserted binding's IDENTITY id, raising
--   duplicate key value violates unique constraint "idx_sub_table_view_configs_binding_id"
-- on the next import. Cascade from the binding fixes this at the DB level.

-- Clean any pre-existing orphans so the constraints can be added on legacy DBs.
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
