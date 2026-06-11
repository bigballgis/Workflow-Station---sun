-- Global uniqueness for dw_table_definitions.table_name (per-table PK sequences rely on stable table identity).
ALTER TABLE dw_table_definitions DROP CONSTRAINT IF EXISTS uk_table_name_fu;
ALTER TABLE dw_table_definitions ADD CONSTRAINT uk_dw_table_name UNIQUE (table_name);
