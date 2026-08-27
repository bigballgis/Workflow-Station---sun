package com.portal.util;

import com.platform.common.list.ListColumnMeta;
import com.platform.common.list.ListColumnMeta.Kind;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fixed column declaration for My Requests. Status is the closed process-instance ENUM;
 * current assignee is USER (people picker). Request ID is the persisted
 * process-variable text {@code __request_id}; filter/sort compile to that JSON path.
 */
public final class MyApplicationColumnSpec {

    private MyApplicationColumnSpec() {
    }

    public static List<ListColumnMeta> columns() {
        return List.of(
                ListColumnMeta.of("requestId", "application.requestId", Kind.TEXT),
                ListColumnMeta.of("businessKey", "application.processTitle", Kind.TEXT),
                ListColumnMeta.of("currentAssignee", "application.currentAssignee", Kind.USER),
                ListColumnMeta.of("startTime", "application.startTime", Kind.DATETIME),
                ListColumnMeta.withOptions("status", "application.status", Kind.ENUM, statusOptions())
        );
    }

    public static ListFilterSql sql() {
        Map<String, ListColumnMeta> byField = new LinkedHashMap<>();
        for (ListColumnMeta column : columns()) {
            byField.put(column.field(), column);
        }
        return new ListFilterSql(byField, MyApplicationColumnSpec::sqlFor, "pi.id", "pi.start_time DESC");
    }

    private static String sqlFor(String field) {
        return switch (field) {
            case "requestId" -> "pi.variables->>'__request_id'";
            case "businessKey" -> "pi.business_key";
            case "currentAssignee" -> "pi.current_assignee";
            case "startTime" -> "pi.start_time::text";
            case "status" -> "pi.status";
            default -> throw new IllegalArgumentException("Unknown my-application column: " + field);
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
