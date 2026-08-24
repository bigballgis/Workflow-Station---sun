package com.portal.util;

import com.platform.common.list.ListColumnMeta;
import com.platform.common.list.ListColumnMeta.Kind;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fixed column declaration for My Delegation Rules. Status and type are closed ENUMs so they
 * may group; identity columns use USER so the people-picker filter matches stored user ids.
 */
public final class DelegationRuleColumnSpec {

    private DelegationRuleColumnSpec() {
    }

    public static List<ListColumnMeta> columns() {
        return List.of(
                ListColumnMeta.of("delegateId", "delegation.delegateTo", Kind.USER),
                ListColumnMeta.withOptions("delegationType", "delegation.delegationType",
                        Kind.ENUM, typeOptions()),
                ListColumnMeta.of("startTime", "delegation.startTime", Kind.DATETIME),
                ListColumnMeta.of("endTime", "delegation.endTime", Kind.DATETIME),
                ListColumnMeta.withOptions("status", "delegation.status", Kind.ENUM, statusOptions()),
                ListColumnMeta.of("reason", "delegation.reason", Kind.TEXT),
                ListColumnMeta.of("createdAt", "task.createTime", Kind.DATETIME)
        );
    }

    public static ListFilterSql sql() {
        Map<String, ListColumnMeta> byField = new LinkedHashMap<>();
        for (ListColumnMeta column : columns()) {
            byField.put(column.field(), column);
        }
        return new ListFilterSql(byField, DelegationRuleColumnSpec::sqlFor, "r.id", "r.created_at DESC");
    }

    private static String sqlFor(String field) {
        return switch (field) {
            case "delegateId" -> "r.delegate_id";
            case "delegationType" -> "r.delegation_type";
            case "startTime" -> "r.start_time::text";
            case "endTime" -> "r.end_time::text";
            case "status" -> "r.status";
            case "reason" -> "r.reason";
            case "createdAt" -> "r.created_at::text";
            default -> throw new IllegalArgumentException("Unknown delegation-rule column: " + field);
        };
    }

    private static List<ListColumnMeta.Option> typeOptions() {
        return List.of(
                new ListColumnMeta.Option("ALL", "delegation.all"),
                new ListColumnMeta.Option("PARTIAL", "delegation.partial"),
                new ListColumnMeta.Option("TEMPORARY", "delegation.temporary"),
                new ListColumnMeta.Option("URGENT", "delegation.urgent")
        );
    }

    private static List<ListColumnMeta.Option> statusOptions() {
        return List.of(
                new ListColumnMeta.Option("ACTIVE", "delegation.active"),
                new ListColumnMeta.Option("INACTIVE", "delegation.inactive"),
                new ListColumnMeta.Option("EXPIRED", "delegation.expired"),
                new ListColumnMeta.Option("SUSPENDED", "delegation.suspended")
        );
    }
}
