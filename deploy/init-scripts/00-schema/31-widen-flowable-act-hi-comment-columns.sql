-- =============================================================================
-- Widen Flowable ACT_HI_COMMENT columns (PostgreSQL)
-- =============================================================================
-- Symptom: Task completion fails with
--   PSQLException: value too long for type character varying(255)
--   insert into ACT_HI_COMMENT (..., USER_ID_, ..., ACTION_, MESSAGE_, FULL_MSG_) ...
-- Cause: Legacy DDL may keep MESSAGE_/ACTION_/TYPE_/USER_ID_ at 255, or FULL_MSG_ as
--        varchar instead of bytea. Any of these can trigger the error depending on payload.
-- Fix: Widen text columns; convert FULL_MSG_ to bytea when it is still a character type.
--
-- Re-run anytime (idempotent). If act_hi_comment does not exist yet, skip via IF EXISTS.
-- =============================================================================

-- String columns (split so one failure does not block the rest)
ALTER TABLE IF EXISTS act_hi_comment ALTER COLUMN message_ TYPE TEXT;
ALTER TABLE IF EXISTS act_hi_comment ALTER COLUMN action_ TYPE VARCHAR(4000);
ALTER TABLE IF EXISTS act_hi_comment ALTER COLUMN type_ TYPE VARCHAR(4000);
ALTER TABLE IF EXISTS act_hi_comment ALTER COLUMN user_id_ TYPE VARCHAR(4000);

-- FULL_MSG_ must be bytea in Flowable 7; old installs sometimes used varchar(255).
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
