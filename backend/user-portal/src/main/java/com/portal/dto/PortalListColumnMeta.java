package com.portal.dto;

import java.util.List;

/**
 * Single-point declaration of a list column's capabilities, returned to the frontend so
 * header menus and filter dialogs render strictly from it. Operator names are a shared
 * contract with {@code frontend/shared/src/list/columnMeta.ts}; the kind→operator matrix
 * lives ONLY here — the frontend never derives operators on its own.
 *
 * <p>Grouping is a per-field semantic capability: only closed-value kinds
 * (ENUM / USER / BOOLEAN) may group; TEXT / NUMBER / DATETIME never do. The canonical
 * constructor enforces this so no endpoint can declare a groupable free-text column.
 */
public record PortalListColumnMeta(
        String field,
        String label,
        Kind kind,
        boolean filterable,
        boolean sortable,
        boolean groupable,
        List<String> operators,
        List<Option> options) {

    public enum Kind { TEXT, ENUM, USER, DATETIME, NUMBER, BOOLEAN }

    public record Option(String value, String label) {
        public Option {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("column option value is required");
            }
            if (label == null || label.isBlank()) {
                throw new IllegalArgumentException("column option label is required for value " + value);
            }
        }
    }

    private static final List<String> TEXT_OPERATORS = List.of(
            "contains", "notContains", "eq", "ne", "startsWith", "endsWith", "isNull", "isNotNull");
    private static final List<String> CLOSED_VALUE_OPERATORS = List.of(
            "eq", "ne", "isNull", "isNotNull");
    private static final List<String> DATETIME_OPERATORS = List.of(
            "on", "before", "after", "between", "isNull", "isNotNull");
    private static final List<String> NUMBER_OPERATORS = List.of(
            "eq", "ne", "gt", "gte", "lt", "lte", "between", "isNull", "isNotNull");
    private static final List<String> BOOLEAN_OPERATORS = List.of("eq");

    public PortalListColumnMeta {
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("column field is required");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("column label is required for field " + field);
        }
        if (kind == null) {
            throw new IllegalArgumentException("column kind is required for field " + field);
        }
        operators = operators == null ? List.of() : List.copyOf(operators);
        options = options == null ? List.of() : List.copyOf(options);
        if (filterable && operators.isEmpty()) {
            throw new IllegalArgumentException(
                    "filterable column " + field + " declared without an operator whitelist");
        }
        if (groupable && !defaultGroupable(kind)) {
            throw new IllegalArgumentException(
                    "column " + field + " of kind " + kind
                            + " cannot be groupable — only closed-value kinds (ENUM/USER/BOOLEAN) group");
        }
    }

    /** The one place the kind→operator matrix is defined. */
    public static List<String> operatorsFor(Kind kind) {
        return switch (kind) {
            case TEXT -> TEXT_OPERATORS;
            case ENUM, USER -> CLOSED_VALUE_OPERATORS;
            case DATETIME -> DATETIME_OPERATORS;
            case NUMBER -> NUMBER_OPERATORS;
            case BOOLEAN -> BOOLEAN_OPERATORS;
        };
    }

    /** Grouping default by field semantics — closed value sets group, open ones don't. */
    public static boolean defaultGroupable(Kind kind) {
        return kind == Kind.ENUM || kind == Kind.USER || kind == Kind.BOOLEAN;
    }

    /** Standard column: filterable + sortable, kind-derived operators and groupable default. */
    public static PortalListColumnMeta of(String field, String label, Kind kind) {
        return new PortalListColumnMeta(
                field, label, kind, true, true, defaultGroupable(kind), operatorsFor(kind), List.of());
    }

    /** Closed-value column with its option list (ENUM status values, boolean labels, ...). */
    public static PortalListColumnMeta withOptions(
            String field, String label, Kind kind, List<Option> options) {
        if (!defaultGroupable(kind)) {
            throw new IllegalArgumentException(
                    "column " + field + " of kind " + kind + " does not take a closed option list");
        }
        if (options == null || options.isEmpty()) {
            throw new IllegalArgumentException(
                    "closed-value column " + field + " declared without options");
        }
        return new PortalListColumnMeta(
                field, label, kind, true, true, true, operatorsFor(kind), options);
    }

    /** Display-only column: no filter, no sort, no group (e.g. computed/action columns). */
    public static PortalListColumnMeta displayOnly(String field, String label, Kind kind) {
        return new PortalListColumnMeta(field, label, kind, false, false, false, List.of(), List.of());
    }

    public boolean allowsOperator(String operator) {
        return operators.contains(operator);
    }
}
