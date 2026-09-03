package com.admin.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Field metadata attached to table bindings for Portal / Preview FK/PK runtime (PRD S5).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableFieldDefinitionDTO {
    private String fieldName;

    /**
     * Designer column type ({@code dw_field_definitions.data_type} / {@code rt_field_definitions.data_type}):
     * VARCHAR, TEXT, BIGINT, DECIMAL, DATE, TIMESTAMP, BOOLEAN, FILE, …
     *
     * <p>Exposed so the runtime can answer "does this table hold uploaded files?" from the design
     * instead of guessing at a column literally named {@code file} or a table literally named
     * {@code attachment} — those guesses break the moment a Function Unit renames either.
     */
    private String dataType;

    private Boolean isPrimaryKey;
    private Boolean isForeignKey;
    private Long refTableId;
    private List<String> refPrimaryKeyFields;
    private Map<String, Object> pkGeneration;
    private String fkDisplayMode;

    /**
     * Whether the column is formula-driven. Runtime renderers use this rather than a persisted
     * readonly flag, so a column becomes read-only the moment the designer marks it computed.
     */
    private Boolean isComputed;

    /**
     * Computed field definition (version, scope, source, AST, dependsOn, onError), so the runtime
     * can preview the result while the user edits the fields it depends on.
     */
    private Map<String, Object> computedField;
}
