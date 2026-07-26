-- =====================================================
-- 56. Developer Workstation: User Preferences (dw_user_preferences)
--     跨设备/跨浏览器跟随账号的用户级 UI 偏好
--     （当前用途：FU 列表 Launchpad 布局——排序 + 分组）。
--     pref_value 为前端自定义 JSON，后端只按 (user_id, pref_key) 存取、不解析。
-- =====================================================
CREATE TABLE IF NOT EXISTS dw_user_preferences (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    pref_key VARCHAR(64) NOT NULL,
    pref_value TEXT NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_dw_user_pref UNIQUE (user_id, pref_key)
);

COMMENT ON TABLE dw_user_preferences IS 'Per-user UI preferences for developer workstation (e.g. launchpad layout)';
