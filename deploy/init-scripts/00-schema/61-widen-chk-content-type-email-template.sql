-- =============================================================================
-- Widen sys_function_unit_contents.chk_content_type to allow EMAIL_TEMPLATE
-- =============================================================================
-- Symptom: DW Deploy → Upload to admin center fails with HTTP 500
--   admin.fu.import_unexpected_error when the package contains email templates.
-- Cause: ContentType.EMAIL_TEMPLATE is written by FunctionUnitImportController /
--   FunctionUnitPackageParser, but chk_content_type still only allows
--   PROCESS/FORM/DATA_TABLE/SCRIPT/ACTION/MAIN_TABLE_VIEW.
-- Fix: DROP and re-ADD the CHECK constraint including EMAIL_TEMPLATE.
--
-- Idempotent for existing and fresh databases (append-only; do not edit 01-*.sql).
-- =============================================================================

ALTER TABLE IF EXISTS sys_function_unit_contents
    DROP CONSTRAINT IF EXISTS chk_content_type;

ALTER TABLE IF EXISTS sys_function_unit_contents
    ADD CONSTRAINT chk_content_type
    CHECK (content_type IN (
        'PROCESS',
        'FORM',
        'DATA_TABLE',
        'SCRIPT',
        'ACTION',
        'MAIN_TABLE_VIEW',
        'EMAIL_TEMPLATE'
    ));
