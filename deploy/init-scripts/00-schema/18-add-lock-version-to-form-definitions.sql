-- =====================================================
-- 18. Add lock_version column to dw_form_definitions
-- =====================================================
-- FormDefinition entity uses @Version with lock_version
-- for optimistic locking.

ALTER TABLE dw_form_definitions
ADD COLUMN IF NOT EXISTS lock_version BIGINT DEFAULT 0;

COMMENT ON COLUMN dw_form_definitions.lock_version IS 'Optimistic locking version';
