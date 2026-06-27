package com.platform.common.relationtable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * A single cell-level validation error produced while validating an imported Relation Table row.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RowValidationError implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 1-based row number as seen by the user in the imported file (excludes the header row). */
    private int row;

    /** Field (column) name the error refers to; may be null for whole-row errors. */
    private String field;

    /** Human-readable error message. */
    private String message;
}
