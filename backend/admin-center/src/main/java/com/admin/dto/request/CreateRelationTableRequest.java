package com.admin.dto.request;

import com.platform.common.enums.RelationDataType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRelationTableRequest {

    @NotBlank(message = "{validation.tableName.notBlank}")
    @Size(max = 100, message = "{validation.tableName.size}")
    @Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "{validation.tableName.pattern}")
    private String tableName;

    @Size(max = 200, message = "{validation.displayName.size}")
    private String displayName;

    private String description;

    @NotEmpty(message = "{validation.fieldDefinitions.notEmpty}")
    @Valid
    private List<FieldDefinitionRequest> fieldDefinitions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldDefinitionRequest {

        @NotBlank(message = "{validation.fieldName.notBlank}")
        @Size(max = 100, message = "{validation.fieldName.size}")
        @Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "{validation.fieldName.pattern}")
        private String fieldName;

        @NotNull(message = "{validation.dataType.notNull}")
        private RelationDataType dataType;

        private Integer length;
        private Integer precision;
        private Integer scale;

        @Builder.Default
        private Boolean nullable = true;

        @Builder.Default
        private Boolean isPrimaryKey = false;

        @Size(max = 500, message = "{validation.defaultValue.size}")
        private String defaultValue;

        private String displayName;

        private Integer sortOrder;

        private Map<String, Object> pkGeneration;

        private Boolean isForeignKey;

        private Long refTableId;

        private List<String> refPrimaryKeyFields;

        /** readonly | hidden */
        private String fkDisplayMode;

        /** LOOKUP field configuration; only meaningful when dataType == LOOKUP. */
        private Map<String, Object> lookupConfig;

        /** Whether this column is derived from a formula instead of user input. */
        private Boolean isComputed;

        /**
         * Computed field definition: version, scope, source text, compiled AST, dependsOn, onError.
         * Validated by {@code RelationComputedFieldValidator} before persisting.
         */
        private Map<String, Object> computedField;
    }
}
