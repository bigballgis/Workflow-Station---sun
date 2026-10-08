-- Main Table View: per-column choice of what a select-like field shows in the Portal list.
-- select_display = 'value' (default, the stored option value) | 'label' (the option's label,
-- resolved at read time from the static options of the form widget bound to the field).

ALTER TABLE dw_main_table_view_fields
    ADD COLUMN IF NOT EXISTS select_display VARCHAR(10) NOT NULL DEFAULT 'value';

COMMENT ON COLUMN dw_main_table_view_fields.select_display IS
    'value = show the stored option value; label = show the option label from the bound form widget';
