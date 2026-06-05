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
    private Boolean isPrimaryKey;
    private Boolean isForeignKey;
    private Long refTableId;
    private List<String> refPrimaryKeyFields;
    private Map<String, Object> pkGeneration;
    private String fkDisplayMode;
}
