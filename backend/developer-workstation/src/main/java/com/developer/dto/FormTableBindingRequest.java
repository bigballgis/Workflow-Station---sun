package com.developer.dto;

import com.developer.enums.BindingMode;
import com.developer.enums.BindingType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 表单表绑定请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormTableBindingRequest {
    
    /**
     * 要绑定的表ID
     */
    @NotNull(message = "{validation.table_id_required}")
    private Long tableId;
    
    /**
     * 绑定类型
     */
    @NotNull(message = "{validation.binding_type_required}")
    private BindingType bindingType;
    
    /**
     * 绑定模式
     */
    private BindingMode bindingMode;
    
    /**
     * 外键字段名（子表/关联表需要）
     */
    private String foreignKeyField;
    
    /**
     * 排序顺序
     */
    private Integer sortOrder;
}
