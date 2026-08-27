package com.portal.util;

import com.platform.common.list.ListColumnMeta;
import com.platform.common.list.ListColumnMeta.Kind;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * All-requests audit list for one function unit. Same stored columns as My Requests,
 * plus initiator — reviewers see other people's requests, so who raised it is a
 * first-class column here.
 */
public final class AuditApplicationColumnSpec {

    private AuditApplicationColumnSpec() {
    }

    public static List<ListColumnMeta> columns() {
        return List.of(
                ListColumnMeta.of("requestId", "application.requestId", Kind.TEXT),
                ListColumnMeta.of("businessKey", "application.processTitle", Kind.TEXT),
                ListColumnMeta.of("startUserName", "audit.initiator", Kind.USER),
                ListColumnMeta.of("currentAssignee", "application.currentAssignee", Kind.USER),
                ListColumnMeta.of("startTime", "application.startTime", Kind.DATETIME),
                ListColumnMeta.withOptions("status", "application.status", Kind.ENUM, statusOptions())
        );
    }

    /** Visible columns the toolbar Quick Find ORs across — the same values the grid shows. */
    public static List<String> searchableFields() {
        return columns().stream().map(ListColumnMeta::field).toList();
    }

    public static ListFilterSql sql() {
        Map<String, ListColumnMeta> byField = new LinkedHashMap<>();
        for (ListColumnMeta column : columns()) {
            byField.put(column.field(), column);
        }
        return new ListFilterSql(byField, AuditApplicationColumnSpec::sqlFor, "pi.id", "pi.start_time DESC");
    }

    private static String sqlFor(String field) {
        return switch (field) {
            case "requestId" -> "pi.variables->>'__request_id'";
            case "businessKey" -> "pi.business_key";
            case "startUserName" -> "COALESCE(pi.start_user_name, pi.start_user_id)";
            case "currentAssignee" -> "pi.current_assignee";
            case "startTime" -> "pi.start_time::text";
            case "status" -> "pi.status";
            default -> throw new IllegalArgumentException("Unknown audit-application column: " + field);
        };
    }

    private static List<ListColumnMeta.Option> statusOptions() {
        return List.of(
                new ListColumnMeta.Option("RUNNING", "application.running"),
                new ListColumnMeta.Option("COMPLETED", "application.completed"),
                new ListColumnMeta.Option("WITHDRAWN", "application.withdrawn"),
                new ListColumnMeta.Option("REJECTED", "application.rejected")
        );
    }
}
