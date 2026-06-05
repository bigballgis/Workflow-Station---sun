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
}
