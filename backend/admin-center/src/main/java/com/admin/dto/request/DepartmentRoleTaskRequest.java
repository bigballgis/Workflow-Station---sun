package com.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 部门角色任务分配请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentRoleTaskRequest {
    
    @NotBlank(message = "部门ID不能为空")
    private String departmentId;
    
    @NotBlank(message = "角色ID不能为空")
    private String roleId;
    
    private String taskId;
    
    private String comment;
}
