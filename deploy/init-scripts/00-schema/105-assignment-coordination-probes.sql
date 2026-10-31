-- Shared neutral startup/first-use proof. Never stores workflow facts or business values.
CREATE TABLE IF NOT EXISTS we_assignment_coordination_probes (
    probe_id UUID PRIMARY KEY,
    nonce UUID NOT NULL,
    participant VARCHAR(64) NOT NULL CHECK (participant IN ('ADMIN_CENTER','USER_PORTAL')),
    relation_oids JSONB NOT NULL CHECK (jsonb_typeof(relation_oids)='object'),
    acknowledgement UUID,
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_we_assignment_coordination_probes_expiry ON we_assignment_coordination_probes(expires_at);
