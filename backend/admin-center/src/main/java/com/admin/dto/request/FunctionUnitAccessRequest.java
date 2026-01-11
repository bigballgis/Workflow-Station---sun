package com.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 功能单元访问权限配置请求
 * 简化后只支持角色分配
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FunctionUnitAccessRequest {
    
    @NotBlank(message = "角色ID不能为空")
    private String roleId;
    
    /** 角色名称（可选，如果不提供则自动查询） */
    private String roleName;
}
