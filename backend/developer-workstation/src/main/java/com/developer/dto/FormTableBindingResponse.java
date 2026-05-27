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
 * Form-Table Binding Response DTO
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
     * Convert from entity to response DTO.
     */
    public static FormTableBindingResponse fromEntity(FormTableBinding binding) {
        return fromEntity(binding, null);
    }

    public static FormTableBindingResponse fromEntity(FormTableBinding binding, String relationTableName) {
        return fromEntity(binding, relationTableName, null);
    }

    /**
     * @param formIdOverride if non-null, use it as the formId in the response to avoid calling
     *                       {@link FormTableBinding#getFormId()} when the LAZY {@code form} is not initialized.
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
     * Use when the service layer already knows the physical table or is a RELATED binding:
     * avoids calling {@link FormTableBinding#getTableId()} /
     * {@link FormTableBinding#getTableName()} / {@link FormTableBinding#getFormId()}
     * (which would touch LAZY associations).
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
