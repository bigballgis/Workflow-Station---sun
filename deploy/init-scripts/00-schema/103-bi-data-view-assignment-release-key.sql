-- WP12: a BI Data View Assignment names its View by released data — the function unit code and the
-- View name (unique within a function unit) — and is resolved against the unit's active release on
-- every read. Runtime services no longer read Developer Workstation tables, and production has none:
-- the foreign key to dw_function_units made every insert there fail.
--
-- function_unit_id / table_id / view_id stay as legacy columns (nullable, no longer written).
-- Idempotent: safe to re-run.
BEGIN;

ALTER TABLE bi_data_view_assignment ADD COLUMN IF NOT EXISTS function_unit_code VARCHAR(100);
ALTER TABLE bi_data_view_assignment ADD COLUMN IF NOT EXISTS view_name VARCHAR(255);

DO $$
DECLARE
    fk record;
BEGIN
    FOR fk IN
        SELECT c.conname FROM pg_constraint c
        WHERE c.conrelid = 'bi_data_view_assignment'::regclass AND c.contype = 'f'
          AND c.confrelid::regclass::text LIKE 'dw\_%'
    LOOP
        EXECUTE format('ALTER TABLE bi_data_view_assignment DROP CONSTRAINT %I', fk.conname);
    END LOOP;
END $$;

ALTER TABLE bi_data_view_assignment ALTER COLUMN function_unit_id DROP NOT NULL;
ALTER TABLE bi_data_view_assignment ALTER COLUMN table_id DROP NOT NULL;

-- One-time carry-over where Developer Workstation tables exist (dev / UAT). Production cannot hold
-- rows (see above), so this is a no-op there. Part of the migration, not a runtime read.
DO $$
BEGIN
    IF to_regclass('public.dw_function_units') IS NOT NULL
       AND to_regclass('public.dw_main_table_view_configs') IS NOT NULL THEN
        UPDATE bi_data_view_assignment a
        SET function_unit_code = fu.code, view_name = v.view_name
        FROM dw_main_table_view_configs v
        JOIN dw_function_units fu ON fu.id = v.function_unit_id
        WHERE a.function_unit_code IS NULL AND a.view_id = v.id;
        UPDATE bi_data_view_assignment a
        SET function_unit_code = fu.code
        FROM dw_function_units fu
        WHERE a.function_unit_code IS NULL AND a.function_unit_id = fu.id;
    END IF;
END $$;

-- Same-named Views of one unit collapse onto one key: keep the earliest binding, the others stay
-- inspectable (legacy view_id kept) but no longer name a View.
UPDATE bi_data_view_assignment a
SET view_name = NULL, enabled = FALSE
FROM (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY dashboard_id, function_unit_code, view_name
                                  ORDER BY created_at, id) AS rn
    FROM bi_data_view_assignment
    WHERE function_unit_code IS NOT NULL AND view_name IS NOT NULL
) d
WHERE a.id = d.id AND d.rn > 1;

-- What cannot be resolved remains visible for an administrator to retarget (as in script 97).
UPDATE bi_data_view_assignment SET enabled = FALSE
WHERE enabled AND (function_unit_code IS NULL OR view_name IS NULL);

ALTER TABLE bi_data_view_assignment DROP CONSTRAINT IF EXISTS uk_bi_data_view_assignment;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_bi_data_view_assignment_release_view') THEN
        ALTER TABLE bi_data_view_assignment ADD CONSTRAINT uk_bi_data_view_assignment_release_view
            UNIQUE (dashboard_id, function_unit_code, view_name);
    END IF;
END $$;
CREATE INDEX IF NOT EXISTS idx_bi_data_view_assignment_release_view
    ON bi_data_view_assignment(function_unit_code, view_name, enabled);

COMMENT ON COLUMN bi_data_view_assignment.function_unit_code IS
    'Function unit code; the View is resolved in the unit''s active release on every read';
COMMENT ON COLUMN bi_data_view_assignment.view_name IS
    'View name, unique within a function unit; NULL only for an unresolved legacy binding';
COMMENT ON COLUMN bi_data_view_assignment.function_unit_id IS 'Legacy Developer Workstation id; no longer written';
COMMENT ON COLUMN bi_data_view_assignment.table_id IS 'Legacy Developer Workstation id; no longer written';
COMMENT ON COLUMN bi_data_view_assignment.view_id IS 'Legacy Developer Workstation id; no longer written';

COMMIT;
