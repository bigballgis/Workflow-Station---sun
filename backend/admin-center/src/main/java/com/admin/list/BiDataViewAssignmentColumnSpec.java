package com.admin.list;

import com.platform.common.list.ListColumnMeta;
import com.platform.common.list.ListColumnMeta.Kind;
import com.platform.common.list.ListFilterSql;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Column whitelist shared by SQL filters, ordering and the Admin header menus. */
public final class BiDataViewAssignmentColumnSpec {
    private BiDataViewAssignmentColumnSpec() { }

    public static List<ListColumnMeta> columns() {
        return List.of(
                ListColumnMeta.of("dashboardTitle", "bi.dataViewAssignment.colDashboard", Kind.TEXT),
                ListColumnMeta.of("functionUnitName", "bi.dataViewAssignment.colTarget", Kind.TEXT),
                ListColumnMeta.of("tableDisplayName", "bi.dataViewAssignment.colTable", Kind.TEXT),
                ListColumnMeta.of("viewName", "bi.dataViewAssignment.formView", Kind.TEXT),
                ListColumnMeta.withOptions("enabled", "bi.assignment.enabled", Kind.BOOLEAN, List.of(
                        new ListColumnMeta.Option("true", "bi.assignment.enabled"),
                        new ListColumnMeta.Option("false", "bi.assignment.disabled"))));
    }

    public static ListFilterSql sql() {
        Map<String, ListColumnMeta> byField = new LinkedHashMap<>();
        columns().forEach(column -> byField.put(column.field(), column));
        return new ListFilterSql(byField, BiDataViewAssignmentColumnSpec::sqlFor, "a.id", "a.created_at ASC");
    }

    private static String sqlFor(String field) {
        return switch (field) {
            case "dashboardTitle" -> "d.dashboard_title";
            case "functionUnitName" -> "fu.name";
            case "tableDisplayName" -> "COALESCE(NULLIF(t.table_display_name, ''), t.table_name)";
            case "viewName" -> "v.view_name";
            case "enabled" -> "a.enabled::text";
            default -> throw new IllegalArgumentException("Unknown Data View Assignment column: " + field);
        };
    }
}
