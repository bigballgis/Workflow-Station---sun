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
    
    @NotBlank(message = "表单名不能为空")
    @Size(max = 100, message = "表单名长度不能超过100个字符")
    private String formName;
    
    @NotNull(message = "表单类型不能为空")
    private FormType formType;
    
    @NotNull(message = "表单配置不能为空")
    private Map<String, Object> configJson;
    
    private String description;
    
    private Long boundTableId;
}
