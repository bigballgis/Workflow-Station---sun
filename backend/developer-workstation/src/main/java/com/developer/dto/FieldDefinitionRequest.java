package com.developer.dto;

import com.developer.enums.DataType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 字段定义请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldDefinitionRequest {
    
    @NotBlank(message = "字段名不能为空")
    @Size(max = 100, message = "字段名长度不能超过100个字符")
    private String fieldName;
    
    @NotNull(message = "数据类型不能为空")
    private DataType dataType;
    
    private Integer length;
    private Integer precision;
    private Integer scale;
    
    @Builder.Default
    private Boolean nullable = true;
    
    private String defaultValue;
    
    @Builder.Default
    private Boolean isPrimaryKey = false;
    
    @Builder.Default
    private Boolean isUnique = false;
    
    private String description;
    private Integer sortOrder;
}
