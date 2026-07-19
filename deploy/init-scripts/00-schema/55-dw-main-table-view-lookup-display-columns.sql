-- Main Table View: Power Apps-style lookup display columns
-- column_type = 'field' (default) | 'lookup_display'
-- lookup_display rows store source field (e.g. t) + target attribute (e.g. full_name);
-- field_name uses synthetic key source@display (e.g. t@full_name).

ALTER TABLE dw_main_table_view_fields
    ADD COLUMN IF NOT EXISTS column_type VARCHAR(20) NOT NULL DEFAULT 'field';

ALTER TABLE dw_main_table_view_fields
    ADD COLUMN IF NOT EXISTS lookup_source_field VARCHAR(100);

ALTER TABLE dw_main_table_view_fields
    ADD COLUMN IF NOT EXISTS lookup_display_field VARCHAR(100);

COMMENT ON COLUMN dw_main_table_view_fields.column_type IS
    'field = physical/system column; lookup_display = derived attribute from a lookup source field';
COMMENT ON COLUMN dw_main_table_view_fields.lookup_source_field IS
    'For lookup_display: form/main-table lookup field name (e.g. t)';
COMMENT ON COLUMN dw_main_table_view_fields.lookup_display_field IS
    'For lookup_display: attribute on the lookup target table (e.g. full_name)';
