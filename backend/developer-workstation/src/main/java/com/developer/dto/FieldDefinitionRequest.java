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
    
    @NotBlank(message = "{validation.field_name_required}")
    @Size(max = 100, message = "{validation.field_name_max_length}")
    private String fieldName;
    
    @NotNull(message = "{validation.data_type_required}")
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
