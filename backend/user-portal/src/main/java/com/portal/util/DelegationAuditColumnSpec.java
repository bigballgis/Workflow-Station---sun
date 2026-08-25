package com.portal.util;

import com.platform.common.list.ListColumnMeta;
import com.platform.common.list.ListColumnMeta.Kind;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fixed column declaration for Delegation Audit. Operation type/result are free text (writers
 * emit several verbs); identity columns use USER for people-picker filters.
 */
public final class DelegationAuditColumnSpec {

    private DelegationAuditColumnSpec() {
    }

    public static List<ListColumnMeta> columns() {
        return List.of(
                ListColumnMeta.of("operationType", "delegation.operationType", Kind.TEXT),
                ListColumnMeta.of("delegatorId", "delegation.delegator", Kind.USER),
                ListColumnMeta.of("delegateId", "delegation.delegate", Kind.USER),
                ListColumnMeta.of("taskId", "delegation.taskId", Kind.TEXT),
                ListColumnMeta.of("operationResult", "delegation.result", Kind.TEXT),
                ListColumnMeta.of("createdAt", "delegation.time", Kind.DATETIME)
        );
    }

    public static ListFilterSql sql() {
        Map<String, ListColumnMeta> byField = new LinkedHashMap<>();
        for (ListColumnMeta column : columns()) {
            byField.put(column.field(), column);
        }
        return new ListFilterSql(byField, DelegationAuditColumnSpec::sqlFor, "a.id", "a.created_at DESC");
    }

    private static String sqlFor(String field) {
        return switch (field) {
            case "operationType" -> "a.operation_type";
            case "delegatorId" -> "a.delegator_id";
            case "delegateId" -> "a.delegate_id";
            case "taskId" -> "a.task_id";
            case "operationResult" -> "a.operation_result";
            case "createdAt" -> "a.created_at::text";
            default -> throw new IllegalArgumentException("Unknown delegation-audit column: " + field);
        };
    }
}
