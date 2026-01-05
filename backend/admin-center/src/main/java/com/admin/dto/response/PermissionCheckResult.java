package com.admin.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 权限检查结果DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionCheckResult {
    
    private boolean allowed;
    private String resource;
    private String action;
    private String roleId;
    private String roleName;
    private String conditionType;
    private String conditionValue;
    private String reason;
    
    public static PermissionCheckResult allowed(String roleId, String roleName) {
        return PermissionCheckResult.builder()
                .allowed(true)
                .roleId(roleId)
                .roleName(roleName)
                .build();
    }
    
    public static PermissionCheckResult denied() {
        return PermissionCheckResult.builder()
                .allowed(false)
                .reason("没有相应权限")
                .build();
    }
    
    public static PermissionCheckResult denied(String reason) {
        return PermissionCheckResult.builder()
                .allowed(false)
                .reason(reason)
                .build();
    }
}
