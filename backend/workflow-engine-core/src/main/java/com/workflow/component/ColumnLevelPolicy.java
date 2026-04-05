package com.workflow.component;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 列级权限策略（原 {@link DataAccessSecurityComponent} 内嵌类型）。
 */
public class ColumnLevelPolicy {
    private String policyId;
    private String tableName;
    private Set<String> visibleColumns;
    private Set<String> hiddenColumns;
    private Set<String> maskedColumns;
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

    public Set<String> getVisibleColumns() {
        return visibleColumns;
    }

    public void setVisibleColumns(Set<String> visibleColumns) {
        this.visibleColumns = visibleColumns;
    }

    public Set<String> getHiddenColumns() {
        return hiddenColumns;
    }

    public void setHiddenColumns(Set<String> hiddenColumns) {
        this.hiddenColumns = hiddenColumns;
    }

    public Set<String> getMaskedColumns() {
        return maskedColumns;
    }

    public void setMaskedColumns(Set<String> maskedColumns) {
        this.maskedColumns = maskedColumns;
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
