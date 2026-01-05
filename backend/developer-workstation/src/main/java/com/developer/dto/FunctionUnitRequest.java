package com.developer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 功能单元请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FunctionUnitRequest {
    
    @NotBlank(message = "名称不能为空")
    @Size(max = 100, message = "名称长度不能超过100个字符")
    private String name;
    
    private String description;
    
    private Long iconId;
}
