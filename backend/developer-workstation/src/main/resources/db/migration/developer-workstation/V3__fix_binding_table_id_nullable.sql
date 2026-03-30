-- Fix: Allow table_id to be NULL for RELATED type bindings (which use relation_table_id instead)
ALTER TABLE dw_form_table_bindings ALTER COLUMN table_id DROP NOT NULL;

-- Drop the unique constraint that prevents multiple NULL table_id rows
ALTER TABLE dw_form_table_bindings DROP CONSTRAINT IF EXISTS uk_form_table_binding;
