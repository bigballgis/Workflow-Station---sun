package com.platform.common.computedfield;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Data a computed field is evaluated against.
 *
 * @param row       same-row field values, straight out of the process-variable map
 * @param subTables canonical lower-cased table name to rows, already de-duplicated by
 *                  {@link SubTableNormalizer}. Passing a raw {@code __subTables__} map here would
 *                  reintroduce the alias double-counting bug.
 * @param parents   parent rows a SUB-table formula may read via {@code table.column}, keyed by
 *                  lower-cased physical table name. MVP supplies only the Function Unit MAIN row.
 */
public record ComputedFieldContext(Map<String, Object> row,
                                   Map<String, List<Map<String, Object>>> subTables,
                                   Map<String, Map<String, Object>> parents) {

    /**
     * Context for a row-scope formula with no sub-table or parent access.
     *
     * @param row same-row field values
     * @return the context
     */
    public ComputedFieldContext(Map<String, Object> row,
                                Map<String, List<Map<String, Object>>> subTables) {
        this(row, subTables, Map.of());
    }

    /**
     * Context for a row-scope formula with no sub-table access.
     *
     * @param row same-row field values
     * @return the context
     */
    public static ComputedFieldContext ofRow(Map<String, Object> row) {
        return new ComputedFieldContext(row, Map.of(), Map.of());
    }

    /**
     * Context for a SUB-table row formula that may read the MAIN row.
     *
     * @param row     same-row field values
     * @param parents parent rows keyed by lower-cased table name
     * @return the context
     */
    public static ComputedFieldContext ofRow(Map<String, Object> row,
                                             Map<String, Map<String, Object>> parents) {
        return new ComputedFieldContext(row, Map.of(),
                parents == null ? Map.of() : parents);
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
        return subTables.get(table.toLowerCase(Locale.ROOT));
    }

    /**
     * The parent row named by a qualified field reference, looked up case-insensitively.
     *
     * @param table table name as written in the formula
     * @return the parent row, or null when this context has no such parent
     */
    public Map<String, Object> parentRow(String table) {
        if (parents == null || table == null) {
            return null;
        }
        return parents.get(table.toLowerCase(Locale.ROOT));
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
