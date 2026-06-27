ALTER TABLE dw_form_definitions
  ADD COLUMN field_permissions JSONB DEFAULT '{}',
  ADD COLUMN show_live_values BOOLEAN NOT NULL DEFAULT TRUE;
