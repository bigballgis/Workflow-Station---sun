-- =============================================================================
-- Repair Flowable schema after TRUNCATE or corrupt act_ge_property (engine NPE)
-- =============================================================================
-- Symptom: workflow-engine fails with
--   NullPointerException: dbVersionProperty is null
--   at ProcessDbSchemaManager.schemaUpdate
-- Cause: act_* tables exist but act_ge_property has no schema version row (e.g. full
-- TRUNCATE of Flowable tables without DROP).
--
-- Fix: DROP all public.act_* and public.flw_* tables. On next workflow-engine start,
-- Flowable recreates DDL (database-schema-update=true in docker profile).
--
-- Usage (dev Docker; user/db match deploy/environments/dev/.env, e.g. platform_dev / workflow_platform_dev):
--   docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -v ON_ERROR_STOP=1 -f - < 01-repair-flowable-schema.sql
-- PowerShell:
--   Get-Content 01-repair-flowable-schema.sql -Raw | docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -v ON_ERROR_STOP=1
-- =============================================================================

BEGIN;

DO $flw$
DECLARE
    r RECORD;
    dropped_count int := 0;
BEGIN
    FOR r IN
        SELECT tablename
        FROM pg_tables
        WHERE schemaname = 'public'
          AND (
              tablename LIKE 'act\_%' ESCAPE '\'
              OR tablename LIKE 'flw\_%' ESCAPE '\'
          )
        ORDER BY tablename
    LOOP
        EXECUTE format('DROP TABLE IF EXISTS public.%I CASCADE', r.tablename);
        RAISE NOTICE 'Dropped Flowable table: %', r.tablename;
        dropped_count := dropped_count + 1;
    END LOOP;

    IF dropped_count = 0 THEN
        RAISE NOTICE 'No act_* / flw_* tables found.';
    ELSE
        RAISE NOTICE 'Dropped % Flowable table(s). Restart workflow-engine container.', dropped_count;
    END IF;
END
$flw$;

COMMIT;
