package com.platform.common.computedfield;

import java.util.List;
import java.util.Map;

/**
 * Data a computed field is evaluated against.
 *
 * @param row       same-row field values, straight out of the process-variable map
 * @param subTables canonical lower-cased table name to rows, already de-duplicated by
 *                  {@link SubTableNormalizer}. Passing a raw {@code __subTables__} map here would
 *                  reintroduce the alias double-counting bug.
 */
public record ComputedFieldContext(Map<String, Object> row,
                                   Map<String, List<Map<String, Object>>> subTables) {

    /**
     * Context for a row-scope formula with no sub-table access.
     *
     * @param row same-row field values
     * @return the context
     */
    public static ComputedFieldContext ofRow(Map<String, Object> row) {
        return new ComputedFieldContext(row, Map.of());
    }

    /**
     * Rows of a sub-table, looked up case-insensitively.
     *
     * @param table table name as written in the formula
     * @return the rows, or null when the record carries no such sub-table
     */
    public List<Map<String, Object>> rowsOf(String table) {
        if (subTables == null || table == null) {
            return null;
        }
        return subTables.get(table.toLowerCase());
    }

    /**
     * Value of a same-row field.
     *
     * @param field field name
     * @return the raw value, null when absent
     */
    public Object fieldValue(String field) {
        return row == null ? null : row.get(field);
    }
}
