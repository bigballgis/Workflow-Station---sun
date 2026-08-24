package com.portal.util;

import com.portal.dto.PortalListColumnMeta;
import com.portal.dto.PortalListColumnMeta.Kind;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fixed column declaration for My Requests. Status is the closed process-instance ENUM;
 * current assignee is USER (people picker + groupable). Request ID is the persisted
 * process-variable text {@code __request_id}; filter/sort compile to that JSON path.
 */
public final class MyApplicationColumnSpec {

    private MyApplicationColumnSpec() {
    }

    public static List<PortalListColumnMeta> columns() {
        return List.of(
                PortalListColumnMeta.of("requestId", "application.requestId", Kind.TEXT),
                PortalListColumnMeta.of("businessKey", "application.processTitle", Kind.TEXT),
                PortalListColumnMeta.of("currentStepName", "application.currentStep", Kind.TEXT),
                PortalListColumnMeta.of("currentAssignee", "application.currentAssignee", Kind.USER),
                PortalListColumnMeta.of("startTime", "application.startTime", Kind.DATETIME),
                PortalListColumnMeta.withOptions("status", "application.status", Kind.ENUM, statusOptions())
        );
    }

    public static ListFilterSql sql() {
        Map<String, PortalListColumnMeta> byField = new LinkedHashMap<>();
        for (PortalListColumnMeta column : columns()) {
            byField.put(column.field(), column);
        }
        return new ListFilterSql(byField, MyApplicationColumnSpec::sqlFor, "pi.id", "pi.start_time DESC");
    }

    private static String sqlFor(String field) {
        return switch (field) {
            case "requestId" -> "pi.variables->>'__request_id'";
            case "businessKey" -> "pi.business_key";
            case "currentStepName" -> "pi.current_node";
            case "currentAssignee" -> "pi.current_assignee";
            case "startTime" -> "pi.start_time::text";
            case "status" -> "pi.status";
            default -> throw new IllegalArgumentException("Unknown my-application column: " + field);
        };
    }

    private static List<PortalListColumnMeta.Option> statusOptions() {
        return List.of(
                new PortalListColumnMeta.Option("RUNNING", "application.running"),
                new PortalListColumnMeta.Option("COMPLETED", "application.completed"),
                new PortalListColumnMeta.Option("WITHDRAWN", "application.withdrawn"),
                new PortalListColumnMeta.Option("REJECTED", "application.rejected")
        );
    }
}
