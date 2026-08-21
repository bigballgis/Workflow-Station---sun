package com.portal.util;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Compiles a Main Table View's designed filter ({@code dw_main_table_view_configs.filter_config})
 * into SQL.
 *
 * <p>This is not the filter the user types — it is the one the view's designer baked in, and it
 * decides which rows the view is <em>about</em>. It therefore has to be applied before paging: a
 * view narrowed to open requests must page through open requests, not page through everything and
 * hide some of each page.
 *
 * <p>It is a separate compiler from {@link ListFilterSql} because it is a different language. The
 * designer's vocabulary is wider ({@code notStartsWith}, {@code isNull}, {@code in}) and its
 * comparisons are case-insensitive throughout, and its treatment of a missing value differs:
 * every operator except the two null checks rejects a row whose value is absent, including the
 * negative ones. Those semantics are matched here condition for condition, because the views
 * already in use were designed against them.
 *
 * <p>A condition on a column whose displayed value is computed in Java — a lookup or FK label —
 * cannot be compiled: the database holds the key, not the label, so the filter would quietly
 * match on something other than what the designer saw. That is reported rather than approximated.
 */
public final class MainTableViewDesignerFilterSql {

    private static final Pattern NUMERIC = Pattern.compile("^-?[0-9]+(\\.[0-9]+)?$");
    private static final String NUMERIC_GUARD = "'^-?[0-9]+(\\.[0-9]+)?$'";

    /** Resolves a designed field to its SQL expression, or null when it has no queryable one. */
    @FunctionalInterface
    public interface QueryableRef {
        String sqlFor(String field);
    }

    private final QueryableRef columnRef;
    private final String context;

    /**
     * @param context what to name in an error — the view being compiled, so a broken design is
     *                traceable without the caller having to guess which of many views it was
     */
    public MainTableViewDesignerFilterSql(QueryableRef columnRef, String context) {
        this.columnRef = columnRef;
        this.context = context;
    }

    /**
     * @return SQL fragment starting with {@code  AND (...)}, or "" when the view filters nothing;
     *         bind values are appended to {@code outParams} in order
     */
    public String whereClause(Map<String, Object> filterConfig, List<Object> outParams) {
        if (filterConfig == null || filterConfig.isEmpty()) {
            return "";
        }
        String node = node(filterConfig, outParams);
        return node.isEmpty() ? "" : " AND " + node;
    }

    @SuppressWarnings("unchecked")
    private String node(Map<String, Object> node, List<Object> outParams) {
        if (node == null || node.isEmpty()) {
            return "";
        }
        List<String> terms = new ArrayList<>();
        for (Map<String, Object> condition : mapsUnder(node, "conditions")) {
            terms.add(condition(condition, outParams));
        }
        for (Map<String, Object> group : mapsUnder(node, "groups")) {
            String nested = node(group, outParams);
            if (!nested.isEmpty()) {
                terms.add(nested);
            }
        }
        if (terms.isEmpty()) {
            return "";
        }
        String glue = "or".equalsIgnoreCase(stringOf(node.get("logic"))) ? " OR " : " AND ";
        return "(" + String.join(glue, terms) + ")";
    }

