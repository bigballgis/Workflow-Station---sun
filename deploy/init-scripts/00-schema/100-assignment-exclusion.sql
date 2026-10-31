-- Node assignment exclusions. Platform runtime tables only; safe to apply repeatedly.
CREATE TABLE IF NOT EXISTS we_assignment_cases (
    case_id UUID PRIMARY KEY,
    root_instance_id VARCHAR(64) UNIQUE,
    initiator_user_id VARCHAR(64) NOT NULL,
    start_bu_code VARCHAR(255),
    start_bu_id VARCHAR(64),
    runtime_contract_version INTEGER NOT NULL CHECK (runtime_contract_version = 1),
    revision BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0),
    authority_revision BIGINT NOT NULL DEFAULT 0 CHECK (authority_revision >= 0),
    applied_revision BIGINT NOT NULL DEFAULT 0,
    portal_revision BIGINT NOT NULL DEFAULT 0,
    accepted_operation_seq BIGINT NOT NULL DEFAULT 0,
    portal_applied_operation_seq BIGINT NOT NULL DEFAULT 0,
    confirmed_operation_seq BIGINT NOT NULL DEFAULT 0,
    pending_reopen_operation_id UUID,
    portal_applied_reopen_operation_id UUID,
    next_evaluation_at TIMESTAMPTZ,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_error VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (applied_revision <= revision),
    CHECK (confirmed_operation_seq <= portal_applied_operation_seq),
    CHECK (portal_applied_operation_seq <= accepted_operation_seq)
);
CREATE INDEX IF NOT EXISTS idx_assignment_case_due
    ON we_assignment_cases(next_attempt_at) WHERE applied_revision < revision;
CREATE INDEX IF NOT EXISTS idx_assignment_case_authority ON we_assignment_cases(authority_revision);
CREATE INDEX IF NOT EXISTS idx_assignment_case_expiry ON we_assignment_cases(next_evaluation_at)
    WHERE next_evaluation_at IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_assignment_case_pending_sync ON we_assignment_cases(case_id)
    WHERE accepted_operation_seq>confirmed_operation_seq;
