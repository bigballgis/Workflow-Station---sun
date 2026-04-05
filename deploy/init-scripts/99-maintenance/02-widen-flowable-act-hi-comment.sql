-- =============================================================================
-- Maintenance: fix ACT_HI_COMMENT varchar(255) on task complete (existing DB)
-- =============================================================================
-- Same logic as 00-schema/31-widen-flowable-act-hi-comment-columns.sql (inlined so
-- `psql -f -` / docker pipe works; do not use \\ir here).
--
--   psql -h localhost -U platform_dev -d workflow_platform_dev -v ON_ERROR_STOP=1 \
--     -f deploy/init-scripts/99-maintenance/02-widen-flowable-act-hi-comment.sql
--
-- Verify:  \d act_hi_comment
-- =============================================================================

ALTER TABLE IF EXISTS act_hi_comment ALTER COLUMN message_ TYPE TEXT;
ALTER TABLE IF EXISTS act_hi_comment ALTER COLUMN action_ TYPE VARCHAR(4000);
ALTER TABLE IF EXISTS act_hi_comment ALTER COLUMN type_ TYPE VARCHAR(4000);
ALTER TABLE IF EXISTS act_hi_comment ALTER COLUMN user_id_ TYPE VARCHAR(4000);

DO $widen_full_msg$
DECLARE
    dt text;
BEGIN
    SELECT c.data_type INTO dt
    FROM information_schema.columns c
    WHERE c.table_schema = 'public'
      AND c.table_name = 'act_hi_comment'
      AND c.column_name = 'full_msg_';
    IF dt IS NULL THEN
        RETURN;
    END IF;
    IF dt = 'bytea' THEN
        RETURN;
    END IF;
    EXECUTE $sql$
        ALTER TABLE act_hi_comment
        ALTER COLUMN full_msg_ TYPE bytea
        USING CASE
            WHEN full_msg_ IS NULL THEN NULL::bytea
            ELSE convert_to(full_msg_::text, 'UTF8')
        END
    $sql$;
END
$widen_full_msg$;
