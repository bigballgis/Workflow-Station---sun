-- V10: 用户偏好设置表
-- 用于存储用户的各种偏好设置，如"不再提醒"等

CREATE TABLE IF NOT EXISTS sys_user_preferences (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    preference_key VARCHAR(100) NOT NULL,
    preference_value VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    UNIQUE (user_id, preference_key),
    FOREIGN KEY (user_id) REFERENCES sys_users(id) ON DELETE CASCADE
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_user_preferences_user_id ON sys_user_preferences(user_id);
CREATE INDEX IF NOT EXISTS idx_user_preferences_key ON sys_user_preferences(preference_key);

COMMENT ON TABLE sys_user_preferences IS '用户偏好设置表';
COMMENT ON COLUMN sys_user_preferences.id IS '主键ID';
COMMENT ON COLUMN sys_user_preferences.user_id IS '用户ID';
COMMENT ON COLUMN sys_user_preferences.preference_key IS '偏好设置键';
COMMENT ON COLUMN sys_user_preferences.preference_value IS '偏好设置值';
COMMENT ON COLUMN sys_user_preferences.created_at IS '创建时间';
COMMENT ON COLUMN sys_user_preferences.updated_at IS '更新时间';