CREATE INDEX IF NOT EXISTS idx_assignment_case_pending_reopen ON we_assignment_cases(case_id)
    WHERE pending_reopen_operation_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS we_assignment_instances (
    process_instance_id VARCHAR(64) PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES we_assignment_cases(case_id),
    round_id UUID NOT NULL,
    parent_instance_id VARCHAR(64) REFERENCES we_assignment_instances(process_instance_id),
    reopen_source_instance_id VARCHAR(64) REFERENCES we_assignment_instances(process_instance_id),
    reopen_operation_id UUID UNIQUE,
    reopened_by VARCHAR(64),
    reopen_snapshot JSONB CHECK (reopen_snapshot IS NULL OR jsonb_typeof(reopen_snapshot)='object'),
    reopen_mirrored BOOLEAN NOT NULL DEFAULT false,
    reopen_next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    reopen_last_error VARCHAR(128),
    process_definition_id VARCHAR(255) NOT NULL,
    function_unit_code VARCHAR(255) NOT NULL,
    call_path JSONB NOT NULL CHECK (jsonb_typeof(call_path) = 'array'),
    state VARCHAR(24) NOT NULL CHECK (state IN ('ACTIVE','COMPLETED','CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (parent_instance_id IS NULL OR reopen_source_instance_id IS NULL)
);
CREATE INDEX IF NOT EXISTS idx_assignment_instance_round ON we_assignment_instances(case_id,round_id);
CREATE INDEX IF NOT EXISTS idx_assignment_instance_parent ON we_assignment_instances(parent_instance_id);
CREATE INDEX IF NOT EXISTS idx_assignment_reopen_pending ON we_assignment_instances(created_at)
    WHERE reopen_operation_id IS NOT NULL AND NOT reopen_mirrored;
CREATE UNIQUE INDEX IF NOT EXISTS idx_assignment_one_active_root ON we_assignment_instances(case_id)
    WHERE parent_instance_id IS NULL AND state = 'ACTIVE';

CREATE TABLE IF NOT EXISTS we_assignment_task_states (
    task_id VARCHAR(64) PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES we_assignment_cases(case_id),
    process_instance_id VARCHAR(64) NOT NULL REFERENCES we_assignment_instances(process_instance_id),
    process_definition_id VARCHAR(255) NOT NULL,
    node_key VARCHAR(255) NOT NULL,
    slot_key VARCHAR(64) NOT NULL,
    slot_identity JSONB NOT NULL CHECK (jsonb_typeof(slot_identity) = 'object'),
    policy_hash VARCHAR(64) NOT NULL,
    primary_candidates JSONB NOT NULL CHECK (jsonb_typeof(primary_candidates) = 'array'),
    source_spec JSONB CHECK (source_spec IS NULL OR jsonb_typeof(source_spec) = 'object'),
    eligible_candidates JSONB NOT NULL DEFAULT '[]' CHECK (jsonb_typeof(eligible_candidates) = 'array'),
    pool_source VARCHAR(16) CHECK (pool_source IN ('PRIMARY','BACKUP')),
    holder_source VARCHAR(16) CHECK (holder_source IN ('PRIMARY','BACKUP')),
    state VARCHAR(40) NOT NULL,
    synced_revision BIGINT NOT NULL DEFAULT 0,
    authority_revision BIGINT NOT NULL DEFAULT 0,
    released_holder_id VARCHAR(64),
    released_at TIMESTAMPTZ,
    release_history JSONB NOT NULL DEFAULT '[]' CHECK (jsonb_typeof(release_history) = 'array'),
    next_evaluation_at TIMESTAMPTZ,
    handoff_consumed BOOLEAN NOT NULL DEFAULT false,
    last_evaluated_at TIMESTAMPTZ,
    active BOOLEAN NOT NULL DEFAULT true
);
CREATE INDEX IF NOT EXISTS idx_assignment_active_task ON we_assignment_task_states(case_id,synced_revision) WHERE active;

CREATE TABLE IF NOT EXISTS we_assignment_completion_records (
    task_id VARCHAR(64) PRIMARY KEY REFERENCES we_assignment_task_states(task_id),
    case_id UUID NOT NULL REFERENCES we_assignment_cases(case_id),
    round_id UUID NOT NULL,
    process_instance_id VARCHAR(64) NOT NULL REFERENCES we_assignment_instances(process_instance_id),
    process_definition_id VARCHAR(255) NOT NULL,
    function_unit_code VARCHAR(255) NOT NULL,
    node_key VARCHAR(255) NOT NULL,
    slot_key VARCHAR(64) NOT NULL,
    slot_identity JSONB NOT NULL,
    completion_seq BIGINT NOT NULL,
    actor_user_id VARCHAR(64) NOT NULL,
    responsible_user_ids JSONB NOT NULL CHECK (jsonb_typeof(responsible_user_ids) = 'array' AND jsonb_array_length(responsible_user_ids) > 0),
    is_effective BOOLEAN NOT NULL DEFAULT true,
    completed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_assignment_effective_slot
    ON we_assignment_completion_records(case_id,round_id,slot_key) WHERE is_effective;
CREATE INDEX IF NOT EXISTS idx_assignment_effective_history
    ON we_assignment_completion_records(case_id,function_unit_code,node_key) WHERE is_effective;

CREATE TABLE IF NOT EXISTS we_assignment_operations (
    operation_id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES we_assignment_cases(case_id),
    task_id VARCHAR(64) NOT NULL UNIQUE REFERENCES we_assignment_completion_records(task_id),
    actor_user_id VARCHAR(64) NOT NULL,
    represented_user_id VARCHAR(64),
    operation VARCHAR(32) NOT NULL,
    request_digest VARCHAR(64) NOT NULL,
    accepted_effect JSONB NOT NULL,
    operation_seq BIGINT NOT NULL,
    portal_base_revision BIGINT NOT NULL,
    state VARCHAR(24) NOT NULL CHECK (state IN ('PENDING_SYNC','APPLIED','SYNC_CONFLICT')),
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_error VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(case_id,operation_seq)
);
CREATE INDEX IF NOT EXISTS idx_assignment_operation_due ON we_assignment_operations(next_attempt_at)
    WHERE state = 'PENDING_SYNC';

CREATE TABLE IF NOT EXISTS up_assignment_operation_sync (
    operation_id UUID PRIMARY KEY,
    case_id UUID NOT NULL,
    operation_seq BIGINT NOT NULL,
    request_digest VARCHAR(64) NOT NULL,
    effect_digest VARCHAR(64) NOT NULL,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(case_id,operation_seq)
);

CREATE TABLE IF NOT EXISTS up_assignment_reopen_sync (
    operation_id UUID PRIMARY KEY,
    case_id UUID NOT NULL,
    source_instance_id VARCHAR(64) NOT NULL,
    process_instance_id VARCHAR(64) NOT NULL UNIQUE,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Retained after deletion so a missing mirror can never be mistaken for an initial Engine-only start.
CREATE TABLE IF NOT EXISTS up_assignment_source_presence (
    process_instance_id VARCHAR(64) PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- A narrow shared coordination row. Admin and Portal publish their own qualification changes.
CREATE TABLE IF NOT EXISTS ac_assignment_authority_fence (
    scope VARCHAR(32) PRIMARY KEY CHECK (scope = 'platform'),
    revision BIGINT NOT NULL CHECK (revision >= 0)
);
INSERT INTO ac_assignment_authority_fence(scope,revision) VALUES('platform',0) ON CONFLICT(scope) DO NOTHING;
