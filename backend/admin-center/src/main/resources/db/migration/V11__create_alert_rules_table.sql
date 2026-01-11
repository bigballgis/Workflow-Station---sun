-- 告警规则表
CREATE TABLE IF NOT EXISTS admin_alert_rules (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    metric_name VARCHAR(50),
    operator VARCHAR(20),
    threshold DOUBLE PRECISION,
    duration INTEGER,
    severity VARCHAR(20),
    notify_channels VARCHAR(500),
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_alert_rules_name ON admin_alert_rules(name);
CREATE INDEX IF NOT EXISTS idx_alert_rules_enabled ON admin_alert_rules(enabled);
CREATE INDEX IF NOT EXISTS idx_alert_rules_severity ON admin_alert_rules(severity);

COMMENT ON TABLE admin_alert_rules IS '告警规则表';
COMMENT ON COLUMN admin_alert_rules.metric_name IS '监控指标名称';
COMMENT ON COLUMN admin_alert_rules.operator IS '比较运算符：GT, LT, EQ, GTE, LTE';
COMMENT ON COLUMN admin_alert_rules.threshold IS '阈值';
COMMENT ON COLUMN admin_alert_rules.duration IS '持续时间(秒)';
COMMENT ON COLUMN admin_alert_rules.severity IS '告警级别';
COMMENT ON COLUMN admin_alert_rules.notify_channels IS '通知渠道(JSON格式)';
