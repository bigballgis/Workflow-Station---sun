-- Add read_only column to dw_form_stage_bindings
-- This column stores the form node binding's readonly flag
-- so the user portal can determine form editability independently of BPMN

ALTER TABLE dw_form_stage_bindings 
ADD COLUMN IF NOT EXISTS read_only BOOLEAN NOT NULL DEFAULT false;

COMMENT ON COLUMN dw_form_stage_bindings.read_only IS 'Whether the form bound to this stage is read-only';
