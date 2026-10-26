-- Expand legacy table assignments once. Replaying this script must never grant a newly created View.
BEGIN;
ALTER TABLE bi_dashboard_assignment ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE bi_data_view_assignment ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT TRUE;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = current_schema() AND table_name = 'bi_data_view_assignment'
                     AND column_name = 'view_id') THEN
        -- Keep the exact pre-migration configuration for audit, exception handling and rollback.
        CREATE TABLE bi_data_view_assignment_legacy AS TABLE bi_data_view_assignment;
        ALTER TABLE bi_data_view_assignment ADD COLUMN view_id BIGINT;
        ALTER TABLE bi_data_view_assignment DROP CONSTRAINT uk_bi_data_view_assignment;
        -- Retain the original assignment ID for its first View; deterministically identify added bindings.
        INSERT INTO bi_data_view_assignment
            (id, dashboard_id, function_unit_id, table_id, view_id, enabled,
             created_at, created_by, updated_at, updated_by)
        SELECT md5(a.id || ':view:' || v.id), a.dashboard_id, a.function_unit_id, a.table_id, v.id,
               a.enabled, a.created_at, a.created_by, a.updated_at, a.updated_by
        FROM bi_data_view_assignment_legacy a
        JOIN dw_main_table_view_configs v
          ON v.function_unit_id = a.function_unit_id AND v.main_table_id = a.table_id AND v.status = 'PUBLISHED'
        WHERE v.id <> (SELECT MIN(v2.id) FROM dw_main_table_view_configs v2
                        WHERE v2.function_unit_id = a.function_unit_id AND v2.main_table_id = a.table_id
                          AND v2.status = 'PUBLISHED');
        UPDATE bi_data_view_assignment a
        SET view_id = (SELECT MIN(v.id) FROM dw_main_table_view_configs v
                       WHERE v.function_unit_id = a.function_unit_id AND v.main_table_id = a.table_id
                         AND v.status = 'PUBLISHED')
        WHERE a.id IN (SELECT id FROM bi_data_view_assignment_legacy);
        -- Unresolvable legacy records remain visible for an administrator to retarget.
        UPDATE bi_data_view_assignment SET enabled = FALSE WHERE view_id IS NULL;
        ALTER TABLE bi_data_view_assignment ADD CONSTRAINT uk_bi_data_view_assignment UNIQUE (dashboard_id, view_id);
    END IF;
END $$;

-- Deliberately no cascading FK to View: delete/import must preserve an inspectable invalid assignment.
CREATE INDEX IF NOT EXISTS idx_bi_data_view_assignment_view ON bi_data_view_assignment(view_id, enabled);
COMMENT ON COLUMN bi_data_view_assignment.view_id IS 'Concrete Table View identity; NULL only for unresolved legacy configuration';
COMMIT;

SELECT id, dashboard_id, function_unit_id, table_id AS unresolved_legacy_table
FROM bi_data_view_assignment WHERE view_id IS NULL;
