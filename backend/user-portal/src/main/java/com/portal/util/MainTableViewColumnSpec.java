package com.portal.util;

import com.portal.dto.PortalListColumnMeta;
import com.portal.dto.PortalListColumnMeta.Kind;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Declares what a Main Table View's columns can do, and where each one's value lives in SQL.
 *
 * <p>A column is only declared filterable or sortable when the database can actually answer that
 * question about it: system columns are real columns of {@code up_process_instance}, designed
 * fields are JSON members of {@code variables}, and both can be pushed down. Lookup and FK display
 * columns cannot — their value is resolved in Java from other variables after the rows are read,
 * so filtering on one would silently filter on the raw key instead of the label the user sees.
 * Those are declared display-only rather than offered and then quietly misbehaving.
 *
 * <p>Grouping is declared per {@link PortalListColumnMeta#defaultGroupable(Kind)}: it makes sense
 * where values repeat (status, assignee, booleans) and not on free text or timestamps.
 */
public final class MainTableViewColumnSpec {

    /** A view field as stored in {@code dw_main_table_view_fields}, plus its designed data type. */
    public record FieldSource(
            String fieldName,
            String displayLabel,
            boolean systemField,
            String columnType,
            String dataType) {
    }

    /**
     * System columns of the view, mapped to the {@code up_process_instance} expression that holds
     * them. {@code initiator} shows the display name and falls back to the id in projection, so the
     * filter has to look at both or filtering by what is on screen would miss rows.
     */
    private static final Map<String, SystemColumn> SYSTEM_COLUMNS = Map.of(
            "process_status", new SystemColumn("pi.status", Kind.ENUM),
            "start_time", new SystemColumn("pi.start_time::text", Kind.DATETIME),
            "initiator", new SystemColumn("COALESCE(pi.start_user_name, pi.start_user_id)", Kind.USER),
            "current_step", new SystemColumn("pi.current_node", Kind.TEXT));

    private record SystemColumn(String sqlExpression, Kind kind) {
    }

    private MainTableViewColumnSpec() {
    }

    /** @return one declaration per field, in the order given */
    public static List<PortalListColumnMeta> columnsFor(List<FieldSource> fields) {
        List<PortalListColumnMeta> columns = new ArrayList<>(fields.size());
        for (FieldSource field : fields) {
            columns.add(columnFor(field));
        }
        return columns;
    }

    /**
     * Declarations for a view whose rows the query cannot yet address one by one — SUB views,
     * whose rows are JSON members expanded out of an instance and still paged in memory. Offering
     * a filter the read path would ignore is worse than offering none, so these columns declare
     * nothing beyond their label.
     */
    public static List<PortalListColumnMeta> displayOnlyColumnsFor(List<FieldSource> fields) {
        List<PortalListColumnMeta> columns = new ArrayList<>(fields.size());
        for (FieldSource field : fields) {
            String label = field.displayLabel() != null && !field.displayLabel().isBlank()
                    ? field.displayLabel()
                    : field.fieldName();
            columns.add(PortalListColumnMeta.displayOnly(field.fieldName(), label, Kind.TEXT));
        }
        return columns;
    }

    /**
     * Where each declared column's value lives. Designed fields read the JSON member of
     * {@code variables}; the field name is validated as an identifier there, so a crafted column
     * name cannot reach SQL.
     */
    public static ListFilterSql.ColumnRef columnRefFor(List<FieldSource> fields) {
        Map<String, String> systemExpressions = new LinkedHashMap<>();
        for (FieldSource field : fields) {
            SystemColumn system = systemColumnOf(field);
            if (system != null) {
                systemExpressions.put(field.fieldName(), system.sqlExpression());
            }
        }
        return field -> {
            String system = systemExpressions.get(field);
            return system != null ? system : "pi.variables->>'" + requireJsonKey(field) + "'";
        };
    }

    /**
     * The compiler for one view: filters and user sort compile against these columns, and a view
     * the user has not sorted keeps the order its designer configured.
     *
     * @param sortConfig {@code dw_main_table_view_configs.sort_config} entries
     *                   ({@code fieldName} + {@code direction})
     */
    public static ListFilterSql sqlFor(List<FieldSource> fields, List<Map<String, Object>> sortConfig) {
        Map<String, PortalListColumnMeta> byField = new LinkedHashMap<>();
        for (PortalListColumnMeta column : columnsFor(fields)) {
            byField.put(column.field(), column);
        }
        ListFilterSql.ColumnRef columnRef = columnRefFor(fields);
        return new ListFilterSql(byField, columnRef, "pi.id",
                designerOrderBy(byField, columnRef, sortConfig));
    }

    /**
     * The designer's sort, compiled.
     *
     * <p>Ascending puts nulls first and descending puts them last — the inverse of SQL's default,
     * and deliberately so: that is where the in-memory comparator this replaces put them, and a
     * silent reshuffle of every view's first page is not an acceptable side effect of moving the
     * sort into the database. {@code start_time DESC} closes the expression because it is the
     * order rows arrived in before any view sort was applied.
     */
    private static String designerOrderBy(Map<String, PortalListColumnMeta> byField,
                                          ListFilterSql.ColumnRef columnRef,
                                          List<Map<String, Object>> sortConfig) {
        StringBuilder order = new StringBuilder();
        for (Map<String, Object> spec : sortConfig) {
            Object rawField = spec.get("fieldName");
            if (rawField == null) {
                continue;
            }
            PortalListColumnMeta column = byField.get(String.valueOf(rawField));
            if (column == null) {
                // The view no longer shows this field; its own projection could not sort by it either.
                continue;
            }
            boolean descending = "DESC".equalsIgnoreCase(String.valueOf(spec.get("direction")));
            order.append(ListFilterSql.sortExpression(column, columnRef))
                    .append(descending ? " DESC NULLS LAST, " : " ASC NULLS FIRST, ");
        }
        return order.append("pi.start_time DESC").toString();
    }

    private static PortalListColumnMeta columnFor(FieldSource field) {
        String label = field.displayLabel() != null && !field.displayLabel().isBlank()
                ? field.displayLabel()
                : field.fieldName();
        SystemColumn system = systemColumnOf(field);
        if (system != null) {
            return queryable(field.fieldName(), label, system.kind());
        }
        if (isDerivedColumn(field.columnType())) {
            return PortalListColumnMeta.displayOnly(field.fieldName(), label, Kind.TEXT);
        }
        Kind kind = kindOf(field.dataType());
        return kind == null
                ? PortalListColumnMeta.displayOnly(field.fieldName(), label, Kind.TEXT)
                : queryable(field.fieldName(), label, kind);
    }

    private static PortalListColumnMeta queryable(String field, String label, Kind kind) {
        return new PortalListColumnMeta(field, label, kind, true, true,
                PortalListColumnMeta.defaultGroupable(kind),
                PortalListColumnMeta.operatorsFor(kind), List.of());
    }

    private static SystemColumn systemColumnOf(FieldSource field) {
        return field.systemField() ? SYSTEM_COLUMNS.get(field.fieldName()) : null;
    }

    private static boolean isDerivedColumn(String columnType) {
        return "lookup_display".equalsIgnoreCase(columnType) || "fk_display".equalsIgnoreCase(columnType);
    }

    /**
     * @return the kind a designed field is queried as, or null when it has none the database can
     *         compare — a field with no type declaration, or a file whose stored value is a
     *         reference rather than something a user would filter on
     */
    private static Kind kindOf(String dataType) {
        if (dataType == null) {
            return null;
        }
        return switch (dataType.trim().toUpperCase(Locale.ROOT)) {
            case "VARCHAR", "TEXT" -> Kind.TEXT;
            case "INTEGER", "BIGINT", "DECIMAL" -> Kind.NUMBER;
            case "DATE", "TIMESTAMP" -> Kind.DATETIME;
            case "BOOLEAN" -> Kind.BOOLEAN;
            default -> null;
        };
    }

    private static String requireJsonKey(String field) {
        if (field == null || !field.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException("Invalid view field name: " + field);
        }
        return field;
    }
}
