package com.portal.util;

import com.portal.dto.ListColumnFilter;
import com.portal.dto.PortalListColumnMeta;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Compiles shared-list column filters and sort into SQL. Every field and operator is validated
 * against the list's {@link PortalListColumnMeta} declaration before anything reaches SQL — an
 * undeclared field, a non-filterable column or an operator outside the whitelist is a 400,
 * never a silent no-op.
 *
 * <p>Each list binds one instance describing where its values live and how its rows are ordered,
 * so the operator semantics below are written once and every list that adopts the shared header
 * behaves identically:
 * <ul>
 *   <li>TEXT — string comparison; contains/startsWith/endsWith use ILIKE with escaped wildcards;
 *       ne/notContains also match rows where the field is null.</li>
 *   <li>NUMBER — comparisons only match rows whose stored value parses as a number (regex-guarded
 *       cast), so a non-numeric stored value simply fails the predicate instead of erroring.</li>
 *   <li>DATETIME — the filter dialog sends {@code YYYY-MM-DD}; comparison is on the first 10
 *       chars of the stored ISO string, so date-only bounds are inclusive.</li>
 *   <li>BOOLEAN — case-insensitive equality against true/false.</li>
 * </ul>
 */
public final class ListFilterSql {

    /** Matches values that can safely be cast to numeric inside SQL. */
    private static final String SQL_NUMERIC_GUARD = "'^-?[0-9]+(\\.[0-9]+)?$'";
    private static final Pattern DATE_VALUE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final Pattern IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    /** Resolves a declared column name to the SQL expression holding its value. */
    @FunctionalInterface
    public interface ColumnRef {
        String sqlFor(String field);
    }

    /** Value lives in the JSON row document of {@code rt_table_data_rows}. */
    public static final ColumnRef JSON_ROW = field -> "data->>'" + requireIdentifier(field) + "'";

    /** Value is a real text column of the queried table. */
    public static final ColumnRef PHYSICAL_COLUMN = ListFilterSql::requireIdentifier;

    private final Map<String, PortalListColumnMeta> columnsByField;
    private final ColumnRef columnRef;
    private final String tiebreak;
    private final String defaultOrderBy;

    /**
     * @param tiebreak       column appended to every ORDER BY so equal values page deterministically
     * @param defaultOrderBy ORDER BY body used when the caller requests no sort, without the
     *                       {@code ORDER BY} keyword and without the tiebreak; null orders by
     *                       the tiebreak alone
     */
    public ListFilterSql(Map<String, PortalListColumnMeta> columnsByField, ColumnRef columnRef,
                         String tiebreak, String defaultOrderBy) {
        this.columnsByField = Map.copyOf(columnsByField);
        this.columnRef = columnRef;
        this.tiebreak = requireIdentifier(tiebreak);
        this.defaultOrderBy = defaultOrderBy;
    }

    /** Rows keep their insertion order when nothing else is asked for. */
    public static ListFilterSql orderedById(Map<String, PortalListColumnMeta> columnsByField,
                                            ColumnRef columnRef) {
        return new ListFilterSql(columnsByField, columnRef, "id", null);
    }

    /**
     * @return SQL fragment starting with {@code  AND (...)} for each filter, or "" when empty;
     *         bind values are appended to {@code outParams} in order
     */
    public String whereClause(List<ListColumnFilter> filters, List<Object> outParams) {
        StringBuilder sql = new StringBuilder();
        for (ListColumnFilter filter : filters) {
            PortalListColumnMeta column = requireFilterableColumn(filter);
            sql.append(" AND ").append(predicate(column, filter, outParams));
        }
        return sql.toString();
    }

    /** @return full {@code  ORDER BY ...} clause, always ending in the tiebreak column */
    public String orderBy(String sortField, String sortDirection) {
        if (sortField == null) {
            return defaultOrderBy == null
                    ? " ORDER BY " + tiebreak
                    : " ORDER BY " + defaultOrderBy + ", " + tiebreak;
        }
        PortalListColumnMeta column = columnsByField.get(sortField);
        if (column == null) {
            throw new IllegalArgumentException("Unknown sort column: " + sortField);
        }
        if (!column.sortable()) {
            throw new IllegalArgumentException("Column is not sortable: " + sortField);
        }
        String direction = "DESC".equalsIgnoreCase(sortDirection) ? "DESC" : "ASC";
        String ref = columnRef.sqlFor(column.field());
        String expr = column.kind() == PortalListColumnMeta.Kind.NUMBER
                ? "(CASE WHEN " + ref + " ~ " + SQL_NUMERIC_GUARD + " THEN (" + ref + ")::numeric END)"
                : ref;
        return " ORDER BY " + expr + " " + direction + " NULLS LAST, " + tiebreak;
    }

