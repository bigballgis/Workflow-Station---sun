package com.platform.common.list;

/**
 * One column filter as the shared list header's filter dialog produces it. Which operators
 * a column accepts is declared by {@link ListColumnMeta}; this record only carries the
 * request, and a filter that names an undeclared field or an operator outside that column's
 * whitelist is rejected when it is compiled, never silently dropped.
 *
 * @param value2 upper bound of a {@code between} filter, unused by every other operator
 */
public record ListColumnFilter(String field, String operator, String value, String value2) {

    public ListColumnFilter {
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("filter field is required");
        }
        if (operator == null || operator.isBlank()) {
            throw new IllegalArgumentException("filter operator is required for field " + field);
        }
    }
}
