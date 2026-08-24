package com.portal.util;

import com.platform.common.list.ListColumnFilter;
import com.platform.common.list.ListColumnMeta;
import com.platform.common.jdbc.SqlIdentifiers;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Compiles shared-list column filters and sort into SQL. Every field and operator is validated
 * against the list's {@link ListColumnMeta} declaration before anything reaches SQL — an
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
 *   <li>DATETIME — calendar-day comparison on the first 10 chars of the stored ISO string.
 *       Relative operators ({@code today}, {@code last7days}, …) expand to inclusive bounds
 *       in {@link ListRelativeDates#ZONE}; {@code on}/{@code before}/{@code after}/{@code between}
 *       take {@code YYYY-MM-DD} from the dialog.</li>
 *   <li>BOOLEAN — case-insensitive eq/ne against true/false; {@code ne} also matches
 *       empty cells, so Not equals True is not the same as Equals False.</li>
   *   <li>USER — eq/ne match any stored identity of the selected {@code sys_users} row
       *       (id, {@code user:}<id>, username, display_name, full_name, employee_id), so a
       *       people picker can send the user id even when legacy rows stored a prefixed id
       *       or a display name. Grouping uses the same table to produce a display label.</li>
 * </ul>
 */
public final class ListFilterSql {

    /** Matches values that can safely be cast to numeric inside SQL. */
    private static final String SQL_NUMERIC_GUARD = "'^-?[0-9]+(\\.[0-9]+)?$'";
    private static final Pattern DATE_VALUE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final Pattern IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");
    /**
     * Ordering refs may be table-qualified ({@code pi.id}); user-supplied field names may not.
     * A tiebreak may name several columns, because what makes a row unique is not always one
     * column — a sub-table row takes both its instance and its own identity to pin down.
     */
    private static final Pattern ORDERING_REF = Pattern.compile(
            "^[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)?"
                    + "(, *[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)?)*$");

    /** Resolves a declared column name to the SQL expression holding its value. */
    @FunctionalInterface
    public interface ColumnRef {
        String sqlFor(String field);
    }

    /** Value lives in the JSON row document of {@code rt_table_data_rows}. */
    public static final ColumnRef JSON_ROW = field -> "data->>'" + requireIdentifier(field) + "'";

    /** Value is a real text column of the queried table. */
    public static final ColumnRef PHYSICAL_COLUMN = ListFilterSql::requireIdentifier;

    private final Map<String, ListColumnMeta> columnsByField;
    private final ColumnRef columnRef;
    private final String tiebreak;
    private final String defaultOrderBy;
    private final Clock clock;

    /**
     * @param tiebreak       column appended to every ORDER BY so equal values page deterministically
     * @param defaultOrderBy ORDER BY body used when the caller requests no sort, without the
     *                       {@code ORDER BY} keyword and without the tiebreak; null orders by
     *                       the tiebreak alone
     */
    public ListFilterSql(Map<String, ListColumnMeta> columnsByField, ColumnRef columnRef,
                         String tiebreak, String defaultOrderBy) {
        this(columnsByField, columnRef, tiebreak, defaultOrderBy, Clock.system(ListRelativeDates.ZONE));
    }

    public ListFilterSql(Map<String, ListColumnMeta> columnsByField, ColumnRef columnRef,
                         String tiebreak, String defaultOrderBy, Clock clock) {
        this.columnsByField = Map.copyOf(columnsByField);
        this.columnRef = columnRef;
        this.tiebreak = requireOrderingRef(tiebreak);
        this.defaultOrderBy = defaultOrderBy;
        if (clock == null) {
            throw new IllegalArgumentException(
                    "clock is required so relative date filters expand against a known calendar");
        }
        this.clock = clock;
    }

    /** Rows keep their insertion order when nothing else is asked for. */
    public static ListFilterSql orderedById(Map<String, ListColumnMeta> columnsByField,
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
            ListColumnMeta column = requireFilterableColumn(filter);
            sql.append(" AND ").append(predicate(column, filter, outParams));
        }
        return sql.toString();
    }

    /** @return full {@code  ORDER BY ...} clause, always ending in the tiebreak column */
    public String orderBy(String sortField, String sortDirection) {
        return " ORDER BY " + orderTerms(sortField, sortDirection);
    }

    /**
     * Orders by the group expression first, then by whatever the caller sorts on. A group's rows
     * have to be contiguous for the page to be able to render group headers at all — without this
     * the same group would reappear on later pages.
     */
    public String orderByGrouped(String groupExpression, String sortField, String sortDirection) {
        return " ORDER BY " + groupExpression + " ASC NULLS LAST, "
                + orderTerms(sortField, sortDirection);
    }

    private String orderTerms(String sortField, String sortDirection) {
        if (sortField == null) {
            return defaultOrderBy == null ? tiebreak : defaultOrderBy + ", " + tiebreak;
        }
        ListColumnMeta column = columnsByField.get(sortField);
        if (column == null) {
            throw new IllegalArgumentException("Unknown sort column: " + sortField);
        }
        if (!column.sortable()) {
            throw new IllegalArgumentException("Column is not sortable: " + sortField);
        }
        String direction = "DESC".equalsIgnoreCase(sortDirection) ? "DESC" : "ASC";
        return sortExpression(column, columnRef) + " " + direction + " NULLS LAST, " + tiebreak;
    }

    /**
     * The expression a column groups by. Grouping and its counts have to come from the same
     * expression as the ordering that makes a group's rows contiguous, otherwise a page can show
     * the same group header twice with counts that do not add up.
     */
    public String groupByExpression(String field) {
        ListColumnMeta column = columnsByField.get(field);
        if (column == null) {
            throw new IllegalArgumentException("Unknown group column: " + field);
        }
        if (!column.groupable()) {
            throw new IllegalArgumentException("Column is not groupable: " + field);
        }
        String ref = columnRef.sqlFor(column.field());
        // USER groups by the display label resolved from sys_users so headers match cells
        // that also resolve bare ids / user:<id> / legacy name storage through the same table.
        return column.kind() == ListColumnMeta.Kind.USER
                ? userDisplayLabelExpression(ref)
                : ref;
    }

    /**
     * The value expression a column sorts by. Numbers are cast so 9 sorts before 10, which text
     * ordering of a JSON value would get wrong; the cast is guarded so a non-numeric stored value
     * sorts as null rather than aborting the query.
     */
    public static String sortExpression(ListColumnMeta column, ColumnRef columnRef) {
        String ref = columnRef.sqlFor(column.field());
        return column.kind() == ListColumnMeta.Kind.NUMBER
                ? "(CASE WHEN " + ref + " ~ " + SQL_NUMERIC_GUARD + " THEN (" + ref + ")::numeric END)"
                : ref;
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

    private ListColumnMeta requireFilterableColumn(ListColumnFilter filter) {
        ListColumnMeta column = columnsByField.get(filter.field());
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

    private String predicate(ListColumnMeta column, ListColumnFilter filter,
                             List<Object> outParams) {
        String op = filter.operator();
        String ref = columnRef.sqlFor(column.field());
        if ("isNull".equals(op)) {
            return "(" + ref + " IS NULL OR " + ref + " = '')";
        }
        if ("isNotNull".equals(op)) {
            return "(" + ref + " IS NOT NULL AND " + ref + " <> '')";
        }
        if (column.kind() == ListColumnMeta.Kind.DATETIME && ListRelativeDates.isRelative(op)) {
            return relativeDatePredicate(ref, op, outParams);
        }
        String value = requireValue(filter, filter.value());
        return switch (column.kind()) {
            case TEXT, ENUM -> textPredicate(ref, filter, value, outParams);
            case USER -> userIdentityPredicate(ref, filter.operator(), value, outParams);
            case NUMBER -> numberPredicate(ref, filter, value, outParams);
            case DATETIME -> datePredicate(ref, filter, value, outParams);
            case BOOLEAN -> booleanPredicate(ref, filter, value, outParams);
        };
    }

    private String relativeDatePredicate(String ref, String operator, List<Object> outParams) {
        ListRelativeDates.DayRange range = ListRelativeDates.range(operator, LocalDate.now(clock));
        String day = "left(" + ref + ", 10)";
        outParams.add(range.start().toString());
        outParams.add(range.end().toString());
        return "(" + day + " >= ? AND " + day + " <= ?)";
    }

    /**
     * The picker sends {@code sys_users.id}. Stored values may be that id, {@code user:}<id>,
     * a username, a display name, or an employee id — match any of them for the selected row.
     */
    private static String userIdentityPredicate(String ref, String operator, String userId,
                                                List<Object> outParams) {
        String users = SqlIdentifiers.requireQualifiedName("sys_users");
        String id = SqlIdentifiers.requireIdentifier("id");
        String username = SqlIdentifiers.requireIdentifier("username");
        String displayName = SqlIdentifiers.requireIdentifier("display_name");
        String fullName = SqlIdentifiers.requireIdentifier("full_name");
        String employeeId = SqlIdentifiers.requireIdentifier("employee_id");
        String match = "(" + ref + " = u." + id + "::text"
                + " OR " + ref + " = ('user:' || u." + id + "::text)"
                + " OR " + ref + " = u." + username
                + " OR " + ref + " = u." + displayName
                + " OR " + ref + " = u." + fullName
                + " OR " + ref + " = u." + employeeId + ")";
        outParams.add(userId);
        String exists = "EXISTS (SELECT 1 FROM " + users + " u WHERE u." + id + " = ? AND " + match + ")";
        if ("eq".equals(operator)) {
            return exists;
        }
        if ("ne".equals(operator)) {
            return "(NOT " + exists + ")";
        }
        throw new IllegalArgumentException("Operator " + operator + " is not allowed on a USER column");
    }

    /**
     * Display label for a USER cell/group key, resolved through {@code sys_users}. Bare id,
     * {@code user:}<id>, username, and legacy name storage all map to one label so GROUP BY
     * headers match what the portal paints after the same resolution.
     */
    static String userDisplayLabelExpression(String ref) {
        String users = SqlIdentifiers.requireQualifiedName("sys_users");
        String id = SqlIdentifiers.requireIdentifier("id");
        String username = SqlIdentifiers.requireIdentifier("username");
        String displayName = SqlIdentifiers.requireIdentifier("display_name");
        String fullName = SqlIdentifiers.requireIdentifier("full_name");
        String employeeId = SqlIdentifiers.requireIdentifier("employee_id");
        String value = "(" + ref + ")";
        String stripped = "(CASE WHEN left(COALESCE(" + value + ", ''), 5) = 'user:'"
                + " THEN substring(COALESCE(" + value + ", '') from 6) ELSE " + value + " END)";
        String label = "COALESCE(NULLIF(TRIM(u." + displayName + "), ''),"
                + " NULLIF(TRIM(u." + fullName + "), ''), u." + username + ")";
        return "COALESCE((SELECT " + label
                + " FROM " + users + " u WHERE u." + id + "::text = " + stripped
                + " OR u." + id + "::text = " + value
                + " OR ('user:' || u." + id + "::text) = " + value
                + " OR u." + username + " = " + value
                + " OR u." + displayName + " = " + value
                + " OR u." + fullName + " = " + value
                + " OR u." + employeeId + " = " + value
                + " LIMIT 1), " + value + ")";
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
        String normalized = value.trim().toLowerCase();
        if (!"true".equals(normalized) && !"false".equals(normalized)) {
            throw new IllegalArgumentException(
                    "Boolean filter on " + filter.field() + " requires true/false, got: " + value);
        }
        outParams.add(normalized);
        return switch (filter.operator()) {
            case "eq" -> "lower(" + ref + ") = ?";
            case "ne" -> "(" + ref + " IS NULL OR lower(" + ref + ") <> ?)";
            default -> throw unsupported(filter);
        };
    }

    /** Escapes LIKE wildcards so user input matches literally (PG default escape is backslash). */
    /** Wildcards a user typed are literal text, not pattern syntax. */
    public static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String requireIdentifier(String field) {
        if (field == null || !IDENTIFIER.matcher(field).matches()) {
            throw new IllegalArgumentException("Invalid field identifier: " + field);
        }
        return field;
    }

    private static String requireOrderingRef(String ref) {
        if (ref == null || !ORDERING_REF.matcher(ref).matches()) {
            throw new IllegalArgumentException("Invalid ordering reference: " + ref);
        }
        return ref;
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
