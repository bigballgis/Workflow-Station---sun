package com.admin.enums;

/**
 * 部署状态枚举
 */
public enum DeploymentStatus {
    /**
     * 待部署
     */
    PENDING,
    
    /**
     * 待审批
     */
    PENDING_APPROVAL,
    
    /**
     * 已审批
     */
    APPROVED,
    
    /**
     * 部署中
     */
    IN_PROGRESS,
    
    /**
     * 正在部署
     */
    DEPLOYING,
    
    /**
     * 部署成功
     */
    SUCCESS,
    
    /**
     * 部署失败
     */
    FAILED,
    
    /**
     * 已取消
     */
    CANCELLED,
    
    /**
     * 正在回滚
     */
    ROLLING_BACK,
    
    /**
     * 已回滚
     */
    ROLLED_BACK,
    
    /**
     * 回滚失败
     */
    ROLLBACK_FAILED
}
