-- =====================================================
-- Notification Table (up_notification)
-- 站内通知表
-- =====================================================
CREATE TABLE IF NOT EXISTS up_notification (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    type VARCHAR(20) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT,
    link VARCHAR(500),
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_up_notification_user_id ON up_notification(user_id);
CREATE INDEX idx_up_notification_created_at ON up_notification(created_at);
CREATE INDEX idx_up_notification_user_created ON up_notification(user_id, created_at DESC);
CREATE INDEX idx_up_notification_user_read ON up_notification(user_id, is_read);

COMMENT ON TABLE up_notification IS '站内通知';
