package com.portal.util;

import com.platform.common.jdbc.SqlIdentifiers;
import com.platform.common.list.ListColumnMeta;
import com.platform.common.list.ListColumnMeta.Kind;
import com.platform.common.list.ListFilterSql;
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

    /**
     * Cells the toolbar text-search ORs across. This is the painted grid, not a
     * model-driven Quick Find view (no hidden find-columns, no related-entity hop).
     */
    public static List<String> searchableFields() {
        return columns().stream().map(ListColumnMeta::field).toList();
    }

    /**
     * Plain ILIKE on the text each cell shows. USER matches the resolved name;
     * DATETIME matches {@code YYYY-MM-DD HH:mm}; title matches the business key
     * or the process name the renderer falls back to.
     */
    public static String textSearchClause(String keyword, List<Object> params) {
        if (keyword == null || keyword.isBlank()) {
            return "";
        }
        String like = "%" + ListFilterSql.escapeLike(keyword.trim()) + "%";
        for (int i = 0; i < 7; i++) {
            params.add(like);
        }
        return " AND ("
                + "pi.variables->>'__request_id' ILIKE ?"
                + " OR COALESCE(NULLIF(BTRIM(pi.business_key), ''), pi.process_definition_name) ILIKE ?"
                + " OR COALESCE(pi.start_user_name, pi.start_user_id) ILIKE ?"
                + " OR " + ProcessAssigneeStoredSql.EXPRESSION + " ILIKE ?"
                + " OR " + assigneeDisplayNameExistsSql()
                + " OR to_char(pi.start_time, 'YYYY-MM-DD HH24:MI') ILIKE ?"
                + " OR pi.status ILIKE ?"
                + ")";
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
            case "currentAssignee" -> ProcessAssigneeStoredSql.EXPRESSION;
            case "startTime" -> "pi.start_time::text";
            case "status" -> "pi.status";
            default -> throw new IllegalArgumentException("Unknown audit-application column: " + field);
        };
    }

    /**
     * Display-name match for the assignee cell. Identifiers are allowlisted at the
     * concatenation site so a catalog-sourced name cannot reach SQL.
     */
    private static String assigneeDisplayNameExistsSql() {
        String users = SqlIdentifiers.requireQualifiedName("sys_users");
        String id = SqlIdentifiers.requireIdentifier("id");
        String username = SqlIdentifiers.requireIdentifier("username");
        String displayName = SqlIdentifiers.requireIdentifier("display_name");
        String fullName = SqlIdentifiers.requireIdentifier("full_name");
        String employeeId = SqlIdentifiers.requireIdentifier("employee_id");
        String name = "COALESCE(NULLIF(BTRIM(u." + fullName + "), ''),"
                + " NULLIF(BTRIM(u." + displayName + "), ''), u." + username + ")";
        String identities = "u." + id + "::text, u." + username + ", u." + employeeId;
        return "EXISTS (SELECT 1 FROM " + users + " u WHERE " + name + " ILIKE ?"
                + " AND (pi.current_assignee IN (" + identities + ")"
                + " OR EXISTS (SELECT 1 FROM unnest(regexp_split_to_array("
                + "btrim(pi.candidate_users), E'\\\\s*,\\\\s*')) AS token(v)"
                + " WHERE token.v IN (" + identities + "))))";
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
