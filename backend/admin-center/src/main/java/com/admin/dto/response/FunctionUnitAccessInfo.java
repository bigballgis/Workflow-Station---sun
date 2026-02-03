package com.admin.dto.response;

import com.admin.entity.FunctionUnitAccess;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 功能单元访问权限配置响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FunctionUnitAccessInfo {
    
    private String id;
    private String functionUnitId;
    private String functionUnitName;
    private String accessType;  // DEVELOPER, USER
    private String targetType;  // ROLE, USER, VIRTUAL_GROUP
    private String targetId;    // 目标ID
    private String targetName;  // 目标名称（用于显示）
    private Instant createdAt;
    private String createdBy;
    
    // 为了向后兼容，保留 roleId 和 roleName 字段
    public String getRoleId() {
        return "ROLE".equals(targetType) ? targetId : null;
    }
    
    public String getRoleName() {
        return "ROLE".equals(targetType) ? targetName : null;
    }
    
    public static FunctionUnitAccessInfo fromEntity(FunctionUnitAccess entity) {
        return FunctionUnitAccessInfo.builder()
                .id(entity.getId())
                .functionUnitId(entity.getFunctionUnit().getId())
                .functionUnitName(entity.getFunctionUnit().getName())
                .accessType(entity.getAccessType())
                .targetType(entity.getTargetType())
                .targetId(entity.getTargetId())
                .targetName(null)  // 需要从其他服务获取
                .createdAt(entity.getCreatedAt())
                .createdBy(entity.getCreatedBy())
                .build();
    }
}
