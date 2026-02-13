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
    
    @NotBlank(message = "{validation.bu_name_required}")
    @Size(max = 100, message = "{validation.bu_name_max_length}")
    private String name;
    
    @NotBlank(message = "{validation.bu_code_required}")
    @Size(max = 50, message = "{validation.bu_code_max_length}")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "{validation.bu_code_pattern}")
    private String code;
    
    private String parentId;
    
    @Size(max = 50, message = "{validation.phone_max_length}")
    private String phone;
    
    private String description;
    
    @Size(max = 50, message = "{validation.cost_center_max_length}")
    private String costCenter;
    
    @Size(max = 200, message = "{validation.location_max_length}")
    private String location;
    
    private Integer sortOrder;
}
