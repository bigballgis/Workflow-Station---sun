package com.portal.util;

import com.platform.common.list.ListColumnMeta;
import com.platform.common.list.ListColumnMeta.Option;
import com.platform.common.list.ListColumnMeta.Kind;
import com.platform.common.list.ListFilterSql;
import com.platform.common.audit.SystemAuditFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Declares what a Main Table View's columns can do, and where each one's value lives in SQL.
 *
 * <p>A column is only declared filterable or sortable when the query can answer it honestly.
 * System columns and designed JSON fields compare in place. Lookup / FK <em>display</em> columns
 * are filterable when the stored-key mapping is known — {@link MainTableViewDerivedFilterSql}
 * converts the visible label to that key. Without a mapping they stay display-only, so the header
 * cannot compare the label to the raw key. Sort stays off for mapped display columns.
 *
 */
public final class MainTableViewColumnSpec {

    /**
     * A view field as stored in {@code dw_main_table_view_fields}, plus its designed data type
     * and, for lookup / FK display columns, the stored-key mapping the filter compiler needs.
     */
    public record FieldSource(
            String fieldName,
            String displayLabel,
            boolean systemField,
            String columnType,
            String dataType,
            String lookupSourceField,
            String lookupDisplayField,
            Long lookupTableId,
            List<String> fkPrimaryKeyFields) {

        public FieldSource {
            fkPrimaryKeyFields = fkPrimaryKeyFields == null ? List.of() : List.copyOf(fkPrimaryKeyFields);
        }

        public FieldSource(String fieldName, String displayLabel, boolean systemField,
                           String columnType, String dataType) {
            this(fieldName, displayLabel, systemField, columnType, dataType, null, null, null, List.of());
        }
    }

    /**
     * Where a view's row lives in SQL.
     *
     * @param jsonSource         expression holding the row's designed fields as jsonb
     * @param instanceIsTheRow   whether the row <em>is</em> a process instance. Designed JSON
     *                           fields still read from {@code jsonSource}. System columns
     *                           (status, start time, initiator, current step) always read the
     *                           owning instance — a SUB row still belongs to one process.
     */
    public record SqlSource(String jsonSource, boolean instanceIsTheRow) {

        /** A MAIN view: one process instance, one row. */
        public static final SqlSource INSTANCE = new SqlSource("pi.variables", true);

        /** A SUB view: one member of {@code __subTables__}, one row. */
        public static final SqlSource EXPANDED_SUB_ROW = new SqlSource("pi.sub_elem", false);
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

    /** Stored values of {@code up_process_instance.status} that a Views status filter may pick. */
    private static final List<Option> PROCESS_STATUS_OPTIONS = List.of(
            new Option("RUNNING", "Running"),
            new Option("COMPLETED", "Completed"),
            new Option("WITHDRAWN", "Withdrawn"));

    private MainTableViewColumnSpec() {
    }

    /** @return one declaration per field, in the order given */
    public static List<ListColumnMeta> columnsFor(List<FieldSource> fields) {
        return columnsFor(fields, SqlSource.INSTANCE);
    }

    /**
     * Kind follows the field, not the row source: MAIN and SUB share the same declarations.
     * Callers still pass {@code source} so this stays next to {@link #columnRefFor}.
     */
    public static List<ListColumnMeta> columnsFor(List<FieldSource> fields, SqlSource source) {
        if (source == null) {
            throw new IllegalArgumentException("SqlSource is required");
        }
        List<ListColumnMeta> columns = new ArrayList<>(fields.size());
        for (FieldSource field : fields) {
            columns.add(columnFor(field));
        }
        return columns;
    }

    /**
     * Where each declared column's value lives. Designed fields read a JSON member of the
     * source's row; the field name is validated as an identifier there, so a crafted column name
     * cannot reach SQL.
     */
    public static ListFilterSql.ColumnRef columnRefFor(List<FieldSource> fields, SqlSource source) {
        Map<String, String> systemExpressions = new LinkedHashMap<>();
        for (FieldSource field : fields) {
            SystemColumn system = systemColumnOf(field);
            if (system != null) {
                systemExpressions.put(field.fieldName(), system.sqlExpression());
            }
        }
        return field -> {
            String system = systemExpressions.get(field);
            return system != null ? system : source.jsonSource() + "->>'" + requireJsonKey(field) + "'";
        };
    }

