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
    
    @NotBlank(message = "{validation.name_required}")
    @Size(max = 100, message = "{validation.name_max_length}")
    private String name;
    
    private String description;
    
    private Long iconId;
}
