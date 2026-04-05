package com.workflow.component;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 行级权限策略（原 {@link DataAccessSecurityComponent} 内嵌类型，顶层化以便编译/序列化稳定）。
 */
public class RowLevelPolicy {
    private String policyId;
    private String tableName;
    private String conditionExpression;
    private Set<String> allowedRoles;
    private Set<String> allowedUsers;
    private String description;
    private boolean enabled;
    private LocalDateTime createdTime;

    public String getPolicyId() {
        return policyId;
    }

    public void setPolicyId(String policyId) {
        this.policyId = policyId;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getConditionExpression() {
        return conditionExpression;
    }

    public void setConditionExpression(String conditionExpression) {
        this.conditionExpression = conditionExpression;
    }

    public Set<String> getAllowedRoles() {
        return allowedRoles;
    }

    public void setAllowedRoles(Set<String> allowedRoles) {
        this.allowedRoles = allowedRoles;
    }

    public Set<String> getAllowedUsers() {
        return allowedUsers;
    }

    public void setAllowedUsers(Set<String> allowedUsers) {
        this.allowedUsers = allowedUsers;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }
}
