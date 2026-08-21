package com.admin.dto.response;

import com.admin.entity.FunctionUnitAuditAccess;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 功能单元审计授权响应。
 *
 * <p>{@code targetCode} 是 user-portal 的匹配依据 —— 角色 id 跨环境会变，code 不会。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FunctionUnitAuditAccessInfo {

    private String id;
    private String functionUnitId;
    private String functionUnitName;
    private String targetType;
    private String targetId;
    private String targetName;
    private String targetCode;
    private Instant createdAt;
    private String createdBy;

    public String getRoleId() {
        return FunctionUnitAuditAccess.TARGET_TYPE_ROLE.equals(targetType) ? targetId : null;
    }

    public String getRoleCode() {
        return FunctionUnitAuditAccess.TARGET_TYPE_ROLE.equals(targetType) ? targetCode : null;
    }

    public String getRoleName() {
        return FunctionUnitAuditAccess.TARGET_TYPE_ROLE.equals(targetType) ? targetName : null;
    }

    public static FunctionUnitAuditAccessInfo fromEntity(FunctionUnitAuditAccess entity) {
        return FunctionUnitAuditAccessInfo.builder()
                .id(entity.getId())
                .functionUnitId(entity.getFunctionUnit().getId())
                .functionUnitName(entity.getFunctionUnit().getName())
                .targetType(entity.getTargetType())
                .targetId(entity.getTargetId())
                .createdAt(entity.getCreatedAt())
                .createdBy(entity.getCreatedBy())
                .build();
    }
}
