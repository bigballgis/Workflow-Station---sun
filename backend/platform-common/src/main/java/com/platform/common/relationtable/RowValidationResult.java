package com.platform.common.relationtable;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Result of validating a single imported row: the coerced/normalized values ready for insert,
 * plus any cell-level errors. A row with errors must NOT be inserted.
 */
@Data
public class RowValidationResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 1-based row number as seen by the user (excludes header). */
    private final int row;

    /** Coerced values keyed by field name (only present when the row has no errors). */
    private final Map<String, Object> values = new LinkedHashMap<>();

    /** Cell-level errors for this row. */
    private final List<RowValidationError> errors = new ArrayList<>();

    public RowValidationResult(int row) {
        this.row = row;
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public void addError(String field, String message) {
        errors.add(new RowValidationError(row, field, message));
    }

    public void putValue(String field, Object value) {
        values.put(field, value);
    }
}
