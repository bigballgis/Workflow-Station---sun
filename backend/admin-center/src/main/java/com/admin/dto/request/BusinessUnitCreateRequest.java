package com.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 业务单元创建请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessUnitCreateRequest {
    
    @NotBlank(message = "业务单元名称不能为空")
    @Size(max = 100, message = "业务单元名称长度不能超过100")
    private String name;
    
    @NotBlank(message = "业务单元编码不能为空")
    @Size(max = 50, message = "业务单元编码长度不能超过50")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "业务单元编码只能包含字母、数字、下划线和连字符")
    private String code;
    
    private String parentId;
    
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
