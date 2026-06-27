-- 权限申请：记录实际提交人（代办场景 applicant=受益人）
ALTER TABLE up_permission_request
    ADD COLUMN IF NOT EXISTS submitted_by_user_id VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_up_permission_request_submitted_by
    ON up_permission_request (submitted_by_user_id);

COMMENT ON COLUMN up_permission_request.submitted_by_user_id IS '登录提交人 userId；为空表示历史数据（视同本人提交）';
