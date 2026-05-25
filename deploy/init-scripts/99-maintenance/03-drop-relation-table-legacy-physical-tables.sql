-- =============================================================================
-- Drop legacy per-table physical tables for Relation Tables (rt_*)
-- =============================================================================
-- Relation Table row data MUST live in rt_table_data_rows (JSONB), not in
-- PostgreSQL tables named after rt_table_definitions.table_name.
--
-- Older dev DBs may still have tables like public.test created by the previous
-- deploy path. This script removes them safely (only names listed in rt_* defs).
--
-- Usage:
--   psql -h HOST -U USER -d DB -v ON_ERROR_STOP=1 \
--     -f 03-drop-relation-table-legacy-physical-tables.sql
-- =============================================================================

DO $drop_legacy_rt_physical$
DECLARE
    r RECORD;
    dropped_count int := 0;
BEGIN
    FOR r IN
        SELECT t.table_name AS physical_name
        FROM rt_table_definitions t
        WHERE EXISTS (
            SELECT 1
            FROM information_schema.tables ist
            WHERE ist.table_schema = 'public'
              AND ist.table_name = t.table_name
              AND ist.table_type = 'BASE TABLE'
        )
        ORDER BY t.table_name
    LOOP
        EXECUTE format('DROP TABLE IF EXISTS public.%I CASCADE', r.physical_name);
        RAISE NOTICE 'Dropped legacy Relation Table physical table: %', r.physical_name;
        dropped_count := dropped_count + 1;
    END LOOP;

    IF dropped_count = 0 THEN
        RAISE NOTICE 'No legacy Relation Table physical tables found.';
    ELSE
        RAISE NOTICE 'Dropped % legacy Relation Table physical table(s).', dropped_count;
    END IF;
END
$drop_legacy_rt_physical$;