    /**
     * Whole-row keyword search across the columns the caller considers searchable, as one
     * OR-ed ILIKE group.
     *
     * @return SQL fragment starting with {@code  AND (...)}, or "" when there is nothing to search
     */
    public String searchClause(String keyword, List<String> fields, List<Object> outParams) {
        if (keyword == null || keyword.isBlank() || fields.isEmpty()) {
            return "";
        }
        StringBuilder sql = new StringBuilder(" AND (");
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                sql.append(" OR ");
            }
            sql.append(columnRef.sqlFor(fields.get(i))).append(" ILIKE ?");
            outParams.add("%" + escapeLike(keyword.trim()) + "%");
        }
        return sql.append(")").toString();
    }

    private PortalListColumnMeta requireFilterableColumn(ListColumnFilter filter) {
        PortalListColumnMeta column = columnsByField.get(filter.field());
        if (column == null) {
            throw new IllegalArgumentException("Unknown filter column: " + filter.field());
        }
        if (!column.filterable()) {
            throw new IllegalArgumentException("Column is not filterable: " + filter.field());
        }
        if (!column.allowsOperator(filter.operator())) {
            throw new IllegalArgumentException(
                    "Operator " + filter.operator() + " is not allowed on column " + filter.field());
        }
        return column;
    }

    private String predicate(PortalListColumnMeta column, ListColumnFilter filter,
                             List<Object> outParams) {
        String op = filter.operator();
        String ref = columnRef.sqlFor(column.field());
        if ("isNull".equals(op)) {
            return "(" + ref + " IS NULL OR " + ref + " = '')";
        }
        if ("isNotNull".equals(op)) {
            return "(" + ref + " IS NOT NULL AND " + ref + " <> '')";
        }
        String value = requireValue(filter, filter.value());
        return switch (column.kind()) {
            case TEXT, ENUM, USER -> textPredicate(ref, filter, value, outParams);
            case NUMBER -> numberPredicate(ref, filter, value, outParams);
            case DATETIME -> datePredicate(ref, filter, value, outParams);
            case BOOLEAN -> booleanPredicate(ref, filter, value, outParams);
        };
    }

    private static String textPredicate(String ref, ListColumnFilter filter, String value,
                                        List<Object> outParams) {
        switch (filter.operator()) {
            case "contains" -> {
                outParams.add("%" + escapeLike(value) + "%");
                return ref + " ILIKE ?";
            }
            case "notContains" -> {
                outParams.add("%" + escapeLike(value) + "%");
                return "(" + ref + " IS NULL OR " + ref + " NOT ILIKE ?)";
            }
            case "startsWith" -> {
                outParams.add(escapeLike(value) + "%");
                return ref + " ILIKE ?";
            }
            case "endsWith" -> {
                outParams.add("%" + escapeLike(value));
                return ref + " ILIKE ?";
            }
            case "eq" -> {
                outParams.add(value);
                return ref + " = ?";
            }
            case "ne" -> {
                outParams.add(value);
                return "(" + ref + " IS NULL OR " + ref + " <> ?)";
            }
            default -> throw unsupported(filter);
        }
    }

    private static String numberPredicate(String ref, ListColumnFilter filter, String value,
                                          List<Object> outParams) {
        String guarded = ref + " ~ " + SQL_NUMERIC_GUARD + " AND (" + ref + ")::numeric";
        outParams.add(parseNumber(filter.field(), value));
        switch (filter.operator()) {
            case "eq" -> {
                return "(" + guarded + " = ?)";
            }
            case "ne" -> {
                return "(" + guarded + " <> ?)";
            }
            case "gt" -> {
                return "(" + guarded + " > ?)";
            }
            case "gte" -> {
                return "(" + guarded + " >= ?)";
            }
            case "lt" -> {
                return "(" + guarded + " < ?)";
            }
            case "lte" -> {
                return "(" + guarded + " <= ?)";
            }
            case "between" -> {
                outParams.add(parseNumber(filter.field(), requireValue(filter, filter.value2())));
                return "(" + guarded + " >= ? AND (" + ref + ")::numeric <= ?)";
            }
            default -> throw unsupported(filter);
        }
    }

    private static String datePredicate(String ref, ListColumnFilter filter, String value,
                                        List<Object> outParams) {
        String day = "left(" + ref + ", 10)";
        outParams.add(requireDate(filter.field(), value));
        switch (filter.operator()) {
            case "on" -> {
                return day + " = ?";
            }
            case "before" -> {
                return day + " < ?";
            }
            case "after" -> {
                return day + " > ?";
            }
            case "between" -> {
                outParams.add(requireDate(filter.field(), requireValue(filter, filter.value2())));
                return "(" + day + " >= ? AND " + day + " <= ?)";
            }
            default -> throw unsupported(filter);
        }
    }

    private static String booleanPredicate(String ref, ListColumnFilter filter, String value,
                                           List<Object> outParams) {
        if (!"eq".equals(filter.operator())) {
            throw unsupported(filter);
        }
        String normalized = value.trim().toLowerCase();
        if (!"true".equals(normalized) && !"false".equals(normalized)) {
            throw new IllegalArgumentException(
                    "Boolean filter on " + filter.field() + " requires true/false, got: " + value);
        }
        outParams.add(normalized);
        return "lower(" + ref + ") = ?";
    }

    /** Escapes LIKE wildcards so user input matches literally (PG default escape is backslash). */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String requireIdentifier(String field) {
        if (field == null || !IDENTIFIER.matcher(field).matches()) {
            throw new IllegalArgumentException("Invalid field identifier: " + field);
        }
        return field;
    }

    private static String requireValue(ListColumnFilter filter, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Filter " + filter.operator() + " on " + filter.field() + " requires a value");
        }
        return value;
    }

    private static BigDecimal parseNumber(String field, String value) {
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Numeric filter on " + field + " requires a number, got: " + value);
        }
    }

    private static String requireDate(String field, String value) {
        if (!DATE_VALUE.matcher(value.trim()).matches()) {
            throw new IllegalArgumentException(
                    "Date filter on " + field + " requires YYYY-MM-DD, got: " + value);
        }
        return value.trim();
    }

    private static IllegalArgumentException unsupported(ListColumnFilter filter) {
        return new IllegalArgumentException(
                "Operator " + filter.operator() + " is not supported on column " + filter.field());
    }
}
