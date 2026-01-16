package com.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 业务单元更新请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessUnitUpdateRequest {
    
    @NotBlank(message = "业务单元名称不能为空")
    @Size(max = 100, message = "业务单元名称长度不能超过100")
    private String name;
    
    @Size(max = 50, message = "电话长度不能超过50")
    private String phone;
    
    private String description;
    
    @Size(max = 50, message = "成本中心长度不能超过50")
    private String costCenter;
    
    @Size(max = 200, message = "位置长度不能超过200")
    private String location;
    
    private Integer sortOrder;
}
