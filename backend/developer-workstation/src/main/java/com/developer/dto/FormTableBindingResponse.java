package com.developer.dto;

import com.developer.entity.FormTableBinding;
import com.developer.enums.BindingMode;
import com.developer.enums.BindingType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 表单表绑定响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormTableBindingResponse {
    
    private Long id;
    private Long formId;
    private Long tableId;
    private String tableName;
    private String tableType;
    /** 公共表ID（当绑定到公共表时） */
    private Long commonTableId;
    /** 公共表编码（当绑定到公共表时） */
    private String commonTableCode;
    /** 是否为公共表绑定 */
    private boolean commonTableBinding;
    private BindingType bindingType;
    private BindingMode bindingMode;
    private String foreignKeyField;
    private Integer sortOrder;
    private Instant createdAt;
    private Instant updatedAt;
    
    /**
     * 从实体转换为响应DTO
     */
    public static FormTableBindingResponse fromEntity(FormTableBinding binding) {
        return FormTableBindingResponse.builder()
                .id(binding.getId())
                .formId(binding.getFormId())
                .tableId(binding.getTableId())
                .tableName(binding.getTableName())
                .tableType(binding.getTable() != null ? binding.getTable().getTableType().name() : null)
                .commonTableId(binding.getCommonTableId())
                .commonTableCode(binding.getCommonTableCode())
                .commonTableBinding(binding.isCommonTableBinding())
                .bindingType(binding.getBindingType())
                .bindingMode(binding.getBindingMode())
                .foreignKeyField(binding.getForeignKeyField())
                .sortOrder(binding.getSortOrder())
                .createdAt(binding.getCreatedAt())
                .updatedAt(binding.getUpdatedAt())
                .build();
    }
}
