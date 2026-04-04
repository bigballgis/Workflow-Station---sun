-- 与 backend/user-portal Flyway V405 对齐：历史库可能缺少 submitted_by_user_id，导致 GET /permissions/requests JDBC 查询 500
ALTER TABLE up_permission_request
    ADD COLUMN IF NOT EXISTS submitted_by_user_id VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_up_permission_request_submitted_by
    ON up_permission_request (submitted_by_user_id);

COMMENT ON COLUMN up_permission_request.submitted_by_user_id IS '登录提交人 userId；为空表示历史数据（视同本人提交）';