    private String condition(Map<String, Object> condition, List<Object> outParams) {
        String field = stringOf(condition.get("fieldName"));
        String operator = stringOf(condition.get("operator"));
        Object expected = condition.get("value");

        if (operator == null || operator.isBlank()) {
            // A condition with no operator constrains nothing, which is how the view already behaves.
            return "TRUE";
        }
        String ref = columnRef.sqlFor(field);
        if (ref == null) {
            throw new IllegalStateException(context + " filters on '" + field
                    + "', whose value the database does not hold — the column is either not part of"
                    + " the view or is a label resolved after the rows are read. Filtering on it"
                    + " would match the underlying key instead. Redesign the view's filter.");
        }

        return switch (operator.trim()) {
            case "isNull" -> "(" + ref + " IS NULL OR btrim(" + ref + ") = '')";
            case "isNotNull" -> "(" + ref + " IS NOT NULL AND btrim(" + ref + ") <> '')";
            case "eq" -> present(ref, "lower(" + ref + ") = lower(?)", outParams, text(expected));
            case "ne" -> present(ref, "lower(" + ref + ") <> lower(?)", outParams, text(expected));
            case "contains" -> present(ref, ref + " ILIKE ?", outParams, "%" + like(expected) + "%");
            case "notContains" -> present(ref, ref + " NOT ILIKE ?", outParams, "%" + like(expected) + "%");
            case "startsWith" -> present(ref, ref + " ILIKE ?", outParams, like(expected) + "%");
            case "notStartsWith" -> present(ref, ref + " NOT ILIKE ?", outParams, like(expected) + "%");
            case "endsWith" -> present(ref, ref + " ILIKE ?", outParams, "%" + like(expected));
            case "notEndsWith" -> present(ref, ref + " NOT ILIKE ?", outParams, "%" + like(expected));
            case "gt" -> comparison(ref, ">", expected, outParams);
            case "lt" -> comparison(ref, "<", expected, outParams);
            case "in" -> in(ref, expected, outParams);
            default -> throw new IllegalStateException(context + " uses filter operator '" + operator
                    + "', which has no meaning here. Redesign the view's filter.");
        };
    }

    /**
     * Every operator but the null checks starts by rejecting rows without a value — including the
     * negative ones, where SQL's own three-valued logic would instead drop the row silently. Both
     * end up excluding it; spelling it out keeps the reason visible.
     */
    private String present(String ref, String predicate, List<Object> outParams, String bind) {
        outParams.add(bind);
        return "(" + ref + " IS NOT NULL AND " + predicate + ")";
    }

    /**
     * The designer's {@code gt}/{@code lt} compare as numbers when both sides look like numbers
     * and as text otherwise — a per-row decision, since one row may hold "12" and the next "n/a".
     * With a non-numeric bound the numeric branch can never be taken, so it is not emitted.
     */
    private String comparison(String ref, String operator, Object expected, List<Object> outParams) {
        String bound = text(expected);
        if (!NUMERIC.matcher(bound).matches()) {
            outParams.add(bound);
            return "(" + ref + " IS NOT NULL AND lower(" + ref + ") " + operator + " lower(?))";
        }
        outParams.add(new BigDecimal(bound));
        outParams.add(bound);
        return "(" + ref + " IS NOT NULL AND CASE WHEN " + ref + " ~ " + NUMERIC_GUARD
                + " THEN (" + ref + ")::numeric " + operator + " ?"
                + " ELSE lower(" + ref + ") " + operator + " lower(?) END)";
    }

    /** A list of accepted values, given either as a collection or as one comma-separated string. */
    private String in(String ref, Object expected, List<Object> outParams) {
        List<String> values = new ArrayList<>();
        if (expected instanceof Collection<?> collection) {
            for (Object value : collection) {
                values.add(String.valueOf(value));
            }
        } else {
            for (String value : text(expected).split(",")) {
                values.add(value.trim());
            }
        }
        if (values.isEmpty()) {
            // An empty list accepts nothing, which is what comparing against no value means.
            return "FALSE";
        }
        StringBuilder sql = new StringBuilder("(" + ref + " IS NOT NULL AND lower(" + ref + ") IN (");
        for (int i = 0; i < values.size(); i++) {
            sql.append(i > 0 ? ", lower(?)" : "lower(?)");
            outParams.add(values.get(i));
        }
        return sql.append("))").toString();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> mapsUnder(Map<String, Object> node, String key) {
        List<Map<String, Object>> maps = new ArrayList<>();
        if (node.get(key) instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    maps.add((Map<String, Object>) map);
                }
            }
        }
        return maps;
    }

    private static String like(Object expected) {
        return ListFilterSql.escapeLike(text(expected));
    }

    private static String text(Object expected) {
        return expected != null ? String.valueOf(expected) : "";
    }

    private static String stringOf(Object raw) {
        return raw != null ? String.valueOf(raw) : null;
    }
}
