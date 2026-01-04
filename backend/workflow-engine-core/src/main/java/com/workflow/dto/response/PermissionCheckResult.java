package com.workflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 权限检查结果DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionCheckResult {
    
    /**
     * 是否允许
     */
    private boolean allowed;
    
    /**
     * 消息
     */
    private String message;
    
    /**
     * 授权角色（如果允许）
     */
    private String grantedByRole;
    
    /**
     * 匹配的权限
     */
    private String matchedPermission;
    
    /**
     * 检查时间
     */
    private LocalDateTime checkTime;
    
    /**
     * 创建允许结果
     */
    public static PermissionCheckResult allowed(String role, String permission) {
        return PermissionCheckResult.builder()
                .allowed(true)
                .message("权限检查通过")
                .grantedByRole(role)
                .matchedPermission(permission)
                .checkTime(LocalDateTime.now())
                .build();
    }
    
    /**
     * 创建拒绝结果
     */
    public static PermissionCheckResult denied(String message) {
        return PermissionCheckResult.builder()
                .allowed(false)
                .message(message)
                .checkTime(LocalDateTime.now())
                .build();
    }
}
