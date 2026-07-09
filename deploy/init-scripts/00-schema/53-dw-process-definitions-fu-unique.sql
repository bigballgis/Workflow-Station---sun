\set ON_ERROR_STOP on

-- =====================================================
-- Developer Workstation: one process definition per FU
-- =====================================================
-- Enforce at most one dw_process_definitions row per
-- function_unit_id. Idempotent via IF NOT EXISTS.
-- =====================================================

CREATE UNIQUE INDEX IF NOT EXISTS idx_dw_process_definitions_fu_unique
ON dw_process_definitions(function_unit_id);

COMMENT ON INDEX idx_dw_process_definitions_fu_unique IS
    'Ensure at most one process definition per function unit';
