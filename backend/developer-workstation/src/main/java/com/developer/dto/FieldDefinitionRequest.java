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

    /**
     * 仅在更新已有字段时由前端回传，作为重命名/显示名变更的匹配键。
     * 由于 {@link com.developer.component.impl.TableDesignComponentImpl#update}
     * 采用「删除-重建」策略，原始 id 在保存后失效；此字段仅用于在事务内
     * 同步表单设计器（form rule.field / rule.title / fieldPermissions key）。
     */
    private Long id;

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
