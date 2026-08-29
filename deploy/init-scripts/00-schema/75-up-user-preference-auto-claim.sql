-- Per-user To Do preference: claim a free pool task when opening it from To Do.
ALTER TABLE up_user_preference
    ADD COLUMN IF NOT EXISTS auto_claim_on_open BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN up_user_preference.auto_claim_on_open IS
    'When true, opening a claimable pool task from To Do claims it first. Default false.';
