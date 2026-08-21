package com.admin.dto.response;

import com.admin.entity.FunctionUnit;
import com.admin.entity.RelationFieldDefinition;
import com.admin.entity.RelationTableDefinition;
import com.platform.common.enums.RelationDataType;
import com.platform.common.enums.RelationTableStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Relation Table 响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelationTableResponse {

    private Long id;
    private String tableName;
    private String displayName;
    private String description;
    private RelationTableStatus status;
    private Boolean enabled;
    private Boolean portalVisible;
    private Integer currentVersion;

    /**
     * Optional Function Unit grouping. functionUnitId references sys_function_units.id;
     * code/name are resolved by the service layer for display and are null when ungrouped.
     */
    private String functionUnitId;
    private String functionUnitCode;
    private String functionUnitName;

    private List<FieldDefinitionResponse> fieldDefinitions;
    private Instant createdAt;
    private String createdBy;
    private Instant updatedAt;
    private String updatedBy;

    /**
     * Permission level the current admin holds on this table for the Table Data page:
     * READONLY | READ_WRITE | null. Resolved against the admin's active role.
     * @see com.platform.common.enums.RelationPermissionLevel
     */
    private String permissionLevel;

    /**
     * 字段定义响应
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldDefinitionResponse {

        private Long id;
        private String fieldName;
        private RelationDataType dataType;
        private Integer length;
        private Integer precision;
        private Integer scale;
        private Boolean nullable;
        private Boolean isPrimaryKey;
        private String defaultValue;
        private String displayName;
        private Integer sortOrder;
        private Boolean isForeignKey;
        private Long refTableId;
        private List<String> refPrimaryKeyFields;
        private Map<String, Object> pkGeneration;
        private String fkDisplayMode;
        private Map<String, Object> lookupConfig;
        private Boolean isComputed;
        private Map<String, Object> computedField;

        /**
         * 从字段定义实体转换
         */
        public static FieldDefinitionResponse fromEntity(RelationFieldDefinition entity) {
            if (entity == null) {
                return null;
            }
            return FieldDefinitionResponse.builder()
                    .id(entity.getId())
                    .fieldName(entity.getFieldName())
                    .dataType(entity.getDataType())
                    .length(entity.getLength())
                    .precision(entity.getPrecision())
                    .scale(entity.getScale())
                    .nullable(entity.getNullable())
                    .isPrimaryKey(entity.getIsPrimaryKey())
                    .defaultValue(entity.getDefaultValue())
                    .displayName(entity.getDisplayName())
                    .sortOrder(entity.getSortOrder())
                    .isForeignKey(entity.getIsForeignKey())
                    .refTableId(entity.getRefTableId())
                    .refPrimaryKeyFields(entity.getRefPrimaryKeyFields())
                    .pkGeneration(entity.getPkGenerationJson())
                    .fkDisplayMode(entity.getFkDisplayMode())
                    .lookupConfig(entity.getLookupConfig())
                    .isComputed(entity.getIsComputed())
                    .computedField(entity.getComputedFieldJson())
                    .build();
        }
    }

    /**
     * 从表定义实体转换
     */
    public static RelationTableResponse fromEntity(RelationTableDefinition entity) {
        if (entity == null) {
            return null;
        }
        List<FieldDefinitionResponse> fields = entity.getFieldDefinitions() != null
                ? entity.getFieldDefinitions().stream()
                    .map(FieldDefinitionResponse::fromEntity)
                    .toList()
                : Collections.emptyList();

        return RelationTableResponse.builder()
                .id(entity.getId())
                .tableName(entity.getTableName())
                .displayName(entity.getDisplayName())
                .description(entity.getDescription())
                .status(entity.getStatus())
                .enabled(entity.getEnabled())
                .portalVisible(entity.getPortalVisible())
                .currentVersion(entity.getCurrentVersion())
                .functionUnitId(entity.getFunctionUnitId())
                .fieldDefinitions(fields)
                .createdAt(entity.getCreatedAt())
                .createdBy(entity.getCreatedBy())
                .updatedAt(entity.getUpdatedAt())
                .updatedBy(entity.getUpdatedBy())
                .build();
    }

    /**
     * Fills functionUnitCode/functionUnitName for display; pass null to leave both unset (ungrouped).
     */
    public void applyFunctionUnit(FunctionUnit functionUnit) {
        if (functionUnit == null) {
            return;
        }
        this.functionUnitCode = functionUnit.getCode();
        this.functionUnitName = functionUnit.getName();
    }
}
