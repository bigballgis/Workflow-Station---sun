package com.admin.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 权限配置DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionConfig {
    
    private String permissionId;
    private String conditionType;
    private String conditionValue;
}
