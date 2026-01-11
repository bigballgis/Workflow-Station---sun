package com.admin.dto.response;

import com.admin.entity.FunctionUnitAccess;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 功能单元访问权限配置响应
 * 简化后只支持角色分配
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FunctionUnitAccessInfo {
    
    private String id;
    private String functionUnitId;
    private String functionUnitName;
    private String roleId;
    private String roleName;
    private Instant createdAt;
    private String createdBy;
    
    public static FunctionUnitAccessInfo fromEntity(FunctionUnitAccess entity) {
        return FunctionUnitAccessInfo.builder()
                .id(entity.getId())
                .functionUnitId(entity.getFunctionUnit().getId())
                .functionUnitName(entity.getFunctionUnit().getName())
                .roleId(entity.getRoleId())
                .roleName(entity.getRoleName())
                .createdAt(entity.getCreatedAt())
                .createdBy(entity.getCreatedBy())
                .build();
    }
}
