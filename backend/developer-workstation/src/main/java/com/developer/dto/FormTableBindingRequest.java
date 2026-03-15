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
 * tableId 和 commonTableId 二选一
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormTableBindingRequest {
    
    /**
     * 要绑定的功能单元表ID（与 commonTableId 二选一）
     */
    private Long tableId;

    /**
     * 要绑定的公共表ID（与 tableId 二选一）
     */
    private Long commonTableId;
    
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
