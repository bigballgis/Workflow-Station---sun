-- Main Table View access control (BU + Role visibility, data involvement flag)

ALTER TABLE dw_main_table_view_configs
    ADD COLUMN IF NOT EXISTS restrict_to_involved_users BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN dw_main_table_view_configs.restrict_to_involved_users IS
    'When true, portal row data is limited to users involved in each process (initiator, assignee, MI participant).';

CREATE TABLE IF NOT EXISTS dw_main_table_view_access (
    id              BIGSERIAL PRIMARY KEY,
    view_config_id  BIGINT NOT NULL REFERENCES dw_main_table_view_configs(id) ON DELETE CASCADE,
    target_type     VARCHAR(20) NOT NULL,
    target_id       VARCHAR(64) NOT NULL,
    CONSTRAINT chk_mtv_access_target_type CHECK (target_type IN ('ROLE', 'BUSINESS_UNIT'))
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_mtv_access_view_target
    ON dw_main_table_view_access(view_config_id, target_type, target_id);

CREATE INDEX IF NOT EXISTS idx_mtv_access_view_config
    ON dw_main_table_view_access(view_config_id);

COMMENT ON TABLE dw_main_table_view_access IS 'Per-view BU/Role visibility rules for User Portal main-table views';
