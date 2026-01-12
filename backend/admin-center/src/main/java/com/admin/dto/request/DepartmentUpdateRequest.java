package com.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 部门更新请求DTO
 * 与创建请求不同，更新时不需要提供部门编码（编码创建后不可修改）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentUpdateRequest {
    
    @NotBlank(message = "部门名称不能为空")
    @Size(max = 100, message = "部门名称长度不能超过100")
    private String name;
    
    private String managerId;
    
    private String secondaryManagerId;
    
    @Size(max = 50, message = "电话长度不能超过50")
    private String phone;
    
    private String description;
    
    @Size(max = 50, message = "成本中心长度不能超过50")
    private String costCenter;
    
    @Size(max = 200, message = "位置长度不能超过200")
    private String location;
    
    private Integer sortOrder;
}
