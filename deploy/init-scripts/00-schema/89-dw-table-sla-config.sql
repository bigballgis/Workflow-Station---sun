-- SLA due date mapping on the PRIMARY main table:
--   {"startDateSource": "FIELD" | "SUBMITTED_AT", "startDateField": "...", "dueDateField": "..."}
-- The lead time lives in ac_sla_policies (per environment); this column only says which fields to use.
-- Idempotent: safe to re-run.

ALTER TABLE dw_table_definitions ADD COLUMN IF NOT EXISTS sla_config JSONB;

COMMENT ON COLUMN dw_table_definitions.sla_config IS
    'SLA due date mapping (start date source/field + due date field) for the main table';
