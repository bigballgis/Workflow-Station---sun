-- Function Unit calls Function Unit (cross-FU sub-process via BPMN callActivity).
-- Idempotent so it can upgrade an existing database.
--
-- Two independent concerns:
--   1. startup_mode  — design-time declaration of how a Function Unit may be started.
--   2. parent/child  — runtime link from a called child process instance back to its caller.

-- ---------------------------------------------------------------------------
-- 1. How may this Function Unit be started?
--
-- STANDALONE : only a user can start it from the Portal catalog (default —
--              every pre-existing Function Unit keeps exactly today's behaviour).
-- CALLABLE   : only another Function Unit may call it; it is not offered as a
--              standalone Portal entry.
-- BOTH       : either way.
--
-- Declared explicitly rather than inferred from role grants: "nobody is granted
-- a role on it" and "it is designed to be called" are different facts, and
-- inferring one from the other is exactly the heuristic the config-over-
-- heuristics rule forbids.
-- ---------------------------------------------------------------------------
ALTER TABLE dw_function_units
    ADD COLUMN IF NOT EXISTS startup_mode VARCHAR(16) NOT NULL DEFAULT 'STANDALONE';

COMMENT ON COLUMN dw_function_units.startup_mode IS
    'How this Function Unit may be started: STANDALONE (user-initiated only, default), CALLABLE (only via another FU''s callActivity), BOTH';

-- ---------------------------------------------------------------------------
-- 2. Caller -> callee process instance link.
--
-- Flowable itself remains the source of truth for the parent/child relation
-- (superProcessInstanceId). These columns mirror it on the portal side so that
-- listing a parent's children is one indexed query instead of a call into the
-- engine per request — the N+1 shape that has bitten portal hot paths before.
--
-- call_activity_id is the BPMN element id of the callActivity that spawned the
-- child. It is what groups children when one callActivity is multi-instance and
-- therefore produces several child instances.
-- ---------------------------------------------------------------------------
ALTER TABLE up_process_instance
    ADD COLUMN IF NOT EXISTS parent_process_instance_id VARCHAR(64);
ALTER TABLE up_process_instance
    ADD COLUMN IF NOT EXISTS call_activity_id VARCHAR(255);

COMMENT ON COLUMN up_process_instance.parent_process_instance_id IS
    'Calling (parent) process instance id when this instance was started by a BPMN callActivity; NULL for user-initiated instances';
COMMENT ON COLUMN up_process_instance.call_activity_id IS
    'BPMN element id of the callActivity that started this instance; groups siblings when that callActivity is multi-instance';

CREATE INDEX IF NOT EXISTS idx_up_pi_parent
    ON up_process_instance(parent_process_instance_id);
CREATE INDEX IF NOT EXISTS idx_up_pi_parent_call_activity
    ON up_process_instance(parent_process_instance_id, call_activity_id);
