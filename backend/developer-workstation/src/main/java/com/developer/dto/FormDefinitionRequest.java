package com.developer.dto;

import com.developer.enums.FormType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 表单定义请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormDefinitionRequest {
    
    @NotBlank(message = "{validation.form_name_required}")
    @Size(max = 100, message = "{validation.form_name_max_length}")
    private String formName;
    
    @NotNull(message = "{validation.form_type_required}")
    private FormType formType;
    
    @NotNull(message = "{validation.form_config_required}")
    private Map<String, Object> configJson;
    
    private String description;
    
    private Long boundTableId;
}
