-- A task-level cursor remains fair while case/authority versions continue changing.
ALTER TABLE we_assignment_task_states ADD COLUMN IF NOT EXISTS last_evaluated_at TIMESTAMPTZ;
CREATE INDEX IF NOT EXISTS idx_assignment_task_last_evaluated
    ON we_assignment_task_states(last_evaluated_at) WHERE active;
