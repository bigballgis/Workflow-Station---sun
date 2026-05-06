package com.developer.dto;

import com.developer.entity.FormTableBinding;
import com.developer.entity.TableDefinition;
import com.developer.enums.BindingMode;
import com.developer.enums.BindingType;
import com.developer.enums.SubMode;
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
    private BindingType bindingType;
    private BindingMode bindingMode;
    private String foreignKeyField;
    private Integer sortOrder;
    private Instant createdAt;
    private Instant updatedAt;
    private Long subListViewId;
    private SubMode subMode;

    /**
     * 从实体转换为响应DTO
     */
    public static FormTableBindingResponse fromEntity(FormTableBinding binding) {
        return fromEntity(binding, null);
    }

    public static FormTableBindingResponse fromEntity(FormTableBinding binding, String relationTableName) {
        return fromEntity(binding, relationTableName, null);
    }

    /**
     * @param formIdOverride 若为非 null，则用其作为响应中的 formId，避免在未初始化 LAZY {@code form} 时调用 {@link FormTableBinding#getFormId()}。
     */
    public static FormTableBindingResponse fromEntity(
            FormTableBinding binding, String relationTableName, Long formIdOverride) {
        String tableName = binding.getTableName();
        String tableType = binding.getTable() != null ? binding.getTable().getTableType().name() : null;
        // For RELATED bindings, table is null — use provided relation table name
        if (tableName == null && binding.getBindingType() == BindingType.RELATED) {
            tableName = relationTableName;
            tableType = "RELATION";
        }
        Long formId = formIdOverride != null ? formIdOverride : binding.getFormId();
        return FormTableBindingResponse.builder()
                .id(binding.getId())
                .formId(formId)
                .tableId(binding.getTableId())
                .tableName(tableName)
                .tableType(tableType)
                .bindingType(binding.getBindingType())
                .bindingMode(binding.getBindingMode())
                .foreignKeyField(binding.getForeignKeyField())
                .sortOrder(binding.getSortOrder())
                .createdAt(binding.getCreatedAt())
                .updatedAt(binding.getUpdatedAt())
                .subListViewId(binding.getSubListViewId())
                .subMode(binding.getSubMode())
                .build();
    }

    /**
     * 在服务层已知物理表或为 RELATED 时使用：不调用 {@link FormTableBinding#getTableId()} /
     * {@link FormTableBinding#getTableName()} / {@link FormTableBinding#getFormId()}（会触碰 LAZY 关联）。
     */
    public static FormTableBindingResponse fromPersisted(
            FormTableBinding binding,
            long formId,
            TableDefinition physicsTableOrNull,
            String relationTableResolvedName) {
        Long tableId;
        String tableName;
        String tableType;
        if (binding.getBindingType() == BindingType.RELATED) {
            tableId = binding.getRelationTableId();
            tableName = relationTableResolvedName;
            tableType = "RELATION";
        } else if (physicsTableOrNull != null) {
            tableId = physicsTableOrNull.getId();
            tableName = physicsTableOrNull.getTableName();
            tableType = physicsTableOrNull.getTableType().name();
        } else {
            throw new IllegalStateException(
                    "fromPersisted: physics TableDefinition required when binding type is not RELATED");
        }
        return FormTableBindingResponse.builder()
                .id(binding.getId())
                .formId(formId)
                .tableId(tableId)
                .tableName(tableName)
                .tableType(tableType)
                .bindingType(binding.getBindingType())
                .bindingMode(binding.getBindingMode())
                .foreignKeyField(binding.getForeignKeyField())
                .sortOrder(binding.getSortOrder())
                .createdAt(binding.getCreatedAt())
                .updatedAt(binding.getUpdatedAt())
                .subListViewId(binding.getSubListViewId())
                .subMode(binding.getSubMode())
                .build();
    }
}
