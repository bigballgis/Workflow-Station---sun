-- Freeze optional post-action side effects with the Portal completion ledger, never with later live values.
ALTER TABLE up_assignment_operation_sync ADD COLUMN IF NOT EXISTS post_action_context JSONB;
ALTER TABLE up_assignment_operation_sync ADD COLUMN IF NOT EXISTS post_action_dispatched BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE up_assignment_operation_sync ADD COLUMN IF NOT EXISTS post_action_next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now();
CREATE INDEX IF NOT EXISTS idx_assignment_post_action_due ON up_assignment_operation_sync(post_action_next_attempt_at)
    WHERE post_action_context IS NOT NULL AND NOT post_action_dispatched;
