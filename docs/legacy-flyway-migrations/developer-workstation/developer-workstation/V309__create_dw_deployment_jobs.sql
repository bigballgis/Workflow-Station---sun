-- 功能单元一键部署到管理中心的异步任务状态（多实例/重启后可查询）
CREATE TABLE IF NOT EXISTS dw_deployment_jobs (
    id VARCHAR(36) PRIMARY KEY,
    function_unit_id BIGINT NOT NULL,
    target_admin_url VARCHAR(1024),
    status VARCHAR(32) NOT NULL,
    progress INTEGER,
    message TEXT,
    version_number VARCHAR(64),
    change_log TEXT,
    steps_json TEXT,
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dw_deployment_job_function_unit FOREIGN KEY (function_unit_id) REFERENCES dw_function_units(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_dw_deployment_jobs_function_unit_started ON dw_deployment_jobs(function_unit_id, started_at DESC);
