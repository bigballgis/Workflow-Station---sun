package com.portal.util;

import com.platform.common.list.ListColumnMeta;
import com.platform.common.list.ListColumnMeta.Kind;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fixed column declaration for Completed Tasks. Kind follows the stored type (task name is
 * TEXT, duration is NUMBER, action is a closed ENUM derived from {@code DELETE_REASON_}).
 * Request ID is the persisted process-variable text {@code __request_id} (same key the
 * form writes); filter/sort compile to that JSON path so COUNT and the page share one predicate.
 */
public final class CompletedTaskColumnSpec {

    /**
     * Mirrors the engine's historic-task action mapping so a filter/group on Action matches
     * the tag the cell shows. ILIKE keeps the historic case-insensitive contains behaviour.
     */
    public static final String ACTION_SQL = "CASE"
            + " WHEN ht.DELETE_REASON_ ILIKE '%approved%' THEN 'approved'"
            + " WHEN ht.DELETE_REASON_ ILIKE '%rejected%' THEN 'rejected'"
            + " WHEN ht.DELETE_REASON_ ILIKE '%transfer%' THEN 'transferred'"
            + " WHEN ht.DELETE_REASON_ ILIKE '%delegate%' THEN 'delegated'"
            + " ELSE 'completed' END";

    private CompletedTaskColumnSpec() {
    }

    public static List<ListColumnMeta> columns() {
        return List.of(
                ListColumnMeta.of("requestId", "task.requestId", Kind.TEXT),
                ListColumnMeta.of("taskName", "task.taskName", Kind.TEXT),
                ListColumnMeta.displayOnly("currentStepName", "task.currentStep", Kind.TEXT),
                ListColumnMeta.of("processDefinitionName", "task.processName", Kind.TEXT),
                ListColumnMeta.withOptions("action", "task.action", Kind.ENUM, actionOptions()),
                ListColumnMeta.of("createTime", "task.createTime", Kind.DATETIME),
                ListColumnMeta.of("completedTime", "task.completedTime", Kind.DATETIME),
                ListColumnMeta.of("durationInMillis", "task.duration", Kind.NUMBER)
        );
    }

    public static ListFilterSql sql() {
        Map<String, ListColumnMeta> byField = new LinkedHashMap<>();
        for (ListColumnMeta column : columns()) {
            byField.put(column.field(), column);
        }
        return new ListFilterSql(byField, CompletedTaskColumnSpec::sqlFor, "ht.ID_", "ht.END_TIME_ DESC");
    }

    private static String sqlFor(String field) {
        return switch (field) {
            case "requestId" -> "pi.variables->>'__request_id'";
            case "taskName" -> "ht.NAME_";
            case "processDefinitionName" -> "pi.process_definition_name";
            case "action" -> ACTION_SQL;
            case "createTime" -> "ht.START_TIME_::text";
            case "completedTime" -> "ht.END_TIME_::text";
            case "durationInMillis" -> "ht.DURATION_::text";
            default -> throw new IllegalArgumentException("Unknown completed-task column: " + field);
        };
    }

    private static List<ListColumnMeta.Option> actionOptions() {
        return List.of(
                new ListColumnMeta.Option("approved", "action.approved"),
                new ListColumnMeta.Option("rejected", "action.rejected"),
                new ListColumnMeta.Option("transferred", "action.transferred"),
                new ListColumnMeta.Option("delegated", "action.delegated"),
                new ListColumnMeta.Option("completed", "action.completed")
        );
    }
}
