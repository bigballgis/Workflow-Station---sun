-- =============================================================================
-- Widen wf_extended_task_info.assignment_target VARCHAR(255) → TEXT
-- =============================================================================
-- Symptom: Task creation fails when role/BU resolution yields many candidate
--   user IDs joined into assignment_target (comma-separated) exceeding 255 chars.
-- Cause: Column was VARCHAR(255); large CANDIDATE_USERS pools overflow.
-- Fix:
--   1) ALTER column to TEXT
--   2) Drop btree index on the full column (low value for long candidate strings;
--      task claiming uses Flowable identity links)
--   3) Recreate partial index for non-CANDIDATE_USERS rows only (USER / VIRTUAL_GROUP /
--      DEPT_ROLE still benefit from equality lookups)
--
-- Idempotent for existing and fresh databases.
-- =============================================================================

DROP INDEX IF EXISTS idx_assignment_target;

ALTER TABLE IF EXISTS wf_extended_task_info
    ALTER COLUMN assignment_target TYPE TEXT;

DROP INDEX IF EXISTS idx_assignment_target_non_candidate_users;

CREATE INDEX IF NOT EXISTS idx_assignment_target_non_candidate_users
    ON wf_extended_task_info (assignment_target)
    WHERE assignment_type <> 'CANDIDATE_USERS';

COMMENT ON COLUMN wf_extended_task_info.assignment_target IS
    'Assignment target: user/group/dept-role id, or comma-separated candidate user ids (TEXT for large pools)';

COMMENT ON INDEX idx_assignment_target_non_candidate_users IS
    'Equality lookup for non-CANDIDATE_USERS assignment targets; excludes long candidate-user pools';
