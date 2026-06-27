-- 功能单元与虚拟开发组分配（开发者工作站工作区隔离）
CREATE TABLE IF NOT EXISTS dw_function_unit_dev_groups (
    id              BIGSERIAL PRIMARY KEY,
    function_unit_id BIGINT NOT NULL REFERENCES dw_function_units(id) ON DELETE CASCADE,
    virtual_group_id VARCHAR(64) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(64),
    CONSTRAINT uq_dw_fu_dev_group UNIQUE (function_unit_id, virtual_group_id)
);

CREATE INDEX IF NOT EXISTS idx_dw_fu_dev_group_fu ON dw_function_unit_dev_groups(function_unit_id);
CREATE INDEX IF NOT EXISTS idx_dw_fu_dev_group_vg ON dw_function_unit_dev_groups(virtual_group_id);

COMMENT ON TABLE dw_function_unit_dev_groups IS 'Team Lead 将设计站功能单元分配给虚拟开发组；组成员（Developer）可编辑/部署';
