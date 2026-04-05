package com.workflow.component;

import java.util.List;

/**
 * 安全告警规则（原 {@link DataAccessSecurityComponent} 内嵌类型）。
 */
public class AlertRule {
    private String ruleId;
    private String ruleName;
    private String eventType;
    private int threshold;
    private int timeWindowMinutes;
    private String severity;
    private List<String> notifyChannels;
    private List<String> notifyUsers;
    private boolean enabled;

    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public int getThreshold() {
        return threshold;
    }

    public void setThreshold(int threshold) {
        this.threshold = threshold;
    }

    public int getTimeWindowMinutes() {
        return timeWindowMinutes;
    }

    public void setTimeWindowMinutes(int timeWindowMinutes) {
        this.timeWindowMinutes = timeWindowMinutes;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public List<String> getNotifyChannels() {
        return notifyChannels;
    }

    public void setNotifyChannels(List<String> notifyChannels) {
        this.notifyChannels = notifyChannels;
    }

    public List<String> getNotifyUsers() {
        return notifyUsers;
    }

    public void setNotifyUsers(List<String> notifyUsers) {
        this.notifyUsers = notifyUsers;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
