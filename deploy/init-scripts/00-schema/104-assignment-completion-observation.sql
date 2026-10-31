-- A post-commit MI observation is cached with its completion, so retries do not append duplicate audit entries.
ALTER TABLE up_assignment_operation_sync ADD COLUMN IF NOT EXISTS completion_observation JSONB;
