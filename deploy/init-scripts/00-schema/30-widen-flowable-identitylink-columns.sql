-- =============================================================================
-- Widen Flowable ACT_*_IDENTITYLINK varchar(255) columns
-- =============================================================================
-- Symptom (workflow-engine): Task completion fails with
--   PSQLException: ERROR: value too long for type character varying(255)
--   insert into ACT_HI_IDENTITYLINK (...)
-- Cause: Flowable default DDL uses VARCHAR(255) for GROUP_ID_, SCOPE_* , etc.
--        Long virtual group ids, scope ids, or definition ids exceed 255.
-- Fix: Widen to VARCHAR(4000) on runtime + historic identity link tables.
--
-- Apply (dev example):
--   docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -v ON_ERROR_STOP=1 -f -
-- =============================================================================

ALTER TABLE IF EXISTS act_ru_identitylink
    ALTER COLUMN group_id_ TYPE VARCHAR(4000),
    ALTER COLUMN type_ TYPE VARCHAR(4000),
    ALTER COLUMN user_id_ TYPE VARCHAR(4000),
    ALTER COLUMN scope_id_ TYPE VARCHAR(4000),
    ALTER COLUMN sub_scope_id_ TYPE VARCHAR(4000),
    ALTER COLUMN scope_type_ TYPE VARCHAR(4000),
    ALTER COLUMN scope_definition_id_ TYPE VARCHAR(4000);

ALTER TABLE IF EXISTS act_hi_identitylink
    ALTER COLUMN group_id_ TYPE VARCHAR(4000),
    ALTER COLUMN type_ TYPE VARCHAR(4000),
    ALTER COLUMN user_id_ TYPE VARCHAR(4000),
    ALTER COLUMN scope_id_ TYPE VARCHAR(4000),
    ALTER COLUMN sub_scope_id_ TYPE VARCHAR(4000),
    ALTER COLUMN scope_type_ TYPE VARCHAR(4000),
    ALTER COLUMN scope_definition_id_ TYPE VARCHAR(4000);
