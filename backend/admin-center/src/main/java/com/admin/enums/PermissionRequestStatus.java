package com.admin.enums;

/**
 * 权限申请状态枚举
 */
public enum PermissionRequestStatus {
    /**
     * 待审批
     */
    PENDING,
    
    /**
     * 已批准
     */
    APPROVED,
    
    /**
     * 已拒绝
     */
    REJECTED,
    
    /**
     * 已取消
     */
    CANCELLED
}