    /**
     * Where the designer's own filter may look.
     *
     * <p>Wider than the user-facing declarations on purpose: a designer may narrow a view by a
     * field the view does not display, and that field's value is still a JSON member the database
     * can read. Only the derived columns are refused (by answering null), because their displayed
     * value is computed after the read and the stored key is not the same thing.
     */
    public static MainTableViewDesignerFilterSql.QueryableRef designerRefFor(List<FieldSource> fields,
                                                                            SqlSource source) {
        Map<String, FieldSource> byName = new LinkedHashMap<>();
        for (FieldSource field : fields) {
            byName.put(field.fieldName(), field);
        }
        ListFilterSql.ColumnRef columnRef = columnRefFor(fields, source);
        return field -> {
            FieldSource declared = byName.get(field);
            if (declared != null && isDerivedColumn(declared.columnType())) {
                return null;
            }
            return columnRef.sqlFor(field);
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
        return sqlFor(fields, sortConfig, SqlSource.INSTANCE, "pi.id");
    }

    /**
     * @param source   where the row lives
     * @param tiebreak what makes a row unique: an instance id for MAIN, and for SUB the instance
     *                 plus the row's own identity, since one instance yields many rows
     */
    public static ListFilterSql sqlFor(List<FieldSource> fields, List<Map<String, Object>> sortConfig,
                                       SqlSource source, String tiebreak) {
        Map<String, ListColumnMeta> byField = new LinkedHashMap<>();
        Map<String, FieldSource> sources = new LinkedHashMap<>();
        for (FieldSource field : fields) {
            sources.put(field.fieldName(), field);
        }
        for (ListColumnMeta column : columnsFor(fields, source)) {
            FieldSource declared = sources.get(column.field());
            // Display-mapped filters compile in MainTableViewDerivedFilterSql. Demoting them here
            // means a leaked label filter is a 400 instead of comparing the label to the stored key.
            byField.put(column.field(), declared != null && isDisplayMapped(declared)
                    ? ListColumnMeta.displayOnly(column.field(), column.label(), column.kind())
                    : column);
        }
        ListFilterSql.ColumnRef columnRef = columnRefFor(fields, source);
        return new ListFilterSql(byField, columnRef, tiebreak,
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
    private static String designerOrderBy(Map<String, ListColumnMeta> byField,
                                          ListFilterSql.ColumnRef columnRef,
                                          List<Map<String, Object>> sortConfig) {
        StringBuilder order = new StringBuilder();
        for (Map<String, Object> spec : sortConfig) {
            Object rawField = spec.get("fieldName");
            if (rawField == null) {
                continue;
            }
            ListColumnMeta column = byField.get(String.valueOf(rawField));
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

    private static ListColumnMeta columnFor(FieldSource field) {
        String label = field.displayLabel() != null && !field.displayLabel().isBlank()
                ? field.displayLabel()
                : field.fieldName();
        SystemColumn system = systemColumnOf(field);
        if (system != null) {
            return queryable(field.fieldName(), label, system.kind());
        }
        if (isDerivedColumn(field.columnType())) {
            return isDisplayMapped(field)
                    ? ListColumnMeta.displayMapped(field.fieldName(), label)
                    : ListColumnMeta.displayOnly(field.fieldName(), label, Kind.TEXT);
        }
        if (SystemAuditFields.isTimestamp(field.fieldName())) {
            return queryable(field.fieldName(), label, Kind.DATETIME);
        }
        if (SystemAuditFields.isUser(field.fieldName())) {
            return queryable(field.fieldName(), label, Kind.USER);
        }
        if (field.systemField()) {
            return ListColumnMeta.displayOnly(field.fieldName(), label, Kind.TEXT);
        }
        Kind kind = kindOf(field.dataType());
        return kind == null
                ? ListColumnMeta.displayOnly(field.fieldName(), label, Kind.TEXT)
                : queryable(field.fieldName(), label, kind);
    }

    private static ListColumnMeta queryable(String field, String label, Kind kind) {
        if (kind == Kind.BOOLEAN) {
            return ListColumnMeta.withOptions(field, label, kind, ListColumnMeta.booleanOptions());
        }
        if (kind == Kind.ENUM) {
            if (!"process_status".equals(field)) {
                throw new IllegalStateException(
                        "ENUM column " + field + " has no closed option list — declare it with withOptions");
            }
            return ListColumnMeta.withOptions(field, label, kind, PROCESS_STATUS_OPTIONS);
        }
        return ListColumnMeta.of(field, label, kind);
    }

    /**
     * The four instance-level view columns. They live on {@code up_process_instance}, so MAIN and
     * SUB views use the same expressions: a sub-table row is still owned by one instance.
     */
    private static SystemColumn systemColumnOf(FieldSource field) {
        if (!field.systemField()) {
            return null;
        }
        return SYSTEM_COLUMNS.get(field.fieldName());
    }

    static boolean isLookupDisplay(String columnType) {
        return "lookup_display".equalsIgnoreCase(columnType);
    }

    static boolean isFkDisplay(String columnType) {
        return "fk_display".equalsIgnoreCase(columnType);
    }

    private static boolean isDerivedColumn(String columnType) {
        return isLookupDisplay(columnType) || isFkDisplay(columnType);
    }

    /**
     * True when this display column has the stored-key mapping the filter compiler needs.
     * Lookup needs a source field and a table id; FK needs a source field and a display attribute.
     */
    public static boolean isDisplayMapped(FieldSource field) {
        if (field.lookupSourceField() == null || field.lookupSourceField().isBlank()) {
            return false;
        }
        if (isLookupDisplay(field.columnType())) {
            return field.lookupTableId() != null;
        }
        if (isFkDisplay(field.columnType())) {
            return field.lookupDisplayField() != null && !field.lookupDisplayField().isBlank();
        }
        return false;
    }

    /**
     * @return the kind a designed field is queried as, or null when the stored value is a file /
     *         blob reference rather than something a user would filter on. Untyped and JSON
     *         fields compare as TEXT against the JSON member. TIME is a clock-of-day DATETIME.
     */
    private static Kind kindOf(String dataType) {
        if (dataType == null || dataType.isBlank()) {
            return Kind.TEXT;
        }
        return switch (dataType.trim().toUpperCase(Locale.ROOT)) {
            case "VARCHAR", "TEXT", "JSON" -> Kind.TEXT;
            case "INTEGER", "BIGINT", "DECIMAL" -> Kind.NUMBER;
            case "DATE", "TIMESTAMP", "TIME" -> Kind.DATETIME;
            case "BOOLEAN" -> Kind.BOOLEAN;
            case "FILE", "BYTEA" -> null;
            default -> Kind.TEXT;
        };
    }

    private static String requireJsonKey(String field) {
        if (field == null || !field.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException("Invalid view field name: " + field);
        }
        return field;
    }
}
