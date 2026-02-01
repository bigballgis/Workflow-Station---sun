package com.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 业务单元角色任务分配请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentRoleTaskRequest {
    
    @NotBlank(message = "业务单元ID不能为空")
    private String businessUnitId;
    
    @NotBlank(message = "角色ID不能为空")
    private String roleId;
    
    private String taskId;
    
    private String comment;
}
