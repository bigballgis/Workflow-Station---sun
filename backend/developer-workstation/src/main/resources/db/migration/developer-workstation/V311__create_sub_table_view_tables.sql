-- Create sub-table view configs table
CREATE TABLE IF NOT EXISTS dw_sub_table_view_configs (
    id BIGSERIAL PRIMARY KEY,
    binding_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- Create unique constraint on binding_id (one view config per binding)
CREATE UNIQUE INDEX IF NOT EXISTS idx_sub_table_view_configs_binding_id ON dw_sub_table_view_configs(binding_id);

-- Create sub-table view fields table
CREATE TABLE IF NOT EXISTS dw_sub_table_view_fields (
    id BIGSERIAL PRIMARY KEY,
    view_config_id BIGINT NOT NULL,
    field_name VARCHAR(100) NOT NULL,
    display_label VARCHAR(200),
    column_width INTEGER,
    sort_order INTEGER NOT NULL DEFAULT 0,
    visible BOOLEAN NOT NULL DEFAULT TRUE
);

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_sub_table_view_fields_config_id ON dw_sub_table_view_fields(view_config_id);
CREATE INDEX IF NOT EXISTS idx_sub_table_view_fields_sort_order ON dw_sub_table_view_fields(view_config_id, sort_order);

-- Add sub_list_view_id column to dw_form_table_bindings (if not exists)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'dw_form_table_bindings' AND column_name = 'sub_list_view_id'
    ) THEN
        ALTER TABLE dw_form_table_bindings ADD COLUMN sub_list_view_id BIGINT;
    END IF;
END $$;

-- Add comment for documentation
COMMENT ON COLUMN dw_form_table_bindings.sub_list_view_id IS 'Sub-table list view config ID, used only when binding_type is SUB';
