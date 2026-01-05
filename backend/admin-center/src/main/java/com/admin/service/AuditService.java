package com.admin.service;

import com.admin.dto.response.BatchImportResult;
import com.admin.entity.PermissionConflict;
import com.admin.entity.PermissionDelegation;
import com.admin.entity.User;
import com.admin.enums.UserStatus;

/**
 * 审计服务接口
 */
public interface AuditService {
    
    /**
     * 记录用户创建
     */
    void recordUserCreation(User user);
    
    /**
     * 记录用户更新
     */
    void recordUserUpdate(User newUser, User oldUser);
    
    /**
     * 记录用户删除
     */
    void recordUserDeletion(User user);
    
    /**
     * 记录状态变更
     */
    void recordStatusChange(User user, UserStatus oldStatus, UserStatus newStatus, String reason);
    
    /**
     * 记录密码重置
     */
    void recordPasswordReset(User user);
    
    /**
     * 记录批量导入
     */
    void recordBatchImport(BatchImportResult result);
    
    /**
     * 记录登录
     */
    void recordLogin(String userId, String username, String ipAddress, boolean success);
    
    /**
     * 记录登出
     */
    void recordLogout(String userId, String username);
    
    /**
     * 记录权限委托
     */
    void recordPermissionDelegation(PermissionDelegation delegation);
    
    /**
     * 记录权限委托撤销
     */
    void recordPermissionDelegationRevocation(PermissionDelegation delegation, String revokedBy, String reason);
    
    /**
     * 记录权限冲突检测
     */
    void recordPermissionConflictDetection(PermissionConflict conflict);
    
    /**
     * 记录权限冲突解决
     */
    void recordPermissionConflictResolution(PermissionConflict conflict, String resolvedBy);
}
