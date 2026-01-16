package com.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户业务单元角色分配请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBusinessUnitRoleAssignRequest {
    
    @NotBlank(message = "业务单元ID不能为空")
    private String businessUnitId;
    
    @NotBlank(message = "角色ID不能为空")
    private String roleId;
}
