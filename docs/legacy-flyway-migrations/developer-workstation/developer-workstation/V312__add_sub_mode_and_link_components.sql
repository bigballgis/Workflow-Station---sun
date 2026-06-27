-- Add sub_mode column to dw_form_table_bindings
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'dw_form_table_bindings' AND column_name = 'sub_mode'
    ) THEN
        ALTER TABLE dw_form_table_bindings ADD COLUMN sub_mode VARCHAR(20);
    END IF;
END $$;

-- Add comment for documentation
COMMENT ON COLUMN dw_form_table_bindings.sub_mode IS 'Sub-table binding mode: FULL (form + list view) or FORM_ONLY (form only)';

-- Create link form component definitions table
CREATE TABLE IF NOT EXISTS dw_link_form_components (
    id BIGSERIAL PRIMARY KEY,
    function_unit_id BIGINT NOT NULL,
    component_name VARCHAR(200) NOT NULL,
    linked_form_id BIGINT NOT NULL,
    display_field VARCHAR(100),
    link_text VARCHAR(200) DEFAULT '详情',
    column_label VARCHAR(200),
    sort_order INTEGER NOT NULL DEFAULT 0,
    config_json JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_link_form_components_function_unit ON dw_link_form_components(function_unit_id);
CREATE INDEX IF NOT EXISTS idx_link_form_components_sort_order ON dw_link_form_components(function_unit_id, sort_order);

-- Create link form data table (stores data for each sub-table row)
CREATE TABLE IF NOT EXISTS dw_link_form_data (
    id BIGSERIAL PRIMARY KEY,
    component_id BIGINT NOT NULL,
    sub_table_row_id BIGINT NOT NULL,
    form_data JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_link_form_data_component_row ON dw_link_form_data(component_id, sub_table_row_id);
CREATE INDEX IF NOT EXISTS idx_link_form_data_row ON dw_link_form_data(sub_table_row_id);
