package com.admin.enums;

/**
 * 权限冲突解决策略枚举
 */
public enum ConflictResolutionStrategy {
    /**
     * 拒绝策略 - 当发现权限冲突时，拒绝授予权限
     */
    DENY,
    
    /**
     * 允许策略 - 当发现权限冲突时，仍然允许授予权限
     */
    ALLOW,
    
    /**
     * 最高权限策略 - 当发现权限冲突时，保留最高级别的权限
     */
    HIGHEST_PRIVILEGE,
    
    /**
     * 最低权限策略 - 当发现权限冲突时，保留最低级别的权限
     */
    LOWEST_PRIVILEGE,
    
    /**
     * 最新权限策略 - 当发现权限冲突时，保留最新授予的权限
     */
    LATEST,
    
    /**
     * 手动解决策略 - 当发现权限冲突时，需要管理员手动解决
     */
    MANUAL
}