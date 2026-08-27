-- =============================================================================
-- Widen sys_function_unit_contents.chk_content_type for DECISION and TABLE_RELATION
-- =============================================================================
-- Admin Function Unit import persists decisions/*.dmn and relations/table_relations.json
-- as catalog blobs. Fail closed: do not skip those files.
-- Idempotent for existing and fresh databases (append-only; do not edit 01-*.sql or 61-*.sql).
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
        'EMAIL_TEMPLATE',
        'DECISION',
        'TABLE_RELATION'
    ));
