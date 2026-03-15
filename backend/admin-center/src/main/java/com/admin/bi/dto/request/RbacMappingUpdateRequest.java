package com.admin.bi.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * RBAC 映射更新请求（全量替换）
 */
@Data
@NoArgsConstructor
public class RbacMappingUpdateRequest {

    /** Superset 角色 ID 列表 */
    @NotNull(message = "Superset role IDs are required")
    private List<Integer> supersetRoleIds;
}
