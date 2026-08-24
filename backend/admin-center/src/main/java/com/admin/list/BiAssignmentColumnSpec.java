package com.admin.list;

import com.platform.common.list.ListColumnMeta;
import com.platform.common.list.ListColumnMeta.Kind;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BI Dashboard Assignment columns. Toolbar targetType/dashboardTitle stay outside this spec
 * and AND with header filters. Outer alias is {@code a}; registry is {@code d}.
 */
public final class BiAssignmentColumnSpec {

    private BiAssignmentColumnSpec() {
    }

    /** Resolved display name for USER / ROLE / BUSINESS_UNIT targets. */
    static final String TARGET_NAME_SQL = """
            CASE a.target_type \
            WHEN 'USER' THEN (SELECT COALESCE(u.display_name, u.username) FROM sys_users u WHERE u.id = a.target_id) \
            WHEN 'ROLE' THEN (SELECT r.name FROM sys_roles r WHERE r.id = a.target_id) \
            WHEN 'BUSINESS_UNIT' THEN (SELECT bu.name FROM sys_business_units bu WHERE bu.id = a.target_id) \
            END""";

    public static List<ListColumnMeta> columns() {
        return List.of(
                ListColumnMeta.of("dashboardTitle", "bi.assignment.colDashboardTitle", Kind.TEXT),
                ListColumnMeta.withOptions("targetType", "bi.assignment.colTargetType", Kind.ENUM, targetTypeOptions()),
                ListColumnMeta.of("targetName", "bi.assignment.colTargetName", Kind.TEXT),
                ListColumnMeta.withOptions("layoutMode", "bi.assignment.colLayoutMode", Kind.ENUM, layoutModeOptions()),
                ListColumnMeta.of("displayOrder", "bi.assignment.colDisplayOrder", Kind.NUMBER),
                ListColumnMeta.of("isDefault", "bi.assignment.colDefault", Kind.BOOLEAN)
        );
    }

    public static ListFilterSql sql() {
        Map<String, ListColumnMeta> byField = new LinkedHashMap<>();
        for (ListColumnMeta column : columns()) {
            byField.put(column.field(), column);
        }
        return new ListFilterSql(byField, BiAssignmentColumnSpec::sqlFor, "a.id", "a.display_order ASC");
    }

    static String sqlFor(String field) {
        return switch (field) {
            case "dashboardTitle" -> "d.dashboard_title";
            case "targetType" -> "a.target_type";
            case "targetName" -> TARGET_NAME_SQL;
            case "layoutMode" -> "a.layout_mode";
            case "displayOrder" -> "a.display_order::text";
            case "isDefault" -> "a.is_default::text";
            default -> throw new IllegalArgumentException("Unknown bi-assignment column: " + field);
        };
    }

    private static List<ListColumnMeta.Option> targetTypeOptions() {
        return List.of(
                new ListColumnMeta.Option("USER", "bi.assignment.targetTypeUser"),
                new ListColumnMeta.Option("ROLE", "bi.assignment.targetTypeRole"),
                new ListColumnMeta.Option("BUSINESS_UNIT", "bi.assignment.targetTypeBusinessUnit")
        );
    }

    private static List<ListColumnMeta.Option> layoutModeOptions() {
        return List.of(
                new ListColumnMeta.Option("SINGLE", "bi.assignment.layoutModeSingle"),
                new ListColumnMeta.Option("MULTI", "bi.assignment.layoutModeMulti"),
                new ListColumnMeta.Option("WIDGET", "bi.assignment.layoutModeWidget")
        );
    }
}
