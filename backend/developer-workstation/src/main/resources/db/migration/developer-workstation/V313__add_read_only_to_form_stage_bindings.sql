-- Align dw_form_stage_bindings with deploy/init-scripts/00-schema/18-add-read-only-to-form-stage-bindings.sql
-- user-portal TaskFormComponent reads b.read_only when resolving task forms from shared PostgreSQL.

ALTER TABLE dw_form_stage_bindings
    ADD COLUMN IF NOT EXISTS read_only BOOLEAN NOT NULL DEFAULT false;

COMMENT ON COLUMN dw_form_stage_bindings.read_only IS 'Whether the form bound to this stage is read-only';
