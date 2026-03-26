package com.platform.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * Generic row DTO for dynamic Relation Table data.
 * Uses a Map to hold column values since table structures are user-defined.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelationTableDataRowDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Row identifier (primary key value)
     */
    private String rowId;

    /**
     * Table definition ID this row belongs to
     */
    private Long tableId;

    /**
     * Column name to value mapping for this row
     */
    private Map<String, Object> data;
}
