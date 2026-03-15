package com.admin.bi.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * RBAC 映射创建请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RbacMappingCreateRequest {

    /** 系统角色 ID */
    @NotBlank(message = "System role ID is required")
    private String sysRoleId;

    /** Superset 角色 ID 列表 */
    @NotEmpty(message = "At least one Superset role ID is required")
    private List<Integer> supersetRoleIds;
}
