package com.platform.common.dto;

import com.platform.common.enums.RelationDataType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Shared DTO for Relation Table field definition data.
 * Describes a single column in a Relation Table.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelationFieldDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Field definition ID
     */
    private Long id;

    /**
     * Column name in the physical table
     */
    private String fieldName;

    /**
     * Data type of the field
     */
    private RelationDataType dataType;

    /**
     * Field length (applicable for VARCHAR, etc.)
     */
    private Integer length;

    /**
     * Precision for DECIMAL type
     */
    private Integer precision;

    /**
     * Scale for DECIMAL type
     */
    private Integer scale;

    /**
     * Whether the field allows null values
     */
    @Builder.Default
    private Boolean nullable = true;

    /**
     * Whether the field is a primary key
     */
    @Builder.Default
    private Boolean isPrimaryKey = false;

    /**
     * Default value expression
     */
    private String defaultValue;

    /**
     * Display name shown to end users in the form designer / runtime UI.
     */
    private String displayName;

    /**
     * Display order of the field
     */
    private Integer sortOrder;

    /**
     * Primary-key generation config (e.g. {@code {"strategy":"uuid|sequence|manual", ...}}).
     * Only meaningful when {@link #isPrimaryKey} is true; null otherwise.
     */
    private java.util.Map<String, Object> pkGeneration;

    /**
     * LOOKUP field configuration. Only meaningful when {@link #dataType} is
     * {@link RelationDataType#LOOKUP}. Carries refTableId, searchFields,
     * displayFields, selectedDisplayField, filterConditions, showBackfillView,
     * multiple, and the derivedFrom block (parentField + join columns) that
     * drives derived auto-fill / cascade filtering between two lookup columns.
     */
    private java.util.Map<String, Object> lookupConfig;

    /**
     * Whether this column is a foreign-key reference to another Relation Table.
     * Carried through the deploy snapshot so FK config survives deployment.
     */
    private Boolean isForeignKey;

    /**
     * Target Relation Table id when {@link #isForeignKey} is true.
     */
    private Long refTableId;

    /**
     * Referenced table's primary-key field names when {@link #isForeignKey} is true.
     */
    private java.util.List<String> refPrimaryKeyFields;

    /**
     * FK display mode: {@code readonly | hidden | editable}. Null when not an FK.
     */
    private String fkDisplayMode;

    /**
     * Whether this column is derived from a formula rather than user input.
     * Computed columns are read-only in forms; the server recomputes them on every write.
     */
    private Boolean isComputed;

    /**
     * Computed field definition: version, scope, source text, validated AST, dependsOn, onError.
     * Only meaningful when {@link #isComputed} is true. Relation tables have no sub-tables,
     * so the scope is always {@code row}.
     */
    private java.util.Map<String, Object> computedField;
}
